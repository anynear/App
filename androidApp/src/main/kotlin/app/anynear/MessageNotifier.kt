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
        val built = NotificationCompat.Builder(context, CHANNEL_ID)
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
            // Own group: without one Android bundles all notifications of the app, and the always present
            // background notification („empfängt Nachrichten“) would stand on top with the message hidden below.
            .setGroup(GROUP)
            .build()
        try {
            manager.notify(notification.key, NOTIFICATION_ID, built)
            showSummary()
        } catch (e: SecurityException) {
            // Permission was revoked in the meantime; the message is in the app anyway.
        }
    }

    fun cancel(key: String) {
        manager.cancel(key, NOTIFICATION_ID)
        if (activeConversations().isEmpty()) manager.cancel(SUMMARY_ID)
    }

    /**
     * Summary of the group, shown by Android only when several conversations are there. It never alerts
     * itself (the conversation does) and lists the conversations.
     */
    private fun showSummary() {
        val titles = activeConversations().mapNotNull { it.notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE) }
        if (titles.isEmpty()) return
        val inbox = NotificationCompat.InboxStyle()
        titles.forEach(inbox::addLine)
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(AppBrand.notificationIcon)
            .setContentTitle(AppBrand.name)
            .setContentText(titles.joinToString(", "))
            .setStyle(inbox)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        manager.notify(SUMMARY_ID, summary)
    }

    /** Message notifications currently shown, without the summary. */
    private fun activeConversations() =
        context.getSystemService(NotificationManager::class.java).activeNotifications.filter {
            it.id == NOTIFICATION_ID && it.tag != null && it.notification.group == GROUP
        }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        SUMMARY_ID,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun allowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

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

        /** Group of all message notifications and its summary. */
        private const val GROUP = "messages"
        private const val SUMMARY_ID = 101

        const val ACTION_OPEN_CONVERSATION = "app.anynear.OPEN_CONVERSATION"
        const val EXTRA_CONVERSATION = "conversation"
    }
}
