package app.anynear.chats

import app.anynear.alerts.AlertsSeen
import app.anynear.mesh.Conversation
import app.anynear.mesh.PeerId

/**
 * Up to when each conversation has been read (send time of its newest message, Unix ms), for the person on all own
 * devices: goes via the person's settings (ADR 0050, key [SETTING_KEY]) like [AlertsSeen]. Per conversation the later
 * point in time wins. A phone back from the emergency card or newly added takes it over, so the messages that come back
 * are unread only where they were unread before (ADR 0107 §8).
 */
object ReadUntil {
    const val SETTING_KEY = "read_until"

    fun merge(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> = AlertsSeen.merge(a, b)

    fun encode(marks: Map<String, Long>): String = AlertsSeen.encode(marks)

    /** Anything unreadable counts as empty so that a broken value from another device blocks nothing. */
    fun decode(text: String?): Map<String, Long> = AlertsSeen.decode(text)

    /**
     * Key of [conversation], the same in the app and in the web app: `c:<circle ID>` or `d:<person ID>`; `null` for a
     * direct conversation without an anynear identity (radio only).
     */
    fun keyOf(conversation: Conversation): String? = when (conversation) {
        is Conversation.Circle -> "c:" + conversation.circleId
        is Conversation.Direct -> PeerId.deviceIdOf(conversation.peer)?.let { "d:$it" }
    }

    /** Inverse of [keyOf]; `null` for an unknown key. */
    fun conversationOf(key: String): Conversation? = when {
        key.startsWith("c:") -> Conversation.Circle(key.drop(2))
        key.startsWith("d:") -> Conversation.Direct(PeerId.device(key.drop(2)))
        else -> null
    }

    /**
     * Whether a received message with send time [sentAt] that comes back while its conversation still loads counts as
     * read: up to the conversation's [mark]; without one (never read since this was introduced), everything sent before
     * [loadingSince], the moment this device began to load. Send times are whole seconds: one from the second loading
     * began counts as sent after it.
     */
    fun readOnArrival(sentAt: Long, mark: Long?, loadingSince: Long): Boolean =
        if (mark != null) sentAt <= mark else sentAt < loadingSince - loadingSince % 1000
}
