package com.pokecio.app.ui.login

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pokecio.app.ui.theme.PockyColors

private const val GLYPH_WIDTH = 255f
private const val GLYPH_HEIGHT = 320f
private const val BOWL_HEIGHT = 215f
private const val STEM_WIDTH = 85f

@Composable
fun PockyLogo(modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val transition = rememberInfiniteTransition(label = "logo")

    val glow by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "glow",
    )
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val dots = List(3) { transition.dotBounce(it) }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        PockyColors.BubbleOut.copy(alpha = 0.6f * glow),
                        PockyColors.BubbleOut.copy(alpha = 0.2f * glow),
                        Color.Transparent,
                    ),
                    radius = this.size.minDimension / 2f,
                ),
            )
        }
        Canvas(
            Modifier
                .size(size * 0.33f, size * 0.33f * GLYPH_HEIGHT / GLYPH_WIDTH)
                .graphicsLayer {
                    scaleX = breath
                    scaleY = breath
                },
        ) {
            drawGlyph(dots.map { it.value })
        }
    }
}

@Composable
private fun InfiniteTransition.dotBounce(index: Int) = animateFloat(
    initialValue = 0f,
    targetValue = 0f,
    animationSpec = infiniteRepeatable(
        animation = keyframes {
            durationMillis = 1400
            0f at 0
            1f at 280 using FastOutSlowInEasing
            0f at 560
        },
        initialStartOffset = StartOffset(index * 180),
    ),
    label = "dot$index",
)

private fun glyphPath(): Path = Path().apply {
    val cornerTopLeft = 55f
    val bowlRadius = BOWL_HEIGHT / 2f
    val stemRadius = STEM_WIDTH / 2f
    val fillet = 22f

    moveTo(0f, cornerTopLeft)
    arcTo(Rect(0f, 0f, cornerTopLeft * 2, cornerTopLeft * 2), 180f, 90f, false)
    lineTo(GLYPH_WIDTH - bowlRadius, 0f)
    arcTo(Rect(GLYPH_WIDTH - bowlRadius * 2, 0f, GLYPH_WIDTH, BOWL_HEIGHT), -90f, 180f, false)
    lineTo(STEM_WIDTH + fillet, BOWL_HEIGHT)
    quadraticTo(STEM_WIDTH, BOWL_HEIGHT, STEM_WIDTH, BOWL_HEIGHT + fillet)
    lineTo(STEM_WIDTH, GLYPH_HEIGHT - stemRadius)
    arcTo(Rect(0f, GLYPH_HEIGHT - STEM_WIDTH, STEM_WIDTH, GLYPH_HEIGHT), 0f, 180f, false)
    close()
}

private fun DrawScope.drawGlyph(lift: List<Float>) {
    val factor = size.width / GLYPH_WIDTH
    val glyph = glyphPath()

    scale(factor, pivot = Offset.Zero) {
        drawPath(
            path = glyph,
            brush = Brush.verticalGradient(
                colors = listOf(PockyColors.LogoLight, PockyColors.LogoDeep),
                startY = 0f,
                endY = GLYPH_HEIGHT,
            ),
        )
        clipPath(glyph) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(PockyColors.Ink.copy(alpha = 0.22f), Color.Transparent),
                    startY = 0f,
                    endY = BOWL_HEIGHT * 0.45f,
                ),
                size = Size(GLYPH_WIDTH, BOWL_HEIGHT * 0.45f),
            )
        }
        drawPath(
            path = glyph,
            color = PockyColors.LogoLight.copy(alpha = 0.7f),
            style = Stroke(width = 3f),
        )

        val radius = 20f
        lift.forEachIndexed { i, value ->
            drawCircle(
                color = PockyColors.Ink.copy(alpha = 0.8f + 0.2f * value),
                radius = radius,
                center = Offset(130f + (i - 1) * 56f, BOWL_HEIGHT / 2f - value * 10f),
            )
        }
    }
}
