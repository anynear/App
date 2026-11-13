package app.anynear

import app.anynear.data.LocalCipher
import app.anynear.data.MessageHistory
import app.anynear.data.MessageStore
import app.anynear.data.TextFile
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.IncomingText
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.PeerId
import app.anynear.mesh.Transport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MessageHistoryTest {
    private class MemoryFile(var content: String? = null) : TextFile {
        override fun read() = content

        override fun write(content: String) {
            this.content = content
        }
    }

    private val file = MemoryFile()
    private val history = MessageHistory(file, LocalCipher(ByteArray(32) { 1 }))
    private val circle = Conversation.Circle("k1")

    private fun restart() = MessageStore(restored = history.load())

    @Test
    fun historySurvivesRestart() {
        val store = MessageStore()
        store.addIncoming(IncomingText(1, 5, false, 1, "Hallo", 1_000), localId = "a", conversation = circle, fromPeer = PeerId.node(5))
        store.addOutgoing(outgoing("b", Conversation.Direct(PeerId.device("x"))))
        store.updateStatus("b", MessageStatus.CONFIRMED, Transport.INTERNET)
        history.save(store.snapshot())

        assertEquals(store.messages.value, restart().messages.value)
    }

    @Test
    fun noDuplicatesAfterRestart() {
        val store = MessageStore()
        val radio = IncomingText(7, 5, false, 1, "Bin unterwegs", 60_000)
        store.addIncoming(radio, localId = "r", conversation = circle)
        store.addOnline("id-1", online("o", text = "Hallo"))
        history.save(store.snapshot())

        val restarted = restart()
        assertFalse(restarted.addIncoming(radio, localId = "r2", conversation = circle))
        assertFalse(restarted.addOnline("id-1", online("o2", text = "Hallo")))
        // The same message still arrives over the internet after the restart: it is still matched.
        assertFalse(restarted.addOnline("id-2", online("o3", text = "Bin unterwegs", ts = 50_000)))
        assertEquals(2, restarted.messages.value.size)
    }

    @Test
    fun waitingMessagesAreNotSentAfterRestart() {
        val store = MessageStore()
        store.addOutgoing(outgoing("q", circle))
        history.save(store.snapshot())

        assertEquals(MessageStatus.FAILED, restart().messages.value.single().status)
    }

    @Test
    fun fileIsEncrypted() {
        val store = MessageStore()
        store.addOnline("id-1", online("o", text = "Geheimer Treffpunkt"))
        history.save(store.snapshot())

        assertFalse("Geheimer Treffpunkt" in file.content.orEmpty())
        // Not readable with another device key.
        assertNull(MessageHistory(file, LocalCipher(ByteArray(32) { 2 })).load())
    }

    @Test
    fun unreadableFileGivesEmptyHistory() {
        file.content = "kaputt"
        assertNull(history.load())
        assertEquals(emptyList(), restart().messages.value)
    }

    private fun online(localId: String, text: String, ts: Long = 0) = ChatMessage(
        localId = localId,
        conversation = circle,
        fromNodeNum = 5,
        text = text,
        timestampMillis = ts,
        outgoing = false,
        fromPeer = PeerId.device("x"),
        senderName = "Anna",
    )

    private fun outgoing(id: String, conversation: Conversation) = ChatMessage(
        localId = id,
        conversation = conversation,
        fromNodeNum = 0,
        text = "Hi",
        timestampMillis = 0,
        outgoing = true,
        status = MessageStatus.QUEUED,
        recipients = setOf(PeerId.device("x")),
    )
}
