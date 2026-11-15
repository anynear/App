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
import androidx.core.content.ContextCompat
import app.anynear.app.FinderKeepAlive
import app.anynear.app.FinderPause
import app.anynear.i18n.texts

/**
 * Lets a self-started search run for documents finish in the background (ADR 0071 item 9): a foreground
 * service of type `dataSync` for as long as the run takes, with progress and „Anhalten“. The run itself stays
 * in [app.anynear.app.DocFinder]; the service only keeps the process alive and shows what is happening.
 */
class DocFinderService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Keeper.stop?.invoke()
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        try {
            val progress = Keeper.progressNotification(this)
            ServiceCompat.startForeground(this, PROGRESS_ID, progress, type)
            NotificationGroups.summarize(this, NotificationGroups.DOC_FINDER, posted = NotificationGroups.Shown(null, PROGRESS_ID, progress))
        } catch (e: Exception) {
            // Android does not allow the service (e.g. time limit reached): the run pauses in the background as before.
            Keeper.started = false
            Keeper.expired?.invoke()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Android 15 limits `dataSync` to about six hours a day; after that the run pauses as without the service. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Keeper.started = false
        Keeper.expired?.invoke()
        stopSelf()
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        NotificationGroups.summarize(this, NotificationGroups.DOC_FINDER, removed = NotificationGroups.Shown(null, PROGRESS_ID, null))
        super.onDestroy()
    }

    /** Bridge between [app.anynear.app.DocFinder] and the service; set in `Application.onCreate()`. */
    class Keeper(private val context: Context) : FinderKeepAlive {
        override fun begin(stop: () -> Unit, expired: () -> Unit): Boolean {
            Companion.stop = stop
            Companion.expired = expired
            checked = 0
            total = 0
            found = 0
            pause = null
            started = try {
                ContextCompat.startForegroundService(context, Intent(context, DocFinderService::class.java))
                true
            } catch (e: Exception) {
                // Android does not allow the service right now (e.g. at start after an update): no display, run waits.
                false
            }
            return started
        }

        override fun progress(checked: Int, total: Int, found: Int, pause: FinderPause?) {
            Companion.checked = checked
            Companion.total = total
            Companion.found = found
            Companion.pause = pause
            // Only as long as the service runs; otherwise the display would claim „sucht“ while the run waits.
            if (!started) return
            NotificationGroups.notify(context, null, PROGRESS_ID, progressNotification(context))
        }

        override fun end(found: Int, completed: Boolean, visible: Boolean) {
            stop = null
            expired = null
            started = false
            context.stopService(Intent(context, DocFinderService::class.java))
            // Only those who do not see the app right now get the alert; otherwise the result is already in the list.
            if (!completed || visible || found == 0) return
            ensureChannel(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(AppBrand.notificationIcon)
                .setContentTitle(texts.find.foundTitle(found))
                .setContentText(texts.find.foundText)
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .setGroup(NotificationGroups.DOC_FINDER)
                .build()
            NotificationGroups.notify(context, null, FOUND_ID, notification)
        }

        /** New documents found automatically (ADR 0081): one notification, a tap opens the suggestions. */
        override fun foundNew(found: Int) {
            if (found == 0) return
            ensureChannel(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(AppBrand.notificationIcon)
                .setContentTitle(texts.find.newFoundTitle(found))
                .setContentText(texts.find.foundText)
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .setGroup(NotificationGroups.DOC_FINDER)
                .build()
            NotificationGroups.notify(context, null, FOUND_ID, notification)
        }

        companion object {
            @Volatile internal var stop: (() -> Unit)? = null

            @Volatile internal var expired: (() -> Unit)? = null

            @Volatile private var checked = 0

            @Volatile private var total = 0

            @Volatile private var found = 0

            @Volatile private var pause: FinderPause? = null

            /** Is the service running for this run? */
            @Volatile internal var started = false

            internal fun progressNotification(context: Context): Notification {
                val stopIntent = PendingIntent.getService(
                    context,
                    0,
                    Intent(context, DocFinderService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE,
                )
                val text = when (pause) {
                    FinderPause.BATTERY -> texts.find.pausedBattery
                    FinderPause.NO_WIFI -> texts.find.noWifiQuestion
                    else -> if (total > 0) texts.find.progress(checked, total) else texts.find.preparing
                }
                return NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(AppBrand.notificationIcon)
                    .setContentTitle(texts.find.backgroundTitle)
                    .setContentText(text)
                    .setProgress(total, checked, total == 0)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSilent(true)
                    .setContentIntent(openApp(context))
                    .addAction(0, texts.find.stop, stopIntent)
                    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                    // Own group with a summary, apart from the background notification ([NotificationGroups]).
                    .setGroup(NotificationGroups.DOC_FINDER)
                    .build()
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "doc_finder"
        private const val PROGRESS_ID = 41
        private const val FOUND_ID = 42
        private const val ACTION_STOP = "app.anynear.finder.STOP"

        /** Notification tapped: open „Dokumente finden“. */
        const val ACTION_OPEN_FINDER = "app.anynear.finder.OPEN"

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_FINDER).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        /** Quiet channel without sound; the name follows the language (like [MeshConnectionService.ensureChannel]). */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, texts.find.backgroundChannel, NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
    }
}
