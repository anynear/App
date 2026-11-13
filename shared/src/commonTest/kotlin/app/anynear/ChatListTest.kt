package app.anynear

import app.anynear.chats.ChatList
import app.anynear.chats.conversationOf
import app.anynear.chats.key
import app.anynear.circles.Circle
import app.anynear.data.MessageStore
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.IncomingText
import app.anynear.mesh.PeerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Made-up circles and people, no real data. */
class ChatListTest {
    private val family = Circle("k1", "Familie", "geheim")
    private val friends = Circle("k2", "Freunde", "geheim")
    private val gone = Circle("k3", "Alter Kreis", "geheim", removed = true)
    private val anna = PeerId.device("geraet-anna")
    private val names = mapOf(anna to "Anna")

    private fun build(messages: List<ChatMessage>) =
        ChatList.build(listOf(family, friends, gone), messages, { names[it] ?: "Unbekannt" }, { it.senderName ?: "?" })

    private fun incoming(id: String, conversation: Conversation, ts: Long, unread: Boolean = true) = ChatMessage(
        localId = id, conversation = conversation, fromNodeNum = 0, text = "Text $id", timestampMillis = ts,
        outgoing = false, fromPeer = anna, senderName = "Anna", unread = unread,
    )

    @Test
    fun newestConversationFirstEmptyCirclesAfterRemovedLast() {
        val list = build(
            listOf(
                incoming("a", Conversation.Circle(family.id), ts = 10),
                incoming("b", Conversation.Direct(anna), ts = 20),
            ),
        )

        assertEquals(listOf("Anna", "Familie", "Freunde", "Alter Kreis"), list.map { it.title })
        assertNull(list[2].last)
        assertTrue(list[3].removed)
    }

    @Test
    fun countsUnreadAndNamesSenderOnlyInCircles() {
        val circle = Conversation.Circle(family.id)
        val list = build(
            listOf(
                incoming("a", circle, ts = 1),
                incoming("b", circle, ts = 2),
                incoming("c", circle, ts = 3, unread = false),
                incoming("d", Conversation.Direct(anna), ts = 0),
            ),
        )

        val familyEntry = list.first { it.title == "Familie" }
        assertEquals(2, familyEntry.unread)
        assertEquals("Anna", familyEntry.lastSender)
        assertEquals("Text c", familyEntry.last?.text)
        assertNull(list.first { it.title == "Anna" }.lastSender)
    }

    @Test
    fun ownMessageShowsYou() {
        val own = ChatMessage("x", Conversation.Circle(friends.id), 0, "Bin da", 5, outgoing = true)
        assertEquals("Du", build(listOf(own)).first().lastSender)
    }

    @Test
    fun conversationKeyRoundTrip() {
        listOf(Conversation.Circle("k1"), Conversation.Direct(anna), Conversation.Direct(PeerId.node(42))).forEach {
            assertEquals(it, conversationOf(it.key()))
        }
        assertNull(conversationOf("unsinn"))
    }

    @Test
    fun storeMarksIncomingUnreadUntilRead() {
        val store = MessageStore()
        val circle = Conversation.Circle(family.id)
        store.addIncoming(IncomingText(1, 5, false, 1, "Hallo", 0), localId = "r", conversation = circle)
        store.addOnline("o1", incoming("o", Conversation.Direct(anna), ts = 1, unread = false))
        store.addOutgoing(ChatMessage("x", circle, 0, "Ich", 2, outgoing = true))

        assertEquals(listOf(true, true, false), store.messages.value.map { it.unread })

        store.markRead(circle)
        assertFalse(store.messages.value.first { it.localId == "r" }.unread)
        assertTrue(store.messages.value.first { it.localId == "o" }.unread)
    }
}
