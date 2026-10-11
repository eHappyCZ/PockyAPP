package com.pokecio.app.ui.rooms

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pokecio.app.ui.theme.PockyColors
import kotlin.math.abs
import kotlin.math.roundToInt

class SwipeAction(val icon: ImageVector, val label: String, val color: Color, val run: () -> Unit)

@Composable
fun SwipeRoom(
    start: SwipeAction?,
    end: SwipeAction?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 88.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }

    val state = rememberDraggableState { delta ->
        val min = if (end != null) -threshold * 1.3f else 0f
        val max = if (start != null) threshold * 1.3f else 0f
        drag = (drag + delta).coerceIn(min, max)
        val reached = abs(drag) >= threshold
        if (reached && !armed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        armed = reached
    }
    val active = if (drag > 0) start else end
    val progress = (abs(drag) / threshold).coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxWidth()
            .draggable(
                state = state,
                orientation = Orientation.Horizontal,
                enabled = start != null || end != null,
                onDragStopped = {
                    if (armed) (if (drag > 0) start else end)?.run?.invoke()
                    armed = false
                    animate(drag, 0f) { value, _ -> drag = value }
                },
            ),
    ) {
        if (active != null && drag != 0f) {
            Row(
                horizontalArrangement = if (drag > 0) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .matchParentSize()
                    .background(active.color.copy(alpha = 0.18f + 0.5f * progress))
                    .padding(horizontal = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(progress)) {
                    Icon(active.icon, contentDescription = null, tint = PockyColors.Ink, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        active.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = PockyColors.Ink,
                    )
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(drag.roundToInt(), 0) }
                .background(PockyColors.Bg),
        ) {
            content()
        }
    }
}
