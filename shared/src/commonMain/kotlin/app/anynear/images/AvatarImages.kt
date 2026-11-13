package app.anynear.images

import app.anynear.attachments.AttachmentFiles
import app.anynear.data.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Uploaded images of circles and people on the phone, encrypted with the device key.
 * Also remembers when own images were last stored in the mailboxes.
 */
class AvatarImages(private val files: AttachmentFiles, private val store: KeyValueStore) {
    private val _version = MutableStateFlow(0)

    /** Changes as soon as an image is added; the UI then reloads. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private var uploads: Map<String, Long> =
        store.getString(UPLOADS_KEY)?.let { runCatching { json.decodeFromString(uploadsSerializer, it) }.getOrNull() }.orEmpty()

    fun bytes(ref: ImageRef): ByteArray? = runCatching { files.read(ref.id) }.getOrNull()

    fun has(ref: ImageRef): Boolean = files.exists(ref.id)

    fun put(ref: ImageRef, bytes: ByteArray) {
        files.write(ref.id, bytes)
        _version.update { it + 1 }
    }

    /** Last stored in the mailboxes (ms), 0 if never. */
    fun uploadedAt(ref: ImageRef): Long = uploads[ref.id] ?: 0L

    fun markUploaded(ref: ImageRef, atMillis: Long) {
        uploads = uploads + (ref.id to atMillis)
        store.putString(UPLOADS_KEY, json.encodeToString(uploadsSerializer, uploads))
    }

    /** Deletes all images that nothing refers to any more. */
    fun keepOnly(refs: Set<ImageRef>) {
        val ids = refs.map { it.id }.toSet()
        runCatching { files.keepOnly(ids) }
        if (uploads.keys.any { it !in ids }) {
            uploads = uploads.filterKeys { it in ids }
            store.putString(UPLOADS_KEY, json.encodeToString(uploadsSerializer, uploads))
        }
    }

    companion object {
        const val DIR_NAME = "avatars"
        private const val UPLOADS_KEY = "avatar_uploads.v1"
        private val json = Json { ignoreUnknownKeys = true }
        private val uploadsSerializer = MapSerializer(String.serializer(), Long.serializer())
    }
}
