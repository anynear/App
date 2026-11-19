package app.anynear

import app.anynear.chats.ReadUntil
import app.anynear.circles.ProfileRepository
import app.anynear.data.MessageStore
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.PeerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Read up to a point per conversation on all own devices and after the emergency card (setting `read_until`, FUT B9). */
class ReadUntilTest {
    private val circle = Conversation.Circle("kreis-1")
    private val direct = Conversation.Direct(PeerId.device("person-b"))

    private fun incoming(conversation: Conversation, id: String, sentAt: Long) = ChatMessage(
        localId = "lokal-$id", conversation = conversation, fromNodeNum = 0, text = id, timestampMillis = sentAt,
        outgoing = false, fromPeer = PeerId.device("person-b"), remoteId = "mid-$id",
    )

    @Test
    fun keysAreTheSameForAppAndWebApp() {
        assertEquals("c:kreis-1", ReadUntil.keyOf(circle))
        assertEquals("d:person-b", ReadUntil.keyOf(direct))
        assertNull(ReadUntil.keyOf(Conversation.Direct(PeerId.node(42))), "nur über Funk, ohne anynear")
        assertEquals(circle, ReadUntil.conversationOf("c:kreis-1"))
        assertEquals(direct, ReadUntil.conversationOf("d:person-b"))
        assertNull(ReadUntil.conversationOf("x:1"))
    }

    @Test
    fun laterTimeWinsAndGoesWithThePersonalSettings() {
        val phone = ProfileRepository(InMemoryStore())
        phone.markReadUntil("c:kreis-1", 100L)
        phone.markReadUntil("c:kreis-1", 50L)
        assertEquals(100L, phone.readUntil.value["c:kreis-1"], "ein früherer Zeitpunkt senkt nichts")

        val restored = ProfileRepository(InMemoryStore())
        restored.markReadUntil("d:person-b", 7L)
        // The restored phone had something the network did not know yet: write back.
        assertTrue(restored.applyPersonalSettings(phone.personalSettings()))
        assertEquals(mapOf("c:kreis-1" to 100L, "d:person-b" to 7L), restored.readUntil.value)
        assertEquals(emptyMap(), ReadUntil.decode("kaputt"))
    }

    @Test
    fun withAMarkOnlyNewerMessagesStayUnread() {
        assertTrue(ReadUntil.readOnArrival(sentAt = 100, mark = 100, loadingSince = 50))
        assertFalse(ReadUntil.readOnArrival(sentAt = 101, mark = 100, loadingSince = 500), "nach dem Lesen gekommen, auf dem alten Handy ungelesen")
    }

    @Test
    fun withoutAMarkEverythingBeforeLoadingCountsAsRead() {
        assertTrue(ReadUntil.readOnArrival(sentAt = 499_000, mark = null, loadingSince = 500_000))
        assertFalse(ReadUntil.readOnArrival(sentAt = 500_000, mark = null, loadingSince = 500_000), "kam erst während des Ladens")
    }

    @Test
    fun aMessageFromTheSecondLoadingBeganIsUnread() {
        // Send times are whole seconds: sent at 500.7 s it says 500 s, after loading began at 500.4 s.
        assertFalse(ReadUntil.readOnArrival(sentAt = 500_000, mark = null, loadingSince = 500_400))
        assertTrue(ReadUntil.readOnArrival(sentAt = 499_000, mark = null, loadingSince = 500_400))
    }

    @Test
    fun restoredMessagesComeInReadAndAMarkFromAnotherDeviceReadsSilently() {
        val store = MessageStore()
        assertTrue(store.addOnline("o1", incoming(circle, "1", 10), read = true))
        assertTrue(store.addOnline("o2", incoming(circle, "2", 20)))
        assertTrue(store.addOnline("o3", incoming(circle, "3", 30)))
        assertEquals(listOf(false, true, true), store.messages.value.map { it.unread })
        assertEquals(30L, store.newestSentAt(circle))

        assertTrue(store.markReadUntil(circle, 20))
        assertEquals(listOf(false, false, true), store.messages.value.map { it.unread })
        assertFalse(store.markReadUntil(circle, 20), "nichts mehr zu ändern")
        assertFalse(store.markReadUntil(direct, 100), "anderes Gespräch")
    }
}
