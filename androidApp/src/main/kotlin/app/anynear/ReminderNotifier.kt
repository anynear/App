package app.anynear

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.anynear.app.Reminder
import app.anynear.i18n.texts

/**
 * Reminders (ADR 0085), e.g. to charge the radio: immediately as a notification in the channel „Erinnerungen“ or
 * scheduled via `AlarmManager` with „AllowWhileIdle“ (without permission for exact alarms; a few minutes later does
 * not matter). After a restart of the phone scheduled alarms are gone; the app reschedules on the next start.
 */
class ReminderNotifier(private val context: Context) {
    fun show(reminder: Reminder) {
        val at = reminder.atMillis
        if (at == null || at <= System.currentTimeMillis()) {
            post(context, reminder.id, reminder.title, reminder.text)
            return
        }
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context, reminder))
    }

    fun cancel(id: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, Reminder(id, "", "")))
    }

    companion object {
        private const val CHANNEL_ID = "reminders"
        private const val NOTIFICATION_ID = 300
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"

        /** One alarm per identifier: the same identifier replaces it (FLAG_UPDATE_CURRENT adopts title and text). */
        private fun pendingIntent(context: Context, reminder: Reminder): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java)
                .setData(android.net.Uri.parse("reminder:" + reminder.id))
                .putExtra(EXTRA_ID, reminder.id)
                .putExtra(EXTRA_TITLE, reminder.title)
                .putExtra(EXTRA_TEXT, reminder.text)
            return PendingIntent.getBroadcast(
                context,
                reminder.id.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        fun post(context: Context, id: String, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context,
                id.hashCode(),
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val built = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(AppBrand.notificationIcon)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(open)
                .setAutoCancel(true)
                // Own group with a summary, apart from the background notification ([NotificationGroups]).
                .setGroup(NotificationGroups.REMINDERS)
                .build()
            try {
                NotificationGroups.notify(context, id, NOTIFICATION_ID, built)
            } catch (e: SecurityException) {
                // Permission revoked in the meantime.
            }
        }

        /** Creates the channel or renames it in the current language; the person's settings stay. */
        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, texts.system.remindersChannel, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = texts.system.remindersChannelDescription
                },
            )
        }
    }
}

/** A scheduled reminder alarm has fired: show the notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderNotifier.EXTRA_ID) ?: return
        ReminderNotifier.post(
            context,
            id,
            intent.getStringExtra(ReminderNotifier.EXTRA_TITLE).orEmpty(),
            intent.getStringExtra(ReminderNotifier.EXTRA_TEXT).orEmpty(),
        )
    }
}
