package app.anynear.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.anynear.AppConfig
import app.anynear.app.AppController
import app.anynear.attachments.Attachment
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.mimeTypeOf
import app.anynear.mesh.ChatMessage
import app.anynear.attachments.AttachmentState
import app.anynear.attachments.DraftAttachment
import app.anynear.data.PersonalFiles
import app.anynear.i18n.texts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Most recently shown pictures; a photo needs several MB unpacked, hence only a few. */
private object ImageCache {
    private const val MAX = 12
    private val map = LinkedHashMap<String, ImageBitmap>()

    fun get(key: String): ImageBitmap? = map.remove(key)?.also { map[key] = it }

    fun put(key: String, image: ImageBitmap) {
        map[key] = image
        while (map.size > MAX) map.remove(map.keys.first())
    }
}

/** Is the content on the phone? Own attachments always, received ones after loading. */
internal val Attachment.local: Boolean get() = state == AttachmentState.READY || state == AttachmentState.UPLOADING

@Composable
private fun rememberAttachmentImage(controller: AppController, attachment: Attachment): ImageBitmap? {
    val key = when (attachment.kind) {
        AttachmentKind.IMAGE -> attachment.blobId
        AttachmentKind.PDF -> "pdf:${attachment.blobId}"
        AttachmentKind.AUDIO, AttachmentKind.FILE -> return null
    }
    val image by produceState(ImageCache.get(key), key, attachment.local) {
        if (value != null || !attachment.local) return@produceState
        val bytes = controller.attachmentBytes(attachment.blobId) ?: return@produceState
        val decoded = when (attachment.kind) {
            AttachmentKind.IMAGE -> withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
            AttachmentKind.PDF -> renderPdfFirstPage(bytes, PDF_PREVIEW_WIDTH_PX)
            AttachmentKind.AUDIO, AttachmentKind.FILE -> null
        }
        if (decoded != null) {
            ImageCache.put(key, decoded)
            value = decoded
        }
    }
    return image
}

/**
 * Attachments in the speech bubble: photos as a grid (up to four, "+n" on the fourth), PDFs with the first
 * page and the name below, other files with name and kind, voice messages for playing. Tapping photos
 * opens the full screen, tapping files the view in the app ([FileViewer]).
 */
@Composable
internal fun MessageAttachments(controller: AppController, attachments: List<Attachment>, onError: (String) -> Unit) {
    val images = attachments.filter { it.kind == AttachmentKind.IMAGE }
    val pdfs = attachments.filter { it.kind == AttachmentKind.PDF || it.kind == AttachmentKind.FILE }
    var viewerStart by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        when (images.size) {
            0 -> Unit
            1 -> {
                val a = images.single()
                val ratio = if (a.width > 0 && a.height > 0) (a.width.toFloat() / a.height).coerceIn(0.6f, 1.8f) else 4f / 3f
                ImageTile(controller, a, Modifier.width(IMAGE_WIDTH).aspectRatio(ratio)) { viewerStart = 0 }
            }
            else -> {
                val shown = images.take(4)
                shown.chunked(2).forEachIndexed { row, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        pair.forEachIndexed { col, a ->
                            val index = row * 2 + col
                            val more = if (index == 3) images.size - 4 else 0
                            ImageTile(controller, a, Modifier.width((IMAGE_WIDTH - 4.dp) / 2).aspectRatio(1f), more) { viewerStart = index }
                        }
                    }
                }
            }
        }
        pdfs.forEach { PdfRow(controller, it, onError) }
        attachments.filter { it.kind == AttachmentKind.AUDIO }.forEach { MessageVoice(controller, it, onError) }
    }
    viewerStart?.let { start -> ImageViewer(controller, images, start) { viewerStart = null } }
}

@Composable
internal fun ImageTile(controller: AppController, attachment: Attachment, modifier: Modifier, more: Int = 0, onClick: () -> Unit) {
    val image = rememberAttachmentImage(controller, attachment)
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (image != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = texts.chat.photo, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Placeholder(attachment)
        }
        if (more > 0 && image != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Text("+$more", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            }
        }
    }
}

/** Placeholder as long as an attachment is not on the phone. */
@Composable
private fun Placeholder(attachment: Attachment) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
        when (attachment.state) {
            AttachmentState.DOWNLOADING -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else -> Icon(
                if (attachment.kind == AttachmentKind.IMAGE) AppIcons.Bild else AppIcons.Dokument,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            when (attachment.state) {
                AttachmentState.DOWNLOADING -> texts.chat.loadsWhenOnline
                AttachmentState.MISSING -> texts.chat.noLongerAvailable
                else -> ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun PdfRow(controller: AppController, attachment: Attachment, onError: (String) -> Unit, modifier: Modifier = Modifier.width(IMAGE_WIDTH)) {
    val preview = rememberAttachmentImage(controller, attachment)
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(10.dp)
    var viewing by remember(attachment.blobId) { mutableStateOf<ViewedFile?>(null) }
    var action by remember(attachment.blobId) { mutableStateOf<FileAction?>(null) }
    viewing?.let { FileViewer(it) { viewing = null } }
    FileActionDialog(controller, action, onDismiss = { action = null }) { text, ok -> if (ok) controller.showNotice(text) else onError(text) }
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = attachment.local) {
                scope.launch {
                    val bytes = controller.attachmentBytes(attachment.blobId) ?: return@launch onError(texts.chat.fileCantOpen)
                    viewing = ViewedFile(bytes, attachment.name.ifEmpty { fallbackName(attachment) }, mimeTypeOf(attachment.kind, attachment.name))
                }
            },
    ) {
        if (preview != null) {
            Image(
                preview,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).background(Color.White),
            )
        } else if (!attachment.local) {
            Box(Modifier.fillMaxWidth().aspectRatio(2.5f), contentAlignment = Alignment.Center) { Placeholder(attachment) }
        }
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Dokument, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Column(Modifier.padding(start = 8.dp).weight(1f, fill = false)) {
                Text(
                    attachment.name.ifEmpty { fallbackName(attachment) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${fileTypeLabel(attachment)} · ${formatSize(attachment.size)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (attachment.local) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { action = FileAction.Download(listOf(attachment)) }) {
                    Icon(AppIcons.Speichern, contentDescription = texts.chat.saveFile, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { action = FileAction.ToFiles(listOf(attachment)) }) {
                    Icon(AppIcons.Schloss, contentDescription = texts.chat.toFiles, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Photos in full screen: swipe to the next, enlarge with two fingers, save at the top left. */
@Composable
internal fun ImageViewer(controller: AppController, images: List<Attachment>, start: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = AppFullScreenDialogProperties) {
        val pager = rememberPagerState(initialPage = start) { images.size }
        val scope = rememberCoroutineScope()
        // Feedback after saving; the app's hint would be behind the full screen.
        var hint by remember { mutableStateOf<String?>(null) }
        var action by remember { mutableStateOf<FileAction?>(null) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(controller, images[page])
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(AppIcons.Schliessen, contentDescription = texts.common.close, tint = Color.White)
            }
            val current = images[pager.currentPage]
            if (current.local) {
                IconButton(
                    onClick = { action = FileAction.Download(listOf(current)) },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Icon(AppIcons.Speichern, contentDescription = texts.chat.savePhoto, tint = Color.White)
                }
                // Next to it: into „Meine Ablage“ (ADR 0040).
                IconButton(
                    onClick = { action = FileAction.ToFiles(listOf(current)) },
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 56.dp, top = 8.dp),
                ) {
                    Icon(AppIcons.Schloss, contentDescription = texts.chat.toFiles, tint = Color.White)
                }
            }
            FileActionDialog(controller, action, onDismiss = { action = null }) { text, _ -> hint = text }
            hint?.let {
                LaunchedEffect(it) {
                    delay(2500)
                    hint = null
                }
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (images.size > 1) {
                Text(
                    texts.chat.pageOf(pager.currentPage + 1, images.size),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(controller: AppController, attachment: Attachment) {
    val image = rememberAttachmentImage(controller, attachment)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (image == null) Placeholder(attachment) else ZoomableBitmap(image, texts.chat.photo)
    }
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Picture filling the screen: two fingers enlarge it around the spot between them, once enlarged one finger moves
 * it (never beyond its edges), a double tap enlarges at that spot or goes back. Not enlarged, a swipe with one finger
 * stays with the surroundings (paging in the chat).
 */
@Composable
private fun ZoomableBitmap(image: ImageBitmap, contentDescription: String) {
    var scale by remember(image) { mutableFloatStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // How far the picture may move at scale s: half of what it sticks out beyond the screen (ContentScale.Fit).
    fun bounds(s: Float): Offset {
        if (size.width == 0 || size.height == 0 || image.width == 0 || image.height == 0) return Offset.Zero
        val fit = min(size.width / image.width.toFloat(), size.height / image.height.toFloat())
        return Offset(
            max(0f, (image.width * fit * s - size.width) / 2f),
            max(0f, (image.height * fit * s - size.height) / 2f),
        )
    }
    fun clamp(o: Offset, s: Float): Offset {
        val b = bounds(s)
        return Offset(o.x.coerceIn(-b.x, b.x), o.y.coerceIn(-b.y, b.y))
    }
    // The spot under focus stays where it is (graphicsLayer scales around the centre).
    fun zoomTo(target: Float, focus: Offset) {
        val s = target.coerceIn(1f, MAX_ZOOM)
        val factor = s / scale
        val center = Offset(size.width / 2f, size.height / 2f)
        offset = clamp(offset * factor + (focus - center) * (1f - factor), s)
        scale = s
    }
    Image(
        image,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(image) {
                detectTapGestures(onDoubleTap = { spot ->
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        zoomTo(DOUBLE_TAP_ZOOM, spot)
                    }
                })
            }
            .pointerInput(image) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) zoomTo(scale * zoom, event.calculateCentroid(useCurrent = true))
                            val pan = event.calculatePan()
                            if (pan != Offset.Zero) offset = clamp(offset + pan, scale)
                            // Taken here, so that the pager does not page and the dialog does not scroll along.
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
    )
}

/** A single picture in full screen, e.g. the picture of a person or a circle. */
@Composable
internal fun PhotoViewer(image: ImageBitmap, contentDescription: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = AppFullScreenDialogProperties) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            ZoomableBitmap(image, contentDescription)
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(AppIcons.Schliessen, contentDescription = texts.common.close, tint = Color.White)
            }
        }
    }
}

/** Selected attachments above the input field, removable one by one. */
@Composable
internal fun DraftStrip(drafts: List<DraftAttachment>, onRemove: (DraftAttachment) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        items(drafts.size) { i ->
            val draft = drafts[i]
            Box(Modifier.size(72.dp)) {
                val shape = RoundedCornerShape(10.dp)
                Box(
                    Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (draft.kind == AttachmentKind.IMAGE) {
                        val image = remember(draft) { runCatching { draft.bytes.decodeToImageBitmap() }.getOrNull() }
                        if (image != null) Image(image, contentDescription = texts.chat.photo, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                            Icon(AppIcons.Dokument, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(draft.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                // Small circle at the top right; the touch target is larger than what one sees.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                        .clickable(onClickLabel = texts.chat.removeAttachment) { onRemove(draft) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(22.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Schliessen, contentDescription = texts.chat.removeAttachment, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

private class SaveFeedback(val text: String, val ok: Boolean)

/**
 * Save an attachment on the phone. Returns what is to be said („Gespeichert in …“ or an error),
 * `null` if the system itself shows a picker.
 */
private suspend fun saveAttachment(controller: AppController, attachment: Attachment): SaveFeedback? {
    val bytes = controller.attachmentBytes(attachment.blobId) ?: return SaveFeedback(texts.chat.attachmentGone, ok = false)
    val name = handOutName(attachment)
    return when (val result = saveToDevice(bytes, name, mimeTypeOf(attachment.kind, name))) {
        is SaveResult.Saved -> SaveFeedback(texts.chat.savedIn(result.where), ok = true)
        SaveResult.Handed -> null
        SaveResult.Failed -> SaveFeedback(texts.chat.saveFailed, ok = false)
    }
}

/** File name when an attachment leaves the app: its own, otherwise „anynear-….jpg“, „Dokument.pdf“ or „Datei“. */
private fun handOutName(attachment: Attachment): String = attachment.name.ifEmpty {
    when (attachment.kind) {
        AttachmentKind.IMAGE -> "${AppConfig.APP_NAME}-${attachment.blobId.take(8)}.jpg"
        AttachmentKind.AUDIO -> "${AppConfig.APP_NAME}-${attachment.blobId.take(8)}.m4a"
        else -> fallbackName(attachment)
    }
}

/**
 * „Teilen…“ under a message: its text and attachments into the system's share menu (Android sharesheet, iOS share
 * menu), only on this explicit request. The automatic text preview of a voice message stays behind. `null` if the
 * menu opened, otherwise what is to be said.
 */
internal suspend fun shareMessage(controller: AppController, message: ChatMessage): String? {
    val files = message.attachments.map { a ->
        val bytes = controller.attachmentBytes(a.blobId) ?: return texts.chat.attachmentGone
        val name = handOutName(a)
        HandedFile(bytes, name, mimeTypeOf(a.kind, name))
    }
    val text = if (message.attachments.any { it.transcribed }) "" else message.text.trim()
    return if (shareContent(text, files)) null else texts.files.cantOpen
}

/** Put an attachment into a storage (ADR 0040, 0049); `null` if it worked, otherwise what is to be said. */
private suspend fun toStore(controller: AppController, attachment: Attachment, store: PersonalFiles): String? {
    val bytes = controller.attachmentBytes(attachment.blobId) ?: return texts.chat.attachmentGone
    val ok = saveAttachmentToMyFiles(controller, bytes, attachment.kind, attachment.name, attachment.width, attachment.height, store)
    return if (ok) null else texts.status.fileTooLarge
}

/** What is to happen with attachments from the chat; happens only after the prompt in [FileActionDialog]. */
internal sealed interface FileAction {
    val attachments: List<Attachment>

    data class Download(override val attachments: List<Attachment>) : FileAction

    data class ToFiles(override val attachments: List<Attachment>) : FileAction
}

/**
 * Confirmation prompt before downloading or saving into a storage, when saving with a choice of storage: „Meine
 * Ablage“ or that of a circle. Stays in the composition, even without [action], so that saving runs to the end after
 * closing. [onResult] receives what is to be said, and whether it worked.
 */
@Composable
internal fun FileActionDialog(controller: AppController, action: FileAction?, onDismiss: () -> Unit, onResult: (String, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val count = action?.attachments?.size ?: 0
    when (action) {
        null -> Unit
        is FileAction.Download -> AppDialog(
            onDismissRequest = onDismiss,
            title = { Text(texts.chat.downloadTitle(count)) },
            text = { Text(texts.chat.downloadText(count)) },
            confirmButton = {
                AppButton(texts.chat.download, {
                    onDismiss()
                    scope.launch {
                        var last: SaveFeedback? = null
                        for (attachment in action.attachments) {
                            val feedback = saveAttachment(controller, attachment) ?: continue
                            last = feedback
                            if (!feedback.ok) break
                        }
                        last?.let { onResult(it.text, it.ok) }
                    }
                }, icon = AppIcons.Speichern)
            },
            dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
        )
        is FileAction.ToFiles -> {
            val circles by controller.circles.collectAsState()
            val byCircle = controller.circleFiles?.byCircle?.collectAsState()?.value.orEmpty()
            val targets = buildList {
                controller.personalFiles?.let { add(Triple(texts.files.justForMe, AppIcons.Schloss, it) to texts.files.addedToMyFiles) }
                circles.filter { !it.removed }.forEach { circle ->
                    byCircle[circle.id]?.let { add(Triple(circle.name, AppIcons.Kreis, it) to texts.files.addedTo(circle.name)) }
                }
            }
            ActionsDialog(texts.files.chooseStore(count), onDismiss = onDismiss) {
                targets.forEach { (target, done) ->
                    val (name, icon, store) = target
                    AppRow(title = name, icon = icon, onClick = {
                        onDismiss()
                        scope.launch {
                            val problem = action.attachments.firstNotNullOfOrNull { toStore(controller, it, store) }
                            onResult(problem ?: done, problem == null)
                        }
                    })
                }
            }
        }
    }
}

/** Name if none came along: „Dokument.pdf“ or „Datei“. */
private fun fallbackName(attachment: Attachment): String =
    if (attachment.kind == AttachmentKind.PDF) texts.chat.defaultPdfName else texts.chat.defaultFileName

/** Briefly the kind below the name: „PDF“, otherwise the extension („DOCX“) or „Datei“. */
internal fun fileTypeLabel(attachment: Attachment): String = when {
    attachment.kind == AttachmentKind.PDF -> "PDF"
    else -> attachment.name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 }?.uppercase() ?: texts.chat.file
}

/** „850 KB“, „2,4 MB“. */
internal fun formatSize(bytes: Long): String {
    if (bytes < 1024 * 1024) return "${maxOf(1, (bytes + 512) / 1024)} KB"
    val tenths = (bytes * 10 + 512 * 1024) / (1024 * 1024)
    return texts.chat.megabytes(tenths)
}

private val IMAGE_WIDTH = 240.dp
private const val PDF_PREVIEW_WIDTH_PX = 600
