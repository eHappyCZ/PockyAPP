package com.pokecio.app.location

import android.content.Context
import com.pokecio.app.R
import com.pokecio.app.core.LiveLength
import com.pokecio.app.core.LiveLocationListener
import com.pokecio.app.core.LivePerson
import com.pokecio.app.core.LocationArea
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import com.pokecio.app.core.TaskHandle
import com.pokecio.app.core.liveLengthMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LiveLocationController(
    private val context: Context,
    private val client: PockyClient,
    private val scope: CoroutineScope,
) {
    private val _people = MutableStateFlow<List<LivePerson>>(emptyList())
    val people: StateFlow<List<LivePerson>> = _people.asStateFlow()

    private val _problems = MutableSharedFlow<Int>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val problems: SharedFlow<Int> = _problems.asSharedFlow()

    private var handle: TaskHandle? = null

    fun start() {
        if (handle != null) return
        handle = try {
            client.observeLiveLocations(object : LiveLocationListener {
                override fun onUpdate(people: List<LivePerson>) {
                    _people.value = people
                }
            })
        } catch (_: PockyException) {
            null
        }
    }

    fun resumeOwn(area: LocationArea) {
        if (!DeviceLocation.allowed(context)) return
        scope.launch {
            val running = LiveLocationService.active.value
            val own = try {
                client.ownLiveLocations()
            } catch (_: PockyException) {
                return@launch
            }
            own.filter { it.endsMs.toLong() > System.currentTimeMillis() && it.roomId !in running }
                .forEach { LiveLocationService.share(context, it.roomId, it.endsMs.toLong(), area) }
        }
    }

    fun share(roomId: String, length: LiveLength, area: LocationArea) {
        scope.launch {
            try {
                client.startLiveLocation(roomId, length)
                val endsMs = System.currentTimeMillis() + liveLengthMs(length).toLong()
                LiveLocationService.share(context, roomId, endsMs, area)
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            }
        }
    }

    fun stop(roomId: String) = LiveLocationService.stop(context, roomId)

    fun stopAll() = LiveLocationService.stop(context)

    fun close() {
        handle?.let {
            it.cancel()
            it.close()
        }
        handle = null
    }
}
