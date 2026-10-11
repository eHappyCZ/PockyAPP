package com.pokecio.app.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.TimelineItem
import com.pokecio.app.ui.people.PeopleHeader
import com.pokecio.app.ui.theme.PockyColors
import java.time.Instant
import java.time.ZoneId

class HistoryState(
    val open: Boolean,
    val items: List<TimelineItem>?,
    val loading: Boolean,
    val reachedStart: Boolean,
    val onShow: (Boolean) -> Unit,
    val onLoadOlder: () -> Unit,
)

@Composable
fun RoomHistory(history: HistoryState, onBack: () -> Unit) {
    val changes = remember(history.items) {
        history.items?.let(::buildRows)?.filterIsInstance<RoomRow.State>()?.asReversed()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PockyColors.Bg),
    ) {
        PeopleHeader(
            title = stringResource(R.string.history_title),
            subtitle = null,
            busy = history.loading,
            onBack = onBack,
        )
        if (changes == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PockyColors.AccentSoft)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            changes.forEachIndexed { index, row ->
                val day = dayOf(row.event.timestampMs.toLong())
                if (index == 0 || dayOf(changes[index - 1].event.timestampMs.toLong()) != day) {
                    item(key = "day-${row.key}") {
                        Text(
                            text = dayLabel(row.event.timestampMs.toLong()),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = PockyColors.InkMuted,
                            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp),
                        )
                    }
                }
                item(key = row.key) { HistoryRow(row) }
            }
            if (changes.isEmpty() && history.reachedStart) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PockyColors.InkMuted,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            if (!history.reachedStart) {
                item(key = "older") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.gallery_load_older),
                            fontWeight = FontWeight.SemiBold,
                            color = PockyColors.AccentSoft,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(enabled = !history.loading, role = Role.Button, onClick = history.onLoadOlder)
                                .background(PockyColors.Surface)
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(row: RoomRow.State) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            text = timeLabel(row.event.timestampMs.toLong()),
            style = MaterialTheme.typography.labelMedium,
            color = PockyColors.InkMuted,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stateText(row.event, row.change, row.repeat),
            style = MaterialTheme.typography.bodyMedium,
            color = PockyColors.Ink,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun dayOf(timestampMs: Long) =
    Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).toLocalDate()
