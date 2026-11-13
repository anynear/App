package app.anynear

import app.anynear.data.MessageStore
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.IncomingText
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.PeerId
import app.anynear.mesh.Transport
import app.anynear.mesh.replyChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageStoreTest {
    private val store = MessageStore()

    @Test
    fun anmpOverRadioAndInternetIsOneMessageByMessageId() {
        // ADR 0021: sender and message ID are the same on all paths, no matter how much time passes.
        val circle = Conversation.Circle("k1")
        fun copy(text: String, ts: Long) = ChatMessage(
            localId = "lokal-$ts", conversation = circle, fromNodeNum = 7, text = text, timestampMillis = ts,
            outgoing = false, fromPeer = PeerId.device("geraet-a"), remoteId = "mid-1",
        )
        assertTrue(store.addRadio(copy("📎 Bild: Hallo", 1_000), fromNodeNum = 7, packetId = 99))
        assertFalse(store.addRadio(copy("📎 Bild: Hallo", 1_000), fromNodeNum = 7, packetId = 99), "dasselbe Funkpaket")
        assertFalse(store.addOnline("briefkasten-1", copy("Hallo", 60 * 60 * 1000)), "dieselbe Nachricht übers Internet")

        val m = store.messages.value.single()
        assertEquals(setOf(Transport.RADIO, Transport.INTERNET), m.via)
        assertEquals("Hallo", m.text, "übers Internet ohne Ankündigung der Anhänge")
    }

    @Test
    fun radioCopyAfterInternetCountsAsArrivedByRadio() {
        // Internet is usually faster; the radio copy shall still count as „über Funk angekommen“ (test 02.11.2026).
        val direct = Conversation.Direct(PeerId.device("geraet-a"))
        val msg = ChatMessage(
            localId = "lokal-1", conversation = direct, fromNodeNum = 7, text = "F1", timestampMillis = 1_000,
            outgoing = false, fromPeer = PeerId.device("geraet-a"), remoteId = "mid-1",
        )
        assertTrue(store.addOnline("briefkasten-1", msg))
        assertFalse(store.arrivedVia("mid-1", PeerId.device("geraet-a"), Transport.RADIO))
        assertFalse(store.addRadio(msg.copy(localId = "lokal-2"), fromNodeNum = 7, packetId = 5))
        assertTrue(store.arrivedVia("mid-1", PeerId.device("geraet-a"), Transport.RADIO))
    }

    @Test
    fun sameRadioDeviceButOtherPersonIsNotMerged() {
        // Shared radio: same text, same radio, but two installations.
        val circle = Conversation.Circle("k1")
        fun from(device: String, mid: String) = ChatMessage(
            localId = "lokal-$device", conversation = circle, fromNodeNum = 7, text = "Bin da", timestampMillis = 1_000,
            outgoing = false, fromPeer = PeerId.device(device), remoteId = mid,
        )
        assertTrue(store.addRadio(from("anna", "mid-a"), fromNodeNum = 7, packetId = 1))
        assertTrue(store.addRadio(from("kevin", "mid-k"), fromNodeNum = 7, packetId = 2))
        assertEquals(2, store.messages.value.size)
    }
    private val circle = Conversation.Circle("k1")

    @Test
    fun repeatedPacketIsShownOnce() {
        val incoming = IncomingText(packetId = 99, fromNodeNum = 5, direct = false, channelIndex = 1, text = "Hallo", timestampMillis = 0)

        assertTrue(store.addIncoming(incoming, localId = "a", conversation = circle))
        assertFalse(store.addIncoming(incoming, localId = "b", conversation = circle))
        assertEquals(1, store.messages.value.size)
    }

    @Test
    fun statusNeverGoesBackwards() {
        store.addOutgoing(outgoing("x"))

        store.updateStatus("x", MessageStatus.CONFIRMED)
        store.updateStatus("x", MessageStatus.RELAYED)

        assertEquals(MessageStatus.CONFIRMED, store.messages.value.single().status)
    }

    @Test
    fun lateConfirmationUpgradesNoConfirmation() {
        store.addOutgoing(outgoing("x"))

        store.updateStatus("x", MessageStatus.NO_CONFIRMATION)
        store.updateStatus("x", MessageStatus.CONFIRMED)

        assertEquals(MessageStatus.CONFIRMED, store.messages.value.single().status)
    }

    @Test
    fun sameMessageOverRadioAndInternetIsShownOnce() {
        val radio = IncomingText(packetId = 7, fromNodeNum = 5, direct = false, channelIndex = 1, text = "Bin unterwegs", timestampMillis = 60_000)
        assertTrue(store.addIncoming(radio, localId = "r", conversation = circle))
        // Arrives shortly afterwards over the internet: is matched, not shown twice.
        assertFalse(store.addOnline("id-1", online("o", node = 5, text = "Bin unterwegs", ts = 30_000)))
        assertEquals(1, store.messages.value.size)
        // Same ID via a second mailbox: nothing new either.
        assertFalse(store.addOnline("id-1", online("o2", node = 5, text = "Bin unterwegs", ts = 30_000)))
        assertEquals(1, store.messages.value.size)
    }

    @Test
    fun repeatedShortAnswersStayRepeated() {
        // Twice „ok“, each over both paths: exactly two messages, matched one to one.
        assertTrue(store.addOnline("a", online("1", node = 5, text = "ok", ts = 0)))
        assertTrue(store.addOnline("b", online("2", node = 5, text = "ok", ts = 1_000)))
        assertFalse(store.addIncoming(IncomingText(1, 5, false, 1, "ok", 2_000), localId = "r1", conversation = circle))
        assertFalse(store.addIncoming(IncomingText(2, 5, false, 1, "ok", 3_000), localId = "r2", conversation = circle))
        assertEquals(2, store.messages.value.size)
    }

    @Test
    fun noMatchingOutsideWindowOrWithoutRadio() {
        assertTrue(store.addOnline("a", online("1", node = 5, text = "Hallo", ts = 0)))
        val late = MessageStore.CROSS_TRANSPORT_WINDOW_MILLIS + 1
        assertTrue(store.addIncoming(IncomingText(1, 5, false, 1, "Hallo", late), localId = "r", conversation = circle))
        // A sender without a radio (node 0) is never matched by the text.
        assertTrue(store.addOnline("b", online("2", node = 0, text = "Hi", ts = 0)))
        assertTrue(store.addOnline("c", online("3", node = 0, text = "Hi", ts = 0)))
        assertEquals(4, store.messages.value.size)
    }

    @Test
    fun incomingRemembersEveryWayItArrived() {
        val radio = IncomingText(packetId = 7, fromNodeNum = 5, direct = false, channelIndex = 1, text = "Bin da", timestampMillis = 0)
        store.addIncoming(radio, localId = "r", conversation = circle)
        assertEquals(setOf(Transport.RADIO), store.messages.value.single().via)

        store.addOnline("id-1", online("o", node = 5, text = "Bin da", ts = 0))
        assertEquals(setOf(Transport.RADIO, Transport.INTERNET), store.messages.value.single().via)
    }

    @Test
    fun outgoingRemembersWaysOutAndWayIn() {
        store.addOutgoing(outgoing("x"))
        store.updateStatus("x", MessageStatus.FAILED, Transport.RADIO)
        assertEquals(emptySet(), store.messages.value.single().via, "nicht angenommen heißt nicht gesendet")

        store.updateStatus("x", MessageStatus.ONLINE, Transport.INTERNET)
        store.updateStatus("x", MessageStatus.RELAYED, Transport.RADIO)
        store.updateStatus("x", MessageStatus.CONFIRMED, Transport.RADIO)

        val message = store.messages.value.single()
        assertEquals(setOf(Transport.INTERNET, Transport.RADIO), message.via)
        assertEquals(setOf(Transport.RADIO), message.deliveredVia)
        assertEquals(MessageStatus.CONFIRMED, message.status)
    }

    @Test
    fun circleMessageCountsAsArrivedOnlyWhenAllConfirmed() {
        store.addOutgoing(outgoing("x").copy(conversation = circle, recipients = setOf("d:a", "d:b")))
        store.updateStatus("x", MessageStatus.ONLINE, Transport.INTERNET)

        store.confirmDelivery("x", "d:a", Transport.INTERNET)
        store.confirmDelivery("x", "d:fremd", Transport.INTERNET)
        assertEquals(MessageStatus.ONLINE, store.messages.value.single().status)
        assertEquals(setOf("d:a"), store.messages.value.single().deliveredTo)

        store.confirmDelivery("x", "d:b", Transport.INTERNET)
        store.confirmDelivery("x", "d:a", Transport.INTERNET) // A repetition does not change the time.
        assertEquals(MessageStatus.CONFIRMED, store.messages.value.single().status)
        assertEquals(setOf("d:a", "d:b"), store.messages.value.single().deliveredAt.keys)
        assertEquals(setOf(Transport.INTERNET), store.messages.value.single().deliveredVia)
    }

    @Test
    fun removedMemberNoLongerHoldsBackArrival() {
        store.addOutgoing(outgoing("x").copy(conversation = circle, recipients = setOf("d:a", "d:b", "d:c")))
        store.addOutgoing(outgoing("y").copy(conversation = Conversation.Circle("k2"), recipients = setOf("d:a", "d:b")))
        store.updateStatus("x", MessageStatus.ONLINE, Transport.INTERNET)
        store.updateStatus("y", MessageStatus.ONLINE, Transport.INTERNET)
        store.confirmDelivery("x", "d:a", Transport.INTERNET)
        store.confirmDelivery("x", "d:c", Transport.INTERNET)
        store.confirmDelivery("y", "d:a", Transport.INTERNET)

        store.dropRecipient(circle, "d:b")
        store.dropRecipient(circle, "d:c") // Has already confirmed, stays in the message info.

        val x = store.messages.value.first { it.localId == "x" }
        assertEquals(MessageStatus.CONFIRMED, x.status)
        assertEquals(setOf("d:a", "d:c"), x.recipients)
        assertEquals(x.deliveredAt.values.max(), x.arrivedAtMillis)
        val y = store.messages.value.first { it.localId == "y" }
        assertEquals(MessageStatus.ONLINE, y.status, "anderer Kreis bleibt unberührt")
        assertEquals(setOf("d:a", "d:b"), y.recipients)
    }

    @Test
    fun remembersWhenMessagesArrived() {
        var clock = 1_000L
        val store = MessageStore(now = { clock })
        // First over radio (the radio's receive time), then over the internet with the send time.
        store.addIncoming(IncomingText(7, 5, false, 1, "Bin da", timestampMillis = 900), localId = "r", conversation = circle)
        clock = 2_000L
        store.addOnline("id-1", online("o", node = 5, text = "Bin da", ts = 800))
        val incoming = store.messages.value.single()
        assertEquals(1_000L, incoming.arrivedAtMillis, "angekommen ist sie beim ersten Mal")
        assertEquals(800L, incoming.timestampMillis, "Sendezeitpunkt kommt übers Internet")

        clock = 3_000L
        store.addOutgoing(outgoing("x").copy(conversation = circle, recipients = setOf("d:a")))
        store.confirmDelivery("x", "d:a", Transport.INTERNET)
        clock = 4_000L
        store.updateStatus("x", MessageStatus.CONFIRMED, Transport.RADIO)
        assertEquals(3_000L, store.find("x")?.arrivedAtMillis)
    }

    @Test
    fun receiptBeforeOwnCopyIsKept() {
        var clock = 1_000L
        val store = MessageStore(now = { clock })
        // The copy from another own device (ADR 0042) arrives after the confirmations from Anna and Ben.
        store.confirmDelivery("x", "d:a", Transport.INTERNET)
        clock = 1_500L
        store.confirmRead("x", "d:b", Transport.INTERNET)
        store.confirmDelivery("y", "d:a", Transport.INTERNET)
        clock = 2_000L
        val copy = outgoing("x").copy(conversation = circle, recipients = setOf("d:a", "d:b"), status = MessageStatus.ONLINE, remoteId = "x")
        check(store.addOnline("obj-x", copy))

        val x = store.find("x")!!
        assertEquals(setOf("d:a", "d:b"), x.deliveredTo)
        assertEquals(mapOf("d:a" to 1_000L, "d:b" to 1_500L), x.deliveredAt, "Zeit der Bestätigung, nicht der Kopie")
        assertEquals(setOf("d:b"), x.readBy)
        assertEquals(MessageStatus.CONFIRMED, x.status, "angekommen bei allen, gelesen erst von einem")

        // If the same copy arrives again (say from the history), nothing counts twice; „y“ keeps waiting.
        store.addFromOwnDevice(listOf(copy))
        assertEquals(x, store.find("x"))
        assertEquals(null, store.find("y"))
        store.addFromOwnDevice(listOf(outgoing("y").copy(conversation = circle, recipients = setOf("d:a"), status = MessageStatus.ONLINE)))
        assertEquals(MessageStatus.CONFIRMED, store.find("y")?.status, "aus dem Verlauf nachgeholt, sobald sie da ist")
    }

    private fun online(localId: String, node: Int, text: String, ts: Long) = ChatMessage(
        localId = localId,
        conversation = Conversation.Circle("k1"),
        fromNodeNum = node,
        text = text,
        timestampMillis = ts,
        outgoing = false,
        fromPeer = PeerId.device("geraet-a"),
        senderName = "Testperson",
    )

    private fun outgoing(id: String) = ChatMessage(
        localId = id,
        conversation = Conversation.Direct(PeerId.node(5)),
        fromNodeNum = 1,
        text = "Test",
        timestampMillis = 0,
        outgoing = true,
        status = MessageStatus.QUEUED,
    )

    @Test
    fun historyFromAnOwnDeviceIsTakenOnceAndNeverResent() {
        val chat = Conversation.Direct(PeerId.device("ben"))
        val mine = ChatMessage(localId = "m1", conversation = chat, fromNodeNum = 0, text = "Hallo", timestampMillis = 1, outgoing = true, status = MessageStatus.ONLINE)
        val his = ChatMessage(localId = "m2", conversation = chat, fromNodeNum = 0, text = "Hi", timestampMillis = 2, outgoing = false, fromPeer = PeerId.device("ben"), remoteId = "r2", unread = true)
        assertEquals(2, store.addFromOwnDevice(listOf(mine, his)))
        assertEquals(0, store.addFromOwnDevice(listOf(mine, his)))
        assertTrue(store.resumable().isEmpty(), "eigene Nachrichten hat das andere Gerät schon verschickt")
        assertTrue(store.messages.value.none { it.unread })
    }

    @Test
    fun replyGoesBackOnTheChannelTheRadioWroteOn() {
        val chat = Conversation.Direct(PeerId.node(42))
        assertEquals(null, replyChannel(store.messages.value, chat))
        store.addIncoming(IncomingText(packetId = 1, fromNodeNum = 42, direct = true, channelIndex = 0, text = "Hallo", timestampMillis = 1_000), "a", chat)
        assertEquals(0, store.messages.value.single().radioChannel)
        assertEquals(0, replyChannel(store.messages.value, chat))
        store.addIncoming(IncomingText(packetId = 2, fromNodeNum = 42, direct = true, channelIndex = 1, text = "Noch da?", timestampMillis = 2_000), "b", chat)
        assertEquals(1, replyChannel(store.messages.value, chat), "der zuletzt genutzte Kanal zählt")
        assertEquals(null, replyChannel(store.messages.value, Conversation.Direct(PeerId.node(7))))
    }
}
