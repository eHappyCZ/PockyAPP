package com.pokecio.app.media

import android.media.MediaPlayer
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

val LocalVoicePlayer = staticCompositionLocalOf<VoicePlayer?> { null }

data class PlaybackState(
    val source: String? = null,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

class VoicePlayer(private val media: MediaLoader, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    fun toggle(source: String) {
        val current = _state.value
        val active = player
        when {
            current.source == source && active != null && current.playing -> {
                active.pause()
                ticker?.cancel()
                _state.value = current.copy(playing = false)
            }
            current.source == source && active != null -> {
                active.start()
                _state.value = current.copy(playing = true)
                tick(active)
            }
            else -> open(source, startAt = 0f)
        }
    }

    fun seek(source: String, fraction: Float) {
        val active = player
        if (_state.value.source != source || active == null) {
            open(source, startAt = fraction)
            return
        }
        val position = (active.duration * fraction.coerceIn(0f, 1f)).toInt()
        active.seekTo(position)
        _state.value = _state.value.copy(positionMs = position.toLong())
    }

    fun stop() {
        ticker?.cancel()
        player?.release()
        player = null
        _state.value = PlaybackState()
    }

    private fun open(source: String, startAt: Float) {
        stop()
        _state.value = PlaybackState(source = source, loading = true)
        scope.launch {
            val file = media.file(source)
            if (_state.value.source != source) return@launch
            if (file == null) {
                _state.value = PlaybackState()
                return@launch
            }
            val created = MediaPlayer()
            try {
                created.setDataSource(file.absolutePath)
                created.prepare()
            } catch (_: Exception) {
                created.release()
                _state.value = PlaybackState()
                return@launch
            }
            created.setOnCompletionListener {
                ticker?.cancel()
                _state.value = _state.value.copy(playing = false, positionMs = 0)
                it.seekTo(0)
            }
            if (startAt > 0f) created.seekTo((created.duration * startAt).toInt())
            created.start()
            player = created
            _state.value = PlaybackState(
                source = source,
                playing = true,
                positionMs = created.currentPosition.toLong(),
                durationMs = created.duration.toLong(),
            )
            tick(created)
        }
    }

    private fun tick(active: MediaPlayer) {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                _state.value = _state.value.copy(positionMs = active.currentPosition.toLong())
                delay(TICK_MS)
            }
        }
    }

    private companion object {
        const val TICK_MS = 50L
    }
}
