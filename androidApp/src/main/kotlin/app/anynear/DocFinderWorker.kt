package app.anynear

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.anynear.app.FinderSchedule
import java.util.concurrent.TimeUnit

/**
 * Find new documents automatically (ADR 0081): once a day, only on the charging cable, the
 * [app.anynear.app.DocFinder] quietly looks over the sources already searched. WorkManager is AndroidX and needs no
 * Google services (F-Droid).
 */
class DocFinderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MainApplication ?: return Result.success()
        val found = app.controller.docFinder?.checkNew() ?: 0
        app.controller.debugLog.add("Dokumente", "Von selbst geschaut: $found neu")
        return Result.success()
    }

    /** Turning the regular look on and off; set in `Application.onCreate()`. */
    class Schedule(private val context: Context) : FinderSchedule {
        override fun set(on: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!on) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<DocFinderWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
                .build()
            // KEEP: an already scheduled run stays as it is; turning on at start changes nothing.
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    private companion object {
        const val NAME = "doc-finder-new"
    }
}
