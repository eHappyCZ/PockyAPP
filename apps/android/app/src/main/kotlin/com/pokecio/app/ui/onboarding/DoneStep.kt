package com.pokecio.app.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.ChatLook
import com.pokecio.app.core.DevicePreferences
import com.pokecio.app.core.EncryptionInfo
import com.pokecio.app.core.LocationArea
import com.pokecio.app.core.NotificationText
import com.pokecio.app.ui.components.ControlShape
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

@Composable
fun DoneStep(
    preferences: DevicePreferences,
    encryption: EncryptionInfo?,
    onEdit: (OnboardingStep) -> Unit,
) {
    val rows = listOf(
        SummaryRow(
            OnboardingStep.UNLOCK,
            PockyIcons.Lock,
            R.string.onboarding_summary_history,
            if (encryption?.deviceVerified == true || encryption?.hasIdentity == false) {
                R.string.onboarding_summary_unlocked
            } else {
                R.string.onboarding_summary_locked
            },
        ),
        SummaryRow(
            OnboardingStep.CHAT_STYLE,
            PockyIcons.Smile,
            R.string.onboarding_summary_style,
            when (preferences.chatStyle) {
                ChatLook.CLASSIC -> R.string.onboarding_style_classic
                ChatLook.AURA -> R.string.onboarding_style_aura
            },
        ),
        SummaryRow(
            OnboardingStep.NOTIFICATIONS,
            PockyIcons.Bell,
            R.string.onboarding_summary_notifications,
            when (preferences.notificationPreview) {
                NotificationText.ALWAYS -> R.string.onboarding_notifications_always
                NotificationText.WHEN_UNLOCKED -> R.string.onboarding_notifications_unlocked
                NotificationText.NEVER -> R.string.onboarding_notifications_never
            },
        ),
        SummaryRow(
            OnboardingStep.LOCATION,
            PockyIcons.Location,
            R.string.onboarding_summary_location,
            when (preferences.locationPrecision) {
                LocationArea.EXACT -> R.string.onboarding_location_exact
                LocationArea.NEIGHBORHOOD -> R.string.onboarding_location_neighborhood
                LocationArea.CITY -> R.string.onboarding_location_city
            },
        ),
        SummaryRow(
            OnboardingStep.VISIBILITY,
            PockyIcons.User,
            R.string.onboarding_summary_visibility,
            when {
                preferences.sharePresence && preferences.shareTyping -> R.string.onboarding_summary_visibility_all
                preferences.sharePresence -> R.string.onboarding_summary_visibility_presence
                preferences.shareTyping -> R.string.onboarding_summary_visibility_typing
                else -> R.string.onboarding_summary_visibility_none
            },
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PockyColors.Surface, ControlShape)
            .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.12f), ControlShape),
    ) {
        rows.forEachIndexed { position, row ->
            if (position > 0) {
                HorizontalDivider(
                    color = PockyColors.InkMuted.copy(alpha = 0.1f),
                    modifier = Modifier.padding(start = 74.dp),
                )
            }
            Summary(row, onEdit)
        }
    }
}

private class SummaryRow(
    val step: OnboardingStep,
    val icon: ImageVector,
    @StringRes val label: Int,
    @StringRes val value: Int,
)

@Composable
private fun Summary(row: SummaryRow, onEdit: (OnboardingStep) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onEdit(row.step) }
            .padding(16.dp),
    ) {
        IconBubble(row.icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(row.label),
                style = MaterialTheme.typography.labelMedium,
                color = PockyColors.InkMuted,
            )
            Text(
                text = stringResource(row.value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PockyColors.Ink,
            )
        }
        Text(
            text = stringResource(R.string.onboarding_summary_change),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = PockyColors.AccentSoft,
        )
    }
}
