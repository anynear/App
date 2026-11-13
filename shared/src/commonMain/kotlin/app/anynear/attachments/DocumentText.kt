package app.anynear.attachments

import app.anynear.anmp.core.DocRecognizer

/*
 * Read text from a file of the storage, only on the device (ADR 0067 §3): photos via the text recognition of the system
 * (iOS Vision) or PP-OCRv5 (Android, ADR 0078), PDFs via their text layer, otherwise the first pages as an image. Text
 * files directly. The result never leaves the device.
 */

/**
 * Version of the reader of this platform (ADR 0078 §3). If it rises, the device reads existing files anew because the
 * new reader recognises more.
 */
internal expect val readerVersion: Int

/** Text from a photo ([AttachmentKind.IMAGE]) or PDF; `null` if the platform cannot read it. */
internal expect suspend fun readPlatformText(bytes: ByteArray, kind: AttachmentKind): String?

/**
 * How far a photo has to be rotated clockwise (0, 90, 180, 270) so that text on it is upright, detected on the
 * device. 0 if there is hardly any text on it, the direction is uncertain or the platform cannot do it. The orientation
 * from the image data (EXIF) is already taken into account.
 */
internal expect suspend fun uprightTurn(image: ByteArray): Int

/** At most this many pages of a PDF without a text layer go through the text recognition. */
internal const val OCR_MAX_PAGES = 3

/** Extensions whose content is already text. */
private val TEXT_ENDINGS = setOf("txt", "csv", "md", "ics", "vcf", "json", "xml", "html", "htm")

/** Extensions of images that were stored as a file (not as a photo). */
private val IMAGE_ENDINGS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")

/** An image, whether stored as a photo or as a file: with hardly any text a photo, otherwise a scan. */
internal fun isImageFile(kind: AttachmentKind, name: String): Boolean =
    kind == AttachmentKind.IMAGE || (kind == AttachmentKind.FILE && name.substringAfterLast('.', "").lowercase() in IMAGE_ENDINGS)

/** Text of the file for search and recognition, at most [DocRecognizer.MAX_TEXT] characters; empty if nothing is readable. */
internal suspend fun readDocumentText(bytes: ByteArray, kind: AttachmentKind, name: String): String {
    val text = when (kind) {
        AttachmentKind.IMAGE, AttachmentKind.PDF -> readPlatformText(bytes, kind)
        AttachmentKind.FILE -> when (name.substringAfterLast('.', "").lowercase()) {
            in TEXT_ENDINGS -> bytes.decodeToString()
            in IMAGE_ENDINGS -> readPlatformText(bytes, AttachmentKind.IMAGE)
            else -> null
        }
        else -> null
    }
    return text.orEmpty().take(DocRecognizer.MAX_TEXT)
}

/**
 * What a photo shows (ADR 0070), as topics from [app.anynear.anmp.core.PhotoTopics]; only on the device. Only that
 * people are visible is detected, never who. `null` if this device cannot do it.
 */
internal expect suspend fun recognizePhotoTopics(bytes: ByteArray): List<String>?
