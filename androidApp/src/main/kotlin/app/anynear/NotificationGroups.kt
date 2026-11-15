package app.anynear

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Every kind of notification in its own group, each group with a summary (ADR 0008).
 *
 * Android bundles the notifications of an app that have no group, and since Android 16 also groups without a
 * summary. The background notification („empfängt Nachrichten“) is always there and then stood on top of the
 * bundle, with the alert, check-in or reminder hidden below it. A group with a summary stays apart. As long as it
 * holds one notification, Android shows that one alone; the summary only appears from two on.
 *
 * Post and cancel grouped notifications through [notify] and [cancel], so that the summary follows.
 */
internal object NotificationGroups {
    /** Background service „empfängt Nachrichten“ ([MeshConnectionService]). */
    const val BACKGROUND = "background"
    const val MESSAGES = "messages"
    const val ALERTS = "alerts"

    /** Alerts of the life signs (ADR 0083), e.g. „Alles gut?“ or a missing check-in. */
    const val LIFE_SIGNS = "lifesigns"

    /** The running own check-in ([CheckInNotifier]). */
    const val CHECK_IN = "lifesign_ongoing"
    const val REMINDERS = "reminders"
    const val UPDATES = "updates"
    const val DOC_FINDER = "doc_finder"

    /** Summaries: tag = group, ID always the same. */
    private const val SUMMARY_ID = 900

    /** Posts [notification] (with its group set) and updates the summary of the group. */
    fun notify(context: Context, tag: String?, id: Int, notification: Notification) {
        NotificationManagerCompat.from(context).notify(tag, id, notification)
        notification.group?.let { summarize(context, it, posted = Shown(tag, id, notification)) }
    }

    /** Cancels a notification of [group] and updates the summary (gone with the last one). */
    fun cancel(context: Context, group: String, tag: String?, id: Int) {
        NotificationManagerCompat.from(context).cancel(tag, id)
        summarize(context, group, removed = Shown(tag, id, null))
    }

    /**
     * Updates the summary of [group] after a notification was posted or removed outside of [notify] and [cancel],
     * e.g. by `startForeground` or by ending a service.
     */
    fun summarize(context: Context, group: String, posted: Shown? = null, removed: Shown? = null) {
        val manager = NotificationManagerCompat.from(context)
        // Android takes notifications over asynchronously: the one just posted may still be missing in the list,
        // the one just cancelled may still be in it.
        val children = (
            active(context)
                .filter { it.notification.group == group && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                .map { Shown(it.tag, it.id, it.notification) }
                .filterNot { it.sameAs(posted) || it.sameAs(removed) } + listOfNotNull(posted)
            ).mapNotNull { it.notification }
        if (children.isEmpty()) {
            manager.cancel(group, SUMMARY_ID)
            return
        }
        val newest = children.maxBy { it.`when` }
        val titles = children.mapNotNull { it.extras.getCharSequence(NotificationCompat.EXTRA_TITLE) }
        val inbox = NotificationCompat.InboxStyle()
        titles.forEach(inbox::addLine)
        val ongoing = children.any { it.flags and Notification.FLAG_ONGOING_EVENT != 0 }
        val summary = NotificationCompat.Builder(context, NotificationCompat.getChannelId(newest) ?: return)
            .setSmallIcon(AppBrand.notificationIcon)
            .setContentTitle(AppBrand.name)
            .setContentText(titles.joinToString(", "))
            .setStyle(inbox)
            .setCategory(newest.category)
            // Like the notifications in it: the running check-in also stands on the lock screen.
            .setVisibility(newest.visibility)
            .setContentIntent(newest.contentIntent)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setGroup(group)
            .setGroupSummary(true)
            // Never alerts itself; the notification in it does.
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            .build()
        try {
            manager.notify(group, SUMMARY_ID, summary)
        } catch (e: SecurityException) {
            // Notifications not allowed (any more); the content is in the app anyway.
        }
    }

    /**
     * Removes summaries whose notifications are gone, e.g. after tapping the last one (it cancels itself, the
     * summary is not told). Called when the app comes to the front.
     */
    fun tidy(context: Context) {
        val shown = active(context)
        val groups = shown.filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }.mapNotNull { it.notification.group }.toSet()
        shown.filter { it.id == SUMMARY_ID && it.tag != null && it.tag !in groups }
            .forEach { NotificationManagerCompat.from(context).cancel(it.tag, SUMMARY_ID) }
    }

    private fun active(context: Context): List<StatusBarNotification> =
        context.getSystemService(NotificationManager::class.java)?.activeNotifications?.toList().orEmpty()

    /** A notification by tag and ID, the way Android tells them apart. */
    class Shown(val tag: String?, val id: Int, val notification: Notification?) {
        fun sameAs(other: Shown?) = other != null && other.tag == tag && other.id == id
    }
}
