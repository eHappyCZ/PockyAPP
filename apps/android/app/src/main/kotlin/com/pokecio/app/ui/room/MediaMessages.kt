package com.pokecio.app.ui.room

import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pokecio.app.R
import com.pokecio.app.media.FileOpener
import com.pokecio.app.media.LocalMediaLoader
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FileMessage(name: String, source: String, size: Long?, mime: String?, own: Boolean) {
    val context = LocalContext.current
    val media = LocalMediaLoader.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    val tint = if (own) PockyColors.Ink else PockyColors.AccentSoft

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !loading && media != null, role = Role.Button) {
                loading = true
                scope.launch {
                    val file = media?.let { FileOpener.prepare(context, it, source, name) }
                    loading = false
                    val opened = file != null && FileOpener.open(context, file, mime)
                    if (!opened) {
                        Toast.makeText(context, R.string.file_cannot_open, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .background(tint.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = tint)
            } else {
                Icon(PockyIcons.File, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Text(
                    text = extension(name, mime),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = PockyColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (size != null) {
                Text(
                    text = readableSize(size),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (own) PockyColors.Ink.copy(alpha = 0.7f) else PockyColors.InkMuted,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(PockyIcons.Download, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun VideoOverlay(durationMs: Long?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
        ) {
            Icon(PockyIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        if (durationMs != null && durationMs > 0) {
            Text(
                text = clock(durationMs),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun VideoPlayerDialog(
    source: String,
    caption: String?,
    details: MediaDetails?,
    actions: MediaActions?,
    onClose: () -> Unit,
) {
    val media = LocalMediaLoader.current
    var file by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<VideoView?>(null) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(source) {
        file = media?.file(source)
        failed = file == null
    }
    LaunchedEffect(view) {
        val player = view ?: return@LaunchedEffect
        while (true) {
            position = player.currentPosition.toLong()
            duration = player.duration.toLong().coerceAtLeast(0)
            delay(200)
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val ready = file
            when {
                ready != null -> AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            setVideoPath(ready.absolutePath)
                            setOnPreparedListener { start() }
                            setOnCompletionListener { playing = false }
                            view = this
                        }
                    },
                    onRelease = { it.stopPlayback() },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .clickable {
                            val player = view ?: return@clickable
                            if (player.isPlaying) player.pause() else player.start()
                            playing = player.isPlaying
                        },
                )
                failed -> Text(
                    text = stringResource(R.string.file_cannot_open),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = PockyColors.AccentSoft)
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(role = Role.Button, onClick = onClose),
            ) {
                Icon(PockyIcons.Close, contentDescription = stringResource(R.string.cancel), tint = Color.White)
            }

            if (details != null && actions != null) {
                MediaActionBar(
                    details = details,
                    actions = actions,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(12.dp),
                )
            }

            if (ready != null) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (!caption.isNullOrBlank()) {
                        Text(caption, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button) {
                                    val player = view ?: return@clickable
                                    if (player.isPlaying) player.pause() else player.start()
                                    playing = player.isPlaying
                                },
                        ) {
                            Icon(
                                if (playing) PockyIcons.Pause else PockyIcons.Play,
                                contentDescription = stringResource(if (playing) R.string.voice_pause else R.string.voice_play),
                                tint = Color.White,
                            )
                        }
                        Slider(
                            value = if (dragging >= 0f) dragging else if (duration > 0) position.toFloat() / duration else 0f,
                            onValueChange = { dragging = it },
                            onValueChangeFinished = {
                                view?.seekTo((dragging * duration).toInt())
                                dragging = -1f
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = PockyColors.AccentSoft,
                                activeTrackColor = PockyColors.Accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${clock(position)} / ${clock(duration)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

private fun extension(name: String, mime: String?): String {
    val fromName = name.substringAfterLast('.', "").takeIf { it.length in 1..4 }
    val fromMime = mime?.substringAfterLast('/')?.takeIf { it.length in 1..4 }
    return (fromName ?: fromMime ?: "").uppercase()
}

fun readableSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%d kB".format(bytes / 1024)
    else -> "$bytes B"
}
