package app.anynear.data

import app.anynear.attachments.AttachmentState
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.IncomingText
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.Reaction
import app.anynear.mesh.SystemNote
import app.anynear.mesh.Transport
import app.anynear.mesh.radioText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.math.abs

/**
 * Message history in memory.
 *
 * The same message can arrive over radio and over the internet. It is shown only once:
 * - same path: via the packet ID (radio) or message ID (internet),
 * - other path: same sender (radio) and same radio packet ID (`pkt`, even hours
 *   later), otherwise same conversation, same radio text (text including the announcement of the attachments),
 *   at most [CROSS_TRANSPORT_WINDOW_MILLIS] apart, and that one to one. Twice "ok"
 *   stays twice "ok".
 * - sent again (after a restart of the sender): same sender, same message ID `mid`.
 *
 * Storing on the device happens via [snapshot] and [MessageHistory]; [restored] is the last
 * stored state. Outgoing messages that had not yet gone out over the internet when the app was closed
 * stay "waiting" if they are younger than [RESUME_WINDOW_MILLIS]; the app then sends them
 * again (see [resumable]). Older ones count as "not sent".
 */
class MessageStore(
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    restored: MessageHistory.Snapshot? = null,
) {
    private val _messages = MutableStateFlow(
        restored?.messages.orEmpty().map {
            if (it.outgoing && it.status == MessageStatus.QUEUED && !isResumable(it)) it.copy(status = MessageStatus.FAILED) else it
        },
    )

    /** Outgoing messages that should still go out over the internet after the restart. */
    fun resumable(): List<ChatMessage> = _messages.value.filter { it.outgoing && isResumable(it) }

    private fun isResumable(message: ChatMessage): Boolean =
        Transport.INTERNET !in message.via &&
            !message.sendFailed &&
            message.status?.arrived != true &&
            now() - message.timestampMillis < RESUME_WINDOW_MILLIS
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private class Arrival(val message: ChatMessage, val via: MutableSet<Transport>)

    private val arrivals = _messages.value
        .filter { !it.outgoing }
        .takeLast(MAX_ARRIVALS)
        .mapTo(mutableListOf()) { Arrival(it, it.via.toMutableSet()) }

    // Immutable sets that are replaced on change so that [snapshot] can read them at any time.
    /** Radio packets already taken over (sender, packet ID) so that repetitions do not appear twice. */
    @Volatile
    private var seenPackets: Set<Pair<Int, Int>> = restored?.seenPackets.orEmpty().toSet()

    @Volatile
    private var seenOnlineIds: Set<String> = restored?.seenOnlineIds.orEmpty().toSet()

    /** Receipt for an own message that is not here yet; time [at] of its arrival. */
    private class EarlyReceipt(val byPeer: String, val via: Transport, val read: Boolean, val at: Long)

    /**
     * Receipts that arrived before their message: the copy of a message from another own device
     * (ADR 0042) can arrive after the receipt of the recipient. Only in memory, per ID the first ones.
     */
    private val earlyReceipts = MutableStateFlow<Map<String, List<EarlyReceipt>>>(emptyMap())

    /** Current state for saving. */
    fun snapshot(): MessageHistory.Snapshot =
        MessageHistory.Snapshot(messages = _messages.value, seenPackets = seenPackets.toList(), seenOnlineIds = seenOnlineIds.toList())

    fun addOutgoing(message: ChatMessage) {
        _messages.update { it + message }
    }

    /** Catch up on receipts that arrived earlier for [localId]; each exactly once. */
    private fun replayEarly(localId: String) {
        var parked: List<EarlyReceipt>? = null
        earlyReceipts.update { map -> parked = map[localId]; if (parked == null) map else map - localId }
        parked?.forEach { r -> if (r.read) confirmRead(localId, r.byPeer, r.via, r.at) else confirmDelivery(localId, r.byPeer, r.via, r.at) }
    }

    /** Remember a receipt for a message still unknown; if the message has come meanwhile after all, catch up right away. */
    private fun park(localId: String, receipt: EarlyReceipt) {
        earlyReceipts.update { map ->
            val list = map[localId].orEmpty()
            if (list.size >= MAX_EARLY_PER_MESSAGE) return@update map
            // The oldest IDs drop out; receipts for foreign messages should not grow.
            val trimmed = if (localId !in map && map.size >= MAX_EARLY) map - map.keys.first() else map
            trimmed + (localId to list + receipt)
        }
        if (find(localId) != null) replayEarly(localId)
    }

    /** Line about a change in the circle (ADR 0012). Does not count as a new message. */
    fun addSystem(localId: String, conversation: Conversation, text: String, note: SystemNote = SystemNote()) {
        val at = now()
        _messages.update {
            it + ChatMessage(
                localId = localId,
                conversation = conversation,
                fromNodeNum = 0,
                text = text,
                timestampMillis = at,
                outgoing = false,
                arrivedAtMillis = at,
                system = note,
            )
        }
    }

    fun find(localId: String): ChatMessage? = _messages.value.firstOrNull { it.localId == localId }

    /**
     * Received over radio. [conversation] and [fromPeer] are already mapped to the person.
     * @return `false` if the message is already shown.
     */
    fun addIncoming(incoming: IncomingText, localId: String, conversation: Conversation, fromPeer: String? = null): Boolean {
        val key = incoming.fromNodeNum to incoming.packetId
        if (incoming.packetId != 0) {
            if (key in seenPackets) return false
            seenPackets = (seenPackets + key).keepLast(MAX_SEEN)
        }
        val message = ChatMessage(
            localId = localId,
            conversation = conversation,
            fromNodeNum = incoming.fromNodeNum,
            text = incoming.text,
            timestampMillis = incoming.timestampMillis,
            outgoing = false,
            fromPeer = fromPeer,
            unread = true,
            radioPacketId = incoming.packetId,
            radioChannel = incoming.channelIndex,
        )
        return add(message, Transport.RADIO)
    }

    /**
     * Received over radio as ANMP (ADR 0021): [message] carries sender and message ID. The same
     * message over the internet is matched via the message ID.
     * @return `false` if the message is already shown.
     */
    fun addRadio(message: ChatMessage, fromNodeNum: Int, packetId: Int): Boolean {
        if (!rememberPacket(fromNodeNum, packetId)) return false
        if (mergeTwin(message, Transport.RADIO)) return false
        return add(message.copy(unread = true, radioPacketId = packetId), Transport.RADIO)
    }

    /** Has the message [remoteId] from [fromPeer] already arrived via [via]? */
    fun arrivedVia(remoteId: String, fromPeer: String, via: Transport): Boolean =
        _messages.value.any { !it.outgoing && it.remoteId == remoteId && it.fromPeer == fromPeer && via in it.via }

    /**
     * Received over the internet. [read]: it comes back after the emergency card and was already read (ADR 0107 §8).
     * @return `false` if the message is already shown.
     */
    fun addOnline(id: String, message: ChatMessage, read: Boolean = false): Boolean {
        if (id in seenOnlineIds) return false
        seenOnlineIds = (seenOnlineIds + id).keepLast(MAX_SEEN)
        // The same message already there over radio, or stored once more by the sender after a restart.
        if (mergeTwin(message, Transport.INTERNET)) return false
        val added = add(if (message.outgoing) message else message.copy(unread = !read), Transport.INTERNET)
        if (added && message.outgoing) replayEarly(message.localId)
        return added
    }

    /**
     * If the message (same sender, same message ID) already exists, it gets the path [via]
     * added. @return `true` if it already existed.
     */
    private fun mergeTwin(message: ChatMessage, via: Transport): Boolean {
        val mid = message.remoteId ?: return false
        val existing = _messages.value.lastOrNull { !it.outgoing && it.remoteId == mid && it.fromPeer == message.fromPeer } ?: return false
        if (via !in existing.via) {
            arrivals.firstOrNull { it.message.localId == existing.localId }?.via?.add(via)
            update(existing.localId) { mergeVia(it, message, via) }
        }
        return true
    }

    /** [current] already came on another path; [other] is the same message via [via]. */
    private fun mergeVia(current: ChatMessage, other: ChatMessage, via: Transport): ChatMessage {
        // The first radio copy tells whether it was heard directly or came from the radio inbox (relayed by another
        // device); without this, an inbox copy after the internet one looked as if this device had heard it over radio.
        val relayed = if (via == Transport.RADIO && Transport.RADIO !in current.via) other.relayed else current.relayed
        // Deleted stays deleted, even if the message later still arrives on some path (ADR 0068).
        if (via != Transport.INTERNET || current.deleted) return current.copy(via = current.via + via, relayed = relayed)
        // Over the internet, send time, message ID and attachments come along; the text
        // is then without the announcement of the attachments from the radio text.
        return current.copy(
            via = current.via + via,
            timestampMillis = other.timestampMillis,
            text = other.text,
            attachments = other.attachments,
            remoteId = other.remoteId,
            space = other.space ?: current.space,
            radioPacketId = if (current.radioPacketId != 0) current.radioPacketId else other.radioPacketId,
            senderName = current.senderName ?: other.senderName,
            replyTo = other.replyTo ?: current.replyTo,
        )
    }

    private fun add(message: ChatMessage, via: Transport): Boolean {
        val twin = arrivals.firstOrNull { it.matches(message) && via !in it.via }
        if (twin != null) {
            twin.via += via
            // One message stays shown, but it carries both paths.
            update(twin.message.localId) { mergeVia(it, message, via) }
            return false
        }
        arrivals += Arrival(message, mutableSetOf(via))
        if (arrivals.size > MAX_ARRIVALS) arrivals.removeAt(0)
        _messages.update { it + message.copy(via = setOf(via), arrivedAtMillis = now()) }
        return true
    }

    private fun Arrival.matches(other: ChatMessage): Boolean {
        // ANMP (ADR 0021): sender and message ID are the same on all paths and decide alone.
        if (message.remoteId != null && other.remoteId != null && message.fromPeer != null && other.fromPeer != null) {
            return message.remoteId == other.remoteId && message.fromPeer == other.fromPeer && message.conversation == other.conversation
        }
        if (message.fromNodeNum == 0 || message.fromNodeNum != other.fromNodeNum || message.conversation != other.conversation) return false
        // With a packet ID on both sides only it decides, no matter how much time lies in between.
        if (message.radioPacketId != 0 && other.radioPacketId != 0) return message.radioPacketId == other.radioPacketId
        return message.radioText == other.radioText &&
            abs(message.timestampMillis - other.timestampMillis) <= CROSS_TRANSPORT_WINDOW_MILLIS
    }

    /**
     * Messages from the history (ADR 0014): not unread, without delivery receipt, sorted in by
     * time. What is already there (same sender and same message ID) stays as it is.
     * @return number of newly taken-over messages.
     */
    fun addHistory(items: List<ChatMessage>): Int {
        var added = 0
        _messages.update { list ->
            val known = list.mapNotNull { m -> m.remoteId?.let { id -> m.fromPeer to id } }.toMutableSet<Pair<String?, String?>>()
            val ownIds = list.filter { it.outgoing }.map { it.localId }.toSet()
            val fresh = items.filter { m ->
                val key = m.fromPeer to m.remoteId
                m.remoteId != null && m.remoteId !in ownIds && known.add(key)
            }
            added = fresh.size
            if (fresh.isEmpty()) list else (list + fresh.map { it.copy(unread = false, fromHistory = true, arrivedAtMillis = now()) }).sortedBy { it.timestampMillis }
        }
        return added
    }

    /**
     * Reaction [emoji] of [reactor] to the message [localId] (ADR 0018), empty = withdrawn. Per
     * person the newest applies; an older or identical copy, for instance via the other path, changes nothing.
     * @return `true` if something has changed.
     */
    fun setReaction(localId: String, reactor: String, emoji: String, ts: Long): Boolean {
        var changed = false
        update(localId) { message ->
            // Empty means withdrawn (ADR 0021); stored as a marker, see [Reaction].
            if (message.system != null || (emoji.isNotEmpty() && !Reaction.isValid(emoji))) return@update message
            val current = message.reactions[reactor]
            // Same time (ANMP counts in seconds): the one that arrived last applies.
            if (current != null && (current.ts > ts || current.emoji == emoji)) return@update message
            changed = true
            message.copy(reactions = message.reactions + (reactor to Reaction(emoji, ts)))
        }
        return changed
    }

    /** Target of a Meshtastic reaction (radio without anynear): the message with radio packet ID [packetId] in [conversation]. */
    fun findByRadioPacket(conversation: Conversation, packetId: Int): ChatMessage? =
        if (packetId == 0) null else _messages.value.lastOrNull { it.conversation == conversation && it.system == null && it.radioPacketId == packetId }

    /**
     * Target of a reaction over the internet: own message [mid] ([ownMessage]) or the one of [authorPeer]
     * with their message ID [mid].
     */
    fun findByRemote(conversation: Conversation, authorPeer: String?, mid: String, ownMessage: Boolean): ChatMessage? =
        _messages.value.lastOrNull {
            it.conversation == conversation && it.system == null &&
                if (ownMessage) it.outgoing && it.localId == mid else !it.outgoing && it.fromPeer == authorPeer && it.remoteId == mid
        }

    /** Remember radio packet (sender, packet ID) as seen. @return `false` if it was already known. */
    fun rememberPacket(fromNodeNum: Int, packetId: Int): Boolean {
        if (packetId == 0) return true
        val key = fromNodeNum to packetId
        if (key in seenPackets) return false
        seenPackets = (seenPackets + key).keepLast(MAX_SEEN)
        return true
    }

    /** Forget all messages of a conversation (circle left or removed, ADR 0014). */
    fun removeConversation(conversation: Conversation) {
        _messages.update { list -> list.filterNot { it.conversation == conversation } }
        arrivals.removeAll { it.message.conversation == conversation }
    }

    /**
     * Deleted for everyone (ADR 0068): only sender, time and IDs stay. @return `false` if the message does not exist
     * or was already deleted.
     */
    fun markDeleted(localId: String): Boolean {
        var changed = false
        update(localId) {
            if (it.deleted || it.system != null) return@update it
            changed = true
            it.copy(deleted = true, text = "", attachments = emptyList(), reactions = emptyMap(), link = null, unread = false, signed = null)
        }
        return changed
    }

    /**
     * The deletion came before the message (other path, history): placeholder with sender and message ID. If the
     * message comes later, [mergeTwin] finds it and it stays deleted.
     */
    fun addDeleted(localId: String, conversation: Conversation, fromPeer: String, fromDevice: String?, remoteId: String, timestampMillis: Long) {
        if (_messages.value.any { !it.outgoing && it.remoteId == remoteId && it.fromPeer == fromPeer }) return
        _messages.update {
            it + ChatMessage(
                localId = localId, conversation = conversation, fromNodeNum = 0, text = "", timestampMillis = timestampMillis,
                outgoing = false, fromPeer = fromPeer, fromDevice = fromDevice, remoteId = remoteId, deleted = true, arrivedAtMillis = now(),
            )
        }
    }

    /** Remove only on this device (ADR 0068, „Nur für mich löschen“). */
    fun remove(localId: String) {
        _messages.update { list -> list.filterNot { it.localId == localId } }
        arrivals.removeAll { it.message.localId == localId }
    }

    /** Remember the signed envelope of an own circle message in order to pass it on to new members. */
    fun setSigned(localId: String, signed: String) {
        update(localId) { if (it.signed == null) it.copy(signed = signed) else it }
    }

    /** Remember the packet ID of the own radio transmission; it goes with the message to the internet (`pkt`). */
    fun setRadioPacket(localId: String, packetId: Int) {
        update(localId) { it.copy(radioPacketId = packetId) }
    }

    /** New state of an attachment [blobId] of the message [localId]. */
    fun updateAttachment(localId: String, blobId: String, state: AttachmentState) {
        update(localId) { message ->
            message.copy(
                attachments = message.attachments.map { if (it.blobId == blobId) it.copy(state = state) else it },
                // The image of a link preview (ADR 0069) runs like an attachment.
                link = message.link?.let { l -> if (l.image?.blobId == blobId) l.copy(image = l.image.copy(state = state)) else l },
            )
        }
    }

    /** Remember the hash of the content of an own attachment; it goes along in the envelope (ADR 0014). */
    fun setAttachmentHash(localId: String, blobId: String, hash: String) {
        update(localId) { message ->
            message.copy(
                attachments = message.attachments.map { if (it.blobId == blobId && it.hash == null) it.copy(hash = hash) else it },
                link = message.link?.let { l -> if (l.image?.blobId == blobId && l.image.hash == null) l.copy(image = l.image.copy(hash = hash)) else l },
            )
        }
    }

    /** Outgoing: finally not sent (e.g. attachment no longer readable); is not tried again. */
    fun markFailed(localId: String) {
        update(localId) { it.copy(status = MessageStatus.FAILED, sendFailed = true) }
    }

    /**
     * New state of an outgoing message on the path [via]. Every state except "waiting" and
     * "not sent" means that it has gone out on this path; [MessageStatus.CONFIRMED] means that
     * it has arrived on this path (radio: confirmation of the recipient device).
     */
    fun updateStatus(localId: String, status: MessageStatus, via: Transport? = null) {
        update(localId) { message ->
            val current = message.status
            val sentVia = via?.takeIf { status != MessageStatus.QUEUED && status != MessageStatus.FAILED }
            val arrivedVia = via?.takeIf { status == MessageStatus.CONFIRMED }
            val next = if (current == null || current.canAdvanceTo(status)) status else current
            message.copy(
                status = next,
                arrivedAtMillis = message.arrivedAtMillis ?: now().takeIf { next == MessageStatus.CONFIRMED },
                via = message.via + listOfNotNull(sentVia),
                deliveredVia = message.deliveredVia + listOfNotNull(arrivedVia),
            )
        }
    }

    /**
     * Delivery receipt from device [byPeer] for the own message [localId], received via [via].
     * Counts only from expected recipients. Once all have confirmed, the message counts as arrived.
     */
    fun confirmDelivery(localId: String, byPeer: String, via: Transport, at: Long = now()) {
        if (find(localId) == null) return park(localId, EarlyReceipt(byPeer, via, read = false, at = at))
        update(localId) { message ->
            if (!message.outgoing || byPeer !in message.recipients) return@update message
            val deliveredTo = message.deliveredTo + byPeer
            val current = message.status
            val all = deliveredTo.containsAll(message.recipients)
            val next = if (all && (current == null || current.canAdvanceTo(MessageStatus.CONFIRMED))) MessageStatus.CONFIRMED else current
            message.copy(
                deliveredTo = deliveredTo,
                deliveredAt = if (byPeer in message.deliveredAt) message.deliveredAt else message.deliveredAt + (byPeer to at),
                deliveredVia = message.deliveredVia + via,
                status = next,
                arrivedAtMillis = message.arrivedAtMillis ?: at.takeIf { next == MessageStatus.CONFIRMED },
            )
        }
    }

    /**
     * Device [peer] is no longer in [conversation] (removed or left): no delivery or read receipt
     * comes from it any more. For own messages that it has not yet confirmed, it no longer
     * counts as a recipient. Once all remaining ones have confirmed, the message counts as arrived. If
     * it has already confirmed receipt, it stays in the message info but no longer holds up "read"
     * (ADR 0019).
     */
    fun dropRecipient(conversation: Conversation, peer: String) {
        _messages.update { list ->
            list.map { message ->
                if (message.conversation != conversation || !message.outgoing || peer !in message.recipients || peer in message.readBy) {
                    return@map message
                }
                if (peer in message.deliveredTo) {
                    val gone = message.readGone + peer
                    return@map message.copy(readGone = gone, status = message.readStatus(message.readBy, gone))
                }
                val recipients = message.recipients - peer
                val current = message.status
                val all = recipients.isNotEmpty() && message.deliveredTo.containsAll(recipients)
                val next = if (all && (current == null || current.canAdvanceTo(MessageStatus.CONFIRMED))) MessageStatus.CONFIRMED else current
                message.copy(
                    recipients = recipients,
                    status = next,
                    // It arrived with the last receipt of the remaining ones.
                    arrivedAtMillis = message.arrivedAtMillis
                        ?: (message.deliveredAt.values.maxOrNull() ?: now()).takeIf { next == MessageStatus.CONFIRMED },
                ).let { it.copy(status = it.readStatus(it.readBy, it.readGone)) }
            }
        }
    }

    /** Status according to read receipts: [MessageStatus.READ] if all expected ones except [gone] have read. */
    private fun ChatMessage.readStatus(readBy: Set<String>, gone: Set<String>): MessageStatus? {
        val expected = recipients.ifEmpty { setOfNotNull((conversation as? Conversation.Direct)?.peer) } - gone
        val all = readBy.isNotEmpty() && readBy.containsAll(expected)
        val current = status
        return if (all && (current == null || current.canAdvanceTo(MessageStatus.READ))) MessageStatus.READ else current
    }

    /**
     * Everything in [conversation] counts as viewed. @return the messages that were unread until just now;
     * their senders get a read receipt (ADR 0019).
     */
    fun markRead(conversation: Conversation): List<ChatMessage> {
        val unread = _messages.value.filter { it.unread && it.conversation == conversation }
        if (unread.isEmpty()) return emptyList()
        val ids = unread.mapTo(HashSet()) { it.localId }
        _messages.update { list -> list.map { if (it.localId in ids) it.copy(unread = false) else it } }
        return unread
    }

    /**
     * [byPeer] has read the own message [localId] (ADR 0019). Counts only from expected
     * recipients; for a direct message without known app devices (radio only) from the conversation partner.
     * Read includes arrived. "Read" applies in the circle only once all recipients have read.
     */
    fun confirmRead(localId: String, byPeer: String, via: Transport, at: Long = now()) {
        val message = find(localId) ?: return park(localId, EarlyReceipt(byPeer, via, read = true, at = at))
        if (!message.outgoing || message.system != null) return
        val expected = message.recipients.ifEmpty { setOfNotNull((message.conversation as? Conversation.Direct)?.peer) }
        if (byPeer !in expected) return
        if (byPeer in message.recipients) confirmDelivery(localId, byPeer, via, at)
        update(localId) { current ->
            val readBy = current.readBy + byPeer
            val next = current.readStatus(readBy, current.readGone)
            current.copy(
                readBy = readBy,
                readAt = if (byPeer in current.readAt) current.readAt else current.readAt + (byPeer to at),
                readVia = current.readVia + via,
                status = next,
                arrivedAtMillis = current.arrivedAtMillis ?: at.takeIf { next == MessageStatus.READ },
            )
        }
    }

    /**
     * Own messages kept as someone else's: the circle history named a removed own device (e.g. the old phone after the
     * emergency card) not as the person. They become own as if this device had got them as copies (ADR 0042), read, under
     * their message ID; one that is already there as own stays and the wrong one goes. @return number of messages fixed.
     */
    fun claimOwn(devices: Set<String>, recipientsOf: (Conversation) -> Set<String>): Int {
        if (devices.isEmpty()) return 0
        var fixed = 0
        _messages.update { list ->
            val taken = list.filter { it.outgoing }.map { it.localId }.toMutableSet()
            var changed = false
            fixed = 0
            val next = list.mapNotNull { m ->
                if (m.outgoing || m.system != null || m.fromDevice !in devices) return@mapNotNull m
                changed = true
                fixed++
                val id = m.remoteId ?: m.localId
                if (!taken.add(id)) return@mapNotNull null
                m.copy(
                    localId = id, outgoing = true, status = MessageStatus.ONLINE, fromPeer = null, unread = false,
                    via = setOf(Transport.INTERNET), recipients = recipientsOf(m.conversation),
                )
            }
            if (changed) next else list
        }
        return fixed
    }

    /**
     * Read up to [sentAt] on an own device (ADR 0107 §8, [app.anynear.chats.ReadUntil]): silently mark as read, without
     * read receipt. @return `true` if something has changed.
     */
    fun markReadUntil(conversation: Conversation, sentAt: Long): Boolean {
        var changed = false
        _messages.update { list ->
            list.map {
                if (it.unread && it.conversation == conversation && it.timestampMillis <= sentAt) it.copy(unread = false).also { changed = true } else it
            }
        }
        return changed
    }

    /** Send time of the newest message in [conversation], `null` if there is none. */
    fun newestSentAt(conversation: Conversation): Long? =
        _messages.value.filter { it.conversation == conversation && it.system == null }.maxOfOrNull { it.timestampMillis }

    /** Read on another own device (ADR 0042): silently mark as read, without read receipt. */
    fun markReadElsewhere(conversation: Conversation, remoteIds: Collection<String>): Boolean {
        val ids = remoteIds.toSet()
        var changed = false
        _messages.update { list ->
            list.map {
                if (it.unread && it.conversation == conversation && (it.remoteId in ids || it.localId in ids)) it.copy(unread = false).also { changed = true } else it
            }
        }
        return changed
    }

    /**
     * A conversation partner has a new identity, the device became a person (ADR 0042): conversation, sender,
     * receipts and reactions move along. `true` if something has changed.
     */
    fun renamePeer(old: String, new: String): Boolean {
        if (old == new) return false
        fun Set<String>.r() = if (old in this) this - old + new else this
        fun <V> Map<String, V>.r() = if (old in this) (this - old) + (new to this.getValue(old)) else this
        var changed = false
        _messages.update { list ->
            list.map { m ->
                val conversation = (m.conversation as? Conversation.Direct)?.takeIf { it.peer == old }?.let { Conversation.Direct(new) } ?: m.conversation
                val next = m.copy(
                    conversation = conversation,
                    fromPeer = if (m.fromPeer == old) new else m.fromPeer,
                    recipients = m.recipients.r(), deliveredTo = m.deliveredTo.r(), deliveredAt = m.deliveredAt.r(),
                    readBy = m.readBy.r(), readAt = m.readAt.r(), readGone = m.readGone.r(), reactions = m.reactions.r(),
                )
                if (next != m) changed = true
                next
            }
        }
        return changed
    }

    private fun update(localId: String, change: (ChatMessage) -> ChatMessage) {
        _messages.update { list -> list.map { if (it.localId == localId) change(it) else it } }
    }

    private fun <T> Set<T>.keepLast(max: Int): Set<T> = if (size > max) drop(size - max).toSet() else this

    companion object {
        const val CROSS_TRANSPORT_WINDOW_MILLIS = 10 * 60 * 1000L

        /** This long the app tries after a restart to still send a message over the internet. */
        const val RESUME_WINDOW_MILLIS = 7 * 24 * 60 * 60 * 1000L
        private const val MAX_ARRIVALS = 2_000
        private const val MAX_SEEN = 5_000
        private const val MAX_EARLY = 500
        private const val MAX_EARLY_PER_MESSAGE = 50
    }
}
