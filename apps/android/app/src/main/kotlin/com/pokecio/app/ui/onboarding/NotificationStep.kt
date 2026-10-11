package com.pokecio.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.NotificationText
import com.pokecio.app.ui.components.ControlShape
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

@Composable
fun NotificationStep(selected: NotificationText, onSelect: (NotificationText) -> Unit) {
    LockScreenPreview(selected)
    Spacer(Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceCard(
            title = stringResource(R.string.onboarding_notifications_always),
            body = stringResource(R.string.onboarding_notifications_always_body),
            selected = selected == NotificationText.ALWAYS,
            onClick = { onSelect(NotificationText.ALWAYS) },
        )
        ChoiceCard(
            title = stringResource(R.string.onboarding_notifications_unlocked),
            body = stringResource(R.string.onboarding_notifications_unlocked_body),
            badge = stringResource(R.string.onboarding_recommended),
            selected = selected == NotificationText.WHEN_UNLOCKED,
            onClick = { onSelect(NotificationText.WHEN_UNLOCKED) },
        )
        ChoiceCard(
            title = stringResource(R.string.onboarding_notifications_never),
            body = stringResource(R.string.onboarding_notifications_never_body),
            selected = selected == NotificationText.NEVER,
            onClick = { onSelect(NotificationText.NEVER) },
        )
    }
}

@Composable
private fun LockScreenPreview(preview: NotificationText) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .background(
                Brush.verticalGradient(
                    listOf(PockyColors.GradientStart.copy(alpha = 0.35f), PockyColors.Bg),
                ),
            )
            .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.12f), ControlShape)
            .padding(16.dp),
    ) {
        Icon(PockyIcons.Lock, contentDescription = null, tint = PockyColors.Ink, modifier = Modifier.size(16.dp))
        Text(
            text = "9:41",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Light,
            color = PockyColors.Ink,
        )
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = preview,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "notification",
        ) { current ->
            val (title, body) = when (current) {
                NotificationText.ALWAYS -> stringResource(R.string.onboarding_sample_sender) to
                    stringResource(R.string.onboarding_style_sample_in)
                NotificationText.WHEN_UNLOCKED -> stringResource(R.string.onboarding_sample_sender) to
                    stringResource(R.string.onboarding_notifications_hidden)
                NotificationText.NEVER -> stringResource(R.string.app_name) to
                    stringResource(R.string.onboarding_notifications_new)
            }
            NotificationCard(title, body)
        }
    }
}

@Composable
private fun NotificationCard(title: String, body: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(PockyColors.Surface.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
            .padding(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(PockyGradient, CircleShape),
        ) {
            Text("P", fontWeight = FontWeight.Bold, color = PockyColors.Ink)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PockyColors.Ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.onboarding_notifications_now),
                    style = MaterialTheme.typography.labelSmall,
                    color = PockyColors.InkMuted,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = PockyColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
