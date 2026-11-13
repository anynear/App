package app.anynear.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import app.anynear.AndroidPlatform
import app.anynear.app.AppController
import app.anynear.attachments.AttachmentLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What another app sends via its share menu (ACTION_SEND, ACTION_SEND_MULTIPLE): text or link and files.
 * `null` if the intent is not a share. Reads at most one file more than a message can carry, so that
 * the notice “höchstens …” appears without reading everything.
 */
suspend fun sharedContentOf(intent: Intent): AppController.SharedContent? {
    if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return null
    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
    val uris = streamsOf(intent).take(AttachmentLimits.MAX_PER_MESSAGE + 1)
    val resolver = AndroidPlatform.requireContext().contentResolver
    val files = withContext(Dispatchers.IO) {
        uris.map { uri ->
            val type = runCatching { resolver.getType(uri) }.getOrNull() ?: intent.type.orEmpty()
            val image = type.startsWith("image/")
            val file = readPickedFile(uri, if (image) MAX_RAW_IMAGE_BYTES else AttachmentLimits.MAX_BYTES)
            AppController.SharedContent.File(file.bytes, file.name, image, file.tooLarge)
        }
    }
    return AppController.SharedContent(text, files)
}

@Suppress("DEPRECATION")
private fun streamsOf(intent: Intent): List<Uri> = when {
    intent.action == Intent.ACTION_SEND_MULTIPLE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
    }.orEmpty()
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
    else -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM))
}
