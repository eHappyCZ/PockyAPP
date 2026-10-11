package com.pokecio.app.ui.room

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokecio.app.R
import com.pokecio.app.core.ChatLook
import com.pokecio.app.core.GeoLocation
import com.pokecio.app.core.IdentityChange
import com.pokecio.app.core.LastMessage
import com.pokecio.app.core.LiveLength
import com.pokecio.app.core.LocationArea
import com.pokecio.app.core.Member
import com.pokecio.app.core.PresenceStatus
import com.pokecio.app.core.Room
import com.pokecio.app.core.RoomInfo
import com.pokecio.app.core.TimelineContent
import com.pokecio.app.core.TimelineEvent
import com.pokecio.app.core.TimelineItem
import com.pokecio.app.core.TimelineItemKind
import com.pokecio.app.core.TimelineReaction
import com.pokecio.app.core.TimelineReply
import com.pokecio.app.core.TimelineSendState
import com.pokecio.app.core.Typist
import com.pokecio.app.core.UserTrust
import com.pokecio.app.core.parseGeoUri
import com.pokecio.app.media.AnimatedMatrixImage
import com.pokecio.app.media.KeyboardImages
import com.pokecio.app.media.LocalMediaLoader
import com.pokecio.app.media.MatrixImage
import com.pokecio.app.media.MediaSaver
import com.pokecio.app.media.Recording
import com.pokecio.app.media.SavedMedia
import com.pokecio.app.media.VoiceRecorder
import com.pokecio.app.media.isAnimatedImage
import com.pokecio.app.media.rememberMatrixImage
import com.pokecio.app.media.uriInfo
import com.pokecio.app.rooms.Composing
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.components.comingSoon
import com.pokecio.app.ui.layout.LocalScreenSize
import com.pokecio.app.ui.layout.SidePanel
import com.pokecio.app.ui.navigation.PushedLayer
import com.pokecio.app.ui.navigation.RaisedLayer
import com.pokecio.app.ui.rooms.AuraAvatar
import com.pokecio.app.ui.rooms.AvatarAura
import com.pokecio.app.ui.rooms.LeaveRoomDialog
import com.pokecio.app.ui.rooms.RoomActionHandlers
import com.pokecio.app.ui.rooms.auraOf
import com.pokecio.app.ui.rooms.previewText
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    roomId: String,
    room: Room?,
    items: List<TimelineItem>?,
    failed: Boolean,
    loadingOlder: Boolean,
    reachedStart: Boolean,
    onBack: () -> Unit,
    presence: Map<String, PresenceStatus>,
    typists: List<Typist>,
    composing: Composing,
    sendingImage: Boolean,
    problems: Flow<Int>,
    onLoadOlder: () -> Unit,
    onSend: (String) -> Unit,
    onDraftChanged: (String) -> Unit,
    onReply: (TimelineEvent) -> Unit,
    onEdit: (TimelineEvent) -> Unit,
    onCancelComposing: () -> Unit,
    onToggleReaction: (TimelineEvent, String) -> Unit,
    onRedact: (TimelineEvent) -> Unit,
    onSendImages: (List<Uri>, String, Boolean) -> Unit,
    onSendVideos: (List<Uri>, String) -> Unit,
    onSendFile: (Uri) -> Unit,
    onSendVoice: (Recording) -> Unit,
    locationArea: LocationArea,
    onSendLocation: (GeoLocation, LocationArea) -> Unit,
    liveUntil: Long?,
    onShareLive: (LiveLength, LocationArea) -> Unit,
    onStopLive: () -> Unit,
    roomActions: RoomActionHandlers,
    info: RoomInfoState,
    identityChanges: List<IdentityChange>,
    onAcknowledgeIdentity: (String) -> Unit,
    onVerifyUser: (String, String?) -> Unit,
    members: Map<String, Member>,
    readReceipts: Boolean,
    history: HistoryState,
    gallery: GalleryState,
    onGalleryShown: () -> Unit,
    sharing: MediaSharingState,
) {
    var leaveTarget by remember { mutableStateOf<Room?>(null) }
    var actionTarget by remember { mutableStateOf<TimelineEvent?>(null) }
    var deleteTarget by remember { mutableStateOf<TimelineEvent?>(null) }
    var infoTarget by remember { mutableStateOf<TimelineEvent?>(null) }
    var pickedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pickedVideos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pickedFile by remember { mutableStateOf<Uri?>(null) }
    var imageRequested by remember { mutableStateOf(false) }
    var attaching by remember { mutableStateOf(false) }
    var pickingLocation by rememberSaveable { mutableStateOf(false) }
    var showGallery by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showGallery) { if (showGallery) onGalleryShown() }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val appContext = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)) { uris ->
        val (videos, images) = uris.partition { appContext.contentResolver.getType(it)?.startsWith("video/") == true }
        if (images.isNotEmpty()) pickedImages = images
        if (videos.isNotEmpty()) pickedVideos = videos
    }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickedFile = uri
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = cameraUri
        if (taken && uri != null) pickedImages = listOf(uri)
    }

    LaunchedEffect(problems) {
        problems.collect { snackbar.showSnackbar(resources.getString(it)) }
    }
    LaunchedEffect(sendingImage) {
        if (imageRequested && !sendingImage) {
            pickedImages = emptyList()
            pickedVideos = emptyList()
            pickedFile = null
            imageRequested = false
        }
    }

    val title = room?.name ?: roomId
    val directPresence = room?.directUserId?.let(presence::get)
    val typingIds = remember(typists) { typists.map { it.userId }.toSet() }
    val auraFor: (String) -> AvatarAura = { userId -> auraOf(presence[userId], userId in typingIds) }
    val rows = remember(items, readReceipts) { items?.let { buildRows(it, readReceipts) }?.asReversed() }
    val isDirect = room?.isDirect == true

    val media = LocalMediaLoader.current
    var pendingSave by remember { mutableStateOf<List<SavedMedia>>(emptyList()) }
    var forwarding by remember { mutableStateOf<List<String>>(emptyList()) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            sharing.onSave(pendingSave)
        } else {
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.media_save_permission)) }
        }
        pendingSave = emptyList()
    }
    val mediaActions = remember(sharing, media) {
        MediaActions(
            onSave = { items ->
                val saved = items.map { it.saved }
                if (MediaSaver.needsPermission(appContext)) {
                    pendingSave = saved
                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    sharing.onSave(saved)
                }
            },
            onShare = { items ->
                scope.launch {
                    val shared = media != null && MediaSaver.share(appContext, media, items.map { it.saved })
                    if (!shared) snackbar.showSnackbar(resources.getString(R.string.media_share_failed))
                }
            },
            onForward = { items -> forwarding = items.mapNotNull { it.eventId } },
        )
    }

    var viewer by remember { mutableStateOf<ViewerImage?>(null) }
    var video by remember { mutableStateOf<VideoTarget?>(null) }
    var shownLocation by remember { mutableStateOf<LocationTarget?>(null) }
    var locationOpen by remember { mutableStateOf(false) }
    val look = LocalChatLook.current

    val infoPanel: @Composable () -> Unit = {
        RoomInfoScreen(
            info = info.details,
            room = room,
            presence = directPresence,
            busy = info.busy,
            trust = info.trust,
            problems = info.problems,
            edits = info.edits,
            roomActions = RoomActionHandlers(
                onMarkRead = roomActions.onMarkRead,
                onFavourite = roomActions.onFavourite,
                onMute = roomActions.onMute,
                onLeave = { leaveTarget = it },
            ),
            onGallery = { showGallery = true },
            onHistory = { history.onShow(true) },
            onBack = { info.onShow(false) },
        )
    }
    val historyPanel: @Composable () -> Unit = {
        RoomHistory(history, onBack = { history.onShow(false) })
    }
    val locationPanel: @Composable () -> Unit = {
        shownLocation?.let { LocationViewer(it, onBack = { locationOpen = false }) }
    }
    val galleryPanel: @Composable () -> Unit = {
        RoomGallery(
            state = gallery,
            actions = mediaActions,
            progress = sharing.progress,
            onCancelSave = sharing.onCancelSave,
            onOpenImage = { viewer = it },
            onOpenVideo = { video = it },
            onBack = { showGallery = false },
        )
    }

    val sidePanel = LocalScreenSize.current.threePane
    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(PockyColors.Bg),
                ) {
                    if (look == ChatLook.AURA) {
                        AuraBackground(Modifier.matchParentSize())
                    }
                    Column(Modifier.fillMaxSize()) {
                        RoomHeader(
                            roomId = roomId,
                            title = title,
                            room = room,
                            presence = directPresence,
                            typists = typists,
                            translucent = look == ChatLook.AURA,
                            onBack = onBack,
                            onGallery = { showGallery = true },
                            onInfo = { info.onShow(true) },
                            actions = RoomActionHandlers(
                                onMarkRead = roomActions.onMarkRead,
                                onFavourite = roomActions.onFavourite,
                                onMute = roomActions.onMute,
                                onLeave = { leaveTarget = it },
                            ),
                        )
                        AnimatedVisibility(visible = liveUntil != null) {
                            LiveBanner(until = liveUntil ?: 0L, onStop = onStopLive)
                        }
                        identityChanges.firstOrNull()?.let { change ->
                            IdentityBanner(
                                change = change,
                                onAcknowledge = { onAcknowledgeIdentity(change.userId) },
                                onVerify = { onVerifyUser(change.userId, change.name) },
                            )
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            when {
                                failed -> Centered(stringResource(R.string.room_failed))
                                rows == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = PockyColors.AccentSoft)
                                }
                                else -> Timeline(
                                    rows = rows,
                                    isDirect = isDirect,
                                    members = if (readReceipts) members else null,
                                    typists = typists,
                                    loadingOlder = loadingOlder,
                                    reachedStart = reachedStart,
                                    onLoadOlder = onLoadOlder,
                                    auraFor = auraFor,
                                    actions = MessageGestures(
                                        onLongPress = { actionTarget = it },
                                        onSwipeReply = onReply,
                                        onReaction = onToggleReaction,
                                        onOpenImage = { viewer = it },
                                        onOpenVideo = { video = it },
                                        onOpenLocation = {
                                            shownLocation = it
                                            locationOpen = true
                                        },
                                    ),
                                )
                            }
                        }
                        ComposingBar(composing, onCancel = onCancelComposing)
                        Composer(
                            mode = composing,
                            onSend = onSend,
                            onSendVoice = onSendVoice,
                            onDraftChanged = onDraftChanged,
                            onPickImage = { attaching = true },
                            onKeyboardImages = { pickedImages = it },
                        )
                    }
                    SnackbarHost(
                        snackbar,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(bottom = 72.dp),
                    )
                }
        }
        if (sidePanel) {
            SidePanel(visible = locationOpen || showGallery || history.open || info.open) {
                when {
                    locationOpen -> locationPanel()
                    showGallery -> galleryPanel()
                    history.open -> historyPanel()
                    else -> infoPanel()
                }
            }
        }
    }

    viewer?.let { image ->
        ImageViewer(image = image, actions = mediaActions, onClose = { viewer = null })
    }

    video?.let { target ->
        VideoPlayerDialog(
            source = target.source,
            caption = target.caption,
            details = target.details,
            actions = mediaActions,
            onClose = { video = null },
        )
    }

    if (forwarding.isNotEmpty()) {
        ForwardDialog(
            rooms = sharing.rooms,
            onPick = { target ->
                sharing.onForward(forwarding, target.id)
                forwarding = emptyList()
            },
            onDismiss = { forwarding = emptyList() },
        )
    }

    leaveTarget?.let { target ->
        LeaveRoomDialog(
            room = target,
            onConfirm = {
                leaveTarget = null
                roomActions.onLeave(target)
            },
            onDismiss = { leaveTarget = null },
        )
    }

    actionTarget?.let { event ->
        MessageActions(
            event = event,
            onReact = { key ->
                actionTarget = null
                onToggleReaction(event, key)
            },
            onReply = {
                actionTarget = null
                onReply(event)
            },
            onEdit = {
                actionTarget = null
                onEdit(event)
            },
            onCopy = {
                actionTarget = null
                plainText(event.content)?.let { text ->
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text))) }
                }
            },
            onForward = {
                actionTarget = null
                event.eventId?.let { forwarding = listOf(it) }
            },
            onInfo = {
                actionTarget = null
                infoTarget = event
            },
            onDelete = {
                actionTarget = null
                deleteTarget = event
            },
            onDismiss = { actionTarget = null },
        )
    }

    infoTarget?.let { target ->
        val event = items
            ?.firstNotNullOfOrNull { (it.kind as? TimelineItemKind.Event)?.event?.takeIf { e -> e.itemKey == target.itemKey } }
            ?: target
        MessageInfoDialog(
            event = event,
            readers = remember(items, event) { readersOf(items.orEmpty(), event) },
            members = members,
            isDirect = isDirect,
            receiptsOn = readReceipts,
            onDismiss = { infoTarget = null },
        )
    }

    deleteTarget?.let { event ->
        DeleteMessageDialog(
            onConfirm = {
                deleteTarget = null
                onRedact(event)
            },
            onDismiss = { deleteTarget = null },
        )
    }

    if (pickedImages.isNotEmpty()) {
        ImageSendDialog(
            uris = pickedImages,
            sending = sendingImage,
            onSend = { caption, full ->
                imageRequested = true
                onSendImages(pickedImages, caption, full)
            },
            onDismiss = { pickedImages = emptyList() },
        )
    }

    if (pickedVideos.isNotEmpty() && pickedImages.isEmpty()) {
        VideoSendDialog(
            uris = pickedVideos,
            sending = sendingImage,
            onSend = { caption ->
                imageRequested = true
                onSendVideos(pickedVideos, caption)
            },
            onDismiss = { pickedVideos = emptyList() },
        )
    }

    pickedFile?.let { uri ->
        FileSendDialog(
            info = remember(uri) { uriInfo(appContext, uri) },
            sending = sendingImage,
            onSend = {
                imageRequested = true
                onSendFile(uri)
            },
            onDismiss = { pickedFile = null },
        )
    }

    if (attaching) {
        AttachSheet(
            onCamera = {
                attaching = false
                val uri = cameraFile(appContext)
                cameraUri = uri
                camera.launch(uri)
            },
            onGallery = {
                attaching = false
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onFile = {
                attaching = false
                documents.launch(arrayOf("*/*"))
            },
            onLocation = {
                attaching = false
                pickingLocation = true
            },
            onDismiss = { attaching = false },
        )
    }

    RaisedLayer(pickingLocation) {
        BackHandler { pickingLocation = false }
        LocationPicker(
            initialArea = locationArea,
            onSend = { location, area ->
                pickingLocation = false
                onSendLocation(location, area)
            },
            onShareLive = { length, area ->
                pickingLocation = false
                onShareLive(length, area)
            },
            onBack = { pickingLocation = false },
        )
    }

    BackHandler(enabled = info.open) { info.onShow(false) }
    BackHandler(enabled = showGallery) { showGallery = false }
    BackHandler(enabled = history.open) { history.onShow(false) }
    BackHandler(enabled = locationOpen) { locationOpen = false }

    PushedLayer(info.open && !sidePanel) { infoPanel() }
    PushedLayer(locationOpen && !sidePanel) { locationPanel() }
    PushedLayer(showGallery && !sidePanel) { galleryPanel() }
    PushedLayer(history.open && !sidePanel) { historyPanel() }



}

class RoomInfoState(
    val open: Boolean,
    val details: RoomInfo?,
    val trust: UserTrust?,
    val busy: Boolean,
    val problems: Flow<Int>,
    val edits: RoomInfoEdits,
    val onShow: (Boolean) -> Unit,
)

class MessageGestures(
    val onLongPress: (TimelineEvent) -> Unit,
    val onSwipeReply: (TimelineEvent) -> Unit,
    val onReaction: (TimelineEvent, String) -> Unit,
    val onOpenImage: (ViewerImage) -> Unit,
    val onOpenVideo: (VideoTarget) -> Unit,
    val onOpenLocation: (LocationTarget) -> Unit,
)

data class VideoTarget(val source: String, val caption: String?, val details: MediaDetails? = null)

@Composable
private fun RoomHeader(
    roomId: String,
    title: String,
    room: Room?,
    presence: PresenceStatus?,
    typists: List<Typist>,
    translucent: Boolean,
    onBack: () -> Unit,
    onGallery: () -> Unit,
    onInfo: () -> Unit,
    actions: RoomActionHandlers,
) {
    val isDirect = room?.isDirect == true

    Column(
        Modifier
            .fillMaxWidth()
            .background(PockyColors.Bg.copy(alpha = if (translucent) 0.55f else 1f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        ) {
            if (!LocalScreenSize.current.twoPane) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onBack),
                ) {
                    Icon(
                        PockyIcons.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = PockyColors.Ink,
                    )
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onInfo),
            ) {
                Box(Modifier.padding(6.dp)) {
                    AuraAvatar(
                        id = roomId,
                        name = title,
                        url = room?.avatarUrl,
                        size = 42.dp,
                        aura = auraOf(presence, typing = typists.isNotEmpty()),
                        rounded = !isDirect,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PockyColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (room?.isEncrypted == true) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                PockyIcons.Lock,
                                contentDescription = stringResource(R.string.room_encrypted),
                                tint = PockyColors.InkMuted.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                    HeaderStatus(typists, isDirect, presence)
                }
            }
            HeaderButton(PockyIcons.Call, stringResource(R.string.room_call), onClick = null)
            HeaderButton(PockyIcons.Video, stringResource(R.string.room_video_call), onClick = null)
            if (room != null) {
                HeaderMenu(room, actions, onGallery, onInfo)
            }
        }
        HorizontalDivider(color = PockyColors.InkMuted.copy(alpha = 0.08f))
    }
}

@Composable
private fun Timeline(
    rows: List<RoomRow>,
    isDirect: Boolean,
    members: Map<String, Member>?,
    typists: List<Typist>,
    loadingOlder: Boolean,
    reachedStart: Boolean,
    onLoadOlder: () -> Unit,
    auraFor: (String) -> AvatarAura,
    actions: MessageGestures,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState, rows.size, reachedStart) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 8
        }
            .distinctUntilChanged()
            .collect { nearTop -> if (nearTop && !reachedStart) onLoadOlder() }
    }

    LaunchedEffect(rows.firstOrNull()?.key) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    val someoneTyping = typists.isNotEmpty()
    LaunchedEffect(someoneTyping) {
        if (someoneTyping && listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    val scope = rememberCoroutineScope()
    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
    var seenNewest by remember { mutableStateOf(rows.firstOrNull()?.key) }
    LaunchedEffect(atBottom, rows.firstOrNull()?.key) {
        if (atBottom) seenNewest = rows.firstOrNull()?.key
    }
    val unseen = remember(rows, seenNewest) {
        rows.takeWhile { it.key != seenNewest }.count { it is RoomRow.Message && !it.event.isOwn }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (typists.isNotEmpty()) {
                item(key = "typing") {
                    TypingBubble(typists, showAvatar = !isDirect, auraFor = auraFor, modifier = Modifier.animateItem())
                }
            }
            items(rows, key = { it.key }, contentType = { it::class }) { row ->
                when (row) {
                    is RoomRow.Day -> CenterLabel(dayLabel(row.timestampMs), emphasized = true)
                    is RoomRow.NewMessages -> NewMessagesDivider()
                    is RoomRow.Start -> CenterLabel(stringResource(R.string.timeline_start))
                    is RoomRow.State -> CenterLabel(stateText(row.event, row.change, row.repeat))
                    is RoomRow.Message -> Column {
                        SwipeToReply(
                            enabled = row.event.canReply,
                            onReply = { actions.onSwipeReply(row.event) },
                        ) {
                            MessageRow(row, showSender = !isDirect, auraFor = auraFor, actions = actions)
                        }
                        if (members != null) {
                            ReadReceipts(
                                receipts = row.event.readBy,
                                senderId = row.event.senderId,
                                own = row.event.isOwn,
                                isDirect = isDirect,
                                members = members,
                            )
                        }
                    }
                }
            }
            if (loadingOlder) {
                item(key = "loading-older") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = PockyColors.AccentSoft)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !atBottom,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 12.dp),
        ) {
            ScrollDownButton(unseen) { scope.launch { listState.animateScrollToItem(0) } }
        }
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, description: String, onClick: (() -> Unit)?) {
    val context = LocalContext.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .alpha(if (onClick != null) 1f else 0.35f)
            .clip(CircleShape)
            .clickable(role = Role.Button) { onClick?.invoke() ?: comingSoon(context) },
    ) {
        Icon(icon, contentDescription = description, tint = PockyColors.Ink, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun HeaderMenu(room: Room, actions: RoomActionHandlers, onGallery: () -> Unit, onInfo: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    fun closing(action: () -> Unit): () -> Unit = {
        open = false
        action()
    }

    Box {
        HeaderButton(PockyIcons.More, stringResource(R.string.menu), onClick = { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = PockyColors.Surface,
        ) {
            MenuItem(PockyIcons.Search, stringResource(R.string.room_menu_search), onClick = null)
            MenuItem(PockyIcons.Images, stringResource(R.string.room_menu_media), onClick = closing(onGallery))
            MenuItem(PockyIcons.Info, stringResource(R.string.room_menu_info), onClick = closing(onInfo))
            HorizontalDivider(color = PockyColors.InkMuted.copy(alpha = 0.1f))
            MenuItem(
                if (room.isMuted) PockyIcons.Bell else PockyIcons.BellOff,
                stringResource(if (room.isMuted) R.string.room_unmute else R.string.room_mute),
                onClick = closing { actions.onMute(room) },
            )
            MenuItem(
                PockyIcons.Pin,
                stringResource(if (room.isFavourite) R.string.room_unpin else R.string.room_pin),
                onClick = closing { actions.onFavourite(room) },
            )
            if (!room.isInvite) {
                MenuItem(
                    PockyIcons.Logout,
                    stringResource(R.string.room_leave),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = closing { actions.onLeave(room) },
                )
            }
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
    tint: Color = PockyColors.Ink,
    onClick: (() -> Unit)?,
) {
    val context = LocalContext.current
    DropdownMenuItem(
        text = { Text(label, color = tint) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) },
        onClick = { onClick?.invoke() ?: comingSoon(context) },
        modifier = Modifier.alpha(if (onClick != null) 1f else 0.4f),
    )
}

@Composable
private fun ScrollDownButton(unseen: Int, onClick: () -> Unit) {
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .size(44.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(PockyColors.Surface)
                .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.15f), CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
        ) {
            Icon(
                PockyIcons.ChevronDown,
                contentDescription = stringResource(R.string.room_scroll_down),
                tint = PockyColors.Ink,
            )
        }
        if (unseen > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .heightIn(min = 20.dp)
                    .widthIn(min = 20.dp)
                    .background(PockyGradient, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    text = if (unseen > 99) "99+" else unseen.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PockyColors.Ink,
                )
            }
        }
    }
}

@Composable
private fun TypingBubble(
    typists: List<Typist>,
    showAvatar: Boolean,
    auraFor: (String) -> AvatarAura,
    modifier: Modifier = Modifier,
) {
    val look = LocalChatLook.current
    val first = typists.first()

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
    ) {
        if (showAvatar) {
            AuraAvatar(
                id = first.userId,
                name = displayName(first.userId, first.name),
                size = 32.dp,
                aura = auraFor(first.userId),
            )
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier
                .bubbleSurface(own = false, shape = bubbleShape(own = false, first = true, last = true), look = look)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            TypingDots()
        }
    }
}

@Composable
private fun MessageRow(
    row: RoomRow.Message,
    showSender: Boolean,
    auraFor: (String) -> AvatarAura,
    actions: MessageGestures,
) {
    val event = row.event
    val own = event.isOwn
    val name = displayName(event.senderId, event.senderName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = if (row.firstInGroup) 8.dp else 2.dp,
            ),
        horizontalArrangement = if (own) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!own && showSender) {
            if (row.lastInGroup) {
                AuraAvatar(
                    id = event.senderId,
                    name = name,
                    url = event.senderAvatar,
                    size = 32.dp,
                    aura = auraFor(event.senderId),
                )
            } else {
                Spacer(Modifier.width(32.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (own) Alignment.End else Alignment.Start) {
            if (!own && showSender && row.firstInGroup) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
                )
            }
            Bubble(
                row = row,
                gestures = actions,
                modifier = Modifier.pointerInput(event.itemKey) {
                    detectTapGestures(onLongPress = { actions.onLongPress(event) })
                },
            )
            if (event.reactions.isNotEmpty()) {
                Reactions(event.reactions) { key -> actions.onReaction(event, key) }
            }
        }
    }
}

@Composable
private fun Bubble(row: RoomRow.Message, gestures: MessageGestures, modifier: Modifier = Modifier) {
    val event = row.event
    val own = event.isOwn
    val look = LocalChatLook.current
    val textColor = PockyColors.Ink
    val metaColor = if (own) PockyColors.Ink.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .widthIn(max = if (LocalScreenSize.current.twoPane) 480.dp else 300.dp)
            .bubbleSurface(own, bubbleShape(own, row.firstInGroup, row.lastInGroup), look)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        event.inReplyTo?.let { ReplyQuote(it, own) }

        val content = event.content
        when (content) {
            is TimelineContent.Image -> {
                MediaBlock(
                    source = content.source,
                    width = content.width,
                    height = content.height,
                    caption = content.caption,
                    video = false,
                    animated = content.original.takeIf { isAnimatedImage(content.mime, content.name) },
                ) {
                    gestures.onOpenImage(
                        ViewerImage(
                            content.source,
                            content.original,
                            content.width,
                            content.height,
                            content.caption,
                            event.mediaDetails(),
                        ),
                    )
                }
                Meta(row, metaColor)
                return@Column
            }
            is TimelineContent.Video -> {
                MediaBlock(
                    source = content.thumbnail,
                    width = content.width,
                    height = content.height,
                    caption = content.caption,
                    video = true,
                    durationMs = content.durationMs?.toLong(),
                ) {
                    gestures.onOpenVideo(VideoTarget(content.source, content.caption, event.mediaDetails()))
                }
                Meta(row, metaColor)
                return@Column
            }
            is TimelineContent.File -> {
                FileMessage(
                    name = content.name,
                    source = content.source,
                    size = content.size?.toLong(),
                    mime = content.mime,
                    own = own,
                )
                Meta(row, metaColor)
                return@Column
            }
            is TimelineContent.Audio -> {
                VoiceMessage(
                    source = content.source,
                    durationMs = content.durationMs?.toLong(),
                    waveform = content.waveform.map { it.toInt() },
                    own = own,
                )
                Meta(row, metaColor)
                return@Column
            }
            is TimelineContent.Location -> {
                val location = parseGeoUri(content.geoUri)
                if (location != null) {
                    val target = LocationTarget(location, content.description, live = false)
                    LocationMessage(target) { gestures.onOpenLocation(target) }
                    Meta(row, metaColor)
                    return@Column
                }
            }
            is TimelineContent.LiveLocation -> {
                val location = content.geoUri?.let(::parseGeoUri)
                if (location != null && content.isLive) {
                    val target = LocationTarget(location, null, live = true)
                    LocationMessage(target) { gestures.onOpenLocation(target) }
                    Meta(row, metaColor)
                    return@Column
                }
            }
            is TimelineContent.Sticker -> {
                MediaBlock(content.source, content.width, content.height, null, video = false, maxWidth = 160.dp)
                Meta(row, metaColor)
                return@Column
            }
            else -> Unit
        }
        val label = contentLabel(content)
        val special = content is TimelineContent.Redacted || content is TimelineContent.UnableToDecrypt

        when {
            label != null -> Text(
                text = label,
                color = if (special) metaColor else textColor,
                fontStyle = if (special) FontStyle.Italic else FontStyle.Normal,
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> {
                val (body, html, prefix) = when (content) {
                    is TimelineContent.Text -> Triple(content.body, content.html, "")
                    is TimelineContent.Notice -> Triple(content.body, content.html, "")
                    is TimelineContent.Emote -> Triple(
                        content.body,
                        content.html,
                        "* ${displayName(event.senderId, event.senderName)} ",
                    )
                    else -> Triple("", null, "")
                }
                Text(
                    text = buildAnnotatedString {
                        append(prefix)
                        append(messageText(body, html, event.inReplyTo != null, PockyColors.Ink))
                    },
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Meta(row, metaColor)
    }
}

@Composable
private fun ColumnScope.Meta(row: RoomRow.Message, metaColor: Color) {
    val event = row.event
    Row(
        modifier = Modifier
            .align(Alignment.End)
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (event.isEdited) {
            Text(
                text = stringResource(R.string.msg_edited),
                style = MaterialTheme.typography.labelSmall,
                color = metaColor,
            )
            Spacer(Modifier.width(6.dp))
        }
        event.shield?.let { shield ->
            ShieldMark(shield, metaColor)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = timeLabel(event.timestampMs.toLong()),
            style = MaterialTheme.typography.labelSmall,
            color = metaColor,
        )
        if (event.isOwn) {
            Spacer(Modifier.width(4.dp))
            SendStateMark(event.sendState, row.read, metaColor)
        }
    }
}

@Composable
private fun MediaBlock(
    source: String?,
    width: UInt?,
    height: UInt?,
    caption: String?,
    video: Boolean,
    maxWidth: Dp = 240.dp,
    durationMs: Long? = null,
    animated: String? = null,
    onOpen: (() -> Unit)? = null,
) {
    val ratio = if (width != null && height != null && width > 0u && height > 0u) {
        width.toFloat() / height.toFloat()
    } else {
        4f / 3f
    }
    val boxHeight = (maxWidth / ratio).coerceIn(120.dp, 320.dp)
    val image = if (animated == null) rememberMatrixImage(source, maxWidth, boxHeight) else null

    Box(
        modifier = Modifier
            .size(maxWidth, boxHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(PockyColors.Bg.copy(alpha = 0.35f))
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (animated != null) {
            AnimatedMatrixImage(source = animated, contentDescription = caption, modifier = Modifier.fillMaxSize())
        } else if (image != null) {
            MatrixImage(
                image = image,
                contentDescription = caption,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = if (video) "🎬" else "📷",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        if (video) VideoOverlay(durationMs)
    }

    if (!caption.isNullOrBlank()) {
        Text(
            text = caption,
            color = PockyColors.Ink,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .widthIn(max = maxWidth)
                .padding(top = 6.dp),
        )
    }
}

@Composable
private fun SendStateMark(state: TimelineSendState, read: Boolean, color: Color) {
    val (icon, tint) = when (state) {
        TimelineSendState.SENDING -> PockyIcons.Clock to color
        TimelineSendState.FAILED -> PockyIcons.AlertCircle to MaterialTheme.colorScheme.error
        TimelineSendState.SENT -> if (read) PockyIcons.CheckDouble to PockyColors.Success else PockyIcons.Check to color
    }
    val label = when {
        state == TimelineSendState.SENDING -> R.string.delivery_sending
        state == TimelineSendState.FAILED -> R.string.delivery_failed
        read -> R.string.delivery_read
        else -> R.string.delivery_sent
    }
    Icon(icon, contentDescription = stringResource(label), tint = tint, modifier = Modifier.size(15.dp))
}

@Composable
private fun ReplyQuote(reply: TimelineReply, own: Boolean) {
    val accent = if (own) PockyColors.Ink else MaterialTheme.colorScheme.primary
    val sender = reply.senderId?.let { displayName(it, reply.senderName) }
    val preview = reply.content?.let {
        previewText(
            LastMessage(
                senderId = reply.senderId.orEmpty(),
                senderName = reply.senderName,
                isOwn = false,
                content = it,
                delivery = null,
            ),
            isDirect = true,
        )
    }

    Row(
        modifier = Modifier
            .padding(bottom = 6.dp)
            .background(PockyColors.Bg.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(end = 8.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )
        Column(Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
            if (sender != null) {
                Text(
                    text = sender,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                )
            }
            Text(
                text = preview ?: "…",
                style = MaterialTheme.typography.bodySmall,
                color = PockyColors.Ink.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Reactions(reactions: List<TimelineReaction>, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.forEach { reaction ->
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .then(
                        if (reaction.reactedByMe) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { onClick(reaction.key) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = reaction.key, style = MaterialTheme.typography.bodyMedium)
                if (reaction.count > 1u) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = reaction.count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterLabel(text: String, emphasized: Boolean = false) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = if (emphasized) 12.dp else 4.dp),
    ) {
        Text(
            text = text,
            style = if (emphasized) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasized) PockyColors.Ink.copy(alpha = 0.85f) else PockyColors.InkMuted,
            textAlign = TextAlign.Center,
            modifier = if (emphasized) {
                Modifier
                    .background(PockyColors.Surface.copy(alpha = 0.85f), CircleShape)
                    .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.1f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            } else {
                Modifier
            },
        )
    }
}

@Composable
private fun NewMessagesDivider() {
    val fade = Brush.horizontalGradient(listOf(Color.Transparent, PockyColors.Accent))
    val fadeBack = Brush.horizontalGradient(listOf(PockyColors.Accent, Color.Transparent))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(fade),
        )
        Text(
            text = stringResource(R.string.new_messages),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = PockyColors.Ink,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(PockyGradient, CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(fadeBack),
        )
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Composer(
    mode: Composing,
    onSend: (String) -> Unit,
    onSendVoice: (Recording) -> Unit,
    onDraftChanged: (String) -> Unit,
    onPickImage: () -> Unit,
    onKeyboardImages: (List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context, scope) }
    val recording by recorder.recording.collectAsStateWithLifecycle()
    val levels by recorder.levels.collectAsStateWithLifecycle()
    val elapsed by recorder.elapsedMs.collectAsStateWithLifecycle()
    var recorded by remember { mutableStateOf<Recording?>(null) }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recorder.start()
    }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    val startRecording: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recorder.start()
        } else {
            microphone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val field = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    var wasEditing by remember { mutableStateOf(false) }
    val canSend = field.text.isNotBlank()

    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.drop(1).collect { onDraftChanged(it) }
    }
    LaunchedEffect(mode) {
        when (mode) {
            is Composing.Edit -> {
                field.setTextAndPlaceCursorAtEnd(plainText(mode.event.content).orEmpty())
                wasEditing = true
            }
            else -> if (wasEditing) {
                field.clearText()
                wasEditing = false
            }
        }
        if (mode != Composing.Message) focus.requestFocus()
    }

    val send = {
        if (canSend) {
            onSend(field.text.toString())
            field.clearText()
            wasEditing = false
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
    ) {
        val review = recorded
        if (review != null) {
            RecordingReview(
                recording = review,
                onDiscard = { recorded = null },
                onSend = {
                    recorded = null
                    onSendVoice(review)
                },
            )
            return@Box
        }
        if (recording) {
            RecordingBar(
                levels = levels,
                elapsedMs = elapsed,
                onCancel = recorder::cancel,
                onStop = { recorded = recorder.stop() },
            )
            return@Box
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(PockyColors.Surface)
                    .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.12f), RoundedCornerShape(25.dp)),
            ) {
                if (mode !is Composing.Edit) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onPickImage),
                    ) {
                        Icon(
                            PockyIcons.Photo,
                            contentDescription = stringResource(R.string.composer_photo),
                            tint = PockyColors.InkMuted,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.width(18.dp))
                }
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .padding(end = 16.dp, top = 13.dp, bottom = 13.dp),
                ) {
                    if (field.text.isEmpty()) {
                        Text(stringResource(R.string.composer_hint), color = PockyColors.InkMuted, fontSize = 16.sp)
                    }
                    BasicTextField(
                        state = field,
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 6),
                        textStyle = TextStyle(color = PockyColors.Ink, fontSize = 16.sp),
                        cursorBrush = SolidColor(PockyColors.AccentSoft),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .contentReceiver { content ->
                                if (!content.hasMediaType(MediaType.Image)) return@contentReceiver content
                                val received = mutableListOf<Uri>()
                                val rest = content.consume { item ->
                                    val copy = item.uri?.let { KeyboardImages.copy(context, it) }
                                    if (copy != null) received += copy
                                    copy != null
                                }
                                if (received.isNotEmpty()) onKeyboardImages(received)
                                rest
                            },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (canSend || mode is Composing.Edit) {
                SendButton(enabled = canSend, editing = mode is Composing.Edit, onClick = send)
            } else {
                MicButton(onClick = startRecording)
            }
        }
    }
}

@Composable
private fun MicButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(50.dp)
            .shadow(12.dp, CircleShape, spotColor = PockyColors.Accent, ambientColor = PockyColors.Accent)
            .clip(CircleShape)
            .background(PockyGradient)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(
            imageVector = PockyIcons.Mic,
            contentDescription = stringResource(R.string.voice_record),
            tint = PockyColors.Ink,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SendButton(enabled: Boolean, editing: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (enabled) 1f else 0.88f, label = "send-scale")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(50.dp)
            .then(
                if (enabled) {
                    Modifier.shadow(12.dp, CircleShape, spotColor = PockyColors.Accent, ambientColor = PockyColors.Accent)
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .background(if (enabled) PockyGradient else SolidColor(PockyColors.Surface))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Icon(
            imageVector = if (editing) PockyIcons.Check else PockyIcons.Send,
            contentDescription = stringResource(R.string.send),
            tint = if (enabled) PockyColors.Ink else PockyColors.InkMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}

private const val MAX_IMAGES = 10

private fun cameraFile(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}
