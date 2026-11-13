package app.anynear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After a restart of the phone or an app update: start the process so that receiving new
 * messages starts again. The rest is done by [MainApplication] (controller starts, registers the
 * foreground service), as long as Android still allows starting here. A search run for documents that the update
 * interrupted gets its service for the background here (ADR 0071 item 10).
 */
class StartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MainApplication
        app.hooks.setMessageReception(app.controller.receptionWanted)
        app.controller.docFinder?.keepGoingInBackground()
    }
}
