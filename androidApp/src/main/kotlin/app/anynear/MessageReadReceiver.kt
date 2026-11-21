package app.anynear

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * „Gelesen“ in a message notification, on the phone, on a watch or in Android Auto: the conversation counts as read
 * without opening the app, the senders get a read receipt
 * ([app.anynear.app.AppController.markReadFromNotification]).
 */
class MessageReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_CONVERSATION) ?: return
        val app = context.applicationContext as MainApplication
        // A broadcast from a notification may run up to a minute in the background; marking needs only seconds.
        val pending = goAsync()
        scope.launch {
            try {
                app.controller.markReadFromNotification(key)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_CONVERSATION = "conversation"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Explicit, so that only this receiver gets it; one per conversation (data differs). */
        fun intent(context: Context, key: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            Intent(context, MessageReadReceiver::class.java)
                .setData(Uri.parse("read:" + Uri.encode(key)))
                .putExtra(EXTRA_CONVERSATION, key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
