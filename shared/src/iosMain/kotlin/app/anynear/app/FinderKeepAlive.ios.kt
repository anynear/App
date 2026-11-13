package app.anynear.app

import app.anynear.i18n.texts
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.BackgroundTasks.BGContinuedProcessingTask
import platform.BackgroundTasks.BGContinuedProcessingTaskRequest
import platform.BackgroundTasks.BGContinuedProcessingTaskRequestSubmissionStrategy
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSProcessInfo
import platform.BackgroundTasks.BGProcessingTask
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.concurrent.Volatile

/*
 * iOS (ADR 0071 item 9): from iOS 26 a `BGContinuedProcessingTask`. iOS shows the progress itself and can end the
 * task at any time; then the run pauses in the background as before. Before iOS 26 no background.
 */

internal actual fun platformFinderKeepAlive(): FinderKeepAlive = IosFinderKeepAlive

/** Must match `BGTaskSchedulerPermittedIdentifiers` in `Info.plist` and begin with the bundle ID. */
private const val TASK_ID = "app.anynear.docfinder"

@OptIn(ExperimentalForeignApi::class)
private object IosFinderKeepAlive : FinderKeepAlive {
    @Volatile private var registered = false

    @Volatile private var task: BGContinuedProcessingTask? = null

    @Volatile private var stop: (() -> Unit)? = null

    @Volatile private var expired: (() -> Unit)? = null

    @Volatile private var finishedEarly = false

    private fun supported(): Boolean = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }

    override fun begin(stop: () -> Unit, expired: () -> Unit): Boolean {
        if (!supported()) return false
        this.stop = stop
        this.expired = expired
        finishedEarly = false
        if (!registered) {
            // Once per process; iOS rejects registering the same identifier a second time.
            registered = BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(TASK_ID, usingQueue = null) { started ->
                val continued = started as? BGContinuedProcessingTask ?: return@registerForTaskWithIdentifier
                if (finishedEarly) {
                    continued.setTaskCompletedWithSuccess(true)
                    return@registerForTaskWithIdentifier
                }
                task = continued
                continued.expirationHandler = {
                    // iOS ends the task: the run pauses in the background and continues on the next opening.
                    task = null
                    this.expired?.invoke()
                    continued.setTaskCompletedWithSuccess(false)
                }
            }
            if (!registered) return false
        }
        val request = BGContinuedProcessingTaskRequest(TASK_ID, texts.find.backgroundTitle, texts.find.preparing)
        // At once or not at all: a run that only starts later does not help.
        request.strategy = BGContinuedProcessingTaskRequestSubmissionStrategy.BGContinuedProcessingTaskRequestSubmissionStrategyFail
        return BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
    }

    override fun progress(checked: Int, total: Int, found: Int, pause: FinderPause?) {
        val current = task ?: return
        val subtitle = when (pause) {
            FinderPause.BATTERY -> texts.find.pausedBattery
            FinderPause.NO_WIFI -> texts.find.noWifiQuestion
            else -> if (total > 0) texts.find.progress(checked, total) else texts.find.preparing
        }
        dispatch_async(dispatch_get_main_queue()) {
            current.progress.totalUnitCount = total.toLong()
            current.progress.completedUnitCount = checked.toLong()
            current.updateTitle(texts.find.backgroundTitle, subtitle)
        }
    }

    /** New documents found on its own (ADR 0081): one notification, a tap opens the suggestions. */
    override fun foundNew(found: Int) {
        if (found == 0) return
        val content = UNMutableNotificationContent()
        content.setTitle(texts.find.newFoundTitle(found))
        content.setBody(texts.find.foundText)
        content.setUserInfo(mapOf<Any?, Any?>(FINDER_INFO_KEY to "1"))
        val request = UNNotificationRequest.requestWithIdentifier(NEW_FOUND_ID, content, null)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
    }

    override fun end(found: Int, completed: Boolean, visible: Boolean) {
        stop = null
        expired = null
        val current = task
        task = null
        if (current == null) {
            // The task has not begun yet: as soon as it starts, end it again right away.
            finishedEarly = true
            return
        }
        dispatch_async(dispatch_get_main_queue()) {
            if (completed) current.updateTitle(texts.find.foundTitle(found), texts.find.foundText)
            current.setTaskCompletedWithSuccess(completed)
        }
    }
}

/** Marks the notification „neue Dokumente gefunden“; the Swift shell then opens the suggestions. */
const val FINDER_INFO_KEY = "finder"
private const val NEW_FOUND_ID = "doc-finder-new"

/** Must match `BGTaskSchedulerPermittedIdentifiers` in `Info.plist` (ADR 0081). */
private const val NEW_TASK_ID = "app.anynear.docfinder.new"

/** Not earlier than after this many seconds; iOS decides when it really runs (mostly at night on the charger). */
private const val NEW_TASK_GAP = 20.0 * 60 * 60

internal actual fun platformFinderSchedule(): FinderSchedule = IosFinderSchedule

/** iOS (ADR 0081): `BGProcessingTask` with power; schedule and unschedule. */
@OptIn(ExperimentalForeignApi::class)
internal object IosFinderSchedule : FinderSchedule {
    override fun set(on: Boolean) {
        val scheduler = BGTaskScheduler.sharedScheduler
        if (!on) {
            scheduler.cancelTaskRequestWithIdentifier(NEW_TASK_ID)
            return
        }
        val request = BGProcessingTaskRequest(NEW_TASK_ID)
        request.requiresExternalPower = true
        request.requiresNetworkConnectivity = false
        request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(NEW_TASK_GAP)
        scheduler.submitTaskRequest(request, null)
    }

    /**
     * Register on app start before it has finished loading (iOS requires that). [check] looks for new items and then
     * calls `done`; if time runs out, iOS aborts it via [cancel]. Afterwards the next time is scheduled.
     */
    fun register(check: (done: () -> Unit) -> Unit, cancel: () -> Unit) {
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(NEW_TASK_ID, usingQueue = null) { started ->
            val task = started as? BGProcessingTask ?: return@registerForTaskWithIdentifier
            task.expirationHandler = {
                cancel()
                task.setTaskCompletedWithSuccess(false)
            }
            // Schedule the next time right away; when switched off the DocFinder unschedules by itself.
            set(true)
            check { task.setTaskCompletedWithSuccess(true) }
        }
    }
}
