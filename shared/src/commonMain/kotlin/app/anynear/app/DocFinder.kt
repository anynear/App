package app.anynear.app

import app.anynear.anmp.core.DocInfo
import app.anynear.anmp.core.DocKind
import app.anynear.anmp.core.DocRecognizer
import app.anynear.anmp.core.DocVectors
import app.anynear.anmp.core.PhotoTopics
import app.anynear.attachments.AttachmentFiles
import app.anynear.attachments.AttachmentKind
import app.anynear.data.FileDoc
import app.anynear.data.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/*
 * Search the phone for important documents (ADR 0071): the phone's photos or a self-chosen folder (also
 * Dropbox, Synology Drive …) are read and recognised only on the device (ADR 0067). The result is a list of
 * suggestions; filing only happens when the person accepts them.
 */

/** An image or PDF on the phone or in the chosen folder. [key] stays the same for the same photo across runs. */
class DeviceItem(val key: String, val name: String, val pdf: Boolean, val size: Long = -1) {
    /** The same file by another route (in the photos and in the folder “Bilder”): name and size, if known. */
    val alias: String? get() = if (size > 0 && name.isNotEmpty()) "n:$name:$size" else null
}

/** Where the search reads from: the phone's photos, a chosen folder or self-selected photos. */
interface DeviceDocSource {
    /** A folder (item 3): reads files that may first have to be downloaded; only on Wi-Fi. */
    val folder: Boolean

    /** All images and PDFs, newest first. */
    suspend fun items(): List<DeviceItem>

    /** Small image (about [PREVIEW_EDGE] pixels) for the pre-check and the list; `null` for PDFs or if it does not work. */
    suspend fun preview(item: DeviceItem): ByteArray?

    /** The whole file; [keep] means accept (may load from the cloud), otherwise only for reading. `null` if it does not work. */
    suspend fun bytes(item: DeviceItem, keep: Boolean): ByteArray?

    /**
     * How the source can be reopened after a restart or update ([DocSourceOpener]) as long as the access
     * exists (item 10); `null`: only for this run.
     */
    val ref: String? get() = null

    /**
     * May “Neue Dokumente von selbst finden” (ADR 0081) look at this source again later? Only sources with permanent
     * access: the photos, the whole phone, a chosen folder; not self-selected photos or files.
     */
    val watchable: Boolean get() = false

    /** Reading means downloading here (folders from cloud apps): in the background only on Wi-Fi (ADR 0081 item 5). */
    val cloud: Boolean get() = folder

    /** Give the access back (Android from 13 on for the photos, persisted folders and files, iOS for the folder). */
    fun close() {}

    companion object {
        const val PREVIEW_EDGE = 640
    }
}

/**
 * A suggestion: an image or PDF with recognised kind. [recognized]: one of the important kinds (ADR 0074). [source] is
 * the [DeviceDocSource.ref] of the source it comes from, so that “Übernehmen” reads it even after a restart.
 */
class FoundDoc(val item: DeviceItem, val doc: DocInfo, val text: String, val preview: ByteArray?, val source: String? = null) {
    val recognized: Boolean get() = DocKind.isImportant(doc.kind)
}

/** Why the search run is waiting right now. */
enum class FinderPause { BACKGROUND, BATTERY, NO_WIFI }

/** State of a search run. */
data class FinderState(
    val running: Boolean = false,
    val folder: Boolean = false,
    val checked: Int = 0,
    val total: Int = 0,
    /** Already checked earlier and therefore skipped (ADR 0071 item 7); “Alles noch einmal prüfen” includes them. */
    val skipped: Int = 0,
    val found: List<FoundDoc> = emptyList(),
    val pause: FinderPause? = null,
    val done: Boolean = false,
    /** A run was ended by a restart or update and cannot continue by itself (access is missing). */
    val interrupted: Boolean = false,
) {
    val active: Boolean get() = running || found.isNotEmpty() || done || interrupted
}

/** Reopens a source after a restart ([DeviceDocSource.ref]); `null` if the access no longer exists. */
fun interface DocSourceOpener {
    fun open(ref: String): DeviceDocSource?
}

/** Stored state of a search run (item 10): whether it was running, from where, and the suggestions. */
@Serializable
class SavedRun(
    val source: String? = null,
    val running: Boolean = false,
    val folder: Boolean = false,
    val done: Boolean = false,
    val found: List<SavedFound> = emptyList(),
)

/** A stored suggestion with kind, details, read text and small thumbnail (Base64). */
@Serializable
class SavedFound(
    val key: String,
    val name: String,
    val pdf: Boolean,
    val size: Long = -1,
    val source: String? = null,
    val doc: FileDoc,
    val text: String = "",
    val preview: String? = null,
)

/** What the search run needs on the device; replaceable in tests. */
interface FinderConditions {
    fun batteryLow(): Boolean
    fun onWifi(): Boolean

    /** Is the phone on the charging cable? Only then does the app look for new documents by itself (ADR 0081 item 5). */
    fun charging(): Boolean = true
}

/** Schedules the regular look for new documents (ADR 0081): Android WorkManager, iOS `BGProcessingTask`. */
interface FinderSchedule {
    fun set(on: Boolean)
}

object NoFinderSchedule : FinderSchedule {
    override fun set(on: Boolean) = Unit
}

/**
 * Remembers only on this phone (item 7): whether the card is gone (key-value store), which photos have already been
 * looked at without a hit and which ones were accepted or deselected (encrypted, only on this device).
 */
class FinderMemory(
    private val values: KeyValueStore,
    private val files: AttachmentFiles,
    /** Version of the reader (ADR 0078): what an older one looked at without a hit, a better one checks once more. */
    private val readerVersion: Int = 1,
) {
    /** Looked at without a hit: key and version of the reader. */
    private var checked: MutableMap<String, Int>? = null
    private var decided: MutableSet<String>? = null

    var cardDismissed: Boolean
        get() = values.getString(CARD_KEY) == "1"
        set(value) = values.putString(CARD_KEY, if (value) "1" else null)

    /** “Neue Dokumente von selbst finden” (ADR 0081): turned on? Default off. */
    var watchOn: Boolean
        get() = values.getString(WATCH_KEY) == "1"
        set(value) = values.putString(WATCH_KEY, if (value) "1" else null)

    /** The question after the first run has been answered (ADR 0081 item 2): it does not come again. */
    var watchAsked: Boolean
        get() = values.getString(WATCH_ASKED_KEY) == "1"
        set(value) = values.putString(WATCH_ASKED_KEY, if (value) "1" else null)

    /** Sources one has searched oneself and that the look-out looks at again later ([DeviceDocSource.ref]). */
    var watchSources: Set<String>
        get() = values.getString(WATCH_SOURCES_KEY)?.split('\n')?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        set(value) = values.putString(WATCH_SOURCES_KEY, value.joinToString("\n").ifEmpty { null })

    /** When “neue Dokumente gefunden” was last reported (ms); at most once a day (ADR 0081 item 6). */
    var lastNotice: Long
        get() = values.getString(NOTICE_KEY)?.toLongOrNull() ?: 0L
        set(value) = values.putString(NOTICE_KEY, value.toString())

    private fun load() {
        if (checked != null) return
        val c = mutableMapOf<String, Int>()
        val d = mutableSetOf<String>()
        files.read(BLOB)?.decodeToString()?.lineSequence()?.forEach { line ->
            val space = line.indexOf(' ')
            when {
                space < 1 -> Unit
                line.startsWith("d ") -> d += line.substring(2)
                // “c key” (version 1) or “c2 key”.
                line[0] == 'c' -> c[line.substring(space + 1)] = line.substring(1, space).toIntOrNull() ?: 1
            }
        }
        checked = c
        decided = d
    }

    /** Already looked at without a hit or already decided, also by another route: not once more. */
    fun skip(item: DeviceItem): Boolean {
        load()
        return (checked!![item.key] ?: 0) >= readerVersion || item.key in decided!! || item.alias?.let { it in decided!! } == true
    }

    fun checked(key: String) {
        load()
        checked!![key] = readerVersion
    }

    /** How many files this phone has already checked without a hit with the current reader (they are skipped). */
    fun checkedCount(): Int {
        load()
        return checked!!.values.count { it >= readerVersion }
    }

    /** “Alles noch einmal prüfen”: forgets what was looked at without a hit; accepted and deselected ones stay. */
    fun forgetChecked() {
        load()
        checked!!.clear()
        save()
    }

    fun decided(keys: Collection<String>) {
        load()
        decided!! += keys
        save()
    }

    /** State of the run and open suggestions (item 10); `null`: nothing open any more. */
    fun loadRun(): SavedRun? =
        files.read(RUN)?.decodeToString()?.let { runCatching { json.decodeFromString(SavedRun.serializer(), it) }.getOrNull() }

    fun saveRun(run: SavedRun?) {
        if (run == null) files.delete(RUN) else files.write(RUN, json.encodeToString(SavedRun.serializer(), run).encodeToByteArray())
    }

    fun save() {
        load()
        val text = buildString {
            checked!!.forEach { (key, version) -> append('c').append(version).append(' ').append(key).append('\n') }
            decided!!.forEach { append("d ").append(it).append('\n') }
        }
        files.write(BLOB, text.encodeToByteArray())
    }

    companion object {
        /** Not in `APP_BINARY_DIRS`, but deleted on reset. */
        const val DIR_NAME = "doc-finder"
        private const val BLOB = "seen"
        private const val RUN = "run"
        private val json = Json { ignoreUnknownKeys = true }
        private const val CARD_KEY = "files.find.card_dismissed"
        private const val WATCH_KEY = "files.find.watch"
        private const val WATCH_ASKED_KEY = "files.find.watch_asked"
        private const val WATCH_SOURCES_KEY = "files.find.watch_sources"
        private const val NOTICE_KEY = "files.find.watch_notice"
    }
}

/**
 * The search run (ADR 0071 items 4, 5 and 9): newest first, one image after another; in the background only as long as
 * the platform keeps it alive ([FinderKeepAlive]), otherwise only as long as the app is visible.
 * Pre-check on the small image, candidates in full size with [DocRecognizer]. Hits appear immediately.
 */
class DocFinder(
    private val scope: CoroutineScope,
    val memory: FinderMemory,
    private val read: suspend (bytes: ByteArray, kind: AttachmentKind, name: String) -> String,
    /** Topics of a photo (ADR 0070), `null`: this device cannot do it. */
    private val look: suspend (bytes: ByteArray) -> List<String>?,
    private val conditions: FinderConditions,
    /** Background for a started run (ADR 0071 item 9). */
    private val keepAlive: FinderKeepAlive = NoFinderKeepAlive,
    /** Reopens sources after a restart (item 10). */
    private val opener: DocSourceOpener = DocSourceOpener { null },
    /** The small meaning model (ADR 0078 §5) for recognition, `null` without a model. */
    private val vectors: suspend () -> DocVectors? = { null },
    /** Schedules the regular look-out (ADR 0081). */
    private val schedule: FinderSchedule = NoFinderSchedule,
    private val now: () -> Long = { 0L },
    private val log: (String) -> Unit,
) {
    private val _state = MutableStateFlow(FinderState())
    val state: StateFlow<FinderState> = _state.asStateFlow()

    private val _watchOn = MutableStateFlow(memory.watchOn)

    /** “Neue Dokumente von selbst finden” is turned on (ADR 0081). */
    val watchOn: StateFlow<Boolean> = _watchOn.asStateFlow()

    private val _watchAsked = MutableStateFlow(memory.watchAsked)

    /** The question after the first run has been answered. */
    val watchAsked: StateFlow<Boolean> = _watchAsked.asStateFlow()

    /** Is a silent look-out for new items running right now? Then a self-started run does not wait for it. */
    @kotlin.concurrent.Volatile
    private var quiet = false

    private var job: Job? = null

    /** Counts the runs; the completion of a replaced run changes nothing any more. */
    @kotlin.concurrent.Volatile
    private var runId = 0
    private var source: DeviceDocSource? = null

    /** Sources of open suggestions reopened after a restart, per [DeviceDocSource.ref]. */
    private val reopened = mutableMapOf<String, DeviceDocSource?>()
    private var mobileDataOk = false

    /** May the current run continue in the background (the platform keeps it alive)? */
    private val backgroundOn = MutableStateFlow(false)
    private var background: Boolean
        get() = backgroundOn.value
        set(value) {
            backgroundOn.value = value
        }

    /** Starts a run over [from]; a running one is ended. [visible] reports whether the app is visible. */
    /**
     * Starts a run over [from]; a running one is ended. [visible] reports whether the app is visible. Open
     * suggestions of earlier runs stay (item 10), the new run does not suggest them twice.
     */
    fun start(from: DeviceDocSource, visible: StateFlow<Boolean>, again: Boolean = false) {
        if (again) memory.forgetChecked()
        job?.cancel()
        job = null
        val id = ++runId
        val previous = source
        source = from
        // Look at it again later in case the person turns on “Neue Dokumente von selbst finden” (ADR 0081 item 4).
        from.ref?.takeIf { from.watchable }?.let { ref -> if (ref !in memory.watchSources) memory.watchSources = memory.watchSources + ref }
        mobileDataOk = false
        _state.update { FinderState(running = true, folder = from.folder, found = it.found) }
        if (previous != null && previous !== from) releaseUnused(previous)
        save()
        background = keepAlive.begin(stop = ::cancel, expired = { background = false })
        job = scope.launch(Dispatchers.Default) {
            var completed = false
            try {
                run(from, visible)
                completed = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Dokumente finden: abgebrochen (${e.message})")
            } finally {
                // A new run has replaced this one: do not overwrite its state.
                if (id == runId) {
                    memory.save()
                    _state.update { it.copy(running = false, pause = null, done = true) }
                    background = false
                    releaseUnused()
                    save()
                    keepAlive.end(_state.value.found.size, completed, visible.value)
                }
            }
        }
    }

    /**
     * At app start (item 10): show open suggestions again and continue a run that a restart or update ended,
     * as long as the access still exists. Otherwise it counts as interrupted; the suggestions stay.
     */
    fun restore(visible: StateFlow<Boolean>) {
        // Turned on (ADR 0081): make sure the regular look-out is scheduled.
        if (memory.watchOn) schedule.set(true)
        val run = memory.loadRun() ?: return
        val found = run.found.map { it.toFound() }
        _state.value = FinderState(folder = run.folder, found = found, done = !run.running, interrupted = run.running)
        if (!run.running) return
        val from = run.source?.let(opener::open)
        if (from == null) {
            log("Dokumente finden: Lauf nach Neustart unterbrochen, kein Zugriff mehr")
            return
        }
        log("Dokumente finden: Lauf nach Neustart fortgesetzt")
        start(from, visible)
    }

    /**
     * Catch up on the background for a running run if the platform allows it now (item 10): Android only lets
     * the service start from the receiver for update and phone restart, not already at app start.
     */
    fun keepGoingInBackground() {
        if (!_state.value.running || background) return
        background = keepAlive.begin(stop = ::cancel, expired = { background = false })
    }

    /** “Trotzdem suchen” without Wi-Fi. */
    fun allowMobileData() {
        mobileDataOk = true
    }

    /** “Abbrechen”: stops, the hits stay. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /** Close the list: end the run, give the access back, forget the state. */
    fun stop() {
        cancel()
        job = null
        source?.close()
        source = null
        reopened.values.forEach { it?.close() }
        reopened.clear()
        _state.value = FinderState()
        memory.saveRun(null)
    }

    /** Whole file of a suggestion for accepting; after a restart from the reopened source. */
    suspend fun bytesFor(found: FoundDoc): ByteArray? {
        val current = source
        val from = when {
            current != null && (found.source == null || found.source == current.ref) -> current
            found.source != null -> reopened.getOrPut(found.source) { opener.open(found.source) }
            else -> null
        }
        return from?.bytes(found.item, keep = true)
    }

    /** Remembers accepted and deselected suggestions so that a later run does not show them again. */
    fun decided(items: Collection<DeviceItem>) {
        memory.decided(items.flatMap { listOfNotNull(it.key, it.alias) })
        val keys = items.map { it.key }.toSet()
        _state.update { s -> s.copy(found = s.found.filter { it.item.key !in keys }) }
        releaseUnused()
        save()
    }

    /**
     * Gives back the access to sources that no open suggestion and no running run needs any more (items 2, 3
     * and 10): as long as suggestions are open, it stays so that “Übernehmen” can read them even after a restart.
     */
    private fun releaseUnused(extra: DeviceDocSource? = null) {
        val needed = _state.value.found.mapTo(mutableSetOf()) { it.source }
        extra?.takeIf { it.ref !in needed }?.let(::giveBack)
        reopened.entries.removeAll { (ref, s) -> (ref !in needed).also { gone -> if (gone) s?.let(::giveBack) } }
        val current = source
        if (current != null && !_state.value.running && current.ref !in needed) {
            giveBack(current)
            source = null
        }
    }

    /**
     * Give the access back, unless the look-out for new items needs it (ADR 0081 item 7): as long as it is on, and
     * until the question after the first run has been answered (otherwise Android would already have revoked the
     * photo permission).
     */
    private fun giveBack(s: DeviceDocSource) {
        if (s.watchable && (memory.watchOn || !memory.watchAsked)) return
        s.close()
    }

    /**
     * Turns “Neue Dokumente von selbst finden” on or off (ADR 0081 items 2 and 3); also answers the question.
     * Off gives back the access to the remembered sources, as far as no open suggestion still needs it.
     */
    fun setWatch(on: Boolean) {
        memory.watchOn = on
        memory.watchAsked = true
        _watchOn.value = on
        _watchAsked.value = true
        schedule.set(on)
        if (on) return
        val needed = _state.value.found.mapTo(mutableSetOf()) { it.source }
        val current = source?.takeIf { _state.value.running }?.ref
        for (ref in memory.watchSources) {
            if (ref in needed || ref == current) continue
            opener.open(ref)?.close()
        }
    }

    /**
     * Silently looks for new documents (ADR 0081): across the remembered sources, only what has not been checked yet,
     * only on the charging cable, cloud folders only on Wi-Fi. New suggestions join the open ones; a notice is given
     * at most once a day. Returns the number of new suggestions. If a self-started run is running right now, it waits
     * until next time.
     */
    suspend fun checkNew(): Int {
        if (!memory.watchOn || quiet || _state.value.running) return 0
        quiet = true
        var fresh = 0
        try {
            if (_state.value.found.isEmpty()) memory.loadRun()?.found?.let { saved -> _state.update { it.copy(found = saved.map { f -> f.toFound() }) } }
            sources@ for (ref in memory.watchSources) {
                val from = opener.open(ref) ?: continue
                if (from.cloud && !conditions.onWifi()) continue
                val known = _state.value.found.mapTo(mutableSetOf()) { it.item.key }
                for (item in from.items()) {
                    if (item.key in known || memory.skip(item)) continue
                    // A self-started run takes precedence; charging cable unplugged or low battery ends the look-out.
                    if (_state.value.running || !conditions.charging() || conditions.batteryLow()) break@sources
                    if (from.cloud && !conditions.onWifi()) continue@sources
                    val found = check(from, item)
                    if (found != null) {
                        _state.update { it.copy(found = it.found + found) }
                        fresh++
                        save()
                    } else {
                        memory.checked(item.key)
                    }
                }
            }
        } finally {
            memory.save()
            quiet = false
        }
        if (fresh > 0 && now() - memory.lastNotice >= NOTICE_GAP) {
            memory.lastNotice = now()
            keepAlive.foundNew(fresh)
        }
        return fresh
    }

    /** Save the state (item 10); without a run and without suggestions nothing is left behind. */
    private fun save() {
        val s = _state.value
        if (!s.running && s.found.isEmpty() && !s.interrupted) return memory.saveRun(null)
        memory.saveRun(SavedRun(source?.ref, s.running, s.folder, s.done, s.found.map { it.toSaved() }))
    }

    private suspend fun run(from: DeviceDocSource, visible: StateFlow<Boolean>) {
        val known = _state.value.found.mapTo(mutableSetOf()) { it.item.key }
        val items = from.items().filter { it.key !in known }
        val all = items.filter { !memory.skip(it) }
        // Whoever searches the whole phone and sees only a few files should know why (ADR 0071 item 7).
        _state.update { it.copy(total = all.size, skipped = items.size - all.size) }
        var sinceSave = 0
        for (item in all) {
            waitUntilReady(from, visible)
            val found = check(from, item)
            if (found != null) {
                _state.update { it.copy(found = it.found + found) }
                save()
            } else {
                memory.checked(item.key)
            }
            _state.update { it.copy(checked = it.checked + 1) }
            _state.value.let { keepAlive.progress(it.checked, it.total, it.found.size, null) }
            if (++sinceSave >= SAVE_EVERY) {
                memory.save()
                save()
                sinceSave = 0
            }
        }
    }

    private suspend fun waitUntilReady(from: DeviceDocSource, visible: StateFlow<Boolean>) {
        while (true) {
            // If the run may not continue in the background (ADR 0071 item 9), do not check every few seconds (battery)
            // but wait until the app is visible again. If it continues in the background, the conditions below apply.
            if (!visible.value && !background) {
                _state.update { it.copy(pause = FinderPause.BACKGROUND) }
                keepAlive.progress(_state.value.checked, _state.value.total, _state.value.found.size, FinderPause.BACKGROUND)
                // Continue as soon as the app is visible or the platform does allow the background after all.
                combine(visible, backgroundOn) { shown, held -> shown || held }.first { it }
            }
            val pause = when {
                !visible.value && !background -> FinderPause.BACKGROUND
                conditions.batteryLow() -> FinderPause.BATTERY
                from.folder && !mobileDataOk && !conditions.onWifi() -> FinderPause.NO_WIFI
                else -> null
            }
            _state.update { it.copy(pause = pause) }
            if (pause != null) _state.value.let { keepAlive.progress(it.checked, it.total, it.found.size, pause) }
            if (pause == null) return
            delay(WAIT_MILLIS)
        }
    }

    /** Suggestion or `null`. Images with hardly any text do not go through text recognition in full size. */
    internal suspend fun check(from: DeviceDocSource, item: DeviceItem): FoundDoc? = try {
        if (item.pdf) {
            val bytes = from.bytes(item, keep = false) ?: return null
            val text = read(bytes, AttachmentKind.PDF, item.name)
            val doc = DocRecognizer.recognizeWith(item.name, text, false, vectors())
            // Only suggest important documents (ADR 0074 item 6).
            FoundDoc(item, doc, text, null, from.ref).takeIf { it.recognized }
        } else {
            val preview = from.preview(item)
            // If the small image surely shows a scene (animals, nature, food …), it is not a document; that saves the
            // slow text recognition. “Menschen” does not count: ID cards show a face.
            if (preview != null && look(preview).orEmpty().any { it in SCENES }) return null
            val quick = preview?.let { read(it, AttachmentKind.IMAGE, item.name) }
            if (quick != null && letters(quick) < MIN_LETTERS) return null
            val bytes = from.bytes(item, keep = false) ?: preview ?: return null
            val text = read(bytes, AttachmentKind.IMAGE, item.name)
            if (letters(text) < MIN_LETTERS) return null
            val doc = DocRecognizer.recognizeWith(item.name, text, true, vectors())
            FoundDoc(item, doc, text, preview, from.ref).takeIf { it.recognized }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("Dokumente finden: ein Bild nicht gelesen (${e.message})")
        null
    }

    companion object {
        /** Below this many letters an image is not a document (ADR 0071 item 4, about 80 characters). */
        const val MIN_LETTERS = 80
        private val SCENES = setOf(
            PhotoTopics.ANIMALS, PhotoTopics.NATURE, PhotoTopics.FOOD, PhotoTopics.VEHICLES, PhotoTopics.SPORT, PhotoTopics.PARTY,
        )
        private const val SAVE_EVERY = 50

        /** A little less than a day so that a notice does not slip to the next day because of a few minutes. */
        private const val NOTICE_GAP = 20L * 60 * 60 * 1000
        private const val WAIT_MILLIS = 2_000L

        private fun letters(text: String) = text.count { it.isLetter() }
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun FoundDoc.toSaved() = SavedFound(
    item.key, item.name, item.pdf, item.size, source, FileDoc.of(doc), text, preview?.let { Base64.encode(it) },
)

@OptIn(ExperimentalEncodingApi::class)
private fun SavedFound.toFound() = FoundDoc(
    DeviceItem(key, name, pdf, size), doc.toInfo(), text, preview?.let { runCatching { Base64.decode(it) }.getOrNull() }, source,
)
