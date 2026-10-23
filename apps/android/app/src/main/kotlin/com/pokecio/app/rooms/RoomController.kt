package com.pokecio.app.rooms

import android.os.SystemClock
import com.pokecio.app.R
import com.pokecio.app.core.GeoLocation
import com.pokecio.app.core.IdentityChange
import com.pokecio.app.core.IdentityChangeListener
import com.pokecio.app.core.LocationArea
import com.pokecio.app.core.Member
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import com.pokecio.app.core.RoomTimeline
import com.pokecio.app.core.TaskHandle
import com.pokecio.app.core.TimelineChange
import com.pokecio.app.core.TimelineEvent
import com.pokecio.app.core.TimelineItem
import com.pokecio.app.core.TimelineItemKind
import com.pokecio.app.core.TimelineListener
import com.pokecio.app.core.VideoUpload
import com.pokecio.app.media.Recording
import com.pokecio.app.media.toJpeg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Composing {
    data object Message : Composing
    data class Reply(val event: TimelineEvent) : Composing
    data class Edit(val event: TimelineEvent) : Composing
}

class RoomController(
    private val client: PockyClient,
    val roomId: String,
    private val scope: CoroutineScope,
) {
    private val _composing = MutableStateFlow<Composing>(Composing.Message)
    val composing: StateFlow<Composing> = _composing.asStateFlow()

    private val _sendingImage = MutableStateFlow(false)
    val sendingImage: StateFlow<Boolean> = _sendingImage.asStateFlow()

    private val _problems = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val problems: SharedFlow<Int> = _problems.asSharedFlow()

    private val _notices = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val notices: SharedFlow<Int> = _notices.asSharedFlow()

    private val _items = MutableStateFlow<List<TimelineItem>?>(null)
    val items: StateFlow<List<TimelineItem>?> = _items.asStateFlow()

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()

    private val _reachedStart = MutableStateFlow(false)
    val reachedStart: StateFlow<Boolean> = _reachedStart.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    val history = RoomHistoryController(client, roomId, scope)
    val media = RoomMediaController(client, roomId, scope)
    val moderation = RoomModerationController(client, roomId, scope)

    private val _members = MutableStateFlow<Map<String, Member>>(emptyMap())
    val members: StateFlow<Map<String, Member>> = _members.asStateFlow()

    private val _identityChanges = MutableStateFlow<List<IdentityChange>>(emptyList())
    val identityChanges: StateFlow<List<IdentityChange>> = _identityChanges.asStateFlow()

    private val buffer = mutableListOf<TimelineItem>()
    private val readRequests = Channel<Unit>(Channel.CONFLATED)
    private val memberRequests = Channel<Unit>(Channel.CONFLATED)
    private var timeline: RoomTimeline? = null
    private var handle: TaskHandle? = null
    private var identityHandle: TaskHandle? = null
    private val jobs = mutableListOf<Job>()
    private var typingActive = false
    private var typingSentAt = 0L

    init {
        jobs += scope.launch { open() }
        jobs += scope.launch {
            memberRequests.consumeEach {
                try {
                    _members.value = client.roomInfo(roomId).members.associateBy { it.userId }
                } catch (_: PockyException) {
                }
                delay(MEMBERS_REFRESH_MS)
            }
        }
        memberRequests.trySend(Unit)
        identityHandle = try {
            client.observeIdentityChanges(roomId, object : IdentityChangeListener {
                override fun onUpdate(changes: List<IdentityChange>) {
                    _identityChanges.value = changes
                }
            })
        } catch (_: PockyException) {
            null
        }
        jobs += scope.launch {
            readRequests.consumeEach {
                try {
                    timeline?.markAsRead()
                } catch (_: PockyException) {
                }
            }
        }
    }

    fun loadOlder() {
        val current = timeline ?: return
        if (_loadingOlder.value || _reachedStart.value) return
        _loadingOlder.value = true

        scope.launch {
            try {
                _reachedStart.value = current.paginateBackwards()
            } catch (_: PockyException) {
            } finally {
                _loadingOlder.value = false
            }
        }
    }

    fun onDraftChanged(text: String) {
        val typing = text.isNotBlank()
        val now = SystemClock.elapsedRealtime()
        if (typing == typingActive && (!typing || now - typingSentAt < TYPING_REFRESH_MS)) return

        typingActive = typing
        typingSentAt = now
        scope.launch {
            try {
                client.sendTyping(roomId, typing)
            } catch (_: PockyException) {
            }
        }
    }

    fun send(text: String) {
        val current = timeline ?: return
        if (text.isBlank()) return
        onDraftChanged("")
        val mode = _composing.value
        _composing.value = Composing.Message

        scope.launch {
            try {
                when (mode) {
                    Composing.Message -> current.sendText(text)
                    is Composing.Reply -> {
                        val target = mode.event.eventId
                        if (target != null) current.sendReply(text, target) else current.sendText(text)
                    }
                    is Composing.Edit -> current.edit(mode.event.itemKey, text)
                }
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            }
        }
    }

    fun startReply(event: TimelineEvent) {
        if (event.canReply) _composing.value = Composing.Reply(event)
    }

    fun startEdit(event: TimelineEvent) {
        if (event.canEdit) _composing.value = Composing.Edit(event)
    }

    fun cancelComposing() {
        _composing.value = Composing.Message
    }

    fun toggleReaction(event: TimelineEvent, key: String) {
        val current = timeline ?: return
        scope.launch {
            try {
                current.toggleReaction(event.itemKey, key)
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            }
        }
    }

    fun forward(eventIds: List<String>, toRoom: String) {
        if (eventIds.isEmpty()) return
        scope.launch {
            var failed = false
            for (eventId in eventIds) {
                try {
                    client.forwardMessage(roomId, eventId, toRoom)
                } catch (_: PockyException) {
                    failed = true
                }
            }
            _notices.tryEmit(if (failed) R.string.forward_failed else R.string.forward_done)
        }
    }

    fun redact(event: TimelineEvent) {
        val current = timeline ?: return
        if (!event.canRedact) return
        scope.launch {
            try {
                current.redact(event.itemKey)
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            }
        }
    }

    fun sendFile(read: () -> ByteArray?, name: String, mime: String) = upload {
        val bytes = withContext(Dispatchers.IO) { read() }
        if (bytes == null) {
            _problems.tryEmit(R.string.file_unreadable)
            return@upload
        }
        it.sendFile(bytes, name, mime)
    }

    fun sendVideos(prepares: List<() -> VideoUpload?>, caption: String) = upload { current ->
        prepares.forEachIndexed { index, prepare ->
            val clip = withContext(Dispatchers.IO) { prepare() }
            if (clip == null) {
                _problems.tryEmit(R.string.file_unreadable)
                return@forEachIndexed
            }
            current.sendVideo(clip, caption.takeIf { index == 0 && it.isNotBlank() })
        }
    }

    private fun upload(action: suspend (RoomTimeline) -> Unit) {
        val current = timeline ?: return
        if (_sendingImage.value) return
        _sendingImage.value = true
        scope.launch {
            try {
                action(current)
            } catch (_: PockyException.FileTooLarge) {
                _problems.tryEmit(R.string.file_too_large)
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            } finally {
                _sendingImage.value = false
            }
        }
    }

    fun sendLocation(location: GeoLocation, area: LocationArea) {
        val current = timeline ?: return
        scope.launch {
            try {
                current.sendLocation(location, area)
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            }
        }
    }

    fun sendVoice(recording: Recording) {
        val current = timeline ?: return
        scope.launch {
            try {
                current.sendVoice(recording.bytes, recording.mime, recording.durationMs.toULong(), recording.levels)
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            }
        }
    }

    fun sendImages(reads: List<() -> ByteArray?>, caption: String, fullQuality: Boolean) {
        val current = timeline ?: return
        if (_sendingImage.value || reads.isEmpty()) return
        _sendingImage.value = true

        scope.launch {
            try {
                reads.forEachIndexed { index, read ->
                    val text = caption.takeIf { index == 0 && it.isNotBlank() }
                    sendOne(current, read, text, fullQuality)
                }
            } finally {
                _sendingImage.value = false
            }
        }
    }

    private suspend fun sendOne(current: RoomTimeline, read: () -> ByteArray?, caption: String?, fullQuality: Boolean) {
        try {
            val bytes = withContext(Dispatchers.IO) { read() }
            if (bytes == null) {
                _problems.tryEmit(R.string.room_image_unreadable)
                return
            }
            try {
                current.sendImage(bytes, caption, fullQuality)
            } catch (_: PockyException.Other) {
                val converted = withContext(Dispatchers.Default) { toJpeg(bytes) }
                if (converted == null) {
                    _problems.tryEmit(R.string.room_image_unreadable)
                    return
                }
                current.sendImage(converted, caption, fullQuality)
            }
        } catch (_: PockyException) {
            _problems.tryEmit(R.string.room_action_failed)
        }
    }

    fun acknowledgeIdentity(userId: String) {
        scope.launch {
            try {
                client.acknowledgeIdentity(userId)
                _identityChanges.value = _identityChanges.value.filterNot { it.userId == userId }
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            }
        }
    }

    fun close() {
        history.close()
        media.close()
        identityHandle?.cancel()
        identityHandle?.close()
        identityHandle = null
        onDraftChanged("")
        jobs.forEach { it.cancel() }
        readRequests.close()
        memberRequests.close()
        handle?.cancel()
        handle?.close()
        handle = null
        timeline?.close()
        timeline = null
    }

    private suspend fun open() {
        try {
            val opened = client.timeline(roomId)
            timeline = opened
            handle = opened.observe(object : TimelineListener {
                override fun onChanges(changes: List<TimelineChange>) {
                    apply(changes)
                }
            })
            loadOlder()
        } catch (_: PockyException) {
            _failed.value = true
        }
    }

    private fun apply(changes: List<TimelineChange>) {
        val unknown = synchronized(buffer) {
            buffer.applyChanges(changes)
            _items.value = buffer.toList()
            hasUnknownReader(buffer)
        }
        readRequests.trySend(Unit)
        if (unknown) memberRequests.trySend(Unit)
    }

    private fun hasUnknownReader(items: List<TimelineItem>): Boolean {
        val known = _members.value
        if (known.isEmpty()) return false
        return items.any { item ->
            val kind = item.kind
            kind is TimelineItemKind.Event && kind.event.readBy.any { it.userId !in known }
        }
    }

    private companion object {
        const val TYPING_REFRESH_MS = 4_000L
        const val MEMBERS_REFRESH_MS = 30_000L
    }
}
