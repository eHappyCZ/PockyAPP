package com.pokecio.app.ui.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.GeoLocation
import com.pokecio.app.core.LivePerson
import com.pokecio.app.location.DeviceLocation
import com.pokecio.app.ui.layout.LocalScreenSize
import com.pokecio.app.ui.room.timeLabel
import com.pokecio.app.ui.rooms.RoomAvatar
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap

private const val PERSON_ZOOM = 14.0
private const val STALE_MS = 10 * 60_000L
private val MarkerSize = 44.dp
private val SidePanelWidth = 340.dp

@Composable
fun MapTab(
    people: List<LivePerson>,
    ownShares: Map<String, Long>,
    bottomPadding: Dp,
    onOpenChat: (String) -> Unit,
    onStopAll: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var cameraTick by remember { mutableIntStateOf(0) }
    var own by remember { mutableStateOf<GeoLocation?>(null) }
    var locating by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val wide = LocalScreenSize.current.twoPane
    val others = people.filter { !it.isOwn && it.location != null }.distinctBy { it.userId }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    fun focus(location: GeoLocation) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(location.toLatLng(), PERSON_ZOOM))
    }

    fun locate(move: Boolean) {
        if (locating) return
        locating = true
        scope.launch {
            val fix = DeviceLocation.current(context)
            locating = false
            if (fix != null) {
                own = fix
                if (move) focus(fix)
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) locate(move = true)
    }

    Box(Modifier.fillMaxSize().background(PockyColors.Bg)) {
        PockyMap(Modifier.fillMaxSize()) { ready, _ ->
            map = ready
            ready.addOnCameraMoveListener { cameraTick++ }
            ready.addOnCameraIdleListener { cameraTick++ }
            others.firstOrNull()?.location?.let { ready.moveCamera(CameraUpdateFactory.newLatLngZoom(it.toLatLng(), PERSON_ZOOM - 1)) }
            if (DeviceLocation.allowed(context)) locate(move = others.isEmpty())
        }

        Canvas(Modifier.fillMaxSize()) {
            cameraTick
            val current = map ?: return@Canvas
            own?.let { fix ->
                val point = current.projection.toScreenLocation(fix.toLatLng())
                val center = Offset(point.x, point.y)
                drawCircle(PockyColors.AccentSoft.copy(alpha = 0.25f), 14.dp.toPx(), center)
                drawCircle(PockyColors.Bg, 7.dp.toPx(), center)
                drawCircle(PockyColors.AccentSoft, 5.dp.toPx(), center)
            }
        }

        map?.let { current ->
            cameraTick
            others.forEach { person ->
                val location = person.location ?: return@forEach
                val point = current.projection.toScreenLocation(location.toLatLng())
                val stale = now - (person.updatedMs?.toLong() ?: 0L) > STALE_MS
                PersonMarker(
                    person = person,
                    stale = stale,
                    modifier = Modifier.offset(
                        x = (point.x / density).dp - MarkerSize / 2 - 3.dp,
                        y = (point.y / density).dp - MarkerSize - 12.dp,
                    ),
                    onClick = { focus(location) },
                )
            }
        }

        Column(
            Modifier
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.map_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PockyColors.Ink,
            )
            Text(
                if (others.isEmpty()) {
                    stringResource(R.string.map_nobody)
                } else {
                    pluralStringResource(R.plurals.map_people, others.size, others.size)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = PockyColors.InkMuted,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding + 12.dp),
        ) {
            MapButton(
                onClick = {
                    if (DeviceLocation.allowed(context)) locate(move = true) else permission.launch(DeviceLocation.permissions)
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 16.dp, bottom = 12.dp),
            ) {
                if (locating) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = PockyColors.Ink, strokeWidth = 2.dp)
                } else {
                    Icon(PockyIcons.Locate, stringResource(R.string.location_locate), tint = PockyColors.Ink)
                }
            }

            if (!wide) {
                if (ownShares.isNotEmpty()) {
                    OwnSharingStrip(ownShares, onStopAll)
                    Spacer(Modifier.height(10.dp))
                }

                if (others.isEmpty()) {
                    EmptyCard()
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(others, key = { it.userId }) { person ->
                            PersonCard(
                                person = person,
                                now = now,
                                onFocus = { person.location?.let(::focus) },
                                onChat = { onOpenChat(person.roomId) },
                            )
                        }
                    }
                }
            }
        }

        if (wide) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
                    .width(SidePanelWidth)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (ownShares.isNotEmpty()) OwnSharingStrip(ownShares, onStopAll)
                if (others.isEmpty()) {
                    EmptyCard()
                } else {
                    others.forEach { person ->
                        PersonCard(
                            person = person,
                            now = now,
                            onFocus = { person.location?.let(::focus) },
                            onChat = { onOpenChat(person.roomId) },
                            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonMarker(person: LivePerson, stale: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .alpha(if (stale) 0.55f else 1f)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(
            Modifier
                .border(3.dp, if (stale) PockyColors.InkMuted else PockyColors.Accent, CircleShape)
                .padding(3.dp),
        ) {
            RoomAvatar(id = person.userId, name = person.name ?: person.userId, url = person.avatarUrl, size = MarkerSize)
        }
        Box(
            Modifier
                .size(width = 3.dp, height = 10.dp)
                .background(if (stale) PockyColors.InkMuted else PockyColors.Accent),
        )
    }
}

@Composable
private fun PersonCard(
    person: LivePerson,
    now: Long,
    onFocus: () -> Unit,
    onChat: () -> Unit,
    modifier: Modifier = Modifier.width(260.dp),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(PockyColors.Surface)
            .border(1.dp, PockyColors.AccentSoft.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onFocus)
            .padding(12.dp),
    ) {
        RoomAvatar(id = person.userId, name = person.name ?: person.userId, url = person.avatarUrl, size = 42.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                person.name ?: person.userId,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = PockyColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${updatedLabel(person.updatedMs?.toLong(), now)} · ${stringResource(R.string.map_until, timeLabel(person.endsMs.toLong()))}",
                style = MaterialTheme.typography.bodySmall,
                color = PockyColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.map_write),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = PockyColors.AccentSoft,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onChat)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun updatedLabel(updatedMs: Long?, now: Long): String {
    if (updatedMs == null) return stringResource(R.string.map_waiting)
    val minutes = ((now - updatedMs) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.map_just_now)
        minutes < 60 -> stringResource(R.string.map_minutes_ago, minutes)
        else -> stringResource(R.string.map_hours_ago, minutes / 60)
    }
}

@Composable
private fun OwnSharingStrip(shares: Map<String, Long>, onStopAll: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PockyColors.Success.copy(alpha = 0.14f))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(PockyColors.Success, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            pluralStringResource(R.plurals.map_own_sharing, shares.size, shares.size),
            style = MaterialTheme.typography.bodyMedium,
            color = PockyColors.Ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.map_stop_all),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = PockyColors.Success,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onStopAll)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EmptyCard() {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PockyColors.Surface)
            .border(1.dp, PockyColors.AccentSoft.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.map_empty_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = PockyColors.Ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.map_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = PockyColors.InkMuted,
        )
    }
}
