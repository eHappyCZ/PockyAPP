package com.pokecio.app.ui.layout

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.window.core.layout.WindowSizeClass

enum class ScreenSize {
    COMPACT,
    MEDIUM,
    EXPANDED,
    LARGE,
    ;

    val twoPane: Boolean get() = this != COMPACT
    val threePane: Boolean get() = this == LARGE
}

private const val LARGE_WIDTH_DP = 1240

val LocalScreenSize = staticCompositionLocalOf { ScreenSize.COMPACT }

@Composable
fun currentScreenSize(): ScreenSize {
    val sizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val widthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }
    return when {
        widthDp >= LARGE_WIDTH_DP -> ScreenSize.LARGE
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> ScreenSize.EXPANDED
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> ScreenSize.MEDIUM
        else -> ScreenSize.COMPACT
    }
}
