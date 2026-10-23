package com.pokecio.app.ui.room

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokecio.app.R
import com.pokecio.app.core.Member
import com.pokecio.app.core.PresenceStatus
import com.pokecio.app.core.Role as MemberRole
import com.pokecio.app.core.Room
import com.pokecio.app.core.RoomInfo
import com.pokecio.app.core.UserTrust
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.people.PeopleHeader
import com.pokecio.app.ui.rooms.AuraAvatar
import com.pokecio.app.ui.rooms.RoomActionHandlers
import com.pokecio.app.ui.rooms.RoomAvatar
import com.pokecio.app.ui.rooms.auraOf
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import kotlinx.coroutines.flow.Flow

class RoomInfoEdits(
    val onRename: (String) -> Unit,
    val onTopic: (String) -> Unit,
    val onAvatar: (Uri) -> Unit,
    val onAddPeople: () -> Unit,
    val onRemove: (String) -> Unit,
    val onVerify: () -> Unit,
)

private enum class InfoEdit { NAME, TOPIC }

@Composable
fun RoomInfoScreen(
    info: RoomInfo?,
    room: Room?,
    presence: PresenceStatus?,
    trust: UserTrust?,
    busy: Boolean,
    problems: Flow<Int>,
    edits: RoomInfoEdits,
    moderation: ModerationState,
    roomActions: RoomActionHandlers,
    onGallery: () -> Unit,
    onHistory: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf<InfoEdit?>(null) }
    var removing by remember { mutableStateOf<Member?>(null) }
    var lifetimeOpen by remember { mutableStateOf(false) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(edits.onAvatar)
    }

    LaunchedEffect(problems) {
        problems.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PockyColors.Bg),
    ) {
        PeopleHeader(
            title = stringResource(if (info?.isDirect == true) R.string.room_info_profile else R.string.room_menu_info),
            subtitle = null,
            busy = busy,
            onBack = onBack,
        )
        if (info == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PockyColors.AccentSoft)
            }
            return@Column
        }

        val joined = info.members.filterNot { it.invited }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item(key = "head") {
                InfoHead(
                    info = info,
                    presence = presence,
                    onAvatar = if (info.canEditAvatar && !info.isDirect) {
                        { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    } else {
                        null
                    },
                    onName = if (info.canEditName && !info.isDirect) ({ editing = InfoEdit.NAME }) else null,
                    onTopic = if (info.canEditTopic && !info.isDirect) ({ editing = InfoEdit.TOPIC }) else null,
                )
            }
            if (trust != null && trust != UserTrust.NO_IDENTITY) {
                item(key = "trust") {
                    Card {
                        TrustRow(trust, onVerify = edits.onVerify)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            item(key = "actions") {
                Card {
                    InfoRow(
                        icon = if (room?.isMuted == true) PockyIcons.Bell else PockyIcons.BellOff,
                        title = stringResource(if (room?.isMuted == true) R.string.room_unmute else R.string.room_mute),
                        onClick = { room?.let(roomActions.onMute) },
                    )
                    Divider()
                    InfoRow(
                        icon = PockyIcons.Pin,
                        title = stringResource(if (room?.isFavourite == true) R.string.room_unpin else R.string.room_pin),
                        onClick = { room?.let(roomActions.onFavourite) },
                    )
                    Divider()
                    InfoRow(
                        icon = PockyIcons.Images,
                        title = stringResource(R.string.room_menu_media),
                        onClick = onGallery,
                    )
                    Divider()
                    InfoRow(
                        icon = PockyIcons.Timeline,
                        title = stringResource(R.string.history_title),
                        onClick = onHistory,
                    )
                }
            }
            if (!info.isDirect) {
                item(key = "members-label") {
                    Text(
                        text = pluralStringResource(R.plurals.room_info_members, joined.size, joined.size).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = PockyColors.InkMuted,
                        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp),
                    )
                }
                if (info.canInvite) {
                    item(key = "add") {
                        Card {
                            InfoRow(PockyIcons.UserPlus, stringResource(R.string.room_info_add_people), onClick = edits.onAddPeople)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(info.members, key = { it.userId }) { member ->
                    MemberRow(member, onClick = if (member.canRemove) ({ removing = member }) else null)
                }
            }
            if (moderation.canSetLifetime) {
                item(key = "lifetime") {
                    Spacer(Modifier.height(20.dp))
                    Card {
                        InfoRow(
                            icon = PockyIcons.Clock,
                            title = stringResource(R.string.lifetime_title),
                            value = lifetimeLabel(moderation.lifetime),
                            onClick = { lifetimeOpen = true },
                        )
                    }
                }
            }
            item(key = "leave") {
                Spacer(Modifier.height(20.dp))
                Card {
                    InfoRow(
                        icon = PockyIcons.Logout,
                        title = stringResource(R.string.room_leave),
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { room?.let(roomActions.onLeave) },
                    )
                }
            }
        }
    }

    if (lifetimeOpen) {
        LifetimeSheet(
            current = moderation.lifetime,
            onSelect = moderation.onLifetime,
            onDismiss = { lifetimeOpen = false },
        )
    }

    editing?.let { field ->
        TextEditDialog(
            title = stringResource(if (field == InfoEdit.NAME) R.string.room_info_name else R.string.room_info_topic),
            initial = if (field == InfoEdit.NAME) info?.name.orEmpty() else info?.topic.orEmpty(),
            maxLength = if (field == InfoEdit.NAME) 64 else 500,
            singleLine = field == InfoEdit.NAME,
            allowEmpty = field == InfoEdit.TOPIC,
            onSave = {
                editing = null
                if (field == InfoEdit.NAME) edits.onRename(it) else edits.onTopic(it)
            },
            onDismiss = { editing = null },
        )
    }

    removing?.let { member ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = PockyColors.Surface,
            title = { Text(stringResource(R.string.room_info_remove_title, member.name ?: member.userId)) },
            text = { Text(stringResource(R.string.room_info_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    edits.onRemove(member.userId)
                }) {
                    Text(stringResource(R.string.room_info_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun InfoHead(
    info: RoomInfo,
    presence: PresenceStatus?,
    onAvatar: (() -> Unit)?,
    onName: (() -> Unit)?,
    onTopic: (() -> Unit)?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 20.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(124.dp)
                .clip(if (info.isDirect) CircleShape else RoundedCornerShape(36.dp))
                .then(if (onAvatar != null) Modifier.clickable(role = Role.Button, onClick = onAvatar) else Modifier),
        ) {
            AuraAvatar(
                id = info.roomId,
                name = info.name,
                url = info.avatarUrl,
                size = 104.dp,
                rounded = !info.isDirect,
                aura = auraOf(presence, typing = false),
            )
            if (onAvatar != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 4.dp)
                        .size(34.dp)
                        .background(PockyColors.Bg, CircleShape)
                        .padding(3.dp)
                        .background(PockyGradient, CircleShape),
                ) {
                    Icon(PockyIcons.Camera, contentDescription = null, tint = PockyColors.OnBrand, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .then(if (onName != null) Modifier.clickable(role = Role.Button, onClick = onName) else Modifier)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(
                text = info.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PockyColors.Ink,
                textAlign = TextAlign.Center,
            )
            if (onName != null) {
                Spacer(Modifier.width(8.dp))
                Icon(PockyIcons.Edit, contentDescription = null, tint = PockyColors.AccentSoft, modifier = Modifier.size(18.dp))
            }
        }
        val subtitle = info.directUserId?.takeIf { info.isDirect }
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = PockyColors.InkMuted)
        }
        if (info.isDirect) {
            HeaderStatus(emptyList(), isDirect = true, presence = presence)
        }
        val topic = info.topic
        if (topic != null || onTopic != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = topic ?: stringResource(R.string.room_info_add_topic),
                style = MaterialTheme.typography.bodyMedium,
                color = if (topic != null) PockyColors.Ink.copy(alpha = 0.85f) else PockyColors.AccentSoft,
                textAlign = TextAlign.Center,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (onTopic != null) Modifier.clickable(role = Role.Button, onClick = onTopic) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (info.isEncrypted) {
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(PockyColors.Success.copy(alpha = 0.12f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(PockyIcons.Lock, contentDescription = null, tint = PockyColors.Success, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.room_info_encrypted),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PockyColors.Success,
                )
            }
        }
    }
}

@Composable
private fun MemberRow(member: Member, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        RoomAvatar(id = member.userId, name = member.name ?: member.userId, url = member.avatarUrl, size = 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = member.name ?: member.userId,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (member.invited) PockyColors.InkMuted else PockyColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (member.invited) stringResource(R.string.room_info_invited) else member.userId,
                style = MaterialTheme.typography.bodySmall,
                color = PockyColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (member.role) {
            MemberRole.ADMIN -> RoleChip(stringResource(R.string.room_info_admin), PockyColors.Gold)
            MemberRole.MODERATOR -> RoleChip(stringResource(R.string.room_info_moderator), PockyColors.AccentSoft)
            MemberRole.MEMBER -> Unit
        }
    }
}

@Composable
private fun RoleChip(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.13f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PockyColors.Surface),
    ) {
        content()
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = PockyColors.InkMuted.copy(alpha = 0.08f), modifier = Modifier.padding(start = 60.dp))
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    tint: Color = PockyColors.Ink,
    value: String? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .background(
                    (if (tint == PockyColors.Ink) PockyColors.AccentSoft else tint).copy(alpha = 0.13f),
                    RoundedCornerShape(11.dp),
                ),
        ) {
            Icon(icon, contentDescription = null, tint = if (tint == PockyColors.Ink) PockyColors.AccentSoft else tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tint)
        if (value != null) {
            Spacer(Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = PockyColors.InkMuted)
        }
    }
}

@Composable
private fun TextEditDialog(
    title: String,
    initial: String,
    maxLength: Int,
    singleLine: Boolean,
    allowEmpty: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val valid = (allowEmpty || value.isNotBlank()) && value.trim() != initial

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PockyColors.Surface,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { if (it.length <= maxLength) value = it },
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 6,
                supportingText = { Text("${value.length}/$maxLength") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PockyColors.Accent,
                    cursorColor = PockyColors.AccentSoft,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim()) }, enabled = valid) { Text(stringResource(R.string.profile_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
