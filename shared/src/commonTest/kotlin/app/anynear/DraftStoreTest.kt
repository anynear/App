package app.anynear

import app.anynear.attachments.AttachmentFiles
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.BinaryFiles
import app.anynear.attachments.DraftAttachment
import app.anynear.data.DraftStore
import app.anynear.data.EncryptedKeyValueStore
import app.anynear.data.KeyValueStore
import app.anynear.data.LocalCipher
import app.anynear.mesh.Conversation
import app.anynear.mesh.PeerId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Made-up conversations and contents, no real data. */
class DraftStoreTest {
    private val cipher = LocalCipher(ByteArray(32) { it.toByte() })
    private val raw = RawStore()
    private val files = MemoryFiles()
    private val circle = Conversation.Circle("k1")
    private val direct = Conversation.Direct(PeerId.device("geraet-anna"))

    /** As after a restart: new store over the same data. */
    private fun store() = DraftStore(EncryptedKeyValueStore(raw, cipher), AttachmentFiles(files, cipher))

    @Test
    fun textSurvivesRestartPerConversationAndEncrypted() {
        store().setText(circle, "Bin gleich da")
        store().setText(direct, "Hallo Anna")
        assertEquals("Bin gleich da", store().text(circle))
        assertEquals("Hallo Anna", store().text(direct))
        assertFalse(raw.values().any { "Bin gleich da" in it })
    }

    @Test
    fun draftMovesWhenTheDeviceTurnsOutToBeAPerson() {
        // Opened from a contact link under the device; then the device became the person (ADR 0042).
        val person = Conversation.Direct(PeerId.device("person-anna"))
        store().setText(direct, "Hallo Anna")
        store().move(direct, person)
        assertEquals("Hallo Anna", store().text(person))
        assertEquals("", store().text(direct))
        // A draft already under the person stays.
        store().setText(direct, "alt")
        store().move(direct, person)
        assertEquals("Hallo Anna", store().text(person))
    }

    @Test
    fun passedOnMarkSurvivesRestartAndMovesWithTheDraft() {
        // ADR 0118: a draft from „Weiterleiten“ goes out marked; the mark survives a restart.
        store().setText(direct, "Schau mal")
        store().setPassedOn(direct, true)
        assertTrue(store().passedOn(direct))
        assertFalse(store().passedOn(circle))
        val person = Conversation.Direct(PeerId.device("person-anna"))
        store().move(direct, person)
        assertTrue(store().passedOn(person))
        assertFalse(store().passedOn(direct))
        // Not onto a draft of its own.
        store().setText(direct, "eigener Text")
        store().setPassedOn(direct, false)
        store().setPassedOn(person, true)
        store().setText(circle, "im Kreis")
        store().move(person, circle)
        assertFalse(store().passedOn(circle))
    }

    @Test
    fun emptyTextRemovesEntry() {
        store().setText(circle, "x")
        store().setText(circle, "")
        assertEquals("", store().text(circle))
        assertTrue(raw.values().isEmpty())
    }

    @Test
    fun attachmentsSurviveRestartAndAreCleanedUp() {
        val photo = DraftAttachment(AttachmentKind.entries.first(), byteArrayOf(1, 2, 3), "foto.jpg", 4, 3)
        val drafts = store()
        drafts.setAttachments(circle, listOf(photo))
        assertEquals(1, files.names().size)
        assertFalse(files.read(files.names().single())!!.contentEquals(photo.bytes))

        val after = store()
        val restored = after.attachments(circle).single()
        assertContentEquals(photo.bytes, restored.bytes)
        assertEquals("foto.jpg", restored.name)
        // Saved again unchanged: no second file.
        after.setAttachments(circle, listOf(restored))
        assertEquals(1, files.names().size)

        after.setAttachments(circle, emptyList())
        assertTrue(files.names().isEmpty())
        assertTrue(store().attachments(circle).isEmpty())
    }

    /** What really ends up on the device. */
    private class RawStore : KeyValueStore {
        private val map = mutableMapOf<String, String>()
        fun values() = map.values.toList()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    private class MemoryFiles : BinaryFiles {
        private val map = mutableMapOf<String, ByteArray>()
        override fun read(name: String) = map[name]
        override fun write(name: String, content: ByteArray) { map[name] = content }
        override fun delete(name: String) { map.remove(name) }
        override fun names() = map.keys.toList()
    }
}
