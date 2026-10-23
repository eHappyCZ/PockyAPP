package com.pokecio.app.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.MessageContent
import com.pokecio.app.core.Pinned
import com.pokecio.app.ui.onboarding.ChoiceCard
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

class ModerationState(
    val pinned: List<Pinned>,
    val canPin: Boolean,
    val lifetime: Long?,
    val canSetLifetime: Boolean,
    val onReport: (eventId: String, reason: String?) -> Unit,
    val onBlock: (userId: String) -> Unit,
    val onPin: (eventId: String) -> Unit,
    val onUnpin: (eventId: String) -> Unit,
    val onLifetime: (seconds: Long?) -> Unit,
)

private const val HOUR = 3_600L
private const val DAY = 24 * HOUR
private const val WEEK = 7 * DAY

val LifetimeChoices: List<Long?> = listOf(null, HOUR, DAY, WEEK)

@Composable
fun lifetimeLabel(seconds: Long?): String = stringResource(
    when {
        seconds == null -> R.string.lifetime_off
        seconds <= HOUR -> R.string.lifetime_hour
        seconds <= DAY -> R.string.lifetime_day
        else -> R.string.lifetime_week
    },
)

@Composable
fun previewLabel(content: MessageContent): String = when (content) {
    is MessageContent.Text -> content.body
    is MessageContent.Emote -> "* ${content.body}"
    MessageContent.Image -> stringResource(R.string.preview_image)
    MessageContent.Video -> stringResource(R.string.preview_video)
    MessageContent.Audio -> stringResource(R.string.preview_audio)
    MessageContent.File -> stringResource(R.string.preview_file)
    MessageContent.Location -> stringResource(R.string.preview_location)
    MessageContent.Sticker -> stringResource(R.string.preview_sticker)
    MessageContent.Poll -> stringResource(R.string.preview_poll)
    MessageContent.Encrypted -> stringResource(R.string.preview_encrypted)
    MessageContent.Invite -> stringResource(R.string.preview_invite)
    MessageContent.Other -> stringResource(R.string.preview_other)
}

private fun Pinned.author(): String =
    senderName?.takeIf { it.isNotBlank() } ?: senderId.removePrefix("@").substringBefore(':')

@Composable
fun PinnedBar(pinned: List<Pinned>, onClick: () -> Unit) {
    val latest = pinned.lastOrNull() ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(PockyColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(PockyIcons.Pin, contentDescription = null, tint = PockyColors.AccentSoft, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = latest.author(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = PockyColors.AccentSoft,
                maxLines = 1,
            )
            Text(
                text = previewLabel(latest.content),
                style = MaterialTheme.typography.bodySmall,
                color = PockyColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (pinned.size > 1) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.pinned_count, pinned.size),
                style = MaterialTheme.typography.labelSmall,
                color = PockyColors.InkMuted,
            )
        }
    }
    HorizontalDivider(color = PockyColors.InkMuted.copy(alpha = 0.1f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinnedSheet(state: ModerationState, onDismiss: () -> Unit) {
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
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.pinned_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PockyColors.Ink,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (state.pinned.isEmpty()) {
                Text(
                    text = stringResource(R.string.pinned_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PockyColors.InkMuted,
                )
            }
            state.pinned.asReversed().forEach { message ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = message.author(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PockyColors.AccentSoft,
                        )
                        Text(
                            text = previewLabel(message.content),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PockyColors.Ink,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (state.canPin) {
                        TextButton(onClick = { state.onUnpin(message.eventId) }) {
                            Text(stringResource(R.string.msg_unpin), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                HorizontalDivider(color = PockyColors.InkMuted.copy(alpha = 0.08f))
            }
        }
    }
}

@Composable
fun ReportDialog(onSend: (String?) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PockyColors.Surface,
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.report_text), color = PockyColors.InkMuted)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 500) reason = it },
                    placeholder = { Text(stringResource(R.string.report_reason_hint)) },
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PockyColors.Accent,
                        cursorColor = PockyColors.AccentSoft,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(reason.trim().ifEmpty { null }) }) {
                Text(stringResource(R.string.report_send), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun BlockDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PockyColors.Surface,
        title = { Text(stringResource(R.string.block_title, name)) },
        text = { Text(stringResource(R.string.block_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.block_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifetimeSheet(current: Long?, onSelect: (Long?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PockyColors.Bg,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.lifetime_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PockyColors.Ink,
            )
            Text(
                text = stringResource(R.string.lifetime_body),
                style = MaterialTheme.typography.bodyMedium,
                color = PockyColors.InkMuted,
            )
            LifetimeChoices.forEach { choice ->
                ChoiceCard(
                    title = lifetimeLabel(choice),
                    body = "",
                    selected = lifetimeLabel(current) == lifetimeLabel(choice),
                    onClick = {
                        onSelect(choice)
                        onDismiss()
                    },
                )
            }
        }
    }
}
