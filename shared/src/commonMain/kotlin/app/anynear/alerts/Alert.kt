package app.anynear.alerts

import app.anynear.anmp.AnmpAttachment
import kotlinx.serialization.Serializable

/**
 * Alerts and channels (ADR 0062): models of the app. The ANMP core verifies, decrypts and deduplicates the alerts;
 * [AlertSource] maps its types onto these so that UI, notifications and tests only know these.
 */

/** Priority of an alert, as in the packet (1, 2, 3). */
@Serializable
enum class AlertPriority(val wire: Int) {
    Info(1),

    /** For official sources “Warnung”, for own and shared channels “Wichtig” (ADR 0062 §7). */
    Warning(2),
    Alarm(3),
    ;

    companion object {
        fun of(wire: Int): AlertPriority = entries.lastOrNull { wire >= it.wire } ?: Info
    }
}

/** State of an alert in the history. */
enum class AlertStatus { Active, Ended, Expired }

/** A channel (ADR 0062 §1): own, shared or curated. */
data class AlertChannel(
    /** space of the channel. */
    val id: String,
    val name: String,
    /** This device is the owner: may send, share, create addresses for devices. */
    val owner: Boolean,
    /** Curated channel of anynear, e.g. official warnings (NINA). */
    val curated: Boolean = false,
    /** Addresses for devices, only at the owner. */
    val ingress: List<AlertIngress> = emptyList(),
    /** Who has subscribed and reported in, only at the owner (ADR 0062 §11), the oldest first. */
    val subscribers: List<AlertSubscriber> = emptyList(),
    /** Subscribed, and the owner sees that we are in (ADR 0062 §11). */
    val seenByOwner: Boolean = false,
)

/** Someone who has subscribed to an own channel (ADR 0062 §11): device, name, since when. */
data class AlertSubscriber(val deviceId: String, val name: String, val sinceMillis: Long)

/** Address for devices (ADR 0062 §4): URL of the HTTP ingress with secret token. */
data class AlertIngress(val id: String, val label: String, val url: String, val createdMillis: Long)

/** An alert as the core delivers it (verified, deduplicated), times in milliseconds. */
data class Alert(
    val channelId: String,
    /** ID as hex (8 bytes). */
    val id: String,
    val timestampMillis: Long,
    val priority: AlertPriority,
    val title: String?,
    val text: String,
    /** Context URL (http/https only), opened on tap. */
    val url: String? = null,
    val untilMillis: Long = timestampMillis + DEFAULT_VALIDITY_MILLIS,
    /** ID of the alert that this one replaces or gives the all-clear for. */
    val replaces: String? = null,
    /** All-clear for or cancellation of [replaces]. */
    val ended: Boolean = false,
    /** Display name of the source, e.g. “NINA (BBK)”. */
    val source: String? = null,
    /** Regional key prefixes, only for curated channels (e.g. `06` Hesse, `06412` Frankfurt). */
    val area: List<String> = emptyList(),
    val attachments: List<AnmpAttachment> = emptyList(),
    /** Sender as hex. */
    val sender: String = "",
    /** From the core: replaced by an all-clear. The app computes “Abgelaufen” itself ([statusAt]). */
    val endedByOther: Boolean = false,
) {
    companion object {
        /** Without “gültig bis” an alert counts as active for 24 hours (ADR 0062 §3). */
        const val DEFAULT_VALIDITY_MILLIS = 24 * 60 * 60 * 1000L
    }
}

/** Result of [AlertSource.join]. */
sealed interface ChannelJoinResult {
    data class Joined(val channel: AlertChannel) : ChannelJoinResult
    data class AlreadyJoined(val channel: AlertChannel) : ChannelJoinResult
    data object ForeignNetwork : ChannelJoinResult
    data object Invalid : ChannelJoinResult
}

/** What an invitation link to a channel reveals before one subscribes to it. */
data class ChannelInvite(val channelId: String, val name: String, val curated: Boolean, val networkId: String, val seenByOwner: Boolean = false)

/**
 * Whether and how a channel notifies (ADR 0062 §5). Lives only on this device. Stored settings from before
 * [notify] read unchanged: [loudFrom] already was “from when on loud”, notifications are then on.
 */
@Serializable
data class ChannelSettings(
    /** Every alert of the channel notifies; off: alerts only appear in the history. */
    val notify: Boolean = true,
    /** From this priority on it rings loudly, even when muted (alarm path); `null`: never loud. */
    val loudFrom: AlertPriority? = AlertPriority.Alarm,
    val sound: AlertSound = AlertSound.Alarm,
)

/**
 * Sounds that the app ships with (iOS only allows bundled ones). [file] is the name of the file without extension:
 * Android `res/raw/<file>.wav`, iOS `<file>.caf`; the relay also sets `alarm.caf` in the wake-up
 * (implementation plan §3).
 */
@Serializable
enum class AlertSound(val file: String) {
    Alarm("alarm"),

    /** Separate sound for official warnings so that one knows without looking where the alarm comes from. */
    Official("amtlich"),
    Hint("hinweis"),

    /**
     * Question „Alles gut?“ for the own check-in (ADR 0083): friendly and clearly different from the alarm, so that
     * nobody is startled who is watching someone else at the same time. Not selectable for channels.
     */
    Question("frage"),

    /**
     * A person calls its helpers (ADR 0083 §2, ADR 0121 §14): urgent and unlike every other sound, so that one knows
     * without looking that a person calls. Not selectable for channels.
     */
    Sos("sos"),

    /** A calmer warning for own and shared channels, next to [Alarm]. Not for curated channels. */
    Warning("warnung"),
    ;

    companion object {
        /** Default: curated channels “Amtlich”, own and shared ones “Alarm”. */
        fun defaultFor(curated: Boolean) = if (curated) Official else Alarm

        /**
         * The sounds a person may choose for a channel (19.11.2026): the official one only for curated channels,
         * the calmer warning only for own and shared ones; [Sos] and [Question] for no channel.
         */
        fun choicesFor(curated: Boolean): List<AlertSound> = if (curated) listOf(Official, Alarm, Hint) else listOf(Alarm, Warning, Hint)

        /** [chosen] if the channel may have it, otherwise its default, e.g. an own channel that was set to „Amtlich“ before. */
        fun allowedFor(curated: Boolean, chosen: AlertSound): AlertSound = chosen.takeIf { it in choicesFor(curated) } ?: defaultFor(curated)
    }
}

/** How an alert appears on this device when the channel notifies. */
enum class AlertLoudness {
    /** Notification with the normal sound, mute and “Nicht stören” apply (like ntfy, default). */
    Normal,

    /** Alarm: loud, through mute and “Nicht stören”, full screen on the lock screen. */
    Loud,
}

/**
 * Content of a notification for an alert, built on the device. [test]: test alarm, belongs to no real
 * entry in the history.
 */
data class AlertNotification(
    /** One notification per alert; an all-clear replaces that of the replaced alert ([replacesKey]). */
    val key: String,
    val channelId: String,
    val channelName: String,
    val title: String,
    val text: String,
    val priority: AlertPriority,
    val loudness: AlertLoudness,
    val sound: AlertSound,
    val timestampMillis: Long,
    val replacesKey: String? = null,
    val test: Boolean = false,
    /** Buttons in the notification, e.g. „Alles gut“ for a check-in (ADR 0083); Android runs them without the app. */
    val actions: List<NotificationAction> = emptyList(),
)

/** Button in a notification: [id] goes back to the app ([app.anynear.lifesigns.LifeSigns.onAction]). */
data class NotificationAction(val id: String, val label: String)

/** What stands in the way of an alarm on this device; the test alarm lists it (ADR 0062 §6). */
enum class AlarmProblem {
    /** Notifications for the app are off. */
    NotificationsOff,

    /** Android: no access to “Nicht stören”, alarms then do not get through. */
    DoNotDisturbAccess,

    /** Android: battery optimisation puts the app to sleep. */
    BatteryOptimized,

    /** Android 14+: full screen on the lock screen is not allowed. */
    FullScreenNotAllowed,

    /** iOS: “Dringende Mitteilungen” (Time Sensitive) are off. */
    TimeSensitiveOff,

    /** iOS: mute switch and Focus cannot be overridden without approval from Apple. Not an error, just honest. */
    SilentSwitch,
}
