package app.anynear

import app.anynear.data.MessageHistory
import app.anynear.data.MessageStore
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.IncomingText
import app.anynear.mesh.PeerId
import app.anynear.mesh.Reaction
import app.anynear.mesh.ownReaction
import app.anynear.mesh.reactionGroups
import app.anynear.ui.reactionDetails
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reactions with emojis (ADR 0018). */
class ReactionTest {
    private val store = MessageStore()
    private val circle = Conversation.Circle("k1")
    private val anna = PeerId.device("anna")
    private val ben = PeerId.device("ben")

    private fun message(store: MessageStore = this.store) =
        store.addOnline("r1", ChatMessage("m", circle, 5, "Hallo", 1_000, outgoing = false, fromPeer = anna, remoteId = "mid-1", radioPacketId = 77))

    @Test
    fun onePerPersonAndTheNewestWins() {
        message()
        assertTrue(store.setReaction("m", ben, "👍", 2_000))
        assertTrue(store.setReaction("m", ben, "❤️", 3_000))
        assertFalse(store.setReaction("m", ben, "😂", 2_500), "ältere Kopie ändert nichts")
        assertFalse(store.setReaction("m", ben, "❤️", 4_000), "dasselbe über den anderen Weg ändert nichts")

        assertEquals(listOf("❤️" to listOf(ben)), store.find("m")!!.reactionGroups)
    }

    @Test
    fun withdrawingRemovesAndOlderCopiesDoNotBringItBack() {
        message()
        store.setReaction("m", Reaction.SELF, "👍", 2_000)
        assertFalse(store.setReaction("m", ben, " ", 3_000), "Leerzeichen ist keine Reaktion")

        // Taking back (ADR 0021): empty, newer than the reaction.
        assertTrue(store.setReaction("m", Reaction.SELF, "", 3_000))
        assertNull(store.find("m")!!.ownReaction)
        assertEquals(emptyList(), store.find("m")!!.reactionGroups)

        // The older copy over the other path does not bring it back, a newer one does.
        assertFalse(store.setReaction("m", Reaction.SELF, "👍", 2_000))
        assertTrue(store.setReaction("m", Reaction.SELF, "❤️", 4_000))
        assertEquals("❤️", store.find("m")!!.ownReaction)
    }

    @Test
    fun groupsCountPeopleInOrderOfTheFirst() {
        message()
        store.setReaction("m", ben, "😂", 2_000)
        store.setReaction("m", Reaction.SELF, "👍", 3_000)
        store.setReaction("m", anna, "😂", 4_000)

        assertEquals(listOf("😂" to listOf(ben, anna), "👍" to listOf(Reaction.SELF)), store.find("m")!!.reactionGroups)
    }

    @Test
    fun targetIsFoundOverBothRoutes() {
        message()
        store.addOutgoing(ChatMessage("own", circle, 1, "Bin da", 1_500, outgoing = true, radioPacketId = 88))

        assertEquals("m", store.findByRadioPacket(circle, 77)?.localId)
        assertEquals("own", store.findByRadioPacket(circle, 88)?.localId)
        assertNull(store.findByRadioPacket(Conversation.Circle("anderer"), 77), "nur im selben Gespräch")
        assertNull(store.findByRadioPacket(circle, 0))

        assertEquals("m", store.findByRemote(circle, anna, "mid-1", ownMessage = false)?.localId)
        assertNull(store.findByRemote(circle, ben, "mid-1", ownMessage = false), "fremde Nachrichten-ID eines anderen Absenders")
        assertEquals("own", store.findByRemote(circle, null, "own", ownMessage = true)?.localId)
    }

    @Test
    fun radioRepeatIsCountedOnce() {
        assertTrue(store.rememberPacket(5, 900))
        assertFalse(store.rememberPacket(5, 900))
        assertTrue(store.rememberPacket(6, 900), "anderes Funkgerät")
        // The same packet list as for messages: what has been seen as a reaction does not become a message.
        assertFalse(store.addIncoming(IncomingText(900, 5, false, 1, "👍", 0, reactionTo = 77), localId = "x", conversation = circle))
    }

    @Test
    fun systemLinesGetNoReactions() {
        store.addSystem("s", circle, "Anna hat den Kreis umbenannt.")
        assertFalse(store.setReaction("s", ben, "👍", 1))
    }

    @Test
    fun reactionsSurviveARestart() {
        message()
        store.setReaction("m", ben, "🙏", 2_000)
        val json = Json { ignoreUnknownKeys = true }
        val saved = json.encodeToString(MessageHistory.Snapshot.serializer(), store.snapshot())

        val restored = MessageStore(restored = json.decodeFromString(MessageHistory.Snapshot.serializer(), saved))
        assertEquals(listOf("🙏" to listOf(ben)), restored.find("m")!!.reactionGroups)
    }

    @Test
    fun detailsNameWhoReactedHow() {
        message()
        store.setReaction("m", ben, "👍", 2_000)
        store.setReaction("m", Reaction.SELF, "👍", 3_000)
        store.setReaction("m", anna, "👍", 4_000)
        store.setReaction("m", PeerId.device("carla"), "❤️", 5_000)
        val names = mapOf(anna to "Anna", ben to "Ben", PeerId.device("carla") to "Carla")

        assertEquals(listOf("👍 Anna, Ben und du", "❤️ Carla"), store.find("m")!!.reactionDetails { names.getValue(it) })
    }
}
