package com.pokecio.app.rooms

import com.pokecio.app.R
import com.pokecio.app.core.Pinned
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoomModerationController(
    private val client: PockyClient,
    private val roomId: String,
    private val scope: CoroutineScope,
) {
    private val _pinned = MutableStateFlow<List<Pinned>>(emptyList())
    val pinned: StateFlow<List<Pinned>> = _pinned.asStateFlow()

    private val _canPin = MutableStateFlow(false)
    val canPin: StateFlow<Boolean> = _canPin.asStateFlow()

    private val _lifetime = MutableStateFlow<Long?>(null)
    val lifetime: StateFlow<Long?> = _lifetime.asStateFlow()

    private val _canSetLifetime = MutableStateFlow(false)
    val canSetLifetime: StateFlow<Boolean> = _canSetLifetime.asStateFlow()

    private val _notices = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val notices: SharedFlow<Int> = _notices.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            try {
                _pinned.value = client.pinnedMessages(roomId)
            } catch (_: PockyException) {
            }
            try {
                _canPin.value = client.canPinMessages(roomId)
                _canSetLifetime.value = client.canSetMessageLifetime(roomId)
                _lifetime.value = client.messageLifetime(roomId)?.toLong()
            } catch (_: PockyException) {
            }
        }
    }

    fun report(eventId: String, reason: String?) {
        scope.launch {
            _notices.tryEmit(
                try {
                    client.reportMessage(roomId, eventId, reason?.takeIf { it.isNotBlank() })
                    R.string.report_done
                } catch (_: PockyException) {
                    R.string.room_action_failed
                },
            )
        }
    }

    fun block(userId: String) {
        scope.launch {
            _notices.tryEmit(
                try {
                    client.blockUser(userId)
                    R.string.block_done
                } catch (_: PockyException) {
                    R.string.room_action_failed
                },
            )
        }
    }

    fun pin(eventId: String) = change { client.pinMessage(roomId, eventId) }

    fun unpin(eventId: String) = change { client.unpinMessage(roomId, eventId) }

    fun setLifetime(seconds: Long?) = change { client.setMessageLifetime(roomId, seconds?.toULong()) }

    private fun change(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                refresh()
            } catch (_: PockyException) {
                _notices.tryEmit(R.string.room_action_failed)
            }
        }
    }
}
