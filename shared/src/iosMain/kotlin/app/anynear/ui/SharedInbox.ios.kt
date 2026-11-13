package app.anynear.ui

import app.anynear.app.AppController
import app.anynear.attachments.AttachmentLimits
import app.anynear.attachments.toByteArray
import app.anynear.data.APP_GROUP
import app.anynear.i18n.texts
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.dataWithContentsOfFile

@Serializable
private class SharedItem(val text: String = "", val files: List<SharedItemFile> = emptyList())

@Serializable
private class SharedItemFile(val file: String, val name: String = "", val image: Boolean = false)

private val itemJson = Json { ignoreUnknownKeys = true }

/**
 * What the Share Extension (iosApp/Share) has stored: per share one folder `<app group>/Shared/<id>/` with
 * `item.json` and the files. Reads all finished folders (half-done ones end in `.part`), merges them and
 * deletes them. `null` if nothing is there.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun takeSharedFromExtension(): AppController.SharedContent? {
    val fm = NSFileManager.defaultManager
    val root = fm.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP)?.path?.let { "$it/Shared" } ?: return null
    val dirs = fm.contentsOfDirectoryAtPath(root, error = null)?.filterIsInstance<String>()
        ?.filterNot { it.endsWith(".part") }?.sorted().orEmpty()
    if (dirs.isEmpty()) return null
    val parts = mutableListOf<String>()
    val files = mutableListOf<AppController.SharedContent.File>()
    for (dir in dirs) {
        val path = "$root/$dir"
        val item = NSData.dataWithContentsOfFile("$path/item.json")?.toByteArray()?.decodeToString()
            ?.let { runCatching { itemJson.decodeFromString<SharedItem>(it) }.getOrNull() }
        if (item != null) {
            if (item.text.isNotBlank()) parts += item.text
            item.files.take(AttachmentLimits.MAX_PER_MESSAGE + 1 - files.size).forEach { f ->
                files += readSharedFile("$path/${f.file}", f.name.ifEmpty { texts.chat.defaultFileName }, f.image)
            }
        }
        fm.removeItemAtPath(path, error = null)
    }
    return AppController.SharedContent(parts.joinToString("\n"), files)
}

@OptIn(ExperimentalForeignApi::class)
private fun readSharedFile(path: String, name: String, image: Boolean): AppController.SharedContent.File {
    val limit = if (image) MAX_RAW_IMAGE_BYTES else AttachmentLimits.MAX_BYTES
    val size = (NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)?.get(NSFileSize) as? NSNumber)?.longLongValue
    if (size != null && size > limit) return AppController.SharedContent.File(null, name, image, tooLarge = true)
    val bytes = NSData.dataWithContentsOfFile(path)?.toByteArray()
    return AppController.SharedContent.File(bytes, name, image)
}
