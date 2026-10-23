package com.pokecio.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

class Palette(
    val bg: Color,
    val surface: Color,
    val accent: Color,
    val accentSoft: Color,
    val bubbleOut: Color,
    val ink: Color,
    val inkMuted: Color,
    val success: Color,
    val danger: Color,
    val gold: Color,
    val light: Boolean,
)

object PockyColors {
    val Dark = Palette(
        bg = Color(0xFF08070C),
        surface = Color(0xFF121019),
        accent = Color(0xFF8B5CF6),
        accentSoft = Color(0xFFB8A1FB),
        bubbleOut = Color(0xFF7C3AED),
        ink = Color(0xFFF4F2F9),
        inkMuted = Color(0xFFA4A0B3),
        success = Color(0xFF34D399),
        danger = Color(0xFFEF4444),
        gold = Color(0xFFF3CD66),
        light = false,
    )

    val Light = Palette(
        bg = Color(0xFFF6F4FB),
        surface = Color(0xFFFFFFFF),
        accent = Color(0xFF7C3AED),
        accentSoft = Color(0xFF6D45D6),
        bubbleOut = Color(0xFFE6DAFB),
        ink = Color(0xFF17141F),
        inkMuted = Color(0xFF6B6679),
        success = Color(0xFF059669),
        danger = Color(0xFFDC2626),
        gold = Color(0xFFB7871A),
        light = true,
    )

    var palette by mutableStateOf(Dark)

    val Bg get() = palette.bg
    val Surface get() = palette.surface
    val Accent get() = palette.accent
    val AccentSoft get() = palette.accentSoft
    val BubbleOut get() = palette.bubbleOut
    val Ink get() = palette.ink
    val InkMuted get() = palette.inkMuted
    val Success get() = palette.success
    val Danger get() = palette.danger
    val Gold get() = palette.gold
    val IsLight get() = palette.light

    val OnBrand = Color(0xFFF4F2F9)
    val LogoLight = Color(0xFFDCCFF7)
    val LogoDeep = Color(0xFFA466DE)
    val GradientStart = Color(0xFF4340A0)
    val GradientEnd = Color(0xFF8E52A6)
}
