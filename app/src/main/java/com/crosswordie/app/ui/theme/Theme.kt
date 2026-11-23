package com.crosswordie.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CrosswordieColorScheme = darkColorScheme(
    primary = SlotActiveBorder,
    secondary = WordleGreen,
    tertiary = WordleYellow,
    background = SlateBackground,
    surface = SlateSurface,
    surfaceVariant = SlateSurfaceElevated,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun CrosswordieTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CrosswordieColorScheme,
        content = content
    )
}
