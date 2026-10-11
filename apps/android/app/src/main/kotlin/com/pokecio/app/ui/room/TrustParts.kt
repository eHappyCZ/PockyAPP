package com.pokecio.app.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.IdentityChange
import com.pokecio.app.core.MessageShield
import com.pokecio.app.core.ShieldInfo
import com.pokecio.app.core.UserTrust
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

@Composable
fun IdentityBanner(
    change: IdentityChange,
    onAcknowledge: () -> Unit,
    onVerify: () -> Unit,
) {
    val tint = if (change.wasVerified) MaterialTheme.colorScheme.error else PockyColors.Gold
    val name = change.name ?: change.userId

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.12f))
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Icon(PockyIcons.ShieldAlert, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(
                if (change.wasVerified) R.string.identity_broken_banner else R.string.identity_changed_banner,
                name,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = PockyColors.Ink,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            BannerAction(stringResource(R.string.identity_verify), tint, onVerify)
            BannerAction(stringResource(R.string.identity_acknowledge), PockyColors.InkMuted, onAcknowledge)
        }
    }
}

@Composable
private fun BannerAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun ShieldMark(shield: ShieldInfo, color: Color) {
    var open by remember { mutableStateOf(false) }
    val tint = if (shield.danger) MaterialTheme.colorScheme.error else color

    Icon(
        PockyIcons.ShieldAlert,
        contentDescription = stringResource(R.string.shield_title),
        tint = tint,
        modifier = Modifier
            .size(15.dp)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button) { open = true },
    )

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = PockyColors.Surface,
            icon = { Icon(PockyIcons.ShieldAlert, contentDescription = null, tint = tint) },
            title = { Text(stringResource(R.string.shield_title)) },
            text = { Text(stringResource(shieldText(shield.reason)), color = PockyColors.InkMuted) },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.verify_close), color = PockyColors.AccentSoft)
                }
            },
        )
    }
}

private fun shieldText(reason: MessageShield): Int = when (reason) {
    MessageShield.AUTHENTICITY_NOT_GUARANTEED -> R.string.shield_authenticity
    MessageShield.UNKNOWN_DEVICE -> R.string.shield_unknown_device
    MessageShield.UNVERIFIED_DEVICE -> R.string.shield_unverified_device
    MessageShield.UNVERIFIED_SENDER -> R.string.shield_unverified_sender
    MessageShield.IDENTITY_CHANGED -> R.string.shield_identity_changed
    MessageShield.MISMATCHED_SENDER -> R.string.shield_mismatched_sender
    MessageShield.SENT_IN_CLEAR -> R.string.shield_sent_in_clear
}

@Composable
fun TrustRow(trust: UserTrust, onVerify: () -> Unit) {
    val verified = trust == UserTrust.VERIFIED
    val tint = when (trust) {
        UserTrust.VERIFIED -> PockyColors.Success
        UserTrust.VERIFICATION_BROKEN -> MaterialTheme.colorScheme.error
        else -> PockyColors.AccentSoft
    }
    val title = when (trust) {
        UserTrust.VERIFIED -> R.string.trust_verified
        UserTrust.VERIFICATION_BROKEN -> R.string.trust_broken
        else -> R.string.trust_verify
    }
    val detail = when (trust) {
        UserTrust.VERIFIED -> R.string.trust_verified_detail
        UserTrust.VERIFICATION_BROKEN -> R.string.trust_broken_detail
        else -> R.string.trust_verify_detail
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (verified) Modifier else Modifier.clickable(role = Role.Button, onClick = onVerify))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .background(tint.copy(alpha = 0.13f), RoundedCornerShape(11.dp)),
        ) {
            Icon(
                if (trust == UserTrust.VERIFICATION_BROKEN) PockyIcons.ShieldAlert else PockyIcons.ShieldCheck,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (verified) PockyColors.Ink else tint,
            )
            Text(
                stringResource(detail),
                style = MaterialTheme.typography.bodySmall,
                color = PockyColors.InkMuted,
            )
        }
    }
}
