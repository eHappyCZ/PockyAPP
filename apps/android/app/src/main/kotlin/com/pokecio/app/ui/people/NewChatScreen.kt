package com.pokecio.app.ui.people

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.Contact
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import kotlinx.coroutines.flow.Flow

@Composable
fun NewChatScreen(
    results: List<Contact>,
    recent: List<Contact>,
    searching: Boolean,
    busy: Boolean,
    problems: Flow<Int>,
    onSearch: (String) -> Unit,
    onPerson: (Contact) -> Unit,
    onNewGroup: () -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val typed = query.trim().length >= 2

    LaunchedEffect(problems) {
        problems.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PockyColors.Bg)
            .imePadding(),
    ) {
        PeopleHeader(stringResource(R.string.new_chat_title), null, busy || searching, onBack)
        PeopleSearchField(
            query = query,
            onQueryChange = {
                query = it
                onSearch(it)
            },
            hint = stringResource(R.string.new_chat_search_hint),
        )

        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "group") {
                NewGroupRow(onClick = onNewGroup)
            }
            if (typed) {
                item(key = "results-label") { SectionLabel(stringResource(R.string.new_chat_people)) }
                if (results.isEmpty() && !searching) {
                    item(key = "nobody") { PeopleHint(stringResource(R.string.new_chat_nobody)) }
                }
                items(results, key = { "r-" + it.userId }) { contact ->
                    PersonRow(contact, onClick = { if (!busy) onPerson(contact) })
                }
            } else if (recent.isNotEmpty()) {
                item(key = "recent-label") { SectionLabel(stringResource(R.string.new_chat_recent)) }
                items(recent, key = { "c-" + it.userId }) { contact ->
                    PersonRow(contact, onClick = { if (!busy) onPerson(contact) })
                }
            } else {
                item(key = "hint") { PeopleHint(stringResource(R.string.new_chat_hint)) }
            }
        }
    }
}

@Composable
private fun NewGroupRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .background(PockyGradient, RoundedCornerShape(14.dp)),
        ) {
            Icon(PockyIcons.Group, contentDescription = null, tint = PockyColors.OnBrand, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.new_group_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PockyColors.Ink,
            )
            Text(
                text = stringResource(R.string.new_group_row_hint),
                style = MaterialTheme.typography.bodySmall,
                color = PockyColors.InkMuted,
            )
        }
        Icon(PockyIcons.ChevronRight, contentDescription = null, tint = PockyColors.InkMuted)
    }
}
