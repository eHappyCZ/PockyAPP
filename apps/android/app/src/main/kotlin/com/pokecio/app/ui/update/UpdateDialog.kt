package com.pokecio.app.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pokecio.app.R
import com.pokecio.app.core.AppUpdate
import com.pokecio.app.ui.components.GradientButton
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.rooms.AppLocale
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import com.pokecio.app.update.UpdatePhase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun UpdateDialog(update: AppUpdate, phase: UpdatePhase, onUpdate: () -> Unit, onLater: () -> Unit) {
    val working = phase is UpdatePhase.Downloading || phase is UpdatePhase.Installing

    Dialog(onDismissRequest = { if (!working) onLater() }) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(PockyColors.Surface)
                .padding(24.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .background(PockyGradient, CircleShape),
            ) {
                Icon(PockyIcons.Download, contentDescription = null, tint = PockyColors.OnBrand, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.update_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PockyColors.Ink,
            )
            Text(
                listOfNotNull(update.versionName, published(update.published), megabytes(update.size?.toLong()))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = PockyColors.InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )

            Column(
                modifier = Modifier
                    .padding(top = 18.dp)
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Notes(stringResource(R.string.update_new), update.added, "✨")
                Notes(stringResource(R.string.update_fixed), update.fixed, "🔧")
                if (update.added.isEmpty() && update.fixed.isEmpty()) {
                    Text(stringResource(R.string.update_no_notes), color = PockyColors.InkMuted)
                }
            }

            Spacer(Modifier.height(18.dp))
            when (phase) {
                is UpdatePhase.Downloading -> {
                    val progress = phase.progress
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            color = PockyColors.Accent,
                            trackColor = PockyColors.Bg,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        )
                    } else {
                        LinearProgressIndicator(
                            color = PockyColors.Accent,
                            trackColor = PockyColors.Bg,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        )
                    }
                    Text(
                        if (progress != null) {
                            stringResource(R.string.update_downloading_percent, (progress * 100).toInt())
                        } else {
                            stringResource(R.string.update_downloading)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = PockyColors.InkMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                is UpdatePhase.Installing -> Text(
                    stringResource(R.string.update_installing),
                    style = MaterialTheme.typography.bodySmall,
                    color = PockyColors.InkMuted,
                )
                is UpdatePhase.Failed -> Text(
                    stringResource(phase.message),
                    style = MaterialTheme.typography.bodySmall,
                    color = PockyColors.Gold,
                    textAlign = TextAlign.Center,
                )
                else -> Unit
            }

            Spacer(Modifier.height(14.dp))
            GradientButton(
                text = stringResource(R.string.update_install),
                onClick = onUpdate,
                busy = working,
            )
            if (!working) {
                TextButton(onClick = onLater, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.update_later), color = PockyColors.InkMuted)
                }
            }
        }
    }
}

@Composable
private fun Notes(title: String, items: List<String>, mark: String) {
    if (items.isEmpty()) return
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = PockyColors.AccentSoft,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    items.forEach { item ->
        Row(modifier = Modifier.padding(bottom = 6.dp)) {
            Text(mark, modifier = Modifier.width(26.dp))
            Text(item, style = MaterialTheme.typography.bodyMedium, color = PockyColors.Ink)
        }
    }
    Spacer(Modifier.height(10.dp))
}

private fun published(date: String?): String? = date?.let {
    runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d. MMMM yyyy", AppLocale)) }.getOrNull()
}

private fun megabytes(bytes: Long?): String? = bytes?.let { "%.0f MB".format(AppLocale, it / 1_048_576.0) }
