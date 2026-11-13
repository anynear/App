package app.anynear.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max

actual fun shrinkImage(raw: ByteArray, maxEdge: Int, quality: Int, rotate: Int): ShrunkImage? = try {
    shrink(raw, maxEdge, quality, rotate)
} catch (e: OutOfMemoryError) {
    // Large photos for the storage (ADR 0067) on older phones: rather smaller than not at all.
    if (maxEdge > AttachmentLimits.IMAGE_MAX_EDGE) shrink(raw, AttachmentLimits.IMAGE_MAX_EDGE, quality, rotate) else null
}

private fun shrink(raw: ByteArray, maxEdge: Int, quality: Int, rotate: Int): ShrunkImage? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    // Roughly pre-shrink so that large photos do not blow up the memory.
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null

    val orientation = runCatching {
        ExifInterface(raw.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        }
        quarterTurns(rotate).takeIf { it != 0 }?.let { postRotate(it.toFloat()) }
        val scale = maxEdge.toFloat() / max(decoded.width, decoded.height)
        if (scale < 1f) postScale(scale, scale)
    }
    val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    // Transparency (PNG) onto white, JPEG has none.
    val opaque = if (result.hasAlpha()) {
        Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888).also { out ->
            android.graphics.Canvas(out).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(result, 0f, 0f, null) }
        }
    } else {
        result
    }
    val bytes = ByteArrayOutputStream().use { out ->
        opaque.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }
    return ShrunkImage(bytes, opaque.width, opaque.height).also {
        if (decoded !== result) decoded.recycle()
        if (opaque !== result) result.recycle()
        opaque.recycle()
    }
}

