package app.anynear.app

import app.anynear.anmp.core.DocKind
import app.anynear.anmp.core.DocRecognizer
import app.anynear.anmp.core.DocVectors
import app.anynear.anmp.core.FileTitles
import app.anynear.anmp.core.PhotoTopics
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.isImageFile
import app.anynear.data.DocTexts
import app.anynear.data.FileDoc
import app.anynear.data.FileTitleData
import app.anynear.data.PersonalFile
import app.anynear.data.PersonalFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Reads the files of the storages on the device and recognises kind and details (ADR 0067 §3, §4). Runs as long as the
 * app is open, one file after another across all storages: new ones right away, existing ones bit by bit. What a
 * person has chosen by hand or has already been recognised with the current rules (also by another device) stays.
 */
class DocReading(
    private val scope: CoroutineScope,
    private val texts: DocTexts,
    private val read: suspend (bytes: ByteArray, kind: AttachmentKind, name: String) -> String,
    /** Topics of a photo (ADR 0070), `null`: this device cannot do it. */
    private val look: suspend (bytes: ByteArray) -> List<String>? = { null },
    /** Version of this platform's reader (ADR 0078 §3); the app rereads older texts. */
    private val readerVersion: Int = 1,
    /** The small meaning model (ADR 0078 §5), `null` if it cannot be loaded. */
    private val vectors: suspend () -> DocVectors? = { null },
    /** Language of the titles (ADR 0091), e.g. `de` or `en`. */
    private val language: () -> String = { "de" },
    private val log: (String) -> Unit,
) {
    private val lock = Mutex()
    private val _version = MutableStateFlow(0)

    /** Increases as soon as new text has been read; the search then rebuilds itself. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** The meaning model for the search (ADR 0078 §5), `null` without a model. */
    suspend fun meaning(): DocVectors? = vectors()

    /** Whether the file has already been read (even if there was nothing in it). */
    fun isRead(id: String): Boolean = texts.has(id)

    /** Read text of a file, empty as long as it has not been read. */
    fun text(id: String): String = texts.get(id).orEmpty()

    /** Reads and recognises the files of [files], now and on every change of their list. */
    fun watch(files: PersonalFiles): Job = scope.launch(Dispatchers.Default) {
        files.list.collect { process(files) }
    }

    /** Text that was already read while searching the phone (ADR 0071): do not read it once more. */
    fun adopt(id: String, text: String) {
        scope.launch(Dispatchers.Default) {
            lock.withLock {
                if (!texts.has(id)) {
                    texts.put(id, text, readerVersion)
                    _version.value++
                }
            }
        }
    }

    /** Removes read text of files that no longer exist. */
    fun keepOnly(ids: Set<String>) {
        scope.launch(Dispatchers.Default) { lock.withLock { texts.keepOnly(ids) } }
    }

    /**
     * Recognise topics of a photo (ADR 0070), once per version of the image recognition. Only for photos, not for
     * scans; what another device has already looked at stays.
     */
    private suspend fun lookAt(files: PersonalFiles, id: String) {
        val file = files.list.value.firstOrNull { it.id == id } ?: return
        val doc = file.doc ?: return
        if (doc.kind != DocKind.PHOTO || doc.seen >= PhotoTopics.VERSION) return
        val bytes = files.bytes(id) ?: return
        val topics = look(bytes) ?: return
        val updated = FileDoc.of(doc.toInfo().withTopics(PhotoTopics.all.filter { it in topics }.toTypedArray(), PhotoTopics.VERSION))
        if (updated.topics == doc.topics) files.docFromOnline(id, updated) else files.setDoc(id, updated)
    }

    private suspend fun process(files: PersonalFiles) = lock.withLock {
        for (file in files.list.value) step(files, file.id, force = false)
        // With a better reader (ADR 0078 §3) reread what has already been read bit by bit; new files take precedence.
        val tried = mutableSetOf<String>()
        while (true) {
            val list = files.list.value
            val fresh = list.firstOrNull { !texts.has(it.id) && it.id !in tried }
            if (fresh != null) {
                tried += fresh.id
                step(files, fresh.id, force = false)
                continue
            }
            val old = list.firstOrNull { it.id !in tried && platformRead(it) && texts.version(it.id) < readerVersion } ?: break
            tried += old.id
            step(files, old.id, force = true)
        }
    }

    /**
     * Makes the title of a file (ADR 0091) after reading and recognising: when it has none, when it was made by older
     * rules or when the text was read anew ([again]). One set by hand stays.
     */
    private suspend fun title(files: PersonalFiles, id: String, again: Boolean) {
        val current = files.list.value.firstOrNull { it.id == id } ?: return
        val old = current.title?.toInfo()
        if (old?.byPerson == true || !(FileTitles.due(old) || again)) return
        val model = vectors()
        val made = withContext(Dispatchers.Default) {
            FileTitles.make(current.name, current.attachmentKind.wire, texts.get(id).orEmpty(), current.doc?.toInfo(), current.addedAt, language(), model)
        }
        // Same text with newer rules is only remembered here; otherwise on the relays too.
        if (made.sameAs(old)) files.titleFromOnline(id, FileTitleData.of(made)) else files.setTitle(id, FileTitleData.of(made))
    }

    /** Files whose text the platform reads (photos, scans, PDFs); only they are reread with a new reader. */
    private fun platformRead(file: PersonalFile): Boolean =
        file.attachmentKind == AttachmentKind.IMAGE || file.attachmentKind == AttachmentKind.PDF || isImageFile(file.attachmentKind, file.name)

    /** Reads (or rereads with [force]), looks at photos and recognises; a kind chosen by hand stays. */
    private suspend fun step(files: PersonalFiles, id: String, force: Boolean) {
        val file = files.list.value.firstOrNull { it.id == id } ?: return
        try {
            var changed = false
            if (force || !texts.has(file.id)) {
                val bytes = files.bytes(file.id) ?: return
                val text = read(bytes, file.attachmentKind, file.name)
                changed = !texts.has(file.id) || texts.get(file.id) != text
                texts.put(file.id, text, readerVersion)
                if (changed) _version.value++
            }
            lookAt(files, file.id)
            val current = files.list.value.firstOrNull { it.id == file.id } ?: return
            val doc = current.doc
            if (doc == null || !(doc.byPerson || (doc.rules >= DocRecognizer.RULES && !(force && changed)))) {
                val model = vectors()
                val found = withContext(Dispatchers.Default) {
                    DocRecognizer.recognizeWith(current.name, texts.get(file.id).orEmpty(), isImageFile(current.attachmentKind, current.name), model)
                }
                // Topics stay, the kind comes from the recognition of the text.
                val withTopics = doc?.let { found.withTopics(it.topics.toTypedArray(), it.seen) } ?: found
                // Same result with newer rules is only remembered here; otherwise on the relays too.
                if (withTopics.sameAs(doc?.toInfo())) files.docFromOnline(file.id, FileDoc.of(withTopics)) else files.setDoc(file.id, FileDoc.of(withTopics))
                lookAt(files, file.id)
            }
            title(files, file.id, force && changed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Ablage: ${file.id.take(8)} nicht gelesen (${e.message})")
        }
    }
}
