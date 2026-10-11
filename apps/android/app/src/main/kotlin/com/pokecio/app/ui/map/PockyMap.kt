package com.pokecio.app.ui.map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pokecio.app.core.GeoLocation
import com.pokecio.app.ui.theme.PockyColors
import kotlin.math.cos
import kotlin.math.pow
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

const val MAP_STYLE_URI = "asset://map/style.json"
private const val EARTH_METERS = 40_075_016.686
private const val TILE_DP = 512.0
val HomeCenter = LatLng(49.4, 17.3)
const val HomeZoom = 6.0
private const val MIN_ZOOM = 5.5
private val CoveredArea = LatLngBounds.Builder()
    .include(LatLng(47.7, 12.0))
    .include(LatLng(51.1, 22.6))
    .build()

fun GeoLocation.toLatLng() = LatLng(latitude, longitude)

fun metersPerDp(latitude: Double, zoom: Double): Double =
    EARTH_METERS * cos(Math.toRadians(latitude)) / (TILE_DP * 2.0.pow(zoom))

@Composable
fun PockyMap(
    modifier: Modifier = Modifier,
    center: LatLng = HomeCenter,
    zoom: Double = HomeZoom,
    interactive: Boolean = true,
    onReady: (MapLibreMap, Style) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ready by rememberUpdatedState(onReady)
    val mapView = remember { createMapView(context, center, zoom, interactive) }

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart().also { started = true }
                Lifecycle.Event.ON_RESUME -> mapView.onResume().also { resumed = true }
                Lifecycle.Event.ON_PAUSE -> mapView.onPause().also { resumed = false }
                Lifecycle.Event.ON_STOP -> mapView.onStop().also { started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.getMapAsync { map ->
            map.setLatLngBoundsForCameraTarget(CoveredArea)
            map.setMinZoomPreference(MIN_ZOOM)
            map.setStyle(Style.Builder().fromUri(MAP_STYLE_URI)) { style -> ready(map, style) }
        }
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        Text(
            "© OpenStreetMap",
            color = PockyColors.InkMuted,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(PockyColors.Bg.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

private fun createMapView(context: Context, center: LatLng, zoom: Double, interactive: Boolean): MapView {
    MapLibre.getInstance(context)
    val options = MapLibreMapOptions.createFromAttributes(context)
        .logoEnabled(false)
        .attributionEnabled(false)
        .compassEnabled(false)
        .rotateGesturesEnabled(false)
        .tiltGesturesEnabled(false)
        .scrollGesturesEnabled(interactive)
        .zoomGesturesEnabled(interactive)
        .doubleTapGesturesEnabled(interactive)
        .camera(CameraPosition.Builder().target(center).zoom(zoom).build())
    return MapView(context, options).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        onCreate(null)
    }
}

@Composable
fun MapButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(PockyColors.Surface.copy(alpha = 0.92f))
            .border(1.dp, PockyColors.AccentSoft.copy(alpha = 0.15f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        content()
    }
}
