package com.pokecio.app.presence

import android.content.Context
import com.pokecio.app.R
import com.pokecio.app.core.DevicePreferences
import com.pokecio.app.core.OwnStatusListener
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import com.pokecio.app.core.PresenceStatus
import com.pokecio.app.core.StatusMessage
import com.pokecio.app.core.TaskHandle
import com.pokecio.app.core.Typist
import com.pokecio.app.core.TypingListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PresenceController(
    private val context: Context,
    private val client: PockyClient,
    private val scope: CoroutineScope,
    preferences: StateFlow<DevicePreferences?>,
) {
    private val _typing = MutableStateFlow<Map<String, List<Typist>>>(emptyMap())
    val typing: StateFlow<Map<String, List<Typist>>> = _typing.asStateFlow()

    private val _presence = MutableStateFlow<Map<String, PresenceStatus>>(emptyMap())
    val presence: StateFlow<Map<String, PresenceStatus>> = _presence.asStateFlow()

    private val _statuses = MutableStateFlow<Map<String, String>>(emptyMap())
    val statuses: StateFlow<Map<String, String>> = _statuses.asStateFlow()

    private val _ownStatus = MutableStateFlow<StatusMessage?>(null)
    val ownStatus: StateFlow<StatusMessage?> = _ownStatus.asStateFlow()

    private val _problems = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val problems: SharedFlow<Int> = _problems.asSharedFlow()

    private val listUsers = MutableStateFlow<Set<String>>(emptySet())
    private val roomUsers = MutableStateFlow<Set<String>>(emptySet())
    private val foreground = MutableStateFlow(false)

    private var typingHandle: TaskHandle? = null
    private var statusHandle: TaskHandle? = null
    private val jobs = mutableListOf<Job>()

    init {
        jobs += scope.launch {
            preferences.filterNotNull()
                .map { it.shareTyping }
                .distinctUntilChanged()
                .collect(::restartTyping)
        }
        jobs += scope.launch {
            val sharing = preferences.filterNotNull().map { it.sharePresence }.distinctUntilChanged()
            val tracked = combine(listUsers, roomUsers) { list, room -> (room + list).take(MAX_TRACKED).toSet() }

            combine(sharing, foreground, tracked) { share, active, users -> Triple(share, active, users) }
                .collectLatest { (share, active, users) ->
                    if (!share) _presence.value = emptyMap()
                    if (!active || users.isEmpty()) return@collectLatest
                    while (isActive) {
                        refresh(users, share)
                        delay(REFRESH_MS)
                    }
                }
        }
        statusHandle = try {
            client.observeOwnStatus(object : OwnStatusListener {
                override fun onUpdate(status: StatusMessage?) {
                    _ownStatus.value = status
                    StatusExpiryWorker.schedule(context, status?.untilMs?.toLong())
                }
            })
        } catch (_: PockyException) {
            null
        }
    }

    fun setStatus(text: String, untilMs: Long?) {
        scope.launch {
            try {
                _ownStatus.value = client.setStatus(text, untilMs?.toULong())
                StatusExpiryWorker.schedule(context, untilMs)
            } catch (_: PockyException.InvalidName) {
                _problems.tryEmit(R.string.status_invalid)
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            }
        }
    }

    fun clearStatus() {
        scope.launch {
            try {
                client.clearStatus()
                _ownStatus.value = null
                StatusExpiryWorker.schedule(context, null)
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            }
        }
    }

    fun trackRoomList(userIds: Collection<String>) {
        listUsers.value = userIds.toSet()
    }

    fun trackRoom(userIds: Collection<String>) {
        roomUsers.value = userIds.toSet()
    }

    fun start() {
        foreground.value = true
    }

    fun stop() {
        foreground.value = false
    }

    fun close() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        typingHandle?.let { it.cancel(); it.close() }
        typingHandle = null
        statusHandle?.let { it.cancel(); it.close() }
        statusHandle = null
    }

    private fun restartTyping(enabled: Boolean) {
        typingHandle?.let { it.cancel(); it.close() }
        typingHandle = null
        _typing.value = emptyMap()
        if (!enabled) return

        try {
            typingHandle = client.observeTyping(object : TypingListener {
                override fun onUpdate(roomId: String, typists: List<Typist>) {
                    _typing.update { current ->
                        if (typists.isEmpty()) current - roomId else current + (roomId to typists)
                    }
                }
            })
        } catch (_: PockyException) {
        }
    }

    private suspend fun refresh(users: Set<String>, share: Boolean) {
        val presence = _presence.value.toMutableMap()
        val statuses = _statuses.value.toMutableMap()
        for (userId in users) {
            try {
                val info = client.presence(userId)
                if (share) presence[userId] = info.status
                val text = info.statusText
                if (text != null) statuses[userId] = text else statuses.remove(userId)
            } catch (_: PockyException) {
            }
        }
        if (share) _presence.value = presence
        _statuses.value = statuses
    }

    private companion object {
        const val REFRESH_MS = 60_000L
        const val MAX_TRACKED = 40
    }
}
