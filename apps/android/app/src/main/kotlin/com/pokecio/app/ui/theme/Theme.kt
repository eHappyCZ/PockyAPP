package com.pokecio.app.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object Appearance {
    private const val STORE = "appearance"
    private const val KEY_THEME = "theme"

    var theme by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun load(context: Context) {
        val saved = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(KEY_THEME, null)
        theme = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
    }

    fun select(context: Context, mode: ThemeMode) {
        theme = mode
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString(KEY_THEME, mode.name).apply()
    }
}

@Composable
fun useDarkTheme(): Boolean = when (Appearance.theme) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PockyTheme(content: @Composable () -> Unit) {
    val dark = useDarkTheme()
    val palette = if (dark) PockyColors.Dark else PockyColors.Light
    remember(palette) {
        PockyColors.palette = palette
        palette
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = PockyColors.OnBrand,
            primaryContainer = palette.bubbleOut,
            onPrimaryContainer = palette.ink,
            background = palette.bg,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            onSurfaceVariant = palette.inkMuted,
            tertiary = palette.success,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = PockyColors.OnBrand,
            primaryContainer = palette.bubbleOut,
            onPrimaryContainer = palette.ink,
            background = palette.bg,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            onSurfaceVariant = palette.inkMuted,
            tertiary = palette.success,
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
