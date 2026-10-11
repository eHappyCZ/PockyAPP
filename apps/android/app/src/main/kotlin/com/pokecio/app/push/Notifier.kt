package com.pokecio.app.push

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.pokecio.app.MainActivity
import com.pokecio.app.R
import com.pokecio.app.core.MessageContent
import com.pokecio.app.core.NotificationInfo
import com.pokecio.app.core.NotificationText
import java.util.concurrent.ConcurrentHashMap

class Notifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)
    private val concealed = ConcurrentHashMap<String, Pair<NotificationInfo, Long>>()

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_DIRECT, context.getString(R.string.channel_direct), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_GROUP, context.getString(R.string.channel_group), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_LOCATION, context.getString(R.string.channel_location), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_UPDATES, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    fun show(info: NotificationInfo, preview: NotificationText) {
        if (!allowed()) return
        val now = System.currentTimeMillis()
        val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

        val notification = when {
            preview == NotificationText.NEVER -> generic(info)
            preview == NotificationText.WHEN_UNLOCKED && locked -> {
                concealed[info.roomId] = info to now
                namesOnly(info)
            }
            else -> full(info, now)
        }
        post(info.roomId, notification)
    }

    fun revealConcealed() {
        val pending = concealed.toMap()
        concealed.clear()
        pending.forEach { (roomId, entry) ->
            if (manager.activeNotifications.any { it.id == notificationId(roomId) }) {
                post(roomId, full(entry.first, entry.second, silent = true))
            }
        }
    }

    fun cancel(roomId: String) {
        concealed.remove(roomId)
        manager.cancel(notificationId(roomId))
    }

    private fun full(info: NotificationInfo, timestamp: Long, silent: Boolean = false): Notification {
        val me = Person.Builder().setName(context.getString(R.string.notification_me)).build()
        val sender = Person.Builder()
            .setName(senderName(info))
            .setKey(info.senderId)
            .build()

        val existing = manager.activeNotifications
            .firstOrNull { it.id == notificationId(info.roomId) }
            ?.notification
            ?.let(NotificationCompat.MessagingStyle::extractMessagingStyleFromNotification)
        val style = existing ?: NotificationCompat.MessagingStyle(me)
        style.isGroupConversation = !info.isDirect
        if (!info.isDirect) style.conversationTitle = info.roomName
        style.addMessage(body(info.content), timestamp, sender)

        return base(info)
            .setStyle(style)
            .setOnlyAlertOnce(silent)
            .addAction(replyAction(info))
            .addAction(readAction(info))
            .build()
    }

    private fun namesOnly(info: NotificationInfo): Notification =
        base(info)
            .setContentTitle(if (info.isDirect) senderName(info) else info.roomName)
            .setContentText(context.getString(R.string.notification_unlock))
            .build()

    private fun generic(info: NotificationInfo): Notification =
        base(info)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_new))
            .build()

    private fun base(info: NotificationInfo): NotificationCompat.Builder =
        NotificationCompat.Builder(context, if (info.isDirect) CHANNEL_DIRECT else CHANNEL_GROUP)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openRoom(info.roomId))

    private fun replyAction(info: NotificationInfo): NotificationCompat.Action {
        val input = RemoteInput.Builder(NotificationActions.KEY_REPLY)
            .setLabel(context.getString(R.string.notification_reply_hint))
            .build()
        return NotificationCompat.Action.Builder(
            R.drawable.pi_reply,
            context.getString(R.string.msg_reply),
            NotificationActions.pendingIntent(context, NotificationActions.REPLY, info, mutable = true),
        )
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    private fun readAction(info: NotificationInfo): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            R.drawable.pi_check_double,
            context.getString(R.string.notification_mark_read),
            NotificationActions.pendingIntent(context, NotificationActions.READ, info, mutable = false),
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

    private fun openRoom(roomId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_ROOM_ID, roomId)
        return PendingIntent.getActivity(
            context,
            notificationId(roomId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun showUpdate(versionName: String): Boolean {
        if (!allowed()) return false
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_UPDATE, true)
        val open = PendingIntent.getActivity(
            context,
            UPDATE_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(context.getString(R.string.update_notification_title, versionName))
            .setContentText(context.getString(R.string.update_notification_text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        return try {
            manager.notify(UPDATE_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancelUpdate() = manager.cancel(UPDATE_ID)

    private fun post(roomId: String, notification: Notification) {
        if (!allowed()) return
        try {
            manager.notify(notificationId(roomId), notification)
        } catch (_: SecurityException) {
        }
    }

    private fun allowed(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    private fun senderName(info: NotificationInfo): String =
        info.senderName?.takeIf { it.isNotBlank() } ?: info.senderId.removePrefix("@").substringBefore(':')

    private fun body(content: MessageContent): String = when (content) {
        is MessageContent.Text -> content.body
        is MessageContent.Emote -> "* ${content.body}"
        MessageContent.Image -> context.getString(R.string.preview_image)
        MessageContent.Video -> context.getString(R.string.preview_video)
        MessageContent.Audio -> context.getString(R.string.preview_audio)
        MessageContent.File -> context.getString(R.string.preview_file)
        MessageContent.Location -> context.getString(R.string.preview_location)
        MessageContent.Sticker -> context.getString(R.string.preview_sticker)
        MessageContent.Poll -> context.getString(R.string.preview_poll)
        MessageContent.Encrypted -> context.getString(R.string.preview_encrypted)
        MessageContent.Invite -> context.getString(R.string.preview_invite)
        MessageContent.Other -> context.getString(R.string.preview_other)
    }

    companion object {
        const val CHANNEL_DIRECT = "direct"
        const val CHANNEL_GROUP = "group"
        const val CHANNEL_LOCATION = "location"
        const val CHANNEL_UPDATES = "updates"
        private const val UPDATE_ID = 9050

        fun notificationId(roomId: String) = roomId.hashCode()
    }
}
