package com.crosswordie.app.ui.game.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import com.crosswordie.app.domain.model.Direction
import com.crosswordie.app.domain.model.WordSlot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.crosswordie.app.data.repository.PlayerStats
import com.crosswordie.app.domain.model.GameMode
import com.crosswordie.app.domain.model.PuzzleBoardState
import com.crosswordie.app.domain.model.SlotStatus
import com.crosswordie.app.ui.theme.SlateBackground
import com.crosswordie.app.ui.theme.SlateSurface
import com.crosswordie.app.ui.theme.SlateSurfaceElevated
import com.crosswordie.app.ui.theme.SlotActiveBorder
import com.crosswordie.app.ui.theme.TextMuted
import com.crosswordie.app.ui.theme.TextPrimary
import com.crosswordie.app.ui.theme.TextSecondary
import com.crosswordie.app.ui.theme.WordleGreen
import com.crosswordie.app.ui.theme.WordleGreenGlow
import com.crosswordie.app.ui.theme.WordleRevealedAmber
import com.crosswordie.app.ui.theme.WordleYellow

@Composable
fun EndGameSummaryDialog(
    board: PuzzleBoardState,
    stats: PlayerStats,
    shareText: String,
    onShareClicked: (String) -> Unit,
    onNextEndlessClicked: () -> Unit,
    onPlayAnotherDailyClicked: () -> Unit,
    onSwitchToEndlessClicked: () -> Unit,
    onReturnToMenuClicked: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val solvedWords = board.slots.count { it.status == SlotStatus.SOLVED }
        val cascadeSolves = board.slots.count { it.autoSolvedByCascade }
        val totalGuesses = board.slots.sumOf { it.guesses.size }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SlateSurface)
                .border(1.dp, SlateSurfaceElevated, RoundedCornerShape(18.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (board.strikes == 0) "FLAWLESS CROSSWORD!" else "CROSSWORD COMPLETE!",
                color = if (board.strikes == 0) WordleGreenGlow else TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )

            val modeSubtitle = when (board.gameMode) {
                GameMode.DAILY -> "Daily Puzzle • ${board.dailyDateString ?: ""}"
                GameMode.ENDLESS -> "Endless Mode • Level ${board.endlessLevel} Cleared"
            }
            Text(
                text = modeSubtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatMetricBox(label = "SCORE", value = board.score.toString(), accent = SlotActiveBorder)
                StatMetricBox(label = "WORDS", value = "$solvedWords/${board.slots.size}", accent = WordleGreen)
                StatMetricBox(label = "GUESSES", value = totalGuesses.toString(), accent = TextPrimary)
                StatMetricBox(label = "CASCADES", value = cascadeSolves.toString(), accent = WordleYellow)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Emoji Summary Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateBackground)
                    .border(1.dp, SlateSurfaceElevated, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = shareText,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onShareClicked(shareText) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SHARE",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                if (board.gameMode == GameMode.ENDLESS) {
                    Button(
                        onClick = onNextEndlessClicked,
                        colors = ButtonDefaults.buttonColors(containerColor = WordleGreen),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "NEXT LEVEL",
                            color = TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Button(
                        onClick = onPlayAnotherDailyClicked,
                        colors = ButtonDefaults.buttonColors(containerColor = WordleGreen),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "NEW PUZZLE",
                            color = TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Return to Main Menu Button
            OutlinedButton(
                onClick = onReturnToMenuClicked,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = SlotActiveBorder
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RETURN TO MAIN MENU",
                    color = SlotActiveBorder,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = "Inspect Board",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun StatsDialog(
    stats: PlayerStats,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SlateSurface)
                .border(1.dp, SlateSurfaceElevated, RoundedCornerShape(18.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PLAYER STATISTICS",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatMetricBox(label = "DAILY STREAK", value = "🔥 ${stats.currentDailyStreak}", accent = WordleGreen)
                StatMetricBox(label = "BEST STREAK", value = stats.bestDailyStreak.toString(), accent = WordleGreenGlow)
                StatMetricBox(label = "DAILY WINS", value = stats.dailyCompleted.toString(), accent = TextPrimary)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatMetricBox(label = "ENDLESS LVL", value = stats.endlessCurrentLevel.toString(), accent = SlotActiveBorder)
                StatMetricBox(label = "WORDS SOLVED", value = stats.totalWordsSolved.toString(), accent = TextPrimary)
                StatMetricBox(label = "CASCADES", value = "⚡ ${stats.totalCascadeAutoSolves}", accent = WordleYellow)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatMetricBox(label = "PERFECT BOARDS", value = stats.perfectBoards.toString(), accent = WordleGreen)
                StatMetricBox(label = "HIGH SCORE", value = "${stats.highScore} PTS", accent = WordleYellow)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SlotActiveBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "CLOSE",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun HowToPlayDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SlateSurface)
                .border(1.dp, SlateSurfaceElevated, RoundedCornerShape(18.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "HOW TO PLAY CROSSWORDIE",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBullet(
                num = "1",
                title = "Clueless Crossword + Wordle",
                desc = "Tap any Across or Down word in the top crossword grid to focus it in the guess panel below. Tapping an intersection toggles Across / Down."
            )
            Spacer(modifier = Modifier.height(8.dp))

            RuleBullet(
                num = "2",
                title = "5 Guesses Per Word",
                desc = "Deduce the active word in up to 5 guesses. Green = exact spot, Yellow = in word but wrong spot, Gray = not in this word."
            )
            Spacer(modifier = Modifier.height(8.dp))

            RuleBullet(
                num = "3",
                title = "The Cascade Hook 🔒",
                desc = "When you solve a word, it locks into the crossword! Every letter it shares with intersecting unsolved words automatically pre-fills as a locked Green hint."
            )
            Spacer(modifier = Modifier.height(8.dp))

            RuleBullet(
                num = "4",
                title = "Hints, Free Letters & Give Up 💡",
                desc = "Use '3-Word Hint' to see 3 candidate words that fit, 'Free Letter' to unlock a tile, or 'Give Up' to reveal a tough word (0 pts) and unlock its intersections. Solve in fewer tries for massive points—and earn bonus Hints in Endless Mode!"
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = WordleGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "GOT IT, LET'S PLAY!",
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun PauseGameDialog(
    board: PuzzleBoardState,
    onResumeGame: () -> Unit,
    onRestartPuzzle: () -> Unit,
    onReturnToMenu: () -> Unit,
    onQuitApp: () -> Unit
) {
    Dialog(onDismissRequest = onResumeGame) {
        val solvedWords = board.slots.count { it.status == SlotStatus.SOLVED || it.status == SlotStatus.REVEALED_FAILED }
        val modeTitle = when (board.gameMode) {
            GameMode.DAILY -> "Daily Puzzle (${board.difficulty.displayName})"
            GameMode.ENDLESS -> "Endless Level ${board.endlessLevel} (${board.difficulty.displayName})"
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SlateSurface)
                .border(1.dp, SlateSurfaceElevated, RoundedCornerShape(18.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GAME PAUSED",
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Text(
                text = modeTitle,
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatMetricBox(label = "PROGRESS", value = "$solvedWords/${board.slots.size}", accent = WordleGreen)
                StatMetricBox(label = "SCORE", value = "${board.score} PTS", accent = SlotActiveBorder)
                StatMetricBox(label = "STRIKES", value = board.strikes.toString(), accent = WordleRevealedAmber)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 1. Resume Game
            Button(
                onClick = onResumeGame,
                colors = ButtonDefaults.buttonColors(containerColor = WordleGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RESUME GAME",
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Restart / New Puzzle
            OutlinedButton(
                onClick = onRestartPuzzle,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "NEW PUZZLE (${board.difficulty.displayName.uppercase()})",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Main Menu (Change Difficulty / Mode)
            OutlinedButton(
                onClick = onReturnToMenu,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = SlotActiveBorder
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MAIN MENU & DIFFICULTY",
                    color = SlotActiveBorder,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Save & Quit App
            OutlinedButton(
                onClick = onQuitApp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                    contentDescription = null,
                    tint = WordleRevealedAmber
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SAVE & QUIT GAME",
                    color = WordleRevealedAmber,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ThreeWordHintDialog(
    slot: WordSlot,
    hintsRemaining: Int,
    isEndlessMode: Boolean,
    onSelectCandidateWord: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val dirName = if (slot.direction == Direction.ACROSS) "ACROSS" else "DOWN"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SlateSurface)
                .border(1.dp, WordleYellow.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = WordleYellow
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "3 POSSIBLE WORDS",
                    color = WordleYellow,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Text(
                text = "One of these 3 words is the exact answer for ${slot.clueNumber} $dirName! Tap a word to fill your guess row:",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (candidate in slot.hintCandidateWords) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SlateBackground)
                            .border(1.2.dp, SlotActiveBorder, RoundedCornerShape(12.dp))
                            .clickable { onSelectCandidateWord(candidate) }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = candidate.toCharArray().joinToString(" "),
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "TRY →",
                            color = SlotActiveBorder,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val footerNote = if (isEndlessMode) {
                "💡 $hintsRemaining Hints left • Earn +1 Hint on 1st-try solves & Cascades!"
            } else {
                "💡 $hintsRemaining Hints remaining for this puzzle"
            }
            Text(
                text = footerNote,
                color = TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "Close",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun GiveUpWordDialog(
    slot: WordSlot,
    onConfirmGiveUp: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val dirName = if (slot.direction == Direction.ACROSS) "ACROSS" else "DOWN"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SlateSurface)
                .border(1.dp, WordleRevealedAmber.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = WordleRevealedAmber
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GIVE UP ON ${slot.clueNumber} $dirName?",
                    color = WordleRevealedAmber,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "This will reveal ${slot.clueNumber} $dirName for 0 points (+1 Strike) and automatically unlock all of its intersecting letters so you can keep solving the crossword.",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "KEEP TRYING",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onConfirmGiveUp,
                    colors = ButtonDefaults.buttonColors(containerColor = WordleRevealedAmber),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "REVEAL WORD",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatMetricBox(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SlateBackground)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = value,
            color = accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            color = TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RuleBullet(
    num: String,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(SlotActiveBorder)
                .padding(horizontal = 7.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = num,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}
