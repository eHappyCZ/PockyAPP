package com.pokecio.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.ui.theme.PockyColors

@Composable
fun BlockedStep(users: List<String>, onUnblock: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.settings_blocked),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = PockyColors.Ink,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (users.isEmpty()) {
            Text(
                text = stringResource(R.string.blocked_none),
                style = MaterialTheme.typography.bodyMedium,
                color = PockyColors.InkMuted,
            )
        }
        users.forEach { userId ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = userId,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PockyColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onUnblock(userId) }) {
                    Text(stringResource(R.string.blocked_unblock))
                }
            }
        }
    }
}
