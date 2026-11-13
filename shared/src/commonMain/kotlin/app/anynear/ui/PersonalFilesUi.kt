package app.anynear.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.anynear.AppConfig
import app.anynear.anmp.core.DocIndex
import app.anynear.anmp.core.DocInfo
import app.anynear.anmp.core.DocItem
import app.anynear.anmp.core.DocKind
import app.anynear.anmp.core.FileTitle
import app.anynear.anmp.core.FileTitles
import app.anynear.data.FileSort
import app.anynear.data.FileTitleData
import app.anynear.app.AppController
import app.anynear.app.DocReading
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.AttachmentLimits
import app.anynear.attachments.DraftAttachment
import app.anynear.attachments.mimeTypeOf
import app.anynear.attachments.shrinkImage
import app.anynear.data.FileDoc
import app.anynear.data.NetState
import app.anynear.data.NetStatus
import app.anynear.data.OnlineProblem
import app.anynear.data.PersonalFile
import app.anynear.data.PersonalFiles
import app.anynear.i18n.texts
import app.anynear.mesh.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

// „Meine Ablage“ (ADR 0040, 0041) and the storages of the circles (ADR 0049) share these pages: [circleId] `null`
// is „Meine Ablage“. Selection, display and sending are the same as for attachments in the chat (ChatAttachments.kt).

/** The storage of a page: „Meine Ablage“ or that of the circle [circleId]; `null` as long as it does not exist (yet). */
@Composable
private fun rememberFiles(controller: AppController, circleId: String?): PersonalFiles? {
    if (circleId == null) return controller.personalFiles
    val circles = controller.circleFiles ?: return null
    val byCircle by circles.byCircle.collectAsState()
    return byCircle[circleId]
}

@Composable
private fun circleName(controller: AppController, circleId: String): String {
    val circles by controller.circles.collectAsState()
    return circles.firstOrNull { it.id == circleId }?.name.orEmpty()
}

/** List of a storage, newest first, with „Foto hinzufügen“ and „Datei hinzufügen“. */
@Composable
internal fun PersonalFilesScreen(controller: AppController, onBack: () -> Unit, onOpenFile: (id: String) -> Unit, circleId: String? = null) {
    val store = rememberFiles(controller, circleId) ?: return
    val files by store.list.collectAsState()
    val scope = rememberCoroutineScope()
    val picker = rememberAttachmentPicker(AttachmentLimits.MAX_PERSONAL_BYTES) { images, picked ->
        scope.launch {
            for (file in picked) {
                val problem = withContext(Dispatchers.Default) { addPersonalFile(controller, store, file, images) }
                problem?.let(controller::showNotice)
            }
        }
    }
    // Fetch right away what others in the circle have stored in the meantime.
    LaunchedEffect(circleId) { circleId?.let { controller.circleFiles?.syncNow(it) } }
    val name = circleId?.let { circleName(controller, it) }
    // Search the phone for documents (ADR 0071), only in „Meine Ablage“.
    val finder = controller.docFinder.takeIf { circleId == null }
    var finding by remember { mutableStateOf<Boolean?>(null) }
    val findRequest by controller.findDocsRequest.collectAsState()
    LaunchedEffect(findRequest) {
        if (findRequest && finder != null) {
            finding = false
            controller.consumeFindDocsRequest()
        }
    }
    var cardGone by remember { mutableStateOf(finder?.memory?.cardDismissed ?: true) }
    finding?.let { askNow ->
        DocFinderScreen(controller, askNow) { finding = null }
        return
    }
    // Bin (ADR 0086): own page, as long as something is in it.
    val bin by store.bin.collectAsState()
    var binOpen by remember(circleId) { mutableStateOf(false) }
    if (binOpen && bin.isNotEmpty()) {
        BinScreen(store, bin, circle = circleId != null) { binOpen = false }
        return
    }
    // Search and stacks instead of folders (ADR 0067).
    var query by remember(circleId) { mutableStateOf("") }
    var kind by remember(circleId) { mutableStateOf("") }
    // Gallery (ADR 0070): photos and documents separated, topics instead of stacks.
    var photosView by remember(circleId) { mutableStateOf(false) }
    var topic by remember(circleId) { mutableStateOf("") }
    val readVersion by (controller.docReading?.version ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }).collectAsState()
    val index by produceState<DocIndex?>(null, files, readVersion) {
        value = withContext(Dispatchers.Default) { docIndexOf(files, controller.docReading) }
    }
    val photoIds = remember(files) { files.filter { isPhoto(it) }.map { it.id }.toSet() }
    val hasPhotos = photoIds.isNotEmpty()
    val hasDocs = photoIds.size < files.size
    // Only photos: straight to the gallery; only documents: no gallery.
    val gallery = hasPhotos && (photosView || !hasDocs)
    val kinds = index?.kinds().orEmpty().filter { it != DocKind.PHOTO }
    val topics = index?.topics().orEmpty().toList()
    if (kind.isNotEmpty() && kind !in kinds) kind = ""
    if (topic.isNotEmpty() && topic !in topics) topic = ""
    // Order (ADR 0091 §7): only for the documents without a search; hits stay by relevance, the gallery by time.
    val sort by controller.filesSort.collectAsState()
    var choosingSort by remember { mutableStateOf(false) }
    val sorted: (List<PersonalFile>) -> List<PersonalFile> = { list -> if (gallery || query.isNotBlank()) list else sort.sort(list) }
    // Search in the background: otherwise noticeable while typing with many files with long text.
    val shown by produceState(sorted(files.filter { (it.id in photoIds) == gallery }), index, query, kind, topic, gallery, files, sort) {
        val i = index ?: return@produceState run { value = sorted(files.filter { (it.id in photoIds) == gallery }) }
        value = withContext(Dispatchers.Default) {
            val byId = files.associateBy { it.id }
            val ids = if (gallery) i.searchTopic(query, DocKind.PHOTO, topic) else i.search(query, kind)
            sorted(ids.mapNotNull { byId[it] }.filter { (it.id in photoIds) == gallery })
        }
    }
    Column(Modifier.fillMaxSize()) {
        AppHeader(
            name ?: texts.files.justForMe,
            subtitle = texts.setup.tabFiles,
            leading = { HeaderBackButton(onBack) },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(if (name != null) texts.files.circleIntro(name) else texts.files.myFilesIntro, style = MaterialTheme.typography.bodyLarge)
            if (finder != null) {
                FindDocsCard(
                    finder,
                    ask = files.isEmpty() && !cardGone,
                    onShow = { finding = false },
                    onSearch = {
                        // The card does not come back after the first search run (item 1).
                        finder.memory.cardDismissed = true
                        cardGone = true
                        finding = true
                    },
                    onNotNow = {
                        finder.memory.cardDismissed = true
                        cardGone = true
                    },
                )
            }
            if (files.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (hasPhotos && hasDocs) ViewSwitch(gallery) { photosView = it }
                    SearchField(query, { query = it }, if (gallery) texts.files.searchPhotos else texts.files.search)
                    // Suggestions from the images, already from one topic on (ADR 0074 item 4).
                    if (gallery && topics.isNotEmpty()) TopicChips(topics, topic) { topic = it }
                    if (!gallery && kinds.size > 1) KindChips(kinds, kind) { kind = it }
                }
                // Long lists shortened, the rest on tap, otherwise you scroll forever (as with the files of a chat).
                val limit = if (gallery) PHOTO_LIMIT else FILE_LIMIT
                var showAll by remember(circleId, query, kind, topic, gallery) { mutableStateOf(false) }
                val visible = if (showAll) shown else shown.take(limit)
                val showMore: (@Composable () -> Unit)? = if (showAll || shown.size <= limit) null else {
                    {
                        AppRow(
                            title = texts.chat.showAllFiles(shown.size),
                            icon = if (gallery) AppIcons.Bild else AppIcons.Dokument,
                            chevron = true,
                            onClick = { showAll = true },
                        )
                    }
                }
                when {
                    shown.isEmpty() -> NoSearchResults()
                    gallery -> {
                        PhotoGrid(store, visible) { onOpenFile(it) }
                        showMore?.let { AppSection { it() } }
                    }
                    else -> {
                        if (query.isBlank() && shown.size > 1) SortRow(sort) { choosingSort = true }
                        AppSection(texts.files.files) {
                            visible.forEach { key(it.id) { PersonalFileRow(store, it) { onOpenFile(it.id) } } }
                            showMore?.invoke()
                        }
                    }
                }
            }
            ActionTiles {
                ActionTile(texts.files.addPhoto, AppIcons.Bild) { picker.pickImages(AttachmentLimits.MAX_PER_MESSAGE) }
                ActionTile(texts.files.addFile, AppIcons.Dokument) { picker.pickFiles() }
            }
            // Search the phone for documents, right below adding (ADR 0071 item 1, no ⋮ menu).
            finder?.let { f -> FindDocsRow(f) { finding = false } }
            if (bin.isNotEmpty()) {
                AppSection {
                    AppRow(title = texts.files.binRow(bin.size), icon = AppIcons.Papierkorb, chevron = true, onClick = { binOpen = true })
                }
            }
            FilesFooter(circleId)
        }
    }
    if (choosingSort) ActionsDialog(texts.files.sort, onDismiss = { choosingSort = false }) {
        FileSort.entries.forEach { s ->
            AppRow(title = sortName(s), trailing = if (s == sort) ({ StatusDot(true) }) else null, onClick = {
                choosingSort = false
                controller.setFilesSort(s)
            })
        }
    }
}

/** Name of a file in lists and headers: its title in full (at most 40 characters), otherwise the shortened file name. */
private fun shownName(file: PersonalFile): String = if (file.hasTitle) file.shown else shortFileName(file.name)

private fun sortName(sort: FileSort): String = when (sort) {
    FileSort.NEWEST -> texts.files.sortNewest
    FileSort.TITLE -> texts.files.sortTitle
    FileSort.NAME -> texts.files.sortName
}

/** „Sortieren: Neueste zuerst“ above the list; tapping chooses the order (ADR 0091 §7). */
@Composable
private fun SortRow(sort: FileSort, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("${texts.files.sort}:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(sortName(sort), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Bin of a storage (ADR 0086): when each file disappears for good, restore or delete for good,
 * empty everything. Opening only works after restoring.
 */
@Composable
private fun BinScreen(store: PersonalFiles, bin: List<PersonalFile>, circle: Boolean, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf<PersonalFile?>(null) }
    var confirmDelete by remember { mutableStateOf<PersonalFile?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        AppHeader(texts.files.bin, leading = { HeaderBackButton(onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                if (circle) texts.files.circleBinIntro(PersonalFiles.BIN_DAYS) else texts.files.binIntro(PersonalFiles.BIN_DAYS),
                style = MaterialTheme.typography.bodyLarge,
            )
            AppSection {
                bin.forEach { f ->
                    key(f.id) {
                        AppRow(
                            title = shownName(f),
                            subtitle = f.purgeAt.takeIf { it > 0 }?.let { texts.files.binGoneOn(formatShortDate(it)) },
                            leading = { FileThumbnail(store, f) },
                            onClick = { chosen = f },
                        )
                    }
                }
            }
            AppButton(texts.files.emptyBin, { confirmEmpty = true }, Modifier.fillMaxWidth())
        }
    }
    chosen?.let { f ->
        ActionsDialog(shownName(f), onDismiss = { chosen = null }) {
            AppRow(title = texts.files.restore, icon = AppIcons.Aktualisieren, onClick = {
                chosen = null
                scope.launch(Dispatchers.Default) { store.restore(f.id) }
            })
            AppRow(title = texts.files.deleteForGood, icon = AppIcons.Papierkorb, danger = true, onClick = {
                chosen = null
                confirmDelete = f
            })
        }
    }
    confirmDelete?.let { f ->
        ConfirmDialog(
            title = texts.files.deleteQuestion(shownName(f)),
            text = if (circle) texts.files.circleDeleteText else texts.files.deleteText,
            confirm = texts.files.deleteForGood,
            onConfirm = {
                confirmDelete = null
                scope.launch(Dispatchers.Default) { store.delete(f.id) }
            },
            onDismiss = { confirmDelete = null },
        )
    }
    if (confirmEmpty) {
        ConfirmDialog(
            title = texts.files.emptyBinQuestion,
            text = if (circle) texts.files.circleEmptyBinText else texts.files.emptyBinText,
            confirm = texts.files.emptyBin,
            onConfirm = {
                confirmEmpty = false
                scope.launch(Dispatchers.Default) { store.emptyBin() }
            },
            onDismiss = { confirmEmpty = false },
        )
    }
}

/** This many files or photos (ten rows) the storage shows before „Alle ansehen“ opens the rest. */
private const val FILE_LIMIT = 20
private const val PHOTO_LIMIT = 30

/** A photo for the gallery (ADR 0070): recognized as a photo, or an image that has not been read yet. */
private fun isPhoto(file: PersonalFile): Boolean =
    file.doc?.kind?.let { it == DocKind.PHOTO } ?: (file.attachmentKind == AttachmentKind.IMAGE)

/** „Dokumente“ and „Fotos“ as two fields of equal width; only visible if there are both. */
@Composable
private fun ViewSwitch(photos: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(selected = !photos, onClick = { onChange(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
            Text(texts.files.documentsView)
        }
        SegmentedButton(selected = photos, onClick = { onChange(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
            Text(texts.files.photosView)
        }
    }
}

/** Topics of the gallery (ADR 0070): only those that occur; tapping again shows all again. */
@Composable
private fun TopicChips(topics: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected.isEmpty(), onClick = { onSelect("") }, label = { Text(texts.files.allKinds) })
        topics.forEach { t ->
            FilterChip(selected = t == selected, onClick = { onSelect(if (t == selected) "" else t) }, label = { Text(texts.files.topicName(t)) })
        }
    }
}

/** Photos as a grid, three per row, square; tapping opens the photo. */
@Composable
private fun PhotoGrid(store: PersonalFiles, photos: List<PersonalFile>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        photos.chunked(GRID_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { file ->
                    key(file.id) {
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(role = Role.Button, onClickLabel = texts.files.open) { onOpen(file.id) },
                        ) { PhotoTile(store, file) }
                    }
                }
                repeat(GRID_COLUMNS - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Preview in the gallery, from the same cache as the small thumbnails of the list. */
@Composable
private fun PhotoTile(store: PersonalFiles, file: PersonalFile) {
    // Own key: the gallery needs larger previews than the list.
    val cacheKey = "grid:${file.id}"
    val image by produceState(ThumbnailCache.get(cacheKey), file.id) {
        value = ThumbnailCache.get(cacheKey)
        if (value != null) return@produceState
        val made = thumbnailLimit.withPermit {
            // Online only (ADR 0080): the stored thumbnail.
            val bytes = withContext(Dispatchers.Default) { if (file.onlineOnly) store.thumbnail(file.id) else store.bytes(file.id) } ?: return@withPermit null
            withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap().scaledToCover(GRID_PX) }.getOrNull() }
        } ?: return@produceState
        ThumbnailCache.put(cacheKey, made)
        value = made
    }
    image?.let { Image(it, contentDescription = file.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { RowIcon(AppIcons.Bild) }
}

/** Stacks of the storage (ADR 0067): only the kinds that exist; tapping again shows all again. */
@Composable
private fun KindChips(kinds: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected.isEmpty(), onClick = { onSelect("") }, label = { Text(texts.files.allKinds) })
        kinds.forEach { k ->
            FilterChip(selected = k == selected, onClick = { onSelect(if (k == selected) "" else k) }, label = { Text(texts.files.kindName(k)) })
        }
    }
}

/** Kind and details in one line, e.g. „Rechnung · Stadtwerke · 12.03.2025 · 84,20 €“. */
private fun docLine(doc: FileDoc): String = listOfNotNull(
    texts.files.kindName(doc.kind),
    doc.from.takeIf { it.isNotEmpty() },
    doc.date.takeIf { it > 0 }?.let(::formatShortDate),
    doc.currency.takeIf { it.isNotEmpty() }?.let { texts.files.amount(doc.amountCents, it) },
    doc.until.takeIf { it > 0 }?.let { texts.files.docUntil(formatShortDate(it)) },
).joinToString(" · ")

/**
 * Online storage space across all storages together (ADR 0029, 0079), at the very top of the storages page (not in the
 * storages): bar, hint when almost or completely full, and „Mehr Speicher“ ([onMore], subscription plans per ADR 0076).
 * Only visible once a sync knows the usage.
 */
@Composable
internal fun StorageMeter(controller: AppController, onMore: () -> Unit) {
    val storage = controller.storage ?: return
    val space by storage.space.collectAsState()
    val s = space ?: return
    val warn = s.full || s.nearlyFull
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(texts.files.storageTitle, style = MaterialTheme.typography.titleMedium)
                    Text(texts.files.storageUsed(formatSize(s.used), formatStorageSize(s.quota)), style = MaterialTheme.typography.bodyMedium)
                }
                AppButton(texts.files.moreStorage, onMore, kind = if (warn) ButtonKind.Primary else ButtonKind.Secondary)
            }
            LinearProgressIndicator(
                progress = { s.fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(
                when {
                    s.full -> texts.files.storageFull
                    s.nearlyFull -> texts.files.storageNearlyFull
                    else -> texts.files.storageAllStores
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** „1 GB“, „1,5 GB“; below that in whole MB („250 MB“, ADR 0076). */
internal fun formatStorageSize(bytes: Long): String {
    if (bytes < 1024L * 1024 * 1024) return "${(bytes + 512L * 1024) / (1024L * 1024)} MB"
    return texts.files.gigabytes((bytes * 10 + 512L * 1024 * 1024) / (1024L * 1024 * 1024))
}

/** Where the files are and who can open them. */
@Composable
private fun FilesFooter(circleId: String?) {
    Text(
        if (circleId != null) texts.files.circleOnlyHere else texts.files.onlyHere,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Takes a selected file into [store]; returns what went wrong, otherwise `null`. */
private suspend fun addPersonalFile(controller: AppController, store: PersonalFiles, file: PickedFile, image: Boolean): String? {
    // Storage space across all storages full (ADR 0079): do not take in anything new that would never get online.
    if (controller.storage?.full == true) return texts.files.storageFull
    val bytes = file.bytes ?: return if (file.tooLarge && !image) texts.files.fileTooLarge else texts.status.fileTooLarge
    // Photos, also images chosen as a file, large, without location (ADR 0067 §7) and upright; downscaled only in the chat.
    val imageFile = !image && file.name.substringAfterLast('.', "").lowercase() in STORE_AS_PHOTO
    if (image || imageFile) {
        val draft = controller.prepareStoreImage(bytes)
        when {
            draft != null && imageFile -> store.add(draft, "${file.name.substringBeforeLast('.')}.jpg")
            draft != null -> store.add(draft, "${texts.files.photoName(formatShortDate(Clock.System.now().toEpochMilliseconds()))}.jpg")
            image -> return texts.status.fileTooLarge
            // An image that cannot be read (rare format): unchanged like any other file.
        }
        if (draft != null) return null
    }
    val (draft, problem) = controller.prepareFile(bytes, file.name, AttachmentLimits.MAX_PERSONAL_BYTES, texts.files.fileTooLarge)
    if (draft == null) return problem
    store.add(draft, draft.name.ifEmpty { file.name })
    return null
}

/**
 * Search across the files of a storage (ADR 0067), with the text read on this device and, if loaded, the
 * small meaning model (ADR 0078). Not on the main thread.
 */
internal suspend fun docIndexOf(files: List<PersonalFile>, reading: DocReading?): DocIndex =
    DocIndex(files.map { DocItem(it.id, it.name, it.attachmentKind.wire, it.addedAt, it.doc?.toInfo(), reading?.text(it.id).orEmpty(), it.title?.text.orEmpty()) }.toTypedArray())
        .also { index -> reading?.meaning()?.let(index::useMeaning) }

@Composable
internal fun PersonalFileRow(store: PersonalFiles, file: PersonalFile, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FileThumbnail(store, file)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(shownName(file), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // With a title the file name stands small and greyed out below it (ADR 0091 §1).
            if (file.hasTitle) {
                Text(
                    shortFileName(file.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                listOfNotNull(
                    file.doc?.kind?.takeIf { it != DocKind.OTHER && it != DocKind.PHOTO }?.let(texts.files::kindName),
                    formatShortDate(file.doc?.date?.takeIf { it > 0 } ?: file.addedAt),
                    formatSize(file.size),
                    texts.files.onlineOnly.takeIf { file.onlineOnly },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(AppIcons.Weiter, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Small thumbnail (photo or first PDF page), so that you recognize the file in the list; otherwise the icon. */
@Composable
private fun FileThumbnail(store: PersonalFiles, file: PersonalFile) {
    val kind = file.attachmentKind
    // With the size in the key: after rotating (also on another device) a new thumbnail.
    val cacheKey = "${file.id}/${file.size}"
    val thumbnail by produceState(ThumbnailCache.get(cacheKey), cacheKey) {
        // Do not rely on value: when the file changes, produceState still holds the image of the previous one.
        value = ThumbnailCache.get(cacheKey)
        if (value != null || (kind != AttachmentKind.IMAGE && kind != AttachmentKind.PDF)) return@produceState
        val made = thumbnailLimit.withPermit {
            // Online only (ADR 0080): the stored thumbnail, a JPEG for PDFs too.
            val bytes = withContext(Dispatchers.Default) { if (file.onlineOnly) store.thumbnail(file.id) else store.bytes(file.id) } ?: return@withPermit null
            when {
                file.onlineOnly || kind == AttachmentKind.IMAGE ->
                    withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap().scaledToCover(THUMBNAIL_PX) }.getOrNull() }
                else -> renderPdfFirstPage(bytes, THUMBNAIL_PX)
            }
        } ?: return@produceState
        ThumbnailCache.put(cacheKey, made)
        value = made
    }
    val image = thumbnail
    if (image == null) {
        // Same width as the thumbnail, so that the text does not jump as soon as it is there.
        Box(Modifier.size(THUMBNAIL_SIZE), contentAlignment = Alignment.Center) {
            RowIcon(if (kind == AttachmentKind.IMAGE) AppIcons.Bild else AppIcons.Dokument)
        }
        return
    }
    Box(
        Modifier
            .size(THUMBNAIL_SIZE)
            .clip(MaterialTheme.shapes.small)
            .background(if (kind == AttachmentKind.PDF) Color.White else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Image(
            image,
            contentDescription = null,
            contentScale = if (kind == AttachmentKind.PDF) ContentScale.FillWidth else ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.size(THUMBNAIL_SIZE),
        )
    }
}

/** Scales down so that the shorter side is [px] large; an unpacked photo would otherwise need several MB per row. */
private fun ImageBitmap.scaledToCover(px: Int): ImageBitmap {
    val scale = px.toFloat() / minOf(width, height)
    if (scale >= 1f) return this
    val size = IntSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
    val out = ImageBitmap(size.width, size.height)
    Canvas(out).drawImageRect(this, IntOffset.Zero, IntSize(width, height), IntOffset.Zero, size, Paint().apply { filterQuality = FilterQuality.Medium })
    return out
}

/** Thumbnails of the storage; small, hence many. */
private object ThumbnailCache {
    private const val MAX = 200
    private val map = LinkedHashMap<String, ImageBitmap>()

    fun get(key: String): ImageBitmap? = map.remove(key)?.also { map[key] = it }

    fun put(key: String, image: ImageBitmap) {
        map[key] = image
        while (map.size > MAX) map.remove(map.keys.first())
    }
}

/** Never unpack many photos at once, even if the list is long. */
private val thumbnailLimit = Semaphore(2)

/** A file: preview, open, send into a chat, save to the phone, rotate, rename, delete. */
@Composable
internal fun PersonalFileScreen(
    controller: AppController,
    fileId: String,
    onBack: () -> Unit,
    onSend: () -> Unit,
    onShowOnline: () -> Unit,
    circleId: String? = null,
) {
    val store = rememberFiles(controller, circleId) ?: return
    val files by store.list.collectAsState()
    val file = files.firstOrNull { it.id == fileId } ?: return
    val problems by store.onlineProblems.collectAsState()
    val scope = rememberCoroutineScope()
    var viewer by remember { mutableStateOf<ImageBitmap?>(null) }
    var viewing by remember { mutableStateOf<ViewedFile?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var titling by remember { mutableStateOf(false) }
    var choosingKind by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var rotating by remember { mutableStateOf(false) }
    // With the size: after rotating (also on another device) the new preview right away.
    val preview by produceState<ImageBitmap?>(null, file.id, file.onlineOnly, file.size) {
        if (file.onlineOnly) {
            // Online only (ADR 0080): the thumbnail, without loading anything.
            value = withContext(Dispatchers.Default) { store.thumbnail(file.id)?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
            return@produceState
        }
        val bytes = withContext(Dispatchers.Default) { store.bytes(file.id) } ?: return@produceState
        value = when (file.attachmentKind) {
            AttachmentKind.IMAGE -> withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
            AttachmentKind.PDF -> renderPdfFirstPage(bytes, PREVIEW_WIDTH_PX)
            else -> null
        }
    }
    val open: () -> Unit = {
        scope.launch {
            val bytes = withContext(Dispatchers.Default) { store.load(file) }
            when {
                bytes == null -> controller.showNotice(cantLoad(file))
                file.attachmentKind == AttachmentKind.IMAGE && !file.onlineOnly -> viewer = preview ?: run { controller.showNotice(texts.files.cantOpen); null }
                file.attachmentKind == AttachmentKind.IMAGE ->
                    viewer = withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() } ?: run { controller.showNotice(texts.files.cantOpen); null }
                else -> viewing = ViewedFile(bytes, file.name, mimeTypeOf(file.attachmentKind, file.name))
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        AppHeader(shownName(file), subtitle = circleId?.let { circleName(controller, it) } ?: texts.files.justForMe, leading = { HeaderBackButton(onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            preview?.let {
                Image(
                    it,
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (file.width > 0 && file.height > 0) (file.width.toFloat() / file.height).coerceIn(0.6f, 1.8f) else 1.2f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (file.attachmentKind == AttachmentKind.PDF) Color.White else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(role = Role.Button, onClickLabel = texts.files.open, onClick = open),
                )
            }
            AppSection {
                AppRow(
                    title = file.shown,
                    subtitle = listOfNotNull(
                        file.name.takeIf { file.hasTitle },
                        texts.files.addedOn(formatShortDate(file.addedAt)),
                        formatSize(file.size),
                    ).joinToString(" · "),
                    icon = if (file.attachmentKind == AttachmentKind.IMAGE) AppIcons.Bild else AppIcons.Dokument,
                )
            }
            DocSection(file) { choosingKind = true }
            ReadTextSection(controller.docReading, file.id)
            OnlineSection(file, problems[file.id], circleId != null, onShowOnline)
            KeepOnDeviceSection(store, file)
            AppButton(texts.files.open, open, Modifier.fillMaxWidth())
            AppSection(footer = texts.files.sendHint) {
                AppRow(title = texts.files.sendToChat, icon = AppIcons.Senden, chevron = true, onClick = {
                    if (file.size > AttachmentLimits.MAX_BYTES) controller.showNotice(texts.files.tooLargeForChat) else onSend()
                })
                AppRow(title = texts.files.saveToPhone, icon = AppIcons.Speichern, onClick = {
                    scope.launch {
                        val bytes = withContext(Dispatchers.Default) { store.load(file) } ?: return@launch controller.showNotice(cantLoad(file))
                        when (val result = saveToDevice(bytes, file.name, mimeTypeOf(file.attachmentKind, file.name))) {
                            is SaveResult.Saved -> controller.showNotice(texts.chat.savedIn(result.where))
                            SaveResult.Handed -> Unit
                            SaveResult.Failed -> controller.showNotice(texts.chat.saveFailed)
                        }
                    }
                })
            }
            AppSection {
                // Rotating only for photos on the phone; one that is online only is first fetched back (ADR 0080).
                if (file.attachmentKind == AttachmentKind.IMAGE && !file.onlineOnly) {
                    AppRow(title = texts.files.rotate, subtitle = texts.files.rotateHint, icon = AppIcons.Aktualisieren, enabled = !rotating, onClick = {
                        rotating = true
                        scope.launch {
                            val ok = rotatePersonalFile(controller, store, file)
                            rotating = false
                            if (!ok) controller.showNotice(texts.files.rotateFailed)
                        }
                    })
                }
                AppRow(title = texts.files.changeTitle, icon = AppIcons.Stift, onClick = { titling = true })
                AppRow(title = texts.files.renameFile, icon = AppIcons.Stift, onClick = { renaming = true })
                AppRow(title = texts.files.moveToBin, icon = AppIcons.Papierkorb, danger = true, onClick = { confirmDelete = true })
            }
            FilesFooter(circleId)
        }
    }
    viewer?.let { PhotoViewer(it, file.shown) { viewer = null } }
    viewing?.let { FileViewer(it) { viewing = null } }
    if (choosingKind) ActionsDialog(texts.files.docChoose, onDismiss = { choosingKind = false }) {
        // To choose from only the important kinds, photo and other (ADR 0074 item 5); one chosen earlier stays visible.
        val current = file.doc?.kind
        val kinds = DocKind.choosable.toList() + listOfNotNull(current?.takeIf { it !in DocKind.choosable })
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            kinds.forEach { k ->
                AppRow(title = texts.files.kindName(k), trailing = if (k == file.doc?.kind) ({ StatusDot(true) }) else null, onClick = {
                    choosingKind = false
                    val chosen = file.doc?.toInfo()?.chosen(k) ?: DocInfo.chosenKind(k)
                    scope.launch(Dispatchers.Default) { store.setDoc(file.id, FileDoc.of(chosen)) }
                })
            }
        }
    }
    if (titling) TitleDialog(file.title?.text ?: file.shown, onDismiss = { titling = false }) { text ->
        titling = false
        // Empty: back to anynear, reading makes it again (ADR 0091 §5).
        val chosen = FileTitle.chosen(text)
        scope.launch(Dispatchers.Default) { store.setTitle(file.id, if (chosen.text.isEmpty()) null else FileTitleData.of(chosen)) }
    }
    if (renaming) RenameDialog(file.name, onDismiss = { renaming = false }) { name ->
        renaming = false
        scope.launch(Dispatchers.Default) { store.rename(file.id, name) }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = texts.files.binQuestion(shownName(file)),
            text = if (circleId != null) texts.files.circleBinText(PersonalFiles.BIN_DAYS) else texts.files.binText(PersonalFiles.BIN_DAYS),
            confirm = texts.files.moveToBin,
            onConfirm = {
                confirmDelete = false
                onBack()
                scope.launch(Dispatchers.Default) { store.trash(file.id) }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * What the phone has read from the file (ADR 0078 §1), collapsed. Shows whether the reading fails or the recognition;
 * the text stays on the phone.
 */
@Composable
private fun ReadTextSection(reading: DocReading?, id: String) {
    reading ?: return
    val version by reading.version.collectAsState()
    val read by produceState<Pair<Boolean, String>?>(null, id, version) {
        value = withContext(Dispatchers.Default) { reading.isRead(id) to reading.text(id) }
    }
    val (done, text) = read ?: return
    var open by remember(id) { mutableStateOf(false) }
    val words = text.split(Regex("\\s+")).count { it.isNotEmpty() }
    AppSection(footer = if (open) texts.files.readTextHint else null) {
        AppRow(
            title = texts.files.readText,
            subtitle = when {
                !done -> texts.files.docReading
                words == 0 -> texts.files.readTextNone
                else -> texts.files.readTextWords(words)
            },
            icon = AppIcons.Dokument,
            onClick = if (words > 0) ({ open = !open }) else null,
        )
        if (open && words > 0) {
            SelectionContainer {
                Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

/** What the file is (ADR 0067); tapping lets you choose the kind. */
@Composable
private fun DocSection(file: PersonalFile, onChoose: () -> Unit) {
    val doc = file.doc
    AppSection(footer = if (doc?.byPerson == true) texts.files.docChosenHint else texts.files.docRecognizedHint) {
        AppRow(
            title = doc?.let { texts.files.kindName(it.kind) } ?: texts.files.docReading,
            subtitle = if (doc?.kind == DocKind.PHOTO) {
                doc.topics.joinToString(", ") { texts.files.topicName(it) }.takeIf { it.isNotEmpty() }
            } else {
                doc?.let(::docLine)?.substringAfter(" · ", "")?.takeIf { it.isNotEmpty() }
            },
            icon = AppIcons.Dokument,
            chevron = true,
            onClick = onChoose,
        )
    }
}

/**
 * „Status im Netz“ (ADR 0028 §7: distributed from two providers on, not already with the first relay); tapping opens
 * [PersonalFileOnlineScreen] as soon as it is known where it is stored.
 */
@Composable
private fun OnlineSection(file: PersonalFile, problem: OnlineProblem?, circle: Boolean, onShowOnline: () -> Unit) {
    val known = file.online && file.relays.isNotEmpty()
    AppSection(texts.files.netStatus) {
        AppRow(
            title = netStatusTitle(file.netStatus),
            subtitle = netStateText(file, problem, circle),
            icon = when (file.netStatus) {
                NetStatus.SPREAD, NetStatus.ONLY_IN_NET -> AppIcons.Haken
                NetStatus.PARTLY_SPREAD -> AppIcons.Achtung
                NetStatus.SPREADING, NetStatus.ON_DEVICE -> AppIcons.Uhr
                NetStatus.IN_BIN -> AppIcons.Papierkorb
            },
            chevron = known,
            onClick = if (known) onShowOnline else null,
        )
    }
}

/** Name of the state under „Status im Netz“. */
private fun netStatusTitle(status: NetStatus): String = when (status) {
    NetStatus.ON_DEVICE -> texts.files.netOnDevice
    NetStatus.SPREADING -> texts.files.notYetOnline
    NetStatus.SPREAD -> texts.files.onlineSaved
    NetStatus.PARTLY_SPREAD -> texts.files.netIncomplete
    NetStatus.ONLY_IN_NET -> texts.files.onlineOnly
    NetStatus.IN_BIN -> texts.files.netInBin
}

/** Sentence on the state: backed up, incomplete, already arrived or why not yet. */
private fun netStateText(file: PersonalFile, problem: OnlineProblem?, circle: Boolean): String = when (file.netState) {
    NetState.SECURED -> onlineSavedText(circle)
    NetState.INCOMPLETE -> texts.files.netIncompleteText
    NetState.SECURING -> if (file.online) texts.files.securingText else notOnlineText(problem, circle)
}

/**
 * „Auf diesem Handy behalten“ (ADR 0080): on is the default; off takes the file off the phone and keeps a thumbnail.
 * Switching off only works once the file is online.
 */
@Composable
private fun KeepOnDeviceSection(store: PersonalFiles, file: PersonalFile) {
    val scope = rememberCoroutineScope()
    var busy by remember(file.id) { mutableStateOf(false) }
    val onDevice by produceState(true, file, busy) {
        // After „wieder behalten“ the sync loads in the background; until then check here.
        while (true) {
            value = withContext(Dispatchers.Default) { file.onlineOnly || store.onDevice(file.id) }
            if (value) break
            delay(2.seconds)
        }
    }
    val keep = !file.onlineOnly
    AppSection(footer = texts.files.keepOnDeviceFooter) {
        AppRow(
            title = texts.files.keepOnDevice,
            subtitle = when {
                !keep -> texts.files.keepOnDeviceOff
                !onDevice -> texts.files.keepOnDeviceLoading
                file.netState != NetState.SECURED -> texts.files.keepOnDeviceWait
                else -> texts.files.keepOnDeviceOn
            },
            icon = AppIcons.Speichern,
            trailing = {
                Switch(
                    checked = keep,
                    // Off only once the file is backed up in the network (two providers) and is completely on the phone.
                    enabled = !busy && (!keep || (file.netState == NetState.SECURED && onDevice)),
                    onCheckedChange = { on ->
                        busy = true
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                if (on) store.keepOnDevice(file.id) else store.setOnlineOnly(file.id, thumbnailOf(file, store.bytes(file.id)))
                            }
                            busy = false
                        }
                    },
                )
            },
        )
    }
}

/** Thumbnail for a file that is online only (ADR 0080): photo or first PDF page as a small JPEG, otherwise none. */
private suspend fun thumbnailOf(file: PersonalFile, bytes: ByteArray?): ByteArray? {
    bytes ?: return null
    return when (file.attachmentKind) {
        AttachmentKind.IMAGE -> shrinkImage(bytes, ONLINE_ONLY_THUMBNAIL_PX, ONLINE_ONLY_THUMBNAIL_QUALITY)?.bytes
        AttachmentKind.PDF -> renderPdfFirstPage(bytes, ONLINE_ONLY_THUMBNAIL_PX)?.toJpeg(ONLINE_ONLY_THUMBNAIL_QUALITY)
        else -> null
    }
}

/** Why a file could not be fetched: online only without internet, or broken. */
private fun cantLoad(file: PersonalFile): String = if (file.onlineOnly) texts.files.onlineOnlyNoInternet else texts.files.cantOpen

/** Why the file is not online yet; without a known reason: waiting for internet. */
private fun notOnlineText(problem: OnlineProblem?, circle: Boolean): String = when (problem) {
    OnlineProblem.TOO_LARGE -> texts.files.tooLargeOnlineText
    OnlineProblem.NOT_ACCEPTED -> if (circle) texts.files.circleNotAcceptedOnlineText else texts.files.notAcceptedOnlineText
    OnlineProblem.STORAGE_FULL -> texts.files.storageFullOnlineText
    null -> texts.files.notYetOnlineText
}

/** Who can open the file online: only me or everyone in the circle. */
private fun onlineSavedText(circle: Boolean): String = if (circle) texts.files.circleOnlineSavedText else texts.files.onlineSavedText

/** Subpage of „Online gespeichert“: since when and at which mailboxes the file is stored. */
@Composable
internal fun PersonalFileOnlineScreen(controller: AppController, fileId: String, onBack: () -> Unit, circleId: String? = null) {
    val store = rememberFiles(controller, circleId) ?: return
    val files by store.list.collectAsState()
    val file = files.firstOrNull { it.id == fileId } ?: return
    val problems by store.onlineProblems.collectAsState()
    val checkedAt = store.checkedAt
    Column(Modifier.fillMaxSize()) {
        AppHeader(netStatusTitle(file.netStatus), subtitle = shortFileName(file.name), leading = { HeaderBackButton(onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                // Data centers of different providers are counted, not relays (ADR 0028 §2).
                if (file.netState == NetState.SECURED && file.onlineSince > 0) texts.files.onlineWhere(formatDateTime(file.onlineSince), file.operators)
                else netStateText(file, problems[file.id], circleId != null),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (file.online && file.relays.isNotEmpty()) {
                AppSection(texts.settings.mailboxes, footer = if (checkedAt <= 0) null else if (circleId != null) texts.files.circleLastChecked(formatDateTime(checkedAt)) else texts.files.lastChecked(formatDateTime(checkedAt))) {
                    file.relays.forEach { relay ->
                        AppRow(title = relay, subtitle = texts.files.holdsThisFile, trailing = { StatusDot(true) })
                    }
                }
            }
        }
    }
}

/** Title of a file (ADR 0091 §5); empty gives it back to anynear. */
@Composable
private fun TitleDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.files.titleDialog) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it.take(FileTitles.MAX) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(texts.files.titleHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { AppButton(texts.files.save, { onSave(text) }) },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.files.renameTitle) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { AppButton(texts.files.rename, { onRename(name) }, enabled = name.isNotBlank()) },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/** Extensions of images that are stored as a photo in the storage; GIF stays a file, otherwise it would stand still. */
private val STORE_AS_PHOTO = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")

/** Send a file from a storage („Meine Ablage“: [circleId] `null`) as an attachment into [conversation]. `null` means sent, else the reason. */
internal suspend fun sendPersonalFile(controller: AppController, fileId: String, conversation: Conversation, circleId: String? = null): String? {
    val store = (if (circleId == null) controller.personalFiles else controller.circleFiles?.of(circleId)) ?: return texts.files.cantOpen
    val file = store.list.value.firstOrNull { it.id == fileId } ?: return texts.files.cantOpen
    val stored = withContext(Dispatchers.Default) { store.loadDraft(file) } ?: return cantLoad(file)
    // Photos are stored large in the storage (ADR 0067 §7); into the chat they go downscaled like every photo.
    val draft = if (stored.kind == AttachmentKind.IMAGE && maxOf(stored.width, stored.height) > AttachmentLimits.IMAGE_MAX_EDGE) {
        controller.prepareImage(stored.bytes) ?: return texts.files.cantOpen
    } else {
        stored
    }
    return when (val result = controller.send(conversation, "", listOf(draft))) {
        AppController.SendResult.Sent -> null
        is AppController.SendResult.Rejected -> result.reason
    }
}

/** Rotate a photo in a storage a quarter turn to the right; goes up as a new version. `false`: did not work. */
private suspend fun rotatePersonalFile(controller: AppController, store: PersonalFiles, file: PersonalFile): Boolean {
    val bytes = withContext(Dispatchers.Default) { store.bytes(file.id) } ?: return false
    val draft = controller.rotateStoreImage(bytes, 90) ?: return false
    return withContext(Dispatchers.Default) { store.replaceContent(file.id, draft) }
}

/** Put an attachment from a chat into a storage ([store], otherwise „Meine Ablage“); [name] empty for photos. */
internal suspend fun saveAttachmentToMyFiles(
    controller: AppController,
    bytes: ByteArray,
    kind: AttachmentKind,
    name: String,
    width: Int,
    height: Int,
    store: PersonalFiles? = controller.personalFiles,
): Boolean {
    store ?: return false
    val fileName = when {
        name.isNotEmpty() -> name
        kind == AttachmentKind.IMAGE -> "${texts.files.photoName(formatShortDate(Clock.System.now().toEpochMilliseconds()))}.jpg"
        kind == AttachmentKind.PDF -> "${AppConfig.APP_NAME}.pdf"
        else -> texts.chat.defaultFileName
    }
    // Photos from the chat upright, as when adding; if it cannot be read, unchanged.
    val draft = (if (kind == AttachmentKind.IMAGE) controller.uprightImage(bytes) else null)
        ?: DraftAttachment(kind, bytes, name = fileName, width = width, height = height)
    return withContext(Dispatchers.Default) { store.add(draft, fileName) } != null
}

private const val PREVIEW_WIDTH_PX = 900

/** Thumbnail of a file that is online only (ADR 0080), as in the sync. */
private const val ONLINE_ONLY_THUMBNAIL_PX = AttachmentLimits.THUMBNAIL_EDGE
private const val ONLINE_ONLY_THUMBNAIL_QUALITY = AttachmentLimits.THUMBNAIL_QUALITY
private val THUMBNAIL_SIZE = 48.dp
private const val THUMBNAIL_PX = 160

/** Photos per row in the gallery. */
private const val GRID_COLUMNS = 3

/** Shorter side of the preview in the gallery, in pixels. */
private const val GRID_PX = 360
