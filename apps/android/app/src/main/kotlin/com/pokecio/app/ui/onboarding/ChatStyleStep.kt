package com.pokecio.app.ui.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.ChatLook
import com.pokecio.app.ui.components.ControlShape
import com.pokecio.app.ui.room.AuraBackground
import com.pokecio.app.ui.room.bubbleShape
import com.pokecio.app.ui.room.bubbleSurface
import com.pokecio.app.ui.theme.PockyColors

@Composable
fun ChatStyleStep(selected: ChatLook, onSelect: (ChatLook) -> Unit) {
    ChatPreview(selected)
    Spacer(Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceCard(
            title = stringResource(R.string.onboarding_style_classic),
            body = stringResource(R.string.onboarding_style_classic_body),
            selected = selected == ChatLook.CLASSIC,
            onClick = { onSelect(ChatLook.CLASSIC) },
        )
        ChoiceCard(
            title = stringResource(R.string.onboarding_style_aura),
            body = stringResource(R.string.onboarding_style_aura_body),
            selected = selected == ChatLook.AURA,
            onClick = { onSelect(ChatLook.AURA) },
        )
    }
}

@Composable
private fun ChatPreview(look: ChatLook) {
    Crossfade(
        targetState = look,
        label = "chat-preview",
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(ControlShape)
            .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.12f), ControlShape),
    ) { current ->
        Box(Modifier.fillMaxSize()) {
            if (current == ChatLook.AURA) {
                AuraBackground(Modifier.matchParentSize())
            } else {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(PockyColors.Bg),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
            ) {
                PreviewBubble(stringResource(R.string.onboarding_style_sample_in), own = false, look = current)
                PreviewBubble(stringResource(R.string.onboarding_style_sample_out), own = true, look = current)
                PreviewBubble(stringResource(R.string.onboarding_style_sample_reply), own = false, look = current)
            }
        }
    }
}

@Composable
private fun PreviewBubble(text: String, own: Boolean, look: ChatLook) {
    Row(
        horizontalArrangement = if (own) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = PockyColors.Ink,
            modifier = Modifier
                .widthIn(max = 220.dp)
                .bubbleSurface(own, bubbleShape(own, first = true, last = true), look)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
