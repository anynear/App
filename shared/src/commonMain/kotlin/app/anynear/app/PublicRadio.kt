package app.anynear.app

import app.anynear.AppConfig
import app.anynear.data.KeyValueStore
import app.anynear.data.LocalCipher
import app.anynear.data.TextFile
import app.anynear.mesh.ChannelSetupResult
import app.anynear.mesh.IncomingPublicText
import app.anynear.mesh.LinkState
import app.anynear.mesh.MeshLink
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.NotReadyException
import app.anynear.mesh.PublicChannel
import app.anynear.mesh.Reaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A message in a public chat (ADR 0055). Exists only on this phone. */
@Serializable
data class PublicMessage(
    val id: String,
    val channel: PublicChannel,
    /** Radio of the sender; 0 for own messages. */
    val fromNodeNum: Int,
    /** Name of the radio at reception, if known. */
    val fromName: String? = null,
    val text: String,
    val timestampMillis: Long,
    val outgoing: Boolean = false,
    val status: MessageStatus? = null,
    /** Radio values at reception, for the details behind the "i"; `null` if the packet did not carry them. */
    val hops: Int? = null,
    val snr: Float? = null,
    val rssi: Int? = null,
    val viaMqtt: Boolean = false,
    /** Meshtastic reactions (ADR 0055 §3): radio `n:<node>` or [Reaction.SELF] → reaction. */
    val reactions: Map<String, Reaction> = emptyMap(),
) {
    /** Packet ID of the transmission that reactions refer to; it is at the end of the [id]. */
    val packetId: Int get() = id.substringAfterLast(':').toIntOrNull() ?: 0

    /** Visible reactions, grouped by emoji in the order of the first ones: emoji → senders. */
    val reactionGroups: List<Pair<String, List<String>>>
        get() = reactions.entries.sortedBy { it.value.ts }.groupBy({ it.value.emoji }, { it.key }).toList()

    val ownReaction: String? get() = reactions[Reaction.SELF]?.emoji

    companion object {
        /** Key of a radio in [reactions]. */
        fun reactorKey(nodeNum: Int) = "n:$nodeNum"

        fun nodeOfReactor(key: String): Int? = key.removePrefix("n:").takeIf { key.startsWith("n:") }?.toIntOrNull()
    }
}

/**
 * Offer to join the channel of the local radio community (ADR 0054 §1a): [freeSlots] free slots on the radio;
 * 0 means that no slot is free and the app offers nothing.
 */
data class CommunityOffer(val name: String, val freeSlots: Int)

/** Result of [PublicRadio.send]. */
sealed interface PublicSendResult {
    data object Sent : PublicSendResult
    data object Empty : PublicSendResult
    data object TooLong : PublicSendResult
    data class TooSoon(val seconds: Int) : PublicSendResult
    data object NotReady : PublicSendResult
}

/**
 * Public radio of the area (ADR 0055): chats „Alle in der Nähe“ and „Mesh Hessen“. Radio only, only on this phone:
 * none of it goes to the internet, into the backup or to one's own other devices. At most [MAX_MESSAGES] messages
 * and [MAX_AGE_MILLIS] old. Notifications only if they are switched on at the chat (default off, ADR 0055 §4).
 */
class PublicRadio(
    private val scope: CoroutineScope,
    private val link: MeshLink,
    /** Own file outside the backup, encrypted with the device key. */
    private val file: TextFile,
    private val cipher: LocalCipher?,
    /** Settings (switch, read until, explanation seen). */
    private val store: KeyValueStore,
    private val now: () -> Long,
    /** Key of „Mesh Hessen“ from the list; empty as long as it is not known. */
    private val communityPsk: ByteArray = AppConfig.MeshHessen.psk,
    private val log: (String) -> Unit = {},
) {
    private val communityKnown: Boolean get() = communityPsk.size == 32

    private val _messages = MutableStateFlow(load())
    val messages: StateFlow<List<PublicMessage>> = _messages.asStateFlow()

    private val _shown = MutableStateFlow(store.getString(SHOWN_KEY) != "0")

    /** Switch on the radio page, default on. */
    val shown: StateFlow<Boolean> = _shown.asStateFlow()

    private val _readUntil = MutableStateFlow(PublicChannel.entries.associateWith { store.getString(readKey(it))?.toLongOrNull() ?: 0L })

    /** Chats that currently exist: channel detected on the radio and switch on. */
    val available: StateFlow<List<PublicChannel>> =
        combine(link.publicChannels, _shown) { channels, shown ->
            if (!shown) emptyList() else PublicChannel.entries.filter { c -> channels.any { it.channel == c } }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** New messages per chat since it was last opened. */
    val unread: StateFlow<Map<PublicChannel, Int>> =
        combine(_messages, _readUntil) { messages, read ->
            PublicChannel.entries.associateWith { c -> messages.count { it.channel == c && !it.outgoing && it.timestampMillis > (read[c] ?: 0L) } }
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val _notify = MutableStateFlow(PublicChannel.entries.associateWith { store.getString(notifyKey(it)) == "1" })

    /** Notifications per chat, switched on on the chat's page; default off. */
    val notify: StateFlow<Map<PublicChannel, Boolean>> = _notify.asStateFlow()

    /** Called after a new message in a chat with notifications switched on. */
    var onNotify: ((PublicChannel) -> Unit)? = null

    private val lastSent = mutableMapOf<PublicChannel, Long>()

    private val _community = MutableStateFlow(store.getString(COMMUNITY_KEY)?.let { it == "1" })

    /** Answer to the offer „Mesh Hessen“ (ADR 0054 §1a): `true` yes, `false` no thanks, `null` not asked yet. */
    val community: StateFlow<Boolean?> = _community.asStateFlow()

    /** Alert "no slot free" dismissed until the next start; it comes back if there is a slot then. */
    private val noSlotDismissed = MutableStateFlow(false)

    /**
     * Quiet alert in the overview, once per community: radio connected, key known, not asked yet and
     * the channel not already there (not created by the person either).
     */
    val offer: StateFlow<CommunityOffer?> =
        combine(
            combine(link.state, link.publicChannels, link.circleChannels) { state, public, circle -> Triple(state, public, circle) },
            link.otherChannelSlots,
            link.oldCircleChannels,
            _community,
            noSlotDismissed,
        ) { (state, public, circle), other, old, answer, dismissed ->
            val ready = communityKnown && state == LinkState.Connected && circle != null && answer == null
            if (!ready || public.any { it.channel == PublicChannel.MESH_HESSEN }) return@combine null
            // Slots 1–7 minus `anynear` and channels of other apps; the app clears channels of earlier versions anyway.
            val free = (MeshLink.CIRCLE_SLOTS - circle.orEmpty().size - other).coerceAtLeast(0)
            if (free == 0 && dismissed) null else CommunityOffer(AppConfig.MeshHessen.NAME, free)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** "Yes": have the channel created (done by the sync in [AppController] as soon as the device is ready). */
    fun acceptCommunity() = setCommunity(true)

    /** "No thanks": do not ask again. This can be changed later on the radio page. */
    fun declineCommunity() = setCommunity(false)

    fun dismissNoSlot() {
        noSlotDismissed.value = true
    }

    /**
     * Switch on the radio page. Off removes the channel again, but only the one from the list (name and known
     * key) that the app created with consent; it does not touch one the person created themselves (ADR 0038).
     */
    fun setCommunity(on: Boolean) {
        val wasOn = _community.value == true
        _community.value = on
        store.putString(COMMUNITY_KEY, if (on) "1" else "0")
        if (!on && wasOn && link.publicChannels.value.any { it.channel == PublicChannel.MESH_HESSEN }) {
            scope.launch { if (!link.removeChannel(AppConfig.MeshHessen.NAME)) log("Kanal ${AppConfig.MeshHessen.NAME}: nicht entfernt") }
        }
    }

    fun start() {
        scope.launch { link.incomingPublic.collect(::onIncoming) }
        prune()
    }

    private fun onIncoming(incoming: IncomingPublicText) {
        if (!_shown.value) return
        if (incoming.reactionTo != 0) return onReaction(incoming)
        val id = "r:${incoming.fromNodeNum}:${incoming.packetId}"
        if (_messages.value.any { it.id == id }) return
        add(
            PublicMessage(
                id = id,
                channel = incoming.channel,
                fromNodeNum = incoming.fromNodeNum,
                fromName = link.knownNames.value[incoming.fromNodeNum],
                text = incoming.text,
                timestampMillis = incoming.timestampMillis,
                hops = incoming.hops,
                snr = incoming.snr,
                rssi = incoming.rssi,
                viaMqtt = incoming.viaMqtt,
            ),
        )
        if (_notify.value[incoming.channel] == true) onNotify?.invoke(incoming.channel)
    }

    /** Reaction of a radio to a message in the same chat; discarded without a matching message. One per radio. */
    private fun onReaction(incoming: IncomingPublicText) {
        if (!Reaction.isValid(incoming.text)) return
        val target = _messages.value.firstOrNull { it.channel == incoming.channel && it.packetId == incoming.reactionTo } ?: return
        val reaction = Reaction(incoming.text, incoming.timestampMillis)
        update { list -> list.map { if (it.id == target.id) it.copy(reactions = it.reactions + (PublicMessage.reactorKey(incoming.fromNodeNum) to reaction)) else it } }
    }

    /**
     * Reacts with [emoji] to the message [messageId], as a Meshtastic reaction to everyone in the channel. Meshtastic
     * knows no taking back; a different emoji replaces the own reaction. Counts as a message for [MIN_GAP_MILLIS].
     */
    fun react(messageId: String, emoji: String): PublicSendResult {
        val target = _messages.value.firstOrNull { it.id == messageId } ?: return PublicSendResult.Empty
        if (!Reaction.isValid(emoji) || target.ownReaction == emoji) return PublicSendResult.Empty
        val wait = secondsUntilNextSend(target.channel)
        if (wait > 0) return PublicSendResult.TooSoon(wait)
        link.sendPublicReaction(emoji, target.packetId, target.channel) ?: return PublicSendResult.NotReady
        val sentAt = now()
        lastSent[target.channel] = sentAt
        update { list -> list.map { if (it.id == messageId) it.copy(reactions = it.reactions + (Reaction.SELF to Reaction(emoji, sentAt))) else it } }
        return PublicSendResult.Sent
    }

    fun setNotify(channel: PublicChannel, on: Boolean) {
        _notify.update { it + (channel to on) }
        store.putString(notifyKey(channel), if (on) "1" else null)
    }

    /** Messages from others in [channel] not read yet, oldest first. */
    fun unreadMessages(channel: PublicChannel): List<PublicMessage> {
        val read = _readUntil.value[channel] ?: 0L
        return _messages.value.filter { it.channel == channel && !it.outgoing && it.timestampMillis > read }
    }

    fun setShown(on: Boolean) {
        _shown.value = on
        store.putString(SHOWN_KEY, if (on) null else "0")
        // Off means: keep nothing any more.
        if (!on) update { emptyList() }
    }

    fun markRead(channel: PublicChannel) {
        val until = _messages.value.filter { it.channel == channel }.maxOfOrNull { it.timestampMillis } ?: now()
        _readUntil.update { it + (channel to until) }
        store.putString(readKey(channel), until.toString())
    }

    /** Explanation at the top of the chat already shown once? */
    fun introSeen(channel: PublicChannel): Boolean = store.getString(introKey(channel)) == "1"

    fun setIntroSeen(channel: PublicChannel) = store.putString(introKey(channel), "1")

    /** The confirmation prompt before the first message comes only once. */
    val firstSendConfirmed: Boolean get() = store.getString(CONFIRMED_KEY) == "1"

    fun confirmFirstSend() = store.putString(CONFIRMED_KEY, "1")

    /** Sends [text] to everyone on [channel]: text only, max. [MAX_BYTES], one message per [MIN_GAP_MILLIS] and channel. */
    fun send(channel: PublicChannel, text: String): PublicSendResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return PublicSendResult.Empty
        if (trimmed.encodeToByteArray().size > MAX_BYTES) return PublicSendResult.TooLong
        val wait = secondsUntilNextSend(channel)
        if (wait > 0) return PublicSendResult.TooSoon(wait)
        val send = try {
            link.sendPublic(trimmed, channel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NotReadyException) {
            return PublicSendResult.NotReady
        } catch (e: Exception) {
            log("Öffentlicher Funk: nicht gesendet (${e::class.simpleName})")
            return PublicSendResult.NotReady
        }
        val sentAt = now()
        lastSent[channel] = sentAt
        val id = "s:$sentAt:${send.packetId}"
        add(PublicMessage(id, channel, 0, null, trimmed, sentAt, outgoing = true, status = MessageStatus.QUEUED))
        scope.launch {
            send.statuses.collect { status -> update { list -> list.map { if (it.id == id) it.copy(status = status) else it } } }
        }
        return PublicSendResult.Sent
    }

    /** Seconds until the next allowed message on [channel], 0 = now. */
    fun secondsUntilNextSend(channel: PublicChannel): Int {
        val last = lastSent[channel] ?: return 0
        val left = MIN_GAP_MILLIS - (now() - last)
        return if (left <= 0) 0 else ((left + 999) / 1000).toInt()
    }

    /**
     * Creates the channel „Mesh Hessen“ on the connected radio if the person has agreed (ADR 0054 §1a) and
     * the key is known. An existing channel of the same name stays as it is. Call when the device is
     * connected and nothing is being taken over right now.
     */
    suspend fun ensureMeshHessen() {
        if (!communityKnown || _community.value != true) return
        val result = link.ensurePublicChannel(AppConfig.MeshHessen.NAME, communityPsk)
        if (result != ChannelSetupResult.Done) log("Kanal ${AppConfig.MeshHessen.NAME}: $result")
    }

    private fun add(message: PublicMessage) = update { (it + message).sortedBy { m -> m.timestampMillis } }

    private fun prune() = update { it }

    private fun update(change: (List<PublicMessage>) -> List<PublicMessage>) {
        val cutoff = now() - MAX_AGE_MILLIS
        val next = change(_messages.value).filter { it.timestampMillis >= cutoff }.takeLast(MAX_MESSAGES)
        _messages.value = next
        runCatching { save(next) }.onFailure { log("Öffentlicher Funk: nicht gespeichert") }
    }

    private fun load(): List<PublicMessage> {
        val raw = runCatching { file.read() }.getOrNull() ?: return emptyList()
        val plain = if (cipher == null) raw else cipher.open(raw) ?: return emptyList()
        return runCatching { json.decodeFromString<List<PublicMessage>>(plain) }.getOrNull().orEmpty()
    }

    private fun save(messages: List<PublicMessage>) {
        val plain = json.encodeToString(messages)
        file.write(cipher?.seal(plain) ?: plain)
    }

    companion object {
        const val FILE_NAME = "public-radio.v1"
        const val MAX_MESSAGES = 500
        const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000

        /** Meshtastic text fits into one radio packet; other apps know no continuation, hence no truncating. */
        const val MAX_BYTES = 200
        const val MIN_GAP_MILLIS = 30_000L

        private const val SHOWN_KEY = "public.shown"
        private const val CONFIRMED_KEY = "public.first-send"
        private const val COMMUNITY_KEY = "public.community.mesh-hessen"
        private fun readKey(channel: PublicChannel) = "public.read.${channel.name.lowercase()}"
        private fun introKey(channel: PublicChannel) = "public.intro.${channel.name.lowercase()}"
        private fun notifyKey(channel: PublicChannel) = "public.notify.${channel.name.lowercase()}"

        /** Key of the notification of a public chat, next to `c:` (circle) and `p:` (person). */
        fun notificationKey(channel: PublicChannel) = "o:${channel.name.lowercase()}"

        fun channelOfNotificationKey(key: String): PublicChannel? =
            if (!key.startsWith("o:")) null else PublicChannel.entries.firstOrNull { notificationKey(it) == key }
        private val json = Json { ignoreUnknownKeys = true }
    }
}
