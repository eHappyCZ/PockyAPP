package com.pokecio.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.pokecio.app.PockyApp
import com.pokecio.app.R
import com.pokecio.app.core.AppUpdate
import com.pokecio.app.core.DownloadListener
import com.pokecio.app.core.PockyException
import com.pokecio.app.core.checkForUpdate
import com.pokecio.app.core.downloadAppUpdate
import com.pokecio.app.ui.rooms.AppLocale
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdatePhase {
    data object Idle : UpdatePhase
    data object Checking : UpdatePhase
    data class Downloading(val progress: Float?) : UpdatePhase
    data object Installing : UpdatePhase
    data class Failed(@StringRes val message: Int) : UpdatePhase
}

enum class UpdateCheck { UP_TO_DATE, AVAILABLE, FAILED }

class UpdateController(private val context: Context, private val scope: CoroutineScope) {

    private val prefs = prefs(context)

    private val _available = MutableStateFlow<AppUpdate?>(null)
    val available: StateFlow<AppUpdate?> = _available.asStateFlow()

    private val _dialog = MutableStateFlow(false)
    val dialog: StateFlow<Boolean> = _dialog.asStateFlow()

    private val _phase = MutableStateFlow<UpdatePhase>(UpdatePhase.Idle)
    val phase: StateFlow<UpdatePhase> = _phase.asStateFlow()

    private val _lastCheck = MutableStateFlow<UpdateCheck?>(null)
    val lastCheck: StateFlow<UpdateCheck?> = _lastCheck.asStateFlow()

    private var checkedThisRun = false

    private val _automatic = MutableStateFlow(isAutomatic(prefs))
    val automatic: StateFlow<Boolean> = _automatic.asStateFlow()

    init {
        installFailures = { _phase.value = UpdatePhase.Failed(R.string.update_install_failed) }
    }

    fun onForeground() {
        if (!_automatic.value) return
        val known = _available.value
        if (known != null && !snoozed(prefs, known.versionCode.toLong())) showDialog()
        if (checkedThisRun && System.currentTimeMillis() - prefs.getLong(KEY_CHECKED, 0L) < CHECK_EVERY_MS) return
        checkedThisRun = true
        check(manual = false)
    }

    fun setAutomatic(enabled: Boolean) {
        _automatic.value = enabled
        prefs.edit { putBoolean(KEY_AUTOMATIC, enabled) }
        if (enabled) UpdateCheckWorker.schedule(context) else UpdateCheckWorker.cancel(context)
    }

    fun check(manual: Boolean = true) {
        if (_phase.value is UpdatePhase.Checking || _phase.value is UpdatePhase.Downloading) return
        _phase.value = UpdatePhase.Checking
        scope.launch {
            try {
                val update = checkForUpdate(currentCode(), Build.VERSION.SDK_INT.toUInt(), AppLocale.language)
                markChecked(prefs)
                _available.value = update
                _lastCheck.value = if (update != null) UpdateCheck.AVAILABLE else UpdateCheck.UP_TO_DATE
                if (update != null && (manual || !snoozed(prefs, update.versionCode.toLong()))) showDialog()
                _phase.value = UpdatePhase.Idle
            } catch (_: PockyException) {
                _lastCheck.value = UpdateCheck.FAILED
                _phase.value = if (manual) UpdatePhase.Failed(R.string.update_check_failed) else UpdatePhase.Idle
            }
        }
    }

    fun open() {
        if (_available.value != null) showDialog() else check()
    }

    private fun showDialog() {
        _dialog.value = true
        (context as? PockyApp)?.notifier?.cancelUpdate()
    }

    fun later() {
        _available.value?.let {
            prefs.edit {
                putLong(KEY_DISMISSED, it.versionCode.toLong())
                putLong(KEY_DISMISSED_AT, System.currentTimeMillis())
            }
        }
        _dialog.value = false
        if (_phase.value is UpdatePhase.Failed) _phase.value = UpdatePhase.Idle
    }

    fun install() {
        val update = _available.value ?: return
        if (_phase.value is UpdatePhase.Downloading || _phase.value is UpdatePhase.Installing) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            _phase.value = UpdatePhase.Failed(R.string.update_allow_install)
            return
        }

        _phase.value = UpdatePhase.Downloading(null)
        scope.launch {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apk = File(dir, "pockyapp-${update.versionCode}.apk")
            try {
                downloadAppUpdate(update, apk.path, object : DownloadListener {
                    override fun onProgress(received: ULong, total: ULong?) {
                        val fraction = total?.takeIf { it > 0u }?.let { received.toFloat() / it.toFloat() }
                        _phase.value = UpdatePhase.Downloading(fraction)
                    }
                })
            } catch (_: PockyException) {
                _phase.value = UpdatePhase.Failed(R.string.update_download_failed)
                return@launch
            }

            if (!sameSigner(apk)) {
                apk.delete()
                _phase.value = UpdatePhase.Failed(R.string.update_signature_failed)
                return@launch
            }

            _phase.value = UpdatePhase.Installing
            try {
                withContext(Dispatchers.IO) { commit(apk) }
            } catch (_: Exception) {
                _phase.value = UpdatePhase.Failed(R.string.update_install_failed)
            }
        }
    }

    private fun currentCode(): UInt = installedCode(context)

    private fun sameSigner(apk: File): Boolean {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return false
        if (archive.packageName != context.packageName) return false
        val incoming = signers(archive)
        if (incoming.isEmpty()) return false
        return incoming ==signers(pm.getPackageInfo(context.packageName, flags))
    }

    private fun signers(info: PackageInfo): Set<String> {
        val digest = MessageDigest.getInstance("SHA-256")
        return info.signingInfo?.apkContentsSigners.orEmpty()
            .map { signature -> digest.digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }
            .toSet()
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("pockyapp.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateInstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    companion object {
        private const val STORE = "updates"
        private const val KEY_CHECKED = "checked"
        private const val KEY_DISMISSED = "dismissed"
        private const val KEY_DISMISSED_AT = "dismissed_at"
        private const val KEY_AUTOMATIC = "automatic"
        private const val KEY_NOTIFIED = "notified"
        private const val CHECK_EVERY_MS = 15 * 60 * 1000L
        private const val SNOOZE_MS = 24 * 60 * 60 * 1000L

        fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

        fun isAutomatic(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_AUTOMATIC, true)

        fun snoozed(prefs: SharedPreferences, versionCode: Long): Boolean =
            prefs.getLong(KEY_DISMISSED, 0L) == versionCode &&
                System.currentTimeMillis() - prefs.getLong(KEY_DISMISSED_AT, 0L) < SNOOZE_MS

        fun markChecked(prefs: SharedPreferences) {
            prefs.edit { putLong(KEY_CHECKED, System.currentTimeMillis()) }
        }

        fun notified(prefs: SharedPreferences): Long = prefs.getLong(KEY_NOTIFIED, 0L)

        fun markNotified(prefs: SharedPreferences, versionCode: Long) {
            prefs.edit { putLong(KEY_NOTIFIED, versionCode) }
        }

        fun installedCode(context: Context): UInt =
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toUInt()

        @Volatile
        internal var installFailures: (() -> Unit)? = null
    }
}
