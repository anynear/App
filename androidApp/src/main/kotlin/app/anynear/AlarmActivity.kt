package app.anynear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.anynear.alerts.NotificationAction
import app.anynear.ui.AlarmScreen

/**
 * Alarm as full screen over the lock screen (ADR 0062 §6), started via the full-screen intent of the
 * notification. „Ton aus“ stops the sound, „Öffnen“ leads to the channel (after unlocking). A button of the app
 * („Alles gut“, „Ich schaue nach“, ADR 0083) runs like the one in the notification, without unlocking.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Over the lock screen and with the screen turned on: showWhenLocked/turnScreenOn in the manifest.
        super.onCreate(savedInstanceState)
        show(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    private fun show(intent: Intent) {
        val channelId = intent.getStringExtra(AlertNotifier.EXTRA_CHANNEL)
        val key = intent.getStringExtra(AlertNotifier.EXTRA_KEY)
        val actionId = intent.getStringExtra(EXTRA_ACTION_ID)
        val action = actionId?.let { NotificationAction(it, intent.getStringExtra(EXTRA_ACTION_LABEL).orEmpty()) }
        setContent {
            AlarmScreen(
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
                channel = intent.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty(),
                onSilence = {
                    silence(key)
                    finish()
                },
                onOpen = {
                    silence(key)
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .setAction(AlertNotifier.ACTION_OPEN_ALERTS)
                            .putExtra(AlertNotifier.EXTRA_CHANNEL, channelId)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                    finish()
                },
                action = action,
                onAction = {
                    // Same way as the button in the notification: stops the sound, removes the notification, runs it.
                    if (actionId != null) runCatching { LifeSignActionReceiver.intent(this, actionId, key.orEmpty()).send() }
                    finish()
                },
            )
        }
    }

    private fun silence(key: String?) {
        AlarmSound.stop()
        key?.let { AlertNotifier(this).cancel(it) }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_CHANNEL_NAME = "channelName"
        const val EXTRA_ACTION_ID = "actionId"
        const val EXTRA_ACTION_LABEL = "actionLabel"
    }
}
