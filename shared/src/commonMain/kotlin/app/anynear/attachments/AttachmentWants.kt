package app.anynear.attachments

import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.PeerId
import app.anynear.core.b64
import app.anynear.core.sha256

/** Hash of an attachment: first 16 bytes of SHA-256 over the content, Base64url (as with images, ADR 0014). */
suspend fun attachmentHash(bytes: ByteArray): String = b64.encode(sha256(bytes).copyOf(16))

/**
 * Attachments via their hash: "whoever has it delivers it" (ADR 0014). Decides which attachment may be handed
 * out on a `want` and which attachments a `have` fills.
 */
object AttachmentWants {
    /**
     * Attachment with hash [hash] that this phone has ([onDevice]) and may give to the asker: it must
     * be in a message of the same conversation, in the circle [circleId] or, for a `want` in the personal
     * mailbox ([circleId] = `null`), in the direct message with [fromDevice]. Whoever knows a hash
     * thus never gets an attachment from a conversation they are not allowed to read.
     */
    fun shareable(messages: List<ChatMessage>, hash: String, circleId: String?, fromDevice: String, onDevice: (blobId: String) -> Boolean): Attachment? {
        val conversation = if (circleId != null) Conversation.Circle(circleId) else Conversation.Direct(PeerId.device(fromDevice))
        return messages.asSequence()
            .filter { it.conversation == conversation }
            .flatMap { it.attachments }
            .firstOrNull { it.hash == hash && it.state == AttachmentState.READY && onDevice(it.blobId) }
    }

    /**
     * Attachments with hash [hash] that are still missing: per message ([ChatMessage.localId]) the attachment. Also own
     * messages that came from another own device (ADR 0042); what this phone sends itself is never missing.
     */
    fun waiting(messages: List<ChatMessage>, hash: String): List<Pair<String, Attachment>> =
        messages.flatMap { m ->
            m.attachments.filter { it.hash == hash && (it.state == AttachmentState.DOWNLOADING || it.state == AttachmentState.MISSING) }.map { m.localId to it }
        }

    /**
     * Attachments that still have to be loaded, with their message; also the preview images of links. Own
     * messages belong to them if they came from another own device (ADR 0042): there the attachment is
     * at [AttachmentState.DOWNLOADING], ones sent here never.
     */
    fun pending(messages: List<ChatMessage>): List<Pair<ChatMessage, Attachment>> =
        messages.flatMap { m -> (m.attachments + listOfNotNull(m.link?.image)).filter { it.state == AttachmentState.DOWNLOADING }.map { m to it } }
}
