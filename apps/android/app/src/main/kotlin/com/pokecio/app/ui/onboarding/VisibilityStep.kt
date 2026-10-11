package com.pokecio.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.ui.components.ControlShape
import com.pokecio.app.ui.rooms.AuraAvatar
import com.pokecio.app.ui.rooms.AvatarAura
import com.pokecio.app.ui.theme.PockyColors

@Composable
fun VisibilityStep(
    sharePresence: Boolean,
    shareTyping: Boolean,
    onSharePresence: (Boolean) -> Unit,
    onShareTyping: (Boolean) -> Unit,
) {
    AuraPreview(sharePresence, shareTyping)
    Spacer(Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToggleCard(
            title = stringResource(R.string.onboarding_visibility_presence),
            body = stringResource(R.string.onboarding_visibility_presence_body),
            checked = sharePresence,
            onCheckedChange = onSharePresence,
        )
        ToggleCard(
            title = stringResource(R.string.onboarding_visibility_typing),
            body = stringResource(R.string.onboarding_visibility_typing_body),
            checked = shareTyping,
            onCheckedChange = onShareTyping,
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_visibility_mutual),
        style = MaterialTheme.typography.bodySmall,
        color = PockyColors.InkMuted,
    )
}

@Composable
private fun AuraPreview(sharePresence: Boolean, shareTyping: Boolean) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .background(PockyColors.Bg, ControlShape)
            .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.12f), ControlShape)
            .padding(vertical = 22.dp),
    ) {
        Sample(
            id = "@jana",
            name = stringResource(R.string.onboarding_sample_sender),
            aura = if (sharePresence) AvatarAura.ONLINE else AvatarAura.NONE,
            label = stringResource(if (sharePresence) R.string.presence_online else R.string.onboarding_visibility_hidden),
            labelColor = if (sharePresence) PockyColors.Success else PockyColors.InkMuted,
        )
        Sample(
            id = "@petr",
            name = stringResource(R.string.onboarding_sample_second),
            aura = if (sharePresence) AvatarAura.AWAY else AvatarAura.NONE,
            label = stringResource(if (sharePresence) R.string.presence_away else R.string.onboarding_visibility_hidden),
            labelColor = if (sharePresence) PockyColors.Gold else PockyColors.InkMuted,
        )
        Sample(
            id = "@eva",
            name = stringResource(R.string.onboarding_sample_third),
            aura = if (shareTyping) AvatarAura.TYPING else AvatarAura.NONE,
            label = stringResource(if (shareTyping) R.string.typing_direct else R.string.onboarding_visibility_hidden),
            labelColor = if (shareTyping) PockyColors.AccentSoft else PockyColors.InkMuted,
        )
    }
}

@Composable
private fun Sample(id: String, name: String, aura: AvatarAura, label: String, labelColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AuraAvatar(id = id, name = name, size = 52.dp, aura = aura)
        Spacer(Modifier.height(10.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = PockyColors.Ink)
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor)
    }
}
