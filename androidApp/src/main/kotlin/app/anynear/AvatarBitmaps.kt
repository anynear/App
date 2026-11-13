package app.anynear

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.min

/** Uploaded images (circle, person) for notifications and the share menu: cropped to a square, small. */
internal object AvatarBitmaps {
    /** Size in pixels; the system scales down itself, no icon needs more. */
    private const val SIZE = 192

    fun square(bytes: ByteArray?, size: Int = SIZE): Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        val decoded = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: return null
        val edge = min(decoded.width, decoded.height)
        val cropped = Bitmap.createBitmap(decoded, (decoded.width - edge) / 2, (decoded.height - edge) / 2, edge, edge)
        return if (edge == size) cropped else Bitmap.createScaledBitmap(cropped, size, size, true)
    }
}
