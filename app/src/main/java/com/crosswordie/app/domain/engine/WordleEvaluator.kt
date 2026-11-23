package com.crosswordie.app.domain.engine

import com.crosswordie.app.domain.model.EvaluatedTile
import com.crosswordie.app.domain.model.GuessRow
import com.crosswordie.app.domain.model.LetterState

/**
 * Evaluates player guesses against a target word using standard Wordle duplicate-letter frequency rules,
 * while also computing active keyboard letter states scoped to the focused [com.crosswordie.app.domain.model.WordSlot].
 */
object WordleEvaluator {

    /**
     * Evaluates a [guess] of length L against [targetWord] of length L.
     *
     * Rules:
     * 1. First pass marks exact positional matches as [LetterState.CORRECT] (or [LetterState.LOCKED_HINT]
     *    if that position was already pre-filled from an intersection) and decrements the available count
     *    of that character in [targetWord].
     * 2. Second pass marks remaining characters left-to-right: if the character has remaining count > 0
     *    in [targetWord], it is marked [LetterState.PRESENT] and decrements the count; otherwise [LetterState.ABSENT].
     */
    fun evaluateGuess(
        guess: String,
        targetWord: String,
        lockedIndices: Map<Int, Char> = emptyMap()
    ): GuessRow {
        val upperGuess = guess.trim().uppercase()
        val upperTarget = targetWord.trim().uppercase()
        require(upperGuess.length == upperTarget.length) {
            "Guess length (${upperGuess.length}) must match target length (${upperTarget.length})"
        }

        val length = upperTarget.length
        val states = Array(length) { LetterState.ABSENT }
        val remainingTargetCounts = IntArray(26)

        // Pass 1: Exact matches
        for (i in 0 until length) {
            val gChar = upperGuess[i]
            val tChar = upperTarget[i]
            if (gChar == tChar) {
                states[i] = if (lockedIndices[i] == tChar) {
                    LetterState.LOCKED_HINT
                } else {
                    LetterState.CORRECT
                }
            } else {
                val idx = tChar - 'A'
                if (idx in 0..25) {
                    remainingTargetCounts[idx]++
                }
            }
        }

        // Pass 2: Misplaced (Yellow) vs Absent (Gray)
        for (i in 0 until length) {
            if (states[i] == LetterState.CORRECT || states[i] == LetterState.LOCKED_HINT) continue
            val gChar = upperGuess[i]
            val idx = gChar - 'A'
            if (idx in 0..25 && remainingTargetCounts[idx] > 0) {
                states[i] = LetterState.PRESENT
                remainingTargetCounts[idx]--
            } else {
                states[i] = LetterState.ABSENT
            }
        }

        val tiles = (0 until length).map { i ->
            EvaluatedTile(char = upperGuess[i], state = states[i])
        }
        return GuessRow(tiles)
    }

    /**
     * Computes the keyboard key coloring map (A..Z -> [LetterState]) for the currently focused word slot.
     * Priority order: [LetterState.CORRECT] > [LetterState.LOCKED_HINT] > [LetterState.PRESENT] > [LetterState.ABSENT] > [LetterState.UNKNOWN].
     */
    fun computeKeyboardState(
        guesses: List<GuessRow>,
        lockedIndices: Map<Int, Char>
    ): Map<Char, LetterState> {
        val stateMap = HashMap<Char, LetterState>(26)
        for (ch in 'A'..'Z') {
            stateMap[ch] = LetterState.UNKNOWN
        }

        fun upgradeState(ch: Char, candidate: LetterState) {
            val current = stateMap[ch] ?: LetterState.UNKNOWN
            if (priority(candidate) > priority(current)) {
                stateMap[ch] = candidate
            }
        }

        for ((_, lockedChar) in lockedIndices) {
            upgradeState(lockedChar.uppercaseChar(), LetterState.CORRECT)
        }

        for (row in guesses) {
            for (tile in row.tiles) {
                val normalized = if (tile.state == LetterState.LOCKED_HINT) {
                    LetterState.CORRECT
                } else {
                    tile.state
                }
                upgradeState(tile.char.uppercaseChar(), normalized)
            }
        }

        return stateMap
    }

    private fun priority(state: LetterState): Int = when (state) {
        LetterState.UNKNOWN -> 0
        LetterState.ABSENT -> 1
        LetterState.PRESENT -> 2
        LetterState.LOCKED_HINT -> 3
        LetterState.CORRECT -> 4
    }
}
