package app.anynear

import app.anynear.data.MessageStore
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.IncomingText
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.Transport
import app.anynear.ui.deliveryDetails
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Read receipt over internet and radio (ADR 0019; over radio as ANMP, ADR 0021, see AnmpTest). */
class ReadReceiptTest {
    private val store = MessageStore()
    private val circle = Conversation.Circle("k1")

    private fun outgoing(id: String, conversation: Conversation = circle, recipients: Set<String> = setOf("d:a", "d:b")) = ChatMessage(
        localId = id,
        conversation = conversation,
        fromNodeNum = 1,
        text = "Hallo",
        timestampMillis = 0,
        outgoing = true,
        status = MessageStatus.ONLINE,
        via = setOf(Transport.INTERNET),
        recipients = recipients,
    )

    @Test
    fun circleTurnsReadOnlyWhenEveryoneHasRead() {
        store.addOutgoing(outgoing("x"))

        store.confirmRead("x", "d:a", Transport.INTERNET)
        var x = store.find("x")!!
        assertEquals(MessageStatus.ONLINE, x.status, "einer von zweien")
        assertEquals(setOf("d:a"), x.deliveredTo, "gelesen heißt auch angekommen")

        store.confirmRead("x", "d:b", Transport.RADIO)
        x = store.find("x")!!
        assertEquals(MessageStatus.READ, x.status)
        assertEquals(setOf(Transport.INTERNET, Transport.RADIO), x.readVia)
    }

    @Test
    fun strangersAndOldConfirmationsDoNotCount() {
        store.addOutgoing(outgoing("x"))
        store.confirmRead("x", "d:fremd", Transport.INTERNET)
        assertTrue(store.find("x")!!.readBy.isEmpty())

        store.confirmRead("x", "d:a", Transport.INTERNET)
        store.confirmRead("x", "d:b", Transport.INTERNET)
        store.updateStatus("x", MessageStatus.CONFIRMED, Transport.RADIO)
        assertEquals(MessageStatus.READ, store.find("x")!!.status, "nie zurück auf angekommen")
    }

    @Test
    fun directMessageOverRadioOnlyIsReadByThePartner() {
        val direct = Conversation.Direct("n:42")
        store.addOutgoing(outgoing("x", direct, recipients = emptySet()).copy(radioPacketId = 7))

        store.confirmRead("x", "n:43", Transport.RADIO)
        assertEquals(MessageStatus.ONLINE, store.find("x")!!.status)
        store.confirmRead("x", "n:42", Transport.RADIO)
        assertEquals(MessageStatus.READ, store.find("x")!!.status)
    }

    @Test
    fun memberWhoLeftDoesNotHoldBackRead() {
        store.addOutgoing(outgoing("x"))
        store.confirmDelivery("x", "d:b", Transport.INTERNET)
        store.confirmRead("x", "d:a", Transport.INTERNET)

        store.dropRecipient(circle, "d:b")
        val x = store.find("x")!!
        assertEquals(MessageStatus.READ, x.status)
        assertEquals(setOf("d:a", "d:b"), x.recipients, "angekommen bleibt in der Nachrichteninfo")
    }

    @Test
    fun markReadReturnsWhatWasUnread() {
        store.addIncoming(IncomingText(packetId = 9, fromNodeNum = 5, direct = false, channelIndex = 1, text = "Hi", timestampMillis = 0), "a", circle)
        assertEquals(listOf("a"), store.markRead(circle).map { it.localId })
        assertTrue(store.markRead(circle).isEmpty(), "nur einmal bestätigen")
    }

    @Test
    fun detailsShowReadCountInCircleAndTimeInDirect() {
        store.addOutgoing(outgoing("x", recipients = setOf("d:a", "d:b", "d:c")))
        store.confirmRead("x", "d:a", Transport.INTERNET)
        val details = store.find("x")!!.deliveryDetails(isDirect = false, format = { "t" }, nameOf = { it })
        assertTrue(details.endsWith("Von 1 von 3 gelesen über Internet"), details)

        val direct = Conversation.Direct("d:a")
        store.addOutgoing(outgoing("y", direct, recipients = setOf("d:a")))
        store.confirmRead("y", "d:a", Transport.RADIO)
        val y = store.find("y")!!.deliveryDetails(isDirect = true, format = { "t" })
        assertTrue(y.endsWith("Gelesen: t über Funk"), y)
    }
}
