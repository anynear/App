package app.anynear

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * „Antworten“ in a message notification, on the phone or on a watch (Wear OS, Garmin and others that pass replies on via
 * the notification): sends the typed or picked text without opening the app, the same way as from the chat
 * ([app.anynear.app.AppController.replyFromNotification]).
 */
class MessageReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_CONVERSATION) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString().orEmpty()
        val app = context.applicationContext as MainApplication
        // A broadcast from a notification may run up to a minute in the background; the reply needs only seconds.
        val pending = goAsync()
        scope.launch {
            try {
                app.controller.replyFromNotification(key, text)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Key of the text in the [RemoteInput] results. */
        const val KEY_TEXT = "reply"
        private const val EXTRA_CONVERSATION = "conversation"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Mutable, so that the system can add the text; explicit, so that only this receiver gets it. */
        fun intent(context: Context, key: String): PendingIntent {
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getBroadcast(
                context,
                key.hashCode(),
                Intent(context, MessageReplyReceiver::class.java)
                    .setData(Uri.parse("reply:" + Uri.encode(key)))
                    .putExtra(EXTRA_CONVERSATION, key),
                mutable or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
