package com.crosswordie.app.domain.engine

import com.crosswordie.app.domain.model.GridCoord
import com.crosswordie.app.domain.model.PuzzleBoardState
import com.crosswordie.app.domain.model.SlotStatus
import com.crosswordie.app.domain.model.WordSlot

/**
 * Manages the core Crosswordie gameplay loop:
 * 1. Submitting a guess for the active [WordSlot].
 * 2. Locking solved words into the crossword grid.
 * 3. Handling the 5-guess failure condition by revealing the word with 0 points (+1 strike)
 *    so the player is never soft-locked.
 * 4. Propagating locked intersecting characters to perpendicular unsolved words (`lockedIndices`),
 *    including recursive cascade auto-solves when all letters of a perpendicular word are unlocked!
 */
object CascadeEngine {

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
        val pointsEarned: Int = 0
    )

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

        // Also lock any newly discovered GREEN letters within this active slot itself so subsequent guesses keep them
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

        val slotsById = board.slots.associateBy { it.id }.toMutableMap()
        slotsById[updatedActiveSlot.id] = updatedActiveSlot

        // If the slot is still IN_PROGRESS, just update the slot's guess list
        if (newStatus == SlotStatus.IN_PROGRESS) {
            val nextBoard = board.copy(
                slots = board.slots.map { slotsById.getValue(it.id) }
            )
            return GuessSubmissionResult(
                updatedBoard = nextBoard,
                outcome = GuessOutcome.CONTINUE_SLOT
            )
        }

        // The slot was either SOLVED or REVEALED_FAILED -> trigger the Cascade Hook!
        val basePoints = if (newStatus == SlotStatus.SOLVED) {
            // More points for solving in fewer guesses (1st try = 500, 2nd = 400, ..., 5th = 100)
            (WordSlot.MAX_GUESSES - updatedGuesses.size + 1) * 100
        } else {
            0
        }
        val addedStrikes = if (newStatus == SlotStatus.REVEALED_FAILED) 1 else 0

        val newlyUnlockedCoords = LinkedHashSet<GridCoord>()
        val cascadedSlotIds = mutableListOf<String>()
        val failedRevealedCoords = HashSet<GridCoord>()

        if (newStatus == SlotStatus.REVEALED_FAILED) {
            // Only mark coords that weren't already locked by a solved perpendicular word
            val previouslyLockedCoords = board.cells.filter { it.lockedChar != null }.map { it.coord }.toSet()
            for (c in updatedActiveSlot.coords) {
                if (c !in previouslyLockedCoords) {
                    failedRevealedCoords.add(c)
                }
            }
        }

        // Queue of completed slots whose letters need to cascade to perpendicular slots
        val cascadeQueue = ArrayDeque<WordSlot>()
        cascadeQueue.add(updatedActiveSlot)

        while (cascadeQueue.isNotEmpty()) {
            val completedSlot = cascadeQueue.removeFirst()
            for ((charIdx, coord) in completedSlot.coords.withIndex()) {
                val solvedChar = completedSlot.targetWord[charIdx]
                newlyUnlockedCoords.add(coord)

                // Find any perpendicular slot that passes through this coordinate
                for ((otherId, otherSlot) in slotsById.entries) {
                    if (otherId == completedSlot.id || otherSlot.isCompleted) continue
                    val idxInOther = otherSlot.indexOfCoord(coord)
                    if (idxInOther >= 0 && otherSlot.lockedIndices[idxInOther] != solvedChar) {
                        val updatedLocks = HashMap(otherSlot.lockedIndices)
                        updatedLocks[idxInOther] = solvedChar

                        // Check if all letters of otherSlot are now locked via intersections!
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

        val cascadeBonusPoints = cascadedSlotIds.size * 350
        val totalEarnedPoints = basePoints + cascadeBonusPoints

        // Update GridCells with locked characters
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

        val updatedSlots = board.slots.map { slotsById.getValue(it.id) }
        val allCompleted = updatedSlots.all { it.isCompleted }

        // Automatically advance focus to the next unsolved slot (preferring an intersecting unsolved slot!)
        val nextActiveSlotId = if (allCompleted) {
            updatedActiveSlot.id
        } else {
            selectNextActiveSlotId(
                currentSlot = updatedActiveSlot,
                allSlots = updatedSlots
            )
        }

        val finalBoard = board.copy(
            cells = updatedCells,
            slots = updatedSlots,
            activeSlotId = nextActiveSlotId,
            strikes = board.strikes + addedStrikes,
            score = board.score + totalEarnedPoints,
            isBoardComplete = allCompleted
        )

        val finalOutcome = when {
            allCompleted -> GuessOutcome.BOARD_COMPLETED
            newStatus == SlotStatus.SOLVED -> GuessOutcome.SLOT_SOLVED
            else -> GuessOutcome.SLOT_REVEALED_FAILED
        }

        return GuessSubmissionResult(
            updatedBoard = finalBoard,
            outcome = finalOutcome,
            solvedOrRevealedSlotId = updatedActiveSlot.id,
            cascadedAutoSolvedSlotIds = cascadedSlotIds,
            newlyUnlockedCoords = newlyUnlockedCoords,
            pointsEarned = totalEarnedPoints
        )
    }

    /**
     * Selects the next unsolved [WordSlot] after [currentSlot] completes.
     * Prioritizes unsolved slots that intersect with [currentSlot] (so the player immediately sees
     * their new green cascade hint!), falling back to the next unsolved slot in board order.
     */
    fun selectNextActiveSlotId(
        currentSlot: WordSlot,
        allSlots: List<WordSlot>
    ): String {
        val currentCoords = currentSlot.coords.toSet()

        // 1. Check unsolved slots that intersect with currentSlot
        val intersectingUnsolved = allSlots.firstOrNull { candidate ->
            !candidate.isCompleted &&
                candidate.id != currentSlot.id &&
                candidate.coords.any { it in currentCoords }
        }
        if (intersectingUnsolved != null) return intersectingUnsolved.id

        // 2. Otherwise cycle forward from currentSlot's position in the slot list
        val currentIndex = allSlots.indexOfFirst { it.id == currentSlot.id }.coerceAtLeast(0)
        for (offset in 1 until allSlots.size) {
            val candidate = allSlots[(currentIndex + offset) % allSlots.size]
            if (!candidate.isCompleted) return candidate.id
        }

        return currentSlot.id
    }
}
