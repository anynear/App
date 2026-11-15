package app.anynear

import app.anynear.attachments.AttachmentFiles
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.AttachmentLimits
import app.anynear.attachments.BinaryFiles
import app.anynear.attachments.DraftAttachment
import app.anynear.core.LocalKey
import app.anynear.data.NetState
import app.anynear.data.NetStatus
import app.anynear.data.PersonalFiles
import app.anynear.data.SharedStorage
import app.anynear.data.StoreSpace
import app.anynear.data.PersonalFile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ADR 0040: „Meine Ablage“ on the phone, encrypted, directory in the key-value store. */
class PersonalFilesTest {
    private val raw = Files()
    private val store = InMemoryStore()
    private fun files() = PersonalFiles(store, AttachmentFiles(raw, LocalKey.cipher(secrets)), now = { 42L })
    private val secrets = InMemorySecretStore()

    @Test
    fun binKeepsTheFileUntilItsTime() {
        var t = 1_000L
        val personal = PersonalFiles(store, AttachmentFiles(raw, LocalKey.cipher(secrets)), now = { t })
        val pdf = personal.add(DraftAttachment(AttachmentKind.PDF, byteArrayOf(1, 2, 3), name = "Plan.pdf"), "Plan.pdf")!!
        // Never online yet: gone at once, there is nothing to restore.
        personal.trash(pdf.id)
        assertEquals(emptyList(), personal.all.value)

        val photo = personal.add(DraftAttachment(AttachmentKind.PDF, byteArrayOf(4, 5), name = "Vertrag.pdf"), "Vertrag.pdf")!!
        personal.markOnline(photo.id, true, since = 900)
        personal.trash(photo.id)
        assertEquals(emptyList(), personal.list.value)
        val inBin = personal.bin.value.single()
        assertTrue(inBin.binPending)
        assertEquals(t + PersonalFiles.BIN_DAYS * 86_400_000L, inBin.purgeAt)
        // Stays on the phone so that restoring also works without internet.
        assertContentEquals(byteArrayOf(4, 5), personal.bytes(photo.id))
        personal.binSent(photo.id, inBin = true, purgeAt = 5_000)
        assertEquals(5_000, personal.bin.value.single().purgeAt)

        personal.restore(photo.id)
        assertEquals(listOf(photo.id), personal.list.value.map { it.id })
        assertEquals(emptyList(), personal.bin.value)

        // Put into the bin from another device; after expiry gone even without internet.
        personal.binSent(photo.id, inBin = false, purgeAt = 0)
        personal.binFromOnline(photo.id, trashedAt = 2_000, purgeAt = 3_000)
        assertEquals(photo.id, personal.bin.value.single().id)
        t = 3_001
        personal.purgeExpired()
        assertEquals(emptyList(), personal.all.value)
        assertEquals(emptyList(), raw.names())
    }

    @Test
    fun addRenameSendDelete() {
        val personal = files()
        val pdf = personal.add(DraftAttachment(AttachmentKind.PDF, byteArrayOf(1, 2, 3), name = "Vollmacht.pdf"), "Vollmacht.pdf")!!
        assertEquals(listOf(pdf), personal.list.value)
        // Encrypted on the device, readable through the storage.
        assertTrue(!raw.read(pdf.id)!!.contentEquals(byteArrayOf(1, 2, 3)))
        assertContentEquals(byteArrayOf(1, 2, 3), personal.bytes(pdf.id))

        personal.rename(pdf.id, "  Vollmacht Oma.pdf ")
        assertEquals("Vollmacht Oma.pdf", personal.list.value.single().name)
        // Reloaded (e.g. after a restart) everything is still there.
        assertEquals(personal.list.value, files().list.value)

        val draft = personal.asDraft(personal.list.value.single())!!
        assertEquals(AttachmentKind.PDF, draft.kind)
        assertEquals("Vollmacht Oma.pdf", draft.name)

        personal.delete(pdf.id)
        assertEquals(emptyList(), personal.list.value)
        assertEquals(emptyList(), raw.names())
    }

    /** Rotated photo: new content of the same file, goes up anew; name and details stay. */
    @Test
    fun replacedContentGoesUpAgainAndComesFromOnline() {
        val personal = files()
        val changed = mutableListOf<PersonalFile>()
        personal.listener = object : PersonalFiles.Listener {
            override fun changed(file: PersonalFile) { changed += file }
            override fun deleted(id: String) {}
        }
        val photo = personal.add(DraftAttachment(AttachmentKind.IMAGE, byteArrayOf(1, 2, 3), width = 400, height = 300), "Brief.jpg")!!
        personal.markOnline(photo.id, true)
        changed.clear()

        assertTrue(personal.replaceContent(photo.id, DraftAttachment(AttachmentKind.IMAGE, byteArrayOf(4, 5, 6, 7), width = 300, height = 400)))
        val rotated = personal.list.value.single()
        assertEquals(listOf(rotated), changed)
        assertEquals("Brief.jpg", rotated.name)
        assertEquals(4L, rotated.size)
        assertEquals(300 to 400, rotated.width to rotated.height)
        assertFalse(rotated.online)
        assertContentEquals(byteArrayOf(4, 5, 6, 7), personal.bytes(photo.id))

        // Rotated again on another device: the new version comes down without going up again.
        changed.clear()
        personal.contentFromOnline(photo.id, 2, 400, 300, byteArrayOf(8, 9), thumbnail = null)
        assertEquals(400 to 300, personal.list.value.single().let { it.width to it.height })
        assertContentEquals(byteArrayOf(8, 9), personal.bytes(photo.id))
        assertEquals(emptyList(), changed)

        // Online only (ADR 0080): cannot be rotated until it is on the phone again.
        personal.markOnline(photo.id, true)
        assertTrue(personal.setOnlineOnly(photo.id, byteArrayOf(0)))
        assertFalse(personal.replaceContent(photo.id, DraftAttachment(AttachmentKind.IMAGE, byteArrayOf(1), width = 1, height = 1)))
        personal.contentFromOnline(photo.id, 3, 300, 400, byteArrayOf(1, 2, 3), thumbnail = byteArrayOf(7))
        assertContentEquals(byteArrayOf(7), personal.thumbnail(photo.id))
        assertNull(personal.bytes(photo.id))
    }

    /** ADR 0049: the storage of a circle is the same class with its own directory. */
    @Test
    fun circleFilesAreSeparateAndGoWhenDeletedElsewhereOrLeft() {
        val cipher = LocalKey.cipher(secrets)
        val personal = files()
        val circle = PersonalFiles(store, AttachmentFiles(raw, cipher), key = "circle.files.k1", now = { 42L })
        val mine = personal.add(DraftAttachment(AttachmentKind.PDF, byteArrayOf(1), name = "Vollmacht.pdf"), "Vollmacht.pdf")!!
        val plan = circle.add(DraftAttachment(AttachmentKind.PDF, byteArrayOf(2), name = "Notfallplan.pdf"), "Notfallplan.pdf")!!
        val map = circle.add(DraftAttachment(AttachmentKind.PDF, byteArrayOf(3), name = "Treffpunkt.pdf"), "Treffpunkt.pdf")!!
        assertEquals(listOf(mine), personal.list.value)
        assertEquals(listOf(plan.id, map.id).toSet(), circle.list.value.map { it.id }.toSet())

        // Someone else in the circle deleted it: gone from the phone, without wanting to delete it online oneself.
        circle.deletedOnline(plan.id)
        assertEquals(listOf(map), circle.list.value)
        assertNull(circle.bytes(plan.id))
        assertEquals(emptySet(), circle.pendingDeletes)

        // Circle left: its storage disappears entirely, „Meine Ablage“ stays.
        circle.clear()
        assertEquals(emptyList(), PersonalFiles(store, AttachmentFiles(raw, cipher), key = "circle.files.k1", now = { 42L }).list.value)
        assertNull(circle.bytes(map.id))
        assertEquals(listOf(mine), files().list.value)
        assertContentEquals(byteArrayOf(1), personal.bytes(mine.id))
    }

    @Test
    fun remembersWhereItIsOnline() {
        val personal = files()
        val pdf = personal.add(DraftAttachment(AttachmentKind.PDF, byteArrayOf(1, 2, 3), name = "Vollmacht.pdf"), "Vollmacht.pdf")!!
        assertEquals(0L, personal.checkedAt)
        personal.markOnline(pdf.id, true, since = 7, relays = listOf("fr-par-01", "de-fra-02"))
        personal.checked()
        // After a restart the storage still knows since when and where the file has been online.
        val again = files().list.value.single()
        assertTrue(again.online)
        assertEquals(7L, again.onlineSince)
        assertEquals(listOf("fr-par-01", "de-fra-02"), again.relays)
        assertEquals(42L, files().checkedAt)
        // Without new details the state stays as it was.
        personal.markOnline(pdf.id, true)
        assertEquals(again, personal.list.value.single())
    }

    @Test
    fun securedOnlyFromTwoOperators() {
        val personal = files()
        val pdf = personal.add(DraftAttachment(AttachmentKind.PDF, byteArrayOf(1, 2, 3), name = "Pass.pdf"), "Pass.pdf")!!
        assertEquals(NetState.SECURING, personal.list.value.single().netState)
        // Arrived at the first relay: not yet secured (ADR 0028 §7).
        personal.markOnline(pdf.id, true, relays = listOf("fr-par-01"), operators = 1)
        assertEquals(NetState.SECURING, personal.list.value.single().netState)
        personal.markOnline(pdf.id, true, relays = listOf("fr-par-01", "de-nue-01"), operators = 2)
        assertEquals(NetState.SECURED, personal.list.value.single().netState)
        // Afterwards fewer than two: incomplete, even after a restart.
        personal.markOnline(pdf.id, true, relays = listOf("fr-par-01"), operators = 1)
        assertEquals(NetState.INCOMPLETE, files().list.value.single().netState)
        personal.markOnline(pdf.id, true, relays = listOf("fr-par-01", "de-nue-01", "pl-waw-01"), operators = 3)
        assertEquals(NetState.SECURED, personal.list.value.single().netState)
    }

    @Test
    fun oneStorageForAllStores() {
        val s = SharedStorage()
        assertEquals(null, s.space.value)
        assertFalse(s.full)
        s.set(StoreSpace(used = 512L shl 20, quota = 1L shl 30))
        assertEquals(0.5f, s.space.value!!.fraction)
        assertFalse(s.space.value!!.nearlyFull)
        // Unknown stays at the last state.
        s.set(null)
        assertEquals(512L shl 20, s.space.value!!.used)
        s.set(StoreSpace(used = 950L shl 20, quota = 1L shl 30))
        assertTrue(s.space.value!!.nearlyFull)
        assertFalse(s.full)
        s.set(StoreSpace(used = (1L shl 30) - 1000, quota = 1L shl 30))
        assertTrue(s.full)
        assertFalse(s.space.value!!.nearlyFull)
    }

    @Test
    fun refusesTooLargeAndOtherKinds() {
        val personal = files()
        assertNull(personal.add(DraftAttachment(AttachmentKind.PDF, ByteArray(AttachmentLimits.MAX_PERSONAL_BYTES.toInt() + 1)), "groß.pdf"))
        // The storage takes more than a chat attachment.
        assertNotNull(personal.add(DraftAttachment(AttachmentKind.FILE, ByteArray(AttachmentLimits.MAX_BYTES.toInt() + 1)), "film.mp4"))
        personal.delete(personal.list.value.single().id)
        assertNull(personal.add(DraftAttachment(AttachmentKind.AUDIO, ByteArray(10)), "ton.m4a"))
        // Any other file works (ADR 0041) and keeps its name when passed on.
        val doc = personal.add(DraftAttachment(AttachmentKind.FILE, ByteArray(10), name = "Liste.xlsx"), "Liste.xlsx")!!
        assertEquals("Liste.xlsx", personal.asDraft(doc)!!.name)
        assertEquals(1, personal.list.value.size)
    }

    /** ADR 0080: online only means thumbnail instead of file; only once it is online; the sync loads it back. */
    @Test
    fun onlineOnlyKeepsOnlyAThumbnailUntilKeptAgain() = runTest {
        val personal = files()
        val pdf = personal.add(DraftAttachment(AttachmentKind.PDF, byteArrayOf(1, 2, 3), name = "Vollmacht.pdf"), "Vollmacht.pdf")!!
        val thumb = byteArrayOf(9, 9)
        // Not online yet: stays on the phone so that the only copy never disappears.
        assertFalse(personal.setOnlineOnly(pdf.id, thumb))
        assertTrue(personal.onDevice(pdf.id))
        assertNull(personal.thumbnail(pdf.id))

        val wanted = mutableListOf<String>()
        personal.listener = object : PersonalFiles.Listener {
            override fun changed(file: PersonalFile) {}
            override fun deleted(id: String) {}
            override fun wantOnDevice(file: PersonalFile) {
                wanted += file.id
            }
            override suspend fun fetch(file: PersonalFile) = byteArrayOf(1, 2, 3).takeIf { file.id == pdf.id }
        }
        personal.markOnline(pdf.id, true)
        assertTrue(personal.setOnlineOnly(pdf.id, thumb))
        val stored = personal.list.value.single()
        assertTrue(stored.onlineOnly)
        assertFalse(personal.onDevice(pdf.id))
        assertNull(personal.bytes(pdf.id))
        assertNull(personal.asDraft(stored))
        assertContentEquals(thumb, personal.thumbnail(pdf.id))
        // Opening and sending fetch the file from the network without storing it again.
        assertContentEquals(byteArrayOf(1, 2, 3), personal.load(stored))
        assertEquals("Vollmacht.pdf", personal.loadDraft(stored)!!.name)
        assertFalse(personal.onDevice(pdf.id))
        // Stays that way after a restart.
        assertTrue(files().list.value.single().onlineOnly)

        // Keep again: the sync downloads, the thumbnail goes.
        personal.keepOnDevice(pdf.id)
        assertEquals(listOf(pdf.id), wanted)
        assertFalse(personal.list.value.single().onlineOnly)
        personal.downloaded(pdf.id, byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), personal.bytes(pdf.id))
        assertNull(personal.thumbnail(pdf.id))
    }

    @Test
    fun deletingAnOnlineOnlyFileTakesItsThumbnail() {
        val personal = files()
        val pdf = personal.add(DraftAttachment(AttachmentKind.PDF, byteArrayOf(1), name = "a.pdf"), "a.pdf")!!
        personal.markOnline(pdf.id, true)
        assertTrue(personal.setOnlineOnly(pdf.id, byteArrayOf(9)))
        // A late download after „nur online“ was set again stores nothing.
        personal.downloaded(pdf.id, byteArrayOf(1))
        assertFalse(personal.onDevice(pdf.id))
        personal.delete(pdf.id)
        assertEquals(emptyList(), raw.names())
    }

    @Test
    fun olderEntriesReadAsOnDevice() {
        store.putString("personal.files", """[{"id":"a","name":"a.pdf","kind":"PDF","size":1,"addedAt":1,"online":true}]""")
        assertFalse(files().list.value.single().onlineOnly)
    }

    private class Files : BinaryFiles {
        private val map = linkedMapOf<String, ByteArray>()
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
    fun netStatusNamesWhatTheNetworkHas() {
        val base = PersonalFile(id = "f", name = "a.pdf", kind = "PDF", size = 1, addedAt = 1)
        assertEquals(NetStatus.ON_DEVICE, base.netStatus)
        assertEquals(NetStatus.SPREADING, base.copy(online = true, operators = 1).netStatus)
        assertEquals(NetStatus.SPREAD, base.copy(online = true, operators = 2).netStatus)
        assertEquals(NetStatus.PARTLY_SPREAD, base.copy(online = true, operators = 1, everSecured = true).netStatus)
        assertEquals(NetStatus.ONLY_IN_NET, base.copy(online = true, operators = 3, onlineOnly = true).netStatus)
        assertEquals(NetStatus.IN_BIN, base.copy(online = true, operators = 3, trashedAt = 5).netStatus)
    }
}
