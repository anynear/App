package app.anynear.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anynear.anmp.core.DocInfo
import app.anynear.app.AppController
import app.anynear.app.DeviceDocSource
import app.anynear.app.DocFinder
import app.anynear.app.FinderState
import app.anynear.app.FinderPause
import app.anynear.app.FoundDoc
import app.anynear.attachments.AttachmentLimits
import app.anynear.data.FileDoc
import app.anynear.i18n.texts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.time.Clock

// Search the phone for important documents (ADR 0071): card when „Meine Ablage“ is empty, entry in the storage menu,
// search run with a list of suggestions. Access to the photos only after „Suchen“, folders via the system's folder
// picker, single files (e.g. from „Download“) via the file picker.

/** Access to the photos or a folder; the result comes via the callback of [rememberDeviceDocAccess]. */
internal interface DeviceDocAccess {
    /** Asks the system for access to the photos (ADR 0071 item 2). */
    fun requestPhotos()

    /** Picker window for photos with multiple selection, without a permission. */
    fun pickPhotos()

    /** Folder picker of the system (ADR 0071 item 3), also Dropbox, Synology Drive … */
    fun pickFolder()

    /** System file picker for PDFs and images with multiple selection, e.g. from „Download“ (which Android locks as a folder). */
    fun pickFiles()

    /** Search the whole phone (Android 11 and up with „Zugriff auf alle Dateien“). */
    val canSearchDevice: Boolean

    /** Whether the access for [searchDevice] has already been granted. */
    fun hasDeviceAccess(): Boolean

    /** With access search right away, otherwise open the setting; after returning with access, search. */
    fun searchDevice()

    /** Opens the setting for the access; [onGranted] as soon as you come back with access. */
    fun requestDeviceAccess(onGranted: () -> Unit) = searchDevice()

    /** Subfolders of [path] (`null`: the storage volumes of the phone), only with the access from [searchDevice]. */
    fun folders(path: String?): List<DeviceFolder> = emptyList()

    /** Search only this folder (with subfolders), with the access from [searchDevice]. */
    fun searchFolder(path: String) {}
}

/** A folder for the selection in the app: name to show, path to search. */
internal class DeviceFolder(val name: String, val path: String)

/** [onSource] with a source, or `null` with [denied] `true` if the system gave no access. Cancelling reports nothing. */
@Composable
internal expect fun rememberDeviceDocAccess(onSource: (source: DeviceDocSource?, denied: Boolean) -> Unit): DeviceDocAccess

/**
 * Card in „Meine Ablage“: if a search is running, is interrupted or suggestions are open, its state with „Ansehen“
 * ([onShow]), also after a restart (item 10). Otherwise above the empty storage the question, once per phone (item 1),
 * if [ask] holds.
 */
@Composable
internal fun FindDocsCard(finder: DocFinder, ask: Boolean, onSearch: () -> Unit, onShow: () -> Unit, onNotNow: () -> Unit) {
    var more by remember { mutableStateOf(false) }
    val state by finder.state.collectAsState()
    if (state.running || state.found.isNotEmpty() || state.interrupted) return FindDocsRunningCard(state, onShow)
    if (!ask) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(texts.find.cardTitle, style = MaterialTheme.typography.titleMedium)
            Text(texts.find.cardText, style = MaterialTheme.typography.bodyMedium)
            Text(
                texts.find.moreAbout,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(role = Role.Button) { more = !more },
            )
            if (more) Text(texts.find.moreText, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(texts.find.search, onSearch)
                AppButton(texts.find.notNow, onNotNow, kind = ButtonKind.Quiet)
            }
        }
    }
}

/** Instead of the question: the state of a run that keeps running, with „Ansehen“. */
@Composable
private fun FindDocsRunningCard(state: FinderState, onShow: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                when {
                    state.running && state.total > 0 -> texts.find.menuRunning(state.checked, state.total)
                    state.running -> texts.find.preparing
                    state.found.isNotEmpty() -> texts.find.found(state.found.size)
                    else -> texts.find.interrupted
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.running) LinearProgressIndicator(
                progress = { if (state.total > 0) state.checked.toFloat() / state.total else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            AppButton(texts.find.show, onShow)
        }
    }
}

/**
 * Small progress circle in the header of every page while the search for documents is running (also in the background):
 * you see that it is running and get to it with one tap. Not on the page of the search itself.
 */
@Composable
internal fun FinderHeaderTask(controller: AppController) {
    val finder = controller.docFinder ?: return
    val state by finder.state.collectAsState()
    if (!state.running) return
    val label = if (state.total > 0) texts.find.menuRunning(state.checked, state.total) else texts.find.preparing
    Box(
        Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = texts.find.show, onClick = controller::openFindDocs)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val color = AppTheme.colors.onHeader
        if (state.total > 0) {
            CircularProgressIndicator(progress = { state.checked.toFloat() / state.total }, modifier = Modifier.size(22.dp), color = color, strokeWidth = 2.5.dp)
        } else {
            CircularProgressIndicator(Modifier.size(22.dp), color = color, strokeWidth = 2.5.dp)
        }
        Icon(AppIcons.Suche, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
    }
}

/**
 * „Handy nach Dokumenten durchsuchen“ (ADR 0071 item 1) as a row under „Foto/Datei hinzufügen“ of „Meine Ablage“;
 * during a run with progress, afterwards with the number of finds.
 */
@Composable
internal fun FindDocsRow(finder: DocFinder, onFind: () -> Unit) {
    val state by finder.state.collectAsState()
    AppSection {
        AppRow(
            title = when {
                state.running && state.total > 0 -> texts.find.menuRunning(state.checked, state.total)
                state.running -> texts.find.preparing
                state.found.isNotEmpty() -> texts.find.found(state.found.size)
                else -> texts.find.menuEntry
            },
            subtitle = texts.find.entryHint,
            icon = AppIcons.Suche,
            chevron = true,
            onClick = onFind,
        )
    }
}

/**
 * Search run and suggestions. [askNow] asks for the photos right on opening (coming from the card). Closing ends the
 * run and gives the access back.
 */
@Composable
internal fun DocFinderScreen(controller: AppController, askNow: Boolean, onClose: () -> Unit) {
    val finder = controller.docFinder ?: return
    val state by finder.state.collectAsState()
    val scope = rememberCoroutineScope()
    var denied by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // Check mark per suggestion; recognized documents are preselected, „Weitere Fotos mit Text“ are not (item 6).
    val checked = remember { mutableStateMapOf<String, Boolean>() }
    var showMore by remember { mutableStateOf(false) }
    // „Alles noch einmal prüfen“ for the next run; afterwards off again.
    var again by remember { mutableStateOf(false) }
    var choosingFolder by remember { mutableStateOf(false) }
    val access = rememberDeviceDocAccess { source, wasDenied ->
        if (source != null) {
            denied = false
            finder.start(source, controller.readable, again)
            again = false
        } else if (wasDenied) {
            denied = true
        }
    }
    // Android 11 and up (change 08.11.2026): first „Zugriff auf alle Dateien“, then the whole phone or a folder.
    var hasAccess by remember { mutableStateOf(access.hasDeviceAccess()) }
    val alreadyChecked = remember(state.running, state.done) { finder.memory.checkedCount() }
    LaunchedEffect(Unit) { if (askNow && !state.active) access.requestPhotos() }
    // Back does not end a run: it keeps running, also in the background (item 9), and found suggestions stay until
    // you take them over or start a new run. Only an empty, finished state is forgotten.
    DisposableEffect(Unit) { onDispose { finder.state.value.let { if (!it.running && it.found.isEmpty()) finder.stop() } } }
    // Find new documents automatically (ADR 0081): ask once, after the first finished run over a source with
    // permanent access. Whoever leaves the page without an answer has said „Nein“; the question does not come again.
    val watchOn by finder.watchOn.collectAsState()
    val watchAsked by finder.watchAsked.collectAsState()
    val askWatch = !watchAsked && state.done && !state.running && finder.memory.watchSources.isNotEmpty()
    var askedHere by remember { mutableStateOf(false) }
    LaunchedEffect(askWatch) { if (askWatch) askedHere = true }
    DisposableEffect(Unit) { onDispose { if (askedHere && !finder.watchAsked.value) finder.setWatch(false) } }
    if (choosingFolder) {
        FolderChooser(access, onDismiss = { choosingFolder = false }) { path ->
            choosingFolder = false
            access.searchFolder(path)
        }
    }
    val choices: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (alreadyChecked > 0) CheckAgainRow(alreadyChecked, again) { again = it }
            if (access.canSearchDevice) {
                if (!hasAccess) {
                    Text(texts.find.deviceText, style = MaterialTheme.typography.bodyMedium)
                    Text(texts.find.deviceTapAllow, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppButton(texts.find.allowAccess, { access.requestDeviceAccess { hasAccess = true } }, Modifier.fillMaxWidth(), kind = ButtonKind.Main)
                } else {
                    AppButton(texts.find.searchDevice, access::searchDevice, Modifier.fillMaxWidth(), kind = ButtonKind.Main)
                    AppButton(texts.find.chooseFolder, { choosingFolder = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                }
            } else {
                if (denied) AppButton(texts.find.pickPhotos, access::pickPhotos, Modifier.fillMaxWidth())
                else AppButton(texts.find.inPhotos, access::requestPhotos, Modifier.fillMaxWidth())
                OtherSources(access)
            }
        }
    }
    fun isChecked(found: FoundDoc) = checked[found.item.key] ?: found.recognized
    val recognized = state.found.filter { it.recognized }
    val withText = state.found.filter { !it.recognized }
    val chosen = state.found.filter(::isChecked)

    Column(Modifier.fillMaxSize()) {
        // Here you are already at the search: no pointer to it in the header.
        CompositionLocalProvider(LocalHeaderTasks provides null) {
            AppHeader(texts.find.title, subtitle = texts.files.justForMe, leading = { HeaderBackButton(onClose) })
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (!state.active) {
                Text(texts.find.cardText, style = MaterialTheme.typography.bodyLarge)
                if (denied) Text(texts.find.denied, style = MaterialTheme.typography.bodyMedium)
                choices()
                Text(texts.find.moreText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                if (askWatch) WatchQuestion(onYes = { finder.setWatch(true) }, onNo = { finder.setWatch(false) })
                Progress(
                    running = state.running,
                    interrupted = state.interrupted,
                    checkedCount = state.checked,
                    total = state.total,
                    skipped = state.skipped,
                    pause = state.pause,
                    onAllowData = finder::allowMobileData,
                    onCancel = finder::cancel,
                )
                if (!state.running && !state.interrupted && state.found.isEmpty()) Text(texts.find.nothingFound, style = MaterialTheme.typography.bodyLarge)
                if (recognized.isNotEmpty()) {
                    AppSection(texts.find.found(recognized.size)) {
                        recognized.groupBy { it.doc.kind }.forEach { (_, group) ->
                            group.forEach { found ->
                                key(found.item.key) { FoundRow(controller, finder, found, isChecked(found)) { checked[found.item.key] = it } }
                            }
                        }
                    }
                }
                if (withText.isNotEmpty()) {
                    AppSection {
                        AppRow(title = "${texts.find.moreWithText} (${withText.size})", icon = AppIcons.Bild, chevron = true, onClick = { showMore = !showMore })
                        if (showMore) withText.forEach { found ->
                            key(found.item.key) { FoundRow(controller, finder, found, isChecked(found)) { checked[found.item.key] = it } }
                        }
                    }
                }
                // Afterwards (or interrupted, item 10) start anew; the run skips what was checked, except with
                // „Alles noch einmal prüfen“.
                if (!state.running) choices()
            }
            // The switch is where you search anyway (ADR 0081 item 3), as soon as the question is answered.
            if (watchAsked) WatchSwitch(watchOn) { finder.setWatch(it) }
        }
        if (state.found.isNotEmpty()) {
            AppButton(
                texts.find.takeOver(chosen.size),
                onClick = {
                    busy = true
                    scope.launch {
                        val shown = state.found
                        val added = takeOver(controller, chosen)
                        // The phone remembers taken-over and deselected ones (item 7).
                        finder.decided(shown.map { it.item })
                        busy = false
                        controller.showNotice(if (added < chosen.size) "${texts.find.added(added)} ${texts.find.someFailed}" else texts.find.added(added))
                        if (!finder.state.value.running) onClose()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                kind = ButtonKind.Main,
                enabled = chosen.isNotEmpty() && !busy,
            )
        }
    }
}

/** The question after the first run (ADR 0081 item 2): yes or no, nothing else. */
@Composable
private fun WatchQuestion(onYes: () -> Unit, onNo: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(texts.find.watchQuestion, style = MaterialTheme.typography.titleMedium)
            Text(texts.find.watchText, style = MaterialTheme.typography.bodyMedium)
            AppButton(texts.find.watchYes, onYes, Modifier.fillMaxWidth())
            AppButton(texts.find.watchNo, onNo, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet)
        }
    }
}

/** Switch „Neue Dokumente von selbst finden“ on and off (ADR 0081 item 3). */
@Composable
private fun WatchSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    AppSection(footer = if (on) texts.find.watchSwitchOn else texts.find.watchSwitchOff) {
        AppRow(
            title = texts.find.watchSwitch,
            icon = AppIcons.Suche,
            trailing = { Switch(checked = on, onCheckedChange = onChange) },
            onClick = { onChange(!on) },
        )
    }
}

/** Further sources besides the photos: the whole phone (Android 11 and up) or single files, plus a folder. */
@Composable
private fun OtherSources(access: DeviceDocAccess) {
    AppButton(texts.find.pickFiles, access::pickFiles, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
    AppButton(texts.find.alsoFolder, access::pickFolder, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
}


@Composable
private fun Progress(
    running: Boolean,
    interrupted: Boolean,
    checkedCount: Int,
    total: Int,
    skipped: Int,
    pause: FinderPause?,
    onAllowData: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            when {
                !running && interrupted -> texts.find.interrupted
                !running -> texts.find.done
                total == 0 -> texts.find.preparing
                else -> texts.find.progress(checkedCount, total)
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        // Otherwise you wonder why only few files are looked at for the whole phone.
        if (skipped > 0) Text(texts.find.skipped(skipped), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (running) {
            if (total > 0) LinearProgressIndicator(progress = { checkedCount.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            when (pause) {
                FinderPause.BACKGROUND -> Text(texts.find.pausedBackground, style = MaterialTheme.typography.bodyMedium)
                FinderPause.BATTERY -> Text(texts.find.pausedBattery, style = MaterialTheme.typography.bodyMedium)
                FinderPause.NO_WIFI -> {
                    Text(texts.find.noWifiQuestion, style = MaterialTheme.typography.bodyMedium)
                    AppButton(texts.find.searchAnyway, onAllowData)
                }
                null -> Unit
            }
            Text(texts.find.keepsRunning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppButton(texts.find.cancel, onCancel, kind = ButtonKind.Quiet)
        }
    }
}

/** A suggestion: thumbnail (tap: view full screen), kind and details, check mark. */
@Composable
private fun FoundRow(controller: AppController, finder: DocFinder, found: FoundDoc, checked: Boolean, onChecked: (Boolean) -> Unit) {
    var opening by remember(found.item.key) { mutableStateOf(false) }
    if (opening) FoundViewer(controller, finder, found) { opening = false }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Checkbox) { onChecked(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FoundThumbnail(finder, found, onOpen = { opening = true })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(foundLine(found.doc), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(shortFileName(found.item.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Checkbox(checked = checked, onCheckedChange = onChecked)
    }
}

/** Kind and details, e.g. „Rechnung · Stadtwerke · 12.03.2025“. */
private fun foundLine(doc: DocInfo): String = listOfNotNull(
    texts.files.kindName(doc.kind),
    doc.from.takeIf { it.isNotEmpty() },
    doc.date.takeIf { it > 0 }?.let(::formatShortDate),
).joinToString(" · ")

/**
 * Suggestion in full screen, like a file in „Meine Ablage“: a photo in its own full screen (zoomable), a PDF in the
 * viewer of the app ([FileViewer]). The whole file is read; from the cloud only if it could be taken over anyway.
 */
@Composable
private fun FoundViewer(controller: AppController, finder: DocFinder, found: FoundDoc, onClose: () -> Unit) {
    var photo by remember(found.item.key) { mutableStateOf<ImageBitmap?>(null) }
    var pdf by remember(found.item.key) { mutableStateOf<ViewedFile?>(null) }
    LaunchedEffect(found.item.key) {
        val bytes = finder.bytesFor(found)
        when {
            bytes == null -> {
                controller.showNotice(texts.files.cantOpen)
                onClose()
            }
            found.item.pdf -> {
                pdf = ViewedFile(bytes, found.item.name, "application/pdf")
            }
            else -> {
                photo = withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
                if (photo == null) {
                    controller.showNotice(texts.files.cantOpen)
                    onClose()
                }
            }
        }
    }
    photo?.let { PhotoViewer(it, shortFileName(found.item.name), onClose) }
    pdf?.let { FileViewer(it, onClose) }
}

@Composable
private fun FoundThumbnail(finder: DocFinder, found: FoundDoc, onOpen: () -> Unit) {
    val image by produceState<ImageBitmap?>(null, found.item.key) {
        value = if (found.item.pdf) {
            finder.bytesFor(found)?.let { renderPdfFirstPage(it, THUMB_PX) }
        } else {
            withContext(Dispatchers.Default) { found.preview?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
        }
    }
    Box(
        Modifier.size(THUMB_SIZE).clip(MaterialTheme.shapes.small)
            .background(if (found.item.pdf) Color.White else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClickLabel = texts.files.open, onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.size(THUMB_SIZE))
        } else {
            Icon(if (found.item.pdf) AppIcons.Dokument else AppIcons.Bild, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Puts the chosen suggestions into „Meine Ablage“ (item 6): photos like „Foto hinzufügen“ (3200 pixels, no location, upright),
 * PDFs unchanged, each right away with kind and details; the text already read is taken over instead of read again.
 */
private suspend fun takeOver(controller: AppController, chosen: List<FoundDoc>): Int = withContext(Dispatchers.Default) {
    val finder = controller.docFinder ?: return@withContext 0
    val store = controller.personalFiles ?: return@withContext 0
    var added = 0
    for (found in chosen) {
        val bytes = finder.bytesFor(found) ?: continue
        val file = if (found.item.pdf) {
            val (draft, _) = controller.prepareFile(bytes, found.item.name, AttachmentLimits.MAX_PERSONAL_BYTES, texts.files.fileTooLarge)
            draft?.let { store.add(it, it.name.ifEmpty { found.item.name }) }
        } else {
            val draft = controller.prepareStoreImage(bytes)
            draft?.let { store.add(it, "${found.item.name.substringBeforeLast('.').ifEmpty { texts.files.photoName(formatShortDate(Clock.System.now().toEpochMilliseconds())) }}.jpg") }
        } ?: continue
        controller.docReading?.adopt(file.id, found.text)
        store.setDoc(file.id, FileDoc.of(found.doc))
        added++
    }
    added
}

private val THUMB_SIZE = 56.dp
private const val THUMB_PX = 168

/** Switch „Alles noch einmal prüfen“ with the sentence how many files are skipped otherwise. */
@Composable
private fun CheckAgainRow(count: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(texts.find.alreadyChecked(count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onChange(!checked) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = onChange)
            Text(texts.find.checkAgain, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Choose a folder in the app (Android with „Zugriff auf alle Dateien“): also „Download“, locked by the system's picker. */
@Composable
private fun FolderChooser(access: DeviceDocAccess, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    // Path from the storage volume to the current folder; empty: the storage volumes of the phone.
    val trail = remember { mutableStateListOf<DeviceFolder>() }
    val current = trail.lastOrNull()
    val folders by produceState(emptyList<DeviceFolder>(), current?.path) {
        value = withContext(Dispatchers.Default) { access.folders(current?.path) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(current?.name ?: texts.find.chooseFolderTitle) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (current != null) {
                    AppRow(title = texts.common.back, icon = AppIcons.Zurueck, onClick = { trail.removeAt(trail.lastIndex) })
                }
                if (current != null && folders.isEmpty()) Text(texts.find.noSubfolders, Modifier.padding(16.dp))
                folders.forEach { f -> AppRow(title = f.name, icon = AppIcons.Dokument, chevron = true, onClick = { trail += f }) }
            }
        },
        confirmButton = {
            if (current != null) AppButton(texts.find.searchThisFolder, { onChoose(current.path) })
        },
        dismissButton = { AppButton(texts.find.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}
