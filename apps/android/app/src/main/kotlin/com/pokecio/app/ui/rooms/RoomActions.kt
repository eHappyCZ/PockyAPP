package com.pokecio.app.ui.rooms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.Room
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

class RoomActionHandlers(
    val onMarkRead: (Room) -> Unit,
    val onFavourite: (Room) -> Unit,
    val onMute: (Room) -> Unit,
    val onLeave: (Room) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomActionsSheet(room: Room, handlers: RoomActionHandlers, onDismiss: () -> Unit) {
    fun run(action: (Room) -> Unit): () -> Unit = {
        onDismiss()
        action(room)
    }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                AuraAvatar(id = room.id, name = room.name, url = room.avatarUrl, size = 40.dp, rounded = !room.isDirect)
                Spacer(Modifier.width(14.dp))
                Text(
                    text = room.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PockyColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(
                color = PockyColors.InkMuted.copy(alpha = 0.1f),
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (room.unreadMessages > 0u || room.unreadMentions > 0u) {
                ActionRow(PockyIcons.CheckDouble, stringResource(R.string.room_mark_read), onClick = run(handlers.onMarkRead))
            }
            ActionRow(
                PockyIcons.Pin,
                stringResource(if (room.isFavourite) R.string.room_unpin else R.string.room_pin),
                onClick = run(handlers.onFavourite),
            )
            ActionRow(
                if (room.isMuted) PockyIcons.Bell else PockyIcons.BellOff,
                stringResource(if (room.isMuted) R.string.room_unmute else R.string.room_mute),
                onClick = run(handlers.onMute),
            )
            if (!room.isInvite) {
                ActionRow(
                    PockyIcons.Logout,
                    stringResource(R.string.room_leave),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = run(handlers.onLeave),
                )
            }
        }
    }
}

@Composable
fun LeaveRoomDialog(room: Room, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PockyColors.Surface,
        title = { Text(stringResource(R.string.room_leave_title, room.name)) },
        text = {
            Text(
                stringResource(
                    if (room.isDirect) R.string.room_leave_text_direct else R.string.room_leave_text_group,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.room_leave), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, tint: Color = PockyColors.Ink, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tint)
    }
}
