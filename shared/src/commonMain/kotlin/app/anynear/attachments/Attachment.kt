package app.anynear.attachments

import app.anynear.i18n.texts
import kotlinx.serialization.Serializable

/** Kind of an attachment (ADR 0011, 0030, 0041). Photos, PDF and voice have their own display, everything else is [FILE]. */
@Serializable
enum class AttachmentKind(
    /** Short form in the envelope (`t`). */
    internal val wire: String,
    val mimeType: String,
) {
    IMAGE("jpg", "image/jpeg"),
    PDF("pdf", "application/pdf"),

    /** Voice message: AAC in MP4, mono (ADR 0030). */
    AUDIO("m4a", "audio/mp4"),

    /** Any other file, unchanged, with name; the MIME type follows from the extension ([mimeTypeOf], ADR 0041). */
    FILE("file", "application/octet-stream"),
    ;

    companion object {
        internal fun fromWire(value: String): AttachmentKind? = entries.firstOrNull { it.wire == value }
    }
}

/** Where an attachment currently stands. */
@Serializable
enum class AttachmentState {
    /** Outgoing: is on the phone, waiting for internet or being stored. */
    UPLOADING,

    /** Incoming: is loaded as soon as internet is there. */
    DOWNLOADING,

    /** Is stored encrypted on this phone and can be displayed. */
    READY,

    /** Incoming: no longer in any mailbox (older than the retention period). */
    MISSING,
}

/**
 * An attachment of a message. The content is encrypted with its own random key [key]
 * as blob [blobId] in the mailboxes; on the phone encrypted with the device key
 * ([AttachmentFiles]).
 *
 * Is stored with the message; change fields only in a backward-compatible way.
 */
@Serializable
data class Attachment(
    /** 16 bytes, Base64url. Also the file name on the phone. */
    val blobId: String,
    /** AES-256 key of the blob, Base64url. */
    val key: String,
    val kind: AttachmentKind,
    /** File name (PDF and other files); empty for images. */
    val name: String = "",
    /** Size of the content in bytes (unencrypted). */
    val size: Long,
    /** Images: aspect ratio for the placeholder, 0 if unknown. */
    val width: Int = 0,
    val height: Int = 0,
    val state: AttachmentState,
    /**
     * Hash of the content (first 16 bytes of SHA-256, Base64url). By it everyone recognises the same attachment, no
     * matter who delivers it; if it is missing in the mailboxes, it comes via `want`/`have` (ADR 0014). Missing in
     * older messages.
     */
    val hash: String? = null,
    /** Voice message: length in milliseconds, 0 if unknown (ADR 0030). */
    val durationMillis: Long = 0,
    /** Voice message: the text of the message is the automatic text preview of this recording (ADR 0030). */
    val transcribed: Boolean = false,
)

/** Selected, shrunk or recorded, not sent yet. */
class DraftAttachment(
    val kind: AttachmentKind,
    val bytes: ByteArray,
    val name: String = "",
    val width: Int = 0,
    val height: Int = 0,
    /** Voice message: length in milliseconds. */
    val durationMillis: Long = 0,
)

object AttachmentLimits {
    /**
     * At most this many attachments per message so that the envelope including the hashes of the attachments stays
     * below 4 KB, also for a direct message (ADR 0011, 0014).
     */
    const val MAX_PER_MESSAGE = 5

    /** More are received so that messages of older versions (up to 10) stay complete. */
    const val MAX_RECEIVED_PER_MESSAGE = 10

    /** Largest attachment (unencrypted). The mailbox accepts 10 MB plus encryption. */
    const val MAX_BYTES: Long = 10L * 1024 * 1024

    /** Largest file in „Meine Ablage“ (unencrypted). The relays accept 50 MiB plus encryption (ANDP policy). */
    const val MAX_PERSONAL_BYTES: Long = 50L * 1024 * 1024

    /** File names are truncated in the envelope so that five PDFs including hashes fit into one message. */
    const val MAX_NAME_CHARS = 40

    /** Longest edge of an image after shrinking. */
    const val IMAGE_MAX_EDGE = 1600

    /** JPEG quality after shrinking. */
    const val IMAGE_QUALITY = 80

    /**
     * Photos in the storages (ADR 0067 §7): larger so that small print stays readable (A4 at about 270 dpi), without
     * the place of capture. When sending into a chat it is shrunk to [IMAGE_MAX_EDGE].
     */
    const val STORE_IMAGE_MAX_EDGE = 3200

    /** JPEG quality of the photos in the storages. */
    const val STORE_IMAGE_QUALITY = 90

    /** Longest edge of the preview image of an online-only file (ADR 0080), in pixels; enough for list and gallery. */
    const val THUMBNAIL_EDGE = 480

    /** JPEG quality of this preview image. */
    const val THUMBNAIL_QUALITY = 80

    /**
     * Longest voice message without a subscription preview; after that the recording ends by itself and is kept
     * (ADR 0030). With the preview the limit of the own plan applies ([audioMaxMillis], ADR 0099).
     */
    const val AUDIO_MAX_MILLIS: Long = 5L * 60 * 1000

    /**
     * Never longer than this, whatever the preview says (ADR 0099): about 5.4 MB, well below what the relays accept
     * for one content. More needs a new app version.
     */
    const val AUDIO_HARD_MAX_MILLIS: Long = 30L * 60 * 1000

    /**
     * Longest recording for the own plan's [voiceMinutes] from the subscription preview; without a value
     * [AUDIO_MAX_MILLIS], never more than [AUDIO_HARD_MAX_MILLIS].
     */
    fun audioMaxMillis(voiceMinutes: Int?): Long =
        voiceMinutes?.takeIf { it > 0 }?.let { minOf(it * 60_000L, AUDIO_HARD_MAX_MILLIS) } ?: AUDIO_MAX_MILLIS

    /** Mailboxes keep attachments this long (like messages). */
    const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
}

/**
 * MIME type for opening and saving: from the kind, for [AttachmentKind.FILE] from the extension of the name. Unknown
 * extensions stay `application/octet-stream`; then the system chooses an app itself.
 */
fun mimeTypeOf(kind: AttachmentKind, name: String): String {
    if (kind != AttachmentKind.FILE) return kind.mimeType
    return MIME_BY_EXTENSION[name.substringAfterLast('.', "").lowercase()] ?: kind.mimeType
}

private val MIME_BY_EXTENSION = mapOf(
    "pdf" to "application/pdf",
    "txt" to "text/plain", "csv" to "text/csv", "rtf" to "application/rtf", "html" to "text/html", "htm" to "text/html",
    "doc" to "application/msword",
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "xls" to "application/vnd.ms-excel",
    "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "ppt" to "application/vnd.ms-powerpoint",
    "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "odt" to "application/vnd.oasis.opendocument.text", "ods" to "application/vnd.oasis.opendocument.spreadsheet",
    "odp" to "application/vnd.oasis.opendocument.presentation",
    "pages" to "application/vnd.apple.pages", "numbers" to "application/vnd.apple.numbers", "key" to "application/vnd.apple.keynote",
    "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif", "webp" to "image/webp",
    "heic" to "image/heic", "heif" to "image/heif", "tif" to "image/tiff", "tiff" to "image/tiff", "svg" to "image/svg+xml",
    "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "wav" to "audio/wav", "ogg" to "audio/ogg", "aac" to "audio/aac",
    "mp4" to "video/mp4", "mov" to "video/quicktime", "m4v" to "video/x-m4v", "webm" to "video/webm", "3gp" to "video/3gpp",
    "zip" to "application/zip", "gpx" to "application/gpx+xml", "kml" to "application/vnd.google-earth.kml+xml",
    "vcf" to "text/vcard", "ics" to "text/calendar", "json" to "application/json", "xml" to "application/xml",
    "epub" to "application/epub+zip", "apk" to "application/vnd.android.package-archive",
)

/** Truncates a file name to [AttachmentLimits.MAX_NAME_CHARS] characters, the extension is kept. */
fun shortenFileName(name: String): String {
    val clean = name.substringAfterLast('/').trim().ifEmpty { texts.chat.defaultFileName }
    if (clean.length <= AttachmentLimits.MAX_NAME_CHARS) return clean
    val ext = clean.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 }?.let { ".$it" }.orEmpty()
    return clean.take(AttachmentLimits.MAX_NAME_CHARS - ext.length - 1).trimEnd() + "…" + ext
}

/**
 * Attachments do not go over radio. The radio text announces them so that the recipient knows that something
 * follows as soon as internet is there. Other Meshtastic apps also show this readably.
 */
object RadioAnnouncement {
    const val PREFIX = "📎 "

    /** Voice messages are announced by a microphone, text follows without a colon (ADR 0030). */
    const val VOICE_PREFIX = "🎤 "

    /**
     * „📎 2 Bilder, 1 PDF“ or with text „📎 Bild: Hallo“. Without attachments the text itself.
     * Voice message: „🎤 Hallo“, without text „🎤 Sprachnachricht 0:42“ ([voiceMillis]).
     */
    fun radioText(text: String, kinds: List<AttachmentKind>, voiceMillis: Long = 0): String {
        if (kinds.isEmpty()) return text
        if (AttachmentKind.AUDIO in kinds) {
            return VOICE_PREFIX + text.ifEmpty { texts.status.announceVoice(voiceMillis.takeIf { it > 0 }?.let(::formatDuration)) }
        }
        val images = kinds.count { it == AttachmentKind.IMAGE }
        val pdfs = kinds.count { it == AttachmentKind.PDF }
        val files = kinds.count { it == AttachmentKind.FILE }
        // A single attachment without a number („📎 Bild“), otherwise with one („📎 2 Bilder, 1 PDF“).
        val parts = if (kinds.size == 1) {
            listOf(
                when {
                    images == 1 -> texts.status.announceImage
                    pdfs == 1 -> texts.status.announcePdf
                    else -> texts.status.announceFile
                },
            )
        } else {
            listOfNotNull(
                images.takeIf { it > 0 }?.let(texts.status::announceImages),
                pdfs.takeIf { it > 0 }?.let(texts.status::announcePdfs),
                files.takeIf { it > 0 }?.let(texts.status::announceFiles),
            )
        }
        val label = PREFIX + parts.joinToString(", ")
        return if (text.isEmpty()) label else "$label: $text"
    }

    /** Did a radio text announce attachments? */
    fun announces(radioText: String): Boolean = radioText.startsWith(PREFIX) || radioText.startsWith(VOICE_PREFIX)

    /** At the end of a truncated radio text (3 bytes, counts towards the limit). */
    const val ELLIPSIS = "…"

    /**
     * Like attachments, a long text goes completely only over the internet. Over radio goes the beginning that fits
     * into [maxBytes] (UTF-8): at the last word boundary with [ELLIPSIS]. If there is no word boundary in the
     * front half, at the last whole character; emojis and combined characters stay whole.
     */
    fun cut(radioText: String, maxBytes: Int): String {
        if (radioText.encodeToByteArray().size <= maxBytes) return radioText
        val room = maxBytes - ELLIPSIS.encodeToByteArray().size
        var end = 0
        var bytes = 0
        while (end < radioText.length) {
            val next = radioText.nextBoundary(end)
            val size = radioText.substring(end, next).encodeToByteArray().size
            if (bytes + size > room) break
            bytes += size
            end = next
        }
        // If a space follows, a whole word ends here; otherwise back to the last space.
        val space = if (end < radioText.length && radioText[end].isWhitespace()) end else radioText.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), end - 1)
        val stop = if (space >= end / 2) space else end
        return radioText.substring(0, stop).trimEnd() + ELLIPSIS
    }

    /** End of the character from [start]: surrogate pairs, combining characters and emoji sequences belong to it. */
    private fun String.nextBoundary(start: Int): Int {
        var i = start + codePointLength(start)
        // Flags are two regional letters in a row.
        if (isRegionalIndicator(start) && i < length && isRegionalIndicator(i)) i += 2
        while (i < length) {
            val c = this[i]
            val joined = this[i - 1] == ZWJ
            val modifier = c == '\uD83C' && i + 1 < length && this[i + 1].code in SKIN_TONES
            if (!joined && !modifier && c != ZWJ && c.code !in VARIATION_SELECTORS && !c.isCombining()) break
            i += codePointLength(i)
        }
        return i
    }

    private fun String.codePointLength(i: Int) = if (this[i].isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) 2 else 1

    private fun String.isRegionalIndicator(i: Int) = this[i] == '\uD83C' && i + 1 < length && this[i + 1].code in 0xDDE6..0xDDFF

    private fun Char.isCombining() = category == CharCategory.NON_SPACING_MARK || category == CharCategory.ENCLOSING_MARK || category == CharCategory.COMBINING_SPACING_MARK

    private const val ZWJ = '‍'
    private val VARIATION_SELECTORS = 0xFE00..0xFE0F
    private val SKIN_TONES = 0xDFFB..0xDFFF
}

/** Duration as "0:42" or "12:05". */
fun formatDuration(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) + 500) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
