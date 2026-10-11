package com.pokecio.app.ui.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.Member
import com.pokecio.app.core.ReadBy
import com.pokecio.app.core.TimelineContent
import com.pokecio.app.core.TimelineEvent
import com.pokecio.app.core.TimelineItem
import com.pokecio.app.core.TimelineItemKind
import com.pokecio.app.core.TimelineSendState
import com.pokecio.app.ui.rooms.RoomAvatar
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

fun readersOf(items: List<TimelineItem>, event: TimelineEvent): List<ReadBy> {
    val events = items.mapNotNull { (it.kind as? TimelineItemKind.Event)?.event }
    val index = events.indexOfFirst { it.itemKey == event.itemKey }
    if (index < 0) return emptyList()
    val later = events.drop(index)
    val explicit = later.flatMap { it.readBy }
    val replied = later.drop(1)
        .filter { !it.isOwn && it.content !is TimelineContent.State }
        .map { ReadBy(it.senderId, it.timestampMs) }
    return (explicit + replied)
        .filter { it.userId != event.senderId }
        .groupBy { it.userId }
        .map { (_, receipts) -> receipts.minBy { it.timestampMs ?: ULong.MAX_VALUE } }
        .sortedByDescending { it.timestampMs ?: 0uL }
}

@Composable
fun MessageInfoDialog(
    event: TimelineEvent,
    readers: List<ReadBy>,
    members: Map<String, Member>,
    isDirect: Boolean,
    receiptsOn: Boolean,
    onDismiss: () -> Unit,
) {
    val sent = event.timestampMs.toLong()
    val others = (members.keys - event.senderId).size - if (event.isOwn) 0 else 1

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PockyColors.Surface,
        title = { Text(stringResource(R.string.msg_info_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (event.sendState) {
                    TimelineSendState.SENDING -> Step(PockyIcons.Clock, PockyColors.InkMuted, stringResource(R.string.delivery_sending), null)
                    TimelineSendState.FAILED -> Step(
                        PockyIcons.AlertCircle,
                        MaterialTheme.colorScheme.error,
                        stringResource(R.string.delivery_failed),
                        null,
                    )
                    TimelineSendState.SENT -> Step(
                        PockyIcons.Check,
                        PockyColors.InkMuted,
                        stringResource(R.string.msg_info_sent),
                        "${dayLabel(sent)} ${timeLabel(sent)}",
                    )
                }
                if (event.isEdited) {
                    Step(PockyIcons.Edit, PockyColors.InkMuted, stringResource(R.string.msg_info_edited), null)
                }

                HorizontalDivider(color = PockyColors.InkMuted.copy(alpha = 0.15f))

                when {
                    !receiptsOn -> Note(stringResource(R.string.msg_info_receipts_off))
                    event.sendState != TimelineSendState.SENT -> Unit
                    readers.isEmpty() -> Step(PockyIcons.CheckDouble, PockyColors.InkMuted, stringResource(R.string.msg_info_unread), null)
                    isDirect -> {
                        val at = readers.first().timestampMs?.toLong()
                        Step(
                            PockyIcons.CheckDouble,
                            PockyColors.Success,
                            stringResource(R.string.delivery_read),
                            at?.let { "${dayLabel(it)} ${timeLabel(it)}" },
                        )
                    }
                    else -> {
                        Text(
                            text = if (others > 0) {
                                stringResource(R.string.msg_info_read_count, readers.size, others)
                            } else {
                                stringResource(R.string.read_title)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = PockyColors.Ink,
                        )
                        LazyColumn(Modifier.heightIn(max = 280.dp)) {
                            items(readers, key = { it.userId }) { receipt ->
                                Reader(receipt, members[receipt.userId])
                            }
                        }
                    }
                }
                if (receiptsOn) Note(stringResource(R.string.msg_info_note))
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
private fun Step(icon: ImageVector, tint: Color, title: String, detail: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = PockyColors.Ink)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelMedium, color = PockyColors.InkMuted)
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = PockyColors.InkMuted)
}

@Composable
private fun Reader(receipt: ReadBy, member: Member?) {
    val name = displayName(receipt.userId, member?.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        RoomAvatar(id = receipt.userId, name = name, url = member?.avatarUrl, size = 30.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = PockyColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        receipt.timestampMs?.let { at ->
            Text(
                text = "${dayLabel(at.toLong())} ${timeLabel(at.toLong())}",
                style = MaterialTheme.typography.labelMedium,
                color = PockyColors.InkMuted,
            )
        }
    }
}
