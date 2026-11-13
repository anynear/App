package app.anynear

import androidx.compose.ui.graphics.Color
import app.anynear.attachments.Attachment
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.AttachmentState
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.SystemNote
import app.anynear.ui.chatFiles
import app.anynear.ui.chatSearchMatches
import app.anynear.ui.searchSnippet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Search in the conversation and „Fotos und Dateien“. Made-up messages, no real data. */
class ChatSearchTest {
    private val family = Conversation.Circle("k1")
    private val other = Conversation.Circle("k2")

    private fun message(id: String, text: String, ts: Long, conversation: Conversation = family, attachments: List<Attachment> = emptyList(), system: SystemNote? = null) =
        ChatMessage(
            localId = id, conversation = conversation, fromNodeNum = 0, text = text, timestampMillis = ts,
            outgoing = false, attachments = attachments, system = system,
        )

    private fun attachment(id: String, kind: AttachmentKind, name: String = "") =
        Attachment(blobId = id, key = "k", kind = kind, name = name, size = 1000, state = AttachmentState.READY)

    @Test
    fun findsTextAndFileNamesNewestFirstIgnoringCaseAndSystemLines() {
        val messages = listOf(
            message("a", "Treffpunkt am Rathaus", ts = 10),
            message("b", "", ts = 30, attachments = listOf(attachment("p", AttachmentKind.PDF, "treffpunkt.pdf"))),
            message("c", "Anna ist dem Kreis beigetreten, Treffpunkt", ts = 40, system = SystemNote()),
            message("d", "Bis später", ts = 20),
        )
        assertEquals(listOf("b", "a"), chatSearchMatches(messages, "  TREFFPUNKT ").map { it.localId })
        assertTrue(chatSearchMatches(messages, "Bahnhof").isEmpty())
    }

    @Test
    fun snippetMarksMatchAndShortensLongTextBefore() {
        val text = "Erst kommt ein langer Satz, der mit dem Gesuchten nichts zu tun hat, und dann der Treffpunkt am Brunnen."
        val snippet = searchSnippet(text, "treffpunkt", Color.Yellow)
        assertTrue(snippet.text.startsWith("…"))
        assertTrue(snippet.text.endsWith("Treffpunkt am Brunnen."))
        val mark = snippet.spanStyles.single()
        assertEquals("Treffpunkt", snippet.text.substring(mark.start, mark.end))
        // Short text stays whole.
        assertEquals("Treffpunkt", searchSnippet("Treffpunkt", "punkt", Color.Yellow).text)
    }

    @Test
    fun filesOfOneConversationNewestFirst() {
        val messages = listOf(
            message("a", "", ts = 10, attachments = listOf(attachment("img1", AttachmentKind.IMAGE), attachment("img2", AttachmentKind.IMAGE))),
            message("b", "", ts = 30, attachments = listOf(attachment("voice", AttachmentKind.AUDIO))),
            message("c", "", ts = 20, conversation = other, attachments = listOf(attachment("fremd", AttachmentKind.PDF, "x.pdf"))),
            message("d", "nur Text", ts = 40),
        )
        assertEquals(listOf("voice", "img1", "img2"), chatFiles(messages, family).map { it.attachment.blobId })
    }
}
