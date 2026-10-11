package com.pokecio.app.rooms

import com.pokecio.app.R
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import com.pokecio.app.core.Room
import com.pokecio.app.core.RoomListListener
import com.pokecio.app.core.SyncStatus
import com.pokecio.app.core.SyncStatusListener
import com.pokecio.app.core.TaskHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoomListController(
    private val client: PockyClient,
    private val scope: CoroutineScope,
) {
    private val _rooms = MutableStateFlow<List<Room>?>(null)
    val rooms: StateFlow<List<Room>?> = _rooms.asStateFlow()

    private val _status = MutableStateFlow(SyncStatus.IDLE)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val handles = mutableListOf<TaskHandle>()

    init {
        scope.launch {
            try {
                handles += client.observeSyncStatus(object : SyncStatusListener {
                    override fun onUpdate(status: SyncStatus) {
                        _status.value = status
                    }
                })
                handles += client.observeRooms(object : RoomListListener {
                    override fun onUpdate(rooms: List<Room>) {
                        _rooms.value = rooms
                    }
                })
            } catch (_: PockyException) {
                _status.value = SyncStatus.ERROR
            }
        }
    }

    private val _problems = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val problems: SharedFlow<Int> = _problems.asSharedFlow()

    fun markRead(roomId: String) = act { client.markRoomRead(roomId) }

    fun setFavourite(roomId: String, favourite: Boolean) = act { client.setFavourite(roomId, favourite) }

    fun setMuted(roomId: String, muted: Boolean) = act { client.setMuted(roomId, muted) }

    fun leave(roomId: String) = act { client.leaveRoom(roomId) }

    private fun act(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            }
        }
    }

    fun start() {
        scope.launch {
            try {
                client.startSync()
            } catch (_: PockyException) {
                _status.value = SyncStatus.ERROR
            }
        }
    }

    fun stop() {
        scope.launch { client.stopSync() }
    }

    fun close() {
        handles.forEach { it.cancel(); it.close() }
        handles.clear()
    }
}
