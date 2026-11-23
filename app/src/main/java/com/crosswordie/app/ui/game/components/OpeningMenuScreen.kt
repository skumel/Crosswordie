package com.crosswordie.app.ui.game.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosswordie.app.data.repository.PlayerStats
import com.crosswordie.app.domain.model.DifficultyLevel
import com.crosswordie.app.domain.model.GameMode
import com.crosswordie.app.domain.model.PuzzleBoardState
import com.crosswordie.app.domain.model.SlotStatus
import com.crosswordie.app.ui.theme.SlateBackground
import com.crosswordie.app.ui.theme.SlateSurface
import com.crosswordie.app.ui.theme.SlateSurfaceElevated
import com.crosswordie.app.ui.theme.SlotActiveBorder
import com.crosswordie.app.ui.theme.SlotActiveFill
import com.crosswordie.app.ui.theme.TextMuted
import com.crosswordie.app.ui.theme.TextPrimary
import com.crosswordie.app.ui.theme.TextSecondary
import com.crosswordie.app.ui.theme.WordleGreen
import com.crosswordie.app.ui.theme.WordleGreenGlow
import com.crosswordie.app.ui.theme.WordleRevealedAmber
import com.crosswordie.app.ui.theme.WordleYellow

@Composable
fun OpeningMenuScreen(
    selectedDifficulty: DifficultyLevel,
    activeBoard: PuzzleBoardState?,
    stats: PlayerStats,
    onSelectDifficulty: (DifficultyLevel) -> Unit,
    onResumeCurrentGame: () -> Unit,
    onStartDailyGame: () -> Unit,
    onStartEndlessGame: () -> Unit,
    onShowStats: () -> Unit,
    onShowHowToPlay: () -> Unit,
    onQuitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Logo & Tagline Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            // Decorative Mini Crossword Tile Logo
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LogoTile(char = 'C', bg = WordleGreen)
                LogoTile(char = 'R', bg = SlotActiveBorder)
                LogoTile(char = 'O', bg = WordleYellow)
                LogoTile(char = 'S', bg = WordleGreen)
                LogoTile(char = 'S', bg = SlateSurfaceElevated)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "CROSSWORDIE",
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            Text(
                text = "Clueless Crossword × Wordle Deduction",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            // Quick Stats Strip
            if (stats.totalWordsSolved > 0 || stats.currentDailyStreak > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateSurface)
                        .border(1.dp, SlateSurfaceElevated, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔥 Streak: ${stats.currentDailyStreak}",
                        color = WordleGreenGlow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Endless Lvl: ${stats.endlessCurrentLevel}",
                        color = SlotActiveBorder,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Words: ${stats.totalWordsSolved}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Resume In-Progress Banner (if an unfinished puzzle is currently paused)
        if (activeBoard != null && !activeBoard.isBoardComplete) {
            val solvedCount = activeBoard.slots.count { it.status == SlotStatus.SOLVED || it.status == SlotStatus.REVEALED_FAILED }
            val modeLabel = if (activeBoard.gameMode == GameMode.DAILY) "Daily Puzzle" else "Endless Level ${activeBoard.endlessLevel}"

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SlotActiveFill)
                    .border(1.5.dp, SlotActiveBorder, RoundedCornerShape(16.dp))
                    .clickable(onClick = onResumeCurrentGame)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PAUSED GAME IN PROGRESS",
                            color = SlotActiveBorder,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "$modeLabel • ${activeBoard.difficulty.displayName}",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "$solvedCount / ${activeBoard.slots.size} Words Completed • ${activeBoard.score} PTS",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = onResumeCurrentGame,
                        colors = ButtonDefaults.buttonColors(containerColor = WordleGreen)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "RESUME",
                            color = TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 3. Difficulty Selector Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "CHOOSE DIFFICULTY LEVEL",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )

            for (difficulty in DifficultyLevel.entries) {
                DifficultyOptionCard(
                    difficulty = difficulty,
                    isSelected = difficulty == selectedDifficulty,
                    onClick = { onSelectDifficulty(difficulty) }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. Primary Play Mode Buttons (Daily Puzzle & Endless Mode)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onStartDailyGame,
                colors = ButtonDefaults.buttonColors(containerColor = WordleGreen),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PLAY DAILY PUZZLE (${selectedDifficulty.displayName.uppercase()})",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Button(
                onClick = onStartEndlessGame,
                colors = ButtonDefaults.buttonColors(containerColor = SlotActiveBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PLAY ENDLESS MODE (${selectedDifficulty.displayName.uppercase()})",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Bottom Utility Row: Statistics, How to Play, Quit Game
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onShowStats,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.BarChart,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "STATS",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onShowHowToPlay,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "RULES",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onQuitApp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                    contentDescription = null,
                    tint = WordleRevealedAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "QUIT",
                    color = WordleRevealedAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DifficultyOptionCard(
    difficulty: DifficultyLevel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = when (difficulty) {
        DifficultyLevel.EASY -> WordleGreen
        DifficultyLevel.MEDIUM -> SlotActiveBorder
        DifficultyLevel.HARD -> WordleRevealedAmber
    }

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.18f) else SlateSurface,
        animationSpec = tween(160),
        label = "diffBg_${difficulty.name}"
    )

    val borderColor = if (isSelected) accentColor else SlateSurfaceElevated
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = difficulty.displayName.uppercase(),
                    color = if (isSelected) accentColor else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                val gridBadge = if (difficulty.prefer5x5) "5×5 GRID" else "6×6 GRID"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SlateSurfaceElevated)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = gridBadge,
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = difficulty.description,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, TextMuted, RoundedCornerShape(10.dp))
            )
        }
    }
}

@Composable
private fun LogoTile(
    char: Char,
    bg: Color
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = char.toString(),
            color = TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
