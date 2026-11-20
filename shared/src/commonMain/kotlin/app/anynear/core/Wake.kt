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
import app.anynear.lifesigns.lifeSignWake
import app.anynear.mesh.Conversation
import app.anynear.andp.B64
import kotlin.time.Clock
import app.anynear.mesh.PeerId
import app.anynear.people.ContactNames
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json

/**
 * Result of a wake-up: [found] is the notification to show; without it, [nothingToShow] says that the fetch was
 * complete and found nothing for this device. `false` with nothing found: no answer or no time, a message may wait.
 */
class WakeOutcome(val found: WakeNotification?, val nothingToShow: Boolean)

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
    suspend fun contentFor(objectId: String?): WakeNotification? = outcomeFor(objectId).found

    /**
     * Like [contentFor], and says whether there was nothing to show ([WakeOutcome.nothingToShow]): every space
     * answered and nothing new for this device (an own message from another device, an alert outside the region or
     * already shown, a duplicate wake-up of another holder). Then the extension may swallow the wake-up (ADR 0117).
     */
    suspend fun outcomeFor(objectId: String?): WakeOutcome {
        var complete = false
        // A person with several devices gets one entry per device, waking happens only once (ANMP postPerson).
        // If the wake-up arrives before the entry for this device, retry briefly instead of staying with the fixed text.
        repeat(RETRIES) { attempt ->
            // Each holder of the space wakes for the same object (up to three, same collapse-id). The extension may run
            // for them at the same time: what a parallel run has shown in the meantime counts as found, otherwise this
            // run would find nothing and its fixed text would replace the right one.
            shown().firstOrNull { it.objectId == objectId }?.let { return WakeOutcome(it, nothingToShow = false) }
            val (found, done) = fetchOnce(objectId)
            found?.let { return WakeOutcome(it, nothingToShow = false) }
            complete = done
            if (attempt < RETRIES - 1) delay(RETRY_PAUSE)
        }
        shown().firstOrNull { it.objectId == objectId }?.let { return WakeOutcome(it, nothingToShow = false) }
        return WakeOutcome(null, nothingToShow = complete)
    }

    private suspend fun fetchOnce(objectId: String?): Pair<WakeNotification?, Boolean> {
        val result = core.wakeFetchResult()
        val found = result.events.mapNotNull { event ->
            when (event) {
                is CoreEvent.Message -> notificationOf(event)
                is CoreEvent.Reaction -> notificationOf(event)
                is CoreEvent.Alert -> notificationOf(event)
                is CoreEvent.AliveWake -> notificationOf(event)
                is CoreEvent.SosWake -> notificationOf(event)
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
        return chosen to result.complete
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

    /** Sign of life of another person (ADR 0083): as the app shows it to helpers ([lifeSignWake]). */
    private fun notificationOf(e: CoreEvent.AliveWake): WakeNotification? {
        // In a circle, or for this person alone (empty circle ID, ADR 0121 §23): then the person stands for the circle.
        val direct = e.circleId.isEmpty()
        val circle = if (direct) null else core.circle(e.circleId) ?: return null
        val known = if (direct) core.membersOf(MemberState.CONTACTS) else core.membersOf(e.circleId)
        val who = ContactNames.active(ContactNames.decode(store.getString(ContactNames.SETTING_KEY)))[e.owner]?.takeIf { direct }
            ?: known.lastOrNull { (it.personId ?: it.deviceId) == e.owner }?.name?.ifBlank { null } ?: texts.status.someone
        val planId = B64.encode(e.alive.plan)
        val target = if (direct) app.anynear.lifesigns.LifeSigns.PERSON_PREFIX + e.owner else e.circleId
        val n = app.anynear.lifesigns.checkInWake(store, planId, target, who, circle?.name ?: who, e.alive, e.call, e.timestampMillis, Clock.System.now().toEpochMilliseconds()) ?: return null
        return WakeNotification(
            objectId = e.objectId,
            key = n.key,
            title = n.title,
            body = n.text,
            timestampMillis = n.timestampMillis,
            alertChannel = n.channelId,
            sound = n.sound.file.takeIf { n.loudness == AlertLoudness.Loud },
            level = wakeLevelOf(n.loudness),
        )
    }

    /** A call for the helpers (ADR 0083 §2, §5.4): rings on a locked iPhone like in the running app ([app.anynear.sos.sosWake]). */
    private fun notificationOf(e: CoreEvent.SosWake): WakeNotification? {
        val known = e.circleId?.let { core.membersOf(it) }.orEmpty() + core.membersOf(MemberState.CONTACTS)
        val who = ContactNames.active(ContactNames.decode(store.getString(ContactNames.SETTING_KEY)))[e.owner]
            ?: known.lastOrNull { (it.personId ?: it.deviceId) == e.owner }?.name?.ifBlank { null } ?: texts.status.someone
        val target = e.circleId ?: (app.anynear.lifesigns.LifeSigns.PERSON_PREFIX + e.owner)
        val n = app.anynear.sos.sosWake(store, e.id, target, who, fromCheckIn = e.plan != null, unreachable = false, atMillis = e.timestampMillis) ?: return null
        return WakeNotification(
            objectId = e.objectId, key = n.key, title = n.title, body = n.text, timestampMillis = n.timestampMillis, alertChannel = n.channelId,
            sound = n.sound.file.takeIf { n.loudness == AlertLoudness.Loud }, level = wakeLevelOf(n.loudness),
        )
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
        val sender = known.lastOrNull { it.deviceId == m.fromDevice }?.let(::nameOf) ?: m.fromName
        // A place says what its card says (ADR 0121 §26), not the stand-in text with its link.
        val text = m.spot?.let { app.anynear.mesh.spotLineOf(it.kind, it.label, outgoing = false) }
            ?: m.text.ifEmpty { RadioAnnouncement.radioText("", m.attachments.mapNotNull { AttachmentKind.fromWire(it.type) }) }
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
        val sender = known.lastOrNull { it.deviceId == r.fromDevice }?.let(::nameOf) ?: texts.status.someone
        val text = texts.chat.reactedToYours(r.emoji)
        return when (val c = r.conversation) {
            is CoreConversation.Circle -> {
                val circle = core.circle(c.circleId) ?: return null
                WakeNotification(r.objectId, Conversation.Circle(c.circleId).key(), circle.name, "$sender: $text", r.timestampMillis)
            }
            is CoreConversation.Direct -> WakeNotification(r.objectId, Conversation.Direct(PeerId.device(c.personId)).key(), sender, text, r.timestampMillis)
        }
    }

    /**
     * Name of [member]: an own name for the person ([ContactNames], in the shared store like in the app) covers the
     * name from their profile; `null` if neither is known.
     */
    private fun nameOf(member: MemberState): String? =
        ContactNames.active(ContactNames.decode(store.getString(ContactNames.SETTING_KEY)))[member.person]
            ?: member.name.ifBlank { null }

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

/** Level of the notification on iOS for an alert: alarm "Critical" (with [AppConfig.CRITICAL_ALERTS] off "Time Sensitive"). */
fun wakeLevelOf(loudness: AlertLoudness): String = when (loudness) {
    AlertLoudness.Normal -> WakeLevel.ACTIVE
    AlertLoudness.Loud -> if (AppConfig.CRITICAL_ALERTS) WakeLevel.CRITICAL else WakeLevel.TIME_SENSITIVE
}
