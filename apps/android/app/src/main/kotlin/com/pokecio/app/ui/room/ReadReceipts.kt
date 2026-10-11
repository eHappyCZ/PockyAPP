package com.pokecio.app.ui.room

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.Member
import com.pokecio.app.core.ReadBy
import com.pokecio.app.ui.rooms.RoomAvatar
import com.pokecio.app.ui.theme.PockyColors

private const val SHOWN_AVATARS = 4

@Composable
fun ReadReceipts(
    receipts: List<ReadBy>,
    senderId: String,
    own: Boolean,
    isDirect: Boolean,
    members: Map<String, Member>,
) {
    val readers = remember(receipts, senderId) {
        receipts.filter { it.userId != senderId }.sortedByDescending { it.timestampMs ?: 0uL }
    }
    if (readers.isEmpty()) return
    if (isDirect && !own) return

    var open by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 16.dp, top = 3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button) { open = true }
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            if (isDirect) {
                val at = readers.first().timestampMs
                Text(
                    text = if (at != null) {
                        stringResource(R.string.read_at, timeLabel(at.toLong()))
                    } else {
                        stringResource(R.string.read_done)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = PockyColors.InkMuted,
                )
            } else {
                readers.take(SHOWN_AVATARS).forEachIndexed { index, receipt ->
                    val member = members[receipt.userId]
                    Box(
                        Modifier
                            .offset(x = (-4 * index).dp)
                            .border(1.5.dp, PockyColors.Bg, CircleShape),
                    ) {
                        RoomAvatar(
                            id = receipt.userId,
                            name = displayName(receipt.userId, member?.name),
                            url = member?.avatarUrl,
                            size = 16.dp,
                        )
                    }
                }
                val more = readers.size - SHOWN_AVATARS
                if (more > 0) {
                    Text(
                        text = "+$more",
                        style = MaterialTheme.typography.labelSmall,
                        color = PockyColors.InkMuted,
                        modifier = Modifier.offset(x = (-4 * SHOWN_AVATARS + 6).dp),
                    )
                }
            }
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = PockyColors.Surface,
            title = { Text(stringResource(R.string.read_title)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(readers, key = { it.userId }) { receipt ->
                        ReaderRow(receipt, members[receipt.userId])
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.verify_close), color = PockyColors.AccentSoft)
                }
            },
        )
    }
}

@Composable
private fun ReaderRow(receipt: ReadBy, member: Member?) {
    val name = displayName(receipt.userId, member?.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        RoomAvatar(id = receipt.userId, name = name, url = member?.avatarUrl, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
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
