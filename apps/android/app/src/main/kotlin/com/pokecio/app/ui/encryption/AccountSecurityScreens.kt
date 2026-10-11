package com.pokecio.app.ui.encryption

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.encryption.PasswordProgress
import com.pokecio.app.encryption.SetupProgress
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

private const val MIN_PASSWORD = 8

@Composable
fun ChangePassphraseScreen(
    progress: SetupProgress,
    available: Boolean,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val busy = progress == SetupProgress.Busy

    EncryptionScaffold(title = stringResource(R.string.passphrase_change_title), onBack = onClose) {
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
            return@EncryptionScaffold
        }

        Text(
            text = stringResource(if (available) R.string.passphrase_change_intro else R.string.passphrase_change_unavailable),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (!available) return@EncryptionScaffold

        Spacer(Modifier.height(24.dp))
        Column(Modifier.widthIn(max = 420.dp)) {
            PassphraseFields(
                passphrase = passphrase,
                onPassphrase = { passphrase = it },
                repeat = repeat,
                onRepeat = { repeat = it },
                enabled = !busy,
            )
            if (progress is SetupProgress.Failed) {
                Spacer(Modifier.height(8.dp))
                ErrorText(stringResource(progress.message))
            }
            Spacer(Modifier.height(24.dp))
            SubmitButton(
                label = stringResource(R.string.passphrase_change_submit),
                busy = busy,
                enabled = passphraseReady(passphrase, repeat),
                onClick = { onSave(passphrase.trim()) },
            )
        }
    }
}

@Composable
fun ChangePasswordScreen(
    progress: PasswordProgress,
    onSave: (String, String, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var signOutOthers by remember { mutableStateOf(false) }
    val busy = progress == PasswordProgress.Busy
    val mismatch = repeat.isNotEmpty() && repeat != password

    EncryptionScaffold(title = stringResource(R.string.password_change_title), onBack = onClose) {
        Spacer(Modifier.height(16.dp))
        Icon(
            PockyIcons.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))

        if (progress == PasswordProgress.Done) {
            Text(
                text = stringResource(R.string.password_done),
                style = MaterialTheme.typography.titleMedium,
                color = PockyColors.Success,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onClose, shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.setup_done))
            }
            return@EncryptionScaffold
        }

        Column(Modifier.widthIn(max = 420.dp)) {
            SecretField(current, { current = it }, stringResource(R.string.password_current), enabled = !busy)
            Spacer(Modifier.height(8.dp))
            SecretField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.password_new),
                enabled = !busy,
                supporting = stringResource(R.string.password_hint),
            )
            Spacer(Modifier.height(8.dp))
            SecretField(
                value = repeat,
                onValueChange = { repeat = it },
                label = stringResource(R.string.password_repeat),
                enabled = !busy,
                supporting = if (mismatch) stringResource(R.string.password_mismatch) else null,
                isError = mismatch,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = signOutOthers, onCheckedChange = { signOutOthers = it }, enabled = !busy)
                Text(
                    text = stringResource(R.string.password_sign_out_others),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.password_sign_out_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress is PasswordProgress.Failed) {
                Spacer(Modifier.height(8.dp))
                ErrorText(stringResource(progress.message))
            }
            Spacer(Modifier.height(24.dp))
            SubmitButton(
                label = stringResource(R.string.password_submit),
                busy = busy,
                enabled = current.isNotEmpty() && password.length >= MIN_PASSWORD && password == repeat,
                onClick = { onSave(current, password, signOutOthers) },
            )
        }
    }
}

@Composable
fun DeleteAccountScreen(
    userId: String,
    progress: PasswordProgress,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val word = stringResource(R.string.account_delete_confirm_word)
    val busy = progress == PasswordProgress.Busy

    EncryptionScaffold(title = stringResource(R.string.account_delete_title), onBack = onClose) {
        Spacer(Modifier.height(16.dp))
        Icon(
            PockyIcons.Trash,
            contentDescription = null,
            tint = PockyColors.Danger,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.account_delete_intro, userId),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(20.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            listOf(
                R.string.account_delete_gone,
                R.string.account_delete_profile,
                R.string.account_delete_messages,
                R.string.account_delete_device,
            ).forEach { line ->
                Text(
                    text = "•  " + stringResource(line),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.widthIn(max = 420.dp)) {
            SecretField(password, { password = it }, stringResource(R.string.password_current), enabled = !busy)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text(stringResource(R.string.account_delete_confirm, word)) },
                singleLine = true,
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (progress is PasswordProgress.Failed) {
                Spacer(Modifier.height(8.dp))
                ErrorText(stringResource(progress.message))
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onDelete(password) },
                enabled = password.isNotEmpty() && confirm.trim() == word && !busy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PockyColors.Danger,
                    contentColor = PockyColors.Ink,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = PockyColors.Ink,
                    )
                } else {
                    Text(stringResource(R.string.account_delete_submit))
                }
            }
        }
    }
}

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    supporting: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SubmitButton(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
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
            Text(label)
        }
    }
}
