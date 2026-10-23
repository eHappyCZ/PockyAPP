package com.pokecio.app.ui.room

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokecio.app.R
import com.pokecio.app.core.TimelineContent
import com.pokecio.app.core.TimelineEvent
import com.pokecio.app.core.TimelineItem
import com.pokecio.app.core.TimelineItemKind
import com.pokecio.app.media.DownloadProgress
import com.pokecio.app.media.MatrixImage
import com.pokecio.app.media.rememberMatrixImage
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.people.PeopleHeader
import com.pokecio.app.ui.rooms.AppLocale
import com.pokecio.app.ui.rooms.RoomAvatar
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class GalleryState(
    val items: List<TimelineItem>?,
    val loading: Boolean,
    val complete: Boolean,
)

private enum class GalleryTab { PHOTOS, VIDEOS, VOICE, FILES }

private class Album(
    val photos: List<TimelineEvent>,
    val videos: List<TimelineEvent>,
    val voice: List<TimelineEvent>,
    val files: List<TimelineEvent>,
)

@Composable
fun RoomGallery(
    state: GalleryState,
    actions: MediaActions,
    progress: DownloadProgress?,
    onCancelSave: () -> Unit,
    onOpenImage: (ViewerImage) -> Unit,
    onOpenVideo: (VideoTarget) -> Unit,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(GalleryTab.PHOTOS) }
    val album = remember(state.items) { album(state.items.orEmpty()) }
    val shown = when (tab) {
        GalleryTab.PHOTOS -> album.photos
        GalleryTab.VIDEOS -> album.videos
        GalleryTab.VOICE -> album.voice
        GalleryTab.FILES -> album.files
    }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selecting = selected.isNotEmpty()
    val chosen = { shown.filter { it.itemKey in selected }.mapNotNull { it.mediaDetails() } }
    BackHandler(enabled = selecting) { selected = emptySet() }

    Column(
        Modifier
            .fillMaxSize()
            .background(PockyColors.Bg),
    ) {
        if (selecting) {
            SelectionHeader(
                count = selected.size,
                onClose = { selected = emptySet() },
                onSave = {
                    actions.onSave(chosen())
                    selected = emptySet()
                },
                onShare = { actions.onShare(chosen()) },
                onForward = {
                    actions.onForward(chosen())
                    selected = emptySet()
                },
            )
        } else {
            PeopleHeader(
                title = stringResource(R.string.room_menu_media),
                subtitle = if (!state.complete) stringResource(R.string.gallery_loading_older) else null,
                busy = state.loading,
                onBack = onBack,
            )
        }
        GalleryTabs(
            selected = tab,
            counts = mapOf(
                GalleryTab.PHOTOS to album.photos.size,
                GalleryTab.VIDEOS to album.videos.size,
                GalleryTab.VOICE to album.voice.size,
                GalleryTab.FILES to album.files.size,
            ),
            onSelect = {
                tab = it
                selected = emptySet()
            },
        )
        val grid = tab == GalleryTab.PHOTOS || tab == GalleryTab.VIDEOS
        GalleryToolbar(
            progress = progress,
            selecting = selecting,
            canSelect = grid && shown.isNotEmpty(),
            canSaveAll = shown.isNotEmpty() && state.complete,
            onCancelSave = onCancelSave,
            onSelect = { selected = if (selecting) shown.map { it.itemKey }.toSet() else setOf(shown.first().itemKey) },
            onSaveAll = { actions.onSave(shown.mapNotNull { it.mediaDetails() }) },
        )

        when {
            state.items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PockyColors.AccentSoft)
            }
            shown.isEmpty() && state.complete -> Text(
                text = stringResource(emptyText(tab)),
                style = MaterialTheme.typography.bodyMedium,
                color = PockyColors.InkMuted,
                modifier = Modifier.padding(24.dp),
            )
            grid -> MediaGrid(
                events = shown,
                selected = selected,
                onToggle = { key -> selected = if (key in selected) selected - key else selected + key },
                onOpen = { event ->
                    when (val content = event.content) {
                        is TimelineContent.Image -> onOpenImage(
                            ViewerImage(
                                content.source,
                                content.original,
                                content.width,
                                content.height,
                                content.caption,
                                event.mediaDetails(),
                            ),
                        )
                        is TimelineContent.Video -> onOpenVideo(VideoTarget(content.source, content.caption, event.mediaDetails()))
                        else -> {}
                    }
                },
            )
            else -> MediaList(events = shown)
        }
    }
}

@Composable
private fun GalleryTabs(selected: GalleryTab, counts: Map<GalleryTab, Int>, onSelect: (GalleryTab) -> Unit) {
    val tabs = GalleryTab.entries

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(54.dp)
            .clip(CircleShape)
            .background(PockyColors.Surface)
            .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.1f), CircleShape)
            .padding(4.dp),
    ) {
        val segment = maxWidth / tabs.size
        val offset by animateDpAsState(
            targetValue = segment * selected.ordinal,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
            label = "gallery-indicator",
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
            tabs.forEach { tab ->
                val active = tab == selected
                val color by animateColorAsState(
                    if (active) PockyColors.OnBrand else PockyColors.InkMuted,
                    label = "gallery-text",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(role = Role.Tab) { onSelect(tab) },
                ) {
                    Text(
                        text = stringResource(tabLabel(tab)),
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = counts[tab]?.toString().orEmpty(),
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        color = color.copy(alpha = 0.75f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaGrid(
    events: List<TimelineEvent>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (TimelineEvent) -> Unit,
) {
    val months = remember(events) { events.groupBy { monthOf(it.timestampMs.toLong()) } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        contentPadding = PaddingValues(start = 3.dp, end = 3.dp, bottom = 16.dp),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        months.forEach { (month, inMonth) ->
            item(key = "month-$month", span = { GridItemSpan(maxLineSpan) }) {
                MonthLabel(month)
            }
            items(inMonth, key = { it.itemKey }) { event ->
                Tile(
                    event = event,
                    selected = event.itemKey in selected,
                    onClick = { if (selected.isEmpty()) onOpen(event) else onToggle(event.itemKey) },
                    onLongClick = { onToggle(event.itemKey) },
                )
            }
        }
    }
}

@Composable
private fun MediaList(events: List<TimelineEvent>) {
    val months = remember(events) { events.groupBy { monthOf(it.timestampMs.toLong()) } }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        months.forEach { (month, inMonth) ->
            item(key = "month-$month") { MonthLabel(month) }
            items(inMonth, key = { it.itemKey }) { event -> ListRow(event) }
        }
    }
}

@Composable
private fun MonthLabel(month: String) {
    Text(
        text = month,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = PockyColors.InkMuted,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun Tile(event: TimelineEvent, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val content = event.content
    val source = when (content) {
        is TimelineContent.Image -> content.source
        is TimelineContent.Video -> content.thumbnail
        else -> null
    }
    val bitmap = rememberMatrixImage(source, THUMB, THUMB)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(PockyColors.Surface)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick),
    ) {
        if (bitmap != null) {
            MatrixImage(image = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else if (source != null) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = PockyColors.InkMuted)
        } else if (content is TimelineContent.Video) {
            Icon(PockyIcons.Video, contentDescription = null, tint = PockyColors.InkMuted, modifier = Modifier.size(32.dp))
        }
        if (content is TimelineContent.Video) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Icon(PockyIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                content.durationMs?.let { duration ->
                    Spacer(Modifier.width(3.dp))
                    Text(durationLabel(duration.toLong()), fontSize = 11.sp, color = Color.White)
                }
            }
        }
        if (selected) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(PockyColors.Accent.copy(alpha = 0.35f))
                    .border(2.dp, PockyColors.AccentSoft, RoundedCornerShape(4.dp)),
            )
            Icon(
                PockyIcons.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(PockyColors.Accent, CircleShape),
            )
        }
    }
}

@Composable
private fun SelectionHeader(
    count: Int,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onForward: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        HeaderButton(PockyIcons.Close, stringResource(R.string.cancel), onClose)
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.media_selected, count),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = PockyColors.Ink,
            modifier = Modifier.weight(1f),
        )
        HeaderButton(PockyIcons.Download, stringResource(R.string.media_save), onSave)
        HeaderButton(PockyIcons.Share, stringResource(R.string.media_share), onShare)
        HeaderButton(PockyIcons.Forward, stringResource(R.string.media_forward), onForward)
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
    ) {
        Icon(icon, contentDescription = label, tint = PockyColors.Ink)
    }
}

@Composable
private fun GalleryToolbar(
    progress: DownloadProgress?,
    selecting: Boolean,
    canSelect: Boolean,
    canSaveAll: Boolean,
    onCancelSave: () -> Unit,
    onSelect: () -> Unit,
    onSaveAll: () -> Unit,
) {
    if (progress != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.media_saving, progress.done, progress.total),
                    style = MaterialTheme.typography.labelLarge,
                    color = PockyColors.Ink,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (progress.total > 0) progress.done.toFloat() / progress.total else 0f },
                    color = PockyColors.Accent,
                    trackColor = PockyColors.Surface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            HeaderButton(PockyIcons.Close, stringResource(R.string.cancel), onCancelSave)
        }
        return
    }
    if (!canSelect && !canSaveAll) return

    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        if (canSelect) {
            TextButton(onClick = onSelect) {
                Text(
                    stringResource(if (selecting) R.string.media_select_all else R.string.media_select),
                    color = PockyColors.AccentSoft,
                )
            }
        }
        if (canSaveAll && !selecting) {
            TextButton(onClick = onSaveAll) {
                Icon(PockyIcons.Download, contentDescription = null, tint = PockyColors.AccentSoft, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.media_save_all), color = PockyColors.AccentSoft)
            }
        }
    }
}

@Composable
private fun ListRow(event: TimelineEvent) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PockyColors.Surface)
            .padding(10.dp),
    ) {
        val sender = if (event.isOwn) stringResource(R.string.gallery_you) else displayName(event.senderId, event.senderName)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
        ) {
            RoomAvatar(id = event.senderId, name = sender, url = event.senderAvatar, size = 26.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = sender,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = PockyColors.Ink,
                    maxLines = 1,
                )
                Text(
                    text = "${dayLabel(event.timestampMs.toLong())} ${timeLabel(event.timestampMs.toLong())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PockyColors.InkMuted,
                )
            }
        }
        when (val content = event.content) {
            is TimelineContent.Audio -> VoiceMessage(
                source = content.source,
                durationMs = content.durationMs?.toLong(),
                waveform = content.waveform.map { it.toInt() },
                own = false,
            )
            is TimelineContent.File -> FileMessage(
                name = content.name,
                source = content.source,
                size = content.size?.toLong(),
                mime = content.mime,
                own = false,
            )
            else -> {}
        }
    }
}

private fun album(items: List<TimelineItem>): Album {
    val events = items.mapNotNull { (it.kind as? TimelineItemKind.Event)?.event }.asReversed()
    return Album(
        photos = events.filter { it.content is TimelineContent.Image },
        videos = events.filter { it.content is TimelineContent.Video },
        voice = events.filter { it.content is TimelineContent.Audio },
        files = events.filter { it.content is TimelineContent.File },
    )
}

private val MonthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", AppLocale)

private fun monthOf(timestampMs: Long): String =
    Instant.ofEpochMilli(timestampMs)
        .atZone(ZoneId.systemDefault())
        .format(MonthFormat)
        .replaceFirstChar { it.titlecase(AppLocale) }

private fun durationLabel(durationMs: Long): String {
    val seconds = durationMs / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun tabLabel(tab: GalleryTab): Int = when (tab) {
    GalleryTab.PHOTOS -> R.string.gallery_photos
    GalleryTab.VIDEOS -> R.string.gallery_videos
    GalleryTab.VOICE -> R.string.gallery_voice
    GalleryTab.FILES -> R.string.gallery_files
}

private fun emptyText(tab: GalleryTab): Int = when (tab) {
    GalleryTab.PHOTOS -> R.string.gallery_empty_photos
    GalleryTab.VIDEOS -> R.string.gallery_empty_videos
    GalleryTab.VOICE -> R.string.gallery_empty_voice
    GalleryTab.FILES -> R.string.gallery_empty_files
}

private val THUMB = 240.dp
