package com.pokecio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PockyColorScheme = darkColorScheme(
    primary = PockyColors.Accent,
    onPrimary = PockyColors.Ink,
    primaryContainer = PockyColors.BubbleOut,
    onPrimaryContainer = PockyColors.Ink,
    background = PockyColors.Bg,
    onBackground = PockyColors.Ink,
    surface = PockyColors.Surface,
    onSurface = PockyColors.Ink,
    onSurfaceVariant = PockyColors.InkMuted,
    tertiary = PockyColors.Success,
)

@Composable
fun PockyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PockyColorScheme,
        content = content,
    )
}
