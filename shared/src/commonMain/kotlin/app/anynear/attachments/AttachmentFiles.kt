package app.anynear.attachments

import app.anynear.data.LocalCipher

/** Binary files in a directory in the private storage of the app. */
interface BinaryFiles {
    fun read(name: String): ByteArray?

    /** Replaces the content in one step. */
    fun write(name: String, content: ByteArray)

    fun delete(name: String)

    fun names(): List<String>
}

/** Directory [dirName] in the private data directory of the app (is deleted on uninstall). */
expect fun platformBinaryFiles(dirName: String): BinaryFiles

/**
 * Attachments on the phone, encrypted with the device key ([LocalCipher]). The file name is the
 * blob ID, which consists only of Base64url characters.
 */
class AttachmentFiles(private val files: BinaryFiles, private val cipher: LocalCipher) {
    fun read(blobId: String): ByteArray? {
        if (!valid(blobId)) return null
        return files.read(blobId)?.let(cipher::openBytes)
    }

    fun write(blobId: String, content: ByteArray) {
        require(valid(blobId)) { "Ungültige Blob-ID" }
        files.write(blobId, cipher.sealBytes(content))
    }

    fun exists(blobId: String): Boolean = valid(blobId) && blobId in files.names()

    /** Names of all files in the directory. */
    fun names(): List<String> = files.names()

    fun delete(blobId: String) {
        if (valid(blobId)) files.delete(blobId)
    }

    /** Deletes everything that none of the messages needs any more. */
    fun keepOnly(blobIds: Set<String>) {
        files.names().filter { it !in blobIds }.forEach(files::delete)
    }

    private fun valid(blobId: String) = blobId.isNotEmpty() && blobId.length <= 64 && blobId.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    companion object {
        const val DIR_NAME = "attachments"
    }
}
