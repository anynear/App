package app.anynear.chats

import app.anynear.circles.Circle
import app.anynear.i18n.texts
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.PeerId

/** A row of the overview „Nachrichten“: a circle or a person, as in common messengers. */
data class ChatListEntry(
    val conversation: Conversation,
    /** Circle name or name of the person. */
    val title: String,
    /** Last message, `null` if nothing has been written here yet. */
    val last: ChatMessage?,
    /** „Du“, the name of the sender in the circle, `null` for an incoming direct message. */
    val lastSender: String?,
    val unread: Int,
    /** Circle we were removed from: stays readable, but is at the bottom. */
    val removed: Boolean = false,
) {
    /** Stable key, e.g. for lists and notifications. */
    val key: String get() = conversation.key()
}

/** `c:<Kreis-ID>` or `p:<PeerId>`. Inverse: [conversationOf]. */
fun Conversation.key(): String = when (this) {
    is Conversation.Circle -> "c:$circleId"
    is Conversation.Direct -> "p:$peer"
}

fun conversationOf(key: String): Conversation? = when {
    key.startsWith("c:") -> Conversation.Circle(key.removePrefix("c:"))
    key.startsWith("p:") -> Conversation.Direct(key.removePrefix("p:"))
    else -> null
}

/** A contact for the overview: its conversation [peer], its [devices] (older conversations may carry one of them). */
data class ListedContact(
    val peer: String,
    val devices: List<String> = emptyList(),
    /** When it became a contact (ms); 0 = unknown, then it goes to the end. */
    val addedAt: Long = 0,
)

object ChatList {
    /**
     * All circles, all persons with whom there are direct messages and all [contacts], also without messages, in one
     * list by their newest event (ADR 0008): the last message, for a contact at least the time it was added, for a
     * circle at least the time it was joined or created. Then everything without a known time (contacts and circles
     * from before that time was kept) by name; removed circles at the very bottom.
     */
    fun build(
        circles: List<Circle>,
        messages: List<ChatMessage>,
        nameOf: (peer: String) -> String,
        senderOf: (ChatMessage) -> String,
        contacts: List<ListedContact> = emptyList(),
    ): List<ChatListEntry> {
        val byConversation = messages.groupBy { it.conversation }
        fun entry(conversation: Conversation, title: String, removed: Boolean = false): ChatListEntry {
            val list = byConversation[conversation].orEmpty()
            // A line in a direct conversation is only a note of this device, such as „Isa möchte Kevin hinzufügen …“ after
            // an introduction (ADR 0116): only the device that passed the request on has it. The overview keeps showing
            // the last real message, the same on every device and in the web app; the line stands for the conversation
            // only while there is nothing else in it.
            val shown = if (conversation is Conversation.Direct) list.filter { it.system == null }.ifEmpty { list } else list
            val last = shown.maxByOrNull { it.timestampMillis }
            val sender = when {
                last == null -> null
                last.system != null -> null
                last.outgoing -> texts.status.youSender
                conversation is Conversation.Circle -> senderOf(last)
                else -> null
            }
            return ChatListEntry(conversation, title, last, sender, list.count { it.unread }, removed)
        }
        val circleEntries = circles.map { entry(Conversation.Circle(it.id), it.name, it.removed) }
        val directs = byConversation.keys.filterIsInstance<Conversation.Direct>()
        // A conversation may still carry a device of the contact instead of its person (ADR 0042).
        fun contactOf(peer: String) = contacts.firstOrNull { it.peer == peer } ?: PeerId.deviceIdOf(peer)?.let { d -> contacts.firstOrNull { d in it.devices } }
        val directEntries = directs.map { entry(it, nameOf(it.peer)) }
        val withConversation = directs.mapNotNull { contactOf(it.peer) }.toSet()
        val contactEntries = contacts.filter { it !in withConversation }.map { entry(Conversation.Direct(it.peer), nameOf(it.peer)) }
        val since = (directEntries + contactEntries).associate { e ->
            e.conversation to (contactOf((e.conversation as Conversation.Direct).peer)?.addedAt ?: 0)
        } + circles.associate { Conversation.Circle(it.id) to it.joinedAt }
        fun latest(e: ChatListEntry): Long = maxOf(e.last?.timestampMillis ?: 0, since[e.conversation] ?: 0)
        val (active, removed) = (circleEntries + directEntries + contactEntries).partition { !it.removed }
        val (dated, undated) = active.partition { it.last != null || latest(it) > 0 }
        return dated.sortedByDescending(::latest) + undated.sortedBy { it.title.lowercase() } +
            removed.sortedByDescending { it.last?.timestampMillis ?: 0 }
    }
}
