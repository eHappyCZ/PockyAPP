package com.pokecio.app.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokecio.app.R
import com.pokecio.app.core.Room
import com.pokecio.app.core.TimelineContent
import com.pokecio.app.core.TimelineEvent
import com.pokecio.app.media.DownloadProgress
import com.pokecio.app.media.SavedMedia
import com.pokecio.app.ui.rooms.RoomAvatar
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MediaDetails(
    val eventId: String?,
    val senderId: String,
    val senderName: String?,
    val own: Boolean,
    val timestampMs: Long,
    val source: String,
    val name: String,
    val size: Long?,
    val mime: String?,
    val width: UInt?,
    val height: UInt?,
    val durationMs: Long?,
    val caption: String?,
) {
    val saved: SavedMedia get() = SavedMedia(source, name, mime)
}

class MediaSharingState(
    val progress: DownloadProgress?,
    val rooms: List<Room>,
    val onSave: (List<SavedMedia>) -> Unit,
    val onCancelSave: () -> Unit,
    val onForward: (List<String>, String) -> Unit,
)

class MediaActions(
    val onSave: (List<MediaDetails>) -> Unit,
    val onShare: (List<MediaDetails>) -> Unit,
    val onForward: (List<MediaDetails>) -> Unit,
)

private val FileStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

fun TimelineEvent.mediaDetails(): MediaDetails? {
    val stamp = Instant.ofEpochMilli(timestampMs.toLong()).atZone(ZoneId.systemDefault()).format(FileStamp)
    return when (val content = content) {
        is TimelineContent.Image -> MediaDetails(
            eventId = eventId,
            senderId = senderId,
            senderName = senderName,
            own = isOwn,
            timestampMs = timestampMs.toLong(),
            source = content.original,
            name = stampedName("IMG_$stamp", content.name),
            size = content.size?.toLong(),
            mime = content.mime,
            width = content.width,
            height = content.height,
            durationMs = null,
            caption = content.caption,
        )
        is TimelineContent.Video -> MediaDetails(
            eventId = eventId,
            senderId = senderId,
            senderName = senderName,
            own = isOwn,
            timestampMs = timestampMs.toLong(),
            source = content.source,
            name = stampedName("VID_$stamp", content.name),
            size = content.size?.toLong(),
            mime = content.mime,
            width = content.width,
            height = content.height,
            durationMs = content.durationMs?.toLong(),
            caption = content.caption,
        )
        is TimelineContent.File -> MediaDetails(
            eventId = eventId,
            senderId = senderId,
            senderName = senderName,
            own = isOwn,
            timestampMs = timestampMs.toLong(),
            source = content.source,
            name = content.name,
            size = content.size?.toLong(),
            mime = content.mime,
            width = null,
            height = null,
            durationMs = null,
            caption = null,
        )
        is TimelineContent.Audio -> MediaDetails(
            eventId = eventId,
            senderId = senderId,
            senderName = senderName,
            own = isOwn,
            timestampMs = timestampMs.toLong(),
            source = content.source,
            name = stampedName("AUD_$stamp", content.caption),
            size = null,
            mime = null,
            width = null,
            height = null,
            durationMs = content.durationMs?.toLong(),
            caption = null,
        )
        else -> null
    }
}

private fun stampedName(stamp: String, original: String): String {
    val extension = original.substringAfterLast('.', "").takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
    return if (extension != null) "PockyAPP_$stamp.${extension.lowercase()}" else "PockyAPP_$stamp"
}

@Composable
fun MediaActionBar(details: MediaDetails, actions: MediaActions, modifier: Modifier = Modifier) {
    var info by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        ViewerButton(PockyIcons.Info, stringResource(R.string.media_info)) { info = true }
        ViewerButton(PockyIcons.Download, stringResource(R.string.media_save)) { actions.onSave(listOf(details)) }
        ViewerButton(PockyIcons.Share, stringResource(R.string.media_share)) { actions.onShare(listOf(details)) }
        if (details.eventId != null) {
            ViewerButton(PockyIcons.Forward, stringResource(R.string.media_forward)) { actions.onForward(listOf(details)) }
        }
    }

    if (info) {
        MediaInfoDialog(details, onDismiss = { info = false })
    }
}

@Composable
private fun ViewerButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun MediaInfoDialog(details: MediaDetails, onDismiss: () -> Unit) {
    val sender = if (details.own) stringResource(R.string.gallery_you) else displayName(details.senderId, details.senderName)
    val width = details.width
    val height = details.height

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PockyColors.Surface,
        title = { Text(stringResource(R.string.media_info_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow(stringResource(R.string.media_info_sender), sender)
                InfoRow(
                    stringResource(R.string.media_info_sent),
                    "${dayLabel(details.timestampMs)} ${timeLabel(details.timestampMs)}",
                )
                InfoRow(stringResource(R.string.media_info_name), details.name)
                details.size?.let { InfoRow(stringResource(R.string.media_info_size), readableSize(it)) }
                if (width != null && height != null) {
                    InfoRow(stringResource(R.string.media_info_dimensions), "$width × $height px")
                }
                details.durationMs?.let { duration ->
                    val seconds = duration / 1000
                    InfoRow(stringResource(R.string.media_info_duration), "%d:%02d".format(seconds / 60, seconds % 60))
                }
                details.mime?.let { InfoRow(stringResource(R.string.media_info_type), it) }
                details.caption?.takeIf { it.isNotBlank() }?.let { InfoRow(stringResource(R.string.media_info_caption), it) }
                if (width != null || details.mime?.substringBefore('/') in setOf("image", "video")) {
                    Text(
                        text = stringResource(R.string.media_info_private),
                        style = MaterialTheme.typography.bodySmall,
                        color = PockyColors.InkMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.verify_close), color = PockyColors.AccentSoft)
            }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = PockyColors.InkMuted)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = PockyColors.Ink)
    }
}

@Composable
fun ForwardDialog(rooms: List<Room>, onPick: (Room) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(rooms, query) {
        rooms
            .filterNot { it.isInvite }
            .sortedByDescending { it.timestampMs ?: 0uL }
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PockyColors.Surface,
        title = { Text(stringResource(R.string.forward_title)) },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(PockyColors.Bg, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.forward_search), color = PockyColors.InkMuted, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = PockyColors.Ink, fontSize = 16.sp),
                        cursorBrush = SolidColor(PockyColors.Accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.id }) { room ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) { onPick(room) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            RoomAvatar(id = room.id, name = room.name, url = room.avatarUrl, size = 36.dp, rounded = !room.isDirect)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = room.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = PockyColors.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = PockyColors.AccentSoft)
            }
        },
    )
}
