package app.anynear.data

import app.anynear.attachments.AttachmentFiles
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.DraftAttachment
import app.anynear.chats.key
import app.anynear.mesh.Conversation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * Drafts not sent yet, per conversation, so that they survive a change of page and a
 * restart. Text and details about the attachments are in [store] (encrypted on the device, see
 * [EncryptedKeyValueStore]), the attachments themselves encrypted in [files].
 *
 * Text and attachments are under separate keys: the text is saved directly on every
 * keystroke, the attachments one after another in the background. The attachment functions are therefore not
 * thread-safe and belong on a single background thread.
 */
class DraftStore(private val store: KeyValueStore, private val files: AttachmentFiles) {
    @Serializable
    private data class SavedAttachment(val file: String, val kind: String, val name: String = "", val width: Int = 0, val height: Int = 0, val duration: Long = 0)

    /** One file per attachment so that unchanged attachments are not rewritten on every change. */
    private val fileOf = mutableMapOf<DraftAttachment, String>()

    fun text(conversation: Conversation): String = store.getString(textKey(conversation)).orEmpty()

    fun setText(conversation: Conversation, text: String) = store.putString(textKey(conversation), text.ifEmpty { null })

    /** Reads the attachments from the device; missing or unreadable ones are left out. */
    fun attachments(conversation: Conversation): List<DraftAttachment> = saved(conversation).mapNotNull { a ->
        val kind = AttachmentKind.entries.firstOrNull { it.name == a.kind } ?: return@mapNotNull null
        val bytes = files.read(a.file) ?: return@mapNotNull null
        DraftAttachment(kind, bytes, a.name, a.width, a.height, a.duration).also { fileOf[it] = a.file }
    }

    /** Writes new attachments and deletes removed ones; an empty list clears everything (e.g. after sending). */
    fun setAttachments(conversation: Conversation, attachments: List<DraftAttachment>) {
        val before = saved(conversation).map { it.file }.toSet()
        val now = attachments.map { a ->
            val file = fileOf.getOrPut(a) { Uuid.random().toString().also { files.write(it, a.bytes) } }
            SavedAttachment(file, a.kind.name, a.name, a.width, a.height, a.durationMillis)
        }
        store.putString(filesKey(conversation), now.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) })
        val kept = now.map { it.file }.toSet()
        (before - kept).forEach(files::delete)
        fileOf.values.removeAll { it in before && it !in kept }
    }

    /** The conversation got a new key (a device became a person, ADR 0042): its draft moves along, unless [to] has one. */
    fun move(from: Conversation, to: Conversation) {
        if (from == to) return
        if (text(to).isEmpty()) store.putString(textKey(to), store.getString(textKey(from)))
        if (saved(to).isEmpty()) store.putString(filesKey(to), store.getString(filesKey(from)))
        store.putString(textKey(from), null)
        store.putString(filesKey(from), null)
    }

    private fun saved(conversation: Conversation): List<SavedAttachment> =
        store.getString(filesKey(conversation))?.let { runCatching { json.decodeFromString<List<SavedAttachment>>(it) }.getOrNull() }.orEmpty()

    private fun textKey(conversation: Conversation) = "draft.text." + conversation.key()

    private fun filesKey(conversation: Conversation) = "draft.files." + conversation.key()

    companion object {
        const val DIR_NAME = "drafts"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
