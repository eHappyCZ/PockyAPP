package com.pokecio.app.ui.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokecio.app.R
import com.pokecio.app.core.StatusMessage
import com.pokecio.app.ui.components.GradientButton
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import java.time.LocalDate
import java.time.ZoneId

class StatusPerson(
    val roomId: String,
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    val status: String?,
    val aura: AvatarAura,
    val online: Boolean,
)

@Composable
fun StatusStrip(
    ownId: String,
    ownName: String,
    ownAvatar: String?,
    ownStatus: StatusMessage?,
    ownAura: AvatarAura,
    people: List<StatusPerson>,
    onOwn: () -> Unit,
    onPerson: (StatusPerson) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
    ) {
        item(key = "own") {
            StatusItem(
                id = ownId,
                name = stringResource(R.string.status_you),
                avatarUrl = ownAvatar,
                status = ownStatus?.text,
                placeholder = stringResource(R.string.status_add),
                aura = ownAura,
                online = false,
                own = true,
                onClick = onOwn,
                avatarName = ownName,
            )
        }
        items(people, key = { it.userId }) { person ->
            StatusItem(
                id = person.roomId,
                name = person.name.substringBefore(' '),
                avatarUrl = person.avatarUrl,
                status = person.status,
                placeholder = null,
                aura = person.aura,
                online = person.online,
                own = false,
                onClick = { onPerson(person) },
                avatarName = person.name,
            )
        }
    }
}

@Composable
private fun StatusItem(
    id: String,
    name: String,
    avatarUrl: String?,
    status: String?,
    placeholder: String?,
    aura: AvatarAura,
    online: Boolean,
    own: Boolean,
    onClick: () -> Unit,
    avatarName: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(78.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(Modifier.heightIn(min = 46.dp), contentAlignment = Alignment.BottomCenter) {
            when {
                status != null -> StatusBubble(status)
                placeholder != null -> PlaceholderBubble(placeholder)
            }
        }
        Spacer(Modifier.height(2.dp))
        Box {
            AuraAvatar(id = id, name = avatarName, url = avatarUrl, size = 54.dp, aura = aura, onlineDot = online)
            if (own && status == null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .background(PockyColors.Bg, CircleShape)
                        .padding(2.dp)
                        .background(PockyGradient, CircleShape),
                ) {
                    Text("+", color = PockyColors.OnBrand, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = PockyColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusBubble(text: String) {
    val shape = RoundedCornerShape(14.dp)
    val fill = Brush.verticalGradient(listOf(Color(0xFF221D33), PockyColors.Surface))
    val edge = Brush.verticalGradient(listOf(PockyColors.AccentSoft.copy(alpha = 0.55f), PockyColors.Accent.copy(alpha = 0.08f)))

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = PockyColors.Ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp,
            modifier = Modifier
                .widthIn(max = 78.dp)
                .shadow(8.dp, shape, ambientColor = PockyColors.Accent, spotColor = PockyColors.Accent)
                .background(fill, shape)
                .border(1.dp, edge, shape)
                .padding(horizontal = 9.dp, vertical = 6.dp),
        )
        ThoughtTail(fill = fill, edge = PockyColors.AccentSoft.copy(alpha = 0.2f))
    }
}

@Composable
private fun PlaceholderBubble(text: String) {
    val dash = PockyColors.InkMuted.copy(alpha = 0.45f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = PockyColors.InkMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = dash,
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                        ),
                    )
                }
                .padding(horizontal = 9.dp, vertical = 6.dp),
        )
        ThoughtTail(fill = SolidColor(Color.Transparent), edge = dash)
    }
}

@Composable
private fun ThoughtTail(fill: Brush, edge: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .padding(end = 10.dp)
                .size(7.dp)
                .background(fill, CircleShape)
                .border(1.dp, edge, CircleShape),
        )
        Spacer(Modifier.height(1.dp))
        Box(
            Modifier
                .padding(end = 18.dp)
                .size(4.dp)
                .background(fill, CircleShape)
                .border(0.5.dp, edge, CircleShape),
        )
    }
}

@Composable
fun GlowDivider() {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            PockyColors.GradientStart,
                            PockyColors.AccentSoft,
                            PockyColors.GradientEnd,
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(
                    Brush.verticalGradient(listOf(PockyColors.Accent.copy(alpha = 0.16f), Color.Transparent)),
                ),
        )
    }
}

enum class StatusDuration(val label: Int) {
    HOUR(R.string.status_hour),
    FOUR_HOURS(R.string.status_four_hours),
    TODAY(R.string.status_today),
    DAY(R.string.status_day),
    FOREVER(R.string.status_forever);

    fun until(now: Long = System.currentTimeMillis()): Long? = when (this) {
        HOUR -> now + 60 * 60 * 1000L
        FOUR_HOURS -> now + 4 * 60 * 60 * 1000L
        TODAY -> LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        DAY -> now + 24 * 60 * 60 * 1000L
        FOREVER -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatusEditorSheet(
    current: StatusMessage?,
    onSave: (text: String, untilMs: Long?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(current?.text.orEmpty()) }
    var duration by rememberSaveable { mutableStateOf(StatusDuration.DAY) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PockyColors.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.status_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PockyColors.Ink,
            )
            Text(
                text = stringResource(R.string.status_hint),
                style = MaterialTheme.typography.bodySmall,
                color = PockyColors.InkMuted,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= MAX_STATUS) text = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.status_placeholder)) },
                supportingText = { Text("${text.length}/$MAX_STATUS") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PockyColors.Accent,
                    cursorColor = PockyColors.AccentSoft,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                stringArrayResource(R.array.status_suggestions).forEach { suggestion ->
                    Chip(suggestion, selected = text == suggestion) { text = suggestion }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.status_duration),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = PockyColors.InkMuted,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusDuration.entries.forEach { option ->
                    Chip(stringResource(option.label), selected = duration == option) { duration = option }
                }
            }
            Spacer(Modifier.height(20.dp))
            GradientButton(
                text = stringResource(R.string.profile_save),
                enabled = text.isNotBlank(),
                onClick = { onSave(text.trim(), duration.until()) },
            )
            if (current != null) {
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.status_clear), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusCardSheet(person: StatusPerson, onMessage: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PockyColors.Surface,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            AuraAvatar(id = person.roomId, name = person.name, url = person.avatarUrl, size = 84.dp, aura = person.aura, onlineDot = person.online)
            Spacer(Modifier.height(12.dp))
            Text(person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PockyColors.Ink)
            if (person.online) {
                Text(stringResource(R.string.presence_online), style = MaterialTheme.typography.labelMedium, color = PockyColors.Success)
            }
            person.status?.let { status ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium,
                    color = PockyColors.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PockyColors.Bg, RoundedCornerShape(16.dp))
                        .border(1.dp, PockyColors.AccentSoft.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            GradientButton(text = stringResource(R.string.status_message), onClick = onMessage)
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(PockyGradient)
                } else {
                    Modifier
                        .background(PockyColors.Bg)
                        .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.2f), CircleShape)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        if (selected) {
            Icon(PockyIcons.Check, contentDescription = null, tint = PockyColors.OnBrand, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = PockyColors.OnBrand)
    }
}

private const val MAX_STATUS = 60
