package com.pokecio.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import com.pokecio.app.core.GeoLocation
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

object DeviceLocation {

    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    private const val FRESH_MS = 60_000L
    private const val TIMEOUT_MS = 20_000L

    fun allowed(context: Context): Boolean = permissions.any {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    fun enabled(context: Context): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return providers(manager).isNotEmpty()
    }

    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): GeoLocation? {
        if (!allowed(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = providers(manager)
        if (providers.isEmpty()) return null

        val recent = providers
            .mapNotNull { manager.getLastKnownLocation(it) }
            .filter { System.currentTimeMillis() - it.time < FRESH_MS }
            .minByOrNull { it.accuracy }
        if (recent != null && recent.accuracy < 50f) return recent.toGeo()

        val fresh = withTimeoutOrNull(TIMEOUT_MS) { request(manager, providers.first()) }
        return (fresh ?: recent ?: providers.firstNotNullOfOrNull { manager.getLastKnownLocation(it) })?.toGeo()
    }

    fun providers(manager: LocationManager): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
    }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    @SuppressLint("MissingPermission")
    private suspend fun request(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, Runnable::run) { cont.resume(it) }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }
                }
                cont.invokeOnCancellation { manager.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }

    private fun Location.toGeo() = GeoLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
    )
}
