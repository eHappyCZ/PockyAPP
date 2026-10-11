package com.pokecio.app.rooms

import com.pokecio.app.core.TimelineChange
import com.pokecio.app.core.TimelineItem

fun MutableList<TimelineItem>.applyChanges(changes: List<TimelineChange>) {
    for (change in changes) {
        when (change) {
            is TimelineChange.Reset -> {
                clear()
                addAll(change.items)
            }
            is TimelineChange.Append -> addAll(change.items)
            TimelineChange.Clear -> clear()
            is TimelineChange.PushFront -> add(0, change.item)
            is TimelineChange.PushBack -> add(change.item)
            TimelineChange.PopFront -> removeFirstOrNull()
            TimelineChange.PopBack -> removeLastOrNull()
            is TimelineChange.Insert -> add(change.index.toInt(), change.item)
            is TimelineChange.Set -> this[change.index.toInt()] = change.item
            is TimelineChange.Remove -> removeAt(change.index.toInt())
            is TimelineChange.Truncate -> {
                while (size > change.length.toInt()) removeLast()
            }
        }
    }
}
