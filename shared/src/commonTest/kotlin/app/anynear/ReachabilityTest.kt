package app.anynear

import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.PeerId
import app.anynear.mesh.Transport
import app.anynear.people.Reachability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Made-up people and radios, no real data. */
class ReachabilityTest {
    private val anna = PeerId.device("geraet-anna")
    private val circle = Conversation.Circle("k1")

    private fun incoming(id: String, ts: Long, via: Set<Transport>, node: Int = 0, from: String? = anna, arrived: Long? = null) = ChatMessage(
        localId = id, conversation = circle, fromNodeNum = node, text = "Text $id", timestampMillis = ts,
        outgoing = false, fromPeer = from, via = via, arrivedAtMillis = arrived,
    )

    private fun outgoing(id: String, conversation: Conversation, deliveredAt: Map<String, Long>, via: Set<Transport>) = ChatMessage(
        localId = id, conversation = conversation, fromNodeNum = 0, text = "Text $id", timestampMillis = 1,
        outgoing = true, recipients = deliveredAt.keys, deliveredTo = deliveredAt.keys, deliveredAt = deliveredAt, deliveredVia = via,
    )

    @Test
    fun nothingKnownWithoutMessages() {
        val r = Reachability.of(emptyList(), anna, null)
        assertNull(r.lastHeardMillis)
        assertEquals(emptyList(), r.radioNodes)
    }

    @Test
    fun newestMessageOrReceiptCounts() {
        val messages = listOf(
            incoming("a", ts = 10, via = setOf(Transport.INTERNET), arrived = 12),
            outgoing("b", Conversation.Direct(anna), mapOf(anna to 30L), setOf(Transport.RADIO)),
            outgoing("c", circle, mapOf(PeerId.device("geraet-ben") to 50L), setOf(Transport.INTERNET)),
        )
        val r = Reachability.of(messages, anna, null)
        assertEquals(30L, r.lastHeardMillis)
        assertEquals(setOf(Transport.RADIO), r.lastHeardVia)
    }

    @Test
    fun receiptInCircleHasNoClearWay() {
        val messages = listOf(outgoing("a", circle, mapOf(anna to 5L, PeerId.device("geraet-ben") to 6L), setOf(Transport.RADIO)))
        assertEquals(emptySet(), Reachability.of(messages, anna, null).lastHeardVia)
    }

    @Test
    fun radioDevicesFromMessagesAndContact() {
        val messages = listOf(
            incoming("a", ts = 1, via = setOf(Transport.RADIO), node = 7),
            incoming("b", ts = 2, via = setOf(Transport.RADIO), node = 9, from = null),
            incoming("c", ts = 3, via = setOf(Transport.INTERNET), node = 8),
        )
        assertEquals(listOf(9, 7), Reachability.of(messages, anna, 9).radioNodes)
    }
}
