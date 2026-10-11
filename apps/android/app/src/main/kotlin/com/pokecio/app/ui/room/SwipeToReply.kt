package com.pokecio.app.ui.room

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import kotlin.math.roundToInt

@Composable
fun SwipeToReply(enabled: Boolean, onReply: () -> Unit, content: @Composable () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }

    val state = rememberDraggableState { delta ->
        drag = (drag + delta).coerceIn(0f, threshold * 1.4f)
        val reached = drag >= threshold
        if (reached && !armed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        armed = reached
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = state,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStopped = {
                    if (armed) onReply()
                    armed = false
                    animate(drag, 0f) { value, _ -> drag = value }
                },
            ),
    ) {
        Icon(
            imageVector = PockyIcons.Reply,
            contentDescription = null,
            tint = PockyColors.AccentSoft,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .size(22.dp)
                .alpha((drag / threshold).coerceIn(0f, 1f)),
        )
        Box(Modifier.offset { IntOffset(drag.roundToInt(), 0) }) {
            content()
        }
    }
}
