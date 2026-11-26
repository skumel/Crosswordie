package com.crosswordie.app.ui.game

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.crosswordie.app.domain.model.GameMode
import com.crosswordie.app.ui.game.components.CrosswordGridView
import com.crosswordie.app.ui.game.components.EndGameSummaryDialog
import com.crosswordie.app.ui.game.components.GameKeyboard
import com.crosswordie.app.ui.game.components.GiveUpWordDialog
import com.crosswordie.app.ui.game.components.HowToPlayDialog
import com.crosswordie.app.ui.game.components.OpeningMenuScreen
import com.crosswordie.app.ui.game.components.PauseGameDialog
import com.crosswordie.app.ui.game.components.StatsDialog
import com.crosswordie.app.ui.game.components.ThreeWordHintDialog
import com.crosswordie.app.ui.game.components.TopHeaderBar
import com.crosswordie.app.ui.game.components.WordGuessPanel
import com.crosswordie.app.ui.theme.SlateBackground
import com.crosswordie.app.ui.theme.SlateSurfaceElevated
import com.crosswordie.app.ui.theme.SlotActiveBorder

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    // When playing a puzzle, pressing system Back opens the Pause dialog
    BackHandler(enabled = !state.showOpeningMenu) {
        if (state.showPauseDialog) {
            viewModel.togglePauseDialog(false)
        } else {
            viewModel.togglePauseDialog(true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .safeDrawingPadding()
    ) {
        val board = state.board
        val activeSlot = state.activeSlot

        if (state.isLoading) {
            CircularProgressIndicator(
                color = SlotActiveBorder,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (state.showOpeningMenu) {
            OpeningMenuScreen(
                selectedDifficulty = state.selectedDifficulty,
                activeBoard = board,
                stats = state.stats,
                onSelectDifficulty = { diff -> viewModel.selectDifficulty(diff) },
                onResumeCurrentGame = { viewModel.resumeCurrentGame() },
                onStartDailyGame = { viewModel.startGameFromMenu(GameMode.DAILY) },
                onStartEndlessGame = { viewModel.startGameFromMenu(GameMode.ENDLESS) },
                onShowStats = { viewModel.toggleStatsSheet(true) },
                onShowHowToPlay = { viewModel.toggleHowToPlayDialog(true) },
                onQuitApp = { activity?.finish() }
            )
        } else if (board != null && activeSlot != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Header Bar (Pause Button, Difficulty & Mode Switcher, Streak, Score, Stats & Help)
                TopHeaderBar(
                    gameMode = board.gameMode,
                    difficulty = board.difficulty,
                    dailyDateString = board.dailyDateString,
                    endlessLevel = board.endlessLevel,
                    score = board.score,
                    hintsRemaining = board.hintsRemaining,
                    strikes = board.strikes,
                    dailyStreak = state.stats.currentDailyStreak,
                    isBoardComplete = board.isBoardComplete,
                    onPauseClick = { viewModel.togglePauseDialog(true) },
                    onSwitchMode = { mode -> viewModel.switchMode(mode) },
                    onShowStats = { viewModel.toggleStatsSheet(true) },
                    onShowHowToPlay = { viewModel.toggleHowToPlayDialog(true) },
                    onShowSummary = { viewModel.showEndGameSummary() }
                )

                HorizontalDivider(
                    color = SlateSurfaceElevated,
                    thickness = 1.dp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // 2. Top Zone (~38% weight): Interactive Crossword Grid + Quick Slot Pills
                CrosswordGridView(
                    board = board,
                    activeSlot = activeSlot,
                    currentInputMap = state.currentInputMap,
                    cursorIndex = state.cursorIndex,
                    newlyUnlockedCoords = state.newlyUnlockedCoords,
                    onCellTapped = { coord -> viewModel.onGridCellTapped(coord) },
                    onSelectSlot = { slotId -> viewModel.selectWordSlot(slotId) },
                    modifier = Modifier
                        .weight(0.39f)
                        .fillMaxWidth()
                )

                // 3. Middle Zone (~39% weight): Dynamic 3-6 Letter Wordle Guess Matrix + Assist Bar
                WordGuessPanel(
                    activeSlot = activeSlot,
                    hintsRemaining = board.hintsRemaining,
                    currentInputMap = state.currentInputMap,
                    cursorIndex = state.cursorIndex,
                    bannerMessage = state.bannerMessage,
                    invalidGuessShakeTrigger = state.invalidGuessShakeTrigger,
                    lastRevealedRowIndex = state.lastRevealedRowIndex,
                    onTileTapped = { colIdx -> viewModel.onGuessTileTapped(colIdx) },
                    onPrevSlot = { viewModel.cycleWordSlot(forward = false) },
                    onNextSlot = { viewModel.cycleWordSlot(forward = true) },
                    onThreeWordHintClick = { viewModel.onThreeWordHintClicked() },
                    onFreeLetterHintClick = { viewModel.onFreeLetterHintClicked() },
                    onGiveUpWordClick = { viewModel.toggleGiveUpConfirmDialog(true) },
                    modifier = Modifier
                        .weight(0.39f)
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                )

                // 4. Bottom Zone: Context-Aware Custom QWERTY Keyboard (Backspace on Left, Green Guess on Right)
                val isSubmitReady = !activeSlot.isCompleted &&
                    (state.currentInputMap.keys + activeSlot.lockedIndices.keys).size == activeSlot.length

                GameKeyboard(
                    keyboardState = state.keyboardState,
                    isSubmitReady = isSubmitReady,
                    onLetterClick = { ch -> viewModel.onLetterTyped(ch) },
                    onBackspaceClick = { viewModel.onBackspacePressed() },
                    onEnterClick = { viewModel.onSubmitGuess() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                )
            }
        }

        // Shared Dialogs (Accessible from both Opening Menu and In-Game)
        if (state.showHintOptionsDialog && board != null && activeSlot != null) {
            ThreeWordHintDialog(
                slot = activeSlot,
                hintsRemaining = board.hintsRemaining,
                isEndlessMode = board.gameMode == GameMode.ENDLESS,
                onSelectCandidateWord = { chosenWord -> viewModel.onSelectHintCandidateWord(chosenWord) },
                onDismiss = { viewModel.dismissHintOptionsDialog() }
            )
        }

        if (state.showGiveUpConfirmDialog && activeSlot != null) {
            GiveUpWordDialog(
                slot = activeSlot,
                onConfirmGiveUp = { viewModel.onConfirmGiveUpWord() },
                onDismiss = { viewModel.toggleGiveUpConfirmDialog(false) }
            )
        }

        if (state.showPauseDialog && board != null) {
            PauseGameDialog(
                board = board,
                onResumeGame = { viewModel.togglePauseDialog(false) },
                onRestartPuzzle = { viewModel.restartCurrentPuzzle() },
                onReturnToMenu = { viewModel.returnToOpeningMenu() },
                onQuitApp = {
                    viewModel.returnToOpeningMenu()
                    activity?.finish()
                }
            )
        }

        if (state.showEndGameDialog && board != null) {
            EndGameSummaryDialog(
                board = board,
                stats = state.stats,
                shareText = state.shareSummaryText,
                onShareClicked = { shareBody ->
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareBody)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Crosswordie Result"))
                },
                onNextEndlessClicked = {
                    viewModel.dismissEndGameDialog()
                    viewModel.startNextEndlessPuzzle()
                },
                onPlayAnotherDailyClicked = {
                    viewModel.dismissEndGameDialog()
                    viewModel.restartCurrentPuzzle()
                },
                onSwitchToEndlessClicked = {
                    viewModel.dismissEndGameDialog()
                    viewModel.switchMode(GameMode.ENDLESS)
                },
                onReturnToMenuClicked = {
                    viewModel.returnToOpeningMenu()
                },
                onDismiss = { viewModel.dismissEndGameDialog() }
            )
        }

        if (state.showStatsSheet) {
            StatsDialog(
                stats = state.stats,
                onDismiss = { viewModel.toggleStatsSheet(false) }
            )
        }

        if (state.showHowToPlayDialog) {
            HowToPlayDialog(
                onDismiss = { viewModel.toggleHowToPlayDialog(false) }
            )
        }
    }
}
