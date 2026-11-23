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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosswordie.app.domain.model.LetterState
import com.crosswordie.app.ui.theme.KeyActionBg
import com.crosswordie.app.ui.theme.KeyDefaultBg
import com.crosswordie.app.ui.theme.TextMuted
import com.crosswordie.app.ui.theme.TextPrimary
import com.crosswordie.app.ui.theme.WordleGray
import com.crosswordie.app.ui.theme.WordleGreen
import com.crosswordie.app.ui.theme.WordleGreenGlow
import com.crosswordie.app.ui.theme.WordleGreenLocked
import com.crosswordie.app.ui.theme.WordleYellow

private val ROW_1 = listOf('Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P')
private val ROW_2 = listOf('A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L')
private val ROW_3 = listOf('Z', 'X', 'C', 'V', 'B', 'N', 'M')

@Composable
fun GameKeyboard(
    keyboardState: Map<Char, LetterState>,
    isSubmitReady: Boolean,
    onLetterClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onEnterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1: Q - P
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (ch in ROW_1) {
                LetterKey(
                    char = ch,
                    state = keyboardState[ch] ?: LetterState.UNKNOWN,
                    onClick = { onLetterClick(ch) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Row 2: A - L (slightly inset)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (ch in ROW_2) {
                LetterKey(
                    char = ch,
                    state = keyboardState[ch] ?: LetterState.UNKNOWN,
                    onClick = { onLetterClick(ch) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Row 3: BACKSPACE (Left) + Z - M + GREEN GUESS (Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // BACKSPACE Key on the LEFT side
            Box(
                modifier = Modifier
                    .weight(1.45f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(KeyActionBg)
                    .clickable(onClick = onBackspaceClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = "Backspace",
                    tint = TextPrimary
                )
            }

            for (ch in ROW_3) {
                LetterKey(
                    char = ch,
                    state = keyboardState[ch] ?: LetterState.UNKNOWN,
                    onClick = { onLetterClick(ch) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Green GUESS Key on the RIGHT side
            val enterBg by animateColorAsState(
                targetValue = if (isSubmitReady) WordleGreen else WordleGreenLocked.copy(alpha = 0.72f),
                animationSpec = tween(160),
                label = "enterBg"
            )
            val enterBorder = if (isSubmitReady) WordleGreenGlow else WordleGreen.copy(alpha = 0.5f)
            Box(
                modifier = Modifier
                    .weight(1.55f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(enterBg)
                    .border(1.2.dp, enterBorder, RoundedCornerShape(7.dp))
                    .clickable(onClick = onEnterClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "GUESS",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun LetterKey(
    char: Char,
    state: LetterState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetBg = when (state) {
        LetterState.CORRECT, LetterState.LOCKED_HINT -> WordleGreen
        LetterState.PRESENT -> WordleYellow
        LetterState.ABSENT -> WordleGray.copy(alpha = 0.55f)
        LetterState.UNKNOWN -> KeyDefaultBg
    }

    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(180),
        label = "keyBg_$char"
    )

    val textColor = if (state == LetterState.ABSENT) TextMuted else TextPrimary

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = char.toString(),
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
