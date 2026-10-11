package com.pokecio.app.ui.room

import com.pokecio.app.core.TimelineContent
import com.pokecio.app.core.TimelineEvent
import com.pokecio.app.core.TimelineItem
import com.pokecio.app.core.TimelineItemKind
import com.pokecio.app.core.TimelineStateChange

sealed interface RoomRow {
    val key: String

    data class Day(override val key: String, val timestampMs: Long) : RoomRow
    data class NewMessages(override val key: String) : RoomRow
    data class Start(override val key: String) : RoomRow

    data class State(
        override val key: String,
        val event: TimelineEvent,
        val change: TimelineStateChange,
        val repeat: Int = 1,
    ) : RoomRow

    data class Message(
        override val key: String,
        val event: TimelineEvent,
        val firstInGroup: Boolean = true,
        val lastInGroup: Boolean = true,
        val read: Boolean = false,
    ) : RoomRow
}

private const val GROUP_WINDOW_MS = 5 * 60 * 1000L

fun buildRows(items: List<TimelineItem>, readReceipts: Boolean = true): List<RoomRow> {
    val lastReadIndex = if (!readReceipts) -1 else items.indexOfLast { item ->
        val kind = item.kind
        kind is TimelineItemKind.Event &&
            (kind.event.readBy.isNotEmpty() || (!kind.event.isOwn && kind.event.content !is TimelineContent.State))
    }

    val rows = ArrayList<RoomRow>(items.size)

    items.forEachIndexed { index, item ->
        when (val kind = item.kind) {
            is TimelineItemKind.DateDivider -> rows += RoomRow.Day(item.id, kind.timestampMs.toLong())
            TimelineItemKind.ReadMarker -> rows += RoomRow.NewMessages(item.id)
            TimelineItemKind.TimelineStart -> rows += RoomRow.Start(item.id)
            is TimelineItemKind.Event -> {
                val event = kind.event
                val content = event.content

                if (content is TimelineContent.State) {
                    val previous = rows.lastOrNull()
                    if (previous is RoomRow.State &&
                        previous.event.senderId == event.senderId &&
                        previous.change::class == content.change::class
                    ) {
                        rows[rows.lastIndex] = previous.copy(
                            event = event,
                            change = content.change,
                            repeat = previous.repeat + 1,
                        )
                    } else {
                        rows += RoomRow.State(item.id, event, content.change)
                    }
                    return@forEachIndexed
                }

                val previous = rows.lastOrNull()
                val continues = previous is RoomRow.Message &&
                    previous.event.senderId == event.senderId &&
                    event.timestampMs.toLong() - previous.event.timestampMs.toLong() < GROUP_WINDOW_MS

                if (continues) {
                    rows[rows.lastIndex] = previous.copy(lastInGroup = false)
                }

                rows += RoomRow.Message(
                    key = item.id,
                    event = event,
                    firstInGroup = !continues,
                    read = event.isOwn && index <= lastReadIndex,
                )
            }
        }
    }

    return rows
}
