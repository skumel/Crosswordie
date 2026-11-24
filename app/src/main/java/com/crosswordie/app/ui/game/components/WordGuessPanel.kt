package com.crosswordie.app.ui.game.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.crosswordie.app.domain.model.Direction
import com.crosswordie.app.domain.model.EvaluatedTile
import com.crosswordie.app.domain.model.LetterState
import com.crosswordie.app.domain.model.SlotStatus
import com.crosswordie.app.domain.model.WordSlot
import com.crosswordie.app.ui.theme.CellCursorBorder
import com.crosswordie.app.ui.theme.SlateSurface
import com.crosswordie.app.ui.theme.SlateSurfaceElevated
import com.crosswordie.app.ui.theme.SlotActiveBorder
import com.crosswordie.app.ui.theme.SlotActiveFill
import com.crosswordie.app.ui.theme.TextMuted
import com.crosswordie.app.ui.theme.TextPrimary
import com.crosswordie.app.ui.theme.TextSecondary
import com.crosswordie.app.ui.theme.WordleGray
import com.crosswordie.app.ui.theme.WordleGreen
import com.crosswordie.app.ui.theme.WordleGreenGlow
import com.crosswordie.app.ui.theme.WordleGreenLocked
import com.crosswordie.app.ui.theme.WordleRevealedAmber
import com.crosswordie.app.ui.theme.WordleYellow
import kotlinx.coroutines.delay

@Composable
fun WordGuessPanel(
    activeSlot: WordSlot,
    currentInputMap: Map<Int, Char>,
    cursorIndex: Int,
    bannerMessage: String?,
    invalidGuessShakeTrigger: Int,
    lastRevealedRowIndex: Int,
    onTileTapped: (Int) -> Unit,
    onPrevSlot: () -> Unit,
    onNextSlot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(invalidGuessShakeTrigger) {
        if (invalidGuessShakeTrigger > 0) {
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 360
                    (-14f) at 50
                    14f at 110
                    (-10f) at 170
                    10f at 230
                    (-5f) at 290
                    0f at 360
                }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Active Word Header Bar with Prev / Next Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SlateSurface)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPrevSlot,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous Word",
                    tint = TextSecondary
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                val dirLabel = if (activeSlot.direction == Direction.ACROSS) "ACROSS" else "DOWN"
                val statusText = when {
                    activeSlot.autoSolvedByCascade -> "⚡ CASCADE AUTO-SOLVED (${activeSlot.targetWord})"
                    activeSlot.status == SlotStatus.SOLVED -> "✓ SOLVED: ${activeSlot.targetWord}"
                    activeSlot.status == SlotStatus.REVEALED_FAILED -> "REVEALED: ${activeSlot.targetWord} (0 PTS)"
                    else -> {
                        val hintsCount = activeSlot.lockedIndices.size
                        val hintSuffix = if (hintsCount > 0) " • $hintsCount LOCKED HINT${if (hintsCount > 1) "S" else ""}" else ""
                        "${activeSlot.clueNumber} $dirLabel • ${activeSlot.length} LETTERS$hintSuffix"
                    }
                }
                val titleColor = when (activeSlot.status) {
                    SlotStatus.SOLVED -> WordleGreenGlow
                    SlotStatus.REVEALED_FAILED -> WordleRevealedAmber
                    SlotStatus.IN_PROGRESS -> TextPrimary
                }

                Text(
                    text = statusText,
                    color = titleColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (bannerMessage != null) {
                    Text(
                        text = bannerMessage,
                        color = WordleYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onNextSlot,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next Word",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Dynamic 5-Row x L-Column Wordle Guess Matrix
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val wordLength = activeSlot.length
            val maxGuesses = WordSlot.MAX_GUESSES
            val rowSpacing = 4.dp
            val colSpacing = 6.dp

            val maxTileHeight = (maxHeight - rowSpacing * (maxGuesses - 1)) / maxGuesses
            val maxTileWidth = (maxWidth - colSpacing * (wordLength - 1)) / wordLength
            val tileSize: Dp = min(min(maxTileHeight, maxTileWidth), 48.dp).coerceAtLeast(24.dp)

            val activeRowIndex = activeSlot.guesses.size

            Column(
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (rowIdx in 0 until maxGuesses) {
                    val isCurrentInputRow = (rowIdx == activeRowIndex && !activeSlot.isCompleted)
                    val rowOffset = if (isCurrentInputRow) shakeOffset.value else 0f

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(colSpacing),
                        modifier = Modifier.graphicsLayer {
                            translationX = rowOffset
                        }
                    ) {
                        val submittedRow = activeSlot.guesses.getOrNull(rowIdx)
                        val shouldFlipAnimate = (rowIdx == lastRevealedRowIndex && submittedRow != null)

                        for (colIdx in 0 until wordLength) {
                            when {
                                submittedRow != null -> {
                                    val tile = submittedRow.tiles[colIdx]
                                    SubmittedGuessTile(
                                        slotId = activeSlot.id,
                                        rowIdx = rowIdx,
                                        colIdx = colIdx,
                                        tile = tile,
                                        tileSize = tileSize,
                                        animateFlip = shouldFlipAnimate
                                    )
                                }

                                isCurrentInputRow -> {
                                    val lockedHintChar = activeSlot.lockedIndices[colIdx]
                                    val isLockedHint = lockedHintChar != null
                                    val ch = lockedHintChar ?: currentInputMap[colIdx]
                                    val isCursor = (colIdx == cursorIndex && !isLockedHint)

                                    ActiveInputTile(
                                        char = ch,
                                        isLockedHint = isLockedHint,
                                        isCursor = isCursor,
                                        tileSize = tileSize,
                                        onClick = { onTileTapped(colIdx) }
                                    )
                                }

                                else -> {
                                    // Future empty row (or completed word remaining rows):
                                    // Show subtle locked hint ghost letters if slot is still in progress
                                    val ghostLockedChar = if (!activeSlot.isCompleted) {
                                        activeSlot.lockedIndices[colIdx]
                                    } else null

                                    FutureGuessTile(
                                        ghostLockedChar = ghostLockedChar,
                                        tileSize = tileSize
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmittedGuessTile(
    slotId: String,
    rowIdx: Int,
    colIdx: Int,
    tile: EvaluatedTile,
    tileSize: Dp,
    animateFlip: Boolean
) {
    val rotationX = remember(slotId, rowIdx, colIdx) { Animatable(0f) }

    LaunchedEffect(slotId, rowIdx, colIdx, animateFlip) {
        if (animateFlip) {
            rotationX.snapTo(0f)
            delay(colIdx * 75L)
            rotationX.animateTo(
                targetValue = 180f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
        } else {
            rotationX.snapTo(180f)
        }
    }

    val isRevealedFace = rotationX.value >= 90f
    val bgColor = if (!isRevealedFace) {
        SlateSurfaceElevated
    } else {
        when (tile.state) {
            LetterState.CORRECT -> WordleGreen
            LetterState.LOCKED_HINT -> WordleGreenLocked
            LetterState.PRESENT -> WordleYellow
            LetterState.ABSENT -> WordleGray
            LetterState.UNKNOWN -> SlateSurfaceElevated
        }
    }

    val borderColor = if (tile.state == LetterState.LOCKED_HINT && isRevealedFace) {
        WordleGreenGlow
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(tileSize)
            .graphicsLayer {
                this.rotationX = if (isRevealedFace) 180f - rotationX.value else rotationX.value
                cameraDistance = 10f * density
            }
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tile.char.toString(),
            color = TextPrimary,
            fontSize = (tileSize.value * 0.44f).coerceIn(13f, 20f).sp,
            fontWeight = FontWeight.ExtraBold
        )
        if (tile.state == LetterState.LOCKED_HINT && isRevealedFace) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked Intersection",
                tint = TextPrimary.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size((tileSize.value * 0.22f).coerceIn(8f, 11f).dp)
            )
        }
    }
}

@Composable
private fun ActiveInputTile(
    char: Char?,
    isLockedHint: Boolean,
    isCursor: Boolean,
    tileSize: Dp,
    onClick: () -> Unit
) {
    val bgColor = when {
        isLockedHint -> WordleGreenLocked.copy(alpha = 0.88f)
        isCursor -> SlotActiveFill
        char != null -> SlateSurfaceElevated
        else -> SlateSurface
    }

    val borderColor = when {
        isLockedHint -> WordleGreenGlow
        isCursor -> CellCursorBorder
        char != null -> SlotActiveBorder
        else -> SlateSurfaceElevated
    }

    val borderWidth = if (isCursor || isLockedHint) 2.dp else 1.2.dp

    Box(
        modifier = Modifier
            .size(tileSize)
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(7.dp))
            .clickable(enabled = !isLockedHint, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (char != null) {
            Text(
                text = char.toString(),
                color = TextPrimary,
                fontSize = (tileSize.value * 0.44f).coerceIn(13f, 20f).sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        if (isLockedHint) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Pre-filled Intersection Hint",
                tint = TextPrimary.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size((tileSize.value * 0.22f).coerceIn(8f, 11f).dp)
            )
        }
    }
}

@Composable
private fun FutureGuessTile(
    ghostLockedChar: Char?,
    tileSize: Dp
) {
    val hasGhost = ghostLockedChar != null
    val bgColor = if (hasGhost) {
        WordleGreenLocked.copy(alpha = 0.22f)
    } else {
        SlateSurface.copy(alpha = 0.55f)
    }
    val borderColor = if (hasGhost) {
        WordleGreen.copy(alpha = 0.45f)
    } else {
        SlateSurfaceElevated.copy(alpha = 0.7f)
    }

    Box(
        modifier = Modifier
            .size(tileSize)
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (ghostLockedChar != null) {
            Text(
                text = ghostLockedChar.toString(),
                color = WordleGreenGlow.copy(alpha = 0.55f),
                fontSize = (tileSize.value * 0.38f).coerceIn(11f, 17f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
