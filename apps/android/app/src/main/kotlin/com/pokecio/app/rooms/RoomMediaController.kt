package com.pokecio.app.rooms

import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import com.pokecio.app.core.RoomTimeline
import com.pokecio.app.core.TaskHandle
import com.pokecio.app.core.TimelineChange
import com.pokecio.app.core.TimelineItem
import com.pokecio.app.core.TimelineListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoomMediaController(
    private val client: PockyClient,
    private val roomId: String,
    private val scope: CoroutineScope,
) {
    private val _items = MutableStateFlow<List<TimelineItem>?>(null)
    val items: StateFlow<List<TimelineItem>?> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _complete = MutableStateFlow(false)
    val complete: StateFlow<Boolean> = _complete.asStateFlow()

    private val buffer = mutableListOf<TimelineItem>()
    private var timeline: RoomTimeline? = null
    private var handle: TaskHandle? = null
    private var job: Job? = null

    fun open() {
        if (job?.isActive == true || timeline != null) return
        job = scope.launch {
            _loading.value = true
            try {
                val opened = client.roomMedia(roomId)
                timeline = opened
                handle = opened.observe(object : TimelineListener {
                    override fun onChanges(changes: List<TimelineChange>) {
                        synchronized(buffer) {
                            buffer.applyChanges(changes)
                            _items.value = buffer.toList()
                        }
                    }
                })
                while (!_complete.value) {
                    _complete.value = opened.paginateBackwards()
                    delay(PAGE_PAUSE_MS)
                }
            } catch (_: PockyException) {
                if (_items.value == null) _items.value = emptyList()
                _complete.value = true
            } finally {
                _loading.value = false
            }
        }
    }

    fun close() {
        job?.cancel()
        job = null
        handle?.cancel()
        handle?.close()
        handle = null
        timeline?.close()
        timeline = null
    }

    private companion object {
        const val PAGE_PAUSE_MS = 150L
    }
}
