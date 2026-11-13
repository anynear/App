package app.anynear.core

/**
 * Token of Apple's push service (iOS), as hex. The app registers it via the ANMP core as a watch at the relays,
 * which then wake the iPhone (ADR 0009, ADR 0035 §5).
 * @property topic Bundle ID of the app; the relay only wakes apps it knows.
 * @property sandbox Development build: Apple delivers via the sandbox address.
 */
data class PushToken(val token: String, val topic: String, val sandbox: Boolean)

/** Content of a wake-up on iOS, built on the iPhone (Notification Service Extension). */
@kotlinx.serialization.Serializable
data class WakeNotification(
    /** ANDP object of the message. */
    val objectId: String,
    /** Conversation, see [app.anynear.chats.key]. */
    val key: String,
    val title: String,
    val body: String,
    val timestampMillis: Long,
    /** Alert (ADR 0062): channel, sound without extension (e.g. `alarm`) and level ([WakeLevel]); `null` for messages. */
    val alertChannel: String? = null,
    val sound: String? = null,
    val level: String? = null,
    /** Alert: key for "already notified" ([app.anynear.alerts.notifiedKey]). */
    val alertKey: String? = null,
)

/** Level of a notification on iOS, as the extension sets it (`interruptionLevel`). */
object WakeLevel {
    const val PASSIVE = "passive"
    const val ACTIVE = "active"
    const val TIME_SENSITIVE = "timeSensitive"
    const val CRITICAL = "critical"
}
