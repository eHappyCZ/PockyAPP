package com.pokecio.app.ui.room

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pokecio.app.core.ChatLook
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.theme.PockyColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val LocalChatLook = staticCompositionLocalOf { ChatLook.CLASSIC }

fun Modifier.bubbleSurface(own: Boolean, shape: Shape, look: ChatLook): Modifier = when {
    look == ChatLook.AURA && own && PockyColors.IsLight -> background(PockyColors.BubbleOut, shape)
    look == ChatLook.AURA && own -> this
        .shadow(8.dp, shape, ambientColor = PockyColors.Accent, spotColor = PockyColors.Accent)
        .background(PockyGradient, shape)
    look == ChatLook.AURA -> this
        .background(PockyColors.Surface.copy(alpha = 0.78f), shape)
        .border(1.dp, PockyColors.AccentSoft.copy(alpha = 0.14f), shape)
    own -> background(PockyColors.BubbleOut, shape)
    else -> background(PockyColors.Surface, shape)
}

fun bubbleShape(own: Boolean, first: Boolean, last: Boolean): Shape = BubbleShape(own, first, last)

private class BubbleShape(val own: Boolean, val first: Boolean, val last: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val big = with(density) { 18.dp.toPx() }
        val small = with(density) { 5.dp.toPx() }
        val tail = with(density) { 7.dp.toPx() }
        val topNear = if (first) big else small

        val rect = RoundRect(
            left = 0f,
            top = 0f,
            right = size.width,
            bottom = size.height,
            topLeftCornerRadius = CornerRadius(if (own) big else topNear),
            topRightCornerRadius = CornerRadius(if (own) topNear else big),
            bottomRightCornerRadius = CornerRadius(if (own) small else big),
            bottomLeftCornerRadius = CornerRadius(if (own) big else small),
        )
        val path = Path().apply { addRoundRect(rect) }
        if (!last) return Outline.Generic(path)

        val h = size.height
        val tailPath = Path().apply {
            if (own) {
                val x = size.width
                moveTo(x - small, h - big)
                lineTo(x - small, h)
                lineTo(x + tail, h)
                quadraticTo(x - 1f, h - small, x - 1f, h - big)
                close()
            } else {
                moveTo(small, h - big)
                lineTo(small, h)
                lineTo(-tail, h)
                quadraticTo(1f, h - small, 1f, h - big)
                close()
            }
        }
        return Outline.Generic(Path.combine(PathOperation.Union, path, tailPath))
    }
}

@Composable
fun AuraBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aura")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )

    val strength = if (PockyColors.IsLight) 0.4f else 1f
    Canvas(modifier.background(PockyColors.Bg)) {
        val angle = phase * 2 * PI.toFloat()
        glow(PockyColors.GradientStart, 0.30f + 0.12f * cos(angle), 0.25f + 0.08f * sin(angle), 0.75f, 0.45f * strength)
        glow(PockyColors.GradientEnd, 0.75f + 0.10f * sin(angle * 2), 0.55f + 0.10f * cos(angle), 0.7f, 0.35f * strength)
        glow(PockyColors.Accent, 0.40f + 0.15f * sin(angle + 1f), 0.85f + 0.05f * cos(angle * 2), 0.6f, 0.25f * strength)
    }
}

private fun DrawScope.glow(color: Color, x: Float, y: Float, radius: Float, alpha: Float) {
    val center = Offset(size.width * x, size.height * y)
    val r = size.maxDimension * radius
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, r),
        radius = r,
        center = center,
    )
}
