package app.anynear.data

import app.anynear.anmp.core.DocRecognizer
import app.anynear.attachments.AttachmentFiles

/**
 * Text that was read on this device from the files of the storages (ADR 0067 §3), per file ID, encrypted with the
 * device key. Not on the relays and not in the backup: every device can read it anew.
 * A file with empty text has been read, there was just nothing in it. Every text carries the version of the reader
 * that read it (ADR 0078 §3); older texts without it count as version 1.
 */
class DocTexts(private val files: AttachmentFiles) {
    private var known: MutableSet<String>? = null
    private val versions = HashMap<String, Int>()

    private fun names(): MutableSet<String> = known ?: files.names().toMutableSet().also { known = it }

    fun has(id: String): Boolean = id in names()

    fun get(id: String): String? = if (has(id)) files.read(id)?.decodeToString()?.let { split(id, it) } else null

    /** Version of the reader that read the text of [id]; 0 if it has not been read. */
    fun version(id: String): Int {
        if (!has(id)) return 0
        versions[id]?.let { return it }
        get(id)
        return versions[id] ?: 1
    }

    fun put(id: String, text: String, version: Int = 1) {
        files.write(id, (HEADER + version + "\n" + text.take(DocRecognizer.MAX_TEXT)).encodeToByteArray())
        names() += id
        versions[id] = version
    }

    private fun split(id: String, stored: String): String {
        if (!stored.startsWith(HEADER)) return stored.also { versions[id] = 1 }
        val end = stored.indexOf('\n')
        versions[id] = stored.substring(HEADER.length, end).toIntOrNull() ?: 1
        return stored.substring(end + 1)
    }

    /** Removes the text of all files that no longer exist in any storage. */
    fun keepOnly(ids: Set<String>) {
        files.keepOnly(ids)
        known = null
        versions.keys.retainAll(ids)
    }

    companion object {
        /** Not in `APP_BINARY_DIRS`: not in the backup, but deleted on reset. */
        const val DIR_NAME = "file-texts"

        /** Before the text: `\u0000v<Version>\n`. A null character is in no text that was read. */
        private const val HEADER = "\u0000v"
    }
}
