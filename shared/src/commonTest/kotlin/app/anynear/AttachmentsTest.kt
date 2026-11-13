package app.anynear

import app.anynear.app.AppController
import app.anynear.attachments.Attachment
import app.anynear.attachments.AttachmentFiles
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.AttachmentLimits
import app.anynear.attachments.AttachmentState
import app.anynear.attachments.AttachmentWants
import app.anynear.attachments.attachmentHash
import app.anynear.attachments.BinaryFiles
import app.anynear.attachments.RadioAnnouncement
import app.anynear.attachments.mimeTypeOf
import app.anynear.attachments.shortenFileName
import app.anynear.chats.findLinks
import app.anynear.data.LocalCipher
import app.anynear.data.MessageHistory
import app.anynear.data.MessageStore
import app.anynear.i18n.texts
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.IncomingText
import app.anynear.mesh.LinkPreview
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.PeerId
import app.anynear.mesh.Transport
import app.anynear.mesh.awaitsAttachments
import app.anynear.mesh.previewText
import app.anynear.mesh.radioText
import app.anynear.core.b64
import app.anynear.ui.formatSize
import app.anynear.ui.formatStorageSize
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttachmentsTest {
    private val circle = Conversation.Circle("k1")
    private val blobId = b64.encode(ByteArray(16) { 1 })
    private val key = b64.encode(ByteArray(32) { 2 })

    private val hash = b64.encode(ByteArray(16) { 3 })

    private fun attachment(kind: AttachmentKind = AttachmentKind.IMAGE, state: AttachmentState = AttachmentState.DOWNLOADING) =
        Attachment(blobId = blobId, key = key, kind = kind, name = if (kind == AttachmentKind.PDF) "Plan.pdf" else "", size = 1234, width = 1600, height = 1200, state = state, hash = hash)

    // --- Links -------------------------------------------------------------------------

    @Test
    fun findsWebAddressesWithoutTrailingPunctuation() {
        val text = "Schau auf www.anynear.app. Oder https://example.org/a?b=1, (siehe https://de.wikipedia.org/wiki/Funk_(Technik))!"
        assertEquals(
            listOf("https://www.anynear.app", "https://example.org/a?b=1", "https://de.wikipedia.org/wiki/Funk_(Technik)"),
            findLinks(text).map { it.url },
        )
        assertEquals("www.anynear.app", text.substring(findLinks(text).first().range))
    }

    @Test
    fun otherSchemesAreNotLinks() {
        assertTrue(findLinks("javascript:alert(1) file:///etc/passwd intent://x tel:112 mailto:a@b.de").isEmpty())
        assertTrue(findLinks("nur https:// und www.").isEmpty())
        assertEquals(listOf("HTTPS://Example.org"), findLinks("HTTPS://Example.org").map { it.url })
    }

    // --- Radio and display --------------------------------------------------------------

    @Test
    fun radioTextAnnouncesAttachments() {
        assertEquals("Hallo", RadioAnnouncement.radioText("Hallo", emptyList()))
        assertEquals("📎 Bild", RadioAnnouncement.radioText("", listOf(AttachmentKind.IMAGE)))
        assertEquals(
            "📎 2 Bilder, 1 PDF: Unterlagen",
            RadioAnnouncement.radioText("Unterlagen", listOf(AttachmentKind.IMAGE, AttachmentKind.PDF, AttachmentKind.IMAGE)),
        )
        assertEquals("📎 3 PDFs", RadioAnnouncement.radioText("", List(3) { AttachmentKind.PDF }))
        assertTrue(RadioAnnouncement.announces("📎 Bild: Hallo"))
        assertFalse(RadioAnnouncement.announces("Hallo 📎"))
    }

    @Test
    fun previewShowsAttachmentsWithoutText() {
        val message = ChatMessage("m", circle, 5, "", 0, outgoing = false, attachments = listOf(attachment()))
        assertEquals("📎 Bild", message.previewText)
        assertEquals("Hallo", message.copy(text = "Hallo").previewText)
    }

    @Test
    fun shortensLongFileNamesKeepingExtension() {
        assertEquals("Plan.pdf", shortenFileName("/storage/Plan.pdf"))
        val long = shortenFileName("Ein sehr langer Dateiname für den Notfallplan der Familie 2026.pdf")
        assertEquals(40, long.length)
        assertTrue(long.endsWith("….pdf"))
        assertEquals(texts.chat.defaultFileName, shortenFileName(" "))
    }

    /** ADR 0041: every file, MIME type from the extension, radio announces „Datei“. */
    @Test
    fun anyFileType() {
        assertEquals(AttachmentKind.FILE, AttachmentKind.entries.single { it.name == "FILE" })
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", mimeTypeOf(AttachmentKind.FILE, "Vollmacht.DOCX"))
        assertEquals("application/octet-stream", mimeTypeOf(AttachmentKind.FILE, "ohne-endung"))
        assertEquals("image/jpeg", mimeTypeOf(AttachmentKind.IMAGE, ""))
        assertEquals("📎 ${texts.status.announceFile}", RadioAnnouncement.radioText("", listOf(AttachmentKind.FILE)))
        assertEquals(
            "📎 ${texts.status.announceImages(1)}, ${texts.status.announcePdfs(1)}, ${texts.status.announceFiles(2)}: Hallo",
            RadioAnnouncement.radioText("Hallo", listOf(AttachmentKind.IMAGE, AttachmentKind.PDF, AttachmentKind.FILE, AttachmentKind.FILE)),
        )
    }

    @Test
    fun formatsSizes() {
        assertEquals("1 KB", formatSize(10))
        assertEquals("850 KB", formatSize(850 * 1024))
        assertEquals("2,4 MB", formatSize(2_500_000))
        assertEquals("10,0 MB", formatSize(10L * 1024 * 1024))
        // Quotas: below 1 GB in whole MB.
        assertEquals("250 MB", formatStorageSize(250L shl 20))
    }

    // --- Mapping radio ↔ internet -----------------------------------------------------

    private fun online(text: String, ts: Long, pkt: Int = 0, mid: String = "mid-1", attachments: List<Attachment> = listOf(attachment())) = ChatMessage(
        localId = "o-$mid",
        conversation = circle,
        fromNodeNum = 5,
        text = text,
        timestampMillis = ts,
        outgoing = false,
        fromPeer = PeerId.device("geraet-a"),
        senderName = "Anna",
        attachments = attachments,
        radioPacketId = pkt,
        remoteId = mid,
    )

    private fun radio(text: String, ts: Long, packetId: Int = 77) =
        IncomingText(packetId = packetId, fromNodeNum = 5, direct = false, channelIndex = 1, text = text, timestampMillis = ts)

    @Test
    fun radioAnnouncementIsCompletedByInternetCopy() {
        val store = MessageStore()
        assertTrue(store.addIncoming(radio("📎 Bild: Hallo", ts = 60_000), "r", circle))
        assertTrue(store.messages.value.single().awaitsAttachments)

        assertFalse(store.addOnline("id-1", online("Hallo", ts = 50_000)))
        val shown = store.messages.value.single()
        assertEquals("Hallo", shown.text)
        assertEquals(listOf(blobId), shown.attachments.map { it.blobId })
        assertEquals(setOf(Transport.RADIO, Transport.INTERNET), shown.via)
        assertFalse(shown.awaitsAttachments)
    }

    @Test
    fun packetIdMatchesEvenHoursLater() {
        val store = MessageStore()
        assertTrue(store.addIncoming(radio("📎 Bild: Hallo", ts = 0, packetId = 4242), "r", circle))
        // Sender was without internet for hours; via pkt it is still mapped.
        assertFalse(store.addOnline("id-1", online("Hallo", ts = 5 * 60 * 60 * 1000L, pkt = 4242)))
        assertEquals(1, store.messages.value.size)
    }

    @Test
    fun differentPacketIdIsADifferentMessage() {
        val store = MessageStore()
        assertTrue(store.addIncoming(radio("ok", ts = 0, packetId = 1), "r", circle))
        assertTrue(store.addOnline("id-1", online("ok", ts = 0, pkt = 2, attachments = emptyList())))
        assertEquals(2, store.messages.value.size)
    }

    @Test
    fun internetFirstThenRadioWithAnnouncement() {
        val store = MessageStore()
        assertTrue(store.addOnline("id-1", online("Hallo", ts = 0)))
        assertFalse(store.addIncoming(radio("📎 Bild: Hallo", ts = 1_000), "r", circle))
        assertEquals(setOf(Transport.INTERNET, Transport.RADIO), store.messages.value.single().via)
    }

    @Test
    fun resentAfterRestartIsShownOnce() {
        val store = MessageStore()
        assertTrue(store.addOnline("id-1", online("Hallo", ts = 0)))
        // Different mailbox ID, same message ID of the sender.
        assertFalse(store.addOnline("id-2", online("Hallo", ts = 0)))
        assertEquals(1, store.messages.value.size)
    }

    @Test
    fun radioTextOfOutgoingMessageAnnouncesAttachments() {
        val message = ChatMessage("m", circle, 1, "Hallo", 0, outgoing = true, attachments = listOf(attachment(AttachmentKind.PDF)))
        assertEquals("📎 PDF: Hallo", message.radioText)
    }

    // --- Queue across restart -----------------------------------------------------

    @Test
    fun unsentMessagesSurviveRestartWithinAWeek() {
        val now = 10L * 24 * 60 * 60 * 1000
        fun queued(id: String, ts: Long) = ChatMessage(id, circle, 1, "x", ts, outgoing = true, status = MessageStatus.QUEUED)
        val restored = MessageHistory.Snapshot(
            messages = listOf(
                queued("fresh", now - 60_000),
                queued("old", now - 8L * 24 * 60 * 60 * 1000),
                queued("sent", now - 60_000).copy(status = MessageStatus.ONLINE, via = setOf(Transport.INTERNET)),
                // Radio route reported „fehlgeschlagen“, over the internet it has not gone out yet: keep trying.
                queued("radioFailed", now - 60_000).copy(status = MessageStatus.FAILED, via = setOf(Transport.RADIO)),
                queued("gaveUp", now - 60_000).copy(status = MessageStatus.FAILED, sendFailed = true),
            ),
        )
        val store = MessageStore(now = { now }, restored = restored)
        assertEquals(listOf("fresh", "radioFailed"), store.resumable().map { it.localId })
        assertEquals(MessageStatus.QUEUED, store.find("fresh")?.status)
        assertEquals(MessageStatus.FAILED, store.find("old")?.status)
    }

    @Test
    fun attachmentStateAndPacketIdAreStored() {
        val store = MessageStore()
        store.addOutgoing(ChatMessage("m", circle, 1, "", 0, outgoing = true, status = MessageStatus.QUEUED, attachments = listOf(attachment(state = AttachmentState.UPLOADING))))
        store.updateAttachment("m", blobId, AttachmentState.READY)
        store.setRadioPacket("m", 99)
        val m = store.find("m")!!
        assertEquals(AttachmentState.READY, m.attachments.single().state)
        assertEquals(99, m.radioPacketId)
    }

    // --- Envelope ---------------------------------------------------------------------

    @Test
    fun wantGetsAnAttachmentOnlyFromTheSameConversation() {
        val ready = attachment(state = AttachmentState.READY)
        val inCircle = ChatMessage("m1", circle, 0, "", 1, outgoing = false, attachments = listOf(ready))
        val direct = ChatMessage("m2", Conversation.Direct(PeerId.device("anna")), 0, "", 2, outgoing = true, attachments = listOf(ready.copy(blobId = "b2")))
        val messages = listOf(inCircle, direct)
        val all = { _: String -> true }
        assertEquals(blobId, AttachmentWants.shareable(messages, hash, "k1", "ben", all)?.blobId)
        // Other circle or foreign contact: nothing, even if the hash matches.
        assertNull(AttachmentWants.shareable(messages, hash, "k2", "ben", all))
        assertNull(AttachmentWants.shareable(messages, hash, null, "ben", all))
        // Direct message: only to the person from the conversation, also own attachments.
        assertEquals("b2", AttachmentWants.shareable(messages, hash, null, "anna", all)?.blobId)
        // Not on the phone or not there yet: nothing.
        assertNull(AttachmentWants.shareable(messages, hash, "k1", "ben") { false })
        assertNull(AttachmentWants.shareable(listOf(inCircle.copy(attachments = listOf(attachment()))), hash, "k1", "ben", all))
        assertNull(AttachmentWants.shareable(messages, b64.encode(ByteArray(16)), "k1", "ben", all))
    }

    @Test
    fun haveFillsEveryMissingAttachmentWithThatHash() {
        val messages = listOf(
            ChatMessage("m1", circle, 0, "", 1, outgoing = false, attachments = listOf(attachment(), attachment().copy(blobId = "other", hash = null))),
            ChatMessage("m2", circle, 0, "", 2, outgoing = false, attachments = listOf(attachment(state = AttachmentState.MISSING).copy(blobId = "b2"))),
            ChatMessage("m3", circle, 0, "", 3, outgoing = false, attachments = listOf(attachment(state = AttachmentState.READY).copy(blobId = "b3"))),
            ChatMessage("m4", circle, 0, "", 4, outgoing = true, attachments = listOf(attachment(state = AttachmentState.UPLOADING).copy(blobId = "b4"))),
            // Own message from another own device (ADR 0042): is missing here just the same.
            ChatMessage("m5", circle, 0, "", 5, outgoing = true, attachments = listOf(attachment(AttachmentKind.PDF).copy(blobId = "b5"))),
        )
        assertEquals(listOf("m1" to blobId, "m2" to "b2", "m5" to "b5"), AttachmentWants.waiting(messages, hash).map { (id, a) -> id to a.blobId })
    }

    @Test
    fun pendingIncludesOwnMessagesFromOtherDevices() {
        val image = attachment().copy(blobId = "link")
        val messages = listOf(
            ChatMessage("m1", circle, 0, "", 1, outgoing = false, attachments = listOf(attachment(), attachment(state = AttachmentState.READY).copy(blobId = "b1"))),
            // Sent on another own device: not loaded here yet.
            ChatMessage("m2", circle, 0, "", 2, outgoing = true, attachments = listOf(attachment(AttachmentKind.PDF).copy(blobId = "b2"))),
            // Sent here: waits for uploading, never for loading.
            ChatMessage("m3", circle, 0, "", 3, outgoing = true, attachments = listOf(attachment(state = AttachmentState.UPLOADING).copy(blobId = "b3"))),
            ChatMessage("m4", circle, 0, "", 4, outgoing = true, link = LinkPreview("https://example.org", image = image)),
        )
        assertEquals(listOf("m1" to blobId, "m2" to "b2", "m4" to "link"), AttachmentWants.pending(messages).map { (m, a) -> m.localId to a.blobId })
    }

    @Test
    fun longTextIsCutForRadioAtAWordBoundary() {
        assertEquals("kurz", RadioAnnouncement.cut("kurz", 10))
        // „…“ has 3 bytes and counts.
        assertEquals("Hallo…", RadioAnnouncement.cut("Hallo liebe Welt", 10))
        assertTrue(RadioAnnouncement.cut("Hallo liebe Welt", 14).let { it == "Hallo liebe…" && it.encodeToByteArray().size <= 14 })
        // No word boundary in the front half: at the last whole character.
        assertEquals("abcdefg…", RadioAnnouncement.cut("abcdefghijkl", 10))
        // With the announcement of the attachments.
        val announced = RadioAnnouncement.radioText("x".repeat(300), listOf(AttachmentKind.IMAGE))
        val cut = RadioAnnouncement.cut(announced, 100)
        assertTrue(cut.startsWith(RadioAnnouncement.PREFIX) && cut.endsWith(RadioAnnouncement.ELLIPSIS) && cut.encodeToByteArray().size <= 100)
    }

    @Test
    fun radioCutKeepsEmojisWhole() {
        // „😀“ 4 bytes: after „ab“ and „…“ there is no room for a second one within 10 bytes.
        assertEquals("ab😀…", RadioAnnouncement.cut("ab😀😀😀", 10))
        // Skin tone, joiners and flags stay together or drop out entirely.
        assertEquals("ab…", RadioAnnouncement.cut("ab👍🏽cd", 9))
        assertEquals("ab…", RadioAnnouncement.cut("ab👩‍👩‍👧xy", 20))
        assertEquals("ab…", RadioAnnouncement.cut("ab🇩🇪xy", 9))
        assertEquals("ab🇩🇪…", RadioAnnouncement.cut("ab🇩🇪xyzw", 13))
        // Combining characters: „e“ with an accent stays whole.
        assertEquals("ab…", RadioAnnouncement.cut("abe\u0301cd", 6))
        val text = "Grüße 👋🏼 an alle 🇩🇪 und ❤️ ".repeat(20)
        for (max in 10..120) {
            val c = RadioAnnouncement.cut(text, max)
            assertTrue(c.encodeToByteArray().size <= max, "$max: $c")
            assertTrue(text.startsWith(c.removeSuffix(RadioAnnouncement.ELLIPSIS)))
            // No half surrogate pair.
            assertEquals(c, c.encodeToByteArray().decodeToString())
        }
    }

    // --- Storage on the phone ---------------------------------------------------------

    private class MemoryFiles : BinaryFiles {
        val map = mutableMapOf<String, ByteArray>()

        override fun read(name: String) = map[name]

        override fun write(name: String, content: ByteArray) {
            map[name] = content
        }

        override fun delete(name: String) {
            map.remove(name)
        }

        override fun names() = map.keys.toList()
    }

    @Test
    fun attachmentsAreStoredEncrypted() {
        val files = MemoryFiles()
        val store = AttachmentFiles(files, LocalCipher(ByteArray(32) { 7 }))
        val content = "Notfallplan".encodeToByteArray()
        store.write(blobId, content)
        assertContentEquals(content, store.read(blobId))
        assertFalse(files.map.getValue(blobId).decodeToString().contains("Notfallplan"))
        assertNull(store.read("../geheim"))

        store.keepOnly(emptySet())
        assertFalse(store.exists(blobId))
    }
}
