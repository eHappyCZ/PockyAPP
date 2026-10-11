package com.pokecio.app.rooms

import com.pokecio.app.R
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import com.pokecio.app.core.RoomInfo
import com.pokecio.app.core.UserTrust
import com.pokecio.app.media.toJpeg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoomInfoController(
    private val client: PockyClient,
    private val scope: CoroutineScope,
) {
    private val _info = MutableStateFlow<RoomInfo?>(null)
    val info: StateFlow<RoomInfo?> = _info.asStateFlow()

    private val _trust = MutableStateFlow<UserTrust?>(null)
    val trust: StateFlow<UserTrust?> = _trust.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _problems = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val problems: SharedFlow<Int> = _problems.asSharedFlow()

    private var roomId: String? = null

    fun load(roomId: String) {
        if (this.roomId != roomId) {
            _info.value = null
            _trust.value = null
        }
        this.roomId = roomId
        scope.launch { refresh(roomId) }
    }

    fun rename(name: String) = change { client.setRoomName(it, name) }

    fun setTopic(topic: String) = change { client.setRoomTopic(it, topic) }

    fun setAvatar(read: () -> ByteArray?) = change { room ->
        val bytes = withContext(Dispatchers.IO) { read() }
            ?: throw PockyException.Other("unreadable image")
        try {
            client.setRoomAvatar(room, bytes)
        } catch (_: PockyException.Other) {
            val converted = withContext(Dispatchers.Default) { toJpeg(bytes) }
                ?: throw PockyException.Other("unreadable image")
            client.setRoomAvatar(room, converted)
        }
    }

    fun invite(userIds: List<String>) = change { client.invitePeople(it, userIds) }

    fun remove(userId: String) = change { client.removeMember(it, userId) }

    private fun change(action: suspend (String) -> Unit) {
        val room = roomId ?: return
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            try {
                action(room)
                refresh(room)
            } catch (_: PockyException.InvalidName) {
                _problems.tryEmit(R.string.room_info_invalid_name)
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            } finally {
                _busy.value = false
            }
        }
    }

    fun awaitVerified(room: String) {
        if (roomId != room) return
        scope.launch {
            repeat(TRUST_CHECKS) {
                val person = _info.value?.directUserId ?: return@launch
                val trust = try {
                    client.userTrust(person)
                } catch (_: PockyException) {
                    null
                }
                if (roomId != room) return@launch
                if (trust != null) _trust.value = trust
                if (trust == UserTrust.VERIFIED) return@launch
                delay(TRUST_CHECK_MS)
            }
        }
    }

    private suspend fun refresh(room: String) {
        try {
            val loaded = client.roomInfo(room)
            if (roomId == room) _info.value = loaded
        } catch (_: PockyException) {
            _problems.tryEmit(R.string.room_info_load_failed)
            return
        }
        val info = _info.value ?: return
        val person = info.directUserId
        if (info.isDirect && info.isEncrypted && person != null) {
            val trust = try {
                client.userTrust(person)
            } catch (_: PockyException) {
                null
            }
            if (roomId == room) _trust.value = trust
        }
    }

    private companion object {
        const val TRUST_CHECKS = 10
        const val TRUST_CHECK_MS = 1_500L
    }
}
