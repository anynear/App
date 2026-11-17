package app.anynear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import app.anynear.attachments.AttachmentLimits

/**
 * File selected by the user. [bytes] is `null` if it was too large to read in
 * ([tooLarge]) or could not be read.
 */
internal class PickedFile(val bytes: ByteArray?, val name: String, val tooLarge: Boolean = false)

/**
 * Opens the system's picker; the result arrives via the callback of [rememberAttachmentPicker]. Files above
 * its `fileLimit` arrive as [PickedFile.tooLarge].
 */
internal interface AttachmentPicker {
    /** Photos from the gallery, at most [max]. Needs no permission. */
    fun pickImages(max: Int)

    /** Any files from Files, Downloads, cloud storage etc. (ADR 0041). */
    fun pickFiles()

    /**
     * Take a photo directly with the camera (as in common messengers); arrives as a picture via the callback. Asks for
     * the camera the first time. Without a camera nothing happens.
     */
    fun takePhoto()
}

/** Maximum size when reading in a photo before downscaling. */
internal const val MAX_RAW_IMAGE_BYTES = 60L * 1024 * 1024

@Composable
internal expect fun rememberAttachmentPicker(
    fileLimit: Long = AttachmentLimits.MAX_BYTES,
    onPicked: (images: Boolean, files: List<PickedFile>) -> Unit,
): AttachmentPicker

/**
 * Is there a picture in the clipboard? Only looks at the kind of content and does not read it, so that the system
 * reports nothing; it is only read with [clipboardImages] when someone taps „Bild einfügen“.
 */
internal expect fun clipboardHasImage(): Boolean

/** Pictures from the clipboard, only on explicit request; empty if there are none in it. */
internal expect suspend fun clipboardImages(): List<PickedFile>

/**
 * Accept pictures that are pasted into the input field (paste menu, keyboard clipboard) as an attachment instead of
 * as text. Only Android knows this; on iOS it stays with „Bild einfügen“ in the attachment menu.
 */
internal expect fun Modifier.receiveImages(onImages: (List<PickedFile>) -> Unit): Modifier

/** First page of a PDF as a picture, [widthPx] wide; `null` if that is not possible. */
internal expect suspend fun renderPdfFirstPage(bytes: ByteArray, widthPx: Int): ImageBitmap?

/** Picture as JPEG with [quality] (0–100), e.g. for the preview picture of a file that is online only (ADR 0080); `null` if that is not possible. */
internal expect fun ImageBitmap.toJpeg(quality: Int): ByteArray?

/** An opened PDF for display in the app ([FileViewer]); [close] after use. */
internal interface PdfPages {
    val count: Int

    /** Page [index] (from 0) as a picture, [widthPx] wide; `null` if that is not possible. */
    suspend fun render(index: Int, widthPx: Int): ImageBitmap?

    fun close()
}

/** Open a PDF for viewing in the app; `null` if it cannot be read or has no pages. */
internal expect suspend fun openPdf(bytes: ByteArray): PdfPages?

/**
 * Show other files (Office, text …) in the app, as far as the system can without a third-party app (iOS: QuickLook).
 * `false` if not; then [FileViewer] shows name and size with the buttons for another app and saving.
 */
internal expect fun previewInApp(bytes: ByteArray, name: String, mimeType: String): Boolean

/**
 * Hand a file to another app, **only on explicit request** („Mit anderer App öffnen“): Android the matching
 * app, otherwise the share menu; iOS the share menu. Viewing otherwise always happens in the app ([FileViewer]).
 * `false` if nothing works at all.
 */
internal expect fun openWithOtherApp(bytes: ByteArray, name: String, mimeType: String): Boolean

/**
 * The system's share menu with the file, **only on explicit request** („Teilen“ button): send to a person or
 * app, save to Files. Never opens a third-party view directly. `false` if nothing works at all.
 */
internal expect fun shareFile(bytes: ByteArray, name: String, mimeType: String): Boolean

/** A file for [shareContent]: content, name without a path, media type. */
internal class HandedFile(val bytes: ByteArray, val name: String, val mimeType: String)

/**
 * The system's share menu with a whole message, **only on explicit request** („Teilen…“ under a message): [text]
 * (may be empty) together with [files] (may be empty). Android: sharesheet, iOS: share menu. `false` if nothing works.
 */
internal expect fun shareContent(text: String, files: List<HandedFile>): Boolean

/** Result of [saveToDevice]. */
internal sealed interface SaveResult {
    /** Saved; [where] says in everyday language where it is (e.g. „Bilder › anynear“). */
    data class Saved(val where: String) : SaveResult

    /** The system showed a picker of its own (iOS: share menu with „Bild sichern“), nothing more to say. */
    data object Handed : SaveResult

    data object Failed : SaveResult
}

/**
 * Store an attachment permanently on the phone, unencrypted and visible to other apps: photos with the
 * pictures, PDFs with the downloads. [name] without a path. Only on explicit request („Speichern“ button), never as
 * a side effect of viewing.
 */
internal expect suspend fun saveToDevice(bytes: ByteArray, name: String, mimeType: String): SaveResult
