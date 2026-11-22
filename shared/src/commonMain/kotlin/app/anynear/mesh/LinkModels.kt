package app.anynear.mesh

import app.anynear.anmp.core.radio.ProfileDeviation
import app.anynear.anmp.core.radio.RadioProfile
import app.anynear.attachments.Attachment
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.RadioAnnouncement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Connection state to the own radio, as the UI shows it. */
sealed interface LinkState {
    /** No radio assigned yet. */
    data object NoDevice : LinkState

    /** Bluetooth permission is missing or Bluetooth is off. */
    data object BluetoothUnavailable : LinkState

    /** Searching for the assigned device (off, out of range or not yet reachable). */
    data object Searching : LinkState

    /** Connection and sync with the device are in progress. */
    data object Connecting : LinkState

    /** Connected and ready. Says nothing about the reachability of other devices. */
    data object Connected : LinkState

    /** Existing connection has dropped, reconnection is in progress. */
    data object Lost : LinkState

    /**
     * Radio on Wi-Fi is used by another phone or app; the app has stepped back and does not reconnect on its own,
     * because every connect would disconnect the other one ([MeshLink.yieldRadio]). Only [MeshLink.start] connects again.
     */
    data object InUseElsewhere : LinkState
}

/**
 * The radio channel `anynear` on the radio (ADR 0051, 0061).
 * Every circle has its own channel so that circles stay separated over radio as well.
 */
data class RadioChannel(val index: Int, val name: String, val psk: okio.ByteString)

/**
 * State of a sent message. Deliberately worded cautiously: "read" only with a
 * read receipt from the recipient (ADR 0019), in the circle only once everyone has confirmed.
 */
enum class MessageStatus(internal val rank: Int) {
    /** Created in the app, not yet accepted by the radio. */
    QUEUED(0),

    /** The radio has accepted the message for sending. */
    HANDED_TO_RADIO(1),

    /** Sent, but no confirmation within the waiting time. */
    NO_CONFIRMATION(2),

    FAILED(2),

    /** A relayed copy or confirmation from the network was heard. No guarantee of receipt. */
    RELAYED(3),

    /** At least one mailbox on the internet has accepted the message. No guarantee of receipt. */
    ONLINE(3),

    /**
     * Arrived on the end device: for direct messages the recipient's device has confirmed, in the circle
     * all members that were known when sending. Does not mean "read".
     */
    CONFIRMED(4),

    /** Read (ADR 0019): for direct messages by the recipient, in the circle by everyone known when sending. */
    READ(5),
    ;

    /** Arrived on the recipient's device (read includes that). */
    val arrived: Boolean get() = rank >= CONFIRMED.rank

    /** States may only get "better", e.g. a late confirmation after the timeout. */
    fun canAdvanceTo(next: MessageStatus): Boolean = next.rank > rank
}

/** Transport path of a message. */
enum class Transport { INTERNET, RADIO }

@Serializable
sealed interface Conversation {
    /** Group chat of a circle. */
    @Serializable
    @SerialName("circle")
    data class Circle(val circleId: String) : Conversation

    /** Direct message with a person, see [PeerId]. */
    @Serializable
    @SerialName("direct")
    data class Direct(val peer: String) : Conversation
}

/**
 * Identifier of a person in the chat: `d:<device ID>` for app members (internet path, possibly additionally
 * with a radio), `n:<node number>` for radios whose app we do not know (yet).
 */
object PeerId {
    fun device(deviceId: String) = "d:$deviceId"

    fun node(nodeNum: Int) = "n:${nodeNum.toUInt()}"

    fun nodeNumOf(peer: String): Int? = peer.removePrefix("n:").takeIf { peer.startsWith("n:") }?.toUIntOrNull()?.toInt()

    fun deviceIdOf(peer: String): String? = peer.removePrefix("d:").takeIf { peer.startsWith("d:") }
}

/** Stored on the device like this ([app.anynear.data.MessageHistory]); change fields only backward-compatibly. */
@Serializable
data class ChatMessage(
    val localId: String,
    val conversation: Conversation,
    /** Radio of the sender, 0 if unknown (e.g. sender without a radio). */
    val fromNodeNum: Int,
    val text: String,
    val timestampMillis: Long,
    val outgoing: Boolean,
    /** Only set for outgoing messages. */
    val status: MessageStatus? = null,
    /** Sender as [PeerId], for incoming messages (since ADR 0042 the person, not the device). */
    val fromPeer: String? = null,
    /** Device of the sender (device ID), for relating reactions and read receipts over radio (ADR 0042). */
    val fromDevice: String? = null,
    /** Name the sender sent along themselves (internet path). */
    val senderName: String? = null,
    /** Outgoing: paths on which the message went out. Incoming: paths on which it arrived. */
    val via: Set<Transport> = emptySet(),
    /** Outgoing: devices as [PeerId] from which a delivery receipt is expected. */
    val recipients: Set<String> = emptySet(),
    /** Outgoing: devices from [recipients] that have confirmed receipt. */
    val deliveredTo: Set<String> = emptySet(),
    /** Outgoing: when each device from [deliveredTo] confirmed receipt (like the message info in WhatsApp). */
    val deliveredAt: Map<String, Long> = emptyMap(),
    /** Outgoing: paths over which delivery receipts came, i.e. how the message arrived. */
    val deliveredVia: Set<Transport> = emptySet(),
    /**
     * Incoming: when it arrived on this device. Outgoing: when it was confirmed as arrived.
     * [timestampMillis], in contrast, is the time of sending (received only over radio: the time of receipt
     * of the radio, because the radio packet carries no time of sending).
     */
    val arrivedAtMillis: Long? = null,
    /** Outgoing: people as [PeerId] who have read the message (ADR 0019). */
    val readBy: Set<String> = emptySet(),
    /** Outgoing: when each person from [readBy] read it. */
    val readAt: Map<String, Long> = emptyMap(),
    /** Outgoing: paths over which read receipts came. */
    val readVia: Set<Transport> = emptySet(),
    /** Outgoing: recipients who confirmed arrival but left the circle before reading; do not count for "read". */
    val readGone: Set<String> = emptySet(),
    /**
     * Incoming: not yet viewed (conversation was not open). On viewing, a
     * read receipt goes to the sender (ADR 0019).
     */
    val unread: Boolean = false,
    /** Images and PDFs (ADR 0011). Only go over the internet. */
    val attachments: List<Attachment> = emptyList(),
    /**
     * Radio packet ID: incoming, that of the received packet or the one the sender sent along on the
     * internet (`pkt`); outgoing, that of the own radio transmission. 0 = none. Matches radio and
     * internet to each other even when hours lie in between (ADR 0010, 0011).
     */
    val radioPacketId: Int = 0,
    /**
     * Incoming as plain Meshtastic text: slot of the radio channel it arrived on. Replies to a radio without anynear go
     * back on this channel, so that the other side can read them even without a shared key (`null` = unknown).
     */
    val radioChannel: Int? = null,
    /** Incoming: message ID of the sender (`mid`), against duplicates after a resend. */
    val remoteId: String? = null,
    /** Incoming over the internet: ANDP space in which the message and its attachments are (ADR 0035). */
    val space: String? = null,
    /**
     * Outgoing: definitively not to be sent over the internet (e.g. attachment no longer readable on
     * the phone). A "failed" from the radio path alone does not end the resending.
     */
    val sendFailed: Boolean = false,
    /** Set: not a message from someone, but a line about a change in the circle (ADR 0012). */
    val system: SystemNote? = null,
    /** Circle message: opened, signed ANMP packet (Base64url), so that new members get it verified (ADR 0014). */
    val signed: String? = null,
    /** Incoming: sent over radio and passed on by another anynear device (radio inbox, ADR 0051). */
    val relayed: Boolean = false,
    /** Came with the history that a member brought along when joining. */
    val fromHistory: Boolean = false,
    /** Reactions (ADR 0018): per person ([PeerId], own under [Reaction.SELF]) at most one. */
    val reactions: Map<String, Reaction> = emptyMap(),
    /**
     * Deleted for everyone by the author (ADR 0068): text, attachments, reactions and preview are gone, only
     * „Von … gelöscht“ is shown. If the deletion came before the message, a placeholder with [remoteId] and [fromPeer]
     * is stored here.
     */
    val deleted: Boolean = false,
    /** Preview of the first web link in the text, loaded by the sender (ADR 0069). */
    val link: LinkPreview? = null,
    /** A contact introduced in this message (ADR 0116); [text] is only the stand-in for older apps. */
    val intro: ContactIntro? = null,
    /** The message this one answers (ADR 0092); looked up by its ID in the same conversation. */
    val replyTo: ReplyRef? = null,
    /** The sender passed on a message it got („Weitergeleitet“, ADR 0118); no origin. */
    val passedOn: Boolean = false,
    /** A meeting point, „Wo bist du?“ or the answer (ADR 0121 §8); [text] is only the stand-in for older apps. */
    val spot: ChatSpot? = null,
)

/**
 * A place in a conversation (ADR 0121 §8): [kind] 1 meeting point, 2 „Wo bist du?“ (no place), 3 the sender's place.
 * [latitude], [longitude] in degrees, [atMillis] when located, [label] the name of a meeting point.
 */
@Serializable
data class ChatSpot(
    val kind: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val atMillis: Long = 0,
    val accuracyMeters: Int? = null,
    val label: String? = null,
    /** [ALL]: until when everyone who joins shares, ms. */
    val untilMillis: Long? = null,
    /** [SHARE]: the level asked for; [SHARED]: the level agreed to, 0 for no (wire values of the levels). */
    val level: Int? = null,
) {
    companion object {
        const val MEET = 1
        const val ASK = 2
        const val HERE = 3

        /** „Alle teilen“ (ADR 0121 §13): an invitation to share in the circle until [untilMillis]. */
        const val ALL = 4

        /** „Angekommen“ (ADR 0121 §26): the sender arrived where it was going; the place and, if it has one, its name. */
        const val ARRIVED = 5

        /** „Standort freigeben“ (ADR 0123 §2): the sender asks that both see each other for good, at [level]. */
        const val SHARE = 6

        /** The answer to [SHARE]: [level] the level asked for as a yes, 0 for no. */
        const val SHARED = 7

        /** The sender ended the sharing for both. */
        const val ENDED = 8

        /** The sender took back its own open question. */
        const val WITHDRAWN = 9
    }
}

/**
 * Reference of an answer (ADR 0092): ANMP message ID [mid] of the answered message and [tag] of its author's
 * installation, both Base64url as on the wire. No text goes along; the app looks the message up itself.
 */
@Serializable
data class ReplyRef(val mid: String, val tag: String)

/**
 * A contact introduced in a message (ADR 0116): the person [person] (person ID) named [name], introduced by
 * [byPerson], valid until [until] (ms). [card]: the signed card the core needs to ask for the contact.
 */
@Serializable
data class ContactIntro(val id: String, val person: String, val name: String, val byPerson: String, val until: Long, val card: String)

/**
 * Preview of a web link (ADR 0069): [url] is in the text of the message; [image] is a small photo that is loaded
 * like an attachment but does not count among the attachments.
 */
@Serializable
data class LinkPreview(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val site: String? = null,
    val image: Attachment? = null,
)

/**
 * Reaction of a person to a message. The newest one ([ts]) applies. Empty [emoji]: withdrawn
 * (ADR 0021); the entry stays as a marker so that an older copy over the other path does not
 * bring it back.
 */
@Serializable
data class Reaction(val emoji: String, val ts: Long) {
    val withdrawn: Boolean get() = emoji.isEmpty()

    companion object {
        /** Key of the own reaction in [ChatMessage.reactions]. */
        const val SELF = "self"

        /** Offered for selection, as in WhatsApp. Every single emoji is accepted on receipt. */
        val CHOICES = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

        /** More than one emoji (including variants) is not a reaction. */
        const val MAX_BYTES = 32

        fun isValid(emoji: String) = emoji.isNotBlank() && emoji.encodeToByteArray().size <= MAX_BYTES
    }
}

/** Visible reactions, grouped by emoji in the order of the first ones: emoji → people. */
val ChatMessage.reactionGroups: List<Pair<String, List<String>>>
    get() = reactions.entries.filter { !it.value.withdrawn }.sortedBy { it.value.ts }
        .groupBy({ it.value.emoji }, { it.key })
        .toList()

/** Own reaction to this message, `null` = none. */
val ChatMessage.ownReaction: String?
    get() = reactions[Reaction.SELF]?.takeIf { !it.withdrawn }?.emoji

/**
 * Line in the circle chat, e.g. „Anna hat das Kreisbild geändert.“ The text is in [ChatMessage.text].
 * For „… hat Ben entfernt.“ this holds who was removed, so that every member can invite them again.
 */
@Serializable
data class SystemNote(
    val removedDevice: String? = null,
    val removedKey: String? = null,
    val removedName: String? = null,
    /** A step about SOS contacts with the person of the chat, as a card (ADR 0123 §5): [app.anynear.sos.CARD_ASKED_US] … */
    val sos: Int? = null,
)

/** Attachments were announced over radio that have not yet arrived over the internet. */
val ChatMessage.awaitsAttachments: Boolean
    get() = !outgoing && attachments.isEmpty() && RadioAnnouncement.announces(text)

/** This is how the message goes out over radio: text with announcement of the attachments. */
val ChatMessage.radioText: String
    get() = RadioAnnouncement.radioText(text, attachments.map { it.kind }, voiceMillis)

/**
 * What a message with a place says in the overview and in a notification (ADR 0121 §26): what the card says, not the
 * stand-in text with its link for older apps. `null` without a place.
 */
val ChatMessage.spotLine: String?
    get() = spot?.let { spotLineOf(it.kind, it.label, outgoing) }

/** [spotLine] for a place of the kind [kind] with the name [label]; also for the wake-up without the running app. */
fun spotLineOf(kind: Int, label: String?, outgoing: Boolean): String {
    val mt = app.anynear.i18n.texts.map
    return when (kind) {
        ChatSpot.MEET -> label?.let { mt.spotMeetText(it) } ?: mt.spotMeet
        ChatSpot.ASK -> mt.spotAsk
        ChatSpot.ALL -> mt.shareAll
        ChatSpot.ARRIVED -> if (outgoing) mt.arrivedOwn else mt.noticeArrived(label)
        ChatSpot.SHARE -> mt.mutualNotice
        ChatSpot.SHARED -> mt.mutualAnswerNotice
        ChatSpot.ENDED -> if (outgoing) mt.sharingEndedOwnNotice else mt.sharingEndedNotice
        ChatSpot.WITHDRAWN -> mt.sharingWithdrawnNotice
        else -> if (outgoing) mt.spotHereOwn else mt.noticeHere
    }
}

/** Short form for overview and notification: text, otherwise the attachments („📎 Bild“, „🎤 Sprachnachricht 0:42“). */
val ChatMessage.previewText: String
    get() = if (deleted) app.anynear.i18n.texts.chat.deletedMessage else if (text.isNotEmpty() || attachments.isEmpty()) text else RadioAnnouncement.radioText("", attachments.map { it.kind }, voiceMillis)

/** Length of the voice message in milliseconds; 0 without a recording or if unknown (ADR 0030). */
val ChatMessage.voiceMillis: Long
    get() = attachments.firstOrNull { it.kind == AttachmentKind.AUDIO }?.durationMillis ?: 0

/** Channel of the last plain Meshtastic text that came in [conversation]; replies go back there. `null` if none. */
fun replyChannel(messages: List<ChatMessage>, conversation: Conversation): Int? =
    messages.lastOrNull { it.conversation == conversation && !it.outgoing && it.radioChannel != null }?.radioChannel

/** Text received over radio, not yet assigned to a circle or person. */
data class IncomingText(
    val packetId: Int,
    val fromNodeNum: Int,
    /** `true`: direct message to us; `false`: to everyone in the channel [channelIndex]. */
    val direct: Boolean,
    val channelIndex: Int,
    val text: String,
    val timestampMillis: Long,
    /** Set: Meshtastic reaction ([text] is the emoji) to the radio packet with this ID (ADR 0018). */
    val reactionTo: Int = 0,
)

/**
 * ANMP packet received over radio (ADR 0021) on `PRIVATE_APP`, still unopened. [direct] and
 * [channelIndex] as in [IncomingText]. Who sent it is only stated in the packet itself.
 */
/** A packet from radio [node] was heard, after [hops] hops if the radio measured them (ADR 0090). */
class HeardNode(val node: Int, val hops: Int?)

class IncomingData(
    val packetId: Int,
    val fromNodeNum: Int,
    val direct: Boolean,
    val channelIndex: Int,
    val payload: ByteArray,
    val timestampMillis: Long,
    /** Hops over which the packet came (`hop_start − hop_limit`); `null` if the firmware does not report it. */
    val hops: Int? = null,
    /** Signal strength on receipt (SNR in dB); `null` if unknown. */
    val snr: Float? = null,
)

/** Own, connected radio. */
data class OwnDevice(
    val nodeNum: Int,
    val longName: String,
    val shortName: String,
    /** Battery level in percent, only if the device reports it. Values above 100 mean external power. */
    val batteryLevel: Int?,
)

/**
 * Software on the connected radio (ADR 0056): [version] as the firmware reports it (`2.5.6.d55c08d`),
 * [hwModel] as the name of the Meshtastic hardware model (`HELTEC_V3`, `T_ECHO`, `RAK4631`).
 */
data class RadioFirmware(val version: String, val hwModel: String, val hasWifi: Boolean, val hasBluetooth: Boolean)

/** Result of [MeshLink.installFirmware]. */
sealed interface FirmwareInstallResult {
    /** Written; whether the radio reports the new version is checked by the caller. */
    data object Written : FirmwareInstallResult

    /** The app does not support this path (yet); nothing was touched. */
    data object Unsupported : FirmwareInstallResult

    /** Aborted; [wroteAnything]: the radio was already in update mode or was written to. */
    data class Failed(val wroteAnything: Boolean) : FirmwareInstallResult
}

/** Result of setting up a circle channel on the radio. */
sealed interface ChannelSetupResult {
    /** Channel created or key renewed and confirmed by reading back. */
    data object Done : ChannelSetupResult

    data object NotConnected : ChannelSetupResult

    /** All 8 channel slots taken; nothing is overwritten without asking. */
    data object NoFreeSlot : ChannelSetupResult

    data class Failed(val reason: String) : ChannelSetupResult
}

/**
 * Value of the device profile that deviates on the radio ([RadioProfile]). [MQTT]: `config_ok_to_mqtt` off or
 * `ignore_mqtt` on (radio bridge, ADR 0054 §1, implementation plan phase 1).
 */

/** Comparison of the connected radio with the device profile. */
data class DeviceSetupCheck(
    val kind: Kind,
    val deviations: Set<ProfileDeviation>,
    /** Current long device name. */
    val currentName: String,
    /**
     * Only the radio area changed (ADR 0054 §1b): an app radio still on the settings of another area of the radio list
     * (or the default) deviates only in its radio values. The app switches it without asking and says so once.
     */
    val areaChange: Boolean = false,
) {
    enum class Kind {
        /** Matches the profile. */
        OK,

        /** Factory-new, no region set yet. */
        NEW,

        /** Set up differently. */
        DIFFERENT,

        /**
         * Already set up for anynear (name of the app), but no longer matches the current profile, usually
         * because the profile has changed with an app update. The name stays.
         */
        UPDATE,

        /** Set up as a distributor in the network (router role); setting up takes this task away from it. */
        ROUTER,
    }

    /** Identifier of the deviations, to remember a „Nicht jetzt“ for exactly this state. */
    val signature: String get() = deviations.map { it.name }.sorted().joinToString(",")
}

sealed interface DeviceSetupResult {
    /** Everything written in one go. The device restarts; afterwards it is checked again. */
    data object Done : DeviceSetupResult

    data object NotConnected : DeviceSetupResult

    data class Failed(val reason: String) : DeviceSetupResult
}

/** Meshtastic device found during the search. */
data class FoundDevice(
    val identifier: String,
    val name: String,
    /** Only known for Bluetooth; used for sorting (closer = further up). */
    val rssi: Int?,
    val connection: Connection,
) {
    enum class Connection { BLUETOOTH, NETWORK }
}

/** Radio assigned once. [identifier] is the MAC on Android, the CoreBluetooth UUID on iOS. */
data class PairedDevice(
    val identifier: String,
    val name: String,
)

/**
 * Answer of the target radio's firmware to a radio check (ADR 0053). All values come from the answer packet,
 * i.e. from the way back: [hops] relays (`hop_start` − `hop_limit`), [snr] in dB and [rssi] in dBm at the
 * last step to the own radio; `null` if unknown.
 */
data class RadioCheckReply(val hops: Int?, val snr: Float?, val rssi: Int?)
