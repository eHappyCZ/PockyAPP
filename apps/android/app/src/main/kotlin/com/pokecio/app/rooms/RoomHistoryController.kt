package com.pokecio.app.rooms

import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import com.pokecio.app.core.RoomTimeline
import com.pokecio.app.core.TaskHandle
import com.pokecio.app.core.TimelineChange
import com.pokecio.app.core.TimelineContent
import com.pokecio.app.core.TimelineItem
import com.pokecio.app.core.TimelineItemKind
import com.pokecio.app.core.TimelineListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoomHistoryController(
    private val client: PockyClient,
    private val roomId: String,
    private val scope: CoroutineScope,
) {
    private val _items = MutableStateFlow<List<TimelineItem>?>(null)
    val items: StateFlow<List<TimelineItem>?> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _reachedStart = MutableStateFlow(false)
    val reachedStart: StateFlow<Boolean> = _reachedStart.asStateFlow()

    private val buffer = mutableListOf<TimelineItem>()
    private var timeline: RoomTimeline? = null
    private var handle: TaskHandle? = null
    private var opening: Job? = null

    fun open() {
        if (timeline != null || opening?.isActive == true) return
        opening = scope.launch {
            try {
                val opened = client.roomHistory(roomId)
                timeline = opened
                handle = opened.observe(object : TimelineListener {
                    override fun onChanges(changes: List<TimelineChange>) {
                        synchronized(buffer) {
                            buffer.applyChanges(changes)
                            _items.value = buffer.toList()
                        }
                    }
                })
                fill()
            } catch (_: PockyException) {
                _items.value = emptyList()
                _reachedStart.value = true
            }
        }
    }

    fun loadOlder() {
        if (_loading.value || _reachedStart.value) return
        scope.launch { fill() }
    }

    fun close() {
        opening?.cancel()
        opening = null
        handle?.cancel()
        handle?.close()
        handle = null
        timeline?.close()
        timeline = null
        synchronized(buffer) { buffer.clear() }
        _items.value = null
        _reachedStart.value = false
    }

    private suspend fun fill() {
        val current = timeline ?: return
        _loading.value = true
        try {
            val target = changeCount() + PAGE_CHANGES
            var pages = 0
            while (!_reachedStart.value && changeCount() < target && pages < MAX_PAGES) {
                _reachedStart.value = current.paginateBackwards()
                pages++
            }
        } catch (_: PockyException) {
        } finally {
            _loading.value = false
        }
    }

    private fun changeCount(): Int = synchronized(buffer) {
        buffer.count { item ->
            val kind = item.kind
            kind is TimelineItemKind.Event && kind.event.content is TimelineContent.State
        }
    }

    private companion object {
        const val PAGE_CHANGES = 20
        const val MAX_PAGES = 10
    }
}
