package app.anynear.core

import app.anynear.anmp.core.Conversation as CoreConversation
import app.anynear.anmp.core.CoreEvent
import app.anynear.anmp.core.MemberState
import app.anynear.anmp.core.AnmpCore
import app.anynear.AppConfig
import app.anynear.alerts.AlertLoudness
import app.anynear.alerts.AlertNotification
import app.anynear.alerts.AlertSettingsStore
import app.anynear.alerts.CoreAlertSource
import app.anynear.alerts.inRegion
import app.anynear.alerts.notificationOf
import app.anynear.alerts.notifiedKey
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.RadioAnnouncement
import app.anynear.chats.key
import app.anynear.data.KeyValueStore
import app.anynear.i18n.texts
import app.anynear.mesh.Conversation
import app.anynear.mesh.PeerId
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json

/**
 * Work of the iOS Notification Service Extension after a wake-up (ADR 0009): the ANMP core fetches all spaces
 * once without waiting ([AnmpCore.wakeFetch], own read positions, nothing is sent), from that the text is built.
 * If a second relay wakes for the same message, the same text comes once more ([store], the last few).
 */
class WakeTexts(private val core: AnmpCore, private val store: KeyValueStore) {
    /**
     * Content for the wake-up for the object [objectId] (field `o` of the push message, ANDP §10): preferably exactly
     * this message, otherwise the newest newly found one. `null`: nothing new, the fixed text stays.
     */
    suspend fun contentFor(objectId: String?): WakeNotification? {
        shown().firstOrNull { it.objectId == objectId }?.let { return it }
        // A person with several devices gets one entry per device, waking happens only once (ANMP postPerson).
        // If the wake-up arrives before the entry for this device, retry briefly instead of staying with the fixed text.
        repeat(RETRIES) { attempt ->
            fetchOnce(objectId)?.let { return it }
            if (attempt < RETRIES - 1) delay(RETRY_PAUSE)
        }
        return null
    }

    private suspend fun fetchOnce(objectId: String?): WakeNotification? {
        val found = core.wakeFetch().mapNotNull { event ->
            when (event) {
                is CoreEvent.Message -> notificationOf(event)
                is CoreEvent.Reaction -> notificationOf(event)
                is CoreEvent.Alert -> notificationOf(event)
                else -> null
            }
        }
        if (found.isNotEmpty()) remember(found)
        // An alert does not carry an object of the wake-up: then the loudest, newest alert before everything else.
        val chosen = found.firstOrNull { it.objectId == objectId }
            ?: found.filter { it.alertChannel != null }.maxWithOrNull(compareBy<WakeNotification> { levelRank(it.level) }.thenBy { it.timestampMillis })
            ?: found.maxByOrNull { it.timestampMillis }
        // Only the shown alert counts as notified; the others are reported by the app as soon as it fetches itself.
        chosen?.alertKey?.let(AlertSettingsStore(store)::markNotified)
        return chosen
    }

    /**
     * Alert of a channel (ADR 0062) with level and sound according to the settings on this device. Official ones outside
     * the own district (or as long as it is unknown) and ones already shown do not count. If the channel has
     * notifications off, the extension cannot swallow the wake-up: then silent ([WakeLevel.PASSIVE]) and without sound.
     */
    private fun notificationOf(e: CoreEvent.Alert): WakeNotification? {
        if (e.history || e.quiet) return null
        val channel = core.channel(e.item.channelId)?.let(CoreAlertSource::channelOf) ?: return null
        val alert = CoreAlertSource.alertOf(e.item)
        val settings = AlertSettingsStore(store)
        if (channel.curated && !inRegion(alert, settings.region()?.kreis)) return null
        val key = notifiedKey(alert)
        if (settings.notified(key)) return null
        val channelSettings = settings.settingsFor(channel)
        val n = notificationOf(alert, channel, channelSettings.copy(notify = true)) ?: return null
        if (!channelSettings.notify) return wakeOf(n, key, sound = null, level = WakeLevel.PASSIVE)
        return wakeOf(n, key, sound = n.sound.file.takeIf { n.loudness == AlertLoudness.Loud }, level = wakeLevelOf(n.loudness))
    }

    private fun wakeOf(n: AlertNotification, key: String, sound: String?, level: String) = WakeNotification(
        objectId = "alert:$key",
        key = n.key,
        title = n.title,
        body = n.text,
        timestampMillis = n.timestampMillis,
        alertChannel = n.channelId,
        sound = sound,
        level = level,
        alertKey = key,
    )

    private fun levelRank(level: String?) = when (level) {
        WakeLevel.CRITICAL, WakeLevel.TIME_SENSITIVE -> 3
        WakeLevel.ACTIVE -> 2
        else -> 1
    }

    private fun notificationOf(m: CoreEvent.Message): WakeNotification? {
        val known = (m.conversation as? CoreConversation.Circle)?.let { core.membersOf(it.circleId) }.orEmpty() + core.membersOf(MemberState.CONTACTS)
        val sender = known.lastOrNull { it.deviceId == m.fromDevice }?.name?.ifBlank { null } ?: m.fromName
        val text = m.text.ifEmpty { RadioAnnouncement.radioText("", m.attachments.mapNotNull { AttachmentKind.fromWire(it.type) }) }
        return when (val c = m.conversation) {
            is CoreConversation.Circle -> {
                val circle = core.circle(c.circleId) ?: return null
                WakeNotification(m.objectId, Conversation.Circle(c.circleId).key(), circle.name, "$sender: $text", m.timestampMillis)
            }
            is CoreConversation.Direct -> WakeNotification(m.objectId, Conversation.Direct(PeerId.device(c.personId)).key(), sender, text, m.timestampMillis)
        }
    }

    /** Only reactions of others to own messages (ADR 0018); the extension does not know the text of the message. */
    private fun notificationOf(r: CoreEvent.Reaction): WakeNotification? {
        if (r.emoji.isEmpty() || r.targetTag != core.identity.value?.tag) return null
        val known = (r.conversation as? CoreConversation.Circle)?.let { core.membersOf(it.circleId) }.orEmpty() + core.membersOf(MemberState.CONTACTS)
        val sender = known.lastOrNull { it.deviceId == r.fromDevice }?.name?.ifBlank { null } ?: texts.status.someone
        val text = texts.chat.reactedToYours(r.emoji)
        return when (val c = r.conversation) {
            is CoreConversation.Circle -> {
                val circle = core.circle(c.circleId) ?: return null
                WakeNotification(r.objectId, Conversation.Circle(c.circleId).key(), circle.name, "$sender: $text", r.timestampMillis)
            }
            is CoreConversation.Direct -> WakeNotification(r.objectId, Conversation.Direct(PeerId.device(c.personId)).key(), sender, text, r.timestampMillis)
        }
    }

    private fun shown(): List<WakeNotification> =
        store.getString(SHOWN_KEY)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    /** Only briefly and only the last few: the history itself stays in the storage of the app. */
    private fun remember(found: List<WakeNotification>) {
        val cutoff = found.maxOf { it.timestampMillis } - SHOWN_FOR_MILLIS
        val list = (shown() + found).distinctBy { it.objectId }.filter { it.timestampMillis >= cutoff }.takeLast(MAX_SHOWN)
        store.putString(SHOWN_KEY, json.encodeToString(serializer, list))
    }

    private companion object {
        const val SHOWN_KEY = "app.wake.shown"
        const val MAX_SHOWN = 20
        const val SHOWN_FOR_MILLIS = 24 * 60 * 60 * 1000L
        /** Fetching and retrying, together well below the deadline of the extension (20 s). */
        const val RETRIES = 4
        val RETRY_PAUSE = 2.seconds
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(WakeNotification.serializer())
    }
}

/** Level of the notification on iOS for an alert: alarm "Time Sensitive" (with approval from Apple "Critical"). */
fun wakeLevelOf(loudness: AlertLoudness): String = when (loudness) {
    AlertLoudness.Normal -> WakeLevel.ACTIVE
    AlertLoudness.Loud -> if (AppConfig.CRITICAL_ALERTS) WakeLevel.CRITICAL else WakeLevel.TIME_SENSITIVE
}
