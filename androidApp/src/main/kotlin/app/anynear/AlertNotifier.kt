package app.anynear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.anynear.alerts.AlarmProblem
import app.anynear.alerts.AlertLoudness
import app.anynear.alerts.AlertNotification
import app.anynear.alerts.AlertSound
import app.anynear.i18n.texts

/**
 * Notifications for alerts (ADR 0062 §5, §6), built on the phone from the decrypted alerts.
 *
 * Two kinds of channels: with the normal sound (every alert that is not loud, including info; muting applies, like
 * the default priority in ntfy) and alarms (loud, even when muted). An alarm channel plays its sound over
 * the alarm audio stream (`USAGE_ALARM`), which is not affected by silent mode, may bypass „Nicht stören“
 * (only works with access to it) and shows a full screen on the lock screen. Android cannot change the sound of an
 * existing channel, so there is a separate alarm channel per sound.
 */
class AlertNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    private val system = context.getSystemService(NotificationManager::class.java)

    fun show(notification: AlertNotification) {
        val channel = when (notification.loudness) {
            AlertLoudness.Normal -> NORMAL_CHANNEL
            AlertLoudness.Loud -> ensureAlarmChannel(notification.sound)
        }
        ensureChannels()
        // An all-clear replaces the notification of the alert it cancels.
        notification.replacesKey?.let { manager.cancel(it, NOTIFICATION_ID) }
        val loud = notification.loudness == AlertLoudness.Loud
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(AppBrand.notificationIcon)
            .setContentTitle(notification.title)
            .setContentText(notification.text)
            .setSubText(notification.channelName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.text))
            .setWhen(notification.timestampMillis)
            .setShowWhen(true)
            .setCategory(if (loud) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_EVENT)
            .setPriority(if (loud) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            // An alarm must be readable on the lock screen too; other alerts only after unlocking.
            .setVisibility(if (loud) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openIntent(notification.channelId))
            .setAutoCancel(true)
        // Buttons of the app, e.g. „Alles gut“ for a check-in (ADR 0083); they work without opening the app.
        notification.actions.forEach { builder.addAction(0, it.label, LifeSignActionReceiver.intent(context, it.id, notification.key)) }
        if (loud) {
            builder.setFullScreenIntent(alarmIntent(notification), true)
                .setDeleteIntent(stopIntent(notification.key))
                .addAction(0, texts.alerts.silence, stopIntent(notification.key))
        }
        try {
            manager.notify(notification.key, NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Notifications not allowed; the alert is in the app anyway, the test alarm says so.
        }
        // „Nicht stören“ without access holds the alarm back. The alarm sound then usually gets through anyway
        // (alarms are allowed there), so play it ourselves. With access the system plays it, not twice.
        if (loud && dndBlocksAlarms()) AlarmSound.play(context, notification.sound)
    }

    fun cancel(key: String) = manager.cancel(key, NOTIFICATION_ID)

    /** All notifications of a channel (key `<channel>/<alert>`, see `notifiedKey`). */
    fun cancelChannel(channelId: String) {
        val system = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        system.activeNotifications.filter { it.id == NOTIFICATION_ID && it.tag?.startsWith("$channelId/") == true }
            .forEach { manager.cancel(it.tag, NOTIFICATION_ID) }
    }

    /** What stands in the way of an alarm (the shared code checks the battery). */
    fun problems(): List<AlarmProblem> = buildList {
        val channel = system.getNotificationChannel(alarmChannelId(AlertSound.Alarm))
        if (!manager.areNotificationsEnabled() || channel?.importance == NotificationManager.IMPORTANCE_NONE) add(AlarmProblem.NotificationsOff)
        if (!system.isNotificationPolicyAccessGranted) add(AlarmProblem.DoNotDisturbAccess)
        if (Build.VERSION.SDK_INT >= 34 && !system.canUseFullScreenIntent()) add(AlarmProblem.FullScreenNotAllowed)
    }

    fun doNotDisturbAccessMissing(): Boolean = !system.isNotificationPolicyAccessGranted

    /** System setting for [problem]; `false` if there is no dedicated one. */
    fun openSettings(problem: AlarmProblem): Boolean {
        val app = Uri.fromParts("package", context.packageName, null)
        val candidates = when (problem) {
            AlarmProblem.DoNotDisturbAccess -> listOf(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            AlarmProblem.FullScreenNotAllowed -> if (Build.VERSION.SDK_INT >= 34) listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, app)) else emptyList()
            else -> emptyList()
        } + Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)
        if (problem == AlarmProblem.NotificationsOff) return false
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Einstellung nicht vorhanden: ${intent.action}")
            }
        }
        return false
    }

    /**
     * Create the channel with the normal sound or rename it in the current language; existing alarm channels too, so
     * that they bypass „Nicht stören“ as soon as access is granted. The former channel without sound no longer exists:
     * info alerts arrived through it unnoticed.
     */
    fun ensureChannels() {
        system.deleteNotificationChannel(OLD_QUIET_CHANNEL)
        system.createNotificationChannel(
            NotificationChannel(NORMAL_CHANNEL, texts.alerts.channelNormal, NotificationManager.IMPORTANCE_DEFAULT),
        )
        AlertSound.entries.forEach { sound ->
            if (system.getNotificationChannel(alarmChannelId(sound)) != null) ensureAlarmChannel(sound)
        }
    }

    private fun ensureAlarmChannel(sound: AlertSound): String {
        val id = alarmChannelId(sound)
        val channel = NotificationChannel(id, texts.alerts.channelAlarm(soundName(sound)), NotificationManager.IMPORTANCE_HIGH).apply {
            description = texts.alerts.channelAlarmDescription
            setSound(soundUri(context, sound), ALARM_AUDIO)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        system.createNotificationChannel(channel)
        return id
    }

    /** Is „Nicht stören“ on and does it hold this alarm back? */
    private fun dndBlocksAlarms(): Boolean =
        system.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL && !system.isNotificationPolicyAccessGranted

    private fun openIntent(channelId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_ALERTS)
            .putExtra(EXTRA_CHANNEL, channelId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, channelId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun alarmIntent(notification: AlertNotification): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java)
            .putExtra(AlarmActivity.EXTRA_TITLE, notification.title)
            .putExtra(AlarmActivity.EXTRA_TEXT, notification.text)
            .putExtra(AlarmActivity.EXTRA_CHANNEL_NAME, notification.channelName)
            .putExtra(EXTRA_CHANNEL, notification.channelId)
            .putExtra(EXTRA_KEY, notification.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        // The app's button (e.g. „Alles gut“) becomes the big button on the full screen (ADR 0083).
        notification.actions.firstOrNull()?.let {
            intent.putExtra(AlarmActivity.EXTRA_ACTION_ID, it.id).putExtra(AlarmActivity.EXTRA_ACTION_LABEL, it.label)
        }
        return PendingIntent.getActivity(context, notification.key.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun stopIntent(key: String): PendingIntent = PendingIntent.getBroadcast(
        context, key.hashCode(), Intent(context, AlarmStopReceiver::class.java).putExtra(EXTRA_KEY, key),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun soundName(sound: AlertSound) = when (sound) {
        AlertSound.Alarm -> texts.alerts.soundAlarm
        AlertSound.Official -> texts.alerts.soundOfficial
        AlertSound.Hint -> texts.alerts.soundHint
        AlertSound.Question -> texts.alerts.soundQuestion
    }

    companion object {
        private const val TAG = "App"
        private const val OLD_QUIET_CHANNEL = "alerts_quiet"
        private const val NORMAL_CHANNEL = "alerts_normal"

        /** One channel per sound; the sound of a channel cannot be changed afterwards. */
        fun alarmChannelId(sound: AlertSound) = "alerts_alarm_${sound.file}"

        /** One notification per alert: tag = alert, ID always the same. */
        private const val NOTIFICATION_ID = 200

        const val ACTION_OPEN_ALERTS = "app.anynear.OPEN_ALERTS"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_KEY = "alert"

        val ALARM_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        fun soundUri(context: Context, sound: AlertSound): Uri = Uri.parse("android.resource://${context.packageName}/raw/${sound.file}")
    }
}

/** Alarm sound played ourselves over the alarm audio stream, once; „Ton aus“ stops it. */
object AlarmSound {
    private var player: MediaPlayer? = null

    @Synchronized
    fun play(context: Context, sound: AlertSound) {
        stop()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AlertNotifier.ALARM_AUDIO)
                setDataSource(context, AlertNotifier.soundUri(context, sound))
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
        }.onFailure { Log.w("App", "Alarmton nicht abspielbar", it) }.getOrNull()
    }

    @Synchronized
    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }
}

/**
 * „Ton aus“ in the notification or swiping away: stop the alarm sound. The system's sound is only stopped by removing
 * the notification; the alert stays in the app.
 */
class AlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmSound.stop()
        intent.getStringExtra(AlertNotifier.EXTRA_KEY)?.let { AlertNotifier(context).cancel(it) }
    }
}
