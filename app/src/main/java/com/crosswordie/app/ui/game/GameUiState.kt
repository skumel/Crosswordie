package com.crosswordie.app.ui.game

import com.crosswordie.app.data.repository.PlayerStats
import com.crosswordie.app.domain.model.DifficultyLevel
import com.crosswordie.app.domain.model.GameMode
import com.crosswordie.app.domain.model.GridCoord
import com.crosswordie.app.domain.model.LetterState
import com.crosswordie.app.domain.model.PuzzleBoardState
import com.crosswordie.app.domain.model.WordSlot

/**
 * Represents the immutable UI state of the Crosswordie app (Opening Menu + Active Game Screen).
 */
data class GameUiState(
    val showOpeningMenu: Boolean = true,
    val selectedDifficulty: DifficultyLevel = DifficultyLevel.MEDIUM,
    val isLoading: Boolean = true,
    val board: PuzzleBoardState? = null,
    val hasSavedDailyInProgress: Boolean = false,
    val hasSavedEndlessInProgress: Boolean = false,
    val currentInputMap: Map<Int, Char> = emptyMap(), // Active row typed letters (including pre-filled locked hints)
    val cursorIndex: Int = 0,                         // Active input column (0 until activeSlot.length)
    val keyboardState: Map<Char, LetterState> = emptyMap(),
    val bannerMessage: String? = null,
    val invalidGuessShakeTrigger: Int = 0,
    val lastRevealedRowIndex: Int = -1,
    val newlyUnlockedCoords: Set<GridCoord> = emptySet(),
    val stats: PlayerStats = PlayerStats(),
    val showPauseDialog: Boolean = false,
    val showStatsSheet: Boolean = false,
    val showHowToPlayDialog: Boolean = false,
    val showEndGameDialog: Boolean = false,
    val shareSummaryText: String = ""
) {
    val activeSlot: WordSlot?
        get() = board?.activeSlot

    val gameMode: GameMode
        get() = board?.gameMode ?: GameMode.DAILY
}
