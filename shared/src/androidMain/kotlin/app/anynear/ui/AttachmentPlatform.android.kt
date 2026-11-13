package app.anynear.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.anynear.AppConfig
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import app.anynear.AndroidPlatform
import app.anynear.attachments.AttachmentLimits
import app.anynear.i18n.texts
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal actual fun rememberAttachmentPicker(fileLimit: Long, onPicked: (images: Boolean, files: List<PickedFile>) -> Unit): AttachmentPicker {
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onPicked)
    val currentFileLimit = rememberUpdatedState(fileLimit)
    fun deliver(images: Boolean, uris: List<Uri>, limit: Long) {
        if (uris.isEmpty()) return
        scope.launch {
            val files = withContext(Dispatchers.IO) { uris.map { readPickedFile(it, limit) } }
            callback.value(images, files)
        }
    }
    val images = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(AttachmentLimits.MAX_PER_MESSAGE)) {
        deliver(true, it, MAX_RAW_IMAGE_BYTES)
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        deliver(false, it, currentFileLimit.value)
    }
    // Take a photo: the camera app writes to a file in the cache (via the FileProvider), then it is read and
    // deleted. Because the app declares CAMERA (QR code), Android requires the permission for the camera app too.
    val context = AndroidPlatform.requireContext()
    val photoFile = remember { mutableListOf<File>() }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = photoFile.removeLastOrNull() ?: return@rememberLauncherForActivityResult
        if (!ok || !file.exists() || file.length() == 0L) {
            file.delete()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                val bytes = runCatching { file.readBytes() }.getOrNull()
                file.delete()
                PickedFile(bytes, file.name)
            }
            callback.value(true, listOf(picked))
        }
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
        photoFile += file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        runCatching { camera.launch(uri) }.onFailure { photoFile.remove(file) }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }
    return remember(images, files, camera, cameraPermission) {
        object : AttachmentPicker {
            // The contract sets the maximum number; the picker does not select more.
            override fun pickImages(max: Int) = images.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

            override fun pickFiles() = files.launch(arrayOf("*/*"))

            override fun takePhoto() {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

internal actual fun clipboardHasImage(): Boolean {
    // Only the description: reading it is not reported by Android as “pasted”.
    val description = clipboard()?.primaryClipDescription ?: return false
    return (0 until description.mimeTypeCount).any { description.getMimeType(it).startsWith("image/") }
}

internal actual suspend fun clipboardImages(): List<PickedFile> = withContext(Dispatchers.IO) {
    val clip = clipboard()?.primaryClip ?: return@withContext emptyList()
    (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }.map { readPickedFile(it, MAX_RAW_IMAGE_BYTES) }
}

private fun clipboard() = AndroidPlatform.requireContext().getSystemService(ClipboardManager::class.java)

@OptIn(ExperimentalFoundationApi::class)
internal actual fun Modifier.receiveImages(onImages: (List<PickedFile>) -> Unit): Modifier = contentReceiver { content ->
    if (!content.hasMediaType(MediaType.Image)) return@contentReceiver content
    val uris = mutableListOf<Uri>()
    // Take over images, text stays for the input field.
    val rest = content.consume { item -> item.uri?.let(uris::add) != null }
    if (uris.isNotEmpty()) {
        val files = uris.map { readPickedFile(it, MAX_RAW_IMAGE_BYTES) }
        onImages(files)
    }
    rest
}

internal fun readPickedFile(uri: Uri, limit: Long): PickedFile {
    val resolver = AndroidPlatform.requireContext().contentResolver
    var name = texts.chat.defaultFileName
    var size = -1L
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
    }
    if (size > limit) return PickedFile(null, name, tooLarge = true)
    val bytes = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            // Do not read more than allowed, even if the size was unknown beforehand.
            input.readNBytesCompat(limit + 1)
        }
    }.getOrNull() ?: return PickedFile(null, name)
    if (bytes.size > limit) return PickedFile(null, name, tooLarge = true)
    return PickedFile(bytes, name)
}

private fun java.io.InputStream.readNBytesCompat(max: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (total < max) {
        val n = read(buffer, 0, minOf(buffer.size.toLong(), max - total).toInt())
        if (n < 0) break
        out.write(buffer, 0, n)
        total += n
    }
    return out.toByteArray()
}

internal actual fun ImageBitmap.toJpeg(quality: Int): ByteArray? = runCatching {
    java.io.ByteArrayOutputStream().also { asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}.getOrNull()

internal actual suspend fun renderPdfFirstPage(bytes: ByteArray, widthPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    // PdfRenderer needs a file; it lies unencrypted in the cache only briefly.
    val file = File.createTempFile("preview", ".pdf", AndroidPlatform.requireContext().cacheDir)
    try {
        file.writeBytes(bytes)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) return@withContext null
                renderer.openPage(0).use { page ->
                    val height = (widthPx.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, widthPx * 3)
                    val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }
        }
    } catch (e: Exception) {
        null
    } finally {
        file.delete()
    }
}

/**
 * PdfRenderer needs a file: it lies unencrypted in the cache only while the viewer is open and is deleted
 * on closing. The renderer can handle only one page at a time, hence the lock.
 */
private class AndroidPdfPages(private val file: File, private val fd: ParcelFileDescriptor, private val renderer: PdfRenderer) : PdfPages {
    private val lock = Mutex()
    private var closed = false
    override val count: Int = renderer.pageCount

    override suspend fun render(index: Int, widthPx: Int): ImageBitmap? = lock.withLock {
        if (closed || index !in 0 until count) return@withLock null
        withContext(Dispatchers.IO) {
            runCatching {
                renderer.openPage(index).use { page ->
                    val height = (widthPx.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, widthPx * 3)
                    val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        // Only once no page is being drawn any more.
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            lock.withLock {
                runCatching { renderer.close() }
                runCatching { fd.close() }
                file.delete()
            }
        }
    }
}

internal actual suspend fun openPdf(bytes: ByteArray): PdfPages? = withContext(Dispatchers.IO) {
    val dir = File(AndroidPlatform.requireContext().cacheDir, "view").apply { mkdirs() }
    // Leftovers in case the app was terminated while viewing.
    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 10 * 60_000 }?.forEach { it.delete() }
    val file = File.createTempFile("view", ".pdf", dir)
    try {
        file.writeBytes(bytes)
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(fd)
        } catch (e: Exception) {
            fd.close()
            throw e
        }
        if (renderer.pageCount == 0) {
            renderer.close()
            fd.close()
            file.delete()
            null
        } else {
            AndroidPdfPages(file, fd, renderer)
        }
    } catch (e: Exception) {
        file.delete()
        null
    }
}

/** Android cannot show Office files and the like without another app. */
internal actual fun previewInApp(bytes: ByteArray, name: String, mimeType: String): Boolean = false

internal actual fun openWithOtherApp(bytes: ByteArray, name: String, mimeType: String): Boolean {
    val context = AndroidPlatform.requireContext()
    val dir = File(context.cacheDir, "open").apply { mkdirs() }
    // Only the most recently handed-over file stays.
    dir.listFiles()?.forEach { it.delete() }
    val safeName = handOverName(name, mimeType)
    val file = File(dir, safeName)
    file.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        // No app for this type: share menu, in it e.g. “In Dateien speichern” or another app.
        shareFile(bytes, safeName, mimeType)
    }
}

internal actual suspend fun saveToDevice(bytes: ByteArray, name: String, mimeType: String): SaveResult = withContext(Dispatchers.IO) {
    val context = AndroidPlatform.requireContext()
    val image = mimeType.startsWith("image/")
    // Before Android 10, writing to “Bilder”/“Downloads” would need a permission: there the share menu.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return@withContext if (withContext(Dispatchers.Main) { shareFile(bytes, name, mimeType) }) SaveResult.Handed else SaveResult.Failed
    }
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            if (image) "${Environment.DIRECTORY_PICTURES}/${AppConfig.APP_NAME}" else Environment.DIRECTORY_DOWNLOADS,
        )
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val collection = if (image) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }
    val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return@withContext SaveResult.Failed
    val written = runCatching { resolver.openOutputStream(uri)?.use { it.write(bytes) } != null }.getOrDefault(false)
    if (!written) {
        resolver.delete(uri, null, null)
        return@withContext SaveResult.Failed
    }
    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    SaveResult.Saved(if (image) "${texts.chat.picturesFolder} › ${AppConfig.APP_NAME}" else "Downloads")
}

/** System share menu (ACTION_SEND) with a briefly decrypted copy in the cache (as with [openWithOtherApp]). */
internal actual fun shareFile(bytes: ByteArray, name: String, mimeType: String): Boolean {
    val context = AndroidPlatform.requireContext()
    val dir = File(context.cacheDir, "open").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, handOverName(name, mimeType))
    file.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType(mimeType).putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** File name for handing over: without special characters, never empty, PDFs with “.pdf” so the other app recognises them. */
private fun handOverName(name: String, mimeType: String): String =
    name.replace(Regex("[^A-Za-z0-9._ äöüÄÖÜß-]"), "_").ifBlank { texts.chat.defaultFileName }
        .let { if (mimeType == "application/pdf" && !it.endsWith(".pdf", ignoreCase = true)) "$it.pdf" else it }
