package app.anynear.chats

import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.Reaction
import app.anynear.mesh.previewText

/**
 * Reactions of others to own messages (ADR 0018) that are shown in the notification of the conversation
 * until it is read. Own, withdrawn and old reactions (for instance from the history on a new
 * device) do not notify.
 */
class ReactionNotices(private val now: () -> Long) {
    data class Notice(val reactor: String, val emoji: String, val targetId: String, val targetText: String, val timestampMillis: Long)

    private val pending = mutableMapOf<Conversation, List<Notice>>()

    /** Remember reaction [emoji] of [reactor] to [target]. @return `true` if it should be notified. */
    fun add(target: ChatMessage, reactor: String, emoji: String, ts: Long): Boolean {
        val others = pending[target.conversation].orEmpty().filterNot { it.reactor == reactor && it.targetId == target.localId }
        pending[target.conversation] = others
        if (!target.outgoing || reactor == Reaction.SELF || emoji.isEmpty() || now() - ts > MAX_AGE_MILLIS) return false
        pending[target.conversation] = (others + Notice(reactor, emoji, target.localId, short(target.previewText), ts)).takeLast(MAX_PER_CONVERSATION)
        return true
    }

    fun of(conversation: Conversation): List<Notice> = pending[conversation].orEmpty()

    fun clear(conversation: Conversation) {
        pending.remove(conversation)
    }

    private fun short(text: String): String =
        text.lineSequence().first().let { if (it.length > MAX_TEXT) it.take(MAX_TEXT).trimEnd() + "…" else it }

    private companion object {
        const val MAX_TEXT = 40
        const val MAX_AGE_MILLIS = 60 * 60 * 1000L
        const val MAX_PER_CONVERSATION = 6
    }
}
