package app.anynear

import app.anynear.chats.ChatList
import app.anynear.chats.ListedContact
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
    private val choir = Circle("k4", "Chor", "geheim", joinedAt = 25)
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
    fun noteAfterAnIntroductionDoesNotReplaceTheLastMessageOfADirectChat() {
        // „Anna möchte Carla hinzufügen …“ only the introducer's device has: the overview keeps the last real message, as
        // in the web app. With nothing else the line stands for the conversation; in circles lines stay the last message.
        val direct = Conversation.Direct(anna)
        val line = ChatMessage(
            localId = "sys", conversation = direct, fromNodeNum = 0, text = "Anna möchte Carla hinzufügen.",
            timestampMillis = 16, outgoing = false, system = app.anynear.mesh.SystemNote(),
        )
        val real = incoming("a", direct, ts = 5)
        assertEquals("a", build(listOf(real, line)).single { it.conversation == direct }.last?.localId)
        assertEquals("a", build(listOf(line, real)).single { it.conversation == direct }.last?.localId)
        assertEquals("sys", build(listOf(line)).single { it.conversation == direct }.last?.localId)
        val circle = Conversation.Circle(family.id)
        val circleLine = line.copy(localId = "sys2", conversation = circle)
        assertEquals("sys2", build(listOf(incoming("b", circle, ts = 5), circleLine)).single { it.conversation == circle }.last?.localId)
    }

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
    fun oneListByNewestEventThenUnknownByNameThenRemoved() {
        val ben = PeerId.device("person-ben")
        val clara = PeerId.device("person-clara")
        val dora = PeerId.device("person-dora")
        val all = names + mapOf(ben to "Ben", clara to "Clara", dora to "Dora")
        val list = ChatList.build(
            listOf(family, friends, gone, choir),
            listOf(
                incoming("a", Conversation.Circle(family.id), ts = 10),
                incoming("b", Conversation.Direct(anna), ts = 20),
            ),
            { all[it] ?: "Unbekannt" },
            { it.senderName ?: "?" },
            listOf(
                ListedContact(anna, addedAt = 40), // added again after the last message: counts as its newest event
                ListedContact(ben, addedAt = 30),
                ListedContact(clara, addedAt = 15),
                ListedContact(dora), // added before the time was kept
            ),
        )

        // Chor: joined at 25, no messages, sorted in by that time. Freunde and Dora: no known time, by name.
        assertEquals(listOf("Anna", "Ben", "Chor", "Clara", "Familie", "Dora", "Freunde", "Alter Kreis"), list.map { it.title })
        assertNull(list.first { it.title == "Ben" }.last)
        assertEquals(Conversation.Direct(ben), list.first { it.title == "Ben" }.conversation)
    }

    @Test
    fun contactWhoseConversationCarriesADeviceIsListedOnce() {
        val person = PeerId.device("person-anna")
        val list = ChatList.build(
            emptyList(),
            listOf(incoming("a", Conversation.Direct(anna), ts = 50)),
            { "Anna" },
            { "?" },
            listOf(ListedContact(person, devices = listOf("geraet-anna"), addedAt = 5)),
        )

        assertEquals(listOf(Conversation.Direct(anna)), list.map { it.conversation })
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
