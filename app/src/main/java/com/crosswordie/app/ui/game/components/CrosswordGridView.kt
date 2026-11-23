package com.crosswordie.app.ui.game.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.crosswordie.app.domain.model.GridCell
import com.crosswordie.app.domain.model.GridCoord
import com.crosswordie.app.domain.model.PuzzleBoardState
import com.crosswordie.app.domain.model.SlotStatus
import com.crosswordie.app.domain.model.WordSlot
import com.crosswordie.app.ui.theme.CellCursorBorder
import com.crosswordie.app.ui.theme.SlateBoardVoid
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

@Composable
fun CrosswordGridView(
    board: PuzzleBoardState,
    activeSlot: WordSlot?,
    currentInputMap: Map<Int, Char>,
    cursorIndex: Int,
    newlyUnlockedCoords: Set<GridCoord>,
    onCellTapped: (GridCoord) -> Unit,
    onSelectSlot: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSlotCoords = remember(activeSlot) { activeSlot?.coords?.toSet() ?: emptySet() }
    val activeCursorCoord = remember(activeSlot, cursorIndex) {
        activeSlot?.coords?.getOrNull(cursorIndex)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Responsive Square Grid Area
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            val gridSize = board.gridSize
            val spacing = 4.dp
            val maxBoardSide = min(maxWidth, maxHeight)
            val cellSize: Dp = ((maxBoardSide - spacing * (gridSize + 1)) / gridSize)
                .coerceIn(28.dp, 54.dp)

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateBoardVoid)
                    .border(1.dp, SlateSurfaceElevated, RoundedCornerShape(12.dp))
                    .padding(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                for (r in 0 until gridSize) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        for (c in 0 until gridSize) {
                            val coord = GridCoord(r, c)
                            val cell = board.cellAt(coord)
                            val isInActiveSlot = coord in activeSlotCoords
                            val isCursorCell = coord == activeCursorCoord
                            val idxInActiveSlot = activeSlot?.indexOfCoord(coord) ?: -1
                            val typedPreviewChar = if (isInActiveSlot && idxInActiveSlot >= 0) {
                                currentInputMap[idxInActiveSlot]
                            } else null

                            CrosswordCellBox(
                                cell = cell,
                                cellSize = cellSize,
                                isInActiveSlot = isInActiveSlot,
                                isCursorCell = isCursorCell,
                                typedPreviewChar = typedPreviewChar,
                                isNewlyUnlocked = coord in newlyUnlockedCoords,
                                onClick = {
                                    if (cell?.isPlayable == true) {
                                        onCellTapped(coord)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Quick Word Slot Pills Bar (1A, 2D, 3A...)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (slot in board.slots) {
                val isSelected = slot.id == activeSlot?.id
                val pillBg = when {
                    isSelected -> SlotActiveBorder
                    slot.status == SlotStatus.SOLVED -> WordleGreen.copy(alpha = 0.28f)
                    slot.status == SlotStatus.REVEALED_FAILED -> WordleRevealedAmber.copy(alpha = 0.28f)
                    slot.lockedIndices.isNotEmpty() -> SlateSurfaceElevated
                    else -> SlateSurface
                }
                val pillBorder = when {
                    isSelected -> TextPrimary
                    slot.status == SlotStatus.SOLVED -> WordleGreen
                    slot.status == SlotStatus.REVEALED_FAILED -> WordleRevealedAmber
                    slot.lockedIndices.isNotEmpty() -> WordleGreen.copy(alpha = 0.6f)
                    else -> Color.Transparent
                }
                val hintCount = slot.lockedIndices.size

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(pillBg)
                        .border(1.dp, pillBorder, RoundedCornerShape(14.dp))
                        .clickable { onSelectSlot(slot.id) }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusSuffix = when {
                        slot.status == SlotStatus.SOLVED -> " ✓"
                        slot.status == SlotStatus.REVEALED_FAILED -> " !"
                        hintCount > 0 -> " •$hintCount"
                        else -> ""
                    }
                    Text(
                        text = "${slot.id}$statusSuffix",
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CrosswordCellBox(
    cell: GridCell?,
    cellSize: Dp,
    isInActiveSlot: Boolean,
    isCursorCell: Boolean,
    typedPreviewChar: Char?,
    isNewlyUnlocked: Boolean,
    onClick: () -> Unit
) {
    if (cell == null || !cell.isPlayable) {
        Box(
            modifier = Modifier
                .size(cellSize)
                .clip(RoundedCornerShape(6.dp))
                .background(SlateBoardVoid)
        )
        return
    }

    val isLocked = cell.lockedChar != null
    val pulseScale = remember { Animatable(1f) }

    LaunchedEffect(isNewlyUnlocked, cell.lockedChar) {
        if (isNewlyUnlocked && isLocked) {
            pulseScale.snapTo(1f)
            pulseScale.animateTo(
                targetValue = 1.16f,
                animationSpec = tween(durationMillis = 140)
            )
            pulseScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val targetBgColor = when {
        cell.isRevealedByFailure -> WordleRevealedAmber.copy(alpha = if (isInActiveSlot) 0.9f else 0.72f)
        isLocked -> if (isInActiveSlot) WordleGreenGlow.copy(alpha = 0.85f) else WordleGreen
        isInActiveSlot -> SlotActiveFill
        else -> SlateSurfaceElevated
    }

    val bgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(180),
        label = "cellBg"
    )

    val borderColor = when {
        isCursorCell -> CellCursorBorder
        isInActiveSlot -> SlotActiveBorder
        isLocked -> WordleGreenGlow.copy(alpha = 0.6f)
        else -> SlateSurface
    }
    val borderWidth = when {
        isCursorCell -> 2.5.dp
        isInActiveSlot -> 1.8.dp
        else -> 1.dp
    }

    val displayedChar = cell.lockedChar ?: typedPreviewChar
    val charColor = when {
        cell.lockedChar != null -> TextPrimary
        typedPreviewChar != null -> SlotActiveBorder
        else -> TextPrimary
    }

    Box(
        modifier = Modifier
            .size(cellSize)
            .graphicsLayer {
                scaleX = pulseScale.value
                scaleY = pulseScale.value
            }
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
    ) {
        // Mini Clue Number in top-left corner
        if (cell.clueNumber != null) {
            Text(
                text = cell.clueNumber.toString(),
                color = if (isLocked) TextPrimary.copy(alpha = 0.85f) else TextMuted,
                fontSize = (cellSize.value * 0.21f).coerceIn(8f, 11f).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 3.dp, top = 1.dp)
            )
        }

        // Main Character (Locked solution or live typed preview)
        if (displayedChar != null) {
            Text(
                text = displayedChar.toString(),
                color = charColor,
                fontSize = (cellSize.value * 0.44f).coerceIn(14f, 22f).sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
