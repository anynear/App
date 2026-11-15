package app.anynear.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.anynear.i18n.texts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** A file that is viewed in the app (chat, „Meine Ablage“, document search, channels). */
internal class ViewedFile(val bytes: ByteArray, val name: String, val mimeType: String)

/**
 * Viewing always in the app, never saving to the phone as a side effect or handing to another app: photos in
 * full screen (zoomable), PDFs with all pages (scroll, enlarge with two fingers), text as text, other files via
 * QuickLook (iOS) or with name and size. Saving and „Mit anderer App öffnen“ only via dedicated buttons.
 */
@Composable
internal fun FileViewer(file: ViewedFile, onClose: () -> Unit) {
    when {
        file.mimeType.startsWith("image/") -> ImageFileViewer(file, onClose)
        file.mimeType == "application/pdf" -> PdfViewer(file, onClose)
        file.mimeType == "text/plain" || file.mimeType == "text/csv" -> TextViewer(file, onClose)
        else -> OtherFileViewer(file, onClose, tryPreview = true)
    }
}

@Composable
private fun ImageFileViewer(file: ViewedFile, onClose: () -> Unit) {
    // null = loading, otherwise the image or nothing (then like any other file).
    val image by produceState<Result<ImageBitmap>?>(null, file) {
        value = withContext(Dispatchers.Default) { runCatching { file.bytes.decodeToImageBitmap() } }
    }
    when (val result = image) {
        null -> Unit
        else -> result.getOrNull()?.let { PhotoViewer(it, file.name, onClose) } ?: OtherFileViewer(file, onClose, tryPreview = false)
    }
}

@Composable
private fun PdfViewer(file: ViewedFile, onClose: () -> Unit) {
    var pages by remember(file) { mutableStateOf<PdfPages?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        val opened = openPdf(file.bytes)
        if (opened == null) {
            failed = true
            return@LaunchedEffect
        }
        pages = opened
        // The view keeps the PDF open until it is closed.
        try {
            awaitCancellation()
        } finally {
            opened.close()
        }
    }
    if (failed) {
        OtherFileViewer(file, onClose, tryPreview = false)
        return
    }
    ViewerFrame(file, onClose, dark = true) {
        val current = pages
        if (current == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        } else {
            PdfPageList(current)
        }
    }
}

/** All pages one below the other; one finger scrolls, two fingers enlarge and pan. */
@Composable
private fun PdfPageList(pages: PdfPages) {
    val list = rememberLazyListState()
    var scale by remember(pages) { mutableFloatStateOf(1f) }
    var offsetX by remember(pages) { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        // A bit sharper than the screen, so that enlarging does not blur immediately.
        val renderPx = (width * 1.5f).toInt().coerceIn(600, 2000)
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = 72.dp, bottom = 56.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pages) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                                val max = (scale - 1f) * width / 2f
                                offsetX = (offsetX + event.calculatePan().x).coerceIn(-max, max)
                                // Intercept before scrolling, otherwise the list scrolls along.
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX),
        ) {
            items(pages.count) { index -> PdfPage(pages, index, renderPx) }
        }
        if (pages.count > 1) {
            Text(
                texts.chat.pageOf(list.firstVisibleItemIndex + 1, pages.count),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PdfPage(pages: PdfPages, index: Int, widthPx: Int) {
    val image by produceState<ImageBitmap?>(null, pages, index, widthPx) { value = pages.render(index, widthPx) }
    val ratio = image?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: (1f / 1.414f)
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White), contentAlignment = Alignment.Center) {
        val shown = image
        if (shown != null) {
            Image(shown, contentDescription = texts.chat.pageOf(index + 1, pages.count), contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize())
        } else {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

/** Plain text (TXT, CSV) for reading and selecting; up to 1 MB, otherwise like any other file. */
@Composable
private fun TextViewer(file: ViewedFile, onClose: () -> Unit) {
    if (file.bytes.size > 1024 * 1024) {
        OtherFileViewer(file, onClose, tryPreview = false)
        return
    }
    val text = remember(file) { file.bytes.decodeToString() }
    ViewerFrame(file, onClose, dark = false) {
        androidx.compose.foundation.text.selection.SelectionContainer(
            Modifier.fillMaxSize().padding(top = 64.dp).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * Files the app does not draw itself (Office, text …). iOS shows them with QuickLook in the app; otherwise name,
 * kind and size with the explicit buttons „Mit anderer App öffnen“ and „Auf dem Handy speichern“.
 */
@Composable
private fun OtherFileViewer(file: ViewedFile, onClose: () -> Unit, tryPreview: Boolean) {
    var previewing by remember(file) { mutableStateOf(tryPreview) }
    LaunchedEffect(file) {
        if (!tryPreview) return@LaunchedEffect
        if (previewInApp(file.bytes, file.name, file.mimeType)) onClose() else previewing = false
    }
    if (previewing) return
    ViewerFrame(file, onClose, dark = false, barActions = false) { actions ->
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(AppIcons.Dokument, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
            Text(file.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Text(
                "${typeLabel(file.name)} · ${formatSize(file.bytes.size.toLong())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                texts.files.cantShowHere,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            AppButton(texts.files.openWithOtherApp, {
                actions.openWithOtherApp()
            }, Modifier.fillMaxWidth(), icon = AppIcons.Teilen)
            AppButton(texts.files.saveToPhone, actions.save, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = AppIcons.Speichern)
        }
    }
}

/**
 * Frame of every view: at the top save, share, name and close; hints appear inside the view, because those of the
 * app would lie behind it. The content gets the same actions for its own buttons.
 */
@Composable
private fun ViewerFrame(
    file: ViewedFile,
    onClose: () -> Unit,
    dark: Boolean,
    barActions: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.BoxScope.(ViewerActions) -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = AppFullScreenDialogProperties) {
        val scope = rememberCoroutineScope()
        var hint by remember { mutableStateOf<String?>(null) }
        val save: () -> Unit = {
            scope.launch {
                hint = when (val result = saveToDevice(file.bytes, file.name, file.mimeType)) {
                    is SaveResult.Saved -> texts.chat.savedIn(result.where)
                    SaveResult.Handed -> null
                    SaveResult.Failed -> texts.chat.saveFailed
                }
            }
        }
        val other: () -> Unit = { if (!openWithOtherApp(file.bytes, file.name, file.mimeType)) hint = texts.files.cantOpen }
        val share: () -> Unit = { if (!shareFile(file.bytes, file.name, file.mimeType)) hint = texts.files.cantOpen }
        val actions = remember(file) { ViewerActions(save, other) }
        val background = if (dark) Color.Black else MaterialTheme.colorScheme.surface
        val onBackground = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
        Box(Modifier.fillMaxSize().background(background)) {
            content(actions)
            Row(
                Modifier.fillMaxWidth()
                    .background(if (dark) Color.Black.copy(alpha = 0.6f) else background)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Without own buttons in the content, save and share are up here. Share shows the share sheet,
                // never directly another app (otherwise Android would open a PDF e.g. in Acrobat Reader).
                if (barActions) {
                    IconButton(onClick = save) {
                        Icon(AppIcons.Speichern, contentDescription = texts.files.saveToPhone, tint = onBackground)
                    }
                    IconButton(onClick = share) {
                        Icon(AppIcons.Teilen, contentDescription = texts.common.share, tint = onBackground)
                    }
                } else {
                    Box(Modifier.size(8.dp))
                }
                Text(
                    shortFileName(file.name),
                    color = onBackground,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton(onClick = onClose) {
                    Icon(AppIcons.Schliessen, contentDescription = texts.common.close, tint = onBackground)
                }
            }
            hint?.let {
                LaunchedEffect(it) {
                    delay(2500)
                    hint = null
                }
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 88.dp, start = 24.dp, end = 24.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Explicit actions of a view: save to the phone, open with another app. */
private class ViewerActions(val save: () -> Unit, val openWithOtherApp: () -> Unit)

/** „PDF“, „DOCX“ or „Datei“. */
private fun typeLabel(name: String): String =
    name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 }?.uppercase() ?: texts.chat.file
