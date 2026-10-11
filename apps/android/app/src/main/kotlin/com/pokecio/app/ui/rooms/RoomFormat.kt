package com.pokecio.app.ui.rooms

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pokecio.app.R
import com.pokecio.app.core.LastMessage
import com.pokecio.app.core.MessageContent
import com.pokecio.app.core.Typist
import com.pokecio.app.ui.room.displayName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun typingText(typists: List<Typist>, isDirect: Boolean): String {
    val names = typists.map { displayName(it.userId, it.name) }
    return when {
        isDirect || names.isEmpty() -> stringResource(R.string.typing_direct)
        names.size == 1 -> stringResource(R.string.typing_one, names[0])
        names.size == 2 -> stringResource(R.string.typing_two, names[0], names[1])
        else -> stringResource(R.string.typing_many)
    }
}

@Composable
fun previewText(message: LastMessage, isDirect: Boolean): String {
    val content = when (val c = message.content) {
        is MessageContent.Text -> c.body
        is MessageContent.Emote -> "* ${c.body}"
        MessageContent.Image -> stringResource(R.string.preview_image)
        MessageContent.Video -> stringResource(R.string.preview_video)
        MessageContent.Audio -> stringResource(R.string.preview_audio)
        MessageContent.File -> stringResource(R.string.preview_file)
        MessageContent.Location -> stringResource(R.string.preview_location)
        MessageContent.Sticker -> stringResource(R.string.preview_sticker)
        MessageContent.Poll -> stringResource(R.string.preview_poll)
        MessageContent.Encrypted -> stringResource(R.string.preview_encrypted)
        MessageContent.Invite -> stringResource(R.string.preview_invite)
        MessageContent.Other -> stringResource(R.string.preview_other)
    }

    val sender = when {
        message.isOwn -> stringResource(R.string.preview_you)
        isDirect -> null
        else -> message.senderName ?: message.senderId.substringBefore(':').removePrefix("@")
    }

    return if (sender == null) content else "$sender: $content"
}

@Composable
fun formatTimestamp(timestampMs: Long): String {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(timestampMs).atZone(zone)
    val today = LocalDate.now(zone)
    val date = time.toLocalDate()

    return when {
        date == today -> time.format(TimeFormat)
        date == today.minusDays(1) -> stringResource(R.string.day_yesterday)
        date.isAfter(today.minusDays(7)) -> date.dayOfWeek
            .getDisplayName(TextStyle.SHORT, AppLocale)
            .replaceFirstChar { it.titlecase(AppLocale) }
        date.year == today.year -> time.format(DateTimeFormatter.ofPattern("d. M.", AppLocale))
        else -> time.format(DateTimeFormatter.ofPattern("d. M. yyyy", AppLocale))
    }
}

val AppLocale: Locale = Locale.forLanguageTag("cs-CZ")

val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", AppLocale)
