package com.pokecio.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter

object MapPreviews {

    private const val TIMEOUT_MS = 30_000L

    private val cache = LruCache<String, Bitmap>(24)
    private val pending = HashMap<String, Deferred<Bitmap?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun cached(key: String): Bitmap? = cache.get(key)

    suspend fun render(
        context: Context,
        target: LatLng,
        zoom: Double,
        width: Int,
        height: Int,
        pixelRatio: Float,
    ): Bitmap? {
        val key = key(target, zoom, width, height)
        cache.get(key)?.let { return it }
        val job = withContext(Dispatchers.Main.immediate) {
            pending.getOrPut(key) {
                scope.async {
                    val bitmap = withTimeoutOrNull(TIMEOUT_MS) {
                        snapshot(context.applicationContext, target, zoom, width, height, pixelRatio)
                    }
                    if (bitmap != null) cache.put(key, bitmap)
                    pending.remove(key)
                    bitmap
                }
            }
        }
        return job.await()
    }

    fun key(target: LatLng, zoom: Double, width: Int, height: Int): String =
        "%.5f,%.5f,%.1f,%dx%d".format(target.latitude, target.longitude, zoom, width, height)

    private suspend fun snapshot(
        context: Context,
        target: LatLng,
        zoom: Double,
        width: Int,
        height: Int,
        pixelRatio: Float,
    ): Bitmap? = suspendCancellableCoroutine { cont ->
        MapLibre.getInstance(context)
        val options = MapSnapshotter.Options(width, height)
            .withStyleBuilder(Style.Builder().fromUri(MAP_STYLE_URI))
            .withCameraPosition(CameraPosition.Builder().target(target).zoom(zoom).build())
            .withPixelRatio(pixelRatio)
            .withLogo(false)
        val snapshotter = MapSnapshotter(context, options)
        cont.invokeOnCancellation { snapshotter.cancel() }
        snapshotter.start(
            { snapshot -> if (cont.isActive) cont.resume(snapshot.bitmap) },
            { _ -> if (cont.isActive) cont.resume(null) },
        )
    }
}

@Composable
fun rememberMapPreview(target: LatLng, zoom: Double, width: Dp, height: Dp): ImageBitmap? {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val w = width.value.toInt()
    val h = height.value.toInt()
    val key = MapPreviews.key(target, zoom, w, h)
    var image by remember(key) { mutableStateOf(MapPreviews.cached(key)?.asImageBitmap()) }
    LaunchedEffect(key) {
        if (image == null) {
            image = MapPreviews.render(context, target, zoom, w, h, density)?.asImageBitmap()
        }
    }
    return image
}
