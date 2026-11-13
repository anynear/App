package app.anynear

import app.anynear.attachments.Attachment
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.AttachmentState
import app.anynear.data.MessageStore
import app.anynear.i18n.texts
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.ReplyRef
import app.anynear.mesh.SystemNote
import app.anynear.ui.answeredMessage
import app.anynear.ui.replySnippet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Answers with a quote (ADR 0092). Made-up messages, no real data. */
class RepliesTest {
    private val family = Conversation.Circle("k1")

    private fun message(localId: String, text: String, outgoing: Boolean = false, remoteId: String? = null, system: SystemNote? = null, deleted: Boolean = false, attachments: List<Attachment> = emptyList(), replyTo: ReplyRef? = null) =
        ChatMessage(
            localId = localId, conversation = family, fromNodeNum = 0, text = text, timestampMillis = 1, outgoing = outgoing,
            remoteId = remoteId, system = system, deleted = deleted, attachments = attachments, replyTo = replyTo,
        )

    @Test
    fun findsOwnByLocalIdAndOthersByRemoteId() {
        val own = message("AAAAAAAAAAA", "Wer kommt mit?", outgoing = true)
        val theirs = message("x1", "Ich!", remoteId = "BBBBBBBBBBB")
        val note = message("BBBBBBBBBBB", "Anna ist beigetreten", system = SystemNote())
        val messages = listOf(own, theirs, note)
        assertEquals(own, answeredMessage(messages, ReplyRef("AAAAAAAAAAA", "t")))
        assertEquals(theirs, answeredMessage(messages, ReplyRef("BBBBBBBBBBB", "t")))
        // A local ID of someone else's message is no ANMP ID.
        assertNull(answeredMessage(messages, ReplyRef("x1", "t")))
        assertNull(answeredMessage(messages, ReplyRef("CCCCCCCCCCC", "t")))
    }

    @Test
    fun snippetIsTheFirstLineOrWhatTheMessageCarries() {
        assertEquals("Erste Zeile", replySnippet(message("a", "  Erste Zeile\nZweite")))
        val voice = Attachment(blobId = "v", key = "k", kind = AttachmentKind.AUDIO, name = "", size = 10, state = AttachmentState.READY)
        val photo = voice.copy(blobId = "p", kind = AttachmentKind.IMAGE)
        assertEquals(texts.chat.voiceMessage, replySnippet(message("b", "", attachments = listOf(voice))))
        assertEquals(texts.chat.photo, replySnippet(message("c", "", attachments = listOf(photo))))
        assertEquals(texts.chat.deletedMessage, replySnippet(message("d", "", deleted = true)))
    }

    @Test
    fun answerFromTheInternetCompletesTheRadioCopy() {
        val store = MessageStore()
        val radio = message("r1", "Ja", remoteId = "DDDDDDDDDDD").copy(fromPeer = "d:anna")
        store.addRadio(radio, fromNodeNum = 7, packetId = 42)
        store.addOnline("o1", radio.copy(localId = "i1", replyTo = ReplyRef("AAAAAAAAAAA", "t")))
        assertEquals(ReplyRef("AAAAAAAAAAA", "t"), store.messages.value.single().replyTo)
    }
}
