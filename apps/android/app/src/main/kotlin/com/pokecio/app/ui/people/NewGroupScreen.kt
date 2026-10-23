package com.pokecio.app.ui.people

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.Contact
import com.pokecio.app.ui.components.GradientButton
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.rooms.RoomAvatar
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import kotlinx.coroutines.flow.Flow

private const val MAX_GROUP_NAME = 64

@Composable
fun NewGroupScreen(
    results: List<Contact>,
    recent: List<Contact>,
    searching: Boolean,
    busy: Boolean,
    problems: Flow<Int>,
    onSearch: (String) -> Unit,
    onCreate: (name: String, members: List<String>) -> Unit,
    onBack: () -> Unit,
    onInvite: ((members: List<String>) -> Unit)? = null,
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Contact>() }
    val context = LocalContext.current

    LaunchedEffect(problems) {
        problems.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    BackHandler(enabled = naming) { naming = false }

    val toggle: (Contact) -> Unit = { contact ->
        val index = selected.indexOfFirst { it.userId == contact.userId }
        if (index >= 0) selected.removeAt(index) else selected.add(contact)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PockyColors.Bg)
            .imePadding(),
    ) {
        PeopleHeader(
            title = stringResource(if (onInvite != null) R.string.room_info_add_people else R.string.new_group_title),
            subtitle = stringResource(if (naming) R.string.new_group_step_name else R.string.new_group_step_people),
            busy = busy || searching,
            onBack = { if (naming) naming = false else onBack() },
        )

        if (!naming) {
            PeopleSearchField(
                query = query,
                onQueryChange = {
                    query = it
                    onSearch(it)
                },
                hint = stringResource(R.string.new_chat_search_hint),
            )
            AnimatedVisibility(visible = selected.isNotEmpty()) {
                SelectedStrip(selected, onRemove = toggle)
            }
            val list = if (query.trim().length >= 2) results else recent
            LazyColumn(Modifier.weight(1f)) {
                if (list.isEmpty()) {
                    item(key = "hint") {
                        PeopleHint(
                            stringResource(if (query.trim().length >= 2) R.string.new_chat_nobody else R.string.new_chat_hint),
                        )
                    }
                }
                items(list, key = { it.userId }) { contact ->
                    PersonRow(contact, selected = selected.any { it.userId == contact.userId }, onClick = { toggle(contact) })
                }
            }
            Box(
                Modifier
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                if (onInvite != null) {
                    GradientButton(
                        text = stringResource(R.string.room_info_invite_count, selected.size),
                        enabled = selected.isNotEmpty(),
                        busy = busy,
                        onClick = { onInvite(selected.map { it.userId }) },
                    )
                } else {
                    GradientButton(
                        text = if (selected.isEmpty()) {
                            stringResource(R.string.new_group_next)
                        } else {
                            stringResource(R.string.new_group_next_count, selected.size)
                        },
                        onClick = { naming = true },
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(96.dp)
                        .background(PockyGradient, RoundedCornerShape(30.dp)),
                ) {
                    Icon(PockyIcons.Group, contentDescription = null, tint = PockyColors.OnBrand, modifier = Modifier.size(42.dp))
                }
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= MAX_GROUP_NAME) name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.new_group_name)) },
                    supportingText = { Text("${name.length}/$MAX_GROUP_NAME") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PockyColors.Accent,
                        cursorColor = PockyColors.AccentSoft,
                        focusedLabelColor = PockyColors.AccentSoft,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (selected.isEmpty()) {
                        stringResource(R.string.new_group_nobody_invited)
                    } else {
                        pluralStringResource(R.plurals.new_group_invites, selected.size, selected.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = PockyColors.InkMuted,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.new_group_encrypted),
                    style = MaterialTheme.typography.bodySmall,
                    color = PockyColors.InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Box(
                Modifier
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                GradientButton(
                    text = stringResource(R.string.new_group_create),
                    enabled = name.isNotBlank(),
                    busy = busy,
                    onClick = { onCreate(name.trim(), selected.map { it.userId }) },
                )
            }
        }
    }
}

@Composable
private fun SelectedStrip(selected: List<Contact>, onRemove: (Contact) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        items(selected, key = { it.userId }) { contact ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(58.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { onRemove(contact) },
            ) {
                Box {
                    RoomAvatar(id = contact.userId, name = contact.label, url = contact.avatarUrl, size = 48.dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .background(PockyColors.Surface, CircleShape),
                    ) {
                        Icon(PockyIcons.Close, contentDescription = null, tint = PockyColors.Ink, modifier = Modifier.size(11.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = contact.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = PockyColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
