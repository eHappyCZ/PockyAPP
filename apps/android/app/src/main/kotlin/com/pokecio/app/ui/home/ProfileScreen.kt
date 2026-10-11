package com.pokecio.app.ui.home

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokecio.app.R
import com.pokecio.app.core.ChatLook
import com.pokecio.app.core.DevicePreferences
import com.pokecio.app.core.EncryptionInfo
import com.pokecio.app.core.LocationArea
import com.pokecio.app.core.NotificationText
import com.pokecio.app.core.Recovery
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.onboarding.ChatStyleStep
import com.pokecio.app.ui.onboarding.LocationStep
import com.pokecio.app.ui.onboarding.NotificationStep
import com.pokecio.app.ui.rooms.AuraAvatar
import com.pokecio.app.ui.rooms.AvatarAura
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import com.pokecio.app.update.UpdateCheck
import kotlinx.coroutines.flow.Flow

class SettingsActions(
    val onRename: (String) -> Unit,
    val onPickAvatar: (Uri) -> Unit,
    val onRemoveAvatar: () -> Unit,
    val onSharePresence: (Boolean) -> Unit,
    val onShareTyping: (Boolean) -> Unit,
    val onSendReadReceipts: (Boolean) -> Unit,
    val onBlockScreenshots: (Boolean) -> Unit,
    val onChatStyle: (ChatLook) -> Unit,
    val onNotificationPreview: (NotificationText) -> Unit,
    val onLocationPrecision: (LocationArea) -> Unit,
    val onVerify: () -> Unit,
    val onBackup: () -> Unit,
    val onChangePassphrase: () -> Unit,
    val onChangePassword: () -> Unit,
    val onAppLock: () -> Unit,
    val onDeleteAccount: () -> Unit,
    val onDevices: () -> Unit,
    val onChangelog: () -> Unit,
    val onOnboarding: () -> Unit,
    val onLogout: () -> Unit,
    val onCheckUpdate: () -> Unit,
    val onAutoUpdate: (Boolean) -> Unit,
)

class UpdateSettings(
    val check: UpdateCheck?,
    val checking: Boolean,
    val available: String?,
    val automatic: Boolean,
)

private enum class Picker { NOTIFICATIONS, STYLE, LOCATION }

private const val MAX_NAME = 64

@Composable
fun ProfileScreen(
    name: String?,
    userId: String,
    avatarUrl: String?,
    online: Boolean,
    preferences: DevicePreferences?,
    encryption: EncryptionInfo?,
    appLockOn: Boolean,
    version: String,
    updates: UpdateSettings,
    bottomPadding: Dp,
    busy: Boolean,
    profileBusy: Boolean,
    problems: Flow<Int>,
    actions: SettingsActions,
) {
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    var editingName by rememberSaveable { mutableStateOf(false) }
    var photoOptions by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(actions.onPickAvatar)
    }
    val pickPhoto = {
        photoOptions = false
        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(problems) {
        problems.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PockyColors.Bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding + 24.dp)),
    ) {
        Text(
            text = stringResource(R.string.tab_me),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = PockyColors.Ink,
            modifier = Modifier.padding(start = 4.dp),
        )

        ProfileHeader(
            name = name,
            userId = userId,
            avatarUrl = avatarUrl,
            online = online,
            busy = profileBusy,
            onAvatar = { photoOptions = true },
            onName = { editingName = true },
        )

        Section(stringResource(R.string.settings_profile)) {
            LinkRow(
                icon = PockyIcons.User,
                title = stringResource(R.string.profile_name),
                value = name ?: userId,
                onClick = if (profileBusy) null else ({ editingName = true }),
            )
            RowDivider()
            LinkRow(
                icon = PockyIcons.Photo,
                title = stringResource(R.string.profile_photo),
                value = stringResource(R.string.profile_photo_change),
                onClick = if (profileBusy) null else ({ photoOptions = true }),
            )
            RowDivider()
            LinkRow(
                icon = PockyIcons.Lock,
                title = stringResource(R.string.password_change),
                onClick = actions.onChangePassword,
            )
        }

        if (preferences != null) {
            Section(stringResource(R.string.settings_privacy)) {
                ToggleRow(
                    icon = PockyIcons.User,
                    title = stringResource(R.string.onboarding_visibility_presence),
                    checked = preferences.sharePresence,
                    onChange = actions.onSharePresence,
                )
                RowDivider()
                ToggleRow(
                    icon = PockyIcons.Smile,
                    title = stringResource(R.string.onboarding_visibility_typing),
                    checked = preferences.shareTyping,
                    onChange = actions.onShareTyping,
                )
                RowDivider()
                ToggleRow(
                    icon = PockyIcons.CheckDouble,
                    title = stringResource(R.string.settings_read_receipts),
                    checked = preferences.sendReadReceipts,
                    onChange = actions.onSendReadReceipts,
                )
            }
            Text(
                text = stringResource(R.string.onboarding_visibility_mutual),
                style = MaterialTheme.typography.bodySmall,
                color = PockyColors.InkMuted,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            )
            Text(
                text = stringResource(R.string.settings_read_receipts_note),
                style = MaterialTheme.typography.bodySmall,
                color = PockyColors.InkMuted,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )

            Section(stringResource(R.string.settings_app)) {
                LinkRow(
                    icon = PockyIcons.Bell,
                    title = stringResource(R.string.settings_notifications),
                    value = stringResource(notificationLabel(preferences.notificationPreview)),
                    onClick = { picker = Picker.NOTIFICATIONS },
                )
                RowDivider()
                LinkRow(
                    icon = PockyIcons.Sparkle,
                    title = stringResource(R.string.settings_style),
                    value = stringResource(styleLabel(preferences.chatStyle)),
                    onClick = { picker = Picker.STYLE },
                )
                RowDivider()
                LinkRow(
                    icon = PockyIcons.Location,
                    title = stringResource(R.string.settings_location),
                    value = stringResource(locationLabel(preferences.locationPrecision)),
                    onClick = { picker = Picker.LOCATION },
                )
            }
        }

        Section(stringResource(R.string.settings_security)) {
            val verified = encryption?.deviceVerified == true
            LinkRow(
                icon = if (verified) PockyIcons.Lock else PockyIcons.Alert,
                title = stringResource(R.string.settings_device),
                value = stringResource(if (verified) R.string.settings_device_verified else R.string.settings_device_unverified),
                valueColor = if (verified) PockyColors.Success else PockyColors.Gold,
                onClick = if (!verified && (encryption?.canRecover == true || encryption?.canVerifyWithDevice == true)) {
                    actions.onVerify
                } else {
                    null
                },
            )
            RowDivider()
            val backupOn = encryption?.recovery == Recovery.ENABLED
            LinkRow(
                icon = PockyIcons.Lock,
                title = stringResource(R.string.settings_backup),
                value = stringResource(
                    when {
                        backupOn -> R.string.settings_backup_on
                        encryption?.canEnableRecovery == true -> R.string.settings_backup_setup
                        else -> R.string.settings_backup_off
                    },
                ),
                valueColor = if (backupOn) PockyColors.Success else PockyColors.InkMuted,
                onClick = if (encryption?.canEnableRecovery == true) actions.onBackup else null,
            )
            if (backupOn) {
                RowDivider()
                LinkRow(
                    icon = PockyIcons.Lock,
                    title = stringResource(R.string.passphrase_change),
                    onClick = actions.onChangePassphrase,
                )
            }
            RowDivider()
            LinkRow(
                icon = PockyIcons.Device,
                title = stringResource(R.string.devices_title),
                onClick = actions.onDevices,
            )
            RowDivider()
            LinkRow(
                icon = PockyIcons.Lock,
                title = stringResource(R.string.settings_app_lock),
                value = stringResource(if (appLockOn) R.string.settings_app_lock_on else R.string.settings_app_lock_off),
                valueColor = if (appLockOn) PockyColors.Success else PockyColors.InkMuted,
                onClick = actions.onAppLock,
            )
            if (preferences != null) {
                RowDivider()
                ToggleRow(
                    icon = PockyIcons.EyeOff,
                    title = stringResource(R.string.settings_block_screenshots),
                    checked = preferences.blockScreenshots,
                    onChange = actions.onBlockScreenshots,
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_block_screenshots_note),
            style = MaterialTheme.typography.bodySmall,
            color = PockyColors.InkMuted,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        )

        Section(stringResource(R.string.settings_about)) {
            LinkRow(
                icon = PockyIcons.Sparkle,
                title = stringResource(R.string.home_onboarding),
                onClick = if (busy) null else actions.onOnboarding,
            )
            RowDivider()
            LinkRow(
                icon = PockyIcons.News,
                title = stringResource(R.string.changelog_title),
                onClick = actions.onChangelog,
            )
            RowDivider()
            LinkRow(
                icon = PockyIcons.Download,
                title = stringResource(R.string.update_settings),
                value = when {
                    updates.checking -> stringResource(R.string.update_checking)
                    updates.available != null -> stringResource(R.string.update_available, updates.available)
                    updates.check == UpdateCheck.UP_TO_DATE -> stringResource(R.string.update_latest)
                    updates.check == UpdateCheck.FAILED -> stringResource(R.string.update_check_failed_short)
                    else -> stringResource(R.string.update_check_now)
                },
                valueColor = if (updates.available != null) PockyColors.Success else PockyColors.InkMuted,
                onClick = if (updates.checking) null else actions.onCheckUpdate,
            )
            RowDivider()
            ToggleRow(
                icon = PockyIcons.Clock,
                title = stringResource(R.string.update_automatic),
                checked = updates.automatic,
                onChange = actions.onAutoUpdate,
            )
            RowDivider()
            LinkRow(
                icon = PockyIcons.Info,
                title = stringResource(R.string.settings_version),
                value = version,
                onClick = null,
            )
        }

        Spacer(Modifier.height(20.dp))
        Card {
            LinkRow(
                icon = PockyIcons.Logout,
                title = stringResource(R.string.home_logout),
                tint = MaterialTheme.colorScheme.error,
                onClick = if (busy) null else actions.onLogout,
            )
        }

        Spacer(Modifier.height(12.dp))
        Card {
            LinkRow(
                icon = PockyIcons.Trash,
                title = stringResource(R.string.account_delete),
                tint = PockyColors.Danger,
                onClick = if (busy) null else actions.onDeleteAccount,
            )
        }
    }

    if (editingName) {
        NameDialog(
            current = name.orEmpty(),
            userId = userId,
            onSave = {
                editingName = false
                actions.onRename(it)
            },
            onDismiss = { editingName = false },
        )
    }

    if (photoOptions) {
        PhotoSheet(
            hasPhoto = avatarUrl != null,
            onPick = pickPhoto,
            onRemove = {
                photoOptions = false
                actions.onRemoveAvatar()
            },
            onDismiss = { photoOptions = false },
        )
    }

    if (preferences != null) {
        picker?.let { open ->
            PickerSheet(onDismiss = { picker = null }) {
                when (open) {
                    Picker.NOTIFICATIONS -> NotificationStep(preferences.notificationPreview, actions.onNotificationPreview)
                    Picker.STYLE -> ChatStyleStep(preferences.chatStyle, actions.onChatStyle)
                    Picker.LOCATION -> LocationStep(preferences.locationPrecision, actions.onLocationPrecision)
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    name: String?,
    userId: String,
    avatarUrl: String?,
    online: Boolean,
    busy: Boolean,
    onAvatar: () -> Unit,
    onName: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(124.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onAvatar),
            ) {
                AuraAvatar(
                    id = userId,
                    name = name ?: userId,
                    url = avatarUrl,
                    size = 104.dp,
                    aura = if (online) AvatarAura.ONLINE else AvatarAura.OWN,
                )
                if (busy) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(104.dp)
                            .background(PockyColors.Bg.copy(alpha = 0.6f), CircleShape),
                    ) {
                        CircularProgressIndicator(color = PockyColors.AccentSoft, strokeWidth = 2.5.dp)
                    }
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onAvatar)
                    .background(PockyColors.Bg, CircleShape)
                    .padding(3.dp)
                    .background(PockyGradient, CircleShape),
            ) {
                Icon(
                    PockyIcons.Photo,
                    contentDescription = stringResource(R.string.profile_photo_change),
                    tint = PockyColors.Ink,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !busy, role = Role.Button, onClick = onName)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(
                text = name ?: userId,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PockyColors.Ink,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                PockyIcons.Edit,
                contentDescription = stringResource(R.string.profile_name_edit),
                tint = PockyColors.AccentSoft,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = userId,
            style = MaterialTheme.typography.bodyMedium,
            color = PockyColors.InkMuted,
        )
    }
}

@Composable
private fun NameDialog(current: String, userId: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(current) }
    val trimmed = value.trim()
    val valid = trimmed.isNotEmpty() && value.length <= MAX_NAME && trimmed != current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PockyColors.Surface,
        title = { Text(stringResource(R.string.profile_name_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.profile_name_hint, userId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PockyColors.InkMuted,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.length <= MAX_NAME) value = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.profile_name_placeholder)) },
                    supportingText = { Text("${value.length}/$MAX_NAME") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PockyColors.Accent,
                        cursorColor = PockyColors.AccentSoft,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(trimmed) }, enabled = valid) {
                Text(stringResource(R.string.profile_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSheet(hasPhoto: Boolean, onPick: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PockyColors.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_photo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PockyColors.Ink,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.profile_photo_note),
                style = MaterialTheme.typography.bodySmall,
                color = PockyColors.InkMuted,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            LinkRow(icon = PockyIcons.Photo, title = stringResource(R.string.profile_photo_pick), onClick = onPick)
            if (hasPhoto) {
                LinkRow(
                    icon = PockyIcons.Trash,
                    title = stringResource(R.string.profile_photo_remove),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = PockyColors.InkMuted,
        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp),
    )
    Card(content)
}

@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PockyColors.Surface),
        content = content,
    )
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(
        color = PockyColors.InkMuted.copy(alpha = 0.08f),
        modifier = Modifier.padding(start = 64.dp),
    )
}

@Composable
internal fun RowIcon(icon: ImageVector, tint: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .background(tint.copy(alpha = 0.13f), RoundedCornerShape(11.dp)),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
internal fun LinkRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    valueColor: Color = PockyColors.InkMuted,
    tint: Color = PockyColors.AccentSoft,
    onClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        RowIcon(icon, tint)
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (tint == PockyColors.AccentSoft) PockyColors.Ink else tint,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                maxLines = 1,
            )
        }
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(PockyIcons.ChevronRight, contentDescription = null, tint = PockyColors.InkMuted)
        }
    }
}

@Composable
internal fun ToggleRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        RowIcon(icon, PockyColors.AccentSoft)
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = PockyColors.Ink,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PockyColors.Ink,
                checkedTrackColor = PockyColors.Accent,
                uncheckedThumbColor = PockyColors.InkMuted,
                uncheckedTrackColor = PockyColors.Bg,
                uncheckedBorderColor = PockyColors.InkMuted.copy(alpha = 0.4f),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PockyColors.Bg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            content = content,
        )
    }
}

private fun notificationLabel(preview: NotificationText) = when (preview) {
    NotificationText.ALWAYS -> R.string.settings_notifications_always
    NotificationText.WHEN_UNLOCKED -> R.string.settings_notifications_unlocked
    NotificationText.NEVER -> R.string.onboarding_notifications_never
}

private fun styleLabel(style: ChatLook) = when (style) {
    ChatLook.CLASSIC -> R.string.onboarding_style_classic
    ChatLook.AURA -> R.string.settings_style_aura
}

private fun locationLabel(area: LocationArea) = when (area) {
    LocationArea.EXACT -> R.string.onboarding_location_exact
    LocationArea.NEIGHBORHOOD -> R.string.onboarding_location_neighborhood
    LocationArea.CITY -> R.string.onboarding_location_city
}
