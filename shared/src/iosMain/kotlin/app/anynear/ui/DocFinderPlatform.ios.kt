package app.anynear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.anynear.app.DeviceDocSource
import app.anynear.app.DeviceItem
import app.anynear.attachments.AttachmentLimits
import app.anynear.attachments.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSSortDescriptor
import platform.Foundation.NSURL
import platform.Foundation.NSURLContentModificationDateKey
import platform.Foundation.NSURLFileSizeKey
import platform.Foundation.NSURLIsRegularFileKey
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.timeIntervalSince1970
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAsset
import platform.Photos.PHAssetMediaTypeImage
import platform.Photos.PHAssetPlaybackStyleImageAnimated
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHFetchOptions
import platform.Photos.PHImageContentModeAspectFit
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/*
 * iOS (ADR 0071 items 2 and 3): photos via PhotoKit, also with „Beschränkter Zugriff“; iCloud photos that are not on
 * the iPhone are checked by the search only with the image already there and loaded only when accepting. Folders via
 * the document picker (Files app with iCloud Drive, Dropbox, Synology Drive …), only for this run. Single PDFs and
 * images via the same document picker with multiple selection, like „Dateien auswählen“ on Android.
 */

@Composable
internal actual fun rememberDeviceDocAccess(onSource: (source: DeviceDocSource?, denied: Boolean) -> Unit): DeviceDocAccess {
    val callback = rememberUpdatedState(onSource)
    return remember { IosDeviceDocAccess { source, denied -> callback.value(source, denied) } }
}

private class IosDeviceDocAccess(private val deliver: (DeviceDocSource?, Boolean) -> Unit) : DeviceDocAccess {
    // UIKit holds delegates only weakly.
    private var documentsDelegate: DocumentsDelegate? = null
    private var pickerDelegate: PickerDelegate? = null

    override fun requestPhotos() {
        PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { status ->
            val ok = status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited
            dispatch_async(dispatch_get_main_queue()) { if (ok) deliver(PhotoLibrarySource(), false) else deliver(null, true) }
        }
    }

    override fun pickPhotos() {
        val config = PHPickerConfiguration().apply {
            setSelectionLimit(0)
            setFilter(PHPickerFilter.imagesFilter)
        }
        val picker = PHPickerViewController(configuration = config)
        val delegate = PickerDelegate { results -> if (results.isNotEmpty()) deliver(PickedPhotosSource(results), false) }
        pickerDelegate = delegate
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    override fun pickFolder() {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder))
        val delegate = DocumentsDelegate { urls -> urls.firstOrNull()?.let { deliver(FolderSource(it), false) } }
        documentsDelegate = delegate
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    // „Zugriff auf alle Dateien“ does not exist on iOS; one picks a folder or single files.
    override val canSearchDevice = false

    override fun hasDeviceAccess() = false

    override fun searchDevice() = Unit

    override fun pickFiles() {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypePDF, UTTypeImage))
        picker.allowsMultipleSelection = true
        val delegate = DocumentsDelegate { urls -> if (urls.isNotEmpty()) deliver(PickedFilesSource(urls), false) }
        documentsDelegate = delegate
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

private class PickerDelegate(private val done: (List<PHPickerResult>) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        done(didFinishPicking.filterIsInstance<PHPickerResult>())
    }
}

private class DocumentsDelegate(private val done: (List<NSURL>) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        done(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
    }
}

/**
 * After a restart (ADR 0071 item 10): only the photo library can be reopened, as long as access exists.
 * Chosen photos, files and folders are valid on iOS for one run only.
 */
internal fun openDocSource(ref: String): DeviceDocSource? {
    if (ref != PHOTOS_REF) return null
    val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
    return if (status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited) PhotoLibrarySource() else null
}

private const val PHOTOS_REF = "photos"

/** Photo library images (with „Beschränkter Zugriff“ only the chosen ones), newest first, without moving images. */
private class PhotoLibrarySource : DeviceDocSource {
    override val folder = false
    override val ref = PHOTOS_REF
    override val watchable = true
    private val assets = mutableMapOf<String, PHAsset>()

    /** Known from [items], otherwise (after a restart) via the photo's identifier. */
    private fun asset(key: String): PHAsset? = assets[key]
        ?: (PHAsset.fetchAssetsWithLocalIdentifiers(listOf(key.removePrefix("p:")), null).firstObject as? PHAsset)?.also { assets[key] = it }

    override suspend fun items(): List<DeviceItem> = withContext(Dispatchers.Default) {
        val options = PHFetchOptions().apply { sortDescriptors = listOf(NSSortDescriptor(key = "creationDate", ascending = false)) }
        val result = PHAsset.fetchAssetsWithMediaType(PHAssetMediaTypeImage, options)
        val out = mutableListOf<DeviceItem>()
        for (i in 0 until result.count.toInt()) {
            val asset = result.objectAtIndex(i.toULong()) as? PHAsset ?: continue
            if (asset.playbackStyle == PHAssetPlaybackStyleImageAnimated) continue
            val key = "p:${asset.localIdentifier}"
            assets[key] = asset
            // Without a name: accepted photos are named as with „Foto hinzufügen“.
            out += DeviceItem(key, "", pdf = false)
        }
        out
    }

    override suspend fun preview(item: DeviceItem): ByteArray? = withContext(Dispatchers.Default) {
        asset(item.key)?.let { image(it, DeviceDocSource.PREVIEW_EDGE.toDouble()) }
    }

    override suspend fun bytes(item: DeviceItem, keep: Boolean): ByteArray? = withContext(Dispatchers.Default) {
        val asset = asset(item.key) ?: return@withContext null
        if (keep) original(asset) else image(asset, AttachmentLimits.STORE_IMAGE_MAX_EDGE.toDouble())
    }

    /** Image in at most [edge] pixels, only what is already on the iPhone. */
    @OptIn(ExperimentalForeignApi::class)
    private fun image(asset: PHAsset, edge: Double): ByteArray? {
        val options = PHImageRequestOptions().apply {
            synchronous = true
            deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
            networkAccessAllowed = false
        }
        var out: ByteArray? = null
        PHImageManager.defaultManager().requestImageForAsset(asset, CGSizeMake(edge, edge), PHImageContentModeAspectFit, options) { image, _ ->
            if (image != null) out = UIImageJPEGRepresentation(image, 0.85)?.toByteArray()
        }
        return out
    }

    /** The original for accepting, also from iCloud. */
    private fun original(asset: PHAsset): ByteArray? {
        val options = PHImageRequestOptions().apply {
            synchronous = true
            deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
            networkAccessAllowed = true
        }
        var out: ByteArray? = null
        PHImageManager.defaultManager().requestImageDataAndOrientationForAsset(asset, options) { data, _, _, _ ->
            if (data != null && data.length.toLong() <= MAX_RAW_IMAGE_BYTES) out = data.toByteArray()
        }
        return out
    }
}

/** Photos chosen in the picker window, without permission. */
private class PickedPhotosSource(private val results: List<PHPickerResult>) : DeviceDocSource {
    override val folder = false

    override suspend fun items(): List<DeviceItem> = results.indices.map { DeviceItem("u:$it", "", pdf = false) }

    private suspend fun data(item: DeviceItem): NSData? {
        val result = results.getOrNull(item.key.removePrefix("u:").toInt()) ?: return null
        return suspendCancellableCoroutine { cont ->
            result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ -> cont.resume(data) }
        }
    }

    override suspend fun preview(item: DeviceItem): ByteArray? = data(item)?.let { scaledJpeg(it, DeviceDocSource.PREVIEW_EDGE.toDouble()) }

    override suspend fun bytes(item: DeviceItem, keep: Boolean): ByteArray? =
        data(item)?.takeIf { it.length.toLong() <= MAX_RAW_IMAGE_BYTES }?.toByteArray()
}

/** PDFs and images chosen in the document picker. Access only until [close]. */
private class PickedFilesSource(private val picked: List<NSURL>) : DeviceDocSource {
    override val folder = false
    private val access = picked.filter { it.startAccessingSecurityScopedResource() }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun items(): List<DeviceItem> = withContext(Dispatchers.Default) {
        picked.mapIndexedNotNull { i, url ->
            val name = url.lastPathComponent.orEmpty()
            val ending = name.substringAfterLast('.', "").lowercase()
            if (ending != "pdf" && ending !in FOLDER_IMAGES) return@mapIndexedNotNull null
            val size = (url.resourceValuesForKeys(listOf(NSURLFileSizeKey), null)?.get(NSURLFileSizeKey) as? NSNumber)?.longLongValue ?: -1L
            if (size > AttachmentLimits.MAX_PERSONAL_BYTES) return@mapIndexedNotNull null
            DeviceItem("d:$i", name, pdf = ending == "pdf", size = size)
        }
    }

    /** Reads via the file coordinator so that apps like Dropbox download the file first. */
    @OptIn(ExperimentalForeignApi::class)
    private fun read(item: DeviceItem): NSData? {
        val url = picked.getOrNull(item.key.removePrefix("d:").toInt()) ?: return null
        var data: NSData? = null
        NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(url, 0u, null) { readable ->
            data = readable?.let { NSData.dataWithContentsOfURL(it) }
        }
        return data
    }

    override suspend fun preview(item: DeviceItem): ByteArray? =
        if (item.pdf) null else withContext(Dispatchers.Default) { read(item)?.let { scaledJpeg(it, DeviceDocSource.PREVIEW_EDGE.toDouble()) } }

    override suspend fun bytes(item: DeviceItem, keep: Boolean): ByteArray? = withContext(Dispatchers.Default) { read(item)?.toByteArray() }

    override fun close() {
        access.forEach { it.stopAccessingSecurityScopedResource() }
    }
}

/** A self-chosen folder with subfolders: images and PDFs, newest first. Access only until [close]. */
private class FolderSource(private val root: NSURL) : DeviceDocSource {
    override val folder = true
    private val access = root.startAccessingSecurityScopedResource()
    private val urls = mutableMapOf<String, NSURL>()

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun items(): List<DeviceItem> = withContext(Dispatchers.Default) {
        val keys = listOf(NSURLContentModificationDateKey, NSURLFileSizeKey, NSURLIsRegularFileKey)
        val walker = NSFileManager.defaultManager.enumeratorAtURL(root, keys, 0u, null) ?: return@withContext emptyList()
        val found = mutableListOf<Pair<Double, DeviceItem>>()
        while (found.size < MAX_FOLDER_ITEMS) {
            val url = walker.nextObject() as? NSURL ?: break
            val name = url.lastPathComponent ?: continue
            val ending = name.substringAfterLast('.', "").lowercase()
            if (ending != "pdf" && ending !in FOLDER_IMAGES) continue
            val values = url.resourceValuesForKeys(keys, null).orEmpty()
            if ((values[NSURLIsRegularFileKey] as? NSNumber)?.boolValue == false) continue
            val size = (values[NSURLFileSizeKey] as? NSNumber)?.longLongValue ?: -1L
            if (size > AttachmentLimits.MAX_PERSONAL_BYTES) continue
            val modified = (values[NSURLContentModificationDateKey] as? NSDate)?.timeIntervalSince1970 ?: 0.0
            val key = "f:${url.path}:$size:${modified.toLong()}"
            urls[key] = url
            found += modified to DeviceItem(key, name, pdf = ending == "pdf", size = size)
        }
        found.sortedByDescending { it.first }.map { it.second }
    }

    /** Reads via the file coordinator so that apps like Dropbox download the file first. */
    @OptIn(ExperimentalForeignApi::class)
    private fun read(item: DeviceItem): NSData? {
        val url = urls[item.key] ?: return null
        var data: NSData? = null
        NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(url, 0u, null) { readable ->
            data = readable?.let { NSData.dataWithContentsOfURL(it) }
        }
        return data
    }

    override suspend fun preview(item: DeviceItem): ByteArray? =
        if (item.pdf) null else withContext(Dispatchers.Default) { read(item)?.let { scaledJpeg(it, DeviceDocSource.PREVIEW_EDGE.toDouble()) } }

    override suspend fun bytes(item: DeviceItem, keep: Boolean): ByteArray? = withContext(Dispatchers.Default) { read(item)?.toByteArray() }

    override fun close() {
        if (access) root.stopAccessingSecurityScopedResource()
    }
}

/** Shrinks an image to at most [edge] pixels as JPEG. */
@OptIn(ExperimentalForeignApi::class)
private fun scaledJpeg(data: NSData, edge: Double): ByteArray? {
    val image = UIImage.imageWithData(data) ?: return null
    val (w, h) = image.size.useContents { width to height }
    if (w <= 0.0 || h <= 0.0) return null
    val scale = min(1.0, edge / max(w, h))
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(w * scale, h * scale))
    val small = renderer.imageWithActions { image.drawInRect(CGRectMake(0.0, 0.0, w * scale, h * scale)) }
    return UIImageJPEGRepresentation(small, 0.85)?.toByteArray()
}

private val FOLDER_IMAGES = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")

/** Upper limit per folder so that a huge cloud folder does not blow up the list. */
private const val MAX_FOLDER_ITEMS = 20_000
