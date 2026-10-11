package com.pokecio.app.ui.encryption

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.encryption.RecoveryProgress
import com.pokecio.app.ui.theme.PockyIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryScreen(
    progress: RecoveryProgress,
    canUseKey: Boolean,
    canUseDevice: Boolean,
    onRecover: (String) -> Unit,
    onVerifyWithDevice: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    val busy = progress == RecoveryProgress.Busy
    val submit = { if (key.isNotBlank() && !busy) onRecover(key.trim()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(PockyIcons.ArrowBack, stringResource(R.string.back))
                    }
                },
                title = { Text(stringResource(R.string.recovery_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (progress == RecoveryProgress.Done) {
                Done(onClose)
                return@Column
            }

            Icon(
                PockyIcons.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.recovery_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.recovery_where),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            Column(modifier = Modifier.widthIn(max = 420.dp)) {
                if (canUseKey) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { value ->
                            if (value.endsWith("\n")) {
                                key = value.trimEnd('\n')
                                submit()
                            } else {
                                key = value.replace('\n', ' ')
                            }
                        },
                        label = { Text(stringResource(R.string.recovery_key_label)) },
                        enabled = !busy,
                        minLines = 2,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                            autoCorrectEnabled = false,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (progress is RecoveryProgress.Failed) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(progress.message),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = submit,
                        enabled = key.isNotBlank() && !busy,
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
                            Text(stringResource(R.string.recovery_submit))
                        }
                    }
                }

                if (canUseDevice) {
                    if (canUseKey) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.recovery_or),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onVerifyWithDevice,
                        enabled = !busy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(R.string.recovery_with_device))
                    }
                }

                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick = onReset,
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.recovery_lost_key))
                }
            }
        }
    }
}

@Composable
private fun Done(onClose: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    Icon(
        PockyIcons.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.size(64.dp),
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.recovery_done),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.recovery_done_detail),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
    Button(onClick = onClose, shape = RoundedCornerShape(14.dp)) {
        Text(stringResource(R.string.recovery_continue))
    }
}
