package com.pokecio.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokecio.app.ui.components.ControlShape
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

@Composable
fun IconBubble(icon: ImageVector, tint: Color = PockyColors.AccentSoft) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .background(tint.copy(alpha = 0.14f), CircleShape),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun PromiseRow(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        IconBubble(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.padding(top = 2.dp)) {
            CardTitle(title)
            Spacer(Modifier.height(2.dp))
            CardBody(body)
        }
    }
}

@Composable
fun ActionCard(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    CardRow(
        modifier = Modifier
            .clip(ControlShape)
            .background(PockyColors.Surface)
            .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.12f), ControlShape)
            .clickable(role = Role.Button, onClick = onClick),
        leading = { IconBubble(icon) },
        title = title,
        body = body,
        trailing = {
            Icon(PockyIcons.ChevronRight, contentDescription = null, tint = PockyColors.InkMuted)
        },
    )
}

@Composable
fun NoticeCard(icon: ImageVector, title: String, body: String, tint: Color = PockyColors.AccentSoft) {
    CardRow(
        modifier = Modifier
            .clip(ControlShape)
            .background(tint.copy(alpha = 0.08f))
            .border(1.dp, tint.copy(alpha = 0.25f), ControlShape),
        leading = { IconBubble(icon, tint) },
        title = title,
        body = body,
    )
}

@Composable
fun ChoiceCard(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    badge: String? = null,
) {
    val border = if (selected) {
        Modifier.border(1.5.dp, PockyGradient, ControlShape)
    } else {
        Modifier.border(1.dp, PockyColors.InkMuted.copy(alpha = 0.12f), ControlShape)
    }

    CardRow(
        modifier = Modifier
            .clip(ControlShape)
            .background(if (selected) PockyColors.Accent.copy(alpha = 0.10f) else PockyColors.Surface)
            .then(border)
            .clickable(role = Role.RadioButton, onClick = onClick),
        leading = icon?.let { { IconBubble(it) } },
        title = title,
        body = body,
        badge = badge,
        trailing = { SelectionMark(selected) },
    )
}

@Composable
fun ToggleCard(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    CardRow(
        modifier = Modifier
            .clip(ControlShape)
            .background(PockyColors.Surface)
            .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.12f), ControlShape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        leading = null,
        title = title,
        body = body,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PockyColors.Ink,
                    checkedTrackColor = PockyColors.Accent,
                    uncheckedThumbColor = PockyColors.InkMuted,
                    uncheckedTrackColor = PockyColors.Bg,
                    uncheckedBorderColor = PockyColors.InkMuted.copy(alpha = 0.4f),
                ),
            )
        },
    )
}

@Composable
private fun SelectionMark(selected: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(PockyGradient)
                } else {
                    Modifier.border(1.5.dp, PockyColors.InkMuted.copy(alpha = 0.4f), CircleShape)
                },
            ),
    ) {
        if (selected) {
            Icon(PockyIcons.Check, contentDescription = null, tint = PockyColors.Ink, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun CardRow(
    modifier: Modifier,
    leading: (@Composable () -> Unit)?,
    title: String,
    body: String,
    badge: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .padding(16.dp),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            CardTitle(title)
            Spacer(Modifier.height(2.dp))
            CardBody(body)
            if (badge != null) {
                Spacer(Modifier.height(8.dp))
                Badge(badge)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = PockyColors.Success,
        modifier = Modifier
            .background(PockyColors.Success.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = PockyColors.Ink,
    )
}

@Composable
private fun CardBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = PockyColors.InkMuted,
    )
}
