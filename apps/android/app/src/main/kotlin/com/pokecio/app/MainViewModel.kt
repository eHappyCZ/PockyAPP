package com.pokecio.app

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pokecio.app.core.LogoutWarning
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import com.pokecio.app.core.TimelineItem
import com.pokecio.app.core.TimelineItemKind
import com.pokecio.app.core.checkServer as findServer
import com.pokecio.app.core.defaultServer
import com.pokecio.app.encryption.DevicesController
import com.pokecio.app.encryption.EncryptionController
import com.pokecio.app.location.LiveLocationController
import com.pokecio.app.encryption.PasswordProgress
import com.pokecio.app.lock.AppLockController
import com.pokecio.app.media.MediaDownloads
import com.pokecio.app.media.MediaLoader
import com.pokecio.app.media.VoicePlayer
import com.pokecio.app.preferences.PreferencesController
import com.pokecio.app.presence.PresenceController
import com.pokecio.app.presence.StatusExpiryWorker
import com.pokecio.app.push.MessageCheckWorker
import com.pokecio.app.push.PushRegistrar
import com.pokecio.app.rooms.PeopleController
import com.pokecio.app.rooms.RoomController
import com.pokecio.app.rooms.RoomInfoController
import com.pokecio.app.rooms.RoomListController
import com.pokecio.app.update.UpdateController
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class Overlay { RECOVERY, SETUP_BACKUP, RESET_IDENTITY }

sealed interface ServerCheck {
    data object Idle : ServerCheck
    data object Checking : ServerCheck
    data class Failed(@StringRes val message: Int) : ServerCheck
}

sealed interface AppState {
    data object Loading : AppState
    data class LoggedOut(val busy: Boolean = false, @StringRes val error: Int? = null) : AppState
    data class LoggedIn(
        val userId: String,
        val rooms: RoomListController,
        val media: MediaLoader,
        val downloads: MediaDownloads,
        val lock: AppLockController,
        val encryption: EncryptionController,
        val preferences: PreferencesController,
        val presence: PresenceController,
        val people: PeopleController,
        val devices: DevicesController,
        val voice: VoicePlayer,
        val roomInfo: RoomInfoController,
        val live: LiveLocationController,
        val openRoom: RoomController? = null,
        val overlay: Overlay? = null,
        val logoutPrompt: LogoutWarning? = null,
        val busy: Boolean = false,
    ) : AppState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val updates = UpdateController(app, viewModelScope)

    private val pocky = app as PockyApp
    private val clients = pocky.clients
    private var pendingRoom: String? = null
    private var client: PockyClient? = null
    private var rooms: RoomListController? = null
    private var presence: PresenceController? = null
    private var roomTracking: Job? = null
    private var listTracking: Job? = null
    private var inForeground = false

    private val _accountDeletion = MutableStateFlow<PasswordProgress>(PasswordProgress.Idle)
    val accountDeletion: StateFlow<PasswordProgress> = _accountDeletion.asStateFlow()

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        viewModelScope.launch { restore() }
    }

    private val loginPrefs = app.getSharedPreferences("login", Context.MODE_PRIVATE)

    private val _server = MutableStateFlow(loginPrefs.getString("server", null) ?: defaultServer())
    val server: StateFlow<String> = _server.asStateFlow()

    private val _serverCheck = MutableStateFlow<ServerCheck>(ServerCheck.Idle)
    val serverCheck: StateFlow<ServerCheck> = _serverCheck.asStateFlow()

    fun checkServer(input: String) {
        if (_serverCheck.value == ServerCheck.Checking) return
        _serverCheck.value = ServerCheck.Checking
        viewModelScope.launch {
            _serverCheck.value = try {
                val info = findServer(input.ifBlank { defaultServer() })
                _server.value = info.server
                loginPrefs.edit().putString("server", info.server).apply()
                ServerCheck.Idle
            } catch (e: PockyException) {
                ServerCheck.Failed(e.messageRes())
            }
        }
    }

    fun clearServerCheck() {
        if (_serverCheck.value != ServerCheck.Checking) _serverCheck.value = ServerCheck.Idle
    }

    fun login(username: String, password: String) {
        if (_state.value !is AppState.LoggedOut) return
        _state.value = AppState.LoggedOut(busy = true)

        viewModelScope.launch {
            try {
                val fresh = clients.login(username, password, deviceName(), _server.value)
                client = fresh
                enterLoggedIn(fresh)
            } catch (e: PockyException) {
                _state.value = AppState.LoggedOut(error = e.messageRes())
            }
        }
    }

    fun openRoom(roomId: String) {
        val current = _state.value as? AppState.LoggedIn ?: return
        val active = client ?: return
        current.openRoom?.close()
        pocky.notifier.cancel(roomId)
        if (inForeground) pocky.visibleRoomId = roomId
        val room = RoomController(active, roomId, viewModelScope)
        _state.value = current.copy(openRoom = room)

        roomTracking?.cancel()
        roomTracking = viewModelScope.launch {
            room.items.filterNotNull().collect { items ->
                current.presence.trackRoom(senders(items))
            }
        }
    }

    fun closeRoom() {
        val current = _state.value as? AppState.LoggedIn ?: return
        current.openRoom?.close()
        current.voice.stop()
        pocky.visibleRoomId = null
        roomTracking?.cancel()
        roomTracking = null
        current.presence.trackRoom(emptyList())
        _state.value = current.copy(openRoom = null)
    }

    fun openRecovery() = showOverlay(Overlay.RECOVERY)

    fun openBackupSetup() = showOverlay(Overlay.SETUP_BACKUP)

    fun openIdentityReset() = showOverlay(Overlay.RESET_IDENTITY)

    fun closeOverlay() {
        val current = _state.value as? AppState.LoggedIn ?: return
        current.encryption.resetProgress()
        current.encryption.resetSetup()
        current.encryption.clearIdentityReset()
        _state.value = current.copy(overlay = null)
    }

    fun requestLogout() {
        val current = _state.value as? AppState.LoggedIn ?: return
        viewModelScope.launch {
            val warning = current.encryption.logoutWarning()
                ?: LogoutWarning(lastDevice = false, recoveryEnabled = true, losesHistory = false)
            (_state.value as? AppState.LoggedIn)?.let { _state.value = it.copy(logoutPrompt = warning) }
        }
    }

    fun dismissLogout() {
        val current = _state.value as? AppState.LoggedIn ?: return
        _state.value = current.copy(logoutPrompt = null)
    }

    fun backupBeforeLogout() {
        val current = _state.value as? AppState.LoggedIn ?: return
        current.encryption.resetSetup()
        _state.value = current.copy(logoutPrompt = null, overlay = Overlay.SETUP_BACKUP)
    }

    private fun showOverlay(overlay: Overlay) {
        val current = _state.value as? AppState.LoggedIn ?: return
        current.encryption.resetProgress()
        current.encryption.resetSetup()
        current.encryption.clearIdentityReset()
        _state.value = current.copy(overlay = overlay)
    }

    fun logout() {
        val current = _state.value as? AppState.LoggedIn ?: return
        _state.value = current.copy(busy = true, logoutPrompt = null)
        viewModelScope.launch { tearDown(current) { clients.logout() } }
    }

    fun deleteAccount(password: String) {
        val current = _state.value as? AppState.LoggedIn ?: return
        val active = client ?: return
        if (_accountDeletion.value == PasswordProgress.Busy) return
        _accountDeletion.value = PasswordProgress.Busy

        viewModelScope.launch {
            try {
                active.deleteAccount(password)
            } catch (e: PockyException) {
                _accountDeletion.value = PasswordProgress.Failed(
                    when (e) {
                        is PockyException.InvalidCredentials -> R.string.setup_wrong_password
                        is PockyException.Network, is PockyException.ServerNotFound -> R.string.error_network
                        else -> R.string.error_unknown
                    },
                )
                return@launch
            }
            _accountDeletion.value = PasswordProgress.Idle
            tearDown(current) { clients.forget() }
        }
    }

    fun clearAccountDeletion() {
        if (_accountDeletion.value != PasswordProgress.Busy) _accountDeletion.value = PasswordProgress.Idle
    }

    private suspend fun tearDown(current: AppState.LoggedIn, signOut: suspend () -> Unit) {
        current.openRoom?.close()
        current.encryption.close()
        current.voice.stop()
        current.media.clear()
        StatusExpiryWorker.schedule(pocky, null)
        pocky.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        current.people.close()
        current.live.stopAll()
        current.live.close()
        rooms?.close()
        rooms = null
        listTracking?.cancel()
        roomTracking?.cancel()
        presence?.close()
        presence = null
        client = null
        pocky.visibleRoomId = null
        MessageCheckWorker.cancel(pocky)
        signOut()
        _state.value = AppState.LoggedOut()
    }

    fun openFromNotification(roomId: String) {
        val current = _state.value as? AppState.LoggedIn
        if (current == null) {
            pendingRoom = roomId
            return
        }
        if (current.openRoom?.roomId != roomId) openRoom(roomId)
    }

    private suspend fun setUpPush(client: PockyClient) {
        val token = PushRegistrar.fetchToken()
        if (token == null) {
            MessageCheckWorker.schedule(pocky)
        } else {
            MessageCheckWorker.cancel(pocky)
            PushRegistrar.register(client, token)
        }
    }

    fun onForeground() {
        (_state.value as? AppState.LoggedIn)?.lock?.onForeground()
        updates.onForeground()
        inForeground = true
        pocky.inForeground = true
        (_state.value as? AppState.LoggedIn)?.openRoom?.let { pocky.visibleRoomId = it.roomId }
        rooms?.start()
        presence?.start()
    }

    fun onBackground() {
        (_state.value as? AppState.LoggedIn)?.lock?.onBackground()
        inForeground = false
        pocky.inForeground = false
        pocky.visibleRoomId = null
        rooms?.stop()
        presence?.stop()
    }

    private suspend fun restore() {
        if (!clients.hasSession()) {
            _state.value = AppState.LoggedOut()
            return
        }

        try {
            val restored = clients.current()
            if (restored == null) {
                _state.value = AppState.LoggedOut()
                return
            }
            client = restored
            enterLoggedIn(restored)
        } catch (_: PockyException) {
            clients.forget()
            _state.value = AppState.LoggedOut(error = R.string.error_session_expired)
        }
    }

    private fun enterLoggedIn(client: PockyClient) {
        val controller = RoomListController(client, viewModelScope)
        val preferences = PreferencesController(client, viewModelScope)
        val presenceTracker = PresenceController(pocky, client, viewModelScope, preferences.preferences)
        rooms = controller
        presence = presenceTracker
        listTracking?.cancel()
        listTracking = viewModelScope.launch {
            controller.rooms.filterNotNull().collect { list ->
                presenceTracker.trackRoomList(list.mapNotNull { it.directUserId })
            }
        }
        if (inForeground) {
            controller.start()
            presenceTracker.start()
        }
        val media = MediaLoader(client, viewModelScope, File(pocky.cacheDir, "media"))
        val live = LiveLocationController(pocky, client, viewModelScope)
        live.start()
        viewModelScope.launch {
            live.resumeOwn(preferences.preferences.filterNotNull().first().locationPrecision)
        }
        _state.value = AppState.LoggedIn(
            userId = client.userId().orEmpty(),
            rooms = controller,
            media = media,
            downloads = MediaDownloads(pocky, media, viewModelScope),
            lock = AppLockController(client, viewModelScope),
            encryption = EncryptionController(client, viewModelScope),
            preferences = preferences,
            presence = presenceTracker,
            people = PeopleController(client, viewModelScope),
            devices = DevicesController(client, viewModelScope),
            voice = VoicePlayer(media, viewModelScope),
            roomInfo = RoomInfoController(client, viewModelScope),
            live = live,
        )
        viewModelScope.launch { setUpPush(client) }
        pendingRoom?.let {
            pendingRoom = null
            openRoom(it)
        }
    }

    override fun onCleared() {
        (_state.value as? AppState.LoggedIn)?.let {
            it.openRoom?.close()
            it.encryption.close()
            it.live.close()
        }
        rooms?.close()
        presence?.close()
    }

    private fun deviceName() = "PockyAPP (${Build.MODEL})"
}

private fun senders(items: List<TimelineItem>): List<String> =
    items.asReversed()
        .mapNotNull { (it.kind as? TimelineItemKind.Event)?.event }
        .filterNot { it.isOwn }
        .map { it.senderId }
        .distinct()

@StringRes
private fun PockyException.messageRes(): Int = when (this) {
    is PockyException.PasswordLoginUnsupported -> R.string.error_password_login_unsupported
    is PockyException.ServerNotFound -> R.string.error_server_not_found
    is PockyException.InvalidCredentials -> R.string.error_invalid_credentials
    is PockyException.UserDeactivated -> R.string.error_user_deactivated
    is PockyException.RateLimited -> R.string.error_rate_limited
    is PockyException.Network -> R.string.error_network
    else -> R.string.error_unknown
}
