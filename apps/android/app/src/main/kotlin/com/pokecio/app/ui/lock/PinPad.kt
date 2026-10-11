package com.pokecio.app.ui.lock

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokecio.app.R
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

const val PIN_LENGTH = 6

@Composable
fun PinDots(filled: Int, error: Boolean) {
    val description = stringResource(R.string.lock_entered, filled, PIN_LENGTH)
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        repeat(PIN_LENGTH) { index ->
            val color by animateColorAsState(
                when {
                    error -> MaterialTheme.colorScheme.error
                    index < filled -> PockyColors.AccentSoft
                    else -> PockyColors.Surface
                },
                label = "pin-dot",
            )
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.25f), CircleShape),
            )
        }
    }
}

@Composable
fun PinPad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    extra: ImageVector? = null,
    extraLabel: String? = null,
    onExtra: () -> Unit = {},
) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { digit -> DigitKey(digit, enabled) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            if (extra != null) {
                IconKey(extra, extraLabel.orEmpty(), enabled, onExtra)
            } else {
                Box(Modifier.size(KEY))
            }
            DigitKey('0', enabled) { onDigit('0') }
            IconKey(PockyIcons.ArrowBack, stringResource(R.string.lock_delete), enabled, onDelete)
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(KEY)
            .clip(CircleShape)
            .background(PockyColors.Surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(digit.toString(), fontSize = 28.sp, fontWeight = FontWeight.Medium, color = PockyColors.Ink)
    }
}

@Composable
private fun IconKey(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(KEY)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
    ) {
        Icon(icon, contentDescription = label, tint = PockyColors.InkMuted, modifier = Modifier.size(26.dp))
    }
}

private val KEY = 74.dp
