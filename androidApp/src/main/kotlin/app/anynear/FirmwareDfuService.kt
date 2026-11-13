package app.anynear

import android.app.Activity
import no.nordicsemi.android.dfu.DfuBaseService

/**
 * Service of the Nordic DFU library for updating nRF52 radios (ADR 0056). Runs as a foreground
 * service as long as the software is being transferred; a tap on the notification opens the app.
 */
class FirmwareDfuService : DfuBaseService() {
    override fun getNotificationTarget(): Class<out Activity> = MainActivity::class.java

    override fun isDebug(): Boolean = BuildConfig.DEBUG
}
