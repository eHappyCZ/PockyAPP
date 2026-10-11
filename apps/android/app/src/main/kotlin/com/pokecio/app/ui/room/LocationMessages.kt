package com.pokecio.app.ui.room

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.GeoLocation
import com.pokecio.app.ui.components.GradientButton
import com.pokecio.app.ui.map.MapButton
import com.pokecio.app.ui.map.PockyMap
import com.pokecio.app.ui.map.metersPerDp
import com.pokecio.app.ui.map.rememberMapPreview
import com.pokecio.app.ui.map.toLatLng
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import java.util.Locale
import kotlin.math.ln
import kotlin.math.roundToInt
import org.maplibre.android.maps.MapLibreMap

private const val PREVIEW_WIDTH = 240
private const val PREVIEW_HEIGHT = 150
private const val EXACT_ZOOM = 15.0
private const val EXACT_LIMIT_M = 150.0

data class LocationTarget(val location: GeoLocation, val description: String?, val live: Boolean)

private fun GeoLocation.isArea() = (accuracyM ?: 0.0) > EXACT_LIMIT_M

private fun GeoLocation.previewZoom(widthDp: Int): Double {
    val radius = accuracyM?.takeIf { isArea() } ?: return EXACT_ZOOM
    val wanted = radius * 2.6 / widthDp
    val zoom = EXACT_ZOOM - ln(wanted / metersPerDp(latitude, EXACT_ZOOM)) / ln(2.0)
    return zoom.coerceIn(6.0, EXACT_ZOOM)
}

private fun formatRadius(meters: Double): String =
    if (meters >= 1000) "~${(meters / 1000).roundToInt()} km" else "~${(meters / 50).roundToInt() * 50} m"

@Composable
fun LocationMessage(target: LocationTarget, onOpen: () -> Unit) {
    val location = target.location
    val zoom = remember(location) { location.previewZoom(PREVIEW_WIDTH) }
    val preview = rememberMapPreview(location.toLatLng(), zoom, PREVIEW_WIDTH.dp, PREVIEW_HEIGHT.dp)

    Column(Modifier.width(PREVIEW_WIDTH.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(PREVIEW_WIDTH.dp, PREVIEW_HEIGHT.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PockyColors.Bg)
                .clickable(role = Role.Button, onClick = onOpen),
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                markLocation(location, center, metersPerDp(location.latitude, zoom))
            }
            if (!location.isArea()) {
                Icon(
                    PockyIcons.Location,
                    contentDescription = null,
                    tint = PockyColors.Accent,
                    modifier = Modifier
                        .offset(y = (-14).dp)
                        .size(32.dp),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            if (target.live) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(PockyColors.Success, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = locationTitle(target),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = PockyColors.Ink,
            )
        }
        target.description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = PockyColors.Ink,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun locationTitle(target: LocationTarget): String {
    val radius = target.location.accuracyM?.takeIf { target.location.isArea() }
    return when {
        target.live -> stringResource(R.string.location_live)
        radius != null -> stringResource(R.string.location_approximate, formatRadius(radius))
        else -> stringResource(R.string.location_message)
    }
}

private fun DrawScope.markLocation(location: GeoLocation, at: Offset, metersPerDp: Double) {
    if (location.isArea()) {
        val radius = ((location.accuracyM ?: 0.0) / metersPerDp).dp.toPx()
        drawCircle(PockyColors.Accent.copy(alpha = 0.18f), radius, at)
        drawCircle(PockyColors.Accent.copy(alpha = 0.85f), radius, at, style = Stroke(2.dp.toPx()))
        drawCircle(PockyColors.Bg, 6.dp.toPx(), at)
        drawCircle(PockyColors.AccentSoft, 4.dp.toPx(), at)
    } else {
        drawCircle(PockyColors.Accent.copy(alpha = 0.25f), 6.dp.toPx(), at)
    }
}

@Composable
fun LocationViewer(target: LocationTarget, onBack: () -> Unit) {
    val context = LocalContext.current
    val location = target.location
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var cameraTick by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(PockyColors.Bg)) {
        PockyMap(
            modifier = Modifier.fillMaxSize(),
            center = location.toLatLng(),
            zoom = location.previewZoom(360),
        ) { ready, _ ->
            map = ready
            ready.addOnCameraMoveListener { cameraTick++ }
            ready.addOnCameraIdleListener { cameraTick++ }
        }

        Canvas(Modifier.fillMaxSize()) {
            cameraTick
            val current = map ?: return@Canvas
            val point = current.projection.toScreenLocation(location.toLatLng())
            val scale = current.projection.getMetersPerPixelAtLatitude(location.latitude)
            markLocation(location, Offset(point.x, point.y), scale)
        }

        if (!location.isArea()) {
            val current = map
            if (current != null) {
                cameraTick
                val point = current.projection.toScreenLocation(location.toLatLng())
                val density = LocalDensity.current.density
                Icon(
                    PockyIcons.Location,
                    contentDescription = null,
                    tint = PockyColors.Accent,
                    modifier = Modifier
                        .offset(x = (point.x / density).dp - 20.dp, y = (point.y / density).dp - 38.dp)
                        .size(40.dp),
                )
            }
        }

        MapButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(12.dp)) {
            Icon(PockyIcons.ArrowBack, stringResource(R.string.back), tint = PockyColors.Ink)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(PockyColors.Surface)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        ) {
            Text(
                locationTitle(target),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PockyColors.Ink,
            )
            target.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = PockyColors.InkMuted)
            }
            Spacer(Modifier.height(12.dp))
            GradientButton(
                text = stringResource(R.string.location_navigate),
                onClick = { navigate(context, location) },
            )
        }
    }
}

private fun navigate(context: Context, location: GeoLocation) {
    val point = "%.6f,%.6f".format(Locale.ROOT, location.latitude, location.longitude)
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=$point"))
    try {
        context.startActivity(Intent.createChooser(intent, null))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.location_no_app, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun LiveBanner(until: Long, onStop: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(PockyColors.Success.copy(alpha = 0.12f))
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(PockyColors.Success, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.live_banner, timeLabel(until)),
            style = MaterialTheme.typography.bodyMedium,
            color = PockyColors.Ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.live_stop),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = PockyColors.Success,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onStop)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun UnrestrictedDialog(onAllow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PockyColors.Surface,
        title = { Text(stringResource(R.string.live_unrestricted_title)) },
        text = { Text(stringResource(R.string.live_unrestricted_body), color = PockyColors.InkMuted) },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(stringResource(R.string.live_unrestricted_allow), color = PockyColors.AccentSoft)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.live_unrestricted_later), color = PockyColors.InkMuted)
            }
        },
    )
}
