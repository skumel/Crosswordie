package com.crosswordie.app.domain.engine

import com.crosswordie.app.domain.model.GameMode
import com.crosswordie.app.domain.model.PuzzleBoardState
import com.crosswordie.app.domain.model.SlotStatus

/**
 * Builds a spoiler-free, Wordle/Crossword-style emoji grid summary for sharing completed puzzles.
 */
object ShareSummaryBuilder {

    fun buildShareText(board: PuzzleBoardState): String {
        val sb = StringBuilder()
        val headerTitle = when (board.gameMode) {
            GameMode.DAILY -> "Crosswordie Daily ${board.dailyDateString ?: ""}".trim()
            GameMode.ENDLESS -> "Crosswordie Endless • Level ${board.endlessLevel}"
        }

        val solvedCount = board.slots.count { it.status == SlotStatus.SOLVED }
        val totalSlots = board.slots.size
        val totalGuesses = board.slots.sumOf { it.guesses.size }

        sb.appendLine("$headerTitle 🟩 $solvedCount/$totalSlots Words ($totalGuesses Guesses)")
        sb.appendLine()

        // Mini Crossword Board Emoji Map
        for (r in 0 until board.gridSize) {
            val rowStr = StringBuilder()
            for (c in 0 until board.gridSize) {
                val cell = board.cellAt(r, c)
                val emoji = when {
                    cell == null || !cell.isPlayable -> "⬛"
                    cell.isRevealedByFailure -> "🟨"
                    cell.lockedChar != null -> "🟩"
                    else -> "⬜"
                }
                rowStr.append(emoji)
            }
            sb.appendLine(rowStr.toString())
        }

        sb.appendLine()
        // Per-slot compact summary
        val slotBadges = board.slots.joinToString("  ") { slot ->
            when {
                slot.autoSolvedByCascade -> "${slot.id}:⚡"
                slot.status == SlotStatus.SOLVED -> "${slot.id}:🟩${slot.guesses.size}/5"
                slot.status == SlotStatus.REVEALED_FAILED -> "${slot.id}:🟥X/5"
                else -> "${slot.id}:⬜"
            }
        }
        sb.append(slotBadges)

        return sb.toString()
    }
}
