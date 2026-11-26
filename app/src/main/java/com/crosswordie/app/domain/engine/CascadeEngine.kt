package com.crosswordie.app.domain.engine

import com.crosswordie.app.domain.generator.WordIndex
import com.crosswordie.app.domain.model.DifficultyLevel
import com.crosswordie.app.domain.model.GameMode
import com.crosswordie.app.domain.model.GridCoord
import com.crosswordie.app.domain.model.LetterState
import com.crosswordie.app.domain.model.PuzzleBoardState
import com.crosswordie.app.domain.model.SlotStatus
import com.crosswordie.app.domain.model.WordSlot
import java.util.Random

/**
 * Manages the core Crosswordie gameplay loop:
 * 1. Submitting a guess for the active [WordSlot] with steep try-based scoring.
 * 2. Locking solved words into the crossword grid.
 * 3. Handling 5-guess failure OR explicit player "Give Up" by revealing the word with 0 points (+1 strike)
 *    and still cascading its intersections so the player is never soft-locked.
 * 4. Supporting limited Hints:
 *    - **3-Word Hint:** Shows 3 candidate words that fit current constraints (guaranteed to include the answer).
 *    - **Free Letter Hint:** Permanently unlocks one unrevealed character in the active word (and cascades to any intersecting word!).
 * 5. Awarding bonus Hints in Endless Mode for 1st-try ("Genius") solves and cascade auto-solves!
 */
object CascadeEngine {

    const val MAX_HINT_CAP = 6

    enum class GuessOutcome {
        CONTINUE_SLOT,
        SLOT_SOLVED,
        SLOT_REVEALED_FAILED,
        BOARD_COMPLETED
    }

    data class GuessSubmissionResult(
        val updatedBoard: PuzzleBoardState,
        val outcome: GuessOutcome,
        val solvedOrRevealedSlotId: String? = null,
        val cascadedAutoSolvedSlotIds: List<String> = emptyList(),
        val newlyUnlockedCoords: Set<GridCoord> = emptySet(),
        val pointsEarned: Int = 0,
        val bonusHintsEarned: Int = 0
    )

    /**
     * Steep try-based points table rewarding solving in fewer guesses:
     * - 1st Try: 1,000 pts (+ wordLength * 50)
     * - 2nd Try:   650 pts (+ wordLength * 40)
     * - 3rd Try:   400 pts (+ wordLength * 30)
     * - 4th Try:   225 pts (+ wordLength * 20)
     * - 5th Try:   100 pts (+ wordLength * 10)
     * Scaled by [DifficultyLevel.scoreMultiplierPercent] (Easy 1.0x, Medium 1.25x, Hard 1.5x).
     */
    fun calculateSolvePoints(
        guessCount: Int,
        wordLength: Int,
        difficulty: DifficultyLevel
    ): Int {
        val rawBase = when (guessCount) {
            1 -> 1000 + wordLength * 50
            2 -> 650 + wordLength * 40
            3 -> 400 + wordLength * 30
            4 -> 225 + wordLength * 20
            else -> 100 + wordLength * 10
        }
        return (rawBase * difficulty.scoreMultiplierPercent) / 100
    }

    /**
     * Submits a validated [guessWord] for [board]'s `activeSlotId` and applies all cascade transitions.
     */
    fun submitGuess(
        board: PuzzleBoardState,
        guessWord: String
    ): GuessSubmissionResult {
        val activeSlot = board.activeSlot
            ?: return GuessSubmissionResult(board, GuessOutcome.CONTINUE_SLOT)

        if (activeSlot.isCompleted) {
            return GuessSubmissionResult(board, GuessOutcome.CONTINUE_SLOT)
        }

        val normalizedGuess = guessWord.trim().uppercase()
        require(normalizedGuess.length == activeSlot.length) {
            "Submitted guess length ${normalizedGuess.length} does not match slot ${activeSlot.id} length ${activeSlot.length}"
        }

        val evaluatedRow = WordleEvaluator.evaluateGuess(
            guess = normalizedGuess,
            targetWord = activeSlot.targetWord,
            lockedIndices = activeSlot.lockedIndices
        )

        val updatedGuesses = activeSlot.guesses + evaluatedRow
        val isExactMatch = normalizedGuess == activeSlot.targetWord
        val isOutOfGuesses = updatedGuesses.size >= WordSlot.MAX_GUESSES && !isExactMatch

        val newStatus = when {
            isExactMatch -> SlotStatus.SOLVED
            isOutOfGuesses -> SlotStatus.REVEALED_FAILED
            else -> SlotStatus.IN_PROGRESS
        }

        val selfLockedIndices = HashMap(activeSlot.lockedIndices)
        if (newStatus == SlotStatus.SOLVED || newStatus == SlotStatus.REVEALED_FAILED) {
            for (i in 0 until activeSlot.length) {
                selfLockedIndices[i] = activeSlot.targetWord[i]
            }
        }

        val updatedActiveSlot = activeSlot.copy(
            guesses = updatedGuesses,
            lockedIndices = selfLockedIndices,
            status = newStatus
        )

        if (newStatus == SlotStatus.IN_PROGRESS) {
            val slotsById = board.slots.associateBy { it.id }.toMutableMap()
            slotsById[updatedActiveSlot.id] = updatedActiveSlot
            val nextBoard = board.copy(
                slots = board.slots.map { slotsById.getValue(it.id) }
            )
            return GuessSubmissionResult(
                updatedBoard = nextBoard,
                outcome = GuessOutcome.CONTINUE_SLOT
            )
        }

        val basePoints = if (newStatus == SlotStatus.SOLVED) {
            calculateSolvePoints(
                guessCount = updatedGuesses.size,
                wordLength = updatedActiveSlot.length,
                difficulty = board.difficulty
            )
        } else {
            0
        }

        // In Endless Mode, solving on the very 1st try awards +1 Bonus Hint!
        val firstTryHintBonus = if (board.gameMode == GameMode.ENDLESS &&
            newStatus == SlotStatus.SOLVED &&
            updatedGuesses.size == 1
        ) 1 else 0

        return completeSlotAndCascade(
            board = board,
            completedActiveSlot = updatedActiveSlot,
            basePoints = basePoints,
            addedStrikes = if (newStatus == SlotStatus.REVEALED_FAILED) 1 else 0,
            initialHintBonus = firstTryHintBonus
        )
    }

    /**
     * Allows the player to voluntarily Give Up on the currently focused [WordSlot].
     * Reveals the word with 0 points (+1 Strike) and cascades all of its intersecting letters
     * to perpendicular unsolved words so the player can keep progressing.
     */
    fun giveUpActiveWord(board: PuzzleBoardState): GuessSubmissionResult {
        val activeSlot = board.activeSlot
            ?: return GuessSubmissionResult(board, GuessOutcome.CONTINUE_SLOT)
        if (activeSlot.isCompleted) {
            return GuessSubmissionResult(board, GuessOutcome.CONTINUE_SLOT)
        }

        val allIndicesLocked = (0 until activeSlot.length).associateWith { i -> activeSlot.targetWord[i] }
        val revealedSlot = activeSlot.copy(
            lockedIndices = allIndicesLocked,
            status = SlotStatus.REVEALED_FAILED
        )

        return completeSlotAndCascade(
            board = board,
            completedActiveSlot = revealedSlot,
            basePoints = 0,
            addedStrikes = 1,
            initialHintBonus = 0
        )
    }

    /**
     * Spends 1 Hint to reveal a single unrevealed letter ("Free Letter") in the active [WordSlot].
     * Skips any position the player ALREADY discovered as Green (`CORRECT` / `LOCKED_HINT`) in prior guesses!
     * Also locks that cell on the crossword grid and propagates it to any intersecting perpendicular word!
     * If this was the final unknown letter of the active word, it completes and cascades the word.
     */
    fun useFreeLetterHint(board: PuzzleBoardState): GuessSubmissionResult? {
        val activeSlot = board.activeSlot ?: return null
        if (activeSlot.isCompleted || board.hintsRemaining <= 0) return null

        // Collect all indices already known to the player (either pre-locked OR matched Green in a previous guess)
        val alreadyKnownGreenIndices = HashSet<Int>(activeSlot.lockedIndices.keys)
        for (row in activeSlot.guesses) {
            for ((idx, tile) in row.tiles.withIndex()) {
                if (tile.state == LetterState.CORRECT || tile.state == LetterState.LOCKED_HINT) {
                    alreadyKnownGreenIndices.add(idx)
                }
            }
        }

        val trulyUnknownPositions = (0 until activeSlot.length).filter { it !in alreadyKnownGreenIndices }
        val unlockedPositions = (0 until activeSlot.length).filter { !activeSlot.lockedIndices.containsKey(it) }
        if (unlockedPositions.isEmpty()) return null

        // Always pick a letter the player does NOT already know as Green (falling back only if every single position was already Green)
        val candidatePool = trulyUnknownPositions.ifEmpty { unlockedPositions }

        // Prefer unlocking an intersection cell among truly unknown positions so it helps two words at once!
        val boardCellsByCoord = board.cells.associateBy { it.coord }
        val chosenIdx = candidatePool.firstOrNull { idx ->
            val c = activeSlot.coords[idx]
            val cell = boardCellsByCoord[c]
            cell?.acrossSlotId != null && cell.downSlotId != null
        } ?: candidatePool.first()

        val revealedChar = activeSlot.targetWord[chosenIdx]
        val revealedCoord = activeSlot.coords[chosenIdx]

        val boardAfterHintSpend = board.copy(
            hintsRemaining = (board.hintsRemaining - 1).coerceAtLeast(0)
        )

        // If revealing this free letter means every position in the word is now known/locked, finish & cascade it!
        val remainingUnknownAfterReveal = trulyUnknownPositions.count { it != chosenIdx }
        if (unlockedPositions.size == 1 || remainingUnknownAfterReveal == 0) {
            val fullLocks = (0 until activeSlot.length).associateWith { i -> activeSlot.targetWord[i] }
            val solvedSlot = activeSlot.copy(
                lockedIndices = fullLocks,
                status = SlotStatus.SOLVED
            )
            return completeSlotAndCascade(
                board = boardAfterHintSpend,
                completedActiveSlot = solvedSlot,
                basePoints = 100,
                addedStrikes = 0,
                initialHintBonus = 0
            )
        }

        // Otherwise lock this single coordinate on the grid, activeSlot, and any intersecting perpendicular slot
        val slotsById = boardAfterHintSpend.slots.associateBy { it.id }.toMutableMap()
        val updatedActiveLocks = HashMap(activeSlot.lockedIndices).apply { put(chosenIdx, revealedChar) }
        slotsById[activeSlot.id] = activeSlot.copy(lockedIndices = updatedActiveLocks)

        val newlyCascadedAutoSolves = mutableListOf<String>()
        val secondaryCascadeQueue = ArrayDeque<WordSlot>()
        val newlyUnlockedCoords = LinkedHashSet<GridCoord>().apply { add(revealedCoord) }

        for ((otherId, otherSlot) in slotsById.entries.toList()) {
            if (otherId == activeSlot.id || otherSlot.isCompleted) continue
            val idxInOther = otherSlot.indexOfCoord(revealedCoord)
            if (idxInOther >= 0) {
                val otherLocks = HashMap(otherSlot.lockedIndices).apply { put(idxInOther, revealedChar) }
                if (otherLocks.size == otherSlot.length) {
                    // Intersecting perpendicular word just had its final missing letter filled!
                    val autoSolvedOther = otherSlot.copy(
                        lockedIndices = otherLocks,
                        status = SlotStatus.SOLVED,
                        autoSolvedByCascade = true
                    )
                    slotsById[otherId] = autoSolvedOther
                    newlyCascadedAutoSolves.add(otherId)
                    secondaryCascadeQueue.add(autoSolvedOther)
                } else {
                    slotsById[otherId] = otherSlot.copy(lockedIndices = otherLocks)
                }
            }
        }

        // Recursively propagate any newly auto-solved perpendicular word to its own intersections!
        while (secondaryCascadeQueue.isNotEmpty()) {
            val completedSlot = secondaryCascadeQueue.removeFirst()
            for ((charIdx, coord) in completedSlot.coords.withIndex()) {
                val solvedChar = completedSlot.targetWord[charIdx]
                newlyUnlockedCoords.add(coord)

                for ((otherId, otherSlot) in slotsById.entries.toList()) {
                    if (otherId == completedSlot.id || otherSlot.isCompleted) continue
                    val idxInOther = otherSlot.indexOfCoord(coord)
                    if (idxInOther >= 0 && otherSlot.lockedIndices[idxInOther] != solvedChar) {
                        val updatedLocks = HashMap(otherSlot.lockedIndices).apply { put(idxInOther, solvedChar) }
                        if (updatedLocks.size == otherSlot.length) {
                            val autoSolvedNext = otherSlot.copy(
                                lockedIndices = updatedLocks,
                                status = SlotStatus.SOLVED,
                                autoSolvedByCascade = true
                            )
                            slotsById[otherId] = autoSolvedNext
                            newlyCascadedAutoSolves.add(otherId)
                            secondaryCascadeQueue.add(autoSolvedNext)
                        } else {
                            slotsById[otherId] = otherSlot.copy(lockedIndices = updatedLocks)
                        }
                    }
                }
            }
        }

        val lockedCharByCoord = HashMap<GridCoord, Char>()
        lockedCharByCoord[revealedCoord] = revealedChar
        for (slot in slotsById.values) {
            if (slot.isCompleted) {
                for ((idx, coord) in slot.coords.withIndex()) {
                    lockedCharByCoord[coord] = slot.targetWord[idx]
                }
            }
        }

        val updatedCells = boardAfterHintSpend.cells.map { cell ->
            val newLocked = lockedCharByCoord[cell.coord] ?: cell.lockedChar
            cell.copy(lockedChar = newLocked)
        }

        val updatedSlots = boardAfterHintSpend.slots.map { slotsById.getValue(it.id) }
        val allCompleted = updatedSlots.all { it.isCompleted }
        val bonusPoints = (newlyCascadedAutoSolves.size * 500 * boardAfterHintSpend.difficulty.scoreMultiplierPercent) / 100
        val flawlessBonus = if (allCompleted && boardAfterHintSpend.strikes == 0) {
            (750 * boardAfterHintSpend.difficulty.scoreMultiplierPercent) / 100
        } else 0
        val totalBonusPoints = bonusPoints + flawlessBonus

        var earnedHints = 0
        if (boardAfterHintSpend.gameMode == GameMode.ENDLESS) {
            if (newlyCascadedAutoSolves.isNotEmpty()) earnedHints += 1
            if (allCompleted) earnedHints += if (boardAfterHintSpend.strikes == 0) 2 else 1
        }
        val finalHintsRemaining = (boardAfterHintSpend.hintsRemaining + earnedHints).coerceAtMost(MAX_HINT_CAP)

        val currentActiveAfterCascade = slotsById.getValue(activeSlot.id)
        val nextActiveSlotId = when {
            allCompleted -> activeSlot.id
            currentActiveAfterCascade.isCompleted -> selectNextActiveSlotId(currentActiveAfterCascade, updatedSlots)
            else -> activeSlot.id
        }

        val finalBoard = boardAfterHintSpend.copy(
            cells = updatedCells,
            slots = updatedSlots,
            activeSlotId = nextActiveSlotId,
            hintsRemaining = finalHintsRemaining,
            score = boardAfterHintSpend.score + totalBonusPoints,
            isBoardComplete = allCompleted
        )

        val outcome = when {
            allCompleted -> GuessOutcome.BOARD_COMPLETED
            currentActiveAfterCascade.isCompleted -> GuessOutcome.SLOT_SOLVED
            else -> GuessOutcome.CONTINUE_SLOT
        }

        return GuessSubmissionResult(
            updatedBoard = finalBoard,
            outcome = outcome,
            solvedOrRevealedSlotId = if (currentActiveAfterCascade.isCompleted) activeSlot.id else null,
            cascadedAutoSolvedSlotIds = newlyCascadedAutoSolves,
            newlyUnlockedCoords = newlyUnlockedCoords,
            pointsEarned = totalBonusPoints,
            bonusHintsEarned = finalHintsRemaining - boardAfterHintSpend.hintsRemaining
        )
    }

    /**
     * Generates up to 3 candidate words (always including `slot.targetWord` plus 2 plausible decoys
     * of the same length that match locked indices and haven't been eliminated by gray letters if possible),
     * shuffled deterministically.
     */
    fun generateThreeWordHintOptions(
        slot: WordSlot,
        wordIndex: WordIndex,
        boardTargetWords: Set<String>,
        seed: Long
    ): List<String> {
        // Collect absent letters and misplaced exclusions from previous guesses on this slot
        val absentChars = HashSet<Char>()
        val wrongPosAt = HashMap<Int, MutableSet<Char>>()
        val confirmedConstraints = HashMap(slot.lockedIndices)

        for (row in slot.guesses) {
            val presentOrCorrectInRow = row.tiles
                .filter { it.state == LetterState.CORRECT || it.state == LetterState.LOCKED_HINT || it.state == LetterState.PRESENT }
                .map { it.char }
                .toSet()

            for ((idx, tile) in row.tiles.withIndex()) {
                when (tile.state) {
                    LetterState.CORRECT, LetterState.LOCKED_HINT -> {
                        confirmedConstraints[idx] = tile.char
                    }
                    LetterState.PRESENT -> {
                        wrongPosAt.getOrPut(idx) { HashSet() }.add(tile.char)
                    }
                    LetterState.ABSENT -> {
                        if (tile.char !in presentOrCorrectInRow) {
                            absentChars.add(tile.char)
                        } else {
                            wrongPosAt.getOrPut(idx) { HashSet() }.add(tile.char)
                        }
                    }
                    LetterState.UNKNOWN -> Unit
                }
            }
        }

        val rng = Random(seed xor slot.id.hashCode().toLong())

        // Tier 1: Candidates matching confirmed green positions AND respecting gray/yellow clues
        val matchingPositionCandidates = wordIndex.getCandidates(
            length = slot.length,
            constraints = confirmedConstraints,
            excludedWords = boardTargetWords
        )

        val strictDecoys = matchingPositionCandidates.filter { candidate ->
            candidate != slot.targetWord &&
                candidate.none { ch -> ch in absentChars } &&
                wrongPosAt.all { (pos, badChars) -> candidate[pos] !in badChars }
        }.shuffled(rng)

        val chosenDecoys = ArrayList<String>(2)
        for (w in strictDecoys) {
            if (chosenDecoys.size >= 2) break
            chosenDecoys.add(w)
        }

        // Tier 2 fallback: Any candidate matching lockedIndices if strict pool had fewer than 2 words
        if (chosenDecoys.size < 2) {
            val fallbackPool = wordIndex.getCandidates(
                length = slot.length,
                constraints = slot.lockedIndices,
                excludedWords = boardTargetWords + chosenDecoys + slot.targetWord
            ).shuffled(rng)
            for (w in fallbackPool) {
                if (chosenDecoys.size >= 2) break
                chosenDecoys.add(w)
            }
        }

        // Tier 3 fallback: Any word of the same length
        if (chosenDecoys.size < 2) {
            val anyOfLength = wordIndex.getCandidates(
                length = slot.length,
                constraints = emptyMap(),
                excludedWords = boardTargetWords + chosenDecoys + slot.targetWord
            ).shuffled(rng)
            for (w in anyOfLength) {
                if (chosenDecoys.size >= 2) break
                chosenDecoys.add(w)
            }
        }

        return (chosenDecoys + slot.targetWord).shuffled(rng)
    }

    private fun completeSlotAndCascade(
        board: PuzzleBoardState,
        completedActiveSlot: WordSlot,
        basePoints: Int,
        addedStrikes: Int,
        initialHintBonus: Int
    ): GuessSubmissionResult {
        val slotsById = board.slots.associateBy { it.id }.toMutableMap()
        slotsById[completedActiveSlot.id] = completedActiveSlot

        val newlyUnlockedCoords = LinkedHashSet<GridCoord>()
        val cascadedSlotIds = mutableListOf<String>()
        val failedRevealedCoords = HashSet<GridCoord>()

        if (completedActiveSlot.status == SlotStatus.REVEALED_FAILED) {
            val previouslyLockedCoords = board.cells.filter { it.lockedChar != null }.map { it.coord }.toSet()
            for (c in completedActiveSlot.coords) {
                if (c !in previouslyLockedCoords) {
                    failedRevealedCoords.add(c)
                }
            }
        }

        val cascadeQueue = ArrayDeque<WordSlot>()
        cascadeQueue.add(completedActiveSlot)

        while (cascadeQueue.isNotEmpty()) {
            val completedSlot = cascadeQueue.removeFirst()
            for ((charIdx, coord) in completedSlot.coords.withIndex()) {
                val solvedChar = completedSlot.targetWord[charIdx]
                newlyUnlockedCoords.add(coord)

                for ((otherId, otherSlot) in slotsById.entries) {
                    if (otherId == completedSlot.id || otherSlot.isCompleted) continue
                    val idxInOther = otherSlot.indexOfCoord(coord)
                    if (idxInOther >= 0 && otherSlot.lockedIndices[idxInOther] != solvedChar) {
                        val updatedLocks = HashMap(otherSlot.lockedIndices)
                        updatedLocks[idxInOther] = solvedChar

                        if (updatedLocks.size == otherSlot.length) {
                            val autoSolvedSlot = otherSlot.copy(
                                lockedIndices = updatedLocks,
                                status = SlotStatus.SOLVED,
                                autoSolvedByCascade = true
                            )
                            slotsById[otherId] = autoSolvedSlot
                            cascadedSlotIds.add(otherId)
                            cascadeQueue.add(autoSolvedSlot)
                        } else {
                            slotsById[otherId] = otherSlot.copy(lockedIndices = updatedLocks)
                        }
                    }
                }
            }
        }

        val cascadeBonusPoints = (cascadedSlotIds.size * 500 * board.difficulty.scoreMultiplierPercent) / 100
        val updatedSlots = board.slots.map { slotsById.getValue(it.id) }
        val allCompleted = updatedSlots.all { it.isCompleted }

        // Flawless Board Completion Bonus (0 strikes across the whole crossword)
        val flawlessBoardBonus = if (allCompleted && (board.strikes + addedStrikes) == 0) {
            (750 * board.difficulty.scoreMultiplierPercent) / 100
        } else {
            0
        }

        val totalEarnedPoints = basePoints + cascadeBonusPoints + flawlessBoardBonus

        // Endless Mode Hint Rewards:
        // - +1 Hint for 1st-try solve (`initialHintBonus`)
        // - +1 Hint if a Cascade Auto-Solve was triggered!
        // - +1 Hint upon clearing an Endless board (+2 if Flawless 0-strike board!)
        var earnedHints = initialHintBonus
        if (board.gameMode == GameMode.ENDLESS) {
            if (cascadedSlotIds.isNotEmpty()) {
                earnedHints += 1
            }
            if (allCompleted) {
                earnedHints += if ((board.strikes + addedStrikes) == 0) 2 else 1
            }
        }
        val nextHintsRemaining = (board.hintsRemaining + earnedHints).coerceAtMost(MAX_HINT_CAP)

        val lockedCharByCoord = HashMap<GridCoord, Char>()
        for (slot in slotsById.values) {
            if (slot.isCompleted) {
                for ((idx, coord) in slot.coords.withIndex()) {
                    lockedCharByCoord[coord] = slot.targetWord[idx]
                }
            }
        }

        val updatedCells = board.cells.map { cell ->
            val newlyLockedChar = lockedCharByCoord[cell.coord] ?: cell.lockedChar
            val isFailureCell = cell.isRevealedByFailure || (cell.coord in failedRevealedCoords)
            cell.copy(
                lockedChar = newlyLockedChar,
                isRevealedByFailure = isFailureCell
            )
        }

        val nextActiveSlotId = if (allCompleted) {
            completedActiveSlot.id
        } else {
            selectNextActiveSlotId(
                currentSlot = completedActiveSlot,
                allSlots = updatedSlots
            )
        }

        val finalBoard = board.copy(
            cells = updatedCells,
            slots = updatedSlots,
            activeSlotId = nextActiveSlotId,
            hintsRemaining = nextHintsRemaining,
            strikes = board.strikes + addedStrikes,
            score = board.score + totalEarnedPoints,
            isBoardComplete = allCompleted
        )

        val finalOutcome = when {
            allCompleted -> GuessOutcome.BOARD_COMPLETED
            completedActiveSlot.status == SlotStatus.SOLVED -> GuessOutcome.SLOT_SOLVED
            else -> GuessOutcome.SLOT_REVEALED_FAILED
        }

        return GuessSubmissionResult(
            updatedBoard = finalBoard,
            outcome = finalOutcome,
            solvedOrRevealedSlotId = completedActiveSlot.id,
            cascadedAutoSolvedSlotIds = cascadedSlotIds,
            newlyUnlockedCoords = newlyUnlockedCoords,
            pointsEarned = totalEarnedPoints,
            bonusHintsEarned = nextHintsRemaining - board.hintsRemaining
        )
    }

    fun selectNextActiveSlotId(
        currentSlot: WordSlot,
        allSlots: List<WordSlot>
    ): String {
        val currentCoords = currentSlot.coords.toSet()

        val intersectingUnsolved = allSlots.firstOrNull { candidate ->
            !candidate.isCompleted &&
                candidate.id != currentSlot.id &&
                candidate.coords.any { it in currentCoords }
        }
        if (intersectingUnsolved != null) return intersectingUnsolved.id

        val currentIndex = allSlots.indexOfFirst { it.id == currentSlot.id }.coerceAtLeast(0)
        for (offset in 1 until allSlots.size) {
            val candidate = allSlots[(currentIndex + offset) % allSlots.size]
            if (!candidate.isCompleted) return candidate.id
        }

        return currentSlot.id
    }
}
