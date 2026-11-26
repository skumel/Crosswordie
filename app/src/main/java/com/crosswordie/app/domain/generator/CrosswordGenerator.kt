package com.crosswordie.app.domain.generator

import com.crosswordie.app.domain.model.DifficultyLevel
import com.crosswordie.app.domain.model.Direction
import com.crosswordie.app.domain.model.GameMode
import com.crosswordie.app.domain.model.GridCell
import com.crosswordie.app.domain.model.GridCoord
import com.crosswordie.app.domain.model.PuzzleBoardState
import com.crosswordie.app.domain.model.WordSlot
import java.util.Random

/**
 * Deterministic, seeded procedural Crossword generator for 5x5 and 6x6 Clueless Crosswords.
 *
 * Uses Constraint Satisfaction Problem (CSP) backtracking with:
 * - Minimum Remaining Values (MRV) + Highest Intersection Degree heuristic for slot ordering
 * - Positional character lookup via [WordIndex]
 * - Forward checking (arc consistency lookahead) so dead-end placements are pruned immediately
 * - Strict uniqueness constraint (no duplicate words on the same board)
 * - Validation that all slots form a single connected component and have >= 1 intersection
 */
class CrosswordGenerator(
    private val wordIndex: WordIndex
) {
    data class Intersection(
        val slotIdA: String,
        val indexInA: Int,
        val slotIdB: String,
        val indexInB: Int,
        val coord: GridCoord
    )

    /**
     * Generates a complete, solvable [PuzzleBoardState] deterministically from [seed] and [difficulty].
     */
    fun generate(
        seed: Long,
        gameMode: GameMode = GameMode.DAILY,
        difficulty: DifficultyLevel = DifficultyLevel.MEDIUM,
        endlessLevel: Int = 1,
        dailyDateString: String? = null,
        prefer5x5: Boolean = (difficulty == DifficultyLevel.EASY)
    ): PuzzleBoardState {
        val rng = Random(seed)

        val primaryPool = when {
            prefer5x5 || difficulty == DifficultyLevel.EASY -> {
                GridTemplate.TEMPLATES_5X5 + GridTemplate.TEMPLATES_6X6
            }
            difficulty == DifficultyLevel.HARD -> {
                // Hard prioritizes 7-8 word 6x6 templates first
                val dense6x6 = GridTemplate.TEMPLATES_6X6.sortedByDescending { it.slotSpecs.size }
                dense6x6 + GridTemplate.TEMPLATES_5X5
            }
            else -> {
                // Medium prioritizes 6-7 word 6x6 templates
                val medium6x6 = GridTemplate.TEMPLATES_6X6.sortedBy { it.slotSpecs.size }
                medium6x6 + GridTemplate.TEMPLATES_5X5
            }
        }

        // Deterministically rotate starting index in the template pool based on seed
        val poolForDifficulty = when (difficulty) {
            DifficultyLevel.EASY -> GridTemplate.TEMPLATES_5X5
            DifficultyLevel.MEDIUM -> GridTemplate.TEMPLATES_6X6.filter { it.slotSpecs.size <= 7 }
                .ifEmpty { GridTemplate.TEMPLATES_6X6 }
            DifficultyLevel.HARD -> GridTemplate.TEMPLATES_6X6.filter { it.slotSpecs.size >= 7 }
                .ifEmpty { GridTemplate.TEMPLATES_6X6 }
        }

        val startOffset = ((seed ushr 1) % poolForDifficulty.size).toInt().coerceAtLeast(0)
        val rotatedPreferred = (0 until poolForDifficulty.size).map { i ->
            poolForDifficulty[(startOffset + i) % poolForDifficulty.size]
        }
        val orderedTemplates = (rotatedPreferred + primaryPool).distinct()

        for (template in orderedTemplates) {
            if (!validateTemplateTopology(template)) continue

            val assignment = solveTemplate(template, rng)
            if (assignment != null) {
                return buildPuzzleBoardState(
                    template = template,
                    wordAssignment = assignment,
                    seed = seed,
                    gameMode = gameMode,
                    difficulty = difficulty,
                    endlessLevel = endlessLevel,
                    dailyDateString = dailyDateString
                )
            }
        }

        throw IllegalStateException("Unable to generate crossword board for seed=$seed")
    }

    /**
     * Verifies that a [GridTemplate] satisfies all Crosswordie structural invariants:
     * 1. Has between 6 and 8 words of lengths 3..6.
     * 2. Every slot is within grid bounds.
     * 3. Every slot intersects at least one perpendicular slot.
     * 4. All slots form a single connected graph component.
     */
    fun validateTemplateTopology(template: GridTemplate): Boolean {
        val specs = template.slotSpecs
        if (specs.size !in 6..8) return false

        for (spec in specs) {
            if (spec.length !in WordIndex.MIN_WORD_LEN..WordIndex.MAX_WORD_LEN) return false
            for (c in spec.coords) {
                if (c.row !in 0 until template.gridSize || c.col !in 0 until template.gridSize) {
                    return false
                }
            }
        }

        val adjacency = buildIntersectionGraph(specs)
        // Every slot must have at least 1 perpendicular intersection
        if (specs.any { adjacency[it.id].isNullOrEmpty() }) return false

        // BFS to verify all slots belong to a single connected component
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        val firstId = specs.first().id
        visited.add(firstId)
        queue.add(firstId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (ix in adjacency[current].orEmpty()) {
                val neighbor = if (ix.slotIdA == current) ix.slotIdB else ix.slotIdA
                if (visited.add(neighbor)) {
                    queue.add(neighbor)
                }
            }
        }

        return visited.size == specs.size
    }

    private fun buildIntersectionGraph(specs: List<SlotSpec>): Map<String, List<Intersection>> {
        val map = HashMap<String, MutableList<Intersection>>()
        for (i in specs.indices) {
            val a = specs[i]
            for (j in i + 1 until specs.size) {
                val b = specs[j]
                if (a.direction == b.direction) continue
                for ((idxA, coordA) in a.coords.withIndex()) {
                    val idxB = b.indexOf(coordA)
                    if (idxB >= 0) {
                        val ix = Intersection(a.id, idxA, b.id, idxB, coordA)
                        map.getOrPut(a.id) { mutableListOf() }.add(ix)
                        map.getOrPut(b.id) { mutableListOf() }.add(
                            Intersection(b.id, idxB, a.id, idxA, coordA)
                        )
                    }
                }
            }
        }
        return map
    }

    private fun solveTemplate(
        template: GridTemplate,
        rng: Random
    ): Map<String, String>? {
        val specsById = template.slotSpecs.associateBy { it.id }
        val intersectionsBySlot = buildIntersectionGraph(template.slotSpecs)
        val assignedWords = HashMap<String, String>()
        val usedWords = HashSet<String>()
        var backtracks = 0

        fun currentConstraintsFor(slotId: String): Map<Int, Char> {
            val constraints = HashMap<Int, Char>()
            for (ix in intersectionsBySlot[slotId].orEmpty()) {
                val otherWord = assignedWords[ix.slotIdB]
                if (otherWord != null) {
                    constraints[ix.indexInA] = otherWord[ix.indexInB]
                }
            }
            return constraints
        }

        fun selectNextUnassignedSlot(): SlotSpec? {
            var bestSpec: SlotSpec? = null
            var bestCandidateCount = Int.MAX_VALUE
            var bestIntersectionDegree = -1

            for (spec in template.slotSpecs) {
                if (assignedWords.containsKey(spec.id)) continue
                val constraints = currentConstraintsFor(spec.id)
                val candidates = wordIndex.getCandidates(spec.length, constraints, usedWords)
                val count = candidates.size
                val degree = intersectionsBySlot[spec.id]?.size ?: 0

                if (count < bestCandidateCount ||
                    (count == bestCandidateCount && degree > bestIntersectionDegree)
                ) {
                    bestCandidateCount = count
                    bestIntersectionDegree = degree
                    bestSpec = spec
                }
            }
            return bestSpec
        }

        fun passesForwardChecking(placedSlotId: String, candidateWord: String): Boolean {
            for (ix in intersectionsBySlot[placedSlotId].orEmpty()) {
                val neighborId = ix.slotIdB
                if (assignedWords.containsKey(neighborId)) continue

                val neighborSpec = specsById.getValue(neighborId)
                val neighborConstraints = HashMap<Int, Char>(currentConstraintsFor(neighborId))
                neighborConstraints[ix.indexInB] = candidateWord[ix.indexInA]

                if (!wordIndex.hasAnyCandidate(neighborSpec.length, neighborConstraints, usedWords)) {
                    return false
                }
            }
            return true
        }

        fun backtrack(): Boolean {
            if (assignedWords.size == template.slotSpecs.size) return true
            if (backtracks >= MAX_BACKTRACKS) return false

            val nextSpec = selectNextUnassignedSlot() ?: return true
            val constraints = currentConstraintsFor(nextSpec.id)
            val candidates = wordIndex.getCandidates(nextSpec.length, constraints, usedWords).toMutableList()

            if (candidates.isEmpty()) {
                backtracks++
                return false
            }

            // Deterministic shuffle using the seeded RNG
            candidates.shuffle(rng)

            // Limit branching factor per level to keep generation snappy while exploring variety
            val maxBranches = candidates.size.coerceAtMost(80)
            for (i in 0 until maxBranches) {
                val word = candidates[i]
                assignedWords[nextSpec.id] = word
                usedWords.add(word)

                if (passesForwardChecking(nextSpec.id, word)) {
                    if (backtrack()) return true
                }

                assignedWords.remove(nextSpec.id)
                usedWords.remove(word)

                if (backtracks >= MAX_BACKTRACKS) return false
            }

            backtracks++
            return false
        }

        return if (backtrack()) assignedWords else null
    }

    private fun buildPuzzleBoardState(
        template: GridTemplate,
        wordAssignment: Map<String, String>,
        seed: Long,
        gameMode: GameMode,
        difficulty: DifficultyLevel,
        endlessLevel: Int,
        dailyDateString: String?
    ): PuzzleBoardState {
        var slots = template.slotSpecs.map { spec ->
            WordSlot(
                id = spec.id,
                clueNumber = spec.clueNumber,
                direction = spec.direction,
                startCoord = spec.startCoord,
                length = spec.length,
                targetWord = wordAssignment.getValue(spec.id)
            )
        }

        val acrossByCoord = HashMap<GridCoord, WordSlot>()
        val downByCoord = HashMap<GridCoord, WordSlot>()
        val charByCoord = HashMap<GridCoord, Char>()
        val clueNumByStartCoord = HashMap<GridCoord, Int>()

        for (slot in slots) {
            clueNumByStartCoord[slot.startCoord] = slot.clueNumber
            for ((idx, coord) in slot.coords.withIndex()) {
                charByCoord[coord] = slot.targetWord[idx]
                if (slot.direction == Direction.ACROSS) {
                    acrossByCoord[coord] = slot
                } else {
                    downByCoord[coord] = slot
                }
            }
        }

        // For Easy difficulty, pre-unlock 1 starter intersection cell so the player starts with a green hint!
        val starterLockedCoords = HashSet<GridCoord>()
        if (difficulty.starterHints > 0) {
            val firstIntersectionCoord = slots.first().coords.firstOrNull { c ->
                acrossByCoord[c] != null && downByCoord[c] != null
            } ?: slots.first().startCoord
            starterLockedCoords.add(firstIntersectionCoord)

            slots = slots.map { slot ->
                val idx = slot.indexOfCoord(firstIntersectionCoord)
                if (idx >= 0) {
                    slot.copy(lockedIndices = mapOf(idx to slot.targetWord[idx]))
                } else {
                    slot
                }
            }
        }

        val cells = ArrayList<GridCell>(template.gridSize * template.gridSize)
        for (r in 0 until template.gridSize) {
            for (c in 0 until template.gridSize) {
                val coord = GridCoord(r, c)
                val solutionChar = charByCoord[coord]
                val starterLocked = if (coord in starterLockedCoords) solutionChar else null
                cells.add(
                    GridCell(
                        coord = coord,
                        isPlayable = solutionChar != null,
                        solutionChar = solutionChar,
                        lockedChar = starterLocked,
                        clueNumber = clueNumByStartCoord[coord],
                        acrossSlotId = acrossByCoord[coord]?.id,
                        downSlotId = downByCoord[coord]?.id
                    )
                )
            }
        }

        val puzzleId = when (gameMode) {
            GameMode.DAILY -> "DAILY_${difficulty.name}_${dailyDateString ?: seed}"
            GameMode.ENDLESS -> "ENDLESS_${difficulty.name}_L${endlessLevel}_$seed"
        }

        return PuzzleBoardState(
            puzzleId = puzzleId,
            seed = seed,
            gameMode = gameMode,
            difficulty = difficulty,
            endlessLevel = endlessLevel,
            dailyDateString = dailyDateString,
            gridSize = template.gridSize,
            cells = cells,
            slots = slots,
            activeSlotId = slots.first().id,
            hintsRemaining = difficulty.dailyHintAllowance
        )
    }

    companion object {
        private const val MAX_BACKTRACKS = 1500
    }
}
