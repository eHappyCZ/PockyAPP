package com.pokecio.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.AccountDevice
import com.pokecio.app.ui.people.PeopleHeader
import com.pokecio.app.ui.people.SectionLabel
import com.pokecio.app.ui.rooms.formatTimestamp
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import kotlinx.coroutines.flow.Flow

@Composable
fun DevicesScreen(
    devices: List<AccountDevice>?,
    busy: Boolean,
    problems: Flow<Int>,
    onSignOut: (deviceId: String, password: String, onDone: () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var target by remember { mutableStateOf<AccountDevice?>(null) }
    val context = LocalContext.current

    LaunchedEffect(problems) {
        problems.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val current = devices?.filter { it.isCurrent }.orEmpty()
    val others = devices?.filterNot { it.isCurrent }.orEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .background(PockyColors.Bg),
    ) {
        PeopleHeader(stringResource(R.string.devices_title), null, busy, onBack)

        if (devices == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PockyColors.AccentSoft)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item(key = "current-label") { SectionLabel(stringResource(R.string.devices_current)) }
            items(current, key = { it.deviceId }) { device ->
                DeviceCard(device, onClick = null)
            }
            item(key = "others-label") { SectionLabel(stringResource(R.string.devices_others)) }
            if (others.isEmpty()) {
                item(key = "none") {
                    Text(
                        text = stringResource(R.string.devices_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PockyColors.InkMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
            items(others, key = { it.deviceId }) { device ->
                DeviceCard(device, onClick = { target = device })
                Spacer(Modifier.height(8.dp))
            }
            item(key = "note") {
                Text(
                    text = stringResource(R.string.devices_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = PockyColors.InkMuted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                )
            }
        }
    }

    target?.let { device ->
        SignOutDialog(
            device = device,
            busy = busy,
            onConfirm = { password -> onSignOut(device.deviceId, password) { target = null } },
            onDismiss = { target = null },
        )
    }
}

@Composable
private fun DeviceCard(device: AccountDevice, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PockyColors.Surface)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .background(PockyColors.AccentSoft.copy(alpha = 0.13f), RoundedCornerShape(13.dp)),
        ) {
            Icon(PockyIcons.Device, contentDescription = null, tint = PockyColors.AccentSoft, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = device.name ?: device.deviceId,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PockyColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val lastSeen = device.lastSeenMs
            Text(
                text = when {
                    device.isCurrent -> stringResource(R.string.devices_active_now)
                    lastSeen != null -> stringResource(R.string.devices_last_seen, formatTimestamp(lastSeen.toLong()))
                    else -> device.deviceId
                },
                style = MaterialTheme.typography.bodySmall,
                color = PockyColors.InkMuted,
            )
            Spacer(Modifier.height(6.dp))
            VerifiedBadge(device.verified)
        }
        if (onClick != null) {
            Icon(PockyIcons.Logout, contentDescription = stringResource(R.string.devices_sign_out), tint = PockyColors.InkMuted)
        }
    }
}

@Composable
private fun VerifiedBadge(verified: Boolean) {
    val color = if (verified) PockyColors.Success else PockyColors.Gold
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(
            if (verified) PockyIcons.CheckCircle else PockyIcons.Alert,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(if (verified) R.string.settings_device_verified else R.string.settings_device_unverified),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun SignOutDialog(device: AccountDevice, busy: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = PockyColors.Surface,
        title = { Text(stringResource(R.string.devices_sign_out_title, device.name ?: device.deviceId)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.devices_sign_out_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PockyColors.InkMuted,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    enabled = !busy,
                    label = { Text(stringResource(R.string.login_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PockyColors.Accent,
                        cursorColor = PockyColors.AccentSoft,
                        focusedLabelColor = PockyColors.AccentSoft,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty() && !busy) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.devices_sign_out), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        },
    )
}
