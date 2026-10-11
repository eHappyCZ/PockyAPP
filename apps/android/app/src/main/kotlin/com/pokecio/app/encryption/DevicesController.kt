package com.pokecio.app.encryption

import com.pokecio.app.R
import com.pokecio.app.core.AccountDevice
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DevicesController(
    private val client: PockyClient,
    private val scope: CoroutineScope,
) {
    private val _devices = MutableStateFlow<List<AccountDevice>?>(null)
    val devices: StateFlow<List<AccountDevice>?> = _devices.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _problems = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val problems: SharedFlow<Int> = _problems.asSharedFlow()

    fun refresh() {
        scope.launch {
            try {
                _devices.value = client.devices()
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.devices_load_failed)
            }
        }
    }

    fun signOut(deviceId: String, password: String, onDone: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            try {
                client.signOutDevice(deviceId, password)
                _devices.value = _devices.value?.filterNot { it.deviceId == deviceId }
                onDone()
            } catch (_: PockyException.InvalidCredentials) {
                _problems.tryEmit(R.string.devices_wrong_password)
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            } finally {
                _busy.value = false
            }
        }
    }
}
