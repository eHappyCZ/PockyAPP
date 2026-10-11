package com.pokecio.app.ui.room

import android.media.MediaPlayer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokecio.app.R
import com.pokecio.app.media.LocalVoicePlayer
import com.pokecio.app.media.Recording
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.delay

@Composable
fun VoiceMessage(source: String, durationMs: Long?, waveform: List<Int>, own: Boolean) {
    val player = LocalVoicePlayer.current
    val state = player?.state?.collectAsStateWithLifecycle()?.value
    val active = state?.source == source
    val playing = active && state.playing
    val loading = active && state.loading
    val total = when {
        active && state.durationMs > 0 -> state.durationMs
        else -> durationMs ?: 0
    }
    val position = if (active) state.positionMs else 0
    val progress = if (total > 0) (position.toFloat() / total).coerceIn(0f, 1f) else 0f
    val bars = remember(waveform, source) { if (waveform.isNotEmpty()) waveform.map { it / 1024f } else placeholderBars(source) }

    val played = if (own) PockyColors.Ink else PockyColors.AccentSoft
    val rest = if (own) PockyColors.Ink.copy(alpha = 0.38f) else PockyColors.InkMuted.copy(alpha = 0.45f)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (own) {
                        Modifier.background(PockyColors.Ink.copy(alpha = 0.18f))
                    } else {
                        Modifier.background(PockyGradient)
                    },
                )
                .clickable(enabled = player != null, role = Role.Button) { player?.toggle(source) },
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = PockyColors.Ink)
            } else {
                Icon(
                    imageVector = if (playing) PockyIcons.Pause else PockyIcons.Play,
                    contentDescription = stringResource(if (playing) R.string.voice_pause else R.string.voice_play),
                    tint = PockyColors.Ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        WaveformBars(
            bars = bars,
            progress = progress,
            played = played,
            rest = rest,
            modifier = Modifier
                .width(150.dp)
                .height(32.dp)
                .pointerInput(source) {
                    detectTapGestures { offset -> player?.seek(source, offset.x / size.width) }
                }
                .pointerInput(source) {
                    detectHorizontalDragGestures { change, _ ->
                        player?.seek(source, change.position.x / size.width)
                    }
                },
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = clock(if (active && position > 0) position else total),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = if (own) PockyColors.Ink.copy(alpha = 0.8f) else PockyColors.InkMuted,
        )
    }
}

@Composable
fun RecordingBar(levels: List<Float>, elapsedMs: Long, onCancel: () -> Unit, onStop: () -> Unit) {
    val blink by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "rec-dot",
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(PockyColors.Surface)
                .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.12f), RoundedCornerShape(25.dp))
                .padding(start = 6.dp, end = 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onCancel),
            ) {
                Icon(
                    PockyIcons.Trash,
                    contentDescription = stringResource(R.string.voice_cancel),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                Modifier
                    .size(9.dp)
                    .alpha(blink)
                    .background(Color(0xFFFF4D6A), CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = clock(elapsedMs),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = PockyColors.Ink,
            )
            Spacer(Modifier.width(12.dp))
            WaveformBars(
                bars = levels,
                progress = 1f,
                played = PockyColors.AccentSoft,
                rest = PockyColors.AccentSoft,
                alignEnd = true,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(PockyGradient)
                .clickable(role = Role.Button, onClick = onStop),
        ) {
            Icon(PockyIcons.Stop, contentDescription = stringResource(R.string.voice_stop), tint = PockyColors.Ink, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun RecordingReview(recording: Recording, onDiscard: () -> Unit, onSend: () -> Unit) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    val total = player?.duration?.toLong()?.takeIf { it > 0 } ?: recording.durationMs

    DisposableEffect(recording) {
        val extension = if (recording.mime == "audio/ogg") "ogg" else "m4a"
        val file = File(context.cacheDir, "voice-review.$extension").apply { writeBytes(recording.bytes) }
        val created = MediaPlayer().apply {
            runCatching {
                setDataSource(file.absolutePath)
                prepare()
            }
            setOnCompletionListener {
                playing = false
                position = 0
                it.seekTo(0)
            }
        }
        player = created
        onDispose {
            created.release()
            player = null
            file.delete()
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            position = player?.currentPosition?.toLong() ?: 0
            delay(50)
        }
    }

    val toggle = {
        player?.let { active ->
            if (active.isPlaying) active.pause() else active.start()
            playing = active.isPlaying
        }
    }
    val seek = { fraction: Float ->
        player?.let { active ->
            val target = (active.duration * fraction.coerceIn(0f, 1f)).toInt()
            active.seekTo(target)
            position = target.toLong()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(PockyColors.Surface)
                .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.12f), RoundedCornerShape(25.dp))
                .padding(start = 6.dp, end = 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDiscard),
            ) {
                Icon(
                    PockyIcons.Trash,
                    contentDescription = stringResource(R.string.voice_cancel),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PockyGradient)
                    .clickable(role = Role.Button) { toggle() },
            ) {
                Icon(
                    imageVector = if (playing) PockyIcons.Pause else PockyIcons.Play,
                    contentDescription = stringResource(if (playing) R.string.voice_pause else R.string.voice_play),
                    tint = PockyColors.Ink,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            WaveformBars(
                bars = recording.levels,
                progress = if (total > 0) (position.toFloat() / total).coerceIn(0f, 1f) else 0f,
                played = PockyColors.AccentSoft,
                rest = PockyColors.InkMuted.copy(alpha = 0.45f),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .pointerInput(recording) {
                        detectTapGestures { offset -> seek(offset.x / size.width) }
                    }
                    .pointerInput(recording) {
                        detectHorizontalDragGestures { change, _ -> seek(change.position.x / size.width) }
                    },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = clock(if (position > 0) position else total),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = PockyColors.Ink,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(PockyGradient)
                .clickable(role = Role.Button, onClick = onSend),
        ) {
            Icon(PockyIcons.Send, contentDescription = stringResource(R.string.voice_send), tint = PockyColors.Ink, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun WaveformBars(
    bars: List<Float>,
    progress: Float,
    played: Color,
    rest: Color,
    modifier: Modifier,
    alignEnd: Boolean = false,
) {
    Canvas(modifier) {
        val barWidth = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val slots = max(1, ((size.width + gap) / (barWidth + gap)).toInt())
        val values = if (alignEnd) bars.takeLast(slots) else resample(bars, slots)
        val offset = if (alignEnd) size.width - values.size * (barWidth + gap) + gap else 0f
        val minHeight = 3.dp.toPx()

        values.forEachIndexed { index, value ->
            val x = offset + index * (barWidth + gap)
            val height = max(minHeight, value.coerceIn(0f, 1f) * size.height)
            val color = if ((index + 0.5f) / values.size <= progress) played else rest
            drawRoundRect(
                color = color,
                topLeft = Offset(x, (size.height - height) / 2),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

private fun resample(values: List<Float>, count: Int): List<Float> {
    if (values.isEmpty()) return List(count) { 0f }
    return List(count) { i ->
        val start = i * values.size / count
        val end = max(start + 1, (i + 1) * values.size / count)
        values.subList(start, end.coerceAtMost(values.size)).maxOrNull() ?: 0f
    }
}

private fun placeholderBars(seed: String): List<Float> {
    var state = seed.hashCode()
    return List(48) {
        state = state * 1103515245 + 12345
        0.25f + ((state ushr 16) and 0x7fff) / 32767f * 0.6f
    }
}

fun clock(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
