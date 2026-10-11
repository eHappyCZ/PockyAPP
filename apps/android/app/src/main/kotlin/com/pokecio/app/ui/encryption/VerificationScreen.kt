package com.pokecio.app.ui.encryption

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokecio.app.R
import com.pokecio.app.core.VerificationEmoji
import com.pokecio.app.core.VerificationStep
import com.pokecio.app.ui.theme.PockyIcons

@Composable
fun VerificationScreen(
    step: VerificationStep,
    busy: Boolean,
    person: String?,
    onAccept: () -> Unit,
    onMatch: () -> Unit,
    onMismatch: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val finished = step is VerificationStep.Done || step is VerificationStep.Cancelled
    val icon = if (person == null) PockyIcons.Device else PockyIcons.User
    fun text(own: Int, other: Int) = if (person == null) own else other

    EncryptionScaffold(
        title = stringResource(text(R.string.verify_title, R.string.verify_user_title)),
        onBack = if (finished) onClose else onCancel,
    ) {
        when (step) {
            VerificationStep.Requested -> Waiting(
                icon = icon,
                text = if (person == null) {
                    stringResource(R.string.verify_requested)
                } else {
                    stringResource(R.string.verify_user_requested, person)
                },
                onCancel = onCancel,
            )
            VerificationStep.Incoming -> {
                Header(
                    icon,
                    if (person == null) {
                        stringResource(R.string.verify_incoming)
                    } else {
                        stringResource(R.string.verify_user_incoming, person)
                    },
                )
                Spacer(Modifier.height(32.dp))
                Actions(
                    primary = stringResource(R.string.verify_accept),
                    secondary = stringResource(R.string.verify_decline),
                    busy = busy,
                    onPrimary = onAccept,
                    onSecondary = onCancel,
                )
            }
            VerificationStep.Ready, VerificationStep.Starting -> Waiting(
                icon = icon,
                text = stringResource(text(R.string.verify_waiting, R.string.verify_user_waiting)),
                onCancel = onCancel,
            )
            is VerificationStep.Emojis -> {
                Text(
                    text = stringResource(text(R.string.verify_compare, R.string.verify_user_compare)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                EmojiGrid(step.emojis)
                Spacer(Modifier.height(32.dp))
                Actions(
                    primary = stringResource(R.string.verify_match),
                    secondary = stringResource(R.string.verify_mismatch),
                    busy = busy,
                    onPrimary = onMatch,
                    onSecondary = onMismatch,
                )
            }
            VerificationStep.Confirmed -> Waiting(
                icon = PockyIcons.CheckCircle,
                text = stringResource(text(R.string.verify_confirmed, R.string.verify_user_confirmed)),
                onCancel = onCancel,
            )
            VerificationStep.Done -> {
                Header(
                    PockyIcons.CheckCircle,
                    stringResource(text(R.string.verify_done, R.string.verify_user_done)),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(text(R.string.verify_done_detail, R.string.verify_user_done_detail)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = onClose, shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.recovery_continue))
                }
            }
            is VerificationStep.Cancelled -> {
                Header(
                    PockyIcons.Close,
                    stringResource(R.string.verify_cancelled),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = onClose, shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.verify_close))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Header(
    icon: ImageVector,
    text: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Spacer(Modifier.height(32.dp))
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(56.dp))
    Spacer(Modifier.height(16.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ColumnScope.Waiting(icon: ImageVector, text: String, onCancel: () -> Unit) {
    Header(icon, text)
    Spacer(Modifier.height(24.dp))
    CircularProgressIndicator()
    Spacer(Modifier.height(32.dp))
    OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(14.dp)) {
        Text(stringResource(R.string.verify_cancel))
    }
}

@Composable
private fun Actions(
    primary: String,
    secondary: String,
    busy: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    Column(modifier = Modifier.widthIn(max = 420.dp)) {
        Button(
            onClick = onPrimary,
            enabled = !busy,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(primary)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSecondary,
            enabled = !busy,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Text(secondary)
        }
    }
}

@Composable
private fun EmojiGrid(emojis: List<VerificationEmoji>) {
    val names = stringArrayResource(R.array.sas_emoji_names)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        emojis.chunked(4).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                row.forEach { emoji ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                            .padding(vertical = 10.dp),
                    ) {
                        Text(text = emoji.symbol, fontSize = 34.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = names.getOrNull(emoji.index.toInt()) ?: emoji.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
