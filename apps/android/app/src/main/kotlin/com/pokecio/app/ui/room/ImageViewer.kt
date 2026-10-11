package com.pokecio.app.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pokecio.app.R
import com.pokecio.app.media.AnimatedMatrixImage
import com.pokecio.app.media.MatrixImage
import com.pokecio.app.media.isAnimatedImage
import com.pokecio.app.media.rememberMatrixImage
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

data class ViewerImage(
    val source: String?,
    val original: String?,
    val width: UInt?,
    val height: UInt?,
    val caption: String?,
    val details: MediaDetails? = null,
)

@Composable
fun ImageViewer(image: ViewerImage, actions: MediaActions?, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transform = rememberTransformableState { _, zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset = if (scale == 1f) Offset.Zero else offset + pan
        }
        val still = image.details?.let { isAnimatedImage(it.mime, it.name) } != true
        val preview = rememberMatrixImage(image.source.takeIf { still }, PREVIEW_SIZE, PREVIEW_SIZE)
        val full = rememberMatrixImage((image.original ?: image.source).takeIf { still }, FULL_SIZE, FULL_SIZE)
        val bitmap = full ?: preview

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val animated = image.details?.takeIf { isAnimatedImage(it.mime, it.name) }?.source
            if (animated != null) {
                AnimatedMatrixImage(
                    source = animated,
                    contentDescription = image.caption,
                    fit = true,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (bitmap != null) {
                MatrixImage(
                    image = bitmap,
                    contentDescription = image.caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                },
                            )
                        }
                        .transformable(transform)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            } else {
                CircularProgressIndicator(color = PockyColors.AccentSoft)
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

            if (image.details != null && actions != null) {
                MediaActionBar(
                    details = image.details,
                    actions = actions,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(12.dp),
                )
            }

            if (!image.caption.isNullOrBlank()) {
                Text(
                    text = image.caption,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
}

private val FULL_SIZE = 1600.dp
private val PREVIEW_SIZE = 240.dp
