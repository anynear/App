package app.anynear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings

/**
 * Alarm for check-ins (ADR 0083 §5.3): wakes the app at the next deadline, so that question, hint and alarm come on
 * time also in deep sleep. With permission for exact alarms (Android 12+: „Alarms & reminders“) on the minute,
 * otherwise inexact with „AllowWhileIdle“ (in deep sleep about every 9 minutes). After a reboot the app plans anew.
 */
object LifeSignWake {
    fun schedule(context: Context, atMillis: Long?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        if (atMillis == null) {
            alarms.cancel(pending)
            return
        }
        if (exactAllowed(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    fun exactAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Opens the setting „Alarms & reminders“ for anynear; `false` if there is none. */
    fun openSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context, 1, Intent(context, LifeSignWakeReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
}

/** The alarm for a check-in went off: stay awake briefly, fetch, check deadlines. */
class LifeSignWakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MainApplication
        val lock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "anynear:lifesign")
            .apply { acquire(WakeAlarm.AWAKE_MILLIS) }
        val pending = goAsync()
        if (app.controller.receptionWanted) app.hooks.setMessageReception(true)
        // Fetch first, then check: a check-in that just arrived moves the deadline.
        app.controller.wakeUp()
        app.controller.lifeSigns?.wake()
        Handler(Looper.getMainLooper()).postDelayed({ app.controller.lifeSigns?.wake() }, 8_000)
        Handler(Looper.getMainLooper()).postDelayed({
            pending.finish()
            if (lock.isHeld) lock.release()
        }, WakeAlarm.AWAKE_MILLIS - 1_000)
    }
}

/** Button in a check-in notification: „Alles gut“ or „Ich schaue nach“, without opening the app. */
class LifeSignActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MainApplication
        AlarmSound.stop()
        intent.getStringExtra(EXTRA_KEY)?.let { AlertNotifier(context).cancel(it) }
        val pending = goAsync()
        intent.getStringExtra(EXTRA_ACTION)?.let { app.controller.lifeSigns?.onAction(it) }
        Handler(Looper.getMainLooper()).postDelayed({ pending.finish() }, 5_000)
    }

    companion object {
        private const val EXTRA_ACTION = "action"
        private const val EXTRA_KEY = "key"

        fun intent(context: Context, action: String, key: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, LifeSignActionReceiver::class.java).setData(Uri.parse("lifesign:$action"))
                .putExtra(EXTRA_ACTION, action).putExtra(EXTRA_KEY, key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/**
 * Ongoing notification while the own check-in runs (ADR 0083 §5.2): visible on the lock screen, „Alles gut“ and
 * „Bin fertig“ work without unlocking and without opening the app. Tapping opens the big button.
 */
class CheckInNotifier(private val context: Context) {
    private val manager = androidx.core.app.NotificationManagerCompat.from(context)

    fun show(checkIn: app.anynear.app.CheckInNotification?) {
        if (checkIn == null) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        val system = context.getSystemService(android.app.NotificationManager::class.java)
        if (system.getNotificationChannel(CHANNEL_ID) == null) {
            system.createNotificationChannel(
                // Default importance without sound and vibration: Android hides „silent“ notifications on the lock
                // screen by default, but this one should stand there with its buttons.
                android.app.NotificationChannel(CHANNEL_ID, app.anynear.i18n.texts.lifeSigns.section, android.app.NotificationManager.IMPORTANCE_DEFAULT).apply {
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).setAction(AlertNotifier.ACTION_OPEN_ALERTS)
                .putExtra(AlertNotifier.EXTRA_CHANNEL, app.anynear.lifesigns.LifeSigns.channelOf(checkIn.planId))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val built = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(AppBrand.notificationIcon)
            .setContentTitle(checkIn.title)
            .setContentText(checkIn.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .addAction(0, checkIn.okLabel, LifeSignActionReceiver.intent(context, checkIn.okAction, ""))
            .addAction(0, checkIn.endLabel, LifeSignActionReceiver.intent(context, checkIn.endAction, ""))
            .build()
        try {
            manager.notify(NOTIFICATION_ID, built)
        } catch (e: SecurityException) {
            // Notifications not allowed; the card in the app stays.
        }
    }

    companion object {
        private const val CHANNEL_ID = "lifesign_ongoing"
        private const val NOTIFICATION_ID = 400
    }
}

/**
 * Tile „Alles gut“ in the quick settings (ADR 0083 §5.2): confirms the running check-in with one tap, also from the
 * lock screen; without one it opens the app.
 */
class LifeSignTileService : android.service.quicksettings.TileService() {
    override fun onStartListening() {
        val running = (applicationContext as MainApplication).controller.lifeSigns?.ownRunning() != null
        qsTile?.apply {
            state = if (running) android.service.quicksettings.Tile.STATE_ACTIVE else android.service.quicksettings.Tile.STATE_INACTIVE
            label = if (running) app.anynear.i18n.texts.lifeSigns.allGood else app.anynear.i18n.texts.lifeSigns.section
            updateTile()
        }
    }

    override fun onClick() {
        val lifeSigns = (applicationContext as MainApplication).controller.lifeSigns
        val plan = lifeSigns?.ownRunning()
        if (plan != null) {
            lifeSigns.onAction("${app.anynear.lifesigns.LifeSigns.ACTION_OK}:$plan")
            AlarmSound.stop()
            return
        }
        val intent = Intent(this, MainActivity::class.java).setAction(AlertNotifier.ACTION_OPEN_ALERTS)
            .putExtra(AlertNotifier.EXTRA_CHANNEL, app.anynear.lifesigns.LifeSigns.CHANNEL_PREFIX)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
