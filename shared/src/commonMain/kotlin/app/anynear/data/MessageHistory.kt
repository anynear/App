package app.anynear.data

import app.anynear.mesh.ChatMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Text file in the private storage of the app. Writing replaces the content in one step. */
interface TextFile {
    fun read(): String?

    fun write(content: String)
}

/** File [name] in the private data directory of the app (is deleted on uninstall). */
expect fun platformTextFile(name: String): TextFile

/**
 * Message history on the device so that it survives restarts and app updates. Is stored encrypted with
 * [cipher] in [file]. An unreadable file yields an empty history instead of a crash.
 */
class MessageHistory(private val file: TextFile, private val cipher: LocalCipher) {
    @Serializable
    data class Snapshot(
        val messages: List<ChatMessage> = emptyList(),
        /** Radio packets already taken over (sender, packet ID). */
        val seenPackets: List<Pair<Int, Int>> = emptyList(),
        /** Message IDs from the internet path already taken over. */
        val seenOnlineIds: List<String> = emptyList(),
        val version: Int = VERSION,
    )

    fun load(): Snapshot? {
        val sealed = runCatching { file.read() }.getOrNull() ?: return null
        val raw = cipher.open(sealed) ?: return null
        return runCatching { json.decodeFromString(Snapshot.serializer(), raw) }.getOrNull()
    }

    fun save(snapshot: Snapshot) {
        file.write(cipher.seal(json.encodeToString(Snapshot.serializer(), snapshot)))
    }

    companion object {
        const val FILE_NAME = "messages.v1"
        private const val VERSION = 1
        private val json = Json { ignoreUnknownKeys = true }
    }
}
