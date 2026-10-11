package com.pokecio.app.ui.encryption

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pokecio.app.R
import com.pokecio.app.core.LogoutWarning

@Composable
fun LogoutDialog(
    warning: LogoutWarning,
    canBackup: Boolean,
    onConfirm: () -> Unit,
    onBackup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val danger = warning.losesHistory

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(stringResource(if (danger) R.string.logout_danger_title else R.string.logout_title))
        },
        text = {
            Text(stringResource(if (danger) R.string.logout_danger_text else R.string.logout_text))
        },
        confirmButton = {
            Column {
                if (danger && canBackup) {
                    TextButton(onClick = onBackup) {
                        Text(stringResource(R.string.logout_backup_first))
                    }
                }
                TextButton(onClick = onConfirm) {
                    Text(
                        text = stringResource(if (danger) R.string.logout_anyway else R.string.logout_confirm),
                        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
