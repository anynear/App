package app.anynear.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import app.anynear.AndroidPlatform
import app.anynear.app.DeviceDocSource
import app.anynear.app.DeviceItem
import app.anynear.attachments.AttachmentLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/*
 * Android (ADR 0071 items 2, 3 and 10): photos via MediaStore with READ_MEDIA_IMAGES (from 14 on also “Fotos auswählen”),
 * up to Android 12 READ_EXTERNAL_STORAGE. Folders and chosen files via the system picker
 * (Storage Access Framework), without a permission. The app keeps the access as long as a run or open suggestions
 * need it, also across a restart (persisted URI grant); after that it gives it back, for the photos from 13 on. The
 * folder “Download” is not released by the folder picker from Android 11. Instead the app searches the whole phone
 * on request with “Zugriff auf alle Dateien”, which one grants in the settings after a short notice; without that
 * access the file picker with multiple selection remains, which opens right in “Download”.
 */

/** Permissions for the photos per Android version. */
private fun photoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasPhotoAccess(context: Context): Boolean = photoPermissions().any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

@Composable
internal actual fun rememberDeviceDocAccess(onSource: (source: DeviceDocSource?, denied: Boolean) -> Unit): DeviceDocAccess {
    val callback = rememberUpdatedState(onSource)
    val context = AndroidPlatform.requireContext()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it } || hasPhotoAccess(context)) callback.value(PhotoLibrarySource(context), false)
        else callback.value(null, true)
    }
    // Without a permission: the picker window with as many photos as the system allows.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) callback.value(PickedPhotosSource(context, uris), false)
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) callback.value(FolderSource(context, uri), false)
    }
    val files = rememberLauncherForActivityResult(OpenFilesInDownload()) { uris ->
        if (uris.isNotEmpty()) callback.value(PickedFilesSource(context, uris), false)
    }
    // Back from the setting “Zugriff auf alle Dateien”: with access on to the choice “ganzes Handy oder Ordner”.
    val granted = remember { arrayOfNulls<() -> Unit>(1) }
    val allFiles = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val then = granted[0]
        granted[0] = null
        if (hasAllFilesAccess()) then?.invoke() ?: callback.value(DeviceFilesSource(context), false)
    }
    return remember(permissions, picker, folder, files, allFiles) {
        object : DeviceDocAccess {
            override fun requestPhotos() {
                // From 14 on the system asks again even if “Fotos auswählen” already applies: that way one can change the selection.
                if (Build.VERSION.SDK_INT < 34 && hasPhotoAccess(context)) callback.value(PhotoLibrarySource(context), false)
                else permissions.launch(photoPermissions())
            }

            override fun pickPhotos() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

            override fun pickFolder() = folder.launch(null)

            override fun pickFiles() = files.launch(PICKED_TYPES)

            // Not in the Google Play build: it does not declare “Zugriff auf alle Dateien”, folders come from the system dialog.
            override val canSearchDevice = Build.VERSION.SDK_INT >= 30 && declaresAllFilesAccess(context)

            override fun hasDeviceAccess() = hasAllFilesAccess()

            override fun folders(path: String?): List<DeviceFolder> {
                val dirs = if (path == null) storageRoots(context) else File(path).listFiles()?.filter { it.isDirectory }.orEmpty()
                return dirs.filter { !it.name.startsWith(".") && !(path != null && it.parentFile in storageRoots(context) && it.name == "Android") }
                    .sortedBy { it.name.lowercase() }
                    .map { DeviceFolder(if (path == null) rootName(context, it) else it.name, it.path) }
            }

            override fun searchFolder(path: String) {
                if (hasAllFilesAccess()) callback.value(DeviceFilesSource(context, File(path)), false)
            }

            override fun requestDeviceAccess(onGranted: () -> Unit) {
                if (hasAllFilesAccess()) return onGranted()
                granted[0] = onGranted
                openAccessSetting()
            }

            override fun searchDevice() {
                if (hasAllFilesAccess()) return callback.value(DeviceFilesSource(context), false)
                openAccessSetting()
            }

            private fun openAccessSetting() {
                if (Build.VERSION.SDK_INT < 30) return
                val app = Uri.fromParts("package", context.packageName, null)
                val page = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, app)
                runCatching { allFiles.launch(page) }
                    .onFailure { runCatching { allFiles.launch(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
            }
        }
    }
}

/** Images on this phone, newest first, without GIF. With “Fotos auswählen” MediaStore returns only the chosen ones. */
private class PhotoLibrarySource(private val context: Context) : DeviceDocSource {
    override val folder = false
    override val ref = PHOTOS_REF
    override val watchable = true

    override suspend fun items(): List<DeviceItem> = withContext(Dispatchers.IO) {
        val out = mutableListOf<DeviceItem>()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.MIME_TYPE} != ?",
            arrayOf("image/gif"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += DeviceItem("p:$id", c.getString(1) ?: "$id.jpg", pdf = false, size = c.getLong(2))
            }
        }
        out
    }

    private fun uri(item: DeviceItem): Uri =
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, item.key.removePrefix("p:").toLong())

    override suspend fun preview(item: DeviceItem): ByteArray? = withContext(Dispatchers.IO) { previewOf(context, uri(item)) }

    override suspend fun bytes(item: DeviceItem, keep: Boolean): ByteArray? = withContext(Dispatchers.IO) {
        readLimited(context, uri(item), MAX_RAW_IMAGE_BYTES)
    }

    /** Give the access back (item 2): ends as soon as the app is next terminated. */
    override fun close() {
        if (Build.VERSION.SDK_INT >= 33) runCatching { context.revokeSelfPermissionsOnKill(photoPermissions().toList()) }
    }
}

/** Photos chosen in the picker window; the access only lasts while the app is running. */
private class PickedPhotosSource(private val context: Context, private val uris: List<Uri>) : DeviceDocSource {
    override val folder = false
    override val ref = refOf(PICKED_PHOTOS_REF, uris)

    init {
        keepAccess(context, uris)
    }

    override fun close() = giveBack(context, uris)

    override suspend fun items(): List<DeviceItem> = uris.mapIndexed { i, uri -> DeviceItem("u:$uri", "IMG_${i + 1}.jpg", pdf = false) }

    private fun uri(item: DeviceItem) = Uri.parse(item.key.removePrefix("u:"))

    override suspend fun preview(item: DeviceItem): ByteArray? = withContext(Dispatchers.IO) { previewOf(context, uri(item)) }

    override suspend fun bytes(item: DeviceItem, keep: Boolean): ByteArray? = withContext(Dispatchers.IO) {
        readLimited(context, uri(item), MAX_RAW_IMAGE_BYTES)
    }
}

/** File picker with multiple selection that starts in the folder “Download” (the system may ignore that). */
private class OpenFilesInDownload : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent = super.createIntent(context, input).apply {
        if (Build.VERSION.SDK_INT >= 26) putExtra(DocumentsContract.EXTRA_INITIAL_URI, DOWNLOAD_FOLDER)
    }
}

/** PDFs and images chosen in the picker window; the access only lasts while the app is running. */
private class PickedFilesSource(private val context: Context, private val uris: List<Uri>) : DeviceDocSource {
    override val folder = false
    override val ref = refOf(PICKED_FILES_REF, uris)

    init {
        keepAccess(context, uris)
    }

    override fun close() = giveBack(context, uris)

    override suspend fun items(): List<DeviceItem> = withContext(Dispatchers.IO) {
        uris.mapIndexedNotNull { i, uri ->
            val mime = context.contentResolver.getType(uri).orEmpty()
            val pdf = mime == "application/pdf"
            if (!pdf && mime !in FOLDER_IMAGES) return@mapIndexedNotNull null
            var name = ""
            var size = -1L
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getString(0).orEmpty()
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
            }
            if (size > AttachmentLimits.MAX_PERSONAL_BYTES) return@mapIndexedNotNull null
            DeviceItem("d:$uri", name.ifEmpty { if (pdf) "Datei_${i + 1}.pdf" else "IMG_${i + 1}.jpg" }, pdf = pdf, size = size)
        }
    }

    private fun uri(item: DeviceItem) = Uri.parse(item.key.removePrefix("d:"))

    override suspend fun preview(item: DeviceItem): ByteArray? =
        if (item.pdf) null else withContext(Dispatchers.IO) { previewOf(context, uri(item)) }

    override suspend fun bytes(item: DeviceItem, keep: Boolean): ByteArray? = withContext(Dispatchers.IO) {
        readLimited(context, uri(item), if (item.pdf) AttachmentLimits.MAX_PERSONAL_BYTES else MAX_RAW_IMAGE_BYTES)
    }
}

private fun hasAllFilesAccess(): Boolean = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()

/** Only the APK outside Google Play asks for “Zugriff auf alle Dateien” (androidApp/src/direct/AndroidManifest.xml). */
private fun declaresAllFilesAccess(context: Context): Boolean = runCatching {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        .requestedPermissions.orEmpty().contains(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE)
}.getOrDefault(false)

/**
 * The whole phone with “Zugriff auf alle Dateien”: all mounted storage volumes (internal storage, SD card) with
 * subfolders, without `Android/` (app data) and without hidden folders; images and PDFs, newest first.
 */
private class DeviceFilesSource(private val context: Context, private val only: File? = null) : DeviceDocSource {
    override val folder = only != null
    override val ref = only?.let { DEVICE_FOLDER_REF + it.path } ?: DEVICE_REF
    override val watchable = true

    /** Is on the phone, nothing is downloaded. */
    override val cloud = false

    override suspend fun items(): List<DeviceItem> = withContext(Dispatchers.IO) {
        (only?.let(::listOf) ?: storageRoots(context)).flatMap { root ->
            root.walkTopDown()
                .onEnter { it == root || !(it.name.startsWith(".") || (it.parentFile == root && it.name == "Android")) }
                .filter { it.isFile && !it.name.startsWith(".") }
                .mapNotNull { file ->
                    val ending = file.extension.lowercase()
                    val pdf = ending == "pdf"
                    if (!pdf && ending !in DEVICE_IMAGES) return@mapNotNull null
                    val size = file.length()
                    if (size > AttachmentLimits.MAX_PERSONAL_BYTES) return@mapNotNull null
                    val modified = file.lastModified()
                    modified to DeviceItem("a:${file.path}:$size:$modified", file.name, pdf = pdf, size = size)
                }
                .toList()
        }
            .sortedByDescending { it.first }
            .take(MAX_DEVICE_ITEMS)
            .map { it.second }
    }

    private fun uri(item: DeviceItem): Uri = Uri.fromFile(File(item.key.removePrefix("a:").substringBeforeLast(':').substringBeforeLast(':')))

    override suspend fun preview(item: DeviceItem): ByteArray? =
        if (item.pdf) null else withContext(Dispatchers.IO) { previewOf(context, uri(item)) }

    override suspend fun bytes(item: DeviceItem, keep: Boolean): ByteArray? = withContext(Dispatchers.IO) {
        readLimited(context, uri(item), if (item.pdf) AttachmentLimits.MAX_PERSONAL_BYTES else MAX_RAW_IMAGE_BYTES)
    }
}

/** Name of a storage volume for humans: “Interner Speicher” or the label of the card. */
private fun rootName(context: Context, root: File): String =
    runCatching {
        (context.getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager).getStorageVolume(root)?.getDescription(context)
    }.getOrNull() ?: root.name

/** Roots of all mounted storage volumes; at least the internal one. */
private fun storageRoots(context: Context): List<File> {
    val volumes = if (Build.VERSION.SDK_INT >= 30) {
        runCatching {
            (context.getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager).storageVolumes
                .filter { it.state == Environment.MEDIA_MOUNTED || it.state == Environment.MEDIA_MOUNTED_READ_ONLY }
                .mapNotNull { it.directory }
        }.getOrDefault(emptyList())
    } else emptyList()
    return volumes.ifEmpty { listOf(Environment.getExternalStorageDirectory()) }.distinctBy { it.canonicalPath }
}

/** A self-chosen folder with subfolders (item 3): images and PDFs, newest first. */
private class FolderSource(private val context: Context, private val tree: Uri) : DeviceDocSource {
    override val folder = true
    override val ref = FOLDER_REF + tree
    override val watchable = true
    private val uris = mutableMapOf<String, Uri>()

    init {
        keepAccess(context, listOf(tree))
    }

    override fun close() = giveBack(context, listOf(tree))

    /** Known from [items], otherwise (after a restart) from the key `f:<id>:<size>:<date>`. */
    private fun uri(item: DeviceItem): Uri = uris[item.key]
        ?: DocumentsContract.buildDocumentUriUsingTree(tree, item.key.removePrefix("f:").substringBeforeLast(':').substringBeforeLast(':'))

    override suspend fun items(): List<DeviceItem> = withContext(Dispatchers.IO) {
        val found = mutableListOf<Pair<Long, DeviceItem>>()
        val pending = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(tree)))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        while (pending.isNotEmpty() && found.size < MAX_FOLDER_ITEMS) {
            val parent = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            runCatching {
                context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2).orEmpty()
                        val size = if (c.isNull(3)) -1L else c.getLong(3)
                        val modified = if (c.isNull(4)) 0L else c.getLong(4)
                        when {
                            mime == DocumentsContract.Document.MIME_TYPE_DIR -> pending += id
                            size > AttachmentLimits.MAX_PERSONAL_BYTES -> Unit
                            mime == "application/pdf" || mime in FOLDER_IMAGES -> {
                                val key = "f:$id:$size:$modified"
                                uris[key] = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                                found += modified to DeviceItem(key, name, pdf = mime == "application/pdf", size = size)
                            }
                        }
                    }
                }
            }
        }
        found.sortedByDescending { it.first }.map { it.second }
    }

    override suspend fun preview(item: DeviceItem): ByteArray? =
        if (item.pdf) null else withContext(Dispatchers.IO) { previewOf(context, uri(item)) }

    override suspend fun bytes(item: DeviceItem, keep: Boolean): ByteArray? = withContext(Dispatchers.IO) {
        readLimited(context, uri(item), AttachmentLimits.MAX_PERSONAL_BYTES)
    }
}

/**
 * Reopens a source after a restart or update (ADR 0071 item 10) as long as the access exists: the
 * photos with permission, the whole phone with “Zugriff auf alle Dateien”, folders and chosen files with a persisted
 * grant. Otherwise `null`.
 */
internal fun openDocSource(context: Context, ref: String): DeviceDocSource? = when {
    ref == PHOTOS_REF -> if (hasPhotoAccess(context)) PhotoLibrarySource(context) else null
    ref == DEVICE_REF -> if (hasAllFilesAccess()) DeviceFilesSource(context) else null
    ref.startsWith(DEVICE_FOLDER_REF) -> File(ref.removePrefix(DEVICE_FOLDER_REF)).takeIf { hasAllFilesAccess() && it.isDirectory }?.let { DeviceFilesSource(context, it) }
    ref.startsWith(FOLDER_REF) -> Uri.parse(ref.removePrefix(FOLDER_REF)).takeIf { kept(context, it) }?.let { FolderSource(context, it) }
    ref.startsWith(PICKED_PHOTOS_REF) -> urisOf(ref, PICKED_PHOTOS_REF).filter { kept(context, it) }.takeIf { it.isNotEmpty() }?.let { PickedPhotosSource(context, it) }
    ref.startsWith(PICKED_FILES_REF) -> urisOf(ref, PICKED_FILES_REF).filter { kept(context, it) }.takeIf { it.isNotEmpty() }?.let { PickedFilesSource(context, it) }
    else -> null
}

private const val PHOTOS_REF = "photos"
private const val DEVICE_REF = "device"
private const val DEVICE_FOLDER_REF = "device-folder:"
private const val FOLDER_REF = "folder:"
private const val PICKED_PHOTOS_REF = "picked-photos:"
private const val PICKED_FILES_REF = "picked-files:"

private fun refOf(prefix: String, uris: List<Uri>) = prefix + uris.joinToString("\n")

private fun urisOf(ref: String, prefix: String) = ref.removePrefix(prefix).split('\n').filter { it.isNotEmpty() }.map(Uri::parse)

/** Persist the grant across a restart; if that is not possible (some providers), it only lasts until the restart. */
private fun keepAccess(context: Context, uris: List<Uri>) = uris.forEach {
    runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}

/** Give the persisted grant back as soon as nothing needs it any more. */
private fun giveBack(context: Context, uris: List<Uri>) = uris.forEach {
    runCatching { context.contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}

private fun kept(context: Context, uri: Uri) = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

/** Small JPEG for pre-check and list: the system's thumbnail, otherwise read scaled down. */
private fun previewOf(context: Context, uri: Uri): ByteArray? = runCatching {
    val edge = DeviceDocSource.PREVIEW_EDGE
    val bitmap = (if (Build.VERSION.SDK_INT >= 29) runCatching { context.contentResolver.loadThumbnail(uri, Size(edge, edge), null) }.getOrNull() else null)
        ?: decodeSampled(context, uri, edge)
        ?: return null
    ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        out.toByteArray()
    }
}.getOrNull()

private fun decodeSampled(context: Context, uri: Uri, edge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= edge) sample *= 2
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
}

private fun readLimited(context: Context, uri: Uri, limit: Long): ByteArray? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { it.readUpTo(limit) }
}.getOrNull()

private fun InputStream.readUpTo(limit: Long): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        total += n
        if (total > limit) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

private val FOLDER_IMAGES = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

/** Images on the phone, by extension. */
private val DEVICE_IMAGES = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")

/** What the file picker offers: PDFs and images. */
private val PICKED_TYPES = arrayOf("application/pdf", "image/*")

/** The folder “Download” in internal storage, as the starting point of the file picker. */
private val DOWNLOAD_FOLDER: Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")

/** Upper limit for the whole phone, the newest first. */
private const val MAX_DEVICE_ITEMS = 50_000

/** Upper limit per folder so that a huge cloud folder does not blow up the list. */
private const val MAX_FOLDER_ITEMS = 20_000
