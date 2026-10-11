package com.pokecio.app.lock

import android.os.SystemClock
import com.pokecio.app.core.AppLock
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import com.pokecio.app.core.UnlockOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PinState {
    data object Idle : PinState
    data object Checking : PinState
    data class Wrong(val attemptsLeft: Int) : PinState
    data class Blocked(val untilMs: Long) : PinState
}

class AppLockController(private val client: PockyClient, private val scope: CoroutineScope) {

    private val _settings = MutableStateFlow<AppLock?>(null)
    val settings: StateFlow<AppLock?> = _settings.asStateFlow()

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _pin = MutableStateFlow<PinState>(PinState.Idle)
    val pin: StateFlow<PinState> = _pin.asStateFlow()

    private var backgroundAt: Long? = null

    init {
        scope.launch {
            val loaded = load()
            _locked.value = loaded?.enabled == true
            loaded?.blockedUntilMs?.toLong()?.takeIf { it > System.currentTimeMillis() }?.let {
                _pin.value = PinState.Blocked(it)
            }
        }
    }

    fun onBackground() {
        backgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        val since = backgroundAt ?: return
        backgroundAt = null
        val current = _settings.value ?: return
        if (current.enabled && SystemClock.elapsedRealtime() - since >= current.timeoutSeconds.toLong() * 1000) {
            _pin.value = PinState.Idle
            _locked.value = true
        }
    }

    fun unlock(pin: String) {
        if (_pin.value == PinState.Checking) return
        _pin.value = PinState.Checking
        scope.launch {
            val outcome = try {
                client.unlockApp(pin)
            } catch (_: PockyException) {
                null
            }
            when (outcome) {
                UnlockOutcome.Unlocked -> {
                    _pin.value = PinState.Idle
                    _locked.value = false
                }
                is UnlockOutcome.WrongPin -> _pin.value = PinState.Wrong(outcome.attemptsLeft.toInt())
                is UnlockOutcome.Blocked -> _pin.value = PinState.Blocked(outcome.untilMs.toLong())
                null -> _pin.value = PinState.Idle
            }
        }
    }

    fun unlockWithBiometrics() {
        scope.launch {
            try {
                client.unlockAppWithBiometrics()
            } catch (_: PockyException) {
            }
            _pin.value = PinState.Idle
            _locked.value = false
        }
    }

    suspend fun enable(pin: String, biometrics: Boolean, timeoutSeconds: Int): Boolean = try {
        client.enableAppLock(pin, biometrics, timeoutSeconds.toUInt())
        load()
        _locked.value = false
        true
    } catch (_: PockyException) {
        false
    }

    fun update(biometrics: Boolean, timeoutSeconds: Int) {
        scope.launch {
            try {
                client.updateAppLock(biometrics, timeoutSeconds.toUInt())
                load()
            } catch (_: PockyException) {
            }
        }
    }

    suspend fun verify(pin: String): PinState = try {
        when (val outcome = client.unlockApp(pin)) {
            UnlockOutcome.Unlocked -> PinState.Idle
            is UnlockOutcome.WrongPin -> PinState.Wrong(outcome.attemptsLeft.toInt())
            is UnlockOutcome.Blocked -> PinState.Blocked(outcome.untilMs.toLong())
        }
    } catch (_: PockyException) {
        PinState.Wrong(0)
    }

    suspend fun disable(pin: String): PinState {
        val result = try {
            when (val outcome = client.disableAppLock(pin)) {
                UnlockOutcome.Unlocked -> PinState.Idle
                is UnlockOutcome.WrongPin -> PinState.Wrong(outcome.attemptsLeft.toInt())
                is UnlockOutcome.Blocked -> PinState.Blocked(outcome.untilMs.toLong())
            }
        } catch (_: PockyException) {
            PinState.Wrong(0)
        }
        load()
        return result
    }

    private suspend fun load(): AppLock? = try {
        client.appLock().also { _settings.value = it }
    } catch (_: PockyException) {
        _settings.value = AppLock(enabled = false, biometrics = false, timeoutSeconds = 0u, blockedUntilMs = 0uL)
        null
    }
}
