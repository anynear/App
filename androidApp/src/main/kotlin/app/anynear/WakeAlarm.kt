package app.anynear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

/**
 * Wakes the phone briefly at regular intervals so that receiving does not stall: as long as the phone sleeps, Android
 * pauses timers, even in the foreground service. A fetch that sits out its wait after a connection drop would
 * otherwise only get its turn again when the app is opened. An alarm with „AllowWhileIdle“ may also fire in deep sleep
 * (Doze), there at most about every 9 minutes; no permission for exact alarms is needed for that. After an exit
 * caused by the system it also starts the process again, not after „Beenden erzwingen“.
 */
object WakeAlarm {
    private const val INTERVAL_MILLIS = 15L * 60 * 1000

    /** The phone stays awake this long after waking: fetch without waiting, decrypt, notification. */
    const val AWAKE_MILLIS = 20_000L

    fun schedule(context: Context) {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + INTERVAL_MILLIS,
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context, 0, Intent(context, WakeReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
}

/** The alarm has fired: stay awake, restart all fetches, set the next alarm. */
class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MainApplication
        val lock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "anynear:wake")
            .apply { acquire(WakeAlarm.AWAKE_MILLIS) }
        // goAsync: without a running service Android otherwise stops the process right after onReceive.
        val pending = goAsync()
        WakeAlarm.schedule(context)
        app.hooks.log("Wecker, Dienst " + if (MeshConnectionService.running != null) "läuft" else "läuft nicht")
        // If the process was ended, the alarm restarts it; then the service too, as far as Android allows.
        if (app.controller.receptionWanted) app.hooks.setMessageReception(true)
        app.controller.wakeUp()
        Handler(Looper.getMainLooper()).postDelayed({
            pending.finish()
            if (lock.isHeld) lock.release()
        }, WakeAlarm.AWAKE_MILLIS - 1_000)
    }
}
