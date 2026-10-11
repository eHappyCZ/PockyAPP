package com.pokecio.app.ui.rooms

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokecio.app.R
import com.pokecio.app.core.MessageDelivery
import com.pokecio.app.core.PresenceStatus
import com.pokecio.app.core.Room
import com.pokecio.app.core.StatusMessage
import com.pokecio.app.core.Typist
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.room.displayName
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import java.text.Normalizer
import kotlinx.coroutines.flow.Flow

enum class RoomFilter { ALL, PEOPLE, GROUPS, UNREAD }

class RoomBanners(
    val offline: Boolean,
    val needsVerification: Boolean,
    val suggestBackup: Boolean,
    val notificationsOff: Boolean,
    val onVerify: () -> Unit,
    val onBackup: () -> Unit,
    val onEnableNotifications: () -> Unit,
)

@Composable
fun RoomListScreen(
    rooms: List<Room>?,
    banners: RoomBanners,
    presence: Map<String, PresenceStatus>,
    typing: Map<String, List<Typist>>,
    ownName: String?,
    ownAvatar: String?,
    ownUserId: String,
    sharingPresence: Boolean,
    statuses: Map<String, String>,
    ownStatus: StatusMessage?,
    onSaveStatus: (String, Long?) -> Unit,
    onClearStatus: () -> Unit,
    bottomPadding: Dp,
    problems: Flow<Int>,
    actions: RoomActionHandlers,
    invites: InviteHandlers,
    onRoomClick: (String) -> Unit,
    onProfile: () -> Unit,
) {
    var actionRoom by remember { mutableStateOf<Room?>(null) }
    var leaveRoom by remember { mutableStateOf<Room?>(null) }
    val context = LocalContext.current

    LaunchedEffect(problems) {
        problems.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(RoomFilter.ALL) }

    val pending = remember(rooms) { rooms.orEmpty().filter { it.isInvite } }
    val unreadCount = rooms.orEmpty().count { !it.isInvite && it.isUnread() }
    val visible = remember(rooms, filter, query) {
        rooms.orEmpty()
            .filterNot { it.isInvite }
            .filter {
                when (filter) {
                    RoomFilter.ALL -> true
                    RoomFilter.UNREAD -> it.isUnread()
                    RoomFilter.PEOPLE -> it.isDirect
                    RoomFilter.GROUPS -> !it.isDirect
                }
            }
            .filter { query.isBlank() || fold(it.name).contains(fold(query)) }
    }
    var editingStatus by rememberSaveable { mutableStateOf(false) }
    var statusCard by remember { mutableStateOf<StatusPerson?>(null) }
    val statusPeople = remember(rooms, presence, typing, statuses) {
        rooms.orEmpty()
            .filter { it.isDirect && !it.isInvite && it.directUserId != null }
            .groupBy { it.directUserId }
            .mapNotNull { (_, chats) ->
                val room = chats.firstOrNull { it.lastMessage != null } ?: chats.first()
                val user = room.directUserId ?: return@mapNotNull null
                val isTyping = chats.any { typing[it.id].orEmpty().isNotEmpty() }
                val online = presence[user] == PresenceStatus.ONLINE
                val status = statuses[user]
                if (status == null && !online && !isTyping) return@mapNotNull null
                StatusPerson(
                    roomId = room.id,
                    userId = user,
                    name = room.name,
                    avatarUrl = room.avatarUrl,
                    status = status,
                    aura = if (isTyping) AvatarAura.TYPING else AvatarAura.NONE,
                    online = online,
                )
            }
            .sortedWith(compareByDescending<StatusPerson> { it.status != null }.thenByDescending { it.online })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PockyColors.Bg)
            .statusBarsPadding(),
    ) {
        Header(
            ownName = ownName ?: ownUserId,
            status = when {
                banners.offline -> ListStatus.Offline
                rooms == null -> ListStatus.Loading
                else -> ListStatus.Unread(rooms.sumOf { it.unreadMessages.toLong() })
            },
            ownUserId = ownUserId,
            ownAvatar = ownAvatar,
            online = sharingPresence && !banners.offline,
            searching = searching,
            onSearch = {
                searching = !searching
                if (!searching) query = ""
            },
            onProfile = onProfile,
        )

        AnimatedVisibility(visible = searching) {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                onClose = {
                    searching = false
                    query = ""
                },
            )
        }

        Banners(banners)

        AnimatedVisibility(visible = !searching) {
            Column {
                StatusStrip(
                    ownId = ownUserId,
                    ownName = ownName ?: ownUserId,
                    ownAvatar = ownAvatar,
                    ownStatus = ownStatus,
                    ownAura = if (sharingPresence && !banners.offline) AvatarAura.ONLINE else AvatarAura.OWN,
                    people = statusPeople,
                    onOwn = { editingStatus = true },
                    onPerson = { statusCard = it },
                )
                GlowDivider()
            }
        }
        FilterTabs(filter, unreadCount) { filter = it }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding + 88.dp),
        ) {
            if (pending.isNotEmpty() && !searching) {
                item(key = "invites-label") {
                    Text(
                        text = stringResource(R.string.invites_title, pending.size),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = PockyColors.Gold,
                        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp),
                    )
                }
                items(pending, key = { "invite-" + it.id }) { room ->
                    InviteCard(room, invites, Modifier.animateItem())
                }
            }
            when {
                rooms == null -> item(key = "placeholder") { PlaceholderList() }
                visible.isEmpty() -> item(key = "empty-$filter") {
                    EmptyList(
                        filter = filter,
                        searching = query.isNotBlank(),
                        modifier = Modifier.animateItem(),
                    )
                }
                else -> items(visible, key = { it.id }) { room ->
                    val unread = room.isUnread()
                    SwipeRoom(
                        start = if (unread) {
                            SwipeAction(PockyIcons.CheckDouble, stringResource(R.string.room_swipe_read), PockyColors.Accent) {
                                actions.onMarkRead(room)
                            }
                        } else {
                            null
                        },
                        end = SwipeAction(
                            icon = if (room.isMuted) PockyIcons.Bell else PockyIcons.BellOff,
                            label = stringResource(if (room.isMuted) R.string.room_swipe_unmute else R.string.room_mute),
                            color = PockyColors.Gold,
                        ) { actions.onMute(room) },
                        modifier = Modifier.animateItem(),
                    ) {
                        RoomRow(
                            room = room,
                            presence = room.directUserId?.let(presence::get),
                            typists = typing[room.id].orEmpty(),
                            onClick = { onRoomClick(room.id) },
                            onLongClick = { actionRoom = room },
                        )
                    }
                }
            }
        }
    }

    if (editingStatus) {
        StatusEditorSheet(
            current = ownStatus,
            onSave = { text, until ->
                editingStatus = false
                onSaveStatus(text, until)
            },
            onClear = {
                editingStatus = false
                onClearStatus()
            },
            onDismiss = { editingStatus = false },
        )
    }

    statusCard?.let { person ->
        StatusCardSheet(
            person = person,
            onMessage = {
                statusCard = null
                onRoomClick(person.roomId)
            },
            onDismiss = { statusCard = null },
        )
    }

    actionRoom?.let { room ->
        RoomActionsSheet(
            room = room,
            handlers = RoomActionHandlers(
                onMarkRead = actions.onMarkRead,
                onFavourite = actions.onFavourite,
                onMute = actions.onMute,
                onLeave = { leaveRoom = it },
            ),
            onDismiss = { actionRoom = null },
        )
    }

    leaveRoom?.let { room ->
        LeaveRoomDialog(
            room = room,
            onConfirm = {
                leaveRoom = null
                actions.onLeave(room)
            },
            onDismiss = { leaveRoom = null },
        )
    }
}

class InviteHandlers(
    val busy: Boolean,
    val onAccept: (String) -> Unit,
    val onDecline: (String) -> Unit,
)

@Composable
private fun InviteCard(room: Room, handlers: InviteHandlers, modifier: Modifier = Modifier) {
    val inviter = room.lastMessage?.let { displayName(it.senderId, it.senderName) }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PockyColors.Surface)
            .border(1.dp, PockyColors.Gold.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuraAvatar(id = room.id, name = room.name, url = room.avatarUrl, size = 46.dp, rounded = !room.isDirect)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = room.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PockyColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (inviter != null) {
                        stringResource(
                            if (room.isDirect) R.string.invite_direct_from else R.string.invite_group_from,
                            inviter,
                        )
                    } else {
                        stringResource(R.string.preview_invite)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = PockyColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(CircleShape)
                    .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.3f), CircleShape)
                    .clickable(enabled = !handlers.busy, role = Role.Button) { handlers.onDecline(room.id) },
            ) {
                Text(stringResource(R.string.invite_decline), fontWeight = FontWeight.SemiBold, color = PockyColors.InkMuted)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(CircleShape)
                    .background(PockyGradient)
                    .clickable(enabled = !handlers.busy, role = Role.Button) { handlers.onAccept(room.id) },
            ) {
                Text(stringResource(R.string.invite_accept), fontWeight = FontWeight.Bold, color = PockyColors.Ink)
            }
        }
    }
}

@Composable
private fun Header(
    ownName: String,
    status: ListStatus,
    ownUserId: String,
    ownAvatar: String?,
    online: Boolean,
    searching: Boolean,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.rooms_greeting, ownName.substringBefore(' ')),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = PockyColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusLine(status)
        }
        Spacer(Modifier.width(12.dp))
        RoundButton(
            icon = PockyIcons.Search,
            description = stringResource(R.string.rooms_search),
            active = searching,
            onClick = onSearch,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onProfile),
        ) {
            AuraAvatar(
                id = ownUserId,
                name = ownName,
                url = ownAvatar,
                size = 36.dp,
                aura = if (online) AvatarAura.ONLINE else AvatarAura.OWN,
            )
        }
    }
}

sealed interface ListStatus {
    data object Loading : ListStatus
    data object Offline : ListStatus
    data class Unread(val messages: Long) : ListStatus
}

@Composable
private fun StatusLine(status: ListStatus) {
    val (text, color) = when (status) {
        ListStatus.Loading -> stringResource(R.string.rooms_status_loading) to PockyColors.InkMuted
        ListStatus.Offline -> stringResource(R.string.rooms_status_offline) to PockyColors.Gold
        is ListStatus.Unread -> if (status.messages == 0L) {
            stringResource(R.string.rooms_status_all_read) to PockyColors.Success
        } else {
            val count = status.messages.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            pluralStringResource(R.plurals.rooms_status_unread, count, count) to PockyColors.AccentSoft
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        if (status == ListStatus.Offline) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(color, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (active) PockyColors.Accent.copy(alpha = 0.25f) else PockyColors.Surface,
        label = "round-button",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(icon, contentDescription = description, tint = PockyColors.Ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(PockyColors.Surface, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 4.dp),
    ) {
        Icon(PockyIcons.Search, contentDescription = null, tint = PockyColors.InkMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .padding(vertical = 14.dp),
        ) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.rooms_search_hint), color = PockyColors.InkMuted)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = PockyColors.Ink, fontSize = 16.sp),
                cursorBrush = SolidColor(PockyColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClose),
        ) {
            Icon(PockyIcons.Close, contentDescription = stringResource(R.string.cancel), tint = PockyColors.InkMuted)
        }
    }
}

@Composable
private fun Banners(banners: RoomBanners) {
    AnimatedVisibility(visible = banners.needsVerification) {
        NoticeBanner(
            title = stringResource(R.string.verify_banner_title),
            text = stringResource(R.string.verify_banner_text),
            onClick = banners.onVerify,
        )
    }
    AnimatedVisibility(visible = banners.suggestBackup && !banners.needsVerification) {
        NoticeBanner(
            title = stringResource(R.string.backup_banner_title),
            text = stringResource(R.string.backup_banner_text),
            onClick = banners.onBackup,
        )
    }
    AnimatedVisibility(visible = banners.notificationsOff) {
        NoticeBanner(
            title = stringResource(R.string.notifications_banner_title),
            text = stringResource(R.string.notifications_banner_text),
            icon = PockyIcons.Bell,
            onClick = banners.onEnableNotifications,
        )
    }
}

@Composable
private fun FilterTabs(selected: RoomFilter, unreadCount: Int, onSelect: (RoomFilter) -> Unit) {
    val filters = RoomFilter.entries

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(44.dp)
            .clip(CircleShape)
            .background(PockyColors.Surface)
            .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.1f), CircleShape)
            .padding(4.dp),
    ) {
        val segment = maxWidth / filters.size
        val offset by animateDpAsState(
            targetValue = segment * selected.ordinal,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
            label = "filter-indicator",
        )

        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .shadow(8.dp, CircleShape, spotColor = PockyColors.Accent, ambientColor = PockyColors.Accent)
                .background(PockyGradient, CircleShape),
        )

        Row(Modifier.fillMaxSize()) {
            filters.forEach { filter ->
                val active = filter == selected
                val color by animateColorAsState(
                    if (active) PockyColors.Ink else PockyColors.InkMuted,
                    label = "filter-text",
                )
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(role = Role.Tab) { onSelect(filter) },
                ) {
                    Text(
                        text = when (filter) {
                            RoomFilter.ALL -> stringResource(R.string.rooms_filter_all)
                            RoomFilter.UNREAD -> stringResource(R.string.rooms_filter_unread)
                            RoomFilter.PEOPLE -> stringResource(R.string.rooms_filter_people)
                            RoomFilter.GROUPS -> stringResource(R.string.rooms_filter_groups)
                        },
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (filter == RoomFilter.UNREAD && unreadCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .heightIn(min = 16.dp)
                                .widthIn(min = 16.dp)
                                .background(if (active) PockyColors.Ink else PockyColors.Accent, CircleShape)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) PockyColors.Accent else PockyColors.Ink,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomRow(
    room: Room,
    presence: PresenceStatus?,
    typists: List<Typist>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unread = room.isUnread()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuraAvatar(
            id = room.id,
            name = room.name,
            url = room.avatarUrl,
            size = 52.dp,
            aura = auraOf(presence, typing = typists.isNotEmpty()),
            rounded = !room.isDirect,
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PockyColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (room.isEncrypted) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            imageVector = PockyIcons.Lock,
                            contentDescription = stringResource(R.string.room_encrypted),
                            tint = PockyColors.InkMuted.copy(alpha = 0.7f),
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    if (room.isMuted) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            imageVector = PockyIcons.BellOff,
                            contentDescription = stringResource(R.string.room_muted),
                            tint = PockyColors.InkMuted.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                if (room.isFavourite) {
                    Icon(
                        imageVector = PockyIcons.Pin,
                        contentDescription = stringResource(R.string.room_pinned),
                        tint = PockyColors.AccentSoft,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                room.timestampMs?.let { ts ->
                    Text(
                        text = formatTimestamp(ts.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                        color = if (unread) PockyColors.AccentSoft else PockyColors.InkMuted,
                        textAlign = TextAlign.End,
                    )
                }
            }

            Spacer(Modifier.height(3.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val delivery = room.lastMessage?.delivery
                if (typists.isEmpty() && room.lastMessage?.isOwn == true && delivery != null) {
                    DeliveryMark(delivery)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = if (typists.isNotEmpty()) {
                        typingText(typists, room.isDirect)
                    } else {
                        room.lastMessage?.let { previewText(it, room.isDirect) }
                            ?: if (room.isInvite) stringResource(R.string.preview_invite) else ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (typists.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        typists.isNotEmpty() -> PockyColors.AccentSoft
                        unread -> PockyColors.Ink
                        else -> PockyColors.InkMuted
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) {
                    Spacer(Modifier.width(8.dp))
                    UnreadBadge(
                        count = room.unreadMessages.toLong(),
                        mention = room.unreadMentions > 0u,
                        muted = room.isMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Long, mention: Boolean, muted: Boolean) {
    val label = when {
        mention -> "@"
        count > 99 -> "99+"
        else -> count.toString()
    }

    Box(
        modifier = Modifier
            .heightIn(min = 22.dp)
            .widthIn(min = 22.dp)
            .background(
                when {
                    mention -> PockyColors.Gold
                    muted -> PockyColors.InkMuted.copy(alpha = 0.35f)
                    else -> PockyColors.Accent
                },
                CircleShape,
            )
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = PockyColors.Ink,
        )
    }
}

@Composable
private fun DeliveryMark(delivery: MessageDelivery) {
    val (icon, tint) = when (delivery) {
        MessageDelivery.SENDING -> PockyIcons.Clock to PockyColors.InkMuted
        MessageDelivery.FAILED -> PockyIcons.AlertCircle to MaterialTheme.colorScheme.error
        MessageDelivery.SENT -> PockyIcons.Check to PockyColors.InkMuted
        MessageDelivery.READ -> PockyIcons.CheckDouble to PockyColors.Success
    }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(
            when (delivery) {
                MessageDelivery.SENDING -> R.string.delivery_sending
                MessageDelivery.FAILED -> R.string.delivery_failed
                MessageDelivery.SENT -> R.string.delivery_sent
                MessageDelivery.READ -> R.string.delivery_read
            },
        ),
        tint = tint,
        modifier = Modifier.size(16.dp),
    )
}

@Composable
private fun NoticeBanner(
    title: String,
    text: String,
    icon: ImageVector = PockyIcons.Lock,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PockyColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = PockyColors.AccentSoft, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = PockyColors.Ink)
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = PockyColors.InkMuted)
        }
        Icon(PockyIcons.ChevronRight, contentDescription = null, tint = PockyColors.InkMuted)
    }
}

@Composable
private fun PlaceholderList() {
    val shift by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmer-shift",
    )
    val brush = Brush.linearGradient(
        colors = listOf(PockyColors.Surface, PockyColors.InkMuted.copy(alpha = 0.16f), PockyColors.Surface),
        start = Offset(shift, 0f),
        end = Offset(shift + 400f, 0f),
    )

    Column {
        repeat(7) { index ->
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(brush, if (index % 3 == 1) RoundedCornerShape(16.dp) else CircleShape),
                )
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(width = (110 + index * 13 % 60).dp, height = 14.dp)
                            .background(brush, RoundedCornerShape(7.dp)),
                    )
                    Box(
                        Modifier
                            .size(width = (180 + index * 29 % 70).dp, height = 12.dp)
                            .background(brush, RoundedCornerShape(6.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyList(filter: RoomFilter, searching: Boolean, modifier: Modifier = Modifier) {
    val (emoji, title, text) = when {
        searching -> Triple("🔍", R.string.rooms_empty_search, R.string.rooms_empty_search_text)
        filter == RoomFilter.UNREAD -> Triple("🎉", R.string.rooms_empty_unread, R.string.rooms_empty_unread_text)
        filter == RoomFilter.PEOPLE -> Triple("👋", R.string.rooms_empty_people, R.string.rooms_empty_people_text)
        filter == RoomFilter.GROUPS -> Triple("👥", R.string.rooms_empty_groups, R.string.rooms_empty_groups_text)
        else -> Triple("💬", R.string.rooms_empty, R.string.rooms_empty_text)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
    ) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PockyColors.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = PockyColors.InkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Room.isUnread() = unreadMessages > 0u || unreadMentions > 0u

private fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
