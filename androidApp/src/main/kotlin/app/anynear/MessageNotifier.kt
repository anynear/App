package app.anynear

import android.Manifest
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
import androidx.core.graphics.drawable.IconCompat
import app.anynear.app.MessageNotification
import app.anynear.i18n.texts

/**
 * Notifications for new messages. They are created on the phone from the decrypted
 * messages; no push service sees content, sender or circle (ADR 0008).
 *
 * One notification per conversation, as in common messengers: the title is the circle or the person,
 * below it the new messages with sender. Tapping opens exactly this conversation.
 */
class MessageNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun show(notification: MessageNotification) {
        if (!allowed()) return
        ensureChannel()
        val me = Person.Builder().setName(texts.common.me).build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(if (notification.isCircle) notification.title else null)
            .setGroupConversation(notification.isCircle)
        notification.lines.forEach { line ->
            val sender = Person.Builder().setName(line.sender)
                .apply { line.senderKey?.let(::setKey) }
                .apply { AvatarBitmaps.square(line.senderImage)?.let { setIcon(IconCompat.createWithBitmap(it)) } }
                .build()
            style.addMessage(line.text, line.timestampMillis, sender)
        }
        // A reply from the notification did not go: say so below the messages, as from oneself.
        notification.replyError?.let { style.addMessage(texts.system.replyNotSent(it), System.currentTimeMillis(), null as Person?) }
        // Image of the circle or the person large next to the notification, as in the overview.
        val largeIcon = AvatarBitmaps.square(notification.image)
        // Only reactions are new: no „0 neuen Nachrichten“.
        val newCount = if (notification.unreadCount > 0) texts.system.newMessages(notification.unreadCount) else texts.system.newReaction
        // Lock screen: only that something is there. Content only after unlocking.
        val public = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(AppBrand.notificationIcon)
            .setContentTitle(AppBrand.name)
            .setContentText(newCount)
            .build()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(AppBrand.notificationIcon)
            .setContentTitle(notification.title)
            .setContentText(notification.lines.lastOrNull()?.text ?: newCount)
            .setStyle(style)
            .setLargeIcon(largeIcon)
            // Belongs to the same-named shortcut (ShareShortcuts): Android then shows it as a conversation with image.
            .setShortcutId(notification.key)
            .setNumber(notification.unreadCount)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(openIntent(notification.key))
            .setAutoCancel(true)
            // Own group with a summary, apart from the background notification ([NotificationGroups]).
            .setGroup(NotificationGroups.MESSAGES)
            // Again after a failed reply, but without a second sound.
            .setOnlyAlertOnce(notification.replyError != null)
        if (notification.canReply) builder.addAction(replyAction(notification.key))
        // Only with something unread: a notification for a failed reply or only reactions has nothing to mark.
        if (notification.unreadCount > 0) builder.addAction(markReadAction(notification.key))
        val built = builder.build()
        try {
            // Summary of app versions up to 13.11.2026, replaced by the one of [NotificationGroups].
            manager.cancel(OLD_SUMMARY_ID)
            NotificationGroups.notify(context, notification.key, NOTIFICATION_ID, built)
        } catch (e: SecurityException) {
            // Permission was revoked in the meantime; the message is in the app anyway.
        }
    }

    fun cancel(key: String) = NotificationGroups.cancel(context, NotificationGroups.MESSAGES, key, NOTIFICATION_ID)

    private fun allowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * „Antworten“ with ready-made short replies: watches (Wear OS, Garmin) show them as quick replies, on the phone the
     * text field stays free. Sends without opening the app ([MessageReplyReceiver]).
     */
    private fun replyAction(key: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(MessageReplyReceiver.KEY_TEXT)
            .setLabel(texts.system.replyHint)
            .setChoices(texts.system.quickReplies.toTypedArray())
            .setAllowFreeFormInput(true)
            .build()
        return NotificationCompat.Action.Builder(AppBrand.notificationIcon, texts.system.reply, MessageReplyReceiver.intent(context, key))
            .addRemoteInput(input)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    /**
     * „Gelesen“: marks the conversation as read without opening the app ([MessageReadReceiver]). Together with
     * „Antworten“ the condition for Android Auto to read out and show the messages; the semantic action tells it which
     * button is which.
     */
    private fun markReadAction(key: String): NotificationCompat.Action =
        NotificationCompat.Action.Builder(AppBrand.notificationIcon, texts.system.markRead, MessageReadReceiver.intent(context, key))
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

    private fun openIntent(key: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_CONVERSATION)
            .putExtra(EXTRA_CONVERSATION, key)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Creates the channel or renames it in the current language. For an existing channel Android only
     * takes over name and description, the person's settings stay.
     */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, texts.system.messagesChannel, NotificationManager.IMPORTANCE_HIGH).apply {
                description = texts.system.messagesChannelDescription
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "messages"

        /** One notification per conversation: tag = conversation, ID always the same. */
        private const val NOTIFICATION_ID = 100

        /** Former summary of the messages, before [NotificationGroups]. */
        private const val OLD_SUMMARY_ID = 101

        const val ACTION_OPEN_CONVERSATION = "app.anynear.OPEN_CONVERSATION"
        const val EXTRA_CONVERSATION = "conversation"
    }
}
