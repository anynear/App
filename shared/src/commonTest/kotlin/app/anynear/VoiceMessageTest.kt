package app.anynear

import app.anynear.attachments.Attachment
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.AttachmentState
import app.anynear.attachments.RadioAnnouncement
import app.anynear.attachments.formatDuration
import app.anynear.data.MessageStore
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.IncomingText
import app.anynear.mesh.PeerId
import app.anynear.mesh.awaitsAttachments
import app.anynear.mesh.previewText
import app.anynear.mesh.radioText
import app.anynear.core.b64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Voice messages (ADR 0030): radio text, merging, format. Made-up test data. */
class VoiceMessageTest {
    private val circle = Conversation.Circle("k1")
    private val blobId = b64.encode(ByteArray(16) { 1 })
    private val key = b64.encode(ByteArray(32) { 2 })

    private fun voice(state: AttachmentState = AttachmentState.DOWNLOADING, millis: Long = 42_000) =
        Attachment(blobId = blobId, key = key, kind = AttachmentKind.AUDIO, size = 126_000, state = state, durationMillis = millis)

    @Test
    fun radioTextAnnouncesVoice() {
        assertEquals("🎤 Sprachnachricht 0:42", RadioAnnouncement.radioText("", listOf(AttachmentKind.AUDIO), 42_000))
        assertEquals("🎤 Sprachnachricht", RadioAnnouncement.radioText("", listOf(AttachmentKind.AUDIO)))
        // With text no „Sprachnachricht“, that saves space over radio.
        assertEquals("🎤 Bin gleich da", RadioAnnouncement.radioText("Bin gleich da", listOf(AttachmentKind.AUDIO), 42_000))
        assertTrue(RadioAnnouncement.announces("🎤 Bin gleich da"))
    }

    @Test
    fun formatsDurations() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:01", formatDuration(600))
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("5:00", formatDuration(5 * 60_000))
    }

    @Test
    fun previewAndRadioTextOfVoiceMessage() {
        val message = ChatMessage("m", circle, 5, "", 0, outgoing = true, attachments = listOf(voice(AttachmentState.UPLOADING)))
        assertEquals("🎤 Sprachnachricht 0:42", message.previewText)
        assertEquals("🎤 Sprachnachricht 0:42", message.radioText)
        assertEquals("🎤 Hallo", message.copy(text = "Hallo").radioText)
    }

    @Test
    fun radioAnnouncementIsCompletedByVoiceFromInternet() {
        val store = MessageStore()
        val radio = IncomingText(packetId = 77, fromNodeNum = 5, direct = false, channelIndex = 1, text = "🎤 Sprachnachricht 0:42", timestampMillis = 60_000)
        assertTrue(store.addIncoming(radio, "r", circle))
        assertTrue(store.messages.value.single().awaitsAttachments)

        val online = ChatMessage(
            localId = "o-1", conversation = circle, fromNodeNum = 5, text = "", timestampMillis = 50_000, outgoing = false,
            fromPeer = PeerId.device("geraet-a"), senderName = "Anna", attachments = listOf(voice()), radioPacketId = 77, remoteId = "mid-1",
        )
        assertFalse(store.addOnline("id-1", online))
        val shown = store.messages.value.single()
        assertEquals("", shown.text)
        assertEquals(42_000, shown.attachments.single().durationMillis)
        assertFalse(shown.awaitsAttachments)
    }

}
