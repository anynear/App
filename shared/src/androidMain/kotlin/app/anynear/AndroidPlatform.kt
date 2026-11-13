package app.anynear

import android.content.Context
import app.anynear.mesh.initMeshStorage

/** Must be called once in `Application.onCreate()`. */
object AndroidPlatform {
    @Volatile
    internal var context: Context? = null
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        this.context = app
        initMeshStorage(app)
    }

    /**
     * DFU service of the app for updating nRF52 radios (ADR 0056); registered in the app's manifest and set in
     * `Application.onCreate()`. Without it the app does not offer this path.
     */
    @Volatile
    var dfuService: Class<out no.nordicsemi.android.dfu.DfuBaseService>? = null

    /**
     * Keeps a self-started search run for documents alive in the background (ADR 0071 item 9), as the
     * app's foreground service; set in `Application.onCreate()`. Without it the run pauses in the background.
     */
    @Volatile
    var finderKeepAlive: app.anynear.app.FinderKeepAlive? = null

    /** Schedules the regular look for new documents (ADR 0081); set by :androidApp (WorkManager). */
    var finderSchedule: app.anynear.app.FinderSchedule? = null

    internal fun requireContext(): Context =
        checkNotNull(context) { "AndroidPlatform.init(context) wurde nicht aufgerufen." }
}
