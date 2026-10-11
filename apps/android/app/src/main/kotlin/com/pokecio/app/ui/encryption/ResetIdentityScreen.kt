package com.pokecio.app.ui.encryption

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
import com.pokecio.app.encryption.SetupProgress
import com.pokecio.app.ui.theme.PockyIcons

@Composable
fun ResetIdentityScreen(
    progress: SetupProgress,
    onReset: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    EncryptionScaffold(title = stringResource(R.string.reset_title), onBack = onClose) {
        Spacer(Modifier.height(16.dp))
        if (progress is SetupProgress.KeyReady) {
            Icon(
                PockyIcons.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            KeyReady(progress.key, onClose)
        } else {
            Icon(
                PockyIcons.Alert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            ResetForm(progress, onReset)
        }
    }
}

@Composable
private fun ResetForm(progress: SetupProgress, onReset: (String, String) -> Unit) {
    var understood by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val busy = progress == SetupProgress.Busy

    Text(
        text = stringResource(R.string.reset_intro),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))

    Column(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Consequence(stringResource(R.string.reset_loses_history))
        Spacer(Modifier.height(10.dp))
        Consequence(stringResource(R.string.reset_devices_unverified))
        Spacer(Modifier.height(10.dp))
        Consequence(stringResource(R.string.reset_contacts_warned))
    }

    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.reset_try_first),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 420.dp),
    )

    Spacer(Modifier.height(20.dp))
    Column(modifier = Modifier.widthIn(max = 420.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = understood, onCheckedChange = { understood = it }, enabled = !busy)
            Text(
                text = stringResource(R.string.reset_understood),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (understood) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.setup_password)) },
                supportingText = { Text(stringResource(R.string.reset_password_hint)) },
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
        }

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
            onClick = { onReset(password, passphrase.trim()) },
            enabled = understood && password.isNotEmpty() && passphraseReady(passphrase, repeat) && !busy,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onError,
                )
            } else {
                Text(stringResource(R.string.reset_submit))
            }
        }
    }
}

@Composable
private fun Consequence(text: String) {
    Row {
        Text(
            text = "•",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
