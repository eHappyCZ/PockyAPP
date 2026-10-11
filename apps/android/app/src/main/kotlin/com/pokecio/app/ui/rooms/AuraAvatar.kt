package com.pokecio.app.ui.rooms

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pokecio.app.core.PresenceStatus
import com.pokecio.app.ui.theme.PockyColors

enum class AvatarAura { NONE, ONLINE, AWAY, TYPING, OWN }

fun auraOf(presence: PresenceStatus?, typing: Boolean): AvatarAura = when {
    typing -> AvatarAura.TYPING
    presence == PresenceStatus.ONLINE -> AvatarAura.ONLINE
    presence == PresenceStatus.UNAVAILABLE -> AvatarAura.AWAY
    else -> AvatarAura.NONE
}

@Composable
fun AuraAvatar(
    id: String,
    name: String,
    url: String? = null,
    size: Dp = 48.dp,
    aura: AvatarAura = AvatarAura.NONE,
    rounded: Boolean = false,
    onlineDot: Boolean = false,
) {
    Box(Modifier.auraRing(aura, rounded)) {
        RoomAvatar(id = id, name = name, url = url, size = size, rounded = rounded)
        if (onlineDot) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.28f)
                    .background(PockyColors.Bg, CircleShape)
                    .padding(size * 0.05f)
                    .background(PockyColors.Success, CircleShape),
            )
        }
    }
}

@Composable
private fun Modifier.auraRing(aura: AvatarAura, rounded: Boolean): Modifier {
    if (aura == AvatarAura.NONE) return this

    val transition = rememberInfiniteTransition(label = "avatar-aura")
    val breath by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )

    return drawBehind {
        val stroke = 2.5.dp.toPx()
        val gap = 2.dp.toPx() + stroke / 2
        val center = Offset(size.width / 2, size.height / 2)
        val outline = Size(size.width + gap * 2, size.height + gap * 2)
        val topLeft = Offset(-gap, -gap)
        val corner = if (rounded) CornerRadius(size.minDimension * ROUNDED_CORNER + gap) else CornerRadius(outline.minDimension / 2)

        fun ring(brush: Brush, width: Float, alpha: Float = 1f) = drawRoundRect(
            brush = brush,
            topLeft = topLeft,
            size = outline,
            cornerRadius = corner,
            alpha = alpha,
            style = Stroke(width, cap = StrokeCap.Round),
        )

        when (aura) {
            AvatarAura.ONLINE -> {
                ring(SolidColor(PockyColors.Success), stroke * 2, alpha = 0.18f * breath)
                ring(SolidColor(PockyColors.Success), stroke, alpha = breath)
            }
            AvatarAura.AWAY -> ring(SolidColor(PockyColors.Gold), stroke, alpha = 0.55f)
            AvatarAura.TYPING -> {
                val shader = SweepGradient(center.x, center.y, SWEEP_COLORS, SWEEP_STOPS)
                shader.setLocalMatrix(Matrix().apply { setRotate(spin, center.x, center.y) })
                val sweep = ShaderBrush(shader)
                ring(SolidColor(PockyColors.AccentSoft), stroke, alpha = 0.12f)
                ring(sweep, stroke * 3, alpha = 0.3f)
                ring(sweep, stroke)
            }
            AvatarAura.OWN -> {
                val shader = SweepGradient(center.x, center.y, OWN_COLORS, null)
                shader.setLocalMatrix(Matrix().apply { setRotate(drift, center.x, center.y) })
                val sweep = ShaderBrush(shader)
                ring(sweep, stroke * 3, alpha = 0.18f + 0.12f * breath)
                ring(sweep, stroke)
            }
            AvatarAura.NONE -> Unit
        }
    }
}

private val SWEEP_COLORS = intArrayOf(
    Color.Transparent.toArgb(),
    PockyColors.Accent.toArgb(),
    PockyColors.AccentSoft.toArgb(),
    Color.Transparent.toArgb(),
)
private val SWEEP_STOPS = floatArrayOf(0f, 0.3f, 0.75f, 0.76f)

private val OWN_COLORS = intArrayOf(
    PockyColors.GradientStart.toArgb(),
    PockyColors.AccentSoft.toArgb(),
    PockyColors.GradientEnd.toArgb(),
    PockyColors.Accent.toArgb(),
    PockyColors.GradientStart.toArgb(),
)
