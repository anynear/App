package app.anynear.people

import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.Transport

/**
 * What we know from the message history about a person's reachability, for the detail page
 * of a contact. Derived locally only, none of it is reported.
 */
data class Reachability(
    /** When something last came from the person: a message or a delivery receipt. */
    val lastHeardMillis: Long?,
    /** Path by which that last arrived; empty if unknown. */
    val lastHeardVia: Set<Transport>,
    /** Radios through which the person has written so far. */
    val radioNodes: List<Int>,
) {
    companion object {
        /**
         * [peer] as [app.anynear.mesh.PeerId]; [nodeNum] is their radio, if known.
         * Only what verifiably came from this person ([ChatMessage.fromPeer]) is counted, not everything
         * from their radio: a radio is often used by several people (ADR 0021).
         */
        fun of(messages: List<ChatMessage>, peer: String, nodeNum: Int?): Reachability {
            val node = nodeNum?.takeIf { it != 0 }
            val fromThem = messages.filter { m -> !m.outgoing && m.system == null && m.fromPeer == peer }
            val heard = fromThem.map { (it.arrivedAtMillis ?: it.timestampMillis) to it.via } +
                messages.filter { it.outgoing }.mapNotNull { m ->
                    // The path of the acknowledgements is unambiguously this person's only in a direct message.
                    m.deliveredAt[peer]?.let { at -> at to if (m.conversation is Conversation.Direct) m.deliveredVia else emptySet() }
                }
            val last = heard.maxByOrNull { it.first }
            val radio = fromThem.filter { Transport.RADIO in it.via && it.fromNodeNum != 0 }.map { it.fromNodeNum }
            return Reachability(
                lastHeardMillis = last?.first,
                lastHeardVia = last?.second.orEmpty(),
                radioNodes = (listOfNotNull(node) + radio).distinct(),
            )
        }
    }
}
