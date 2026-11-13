package app.anynear

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import app.anynear.app.CrashReport
import app.anynear.app.CrashRecorder
import app.anynear.app.CrashReports
import app.anynear.app.usagePlatform
import app.anynear.data.platformTextFile

/**
 * Crash reports on Android (ADR 0082): own handler for uncaught exceptions, plus at start native
 * crashes and „App reagiert nicht“ from `ApplicationExitInfo` (from Android 11 on). Nothing is sent here.
 */
object CrashReporting {
    private const val PREFS = "crashes"
    private const val KEY_EXIT_SEEN = "exit_seen"
    private const val MAX_TRACE_BYTES = 512 * 1024

    /** Creates the recorder and puts the handler in front of the previous one. Call at the very start of `onCreate`. */
    fun install(context: Context, version: () -> String?): CrashRecorder {
        val recorder = CrashRecorder(
            file = platformTextFile(CrashRecorder.FILE_NAME),
            platform = usagePlatform,
            build = version,
            os = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            dataDirs = listOfNotNull(context.applicationInfo.dataDir, context.filesDir.parent),
            now = System::currentTimeMillis,
        )
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            recorder.record(error)
            // After that Android ends the app as usual (notice „wurde beendet“, entry in ApplicationExitInfo).
            previous?.uncaughtException(thread, error)
        }
        return recorder
    }

    /**
     * Native crashes and ANRs since the last start. The first time only set the mark, so that nothing from before this
     * version appears as a new crash. Exceptions in Kotlin code (`REASON_CRASH`) are recorded by the handler itself.
     */
    fun collectExits(context: Context, recorder: CrashRecorder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val exits = runCatching {
            context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(context.packageName, 0, 16)
        }.getOrDefault(emptyList())
        val seen = prefs.getLong(KEY_EXIT_SEEN, -1L)
        val newest = exits.maxOfOrNull { it.timestamp } ?: return
        prefs.edit().putLong(KEY_EXIT_SEEN, maxOf(seen, newest)).apply()
        if (seen < 0) return
        exits.filter { it.timestamp > seen && (it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || it.reason == ApplicationExitInfo.REASON_ANR) }
            .sortedBy { it.timestamp }
            .forEach { exit ->
                val anr = exit.reason == ApplicationExitInfo.REASON_ANR
                val kind = if (anr) CrashReport.Kind.NotResponding else CrashReport.Kind.Native
                val name = if (anr) "ANR" else "CRASH_NATIVE"
                // For native crashes Android provides a tombstone as protobuf from 12 on; we do not read it (yet).
                val trace = if (anr) readTrace(exit)?.let(CrashReports::anrMainThread).orEmpty() else ""
                recorder.record(kind, "$name: ${exit.description.orEmpty()}".trimEnd(':', ' '), trace, at = exit.timestamp)
            }
    }

    private fun readTrace(exit: ApplicationExitInfo): String? = runCatching {
        exit.traceInputStream?.use { input ->
            val bytes = ByteArray(MAX_TRACE_BYTES)
            var read = 0
            while (read < bytes.size) {
                val n = input.read(bytes, read, bytes.size - read)
                if (n < 0) break
                read += n
            }
            bytes.decodeToString(0, read)
        }
    }.getOrNull()
}
