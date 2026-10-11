package com.pokecio.app.media

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pokecio.app.ui.theme.PockyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun isAnimatedImage(mime: String?, name: String?): Boolean =
    mime == "image/gif" || mime == "image/webp" ||
        name?.lowercase()?.let { it.endsWith(".gif") || it.endsWith(".webp") } == true

@Composable
fun AnimatedMatrixImage(
    source: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fit: Boolean = false,
) {
    val media = LocalMediaLoader.current
    var drawable by remember(source) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(source, media) {
        val file = media?.file(source) ?: return@LaunchedEffect
        drawable = withContext(Dispatchers.IO) {
            runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) }.getOrNull()
        }
    }
    DisposableEffect(drawable) {
        val animated = drawable as? AnimatedImageDrawable
        animated?.start()
        onDispose { animated?.stop() }
    }

    val loaded = drawable
    if (loaded == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = PockyColors.InkMuted)
        }
        return
    }
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = if (fit) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
                this.contentDescription = contentDescription
            }
        },
        update = { it.setImageDrawable(loaded) },
        modifier = modifier,
    )
}
