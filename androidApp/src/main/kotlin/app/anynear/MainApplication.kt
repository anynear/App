package app.anynear

import android.app.Application
import android.util.Log
import app.anynear.app.AppController
import app.anynear.app.AppLog
import app.anynear.app.LogLevel
import app.anynear.app.createAppController

class MainApplication : Application() {
    lateinit var hooks: AndroidPlatformHooks
        private set
    lateinit var controller: AppController
        private set

    override fun onCreate() {
        super.onCreate()
        AndroidPlatform.init(this)
        // Crash reports first, so that a crash at start is included too (ADR 0082).
        val crashes = CrashReporting.install(this) { if (::hooks.isInitialized) hooks.appVersion else null }
        AndroidPlatform.dfuService = FirmwareDfuService::class.java
        // Let the search run for documents finish in the background (ADR 0071 item 9).
        AndroidPlatform.finderKeepAlive = DocFinderService.Keeper(this)
        // Find new documents automatically (ADR 0081), only if turned on.
        AndroidPlatform.finderSchedule = DocFinderWorker.Schedule(this)
        hooks = AndroidPlatformHooks(this)
        AppBrand.restoreLauncher(this)
        hooks.refreshNotificationChannels()
        val logSink = AppLog { level, tag, message, cause ->
            val priority = when (level) {
                LogLevel.ERROR -> Log.ERROR
                LogLevel.WARN -> Log.WARN
                LogLevel.INFO -> Log.INFO
                LogLevel.DEBUG -> Log.DEBUG
            }
            Log.println(priority, "App/$tag", message + (cause?.let { "\n" + Log.getStackTraceString(it) } ?: ""))
        }
        controller = createAppController(hooks, logSink, crashes)
        hooks.updater.log = { controller.debugLog.add("Update", it) }
        hooks.log = { controller.debugLog.add("Empfang", it) }
        // Why the previous process ended (manufacturer, update, „Beenden erzwingen“), to troubleshoot without cable.
        BackgroundStops(this).lastExit()?.let { controller.debugLog.add("Start", it) }
        CrashReporting.collectExits(this, crashes)
        // Until an Activity becomes visible, the app runs in the background (e.g. after a restart of the phone).
        controller.setInForeground(false)
        controller.setFocused(false)
        controller.start()
        ShareShortcuts.start(this, controller)
    }
}
