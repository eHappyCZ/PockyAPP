package com.pokecio.app.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import com.pokecio.app.R
import com.pokecio.app.core.TimelineContent
import com.pokecio.app.core.TimelineEvent
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

val QuickReactions = listOf("❤️", "👍", "😂", "😮", "😢", "🔥")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActions(
    event: TimelineEvent,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onInfo: () -> Unit,
    pinned: Boolean,
    canPin: Boolean,
    onPin: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mine = event.reactions.filter { it.reactedByMe }.map { it.key }.toSet()
    val copyable = plainText(event.content) != null
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        EmojiPickerSheet(onPick = onReact, onDismiss = onDismiss)
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PockyColors.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            if (event.eventId != null) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    QuickReactions.forEach { key ->
                        ReactionButton(key, selected = key in mine) { onReact(key) }
                    }
                    MoreReactionsButton { picking = true }
                }
                Spacer(Modifier.size(8.dp))
            }
            if (event.canReply) {
                ActionRow(PockyIcons.Reply, stringResource(R.string.msg_reply), onClick = onReply)
            }
            if (event.canEdit) {
                ActionRow(PockyIcons.Edit, stringResource(R.string.msg_edit), onClick = onEdit)
            }
            if (copyable) {
                ActionRow(PockyIcons.Copy, stringResource(R.string.msg_copy), onClick = onCopy)
            }
            if (event.eventId != null) {
                ActionRow(PockyIcons.Forward, stringResource(R.string.media_forward), onClick = onForward)
            }
            ActionRow(PockyIcons.Info, stringResource(R.string.msg_info), onClick = onInfo)
            if (canPin && event.eventId != null) {
                ActionRow(
                    PockyIcons.Pin,
                    stringResource(if (pinned) R.string.msg_unpin else R.string.msg_pin),
                    onClick = onPin,
                )
            }
            if (!event.isOwn && event.eventId != null) {
                ActionRow(PockyIcons.Alert, stringResource(R.string.msg_report), onClick = onReport)
                ActionRow(PockyIcons.EyeOff, stringResource(R.string.msg_block), onClick = onBlock)
            }
            if (event.canRedact) {
                ActionRow(
                    PockyIcons.Trash,
                    stringResource(R.string.msg_delete),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
fun DeleteMessageDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PockyColors.Surface,
        title = { Text(stringResource(R.string.msg_delete_title)) },
        text = { Text(stringResource(R.string.msg_delete_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.msg_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

fun plainText(content: TimelineContent): String? = when (content) {
    is TimelineContent.Text -> content.body
    is TimelineContent.Notice -> content.body
    is TimelineContent.Emote -> content.body
    is TimelineContent.Image -> content.caption
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiPickerSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val found = remember(query) { EmojiSearch.find(context, query) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PockyColors.Surface,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.emoji_search_hint)) },
                leadingIcon = {
                    Icon(PockyIcons.Search, contentDescription = null, tint = PockyColors.InkMuted, modifier = Modifier.size(18.dp))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PockyColors.Accent,
                    cursorColor = PockyColors.AccentSoft,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (query.isBlank()) {
                AndroidView(
                    factory = { viewContext ->
                        EmojiPickerView(viewContext).apply {
                            emojiGridColumns = 8
                            setOnEmojiPickedListener { item -> onPick(item.emoji) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                )
            } else if (found.isEmpty()) {
                Text(
                    text = stringResource(R.string.emoji_search_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PockyColors.InkMuted,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .padding(horizontal = 8.dp),
                ) {
                    items(found, key = { it.emoji }) { entry ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(46.dp)
                                .clickable(role = Role.Button) { onPick(entry.emoji) },
                        ) {
                            Text(entry.emoji, fontSize = 26.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreReactionsButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .background(PockyColors.Bg, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(
            PockyIcons.Smile,
            contentDescription = stringResource(R.string.msg_more_reactions),
            tint = PockyColors.InkMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ReactionButton(key: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .background(if (selected) PockyColors.Accent.copy(alpha = 0.25f) else PockyColors.Bg, CircleShape)
            .then(if (selected) Modifier.border(1.5.dp, PockyColors.Accent, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(key, fontSize = 22.sp)
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = PockyColors.Ink,
    onClick: () -> Unit,
) {
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
