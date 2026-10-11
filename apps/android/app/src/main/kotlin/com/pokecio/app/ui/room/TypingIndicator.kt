package com.pokecio.app.ui.room

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.PresenceStatus
import com.pokecio.app.core.Typist
import com.pokecio.app.ui.rooms.typingText
import com.pokecio.app.ui.theme.PockyColors

@Composable
fun HeaderStatus(typists: List<Typist>, isDirect: Boolean, presence: PresenceStatus?) {
    val (text, color) = when {
        typists.isNotEmpty() -> typingText(typists, isDirect) to PockyColors.AccentSoft
        presence == PresenceStatus.ONLINE -> stringResource(R.string.presence_online) to PockyColors.Success
        presence == PresenceStatus.UNAVAILABLE -> stringResource(R.string.presence_away) to PockyColors.Gold
        else -> return
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(PockyColors.Surface, CircleShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        repeat(3) { index ->
            val lift by transition.animateFloat(
                initialValue = 0f,
                targetValue = -4f,
                animationSpec = infiniteRepeatable(
                    tween(400, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                    StartOffset(index * 140),
                ),
                label = "dot",
            )
            Box(
                Modifier
                    .offset(y = lift.dp)
                    .size(6.dp)
                    .background(PockyColors.AccentSoft, CircleShape),
            )
        }
    }
}
