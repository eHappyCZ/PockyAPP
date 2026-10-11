package com.pokecio.app.ui.encryption

import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.encryption.SetupProgress
import com.pokecio.app.ui.theme.PockyIcons
import kotlinx.coroutines.launch

@Composable
fun SetupRecoveryScreen(
    progress: SetupProgress,
    onEnable: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    EncryptionScaffold(title = stringResource(R.string.setup_title), onBack = onClose) {
        Spacer(Modifier.height(16.dp))
        Icon(
            PockyIcons.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))

        if (progress is SetupProgress.KeyReady) {
            KeyReady(progress.key, onClose)
        } else {
            PasswordForm(progress, onEnable)
        }
    }
}

@Composable
private fun PasswordForm(progress: SetupProgress, onEnable: (String, String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val busy = progress == SetupProgress.Busy

    Text(
        text = stringResource(R.string.setup_intro),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))

    Column(modifier = Modifier.widthIn(max = 420.dp)) {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.setup_password)) },
            supportingText = { Text(stringResource(R.string.setup_password_hint)) },
            singleLine = true,
            enabled = !busy,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        PassphraseFields(
            passphrase = passphrase,
            onPassphrase = { passphrase = it },
            repeat = repeat,
            onRepeat = { repeat = it },
            enabled = !busy,
        )

        if (progress is SetupProgress.Failed) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(progress.message),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onEnable(password, passphrase.trim()) },
            enabled = password.isNotEmpty() && passphraseReady(passphrase, repeat) && !busy,
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
                Text(stringResource(R.string.setup_submit))
            }
        }
    }
}

@Composable
internal fun KeyReady(key: String, onClose: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.setup_key_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.setup_key_text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))

    Text(
        text = key,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(16.dp),
    )
    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = {
            val clip = ClipData.newPlainText("", key).apply {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            scope.launch { clipboard.setClipEntry(ClipEntry(clip)) }
            copied = true
        },
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(stringResource(if (copied) R.string.setup_copied else R.string.setup_copy))
    }

    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = saved, onCheckedChange = { saved = it })
        Text(
            text = stringResource(R.string.setup_saved),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onClose, enabled = saved, shape = RoundedCornerShape(14.dp)) {
        Text(stringResource(R.string.setup_done))
    }
}
