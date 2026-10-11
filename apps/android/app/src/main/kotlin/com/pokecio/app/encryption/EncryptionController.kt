package com.pokecio.app.encryption

import android.util.Log
import androidx.annotation.StringRes
import com.pokecio.app.R
import com.pokecio.app.core.EncryptionInfo
import com.pokecio.app.core.EncryptionListener
import com.pokecio.app.core.LogoutWarning
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import com.pokecio.app.core.TaskHandle
import com.pokecio.app.core.Verification
import com.pokecio.app.core.VerificationListener
import com.pokecio.app.core.VerificationRequestListener
import com.pokecio.app.core.VerificationStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RecoveryProgress {
    data object Idle : RecoveryProgress
    data object Busy : RecoveryProgress
    data object Done : RecoveryProgress
    data class Failed(@StringRes val message: Int) : RecoveryProgress
}

sealed interface SetupProgress {
    data object Idle : SetupProgress
    data object Busy : SetupProgress
    data class KeyReady(val key: String) : SetupProgress
    data class Failed(@StringRes val message: Int) : SetupProgress
}

sealed interface PasswordProgress {
    data object Idle : PasswordProgress
    data object Busy : PasswordProgress
    data object Done : PasswordProgress
    data class Failed(@StringRes val message: Int) : PasswordProgress
}

data class ActiveVerification(
    val verification: Verification,
    val step: VerificationStep,
    val busy: Boolean = false,
    val person: String? = null,
)

class EncryptionController(
    private val client: PockyClient,
    private val scope: CoroutineScope,
) {
    private val _info = MutableStateFlow<EncryptionInfo?>(null)
    val info: StateFlow<EncryptionInfo?> = _info.asStateFlow()

    private val _progress = MutableStateFlow<RecoveryProgress>(RecoveryProgress.Idle)
    val progress: StateFlow<RecoveryProgress> = _progress.asStateFlow()

    private val _setup = MutableStateFlow<SetupProgress>(SetupProgress.Idle)
    val setup: StateFlow<SetupProgress> = _setup.asStateFlow()

    private val _identityReset = MutableStateFlow<SetupProgress>(SetupProgress.Idle)
    val identityReset: StateFlow<SetupProgress> = _identityReset.asStateFlow()

    private val _passphraseChange = MutableStateFlow<SetupProgress>(SetupProgress.Idle)
    val passphraseChange: StateFlow<SetupProgress> = _passphraseChange.asStateFlow()

    private val _passwordChange = MutableStateFlow<PasswordProgress>(PasswordProgress.Idle)
    val passwordChange: StateFlow<PasswordProgress> = _passwordChange.asStateFlow()

    private val _problems = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val problems: SharedFlow<Int> = _problems.asSharedFlow()

    private val _verification = MutableStateFlow<ActiveVerification?>(null)
    val verification: StateFlow<ActiveVerification?> = _verification.asStateFlow()

    private val handles = mutableListOf<TaskHandle>()
    private var verificationHandle: TaskHandle? = null

    init {
        try {
            handles += client.observeEncryption(object : EncryptionListener {
                override fun onUpdate(info: EncryptionInfo) {
                    _info.value = info
                }
            })
            handles += client.observeVerificationRequests(object : VerificationRequestListener {
                override fun onRequest(verification: Verification) {
                    val active = _verification.value
                    if (active?.verification?.flowId() == verification.flowId()) return
                    if (active == null) {
                        track(verification, VerificationStep.Incoming, personOf(verification))
                    } else {
                        scope.launch { runCatching { verification.cancel() } }
                    }
                }
            })
        } catch (_: PockyException) {
        }
    }

    fun recover(key: String) {
        if (_progress.value == RecoveryProgress.Busy) return
        _progress.value = RecoveryProgress.Busy

        scope.launch {
            _progress.value = try {
                _info.value = client.recover(key)
                RecoveryProgress.Done
            } catch (e: PockyException) {
                RecoveryProgress.Failed(e.messageRes())
            }
        }
    }

    fun resetProgress() {
        _progress.value = RecoveryProgress.Idle
    }

    fun enableRecovery(password: String, passphrase: String) {
        if (_setup.value == SetupProgress.Busy) return
        _setup.value = SetupProgress.Busy

        scope.launch {
            _setup.value = try {
                SetupProgress.KeyReady(client.enableRecovery(password, passphrase))
            } catch (e: PockyException) {
                SetupProgress.Failed(e.messageRes())
            }
        }
    }

    fun resetSetup() {
        _setup.value = SetupProgress.Idle
    }

    fun resetIdentity(password: String, passphrase: String) {
        if (_identityReset.value == SetupProgress.Busy) return
        _identityReset.value = SetupProgress.Busy

        scope.launch {
            _identityReset.value = try {
                SetupProgress.KeyReady(client.resetIdentity(password, passphrase))
            } catch (e: PockyException) {
                SetupProgress.Failed(e.messageRes())
            }
        }
    }

    fun changePassphrase(passphrase: String) {
        if (_passphraseChange.value == SetupProgress.Busy) return
        _passphraseChange.value = SetupProgress.Busy

        scope.launch {
            _passphraseChange.value = try {
                SetupProgress.KeyReady(client.changeRecoveryPassphrase(passphrase))
            } catch (e: PockyException) {
                SetupProgress.Failed(e.messageRes())
            }
        }
    }

    fun clearPassphraseChange() {
        if (_passphraseChange.value != SetupProgress.Busy) {
            _passphraseChange.value = SetupProgress.Idle
        }
    }

    fun changePassword(current: String, newPassword: String, signOutOthers: Boolean) {
        if (_passwordChange.value == PasswordProgress.Busy) return
        _passwordChange.value = PasswordProgress.Busy

        scope.launch {
            _passwordChange.value = try {
                client.changePassword(current, newPassword, signOutOthers)
                PasswordProgress.Done
            } catch (e: PockyException) {
                PasswordProgress.Failed(e.messageRes())
            }
        }
    }

    fun clearPasswordChange() {
        if (_passwordChange.value != PasswordProgress.Busy) {
            _passwordChange.value = PasswordProgress.Idle
        }
    }

    fun clearIdentityReset() {
        if (_identityReset.value != SetupProgress.Busy) {
            _identityReset.value = SetupProgress.Idle
        }
    }

    suspend fun logoutWarning(): LogoutWarning? = try {
        client.logoutWarning()
    } catch (_: PockyException) {
        null
    }

    fun startSelfVerification() {
        if (_verification.value != null) return

        scope.launch {
            try {
                track(client.requestSelfVerification(), VerificationStep.Requested)
            } catch (_: PockyException) {
                _verification.value = null
            }
        }
    }

    fun startUserVerification(userId: String, name: String?) {
        if (_verification.value != null) return

        scope.launch {
            try {
                track(
                    client.requestUserVerification(userId),
                    VerificationStep.Requested,
                    name ?: localPart(userId),
                )
            } catch (_: PockyException) {
                _verification.value = null
                _problems.tryEmit(R.string.verify_user_failed)
            }
        }
    }

    fun acceptVerification() = verificationAction { it.accept() }

    fun confirmEmojis() = verificationAction { it.confirm() }

    fun rejectEmojis() = verificationAction { it.mismatch() }

    fun cancelVerification() = verificationAction { it.cancel() }

    fun dismissVerification() {
        val active = _verification.value ?: return
        val finished = active.step is VerificationStep.Done || active.step is VerificationStep.Cancelled
        if (!finished) {
            scope.launch { runCatching { active.verification.cancel() } }
        }
        verificationHandle?.cancel()
        verificationHandle?.close()
        verificationHandle = null
        _verification.value = null
    }

    fun close() {
        dismissVerification()
        handles.forEach { it.cancel(); it.close() }
        handles.clear()
    }

    private fun personOf(verification: Verification): String? {
        if (verification.isSelfVerification()) return null
        val userId = verification.otherUserId()
        scope.launch {
            val name = client.userDisplayName(userId) ?: return@launch
            val current = _verification.value ?: return@launch
            if (current.verification == verification) {
                _verification.value = current.copy(person = name)
            }
        }
        return localPart(userId)
    }

    private fun track(verification: Verification, initial: VerificationStep, person: String? = null) {
        _verification.value = ActiveVerification(verification, initial, person = person)
        verificationHandle = verification.observe(object : VerificationListener {
            override fun onStep(step: VerificationStep) {
                Log.i(LOG_TAG, describe(step))
                val current = _verification.value ?: return
                if (current.verification == verification) {
                    _verification.value = current.copy(step = step, busy = false)
                }
            }
        })
    }

    private fun verificationAction(action: suspend (Verification) -> Unit) {
        val active = _verification.value ?: return
        _verification.value = active.copy(busy = true)
        scope.launch {
            try {
                action(active.verification)
            } catch (_: PockyException) {
                _verification.value = _verification.value?.copy(busy = false)
            }
        }
    }
}

private const val LOG_TAG = "PockyVerify"

private fun describe(step: VerificationStep): String = when (step) {
    is VerificationStep.Emojis -> "emojis ${step.emojis.size}"
    is VerificationStep.Cancelled -> "cancelled byUs=${step.byUs} reason=${step.reason}"
    else -> step::class.simpleName.orEmpty()
}

private fun localPart(userId: String): String = userId.removePrefix("@").substringBefore(':')

@StringRes
private fun PockyException.messageRes(): Int = when (this) {
    is PockyException.InvalidRecoveryKey -> R.string.recovery_invalid_key
    is PockyException.WeakPassphrase -> R.string.passphrase_weak_error
    is PockyException.WeakPassword -> R.string.password_weak
    is PockyException.InvalidCredentials -> R.string.setup_wrong_password
    is PockyException.Network, is PockyException.ServerNotFound -> R.string.error_network
    else -> R.string.error_unknown
}
