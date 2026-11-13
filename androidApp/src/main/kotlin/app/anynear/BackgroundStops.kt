package app.anynear

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/**
 * Remembers whether something ended anynear in the background although the foreground service was running. That is
 * done by some manufacturers' own power-saving features (Samsung „Schlafende Apps“, Xiaomi without „Autostart“ etc.),
 * which Android itself does not report: battery optimization is then set to off, messages still do not arrive.
 * Android only provides the reasons of the last process exits from Android 11 on.
 */
class BackgroundStops(private val context: Context) {
    private val prefs = context.getSharedPreferences("background", Context.MODE_PRIVATE)

    /** Was anynear ended since the user last opened the setting (at most [WINDOW_MILLIS] back)? */
    fun stoppedSinceHandled(now: Long = System.currentTimeMillis()): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val since = maxOf(prefs.getLong(KEY_HANDLED, 0L), now - WINDOW_MILLIS)
        val exits = runCatching {
            context.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS)
        }.getOrDefault(emptyList())
        return exits.any { it.timestamp > since && isUnexpectedStop(it.reason, it.importance) }
    }

    /**
     * For the log at start: how the previous process ended, e.g. „Letztes Ende vor 42 min: OTHER (…),
     * Wichtigkeit 125, zählt“. `null` before Android 11 or without an entry.
     */
    fun lastExit(now: Long = System.currentTimeMillis()): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val exit = runCatching {
            context.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()
        }.getOrNull() ?: return null
        val minutes = (now - exit.timestamp) / 60_000
        val counts = if (isUnexpectedStop(exit.reason, exit.importance)) ", zählt" else ""
        return "Letztes Ende vor $minutes min: ${reasonName(exit.reason)} (${exit.description.orEmpty()}), Wichtigkeit ${exit.importance}$counts"
    }

    /** The user went to the setting: previous exits no longer count, only a new one. */
    fun markHandled(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_HANDLED, now).apply()
    }

    companion object {
        private const val KEY_HANDLED = "stops_handled_at"
        private const val MAX_EXITS = 16
        private const val WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000

        /**
         * Only exits while the foreground service was running (importance at most "foreground service"), and only
         * by someone else's hand: manufacturer services report that as "other reason" or as a signal. Not counted:
         * crash, own restart, update, „Beenden erzwingen“, revoked permission, low memory, and
         * cached processes without a service, which Android cleans up even without the manufacturer.
         */
        private fun reasonName(reason: Int): String = when (reason) {
            ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
            ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
            ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
            ApplicationExitInfo.REASON_OTHER -> "OTHER"
            else -> "Grund $reason"
        }

        fun isUnexpectedStop(reason: Int, importance: Int): Boolean =
            importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE &&
                (reason == ApplicationExitInfo.REASON_OTHER || reason == ApplicationExitInfo.REASON_SIGNALED)
    }
}
