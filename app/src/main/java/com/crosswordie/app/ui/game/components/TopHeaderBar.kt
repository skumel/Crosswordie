package com.crosswordie.app.ui.game.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosswordie.app.domain.model.DifficultyLevel
import com.crosswordie.app.domain.model.GameMode
import com.crosswordie.app.ui.theme.SlateSurface
import com.crosswordie.app.ui.theme.SlateSurfaceElevated
import com.crosswordie.app.ui.theme.SlotActiveBorder
import com.crosswordie.app.ui.theme.TextMuted
import com.crosswordie.app.ui.theme.TextPrimary
import com.crosswordie.app.ui.theme.TextSecondary
import com.crosswordie.app.ui.theme.WordleGreen
import com.crosswordie.app.ui.theme.WordleRevealedAmber

@Composable
fun TopHeaderBar(
    gameMode: GameMode,
    difficulty: DifficultyLevel,
    dailyDateString: String?,
    endlessLevel: Int,
    score: Int,
    hintsRemaining: Int,
    strikes: Int,
    dailyStreak: Int,
    isBoardComplete: Boolean,
    onPauseClick: () -> Unit,
    onSwitchMode: (GameMode) -> Unit,
    onShowStats: () -> Unit,
    onShowHowToPlay: () -> Unit,
    onShowSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Pause / Menu Button + Brand Title & Difficulty Sub-badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onPauseClick,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(SlateSurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "Pause / Menu",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "CROSSWORDIE",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.9.sp
                    )
                    if (dailyStreak > 0 && gameMode == GameMode.DAILY) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(WordleGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "🔥$dailyStreak",
                                color = WordleGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                val subtitle = when (gameMode) {
                    GameMode.DAILY -> "${difficulty.displayName} • Daily ${dailyDateString ?: ""}"
                    GameMode.ENDLESS -> "${difficulty.displayName} • Endless Lvl $endlessLevel"
                }
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Center: Mode Segmented Pill (Daily | Endless)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SlateSurface)
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeTabPill(
                label = "Daily",
                selected = gameMode == GameMode.DAILY,
                onClick = { onSwitchMode(GameMode.DAILY) }
            )
            ModeTabPill(
                label = "Endless",
                selected = gameMode == GameMode.ENDLESS,
                onClick = { onSwitchMode(GameMode.ENDLESS) }
            )
        }

        // Right: Score & Action Icons
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 2.dp)
            ) {
                Text(
                    text = "$score PTS",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (strikes > 0) {
                    Text(
                        text = "💡$hintsRemaining • $strikes Strike${if (strikes > 1) "s" else ""}",
                        color = WordleRevealedAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "💡 $hintsRemaining Hint${if (hintsRemaining == 1) "" else "s"}",
                        color = com.crosswordie.app.ui.theme.WordleYellow,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (isBoardComplete) {
                IconButton(
                    onClick = onShowSummary,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EmojiEvents,
                        contentDescription = "Puzzle Summary",
                        tint = WordleGreen,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            IconButton(
                onClick = onShowStats,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.BarChart,
                    contentDescription = "Statistics",
                    tint = TextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = onShowHowToPlay,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = "How to Play",
                    tint = TextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (selected) SlotActiveBorder else SlateSurface
    val textColor = if (selected) TextPrimary else TextSecondary
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
