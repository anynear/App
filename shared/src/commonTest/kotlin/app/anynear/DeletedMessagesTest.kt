package app.anynear

import app.anynear.data.MessageStore
import app.anynear.i18n.localeTagOverride
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.Reaction
import app.anynear.mesh.Transport
import app.anynear.mesh.ChatSpot
import app.anynear.mesh.SystemNote
import app.anynear.mesh.isSystemCard
import app.anynear.mesh.previewText
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Deleting messages (ADR 0068) in the app's store. */
class DeletedMessagesTest {
    private val circle = Conversation.Circle("kreis")

    @BeforeTest
    fun german() {
        localeTagOverride = "de-DE"
    }

    @AfterTest
    fun reset() {
        localeTagOverride = null
    }

    private fun incoming(id: String, mid: String, text: String = "Hallo") = ChatMessage(
        localId = id, conversation = circle, fromNodeNum = 0, text = text, timestampMillis = 1_000, outgoing = false,
        fromPeer = "peer-a", remoteId = mid,
    )

    @Test
    fun deletingClearsContentButKeepsSenderAndTime() {
        val store = MessageStore(now = { 5_000 })
        assertTrue(store.addOnline("o1", incoming("l1", "m1")))
        store.setReaction("l1", Reaction.SELF, "👍", 2_000)
        assertTrue(store.markDeleted("l1"))
        val m = store.find("l1")!!
        assertTrue(m.deleted)
        assertEquals("", m.text)
        assertTrue(m.reactions.isEmpty())
        assertEquals("peer-a", m.fromPeer)
        assertEquals("Gelöschte Nachricht", m.previewText)
        assertFalse(store.markDeleted("l1"))
    }

    @Test
    fun deleteBeforeTheMessageKeepsItDeleted() {
        val store = MessageStore(now = { 5_000 })
        store.addDeleted("p1", circle, "peer-a", null, "m1", 3_000)
        // The message arrives afterwards: stays deleted, no second row.
        assertFalse(store.addOnline("o1", incoming("l1", "m1", "geheim")))
        val all = store.messages.value
        assertEquals(1, all.size)
        assertTrue(all.single().deleted)
        assertEquals("", all.single().text)
        assertTrue(Transport.INTERNET in all.single().via)
    }

    @Test
    fun deleteForMeRemovesOnlyThatMessage() {
        val store = MessageStore(now = { 5_000 })
        store.addOnline("o1", incoming("l1", "m1"))
        store.addOnline("o2", incoming("l2", "m2"))
        store.remove("l1")
        assertEquals(listOf("l2"), store.messages.value.map { it.localId })
    }

    @Test
    fun olderStoredMessagesReadAsNotDeletedWithoutPreview() {
        val old = """{"localId":"l1","conversation":{"type":"circle","circleId":"kreis"},"fromNodeNum":0,"text":"alt","timestampMillis":1,"outgoing":false}"""
        val m = Json { ignoreUnknownKeys = true }.decodeFromString(ChatMessage.serializer(), old)
        assertFalse(m.deleted)
        assertNull(m.link)
    }

    @Test
    fun systemCardsAreRecognized() {
        // Places, SOS steps and circle lines are cards of the app: nobody can delete them.
        assertTrue(incoming("l1", "m1").copy(spot = ChatSpot(kind = 3, latitude = 50.0, longitude = 8.0)).isSystemCard)
        assertTrue(incoming("l2", "m2").copy(system = SystemNote(sos = 1)).isSystemCard)
        assertTrue(incoming("l3", "m3").copy(system = SystemNote()).isSystemCard)
        assertFalse(incoming("l4", "m4").isSystemCard)
    }
}
