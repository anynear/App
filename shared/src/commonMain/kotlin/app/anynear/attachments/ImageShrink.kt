package app.anynear.attachments

/** Shrunk photo as JPEG, without location and other image data. */
class ShrunkImage(val bytes: ByteArray, val width: Int, val height: Int)

/**
 * Rotates a photo the right way up (orientation from the image data), shrinks it to at most
 * [maxEdge] pixels on the longest edge and saves it anew as JPEG. The new image no longer contains any
 * image data (EXIF), so no place of capture either. [rotate] then additionally rotates clockwise (0, 90, 180, 270
 * degrees), e.g. for a document photographed sideways or „Drehen“ in the storage. `null` if [raw] is no readable image.
 */
expect fun shrinkImage(raw: ByteArray, maxEdge: Int, quality: Int, rotate: Int = 0): ShrunkImage?

/** [degrees] as 0, 90, 180 or 270 (also for negative values). */
fun quarterTurns(degrees: Int): Int = (((degrees / 90) % 4 + 4) % 4) * 90
