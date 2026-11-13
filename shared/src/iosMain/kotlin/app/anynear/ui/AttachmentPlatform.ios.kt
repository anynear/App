package app.anynear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import app.anynear.attachments.toByteArray
import app.anynear.attachments.toNSData
import app.anynear.i18n.texts
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.QuickLook.QLPreviewController
import platform.UIKit.UIActivityViewController
import platform.QuickLook.QLPreviewControllerDataSourceProtocol
import platform.QuickLook.QLPreviewItemProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSInteger
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
    while (true) top = top.presentedViewController ?: break
    return top
}

@Composable
internal actual fun rememberAttachmentPicker(fileLimit: Long, onPicked: (images: Boolean, files: List<PickedFile>) -> Unit): AttachmentPicker {
    val callback = rememberUpdatedState(onPicked)
    val currentFileLimit = rememberUpdatedState(fileLimit)
    return remember { IosAttachmentPicker({ currentFileLimit.value }) { images, files -> callback.value(images, files) } }
}

private class IosAttachmentPicker(
    private val fileLimit: () -> Long,
    private val deliver: (Boolean, List<PickedFile>) -> Unit,
) : AttachmentPicker {
    // UIKit holds delegates only weakly.
    private var imageDelegate: ImageDelegate? = null
    private var documentDelegate: DocumentDelegate? = null
    private var cameraDelegate: CameraDelegate? = null

    override fun pickImages(max: Int) {
        val config = PHPickerConfiguration().apply {
            setSelectionLimit(max.toLong())
            setFilter(PHPickerFilter.imagesFilter)
        }
        val picker = PHPickerViewController(configuration = config)
        val delegate = ImageDelegate { deliver(true, it) }
        imageDelegate = delegate
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    override fun pickFiles() {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = true)
        picker.allowsMultipleSelection = true
        val delegate = DocumentDelegate(fileLimit()) { deliver(false, it) }
        documentDelegate = delegate
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    // The system camera; iOS itself asks for the camera the first time (NSCameraUsageDescription).
    override fun takePhoto() {
        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) return
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        val delegate = CameraDelegate { deliver(true, it) }
        cameraDelegate = delegate
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

private class CameraDelegate(private val done: (List<PickedFile>) -> Unit) :
    NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return
        // JPEG in high quality; shrinking is done jointly afterwards.
        val data = UIImageJPEGRepresentation(image, 0.95)
        done(listOf(PickedFile(data?.toByteArray(), "")))
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

// `hasImages` only looks at the kind; only `images` reads and makes iOS ask „Einfügen erlauben?“.
internal actual fun clipboardHasImage(): Boolean = UIPasteboard.generalPasteboard.hasImages

internal actual suspend fun clipboardImages(): List<PickedFile> =
    UIPasteboard.generalPasteboard.images.orEmpty().filterIsInstance<UIImage>().map { image ->
        // JPEG in high quality; shrinking is done jointly afterwards.
        val data = UIImageJPEGRepresentation(image, 0.95)
        val tooLarge = data != null && data.length.toLong() > MAX_RAW_IMAGE_BYTES
        PickedFile(data?.takeIf { !tooLarge }?.toByteArray(), "", tooLarge)
    }

internal actual fun Modifier.receiveImages(onImages: (List<PickedFile>) -> Unit): Modifier = this

private class ImageDelegate(private val done: (List<PickedFile>) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) return
        val files = arrayOfNulls<PickedFile>(results.size)
        var remaining = results.size
        results.forEachIndexed { i, result ->
            // HEIC, JPEG, PNG …: UIImage reads everything, shrinking is done jointly afterwards.
            result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
                val tooLarge = data != null && data.length.toLong() > MAX_RAW_IMAGE_BYTES
                val bytes = data?.takeIf { !tooLarge }?.toByteArray()
                dispatch_async(dispatch_get_main_queue()) {
                    files[i] = PickedFile(bytes, "", tooLarge)
                    if (--remaining == 0) done(files.filterNotNull())
                }
            }
        }
    }
}

private class DocumentDelegate(private val limit: Long, private val done: (List<PickedFile>) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val files = didPickDocumentsAtURLs.filterIsInstance<NSURL>().map { url ->
            val name = url.lastPathComponent ?: texts.chat.defaultFileName
            val data = NSData.dataWithContentsOfURL(url)
            when {
                data == null -> PickedFile(null, name)
                data.length.toLong() > limit -> PickedFile(null, name, tooLarge = true)
                else -> PickedFile(data.toByteArray(), name)
            }
        }
        if (files.isNotEmpty()) done(files)
    }
}

internal actual fun ImageBitmap.toJpeg(quality: Int): ByteArray? = runCatching {
    org.jetbrains.skia.Image.makeFromBitmap(asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.JPEG, quality)?.bytes
}.getOrNull()

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun renderPdfFirstPage(bytes: ByteArray, widthPx: Int): ImageBitmap? = withContext(Dispatchers.Default) {
    val page = PDFDocument(data = bytes.toNSData()).pageAtIndex(0u) ?: return@withContext null
    val image = page.thumbnailOfSize(CGSizeMake(widthPx.toDouble(), widthPx * 3.0), forBox = kPDFDisplayBoxMediaBox)
    val png = UIImagePNGRepresentation(image) ?: return@withContext null
    runCatching { png.toByteArray().decodeToImageBitmap() }.getOrNull()
}

/** Holds the data source as long as the preview is open (QuickLook holds it only weakly). */
private var previewSource: PreviewSource? = null

@OptIn(ExperimentalForeignApi::class)
private class PreviewSource(private val url: NSURL) : NSObject(), QLPreviewControllerDataSourceProtocol {
    override fun numberOfPreviewItemsInPreviewController(controller: QLPreviewController): NSInteger = 1

    override fun previewController(controller: QLPreviewController, previewItemAtIndex: NSInteger): QLPreviewItemProtocol =
        @Suppress("CAST_NEVER_SUCCEEDS")
        (url as QLPreviewItemProtocol)
}

private class IosPdfPages(private val document: PDFDocument) : PdfPages {
    override val count: Int = document.pageCount.toInt()

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun render(index: Int, widthPx: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        val page = document.pageAtIndex(index.toULong()) ?: return@withContext null
        val image = page.thumbnailOfSize(CGSizeMake(widthPx.toDouble(), widthPx * 3.0), forBox = kPDFDisplayBoxMediaBox)
        val png = UIImagePNGRepresentation(image) ?: return@withContext null
        runCatching { png.toByteArray().decodeToImageBitmap() }.getOrNull()
    }

    override fun close() = Unit
}

internal actual suspend fun openPdf(bytes: ByteArray): PdfPages? = withContext(Dispatchers.Default) {
    val document = PDFDocument(data = bytes.toNSData())
    if (document.pageCount.toInt() == 0) null else IosPdfPages(document)
}

/** Writes the file briefly into the own temp folder; QuickLook recognises the type by the extension. */
@OptIn(ExperimentalForeignApi::class)
private fun tempFile(folder: String, bytes: ByteArray, name: String, mimeType: String): NSURL? {
    val dir = NSTemporaryDirectory() + folder + "/"
    val fm = NSFileManager.defaultManager
    // Only the file shown last stays.
    fm.removeItemAtPath(dir, error = null)
    fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    val safeName = name.replace(Regex("[/:]"), "_").ifBlank { texts.chat.defaultFileName }
        .let { if (mimeType == "application/pdf" && !it.endsWith(".pdf", ignoreCase = true)) "$it.pdf" else it }
    val url = NSURL.fileURLWithPath(dir + safeName)
    return if (bytes.toNSData().writeToURL(url, atomically = true)) url else null
}

/** QuickLook shows Office, text and much more in the app without handing out the file. */
internal actual fun previewInApp(bytes: ByteArray, name: String, mimeType: String): Boolean {
    val url = tempFile("open", bytes, name, mimeType) ?: return false
    val top = topViewController() ?: return false
    val source = PreviewSource(url)
    previewSource = source
    val preview = QLPreviewController()
    preview.dataSource = source
    top.presentViewController(preview, animated = true, completion = null)
    return true
}

/** Share menu with the file („Öffnen in …“), only on explicit request. */
internal actual fun openWithOtherApp(bytes: ByteArray, name: String, mimeType: String): Boolean {
    val url = tempFile("share", bytes, name, mimeType) ?: return false
    val top = topViewController() ?: return false
    top.presentViewController(UIActivityViewController(listOf(url), null), animated = true, completion = null)
    return true
}

/** Share menu with the file, like [openWithOtherApp]: iOS has only the one menu for that. */
internal actual fun shareFile(bytes: ByteArray, name: String, mimeType: String): Boolean =
    openWithOtherApp(bytes, name, mimeType)

/**
 * iOS: share menu with the file, containing „Bild sichern“ (Photos) or „In Dateien sichern“. That way
 * anynear needs no access to the photo library.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun saveToDevice(bytes: ByteArray, name: String, mimeType: String): SaveResult {
    val dir = NSTemporaryDirectory() + "save/"
    val fm = NSFileManager.defaultManager
    fm.removeItemAtPath(dir, error = null)
    fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    val url = NSURL.fileURLWithPath(dir + name.replace(Regex("[/:]"), "_"))
    if (!bytes.toNSData().writeToURL(url, atomically = true)) return SaveResult.Failed
    val top = topViewController() ?: return SaveResult.Failed
    top.presentViewController(UIActivityViewController(listOf(url), null), animated = true, completion = null)
    return SaveResult.Handed
}
