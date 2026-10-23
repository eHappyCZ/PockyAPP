package com.pokecio.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokecio.app.core.ChatLook
import com.pokecio.app.core.LocationArea
import com.pokecio.app.core.Recovery
import com.pokecio.app.core.SyncStatus
import com.pokecio.app.core.VerificationStep
import com.pokecio.app.core.changelog
import com.pokecio.app.core.coreVersion
import com.pokecio.app.location.BackgroundRun
import com.pokecio.app.location.LiveLocationService
import com.pokecio.app.lock.Biometrics
import com.pokecio.app.media.LocalMediaLoader
import com.pokecio.app.media.LocalVoicePlayer
import com.pokecio.app.media.VideoPrep
import com.pokecio.app.media.uriInfo
import com.pokecio.app.rooms.RoomController
import com.pokecio.app.preferences.AppLanguage
import com.pokecio.app.ui.components.SecureWindow
import com.pokecio.app.ui.components.rememberNotificationPermission
import com.pokecio.app.ui.encryption.ChangePassphraseScreen
import com.pokecio.app.ui.encryption.ChangePasswordScreen
import com.pokecio.app.ui.encryption.DeleteAccountScreen
import com.pokecio.app.ui.encryption.LogoutDialog
import com.pokecio.app.ui.encryption.RecoveryScreen
import com.pokecio.app.ui.encryption.ResetIdentityScreen
import com.pokecio.app.ui.encryption.SetupRecoveryScreen
import com.pokecio.app.ui.encryption.VerificationScreen
import com.pokecio.app.ui.home.ChangelogScreen
import com.pokecio.app.ui.home.DevicesScreen
import com.pokecio.app.ui.home.HomeScreen
import com.pokecio.app.ui.home.HomeTab
import com.pokecio.app.ui.home.ProfileScreen
import com.pokecio.app.ui.home.SettingsActions
import com.pokecio.app.ui.home.UpdateSettings
import com.pokecio.app.ui.layout.ChatsLayout
import com.pokecio.app.ui.layout.EmptyChatPane
import com.pokecio.app.ui.layout.LocalScreenSize
import com.pokecio.app.ui.layout.Readable
import com.pokecio.app.ui.layout.currentScreenSize
import com.pokecio.app.ui.lock.AppLockSettingsScreen
import com.pokecio.app.ui.lock.LockScreen
import com.pokecio.app.ui.login.LoginScreen
import com.pokecio.app.ui.login.RegisterScreen
import com.pokecio.app.ui.map.MapTab
import com.pokecio.app.ui.navigation.Screen
import com.pokecio.app.ui.navigation.screenTransition
import com.pokecio.app.ui.onboarding.OnboardingScreen
import com.pokecio.app.ui.people.NewChatScreen
import com.pokecio.app.ui.people.NewGroupScreen
import com.pokecio.app.ui.people.PeopleFlow
import com.pokecio.app.ui.people.asContact
import com.pokecio.app.ui.room.GalleryState
import com.pokecio.app.ui.room.HistoryState
import com.pokecio.app.ui.room.LocalChatLook
import com.pokecio.app.ui.room.MediaSharingState
import com.pokecio.app.ui.room.ModerationState
import com.pokecio.app.ui.room.RoomInfoEdits
import com.pokecio.app.ui.room.RoomInfoState
import com.pokecio.app.ui.room.RoomScreen
import com.pokecio.app.ui.room.UnrestrictedDialog
import com.pokecio.app.ui.rooms.AppLocale
import com.pokecio.app.ui.rooms.InviteHandlers
import com.pokecio.app.ui.rooms.RoomActionHandlers
import com.pokecio.app.ui.rooms.RoomBanners
import com.pokecio.app.ui.rooms.RoomListScreen
import com.pokecio.app.ui.theme.Appearance
import com.pokecio.app.ui.theme.PockyTheme
import com.pokecio.app.ui.theme.useDarkTheme
import com.pokecio.app.ui.update.UpdateDialog
import com.pokecio.app.update.UpdatePhase
import kotlinx.coroutines.flow.merge

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_UPDATE = "update"
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        Appearance.load(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            PockyTheme {
                val darkTheme = useDarkTheme()
                DisposableEffect(darkTheme) {
                    val bars = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                    onDispose { }
                }

                val state by viewModel.state.collectAsStateWithLifecycle()

                LifecycleStartEffect(viewModel) {
                    viewModel.onForeground()
                    onStopOrDispose { viewModel.onBackground() }
                }

                when (val current = state) {
                    AppState.Loading -> Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    )

                    is AppState.LoggedOut -> {
                        SecureWindow(enabled = true)
                        val server by viewModel.server.collectAsStateWithLifecycle()
                        val serverCheck by viewModel.serverCheck.collectAsStateWithLifecycle()
                        var registering by rememberSaveable { mutableStateOf(false) }
                        if (registering) {
                            BackHandler(enabled = !current.busy) { registering = false }
                            RegisterScreen(
                                busy = current.busy,
                                error = current.error,
                                server = server,
                                onRegister = viewModel::register,
                                onBack = { registering = false },
                            )
                        } else {
                            LoginScreen(
                                busy = current.busy,
                                error = current.error,
                                server = server,
                                serverCheck = serverCheck,
                                onLogin = viewModel::login,
                                onCheckServer = viewModel::checkServer,
                                onCloseServer = viewModel::clearServerCheck,
                                onRegister = { registering = true },
                            )
                        }
                    }

                    is AppState.LoggedIn -> {
                        val lockSettings by current.lock.settings.collectAsStateWithLifecycle()
                        val locked by current.lock.locked.collectAsStateWithLifecycle()
                        val pinState by current.lock.pin.collectAsStateWithLifecycle()
                        val lockShown = lockSettings?.enabled == true && locked
                        Box(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .then(if (lockShown || lockSettings == null) Modifier.clearAndSetSemantics { } else Modifier),
                            ) {
                                val rooms by current.rooms.rooms.collectAsStateWithLifecycle()
                                val status by current.rooms.status.collectAsStateWithLifecycle()
                                val open = current.openRoom

                                var onboardingStep by rememberSaveable { mutableIntStateOf(0) }
                                var homeTab by rememberSaveable { mutableStateOf(HomeTab.CHATS) }
                                var peopleFlow by rememberSaveable { mutableStateOf(PeopleFlow.NONE) }
                                var showDevices by rememberSaveable { mutableStateOf(false) }
                                var showPassphrase by rememberSaveable { mutableStateOf(false) }
                                var showPassword by rememberSaveable { mutableStateOf(false) }
                                var showAppLock by rememberSaveable { mutableStateOf(false) }
                                var showDeleteAccount by rememberSaveable { mutableStateOf(false) }
                                var showChangelog by rememberSaveable { mutableStateOf(false) }
                                var roomInfoOpen by rememberSaveable { mutableStateOf(false) }
                                val lastRoom = remember { Kept<RoomController?>(null) }
                                var askUnrestricted by rememberSaveable { mutableStateOf(false) }
                                val roomInfo by current.roomInfo.info.collectAsStateWithLifecycle()
                                val roomInfoBusy by current.roomInfo.busy.collectAsStateWithLifecycle()
                                val roomTrust by current.roomInfo.trust.collectAsStateWithLifecycle()
                                LaunchedEffect(current.openRoom == null) {
                                    if (current.openRoom == null) roomInfoOpen = false
                                }
                                val devices by current.devices.devices.collectAsStateWithLifecycle()
                                val devicesBusy by current.devices.busy.collectAsStateWithLifecycle()
                                val peopleResults by current.people.results.collectAsStateWithLifecycle()
                                val peopleSearching by current.people.searching.collectAsStateWithLifecycle()
                                val peopleBusy by current.people.busy.collectAsStateWithLifecycle()
                                val recentContacts = remember(rooms) {
                                    rooms.orEmpty().filter { it.isDirect && !it.isInvite }.mapNotNull { it.asContact() }
                                }
                                val openChat: (String) -> Unit = { roomId ->
                                    peopleFlow = PeopleFlow.NONE
                                    homeTab = HomeTab.CHATS
                                    current.people.search("")
                                    viewModel.openRoom(roomId)
                                }
                                val ownAvatar by current.preferences.avatarUrl.collectAsStateWithLifecycle()
                                val version = remember { appVersion() }
                                val installedCode = remember { installedVersionCode() }
                                val releases = remember { changelog(AppLocale.language, installedCode) }
                                val profileBusy by current.preferences.profileBusy.collectAsStateWithLifecycle()

                                val preferences by current.preferences.preferences.collectAsStateWithLifecycle()
                                val blocked by current.preferences.blocked.collectAsStateWithLifecycle()
                                val chatLook = preferences?.chatStyle ?: ChatLook.CLASSIC
                                val notifications = rememberNotificationPermission()
                                val presence by current.presence.presence.collectAsStateWithLifecycle()
                                val typing by current.presence.typing.collectAsStateWithLifecycle()
                                val statuses by current.presence.statuses.collectAsStateWithLifecycle()
                                val ownStatus by current.presence.ownStatus.collectAsStateWithLifecycle()
                                val liveShares by LiveLocationService.active.collectAsStateWithLifecycle()
                                val livePeople by current.live.people.collectAsStateWithLifecycle()
                                val updateCheck by viewModel.updates.lastCheck.collectAsStateWithLifecycle()
                                val updateState by viewModel.updates.phase.collectAsStateWithLifecycle()
                                val availableUpdate by viewModel.updates.available.collectAsStateWithLifecycle()
                                val autoUpdate by viewModel.updates.automatic.collectAsStateWithLifecycle()
                                val screenSize = currentScreenSize()
                                LaunchedEffect(current.downloads) {
                                    current.downloads.results.collect { result ->
                                        val text = when {
                                            result.cancelled -> getString(R.string.media_save_cancelled, result.saved)
                                            result.saved == 0 -> getString(R.string.media_save_failed)
                                            result.failed > 0 -> getString(
                                                R.string.media_saved_partly,
                                                result.saved,
                                                result.saved + result.failed,
                                            )
                                            result.saved == 1 -> getString(R.string.media_saved)
                                            else -> resources.getQuantityString(R.plurals.media_saved_many, result.saved, result.saved)
                                        }
                                        Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
                                    }
                                }

                                CompositionLocalProvider(
                                    LocalMediaLoader provides current.media,
                                    LocalVoicePlayer provides current.voice,
                                    LocalChatLook provides chatLook,
                                    LocalScreenSize provides screenSize,
                                ) {
                                    val encryption by current.encryption.info.collectAsStateWithLifecycle()
                                    val recovery by current.encryption.progress.collectAsStateWithLifecycle()
                                    val needsVerification = encryption?.let {
                                        !it.deviceVerified && (it.canRecover || it.canVerifyWithDevice)
                                    } == true

                                    val verification by current.encryption.verification.collectAsStateWithLifecycle()
                                    val setup by current.encryption.setup.collectAsStateWithLifecycle()
                                    val identityReset by current.encryption.identityReset.collectAsStateWithLifecycle()
                                    val passphraseChange by current.encryption.passphraseChange.collectAsStateWithLifecycle()
                                    val passwordChange by current.encryption.passwordChange.collectAsStateWithLifecycle()
                                    val active = verification
                                    LaunchedEffect(active?.step is VerificationStep.Done) {
                                        val room = current.openRoom
                                        if (active?.step is VerificationStep.Done && room != null) {
                                            current.roomInfo.awaitVerified(room.roomId)
                                        }
                                    }
                                    val displayName by current.preferences.displayName.collectAsStateWithLifecycle()
                                    val prefs = preferences

                                    val roomContent: @Composable (RoomController) -> Unit = { shown ->
                                        BackHandler(onBack = viewModel::closeRoom)

                                        val items by shown.items.collectAsStateWithLifecycle()
                                        val failed by shown.failed.collectAsStateWithLifecycle()
                                        val identityChanges by shown.identityChanges.collectAsStateWithLifecycle()
                                        val members by shown.members.collectAsStateWithLifecycle()
                                        val mediaItems by shown.media.items.collectAsStateWithLifecycle()
                                        val mediaLoading by shown.media.loading.collectAsStateWithLifecycle()
                                        val mediaComplete by shown.media.complete.collectAsStateWithLifecycle()
                                        val downloadProgress by current.downloads.progress.collectAsStateWithLifecycle()
                                        LaunchedEffect(shown) {
                                            shown.notices.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
                                        }
                                        val pinned by shown.moderation.pinned.collectAsStateWithLifecycle()
                                        val canPin by shown.moderation.canPin.collectAsStateWithLifecycle()
                                        val lifetime by shown.moderation.lifetime.collectAsStateWithLifecycle()
                                        val canSetLifetime by shown.moderation.canSetLifetime.collectAsStateWithLifecycle()
                                        LaunchedEffect(shown) {
                                            shown.moderation.notices.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
                                        }
                                        val historyItems by shown.history.items.collectAsStateWithLifecycle()
                                        val historyLoading by shown.history.loading.collectAsStateWithLifecycle()
                                        val historyStart by shown.history.reachedStart.collectAsStateWithLifecycle()
                                        var historyOpen by rememberSaveable(shown.roomId) { mutableStateOf(false) }
                                        LaunchedEffect(historyOpen) {
                                            if (historyOpen) shown.history.open() else shown.history.close()
                                        }
                                        val loadingOlder by shown.loadingOlder.collectAsStateWithLifecycle()
                                        val reachedStart by shown.reachedStart.collectAsStateWithLifecycle()
                                        val composing by shown.composing.collectAsStateWithLifecycle()
                                        val sendingImage by shown.sendingImage.collectAsStateWithLifecycle()
                                        val resolver = LocalContext.current.contentResolver

                                        RoomScreen(
                                            roomId = shown.roomId,
                                            room = rooms?.firstOrNull { it.id == shown.roomId },
                                            items = items,
                                            failed = failed,
                                            loadingOlder = loadingOlder,
                                            reachedStart = reachedStart,
                                            presence = presence,
                                            typists = typing[shown.roomId].orEmpty(),
                                            composing = composing,
                                            sendingImage = sendingImage,
                                            problems = merge(shown.problems, current.encryption.problems),
                                            onBack = viewModel::closeRoom,
                                            onLoadOlder = shown::loadOlder,
                                            onSend = shown::send,
                                            onDraftChanged = shown::onDraftChanged,
                                            onReply = shown::startReply,
                                            onEdit = shown::startEdit,
                                            onCancelComposing = shown::cancelComposing,
                                            onToggleReaction = shown::toggleReaction,
                                            onRedact = shown::redact,
                                            identityChanges = identityChanges,
                                            onAcknowledgeIdentity = shown::acknowledgeIdentity,
                                            onVerifyUser = current.encryption::startUserVerification,
                                            members = members,
                                            readReceipts = preferences?.sendReadReceipts != false,
                                            gallery = GalleryState(mediaItems, mediaLoading, mediaComplete),
                                            onGalleryShown = shown.media::open,
                                            sharing = MediaSharingState(
                                                progress = downloadProgress,
                                                rooms = rooms.orEmpty(),
                                                onSave = current.downloads::save,
                                                onCancelSave = current.downloads::cancel,
                                                onForward = shown::forward,
                                            ),
                                            moderation = ModerationState(
                                                pinned = pinned,
                                                canPin = canPin,
                                                lifetime = lifetime,
                                                canSetLifetime = canSetLifetime,
                                                onReport = shown.moderation::report,
                                                onBlock = shown.moderation::block,
                                                onPin = shown.moderation::pin,
                                                onUnpin = shown.moderation::unpin,
                                                onLifetime = shown.moderation::setLifetime,
                                            ),
                                            history = HistoryState(
                                                open = historyOpen,
                                                items = historyItems,
                                                loading = historyLoading,
                                                reachedStart = historyStart,
                                                onShow = { historyOpen = it },
                                                onLoadOlder = shown.history::loadOlder,
                                            ),
                                            info = RoomInfoState(
                                                open = roomInfoOpen,
                                                details = roomInfo,
                                                trust = roomTrust,
                                                busy = roomInfoBusy,
                                                problems = merge(current.roomInfo.problems, current.encryption.problems),
                                                edits = RoomInfoEdits(
                                                    onRename = current.roomInfo::rename,
                                                    onTopic = current.roomInfo::setTopic,
                                                    onAvatar = { uri ->
                                                        current.roomInfo.setAvatar { resolver.openInputStream(uri)?.use { it.readBytes() } }
                                                    },
                                                    onAddPeople = {
                                                        current.people.search("")
                                                        peopleFlow = PeopleFlow.INVITE
                                                    },
                                                    onRemove = current.roomInfo::remove,
                                                    onVerify = {
                                                        roomInfo?.directUserId?.let { userId ->
                                                            current.encryption.startUserVerification(userId, roomInfo?.name)
                                                        }
                                                    },
                                                ),
                                                onShow = { show ->
                                                    roomInfoOpen = show
                                                    if (show) current.roomInfo.load(shown.roomId)
                                                },
                                            ),
                                            roomActions = RoomActionHandlers(
                                                onMarkRead = { current.rooms.markRead(it.id) },
                                                onFavourite = { current.rooms.setFavourite(it.id, !it.isFavourite) },
                                                onMute = { current.rooms.setMuted(it.id, !it.isMuted) },
                                                onLeave = {
                                                    current.rooms.leave(it.id)
                                                    viewModel.closeRoom()
                                                },
                                            ),
                                            onSendVoice = shown::sendVoice,
                                            locationArea = preferences?.locationPrecision ?: LocationArea.CITY,
                                            onSendLocation = shown::sendLocation,
                                            liveUntil = liveShares[shown.roomId],
                                            onShareLive = { length, area ->
                                                current.live.share(shown.roomId, length, area)
                                                if (!BackgroundRun.unrestricted(this@MainActivity)) askUnrestricted = true
                                            },
                                            onStopLive = { current.live.stop(shown.roomId) },
                                            onSendVideos = { uris, caption ->
                                                shown.sendVideos(uris.map { uri -> { VideoPrep.prepare(this@MainActivity, uri) } }, caption)
                                            },
                                            onSendFile = { uri ->
                                                val info = uriInfo(this@MainActivity, uri)
                                                shown.sendFile(
                                                    read = { resolver.openInputStream(uri)?.use { it.readBytes() } },
                                                    name = info.name,
                                                    mime = info.mime,
                                                )
                                            },
                                            onSendImages = { uris, caption, full ->
                                                shown.sendImages(
                                                    reads = uris.map { uri -> { resolver.openInputStream(uri)?.use { it.readBytes() } } },
                                                    caption = caption,
                                                    fullQuality = full,
                                                )
                                            },
                                        )
                                    }

                                    val screen = when {
                                        active != null -> Screen.VERIFICATION
                                        current.overlay == Overlay.RECOVERY -> Screen.RECOVERY
                                        current.overlay == Overlay.SETUP_BACKUP -> Screen.SETUP_BACKUP
                                        current.overlay == Overlay.RESET_IDENTITY -> Screen.RESET_IDENTITY
                                        prefs == null -> Screen.BLANK
                                        !prefs.onboardingDone -> Screen.ONBOARDING
                                        showDevices -> Screen.DEVICES
                                        showPassphrase -> Screen.PASSPHRASE
                                        showPassword -> Screen.PASSWORD
                                        showAppLock -> Screen.APP_LOCK
                                        showDeleteAccount -> Screen.DELETE_ACCOUNT
                                        showChangelog -> Screen.CHANGELOG
                                        peopleFlow == PeopleFlow.NEW_CHAT -> Screen.NEW_CHAT
                                        peopleFlow == PeopleFlow.INVITE -> Screen.INVITE
                                        peopleFlow == PeopleFlow.NEW_GROUP -> Screen.NEW_GROUP
                                        open != null && !screenSize.twoPane -> Screen.ROOM
                                        else -> Screen.HOME
                                    }
                                    if (open != null) lastRoom.value = open
                                    SecureWindow(enabled = prefs?.blockScreenshots != false || screen.sensitive || lockShown)

                                    AnimatedContent(
                                        targetState = screen,
                                        transitionSpec = { screenTransition() },
                                        label = "screen",
                                    ) { target ->
                                        Readable(enabled = target != Screen.HOME && target != Screen.ROOM) {
                                            when (target) {
                                                Screen.VERIFICATION -> if (active != null) {
                                                    BackHandler(onBack = current.encryption::dismissVerification)

                                                    VerificationScreen(
                                                        step = active.step,
                                                        busy = active.busy,
                                                        person = active.person,
                                                        onAccept = current.encryption::acceptVerification,
                                                        onMatch = current.encryption::confirmEmojis,
                                                        onMismatch = current.encryption::rejectEmojis,
                                                        onCancel = current.encryption::dismissVerification,
                                                        onClose = {
                                                            current.encryption.dismissVerification()
                                                            viewModel.closeOverlay()
                                                        },
                                                    )
                                                }
                                                Screen.RECOVERY -> {
                                                    BackHandler(onBack = viewModel::closeOverlay)

                                                    RecoveryScreen(
                                                        progress = recovery,
                                                        canUseKey = encryption?.canRecover == true,
                                                        canUseDevice = encryption?.canVerifyWithDevice == true,
                                                        onRecover = current.encryption::recover,
                                                        onVerifyWithDevice = current.encryption::startSelfVerification,
                                                        onReset = viewModel::openIdentityReset,
                                                        onClose = viewModel::closeOverlay,
                                                    )
                                                }
                                                Screen.RESET_IDENTITY -> {
                                                    BackHandler(onBack = viewModel::closeOverlay)

                                                    ResetIdentityScreen(
                                                        progress = identityReset,
                                                        onReset = current.encryption::resetIdentity,
                                                        onClose = viewModel::closeOverlay,
                                                    )
                                                }
                                                Screen.SETUP_BACKUP -> {
                                                    BackHandler(onBack = viewModel::closeOverlay)

                                                    SetupRecoveryScreen(
                                                        progress = setup,
                                                        onEnable = current.encryption::enableRecovery,
                                                        onClose = viewModel::closeOverlay,
                                                    )
                                                }
                                                Screen.BLANK -> {
                                                    Box(
                                                        Modifier
                                                            .fillMaxSize()
                                                            .background(MaterialTheme.colorScheme.background),
                                                    )
                                                }
                                                Screen.ONBOARDING -> if (prefs != null) {
                                                    OnboardingScreen(
                                                        index = onboardingStep,
                                                        onIndexChange = { onboardingStep = it },
                                                        displayName = displayName,
                                                        preferences = prefs,
                                                        encryption = encryption,
                                                        onUseRecoveryKey = viewModel::openRecovery,
                                                        onVerifyWithDevice = current.encryption::startSelfVerification,
                                                        onChatStyle = current.preferences::setChatStyle,
                                                        onNotificationPreview = current.preferences::setNotificationPreview,
                                                        onLocationPrecision = current.preferences::setLocationPrecision,
                                                        onSharePresence = current.preferences::setSharePresence,
                                                        onShareTyping = current.preferences::setShareTyping,
                                                        onFinish = current.preferences::finishOnboarding,
                                                    )
                                                }
                                                Screen.DEVICES -> {
                                                    BackHandler { showDevices = false }

                                                    DevicesScreen(
                                                        devices = devices,
                                                        busy = devicesBusy,
                                                        problems = current.devices.problems,
                                                        onSignOut = current.devices::signOut,
                                                        onBack = { showDevices = false },
                                                    )
                                                }
                                                Screen.PASSPHRASE -> {
                                                    val closePassphrase = {
                                                        showPassphrase = false
                                                        current.encryption.clearPassphraseChange()
                                                    }
                                                    BackHandler(onBack = closePassphrase)

                                                    ChangePassphraseScreen(
                                                        progress = passphraseChange,
                                                        available = encryption?.recovery == Recovery.ENABLED && encryption?.deviceVerified == true,
                                                        onSave = current.encryption::changePassphrase,
                                                        onClose = closePassphrase,
                                                    )
                                                }
                                                Screen.PASSWORD -> {
                                                    val closePassword = {
                                                        showPassword = false
                                                        current.encryption.clearPasswordChange()
                                                    }
                                                    BackHandler(onBack = closePassword)

                                                    ChangePasswordScreen(
                                                        progress = passwordChange,
                                                        onSave = current.encryption::changePassword,
                                                        onClose = closePassword,
                                                    )
                                                }
                                                Screen.DELETE_ACCOUNT -> {
                                                    val accountDeletion by viewModel.accountDeletion.collectAsStateWithLifecycle()
                                                    val closeDeletion = {
                                                        showDeleteAccount = false
                                                        viewModel.clearAccountDeletion()
                                                    }
                                                    BackHandler(onBack = closeDeletion)

                                                    DeleteAccountScreen(
                                                        userId = current.userId,
                                                        progress = accountDeletion,
                                                        onDelete = viewModel::deleteAccount,
                                                        onClose = closeDeletion,
                                                    )
                                                }
                                                Screen.APP_LOCK -> {
                                                    BackHandler { showAppLock = false }

                                                    AppLockSettingsScreen(
                                                        settings = lockSettings,
                                                        controller = current.lock,
                                                        onBack = { showAppLock = false },
                                                    )
                                                }
                                                Screen.CHANGELOG -> {
                                                    BackHandler { showChangelog = false }

                                                    ChangelogScreen(
                                                        releases = releases,
                                                        installedCode = installedCode,
                                                        onBack = { showChangelog = false },
                                                    )
                                                }
                                                Screen.NEW_CHAT -> {
                                                    BackHandler { peopleFlow = PeopleFlow.NONE }

                                                    NewChatScreen(
                                                        results = peopleResults,
                                                        recent = recentContacts,
                                                        searching = peopleSearching,
                                                        busy = peopleBusy,
                                                        problems = current.people.problems,
                                                        onSearch = current.people::search,
                                                        onPerson = { current.people.openDirect(it.userId, openChat) },
                                                        onNewGroup = {
                                                            current.people.search("")
                                                            peopleFlow = PeopleFlow.NEW_GROUP
                                                        },
                                                        onBack = { peopleFlow = PeopleFlow.NONE },
                                                    )
                                                }
                                                Screen.INVITE -> {
                                                    BackHandler { peopleFlow = PeopleFlow.NONE }

                                                    NewGroupScreen(
                                                        results = peopleResults,
                                                        recent = recentContacts.filterNot { contact ->
                                                            roomInfo?.members.orEmpty().any { it.userId == contact.userId }
                                                        },
                                                        searching = peopleSearching,
                                                        busy = roomInfoBusy,
                                                        problems = current.roomInfo.problems,
                                                        onSearch = current.people::search,
                                                        onCreate = { _, _ -> },
                                                        onBack = { peopleFlow = PeopleFlow.NONE },
                                                        onInvite = { ids ->
                                                            current.roomInfo.invite(ids)
                                                            current.people.search("")
                                                            peopleFlow = PeopleFlow.NONE
                                                        },
                                                    )
                                                }
                                                Screen.NEW_GROUP -> {
                                                    BackHandler { peopleFlow = PeopleFlow.NEW_CHAT }

                                                    NewGroupScreen(
                                                        results = peopleResults,
                                                        recent = recentContacts,
                                                        searching = peopleSearching,
                                                        busy = peopleBusy,
                                                        problems = current.people.problems,
                                                        onSearch = current.people::search,
                                                        onCreate = { name, members -> current.people.createGroup(name, members, openChat) },
                                                        onBack = { peopleFlow = PeopleFlow.NEW_CHAT },
                                                    )
                                                }
                                                Screen.ROOM -> lastRoom.value?.let { roomContent(it) }
                                                Screen.HOME -> {
                                                    BackHandler(enabled = homeTab != HomeTab.CHATS) { homeTab = HomeTab.CHATS }

                                                    HomeScreen(
                                                        tab = homeTab,
                                                        onTab = { homeTab = it },
                                                        onCompose = {
                                                            current.people.search("")
                                                            peopleFlow = PeopleFlow.NEW_CHAT
                                                        },
                                                    ) { bottomPadding ->
                                                        Crossfade(targetState = homeTab, animationSpec = tween(180), label = "tab") { tab ->
                                                            when (tab) {
                                                                HomeTab.MAP -> MapTab(
                                                                    people = livePeople,
                                                                    ownShares = liveShares,
                                                                    bottomPadding = bottomPadding,
                                                                    onOpenChat = openChat,
                                                                    onStopAll = current.live::stopAll,
                                                                )
                                                                HomeTab.ME -> Readable {
                                                                    ProfileScreen(
                                                                        name = displayName,
                                                                        userId = current.userId,
                                                                        avatarUrl = ownAvatar,
                                                                        online = preferences?.sharePresence == true &&
                                                                            status != SyncStatus.OFFLINE && status != SyncStatus.ERROR,
                                                                        preferences = preferences,
                                                                        encryption = encryption,
                                                                        appLockOn = lockSettings?.enabled == true,
                                                                        version = version,
                                                                updates = UpdateSettings(
                                                                    check = updateCheck,
                                                                    checking = updateState is UpdatePhase.Checking,
                                                                    available = availableUpdate?.versionName,
                                                                    automatic = autoUpdate,
                                                                ),
                                                                        bottomPadding = bottomPadding,
                                                                        busy = current.busy,
                                                                        profileBusy = profileBusy,
                                                                        problems = current.preferences.problems,
                                                                        blocked = blocked,
                                                                        actions = SettingsActions(
                                                                            onRename = current.preferences::rename,
                                                                            onPickAvatar = { uri ->
                                                                                current.preferences.changeAvatar {
                                                                                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                                                                }
                                                                            },
                                                                            onRemoveAvatar = current.preferences::removeAvatar,
                                                                            onSharePresence = current.preferences::setSharePresence,
                                                                            onShareTyping = current.preferences::setShareTyping,
                                                            onSendReadReceipts = current.preferences::setSendReadReceipts,
                                                            onBlockScreenshots = current.preferences::setBlockScreenshots,
                                                                            onChatStyle = current.preferences::setChatStyle,
                                                                            onBlockedOpen = current.preferences::loadBlocked,
                                                                            onUnblock = current.preferences::unblock,
                                                                            onNotificationPreview = current.preferences::setNotificationPreview,
                                                                            onLocationPrecision = current.preferences::setLocationPrecision,
                                                                            onVerify = viewModel::openRecovery,
                                                                            onBackup = viewModel::openBackupSetup,
                                                                            onChangePassphrase = { showPassphrase = true },
                                                                            onChangePassword = { showPassword = true },
                                                                        onAppLock = { showAppLock = true },
                                                                        onDeleteAccount = { showDeleteAccount = true },
                                                                            onDevices = {
                                                                                current.devices.refresh()
                                                                                showDevices = true
                                                                            },
                                                                            onChangelog = { showChangelog = true },
                                                                            onOnboarding = {
                                                                                onboardingStep = 0
                                                                                homeTab = HomeTab.CHATS
                                                                                current.preferences.restartOnboarding()
                                                                            },
                                                                            onLogout = viewModel::requestLogout,
                                                                onCheckUpdate = viewModel.updates::open,
                                                                onAutoUpdate = viewModel.updates::setAutomatic,
                                                                        ),
                                                                    )
                                                                }
                                                                else -> ChatsLayout(
                                                                    twoPane = screenSize.twoPane,
                                                                    list = {
                                                                        RoomListScreen(
                                                                            rooms = rooms,
                                                                            banners = RoomBanners(
                                                                                offline = status == SyncStatus.OFFLINE || status == SyncStatus.ERROR,
                                                                                needsVerification = needsVerification,
                                                                                suggestBackup = encryption?.canEnableRecovery == true,
                                                                                notificationsOff = !notifications.granted,
                                                                                onVerify = viewModel::openRecovery,
                                                                                onBackup = viewModel::openBackupSetup,
                                                                                onEnableNotifications = notifications.request,
                                                                            ),
                                                                            presence = presence,
                                                                            typing = typing,
                                                                            ownName = displayName,
                                                                            ownAvatar = ownAvatar,
                                                                            ownUserId = current.userId,
                                                                            sharingPresence = preferences?.sharePresence == true,
                                                                            statuses = statuses,
                                                                            ownStatus = ownStatus,
                                                                            onSaveStatus = current.presence::setStatus,
                                                                            onClearStatus = current.presence::clearStatus,
                                                                            bottomPadding = bottomPadding,
                                                                            problems = current.rooms.problems,
                                                                            actions = RoomActionHandlers(
                                                                                onMarkRead = { current.rooms.markRead(it.id) },
                                                                                onFavourite = { current.rooms.setFavourite(it.id, !it.isFavourite) },
                                                                                onMute = { current.rooms.setMuted(it.id, !it.isMuted) },
                                                                                onLeave = { current.rooms.leave(it.id) },
                                                                            ),
                                                                            invites = InviteHandlers(
                                                                                busy = peopleBusy,
                                                                                onAccept = { current.people.accept(it, openChat) },
                                                                                onDecline = current.people::decline,
                                                                            ),
                                                                            onRoomClick = viewModel::openRoom,
                                                                            onProfile = { homeTab = HomeTab.ME },
                                                                        )
                                                                    },
                                                                    detail = {
                                                                        AnimatedContent(
                                                                            targetState = open,
                                                                            contentKey = { it?.roomId },
                                                                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                                                                            label = "pane",
                                                                        ) { pane ->
                                                                            if (pane != null) roomContent(pane) else EmptyChatPane()
                                                                        }
                                                                    },
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (askUnrestricted) {
                                        UnrestrictedDialog(
                                            onAllow = {
                                                askUnrestricted = false
                                                BackgroundRun.requestUnrestricted(this@MainActivity)
                                            },
                                            onDismiss = { askUnrestricted = false },
                                        )
                                    }

                                    current.logoutPrompt?.let { warning ->
                                        LogoutDialog(
                                            warning = warning,
                                            canBackup = encryption?.canEnableRecovery == true,
                                            onConfirm = viewModel::logout,
                                            onBackup = viewModel::backupBeforeLogout,
                                            onDismiss = viewModel::dismissLogout,
                                        )
                                    }
                                }
                            }
                            if (lockSettings == null) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background),
                                )
                            } else if (lockShown) {
                                BackHandler { moveTaskToBack(true) }
                                val unlockTitle = stringResource(R.string.lock_biometric_title)
                                val usePin = stringResource(R.string.lock_use_pin)
                                LockScreen(
                                    state = pinState,
                                    biometrics = lockSettings?.biometrics == true && Biometrics.available(this@MainActivity),
                                    onPin = current.lock::unlock,
                                    onBiometrics = {
                                        Biometrics.prompt(this@MainActivity, unlockTitle, usePin, current.lock::unlockWithBiometrics)
                                    },
                                    onLogout = viewModel::logout,
                                )
                            }
                        }
                    }
                }

                val update by viewModel.updates.available.collectAsStateWithLifecycle()
                val updateDialog by viewModel.updates.dialog.collectAsStateWithLifecycle()
                val updatePhase by viewModel.updates.phase.collectAsStateWithLifecycle()
                val pending = update
                val lockedApp = (state as? AppState.LoggedIn)?.lock?.let { lock ->
                    val lockLoaded by lock.settings.collectAsStateWithLifecycle()
                    val lockActive by lock.locked.collectAsStateWithLifecycle()
                    lockLoaded?.enabled == true && lockActive
                } == true
                if (updateDialog && pending != null && !lockedApp) {
                    UpdateDialog(
                        update = pending,
                        phase = updatePhase,
                        onUpdate = viewModel.updates::install,
                        onLater = viewModel.updates::later,
                    )
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        AppLanguage.load(newBase)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(AppLocale) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ROOM_ID)?.let(viewModel::openFromNotification)
        if (intent?.getBooleanExtra(EXTRA_UPDATE, false) == true) viewModel.updates.open()
    }

    private fun installedVersionCode(): UInt =
        packageManager.getPackageInfo(packageName, 0).longVersionCode.toUInt()

    private fun appVersion(): String {
        val app = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        return getString(R.string.settings_version_value, app, coreVersion())
    }
}

private class Kept<T>(var value: T)
