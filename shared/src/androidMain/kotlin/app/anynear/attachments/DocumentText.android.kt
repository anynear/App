package app.anynear.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import app.anynear.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Android (ADR 0067, ADR 0078): photos and pages without a text layer via PP-OCRv5 with LiteRT ([TextOcr]), the models
 * are supplied by :androidApp as assets under `ocr/`. PDFs from Android 15 on via the text layer of PdfRenderer.
 */

/** Version 2: PP-OCRv5 instead of Tesseract (ADR 0078); what was read with version 1 the app reads again. */
internal actual val readerVersion: Int = 2

/** Longest edge of an image for text recognition: as large as photos in the storage, more only costs memory. */
private const val OCR_MAX_EDGE = AttachmentLimits.STORE_IMAGE_MAX_EDGE

/** The models hold buffers; one instance, one after the other. */
private val ocrLock = Mutex()
private var reader: TextOcr? = null

internal actual suspend fun readPlatformText(bytes: ByteArray, kind: AttachmentKind): String? = withContext(Dispatchers.Default) {
    runCatching {
        when (kind) {
            AttachmentKind.IMAGE -> decodeForOcr(bytes)?.let { bitmap -> ocr(bitmap).also { bitmap.recycle() } }
            AttachmentKind.PDF -> pdfText(bytes)
            else -> null
        }
    }.getOrNull()
}

private fun decodeForOcr(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > OCR_MAX_EDGE) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    // Photos straight from the gallery (phone search, ADR 0071) often lie sideways, with their rotation only in EXIF.
    val degrees = runCatching {
        when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (degrees == 0f) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        .also { if (it !== bitmap) bitmap.recycle() }
}

internal actual suspend fun uprightTurn(image: ByteArray): Int = withContext(Dispatchers.Default) {
    runCatching {
        val bitmap = decodeForOcr(image) ?: return@runCatching 0
        try {
            ocrLock.withLock {
                val ocr = reader ?: TextOcr.open()?.also { reader = it } ?: return@withLock 0
                ocr.uprightTurn(bitmap)
            }
        } finally {
            bitmap.recycle()
        }
    }.getOrDefault(0)
}

private suspend fun ocr(bitmap: Bitmap): String? = ocrLock.withLock {
    val ocr = reader ?: TextOcr.open()?.also { reader = it } ?: return null
    ocr.read(bitmap)
}

private suspend fun pdfText(bytes: ByteArray): String? {
    // PdfRenderer needs a file; it lies unencrypted in the cache only briefly (as with the preview).
    val file = File.createTempFile("read", ".pdf", AndroidPlatform.requireContext().cacheDir)
    try {
        file.writeBytes(bytes)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                val layer = if (Build.VERSION.SDK_INT >= 35) textLayer(renderer) else ""
                // Scanned PDFs have no text layer: read the first pages as images.
                if (layer.count { it.isLetter() } >= 40) layer else ocrPages(renderer)
            }
        }
    } finally {
        file.delete()
    }
}

@RequiresApi(35)
private fun textLayer(renderer: PdfRenderer): String = buildString {
    for (i in 0 until renderer.pageCount) {
        renderer.openPage(i).use { page -> page.textContents.forEach { append(it.text).append('\n') } }
        if (length > app.anynear.anmp.core.DocRecognizer.MAX_TEXT) break
    }
}

private suspend fun ocrPages(renderer: PdfRenderer): String = buildString {
    for (i in 0 until minOf(renderer.pageCount, OCR_MAX_PAGES)) {
        val bitmap = renderer.openPage(i).use { page ->
            // About 200 dpi: pages are given in points (1/72 inch).
            val scale = 200f / 72f
            val w = (page.width * scale).toInt().coerceIn(1, OCR_MAX_EDGE)
            val h = (page.height.toLong() * w / page.width.coerceAtLeast(1)).toInt().coerceIn(1, OCR_MAX_EDGE * 2)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(Color.WHITE)
                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            }
        }
        ocr(bitmap)?.let { append(it).append('\n') }
        bitmap.recycle()
    }
}
