package com.pokecio.app.media

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

@Composable
fun rememberMatrixImage(source: String?, width: Dp, height: Dp): ImageBitmap? {
    val loader = LocalMediaLoader.current
    val density = LocalDensity.current
    val widthPx = with(density) { width.roundToPx() }
    val heightPx = with(density) { height.roundToPx() }

    val image by produceState(
        initialValue = source?.let { loader?.cached(it, widthPx, heightPx) },
        source,
        widthPx,
        heightPx,
        loader,
    ) {
        value = source?.let { loader?.cached(it, widthPx, heightPx) }
        if (source != null && loader != null && value == null) {
            value = loader.load(source, widthPx, heightPx)
        }
    }
    return image
}

@Composable
fun MatrixImage(
    image: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Image(
        bitmap = image,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}
