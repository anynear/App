package app.anynear.chats

import app.anynear.circles.Circle
import app.anynear.i18n.texts
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation

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

object ChatList {
    /**
     * All circles and all persons with whom there are direct messages. Newest message first;
     * circles without messages follow in their usual order, removed circles at the very bottom.
     */
    fun build(
        circles: List<Circle>,
        messages: List<ChatMessage>,
        nameOf: (peer: String) -> String,
        senderOf: (ChatMessage) -> String,
    ): List<ChatListEntry> {
        val byConversation = messages.groupBy { it.conversation }
        fun entry(conversation: Conversation, title: String, removed: Boolean = false): ChatListEntry {
            val list = byConversation[conversation].orEmpty()
            val last = list.maxByOrNull { it.timestampMillis }
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
        val directEntries = byConversation.keys.filterIsInstance<Conversation.Direct>().map { entry(it, nameOf(it.peer)) }
        val (active, removed) = (circleEntries + directEntries).partition { !it.removed }
        val (withMessages, empty) = active.partition { it.last != null }
        return withMessages.sortedByDescending { it.last!!.timestampMillis } + empty + removed.sortedByDescending { it.last?.timestampMillis ?: 0 }
    }
}
