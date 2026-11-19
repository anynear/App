package app.anynear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.anynear.i18n.texts

/**
 * Keeps the process in the foreground so that Android does not end it: for receiving new messages
 * from the mailboxes (instead of a push service, ADR 0008) and for the BLE connection to the radio.
 * Receiving and connection themselves belong to the AppController in [MainApplication].
 */
class MeshConnectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onDestroy() {
        if (running === this) running = null
        // Android removes the notification with the service; then the summary goes too.
        NotificationGroups.summarize(this, NotificationGroups.BACKGROUND, removed = NotificationGroups.Shown(null, NOTIFICATION_ID, null))
        log("Dienst beendet")
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Without an intent: Android restarted the service on its own after an exit (START_STICKY).
        val radio = intent?.getBooleanExtra(EXTRA_RADIO, false) ?: false
        refresh(radio)
        log("Dienst läuft" + (if (radio) " (mit Funkgerät)" else "") + (if (intent == null) ", von Android neu gestartet" else ""))
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        log("App aus den letzten Apps entfernt, Dienst läuft weiter")
        super.onTaskRemoved(rootIntent)
    }

    private fun log(message: String) = (application as? MainApplication)?.hooks?.log?.invoke(message)

    /** Adjust type and text without restarting the service (also works when the app is in the background). */
    fun refresh(radio: Boolean) {
        ensureChannel(this)
        val type = when {
            // Android 14+: the type must match the purpose. Messages always, radio only when connected.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING or
                    (if (radio) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            else -> 0
        }
        try {
            val notification = buildNotification()
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            NotificationGroups.summarize(this, NotificationGroups.BACKGROUND, posted = NotificationGroups.Shown(null, NOTIFICATION_ID, notification))
        } catch (e: SecurityException) {
            // Bluetooth permission missing (connectedDevice): then at least receive messages.
            log("Dienst ohne Funkgerät: ${e.message.orEmpty()}")
            if (!radio) throw e
            refresh(radio = false)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            // Tapped: the app explains the notification and offers to hide it.
            Intent(this, MainActivity::class.java).setAction(ACTION_OPEN_NOTICE).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(AppBrand.notificationIcon)
            .setContentTitle(AppBrand.label(this))
            .setContentText(texts.system.receiving)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            // Own group with a summary: never bundled with messages, alerts or life signs, where it would stand on
            // top and hide them ([NotificationGroups]).
            .setGroup(NotificationGroups.BACKGROUND)
            .build()
    }

    companion object {
        /** Running service, read only on the main thread. */
        var running: MeshConnectionService? = null
            private set

        /** The ongoing notification was tapped ([MainActivity]). */
        const val ACTION_OPEN_NOTICE = "app.anynear.background.OPEN_NOTICE"

        /** Whether the connection to the radio is held in addition (type connectedDevice). */
        const val EXTRA_RADIO = "radio"

        /**
         * Own channel without a dot on the app icon: the notification is always there, the dot should only
         * indicate something new. Android does not change that for an existing channel, hence a new ID.
         */
        const val CHANNEL_ID = "background"

        /** Former channel with a dot on the app icon, from the time when the service only held the radio. */
        private const val OLD_CHANNEL_ID = "mesh_connection"
        private const val NOTIFICATION_ID = 1

        /** Creates the channel or renames it in the current language (like [MessageNotifier.ensureChannel]). */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, texts.system.backgroundChannel, NotificationManager.IMPORTANCE_MIN).apply {
                    description = texts.system.backgroundChannelDescription
                    setShowBadge(false)
                },
            )
            // As long as the service still runs with the old channel, Android refuses that; then on the next start.
            try {
                manager.deleteNotificationChannel(OLD_CHANNEL_ID)
            } catch (e: SecurityException) {
                Unit
            }
        }
    }
}
