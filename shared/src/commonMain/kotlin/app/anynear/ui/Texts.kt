package app.anynear.ui

import app.anynear.i18n.texts
import app.anynear.mesh.LinkState
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.Reaction
import app.anynear.mesh.Transport
import app.anynear.mesh.reactionGroups

/** All states in everyday language. No radio or mesh terms. */
internal fun LinkState.label(): String = when (this) {
    LinkState.NoDevice -> texts.chat.linkNoDevice
    LinkState.BluetoothUnavailable -> texts.chat.linkBluetoothUnavailable
    LinkState.Searching -> texts.chat.linkSearching
    LinkState.Connecting -> texts.chat.linkConnecting
    LinkState.Connected -> texts.chat.linkConnected
    LinkState.Lost -> texts.chat.linkLost
    LinkState.InUseElsewhere -> texts.chat.linkInUseElsewhere
}

internal fun LinkState.hint(appName: String): String? = when (this) {
    LinkState.Searching -> texts.chat.linkSearchingHint
    LinkState.BluetoothUnavailable -> texts.chat.linkBluetoothHint(appName)
    LinkState.Lost -> texts.chat.linkLostHint
    LinkState.InUseElsewhere -> texts.chat.linkInUseElsewhereHint
    else -> null
}

internal fun MessageStatus.label(isDirect: Boolean): String = when (this) {
    MessageStatus.QUEUED -> texts.chat.statusSending
    MessageStatus.HANDED_TO_RADIO -> texts.chat.statusHandedToRadio
    MessageStatus.RELAYED -> if (isDirect) texts.chat.statusSentAwaitingConfirmation else texts.chat.statusSent
    MessageStatus.ONLINE -> texts.chat.statusSent
    MessageStatus.CONFIRMED -> texts.chat.statusDelivered
    MessageStatus.READ -> texts.chat.statusRead
    MessageStatus.NO_CONFIRMATION -> texts.chat.statusSentNoConfirmation
    MessageStatus.FAILED -> texts.chat.statusNotSent
}

internal fun Transport.label(): String = when (this) {
    Transport.INTERNET -> texts.common.internet
    Transport.RADIO -> texts.common.radio
}

/** „Internet und Funk“, in a fixed order. */
internal fun Set<Transport>.label(): String = Transport.entries.filter { it in this }.map { it.label() }.let { texts.common.list(it) }

/** Icon of a line in the details of the message, as in WhatsApp. */
internal enum class DeliveryMark {
    /** Sent: one check mark. */
    SENT,

    /** Arrived or received: two check marks. */
    ARRIVED,

    /** Read: two colored (yellow) check marks. */
    READ,

    /** Without an icon, e.g. a person in the circle. */
    NONE,
}

/** One line of the details: [text] without the route, the route is in [via] and is shown as an icon. */
internal data class DeliveryLine(val mark: DeliveryMark, val text: String, val via: Set<Transport> = emptySet(), val indent: Boolean = false) {
    /** The same line in words, for screen readers. */
    val spoken: String get() = when {
        via.isEmpty() -> text
        // Header line of a list („Angekommen bei allen:“): the route before the colon.
        text.endsWith(":") -> text.dropLast(1) + " ${texts.chat.via} ${via.label()}:"
        else -> "$text ${texts.chat.via} ${via.label()}"
    }
}

/**
 * Detailed information below a message (via the „i“ next to it), one per line and always complete:
 * when and by which route it was sent, when and by which route it arrived and whether and when it
 * was read, also „noch nicht“ (ADR 0019; in the circle „gelesen“ only as a number, without names). [format]
 * formats date and time.
 */
internal fun ChatMessage.deliveryLines(isDirect: Boolean, format: (Long) -> String, nameOf: (String) -> String = { it }): List<DeliveryLine> {
    val lines = mutableListOf<DeliveryLine>()
    if (!outgoing) {
        // Received only over radio: the radio packet carries no send time.
        if (Transport.INTERNET in via) lines += DeliveryLine(DeliveryMark.SENT, texts.chat.sentAt(format(timestampMillis)))
        val at = arrivedAtMillis ?: timestampMillis
        lines += DeliveryLine(DeliveryMark.ARRIVED, texts.chat.receivedAt(format(at)), via)
        if (relayed) lines += DeliveryLine(DeliveryMark.NONE, texts.chat.relayedByOther)
        return lines
    }
    lines += if (via.isNotEmpty()) {
        DeliveryLine(DeliveryMark.SENT, texts.chat.sentAt(format(timestampMillis)), via)
    } else {
        DeliveryLine(DeliveryMark.SENT, texts.chat.sentAt(format(timestampMillis)) + status?.let { " (${it.label(isDirect)})" }.orEmpty())
    }
    if (!isDirect && recipients.isNotEmpty()) {
        // Circle: like the message info of a WhatsApp group, every person individually.
        lines += DeliveryLine(
            DeliveryMark.ARRIVED,
            if (deliveredTo.containsAll(recipients)) texts.chat.deliveredToAll else texts.chat.deliveredToSome(deliveredTo.size, recipients.size),
            deliveredVia,
        )
        recipients.map { it to nameOf(it) }.sortedBy { it.second.lowercase() }.forEach { (peer, name) ->
            lines += DeliveryLine(
                DeliveryMark.NONE,
                "$name: " + (deliveredAt[peer]?.let(format) ?: if (peer in deliveredTo) texts.chat.deliveredLower else texts.chat.notYet),
                indent = true,
            )
        }
        val readers = recipients - readGone
        if (readers.isNotEmpty()) {
            val count = readBy.count { it in readers }
            lines += DeliveryLine(
                DeliveryMark.READ,
                when {
                    count == readers.size -> texts.chat.readByAll
                    count == 0 -> texts.chat.readByNone
                    else -> texts.chat.readBySome(count, readers.size)
                },
                readVia,
            )
        }
        return lines
    }
    val readAtMillis = readAt.values.maxOrNull()
    if (status?.arrived == true) {
        // Read over radio without a delivery receipt: „angekommen“ is part of „gelesen“, so it came by the same route.
        val arrivedAt = arrivedAtMillis ?: readAtMillis
        lines += DeliveryLine(DeliveryMark.ARRIVED, texts.chat.deliveredAt(arrivedAt?.let(format) ?: texts.common.yes), deliveredVia.ifEmpty { readVia })
    } else if (via.isNotEmpty() && isDirect) {
        lines += DeliveryLine(DeliveryMark.ARRIVED, texts.chat.notDeliveredYet)
    }
    if (status == MessageStatus.READ) {
        lines += DeliveryLine(DeliveryMark.READ, texts.chat.readAt(readAtMillis?.let(format) ?: texts.common.yes), readVia)
    } else if (status?.arrived == true && isDirect) {
        lines += DeliveryLine(DeliveryMark.READ, texts.chat.notReadYet)
    }
    return lines
}

/** [deliveryLines] in words, one per line. */
internal fun ChatMessage.deliveryDetails(isDirect: Boolean, format: (Long) -> String, nameOf: (String) -> String = { it }): String =
    deliveryLines(isDirect, format, nameOf).joinToString("\n") { it.spoken }

/**
 * Route icons in the footer (always only one group, directly after the check marks): outgoing, before
 * arriving the route by which it went out, afterwards the route by which it arrived; incoming the route by which
 * it arrived.
 */
internal val ChatMessage.footerVia: Set<Transport>
    get() = when {
        !outgoing -> via
        status?.arrived == true -> deliveredVia + readVia
        else -> via
    }

/**
 * Who reacted how (ADR 0018), one line per emoji, e.g. „👍 Anna, Ben und du“. For the
 * details of the message (via the „i“ next to it); empty without reactions.
 */
internal fun ChatMessage.reactionDetails(nameOf: (String) -> String): List<String> =
    reactionGroups.map { (emoji, peers) ->
        val others = peers.filter { it != Reaction.SELF }.map(nameOf).sortedBy { it.lowercase() }
        val names = if (Reaction.SELF in peers) others + texts.common.you else others
        "$emoji " + texts.common.list(names)
    }
