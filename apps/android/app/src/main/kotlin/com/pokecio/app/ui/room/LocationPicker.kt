package com.pokecio.app.ui.room

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.GeoLocation
import com.pokecio.app.core.LiveLength
import com.pokecio.app.core.LocationArea
import com.pokecio.app.core.blurLocation
import com.pokecio.app.location.DeviceLocation
import com.pokecio.app.ui.components.GradientButton
import com.pokecio.app.ui.map.MapButton
import com.pokecio.app.ui.map.PockyMap
import com.pokecio.app.ui.map.toLatLng
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

private const val CLOSE_ZOOM = 16.0

@Composable
fun LocationPicker(
    initialArea: LocationArea,
    onSend: (GeoLocation, LocationArea) -> Unit,
    onShareLive: (LiveLength, LocationArea) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var area by remember { mutableStateOf(initialArea) }
    var own by remember { mutableStateOf<GeoLocation?>(null) }
    var onOwn by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Int?>(null) }
    var cameraTick by remember { mutableIntStateOf(0) }
    var choosingLive by remember { mutableStateOf(false) }
    var pendingLive by remember { mutableStateOf<LiveLength?>(null) }

    fun locate() {
        if (locating) return
        locating = true
        notice = null
        scope.launch {
            val fix = DeviceLocation.current(context)
            locating = false
            if (fix == null) {
                notice = R.string.location_unavailable
                return@launch
            }
            own = fix
            onOwn = true
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(fix.toLatLng(), CLOSE_ZOOM))
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.any { it }
        val live = pendingLive
        pendingLive = null
        when {
            granted && live != null -> onShareLive(live, area)
            granted -> locate()
            else -> notice = R.string.location_denied
        }
    }

    fun requestLocate() {
        if (DeviceLocation.allowed(context)) locate() else permission.launch(DeviceLocation.permissions)
    }

    fun picked(): GeoLocation? {
        val target = map?.cameraPosition?.target ?: return null
        return GeoLocation(target.latitude, target.longitude, own?.accuracyM?.takeIf { onOwn })
    }

    Box(Modifier.fillMaxSize().background(PockyColors.Bg)) {
        PockyMap(Modifier.fillMaxSize()) { ready, _ ->
            map = ready
            ready.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) onOwn = false
            }
            ready.addOnCameraMoveListener { cameraTick++ }
            ready.addOnCameraIdleListener { cameraTick++ }
            if (DeviceLocation.allowed(context)) locate()
        }

        Canvas(Modifier.fillMaxSize()) {
            cameraTick
            val current = map ?: return@Canvas
            val projection = current.projection

            own?.let { fix ->
                val point = projection.toScreenLocation(fix.toLatLng())
                val center = Offset(point.x, point.y)
                drawCircle(PockyColors.AccentSoft.copy(alpha = 0.25f), 14.dp.toPx(), center)
                drawCircle(PockyColors.Bg, 7.dp.toPx(), center)
                drawCircle(PockyColors.AccentSoft, 5.dp.toPx(), center)
            }

            if (area != LocationArea.EXACT) {
                val pick = picked() ?: return@Canvas
                val blurred = blurLocation(pick, area)
                val radiusM = blurred.accuracyM ?: return@Canvas
                val point = projection.toScreenLocation(LatLng(blurred.latitude, blurred.longitude))
                val radius = (radiusM / projection.getMetersPerPixelAtLatitude(blurred.latitude)).toFloat().dp.toPx()
                val center = Offset(point.x, point.y)
                drawCircle(PockyColors.Accent.copy(alpha = 0.16f), radius, center)
                drawCircle(PockyColors.Accent.copy(alpha = 0.8f), radius, center, style = Stroke(2.dp.toPx()))
            }
        }

        Icon(
            PockyIcons.Location,
            contentDescription = null,
            tint = PockyColors.Accent,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-20).dp)
                .size(44.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            MapButton(onClick = onBack) {
                Icon(PockyIcons.ArrowBack, stringResource(R.string.back), tint = PockyColors.Ink)
            }
            Spacer(Modifier.size(10.dp))
            Text(
                stringResource(R.string.location_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PockyColors.Ink,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(PockyColors.Surface.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            MapButton(
                onClick = ::requestLocate,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 16.dp, bottom = 12.dp),
            ) {
                if (locating) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = PockyColors.Ink, strokeWidth = 2.dp)
                } else {
                    Icon(
                        PockyIcons.Locate,
                        stringResource(R.string.location_locate),
                        tint = if (onOwn) PockyColors.AccentSoft else PockyColors.Ink,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(PockyColors.Surface)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AreaChip(R.string.onboarding_location_exact, area == LocationArea.EXACT, Modifier.weight(1f)) {
                        area = LocationArea.EXACT
                    }
                    AreaChip(R.string.onboarding_location_neighborhood, area == LocationArea.NEIGHBORHOOD, Modifier.weight(1f)) {
                        area = LocationArea.NEIGHBORHOOD
                    }
                    AreaChip(R.string.onboarding_location_city, area == LocationArea.CITY, Modifier.weight(1f)) {
                        area = LocationArea.CITY
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(
                        notice ?: if (area == LocationArea.EXACT) R.string.location_hint_exact else R.string.location_hint_area,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (notice != null) PockyColors.Gold else PockyColors.InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                GradientButton(
                    text = stringResource(R.string.location_send),
                    enabled = map != null,
                    onClick = { picked()?.let { onSend(it, area) } },
                )
                Spacer(Modifier.height(8.dp))
                if (choosingLive) {
                    Text(
                        stringResource(R.string.location_live_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = PockyColors.InkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            LiveLength.MINUTES15 to R.string.live_15m,
                            LiveLength.HOUR1 to R.string.live_1h,
                            LiveLength.HOURS8 to R.string.live_8h,
                        ).forEach { (length, label) ->
                            AreaChip(label, selected = false, modifier = Modifier.weight(1f)) {
                                if (DeviceLocation.allowed(context)) {
                                    onShareLive(length, area)
                                } else {
                                    pendingLive = length
                                    permission.launch(DeviceLocation.permissions)
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.location_share_live),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = PockyColors.AccentSoft,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .clickable(role = Role.Button) { choosingLive = true }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AreaChip(label: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) PockyColors.Ink else PockyColors.InkMuted,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) PockyColors.Accent.copy(alpha = 0.28f) else PockyColors.Bg)
            .border(
                1.dp,
                if (selected) PockyColors.Accent else PockyColors.AccentSoft.copy(alpha = 0.12f),
                RoundedCornerShape(50),
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
    )
}
