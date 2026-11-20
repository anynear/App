package app.anynear

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.anynear.location.Place
import kotlin.math.roundToInt

/**
 * Runs while the person shares where it is or calls its helpers (ADR 0121 §13, §14): a foreground service of the type
 * „location“ with a notification that says so, so that the phone keeps locating when the app is not on the screen.
 * Nothing runs hidden: the notification stays for as long as a place leaves the phone, and it ends with the sharing.
 *
 * Started while the app is open, the permission „Beim Verwenden der App“ is enough. Started by the system (after a
 * restart of the phone, for the levels that share all the time) it needs „Immer“; without it the start fails and the
 * sharing continues the next time the app is opened.
 */
class LocationShareService : Service() {
    private var listening = false

    private val listener = LocationListener { location -> (application as? MainApplication)?.hooks?.onPlace(placeOf(location)) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: lastText
        lastText = text
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(text), type)
            listen()
        } catch (e: SecurityException) {
            // No permission for a start from the background: the sharing continues when the app is opened.
            log("Standort im Hintergrund nicht möglich: ${e.message.orEmpty()}")
            stopSelf()
        } catch (e: IllegalStateException) {
            log("Standort im Hintergrund nicht gestartet: ${e.message.orEmpty()}")
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (listening) runCatching { getSystemService(LocationManager::class.java)?.removeUpdates(listener) }
        listening = false
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun listen() {
        if (listening) return
        val manager = getSystemService(LocationManager::class.java) ?: return
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        // Without Google's services: the providers of the system. „fused“ where the system has it, otherwise GPS and network.
        val wanted = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { it in providers }
        for (provider in if (LocationManager.FUSED_PROVIDER in wanted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(LocationManager.FUSED_PROVIDER) else wanted) {
            runCatching { manager.requestLocationUpdates(provider, MIN_MILLIS, MIN_METRES, listener, mainLooper) }
                .onSuccess { listening = true }
        }
        log("Standort wird geteilt" + if (listening) "" else ", aber keine Ortungsquelle")
    }

    private fun placeOf(location: Location): Place {
        // The time from the clock since the start of the phone, as in [ApproximateLocation]: the time of day of a fix can be off.
        val age = ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000).coerceAtLeast(0)
        return Place(location.latitude, location.longitude, System.currentTimeMillis() - age, if (location.hasAccuracy()) location.accuracy.roundToInt() else null)
    }

    private fun notification(text: String): Notification {
        MeshConnectionService.ensureChannel(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, MeshConnectionService.CHANNEL_ID)
            .setSmallIcon(AppBrand.notificationIcon)
            .setContentTitle(AppBrand.label(this))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun log(message: String) = (application as? MainApplication)?.hooks?.log?.invoke(message)

    companion object {
        private const val NOTIFICATION_ID = 7
        private const val EXTRA_TEXT = "text"
        private const val MIN_MILLIS = 60_000L
        private const val MIN_METRES = 30f
        private var lastText = ""

        /** Starts the service with [text] in its notification, or updates the text. `false` if the system refused. */
        fun start(context: Context, text: String): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, LocationShareService::class.java).putExtra(EXTRA_TEXT, text))
            true
        } catch (e: IllegalStateException) {
            false
        } catch (e: SecurityException) {
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationShareService::class.java))
        }
    }
}
