package com.pokecio.app.ui.room

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.TimelineContent
import com.pokecio.app.core.TimelineEvent
import com.pokecio.app.media.UriInfo
import com.pokecio.app.rooms.Composing
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ComposingBar(mode: Composing, onCancel: () -> Unit) {
    val (icon, title, event) = when (mode) {
        Composing.Message -> return
        is Composing.Reply -> Triple(
            PockyIcons.Reply,
            stringResource(R.string.composer_replying, displayName(mode.event.senderId, mode.event.senderName)),
            mode.event,
        )
        is Composing.Edit -> Triple(PockyIcons.Edit, stringResource(R.string.composer_editing), mode.event)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 6.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .background(PockyColors.Accent, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Icon(icon, contentDescription = null, tint = PockyColors.AccentSoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = PockyColors.AccentSoft,
                maxLines = 1,
            )
            Text(
                text = summary(event),
                style = MaterialTheme.typography.bodySmall,
                color = PockyColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(PockyIcons.Close, contentDescription = stringResource(R.string.cancel), tint = PockyColors.InkMuted)
        }
    }
}

@Composable
fun ImageSendDialog(
    uris: List<Uri>,
    sending: Boolean,
    onSend: (caption: String, fullQuality: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var caption by rememberSaveable { mutableStateOf("") }
    var fullQuality by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        containerColor = PockyColors.Surface,
        title = {
            Text(
                if (uris.size == 1) {
                    stringResource(R.string.image_send_title)
                } else {
                    pluralStringResource(R.plurals.image_send_many, uris.size, uris.size)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (uris.size == 1) {
                    Preview(
                        uri = uris.first(),
                        side = PREVIEW_SIDE,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 280.dp),
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uris) { uri ->
                            Preview(uri = uri, side = THUMB_SIDE, modifier = Modifier.size(110.dp), crop = true)
                        }
                    }
                }
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    enabled = !sending,
                    placeholder = { Text(stringResource(R.string.image_caption)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = fullQuality,
                        onCheckedChange = { fullQuality = it },
                        enabled = !sending,
                        colors = CheckboxDefaults.colors(checkedColor = PockyColors.Accent),
                    )
                    Text(stringResource(R.string.image_full_quality), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = stringResource(R.string.image_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = PockyColors.InkMuted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(caption, fullQuality) }, enabled = !sending) {
                if (sending) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.send))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun Preview(uri: Uri, side: Int, modifier: Modifier, crop: Boolean = false) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > side) {
                        val scale = side.toFloat() / longest
                        decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                    }
                }
            }.getOrNull()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PockyColors.Bg),
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit,
    onLocation: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PockyColors.Surface,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            AttachOption(PockyIcons.Camera, stringResource(R.string.attach_camera), Modifier.weight(1f), onCamera)
            AttachOption(PockyIcons.Images, stringResource(R.string.attach_gallery), Modifier.weight(1f), onGallery)
            AttachOption(PockyIcons.File, stringResource(R.string.attach_file), Modifier.weight(1f), onFile)
            AttachOption(PockyIcons.Location, stringResource(R.string.attach_location), Modifier.weight(1f), onLocation)
        }
    }
}

@Composable
private fun AttachOption(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(PockyColors.Bg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 18.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(54.dp)
                .background(PockyGradient, CircleShape),
        ) {
            Icon(icon, contentDescription = null, tint = PockyColors.OnBrand, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = PockyColors.Ink)
    }
}

@Composable
fun FileSendDialog(info: UriInfo, sending: Boolean, onSend: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        containerColor = PockyColors.Surface,
        title = { Text(stringResource(R.string.file_send_title)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .background(PockyColors.AccentSoft.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                ) {
                    Icon(PockyIcons.File, contentDescription = null, tint = PockyColors.AccentSoft)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(info.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    info.size?.let {
                        Text(readableSize(it), style = MaterialTheme.typography.bodySmall, color = PockyColors.InkMuted)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSend, enabled = !sending) {
                if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun VideoSendDialog(uris: List<Uri>, sending: Boolean, onSend: (caption: String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var caption by rememberSaveable { mutableStateOf("") }
    var thumbnail by remember(uris) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uris) {
        thumbnail = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().run {
                    setDataSource(context, uris.first())
                    val frame = getFrameAtTime(0)
                    release()
                    frame
                }
            }.getOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        containerColor = PockyColors.Surface,
        title = { Text(pluralStringResource(R.plurals.video_send_title, uris.size, uris.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PockyColors.Bg),
                ) {
                    val frame = thumbnail
                    if (frame != null) {
                        Image(
                            bitmap = frame.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        VideoOverlay(durationMs = null)
                    } else {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    enabled = !sending,
                    placeholder = { Text(stringResource(R.string.image_caption)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.video_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = PockyColors.InkMuted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(caption) }, enabled = !sending) {
                if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun summary(event: TimelineEvent): String =
    plainText(event.content) ?: when (event.content) {
        is TimelineContent.Image -> stringResource(R.string.preview_image)
        else -> stringResource(R.string.preview_other)
    }

private const val PREVIEW_SIDE = 1080
private const val THUMB_SIDE = 320
