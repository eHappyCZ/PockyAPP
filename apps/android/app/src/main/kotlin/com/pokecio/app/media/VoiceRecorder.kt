package com.pokecio.app.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class Recording(val bytes: ByteArray, val mime: String, val durationMs: Long, val levels: List<Float>)

class VoiceRecorder(private val context: Context, private val scope: CoroutineScope) {

    private val _levels = MutableStateFlow<List<Float>>(emptyList())
    val levels: StateFlow<List<Float>> = _levels.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var sampling: Job? = null
    private var startedAt = 0L
    private val samples = ArrayList<Float>()

    fun start(): Boolean {
        if (recorder != null) return true
        val opus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val target = File(context.cacheDir, "voice-${System.currentTimeMillis()}.${if (opus) "ogg" else "m4a"}")

        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else legacyRecorder()
        try {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (opus) {
                created.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                created.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            } else {
                created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            created.setAudioSamplingRate(48_000)
            created.setAudioEncodingBitRate(32_000)
            created.setOutputFile(target.absolutePath)
            created.prepare()
            created.start()
        } catch (_: Exception) {
            created.release()
            target.delete()
            return false
        }

        recorder = created
        file = target
        samples.clear()
        _levels.value = emptyList()
        _elapsedMs.value = 0
        startedAt = SystemClock.elapsedRealtime()
        _recording.value = true
        sampling = scope.launch {
            while (isActive) {
                val amplitude = runCatching { created.maxAmplitude }.getOrDefault(0)
                val level = sqrt(amplitude / MAX_AMPLITUDE).coerceIn(0f, 1f)
                samples += level
                _levels.value = samples.takeLast(VISIBLE_BARS)
                _elapsedMs.value = SystemClock.elapsedRealtime() - startedAt
                delay(SAMPLE_MS)
            }
        }
        return true
    }

    fun stop(): Recording? {
        val active = recorder ?: return null
        val target = file
        val duration = SystemClock.elapsedRealtime() - startedAt
        finish(active)
        if (target == null) return null
        val bytes = runCatching { target.readBytes() }.getOrNull()
        target.delete()
        if (bytes == null || bytes.isEmpty() || duration < MIN_DURATION_MS) return null
        val mime = if (target.extension == "ogg") "audio/ogg" else "audio/mp4"
        return Recording(bytes, mime, duration, samples.toList())
    }

    fun cancel() {
        val active = recorder ?: return
        finish(active)
        file?.delete()
    }

    private fun finish(active: MediaRecorder) {
        sampling?.cancel()
        sampling = null
        runCatching { active.stop() }
        active.release()
        recorder = null
        _recording.value = false
    }

    private companion object {
        const val SAMPLE_MS = 60L
        const val VISIBLE_BARS = 48
        const val MAX_AMPLITUDE = 32767f
        const val MIN_DURATION_MS = 700L
    }
}

@Suppress("DEPRECATION")
private fun legacyRecorder() = MediaRecorder()
