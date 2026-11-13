package app.anynear

import app.anynear.chats.ReactionNotices
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.PeerId
import app.anynear.mesh.Reaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Notification for reactions to own messages (ADR 0018). */
class ReactionNoticesTest {
    private var now = 10_000_000L
    private val notices = ReactionNotices { now }
    private val circle = Conversation.Circle("k1")
    private val anna = PeerId.device("anna")
    private val own = ChatMessage("own", circle, 1, "Bin gleich da", 9_000_000, outgoing = true)
    private val theirs = ChatMessage("m", circle, 5, "Hallo", 9_000_000, outgoing = false, fromPeer = anna)

    @Test
    fun onlyOthersReactingToOwnMessages() {
        assertFalse(notices.add(theirs, anna, "👍", now), "fremde Nachricht")
        assertFalse(notices.add(own, Reaction.SELF, "👍", now), "eigene Reaktion, etwa vom anderen Gerät")
        assertTrue(notices.add(own, anna, "👍", now))

        val notice = notices.of(circle).single()
        assertEquals("👍", notice.emoji)
        assertEquals("Bin gleich da", notice.targetText)
    }

    @Test
    fun newestPerPersonWithdrawnAndOldOnesStaySilent() {
        notices.add(own, anna, "👍", now)
        notices.add(own, anna, "❤️", now)
        assertEquals(listOf("❤️"), notices.of(circle).map { it.emoji })

        assertFalse(notices.add(own, anna, "", now), "zurückgenommen")
        assertEquals(emptyList(), notices.of(circle))

        assertFalse(notices.add(own, anna, "👍", now - 2 * 60 * 60 * 1000L), "alt, etwa aus dem Verlauf")
    }

    @Test
    fun clearedWhenRead() {
        notices.add(own, anna, "👍", now)
        notices.clear(circle)
        assertEquals(emptyList(), notices.of(circle))
    }

    @Test
    fun longTextsAreShortened() {
        val long = own.copy(text = "a".repeat(80) + "\nzweite Zeile")
        notices.add(long, anna, "👍", now)
        assertEquals("a".repeat(40) + "…", notices.of(circle).single().targetText)
    }
}
