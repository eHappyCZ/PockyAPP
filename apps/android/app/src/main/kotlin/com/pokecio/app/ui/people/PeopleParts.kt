package com.pokecio.app.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokecio.app.R
import com.pokecio.app.core.Contact
import com.pokecio.app.core.Room
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.room.displayName
import com.pokecio.app.ui.rooms.RoomAvatar
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

fun Room.asContact(): Contact? = directUserId?.let { Contact(userId = it, displayName = name, avatarUrl = avatarUrl) }

val Contact.label: String get() = displayName(userId, displayName)

@Composable
fun PeopleHeader(title: String, subtitle: String?, busy: Boolean, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack),
        ) {
            Icon(PockyIcons.ArrowBack, contentDescription = stringResource(R.string.back), tint = PockyColors.Ink)
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PockyColors.Ink,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelLarge, color = PockyColors.InkMuted)
            }
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = PockyColors.AccentSoft)
        }
    }
}

@Composable
fun PeopleSearchField(query: String, onQueryChange: (String) -> Unit, hint: String) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(PockyColors.Surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Icon(PockyIcons.Search, contentDescription = null, tint = PockyColors.InkMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(hint, color = PockyColors.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = PockyColors.Ink, fontSize = 16.sp),
                cursorBrush = SolidColor(PockyColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = PockyColors.InkMuted,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
fun PersonRow(contact: Contact, selected: Boolean? = null, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        RoomAvatar(id = contact.userId, name = contact.label, url = contact.avatarUrl, size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = contact.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PockyColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = contact.userId,
                style = MaterialTheme.typography.bodySmall,
                color = PockyColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected != null) {
            Spacer(Modifier.width(12.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.background(PockyGradient)
                        } else {
                            Modifier.background(PockyColors.InkMuted.copy(alpha = 0.18f))
                        },
                    ),
            ) {
                if (selected) {
                    Icon(PockyIcons.Check, contentDescription = null, tint = PockyColors.OnBrand, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
fun PeopleHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = PockyColors.InkMuted,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
    )
}

enum class PeopleFlow { NONE, NEW_CHAT, NEW_GROUP, INVITE }
