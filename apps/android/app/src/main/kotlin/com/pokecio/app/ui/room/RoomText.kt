package com.pokecio.app.ui.room

import android.util.Patterns
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.pokecio.app.R
import com.pokecio.app.core.Membership
import com.pokecio.app.core.TimelineContent
import com.pokecio.app.core.TimelineEvent
import com.pokecio.app.core.TimelineStateChange
import com.pokecio.app.ui.rooms.AppLocale
import com.pokecio.app.ui.rooms.TimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val replyFallback = Regex("<mx-reply>.*?</mx-reply>", RegexOption.DOT_MATCHES_ALL)

fun displayName(userId: String, name: String?): String =
    name?.takeIf { it.isNotBlank() } ?: userId.removePrefix("@").substringBefore(':')

fun messageText(body: String, html: String?, hasReply: Boolean, linkColor: Color): AnnotatedString {
    val links = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
    )

    if (html != null) {
        return AnnotatedString.fromHtml(html.replace(replyFallback, "").trim(), links)
    }

    val text = if (hasReply) stripReplyFallback(body) else body
    return buildAnnotatedString {
        var last = 0
        val matcher = Patterns.WEB_URL.matcher(text)
        while (matcher.find()) {
            append(text.substring(last, matcher.start()))
            val url = matcher.group()
            val target = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            withLink(LinkAnnotation.Url(target, links)) { append(url) }
            last = matcher.end()
        }
        append(text.substring(last))
    }
}

private fun stripReplyFallback(body: String): String {
    val lines = body.lines()
    if (lines.firstOrNull()?.startsWith("> ") != true) return body
    return lines.dropWhile { it.startsWith(">") }.dropWhile { it.isBlank() }.joinToString("\n")
}

@Composable
fun contentLabel(content: TimelineContent): String? = when (content) {
    is TimelineContent.Image -> stringResource(R.string.msg_image)
    is TimelineContent.Video -> stringResource(R.string.msg_video)
    is TimelineContent.Audio -> stringResource(R.string.msg_audio)
    is TimelineContent.File -> stringResource(R.string.msg_file, content.name)
    is TimelineContent.Location -> stringResource(R.string.msg_location)
    is TimelineContent.LiveLocation -> stringResource(
        if (content.isLive) R.string.msg_live_location else R.string.msg_live_location_ended,
    )
    is TimelineContent.Sticker -> stringResource(R.string.msg_sticker)
    is TimelineContent.Poll -> stringResource(R.string.msg_poll, content.question)
    TimelineContent.Redacted -> stringResource(R.string.msg_redacted)
    TimelineContent.UnableToDecrypt -> stringResource(R.string.msg_utd)
    TimelineContent.Other -> stringResource(R.string.msg_other)
    else -> null
}

@Composable
fun stateText(event: TimelineEvent, change: TimelineStateChange, repeat: Int): String {
    val actor = displayName(event.senderId, event.senderName)

    val text = when (change) {
        is TimelineStateChange.Membership -> {
            val subject = displayName(change.userId, change.name)
            when (change.change) {
                Membership.JOINED -> stringResource(R.string.state_joined, subject)
                Membership.LEFT -> stringResource(R.string.state_left, subject)
                Membership.INVITED -> stringResource(R.string.state_invited, actor, subject)
                Membership.INVITATION_ACCEPTED -> stringResource(R.string.state_invitation_accepted, subject)
                Membership.INVITATION_REJECTED -> stringResource(R.string.state_invitation_rejected, subject)
                Membership.INVITATION_REVOKED -> stringResource(R.string.state_invitation_revoked, actor, subject)
                Membership.KICKED -> stringResource(R.string.state_kicked, actor, subject)
                Membership.BANNED -> stringResource(R.string.state_banned, actor, subject)
                Membership.UNBANNED -> stringResource(R.string.state_unbanned, actor, subject)
                Membership.KNOCKED -> stringResource(R.string.state_knocked, subject)
                Membership.OTHER -> stringResource(R.string.state_membership_other, subject)
            }
        }
        is TimelineStateChange.DisplayName -> {
            val old = change.old
            val new = change.new
            when {
                new == null -> stringResource(R.string.state_display_name_removed, actor)
                old == null -> stringResource(
                    R.string.state_display_name_set,
                    displayName(change.userId, null),
                    new,
                )
                else -> stringResource(R.string.state_display_name, old, new)
            }
        }
        is TimelineStateChange.Avatar -> stringResource(R.string.state_avatar, actor)
        is TimelineStateChange.RoomName -> change.name?.let {
            stringResource(R.string.state_room_name, actor, it)
        } ?: stringResource(R.string.state_room_name_removed, actor)
        is TimelineStateChange.RoomTopic -> change.topic?.let {
            stringResource(R.string.state_room_topic, actor, it)
        } ?: stringResource(R.string.state_room_topic_removed, actor)
        TimelineStateChange.RoomAvatar -> stringResource(R.string.state_room_avatar, actor)
        TimelineStateChange.RoomCreated -> stringResource(R.string.state_room_created, actor)
        TimelineStateChange.EncryptionEnabled -> stringResource(R.string.state_encryption)
        TimelineStateChange.Other -> stringResource(R.string.state_other, actor)
    }

    return if (repeat > 1) stringResource(R.string.state_repeat, text, repeat) else text
}

@Composable
fun dayLabel(timestampMs: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)

    return when {
        date == today -> stringResource(R.string.day_today)
        date == today.minusDays(1) -> stringResource(R.string.day_yesterday)
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("EEEE d. MMMM", AppLocale))
            .replaceFirstChar { it.titlecase(AppLocale) }
        else -> date.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", AppLocale))
    }
}

fun timeLabel(timestampMs: Long): String =
    Instant.ofEpochMilli(timestampMs)
        .atZone(ZoneId.systemDefault())
        .format(TimeFormat)
