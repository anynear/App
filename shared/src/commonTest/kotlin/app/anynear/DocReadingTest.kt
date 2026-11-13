package app.anynear

import app.anynear.anmp.core.DocKind
import app.anynear.anmp.core.DocRecognizer
import app.anynear.anmp.core.PhotoTopics
import app.anynear.app.DocReading
import app.anynear.attachments.AttachmentFiles
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.BinaryFiles
import app.anynear.attachments.DraftAttachment
import app.anynear.attachments.readDocumentText
import app.anynear.core.LocalKey
import app.anynear.data.DocTexts
import app.anynear.data.FileDoc
import app.anynear.data.FileSort
import app.anynear.data.FileTitleData
import app.anynear.data.PersonalFile
import app.anynear.data.PersonalFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ADR 0067: read text on the device only, detect the kind, keep a kind chosen by hand. All texts are made up. */
class DocReadingTest {
    private val secrets = InMemorySecretStore()
    private val store = InMemoryStore()
    private val cipher = LocalKey.cipher(secrets)
    private val files = PersonalFiles(store, AttachmentFiles(Files(), cipher), now = { 42L })
    private val textDir = Files()
    private val texts = DocTexts(AttachmentFiles(textDir, cipher))

    private val invoice = "Standesamt Beispielhausen\nGeburtsurkunde\nGeboren am 12.03.2019 in Beispielhausen"

    @Test
    fun readsRecognizesAndKeepsTheChoiceOfAPerson() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var reads = 0
        val reading = DocReading(scope, texts, { _, _, _ -> reads++; invoice }) {}
        val changed = mutableListOf<String>()
        files.listener = object : PersonalFiles.Listener {
            override fun changed(file: PersonalFile) {}
            override fun deleted(id: String) {}
            override fun docChanged(file: PersonalFile) { changed += file.id }
        }
        val file = files.add(DraftAttachment(AttachmentKind.IMAGE, byteArrayOf(1, 2), name = "IMG_1.jpg"), "IMG_1.jpg")!!
        reading.watch(files)
        val doc = until { files.list.value.single().doc }
        assertEquals(DocKind.CERTIFICATE, doc.kind)
        assertEquals(1552392000000L, doc.date) // 12.03.2019, 12:00 UTC
        assertTrue(files.list.value.single().docPending)
        // Details and title each say „changed“, for the same file.
        assertEquals(listOf(file.id), changed.distinct())
        // The text is stored encrypted on the device, not in plain text.
        assertEquals(invoice, reading.text(file.id))
        assertFalse(textDir.read(file.id)!!.decodeToString().contains("Geburtsurkunde"))

        // ADR 0091: after reading and recognising, a title from kind and date; it goes up with the details.
        val title = until { files.list.value.single().title }
        assertEquals("Geburtsurkunde März 2019", title.text)
        assertFalse(title.byPerson)

        // Uploaded: no longer pending.
        files.docSent(file.id, doc, title)
        assertFalse(files.list.value.single().docPending)

        // A title set by hand: a made one does not replace it; empty gives it back.
        files.setTitle(file.id, FileTitleData("Meine Urkunde", byPerson = true))
        files.setTitle(file.id, title)
        assertEquals("Meine Urkunde", files.list.value.single().shown)
        assertEquals("IMG_1.jpg", files.list.value.single().name)
        assertTrue(files.list.value.single().hasTitle)

        // Chosen by hand: detection does not replace it.
        files.setDoc(file.id, FileDoc.of(doc.toInfo().chosen(DocKind.BANK)))
        files.setDoc(file.id, FileDoc.of(DocRecognizer.recognize("x", invoice, false)))
        assertEquals(DocKind.BANK, files.list.value.single().doc?.kind)
        delay(100)
        assertEquals(1, reads)
        scope.cancel()
    }

    /** ADR 0078 §3: a better reader re-reads and re-detects what exists; what was chosen by hand stays. */
    @Test
    fun betterReaderReadsAgain() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // Version 1 yielded almost nothing (like Tesseract on a skewed photo).
        texts.put("old", "", 1)
        assertEquals(1, texts.version("old"))
        assertEquals("", texts.get("old"))
        val photo = files.add(DraftAttachment(AttachmentKind.IMAGE, byteArrayOf(3), name = "IMG_2.jpg"), "IMG_2.jpg")!!
        val chosen = files.add(DraftAttachment(AttachmentKind.IMAGE, byteArrayOf(4), name = "IMG_3.jpg"), "IMG_3.jpg")!!
        texts.put(photo.id, "", 1)
        texts.put(chosen.id, "", 1)
        files.setDoc(chosen.id, FileDoc(DocKind.INSURANCE, byPerson = true))
        var reads = 0
        val reading = DocReading(scope, texts, { _, _, _ -> reads++; invoice }, readerVersion = 2) {}
        reading.watch(files)
        val doc = until { files.list.value.firstOrNull { it.id == photo.id }?.doc?.takeIf { it.kind == DocKind.CERTIFICATE } }
        assertEquals(DocKind.CERTIFICATE, doc.kind)
        assertEquals(2, texts.version(photo.id))
        until { texts.version(chosen.id).takeIf { it == 2 } }
        assertEquals(DocKind.INSURANCE, files.list.value.first { it.id == chosen.id }.doc?.kind)
        delay(100)
        assertEquals(2, reads)
        scope.cancel()
    }

    /** ADR 0070: photos get topics, once per version; scans do not. */
    @Test
    fun photosGetTopicsOnce() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var looks = 0
        val reading = DocReading(scope, texts, { _, _, _ -> "" }, { looks++; listOf(PhotoTopics.ANIMALS, PhotoTopics.PEOPLE) }) {}
        val photo = files.add(DraftAttachment(AttachmentKind.IMAGE, byteArrayOf(9), name = ""), "Foto.jpg")!!
        reading.watch(files)
        val doc = until { files.list.value.single().doc?.takeIf { it.seen > 0 } }
        assertEquals(DocKind.PHOTO, doc.kind)
        assertEquals(listOf(PhotoTopics.PEOPLE, PhotoTopics.ANIMALS), doc.topics)
        assertEquals(PhotoTopics.VERSION, doc.seen)
        // Another kind chosen by hand: the topics stay, it is not looked at again.
        files.setDoc(photo.id, FileDoc.of(doc.toInfo().chosen(DocKind.OTHER)))
        delay(100)
        assertEquals(listOf(PhotoTopics.PEOPLE, PhotoTopics.ANIMALS), files.list.value.single().doc?.topics)
        assertEquals(1, looks)
        scope.cancel()
    }

    @Test
    fun docFromAnotherDeviceDoesNotOverwriteAPendingChange() {
        val file = files.add(DraftAttachment(AttachmentKind.PDF, byteArrayOf(1), name = "a.pdf"), "a.pdf")!!
        files.setDoc(file.id, FileDoc(DocKind.CONTRACT, byPerson = true, rules = 1))
        files.docFromOnline(file.id, FileDoc(DocKind.INVOICE, rules = 1))
        assertEquals(DocKind.CONTRACT, files.list.value.single().doc?.kind)
        files.docSent(file.id, files.list.value.single().doc)
        files.docFromOnline(file.id, FileDoc(DocKind.INVOICE, rules = 1))
        assertEquals(DocKind.INVOICE, files.list.value.single().doc?.kind)
    }

    @Test
    fun olderStoredListWithoutDocStillLoads() {
        store.putString("personal.files", """[{"id":"f1","name":"a.pdf","kind":"PDF","size":1,"addedAt":2}]""")
        val loaded = PersonalFiles(store, AttachmentFiles(Files(), cipher), now = { 42L }).list.value.single()
        assertNull(loaded.doc)
        assertNull(loaded.title)
        assertFalse(loaded.docPending)
    }

    @Test
    fun plainTextFilesAreReadDirectly() = runBlocking {
        assertEquals("Milch, Brot", readDocumentText("Milch, Brot".encodeToByteArray(), AttachmentKind.FILE, "liste.txt"))
        assertEquals("", readDocumentText(byteArrayOf(1, 2), AttachmentKind.FILE, "tabelle.xlsx"))
    }

    private suspend fun <T : Any> until(get: () -> T?): T = withTimeout(5_000) {
        while (true) {
            get()?.let { return@withTimeout it }
            delay(10)
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
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

    /** ADR 0091 §7: title or file name A–Z with umlauts and numbers; equal ones newest first. */
    @Test
    fun sortsByTitleOrName() {
        fun f(id: String, name: String, title: String?, at: Long) =
            PersonalFile(id, name, AttachmentKind.PDF.name, 1, at, title = title?.let { FileTitleData(it) })
        val list = listOf(
            f("a", "z.pdf", "Ärztebrief", 1),
            f("b", "a.pdf", "Seite 10", 2),
            f("c", "m.pdf", null, 3),
            f("d", "b.pdf", "Seite 2", 4),
        )
        assertEquals(listOf("d", "c", "b", "a"), FileSort.NEWEST.sort(list).map { it.id })
        assertEquals(listOf("a", "c", "d", "b"), FileSort.TITLE.sort(list).map { it.id })
        assertEquals(listOf("b", "d", "c", "a"), FileSort.NAME.sort(list).map { it.id })
        assertEquals(FileSort.NEWEST, FileSort.of(null))
        assertEquals(FileSort.TITLE, FileSort.of("TITLE"))
    }
}
