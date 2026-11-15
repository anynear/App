package app.anynear.data

import app.anynear.anmp.core.DocInfo
import app.anynear.anmp.core.FileOrder
import app.anynear.anmp.core.FileTitle
import app.anynear.anmp.core.FileTitles
import app.anynear.attachments.AttachmentFiles
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.AttachmentLimits
import app.anynear.attachments.DraftAttachment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** A file in „Meine Ablage“ (ADR 0040) or in the storage of a circle (ADR 0049). */
@Serializable
data class PersonalFile(
    /** Also the file name on the phone. */
    val id: String,
    val name: String,
    /** [AttachmentKind.name]: photos, PDF and other files (ADR 0041), no voice messages. */
    val kind: String,
    val size: Long,
    val addedAt: Long,
    val width: Int = 0,
    val height: Int = 0,
    /** Is this version also on the relays (ADR 0041)? */
    val online: Boolean = false,
    /** Since when this version has been on the relays (Unix ms), 0: not known yet. */
    val onlineSince: Long = 0,
    /** Relays (site) that had it at the last sync ([PersonalFiles.checkedAt]), empty: not known yet. */
    val relays: List<String> = emptyList(),
    /** Kind of the document and details (ADR 0067), `null`: not recognised yet. */
    val doc: FileDoc? = null,
    /** [doc] has been changed here and is not on the relays yet. */
    val docPending: Boolean = false,
    /**
     * Online only (ADR 0080): the file is not on this device, only a preview image ([PersonalFiles.thumbnail]).
     * Applies only to this device and does not go to the relays. Default `false`: on the phone and online.
     */
    val onlineOnly: Boolean = false,
    /** In the bin since (Unix ms, ADR 0086), 0: not in the bin. Still counts towards the online storage. */
    val trashedAt: Long = 0,
    /** When it finally disappears from the bin (Unix ms), 0: not in the bin. */
    val purgeAt: Long = 0,
    /** Put into the bin or taken back out, but not on the relays yet. */
    val binPending: Boolean = false,
    /** Distinct operators that had the file at the last sync (ADR 0028 §7), 0: not known yet. */
    val operators: Int = 0,
    /** Was already backed up in the network once (at least two operators); after that, fewer means "incomplete". */
    val everSecured: Boolean = false,
    /** Title (ADR 0091), `null`: none made yet. Goes up with [doc] ([docPending]). */
    val title: FileTitleData? = null,
) {
    val inBin: Boolean get() = trashedAt > 0

    /** What the app shows as its name: the title, otherwise the file name (ADR 0091 §1). */
    val shown: String get() = FileTitles.shown(title?.toInfo(), name)

    /** There is a title that differs from the file name: then the file name stands small below it. */
    val hasTitle: Boolean get() = title?.text?.isNotBlank() == true && title.text != name

    /** State in the network (ADR 0028 §7): backed up only from two operators on, not already with the first relay. */
    val netState: NetState
        get() = when {
            online && operators >= SECURED_OPERATORS -> NetState.SECURED
            online && everSecured -> NetState.INCOMPLETE
            else -> NetState.SECURING
        }

    /** What is shown under „Status im Netz“: from [netState], whether the file is online, only in the network or in the bin. */
    val netStatus: NetStatus
        get() = when {
            inBin -> NetStatus.IN_BIN
            netState == NetState.INCOMPLETE -> NetStatus.PARTLY_SPREAD
            netState == NetState.SECURED -> if (onlineOnly) NetStatus.ONLY_IN_NET else NetStatus.SPREAD
            online -> NetStatus.SPREADING
            else -> NetStatus.ON_DEVICE
        }

    val attachmentKind: AttachmentKind get() = AttachmentKind.entries.firstOrNull { it.name == kind } ?: AttachmentKind.PDF
}

/** Operators from which a file counts as backed up: at least two, target three (ADR 0028 §2, §7; as in the core). */
const val SECURED_OPERATORS = 2

/** What a file shows under „Status im Netz“ (ADR 0028 §7): own network, no cloud storage. */
enum class NetStatus {
    /** Not in the network yet (waiting for internet or was not accepted): „Nur auf diesem Handy“. */
    ON_DEVICE,

    /** In the network, but not yet two operators: „Wird verteilt“. */
    SPREADING,

    /** At least two operators: „Im Netz verteilt“. */
    SPREAD,

    /** Was distributed, now fewer than two operators: „Teilweise verteilt“. */
    PARTLY_SPREAD,

    /** Distributed and on this phone only a preview image (ADR 0080): „Nur im Netz“. */
    ONLY_IN_NET,

    /** In the bin (ADR 0086): „Im Papierkorb“. */
    IN_BIN,
}

/** State of a file in the anynear network (ADR 0028 §7). */
enum class NetState {
    /** Not yet two operators: „Wird im Netz gesichert“. */
    SECURING,

    /** At least two operators: „Im Netz gesichert“. */
    SECURED,

    /** Was backed up, now fewer than two operators: „Sicherung unvollständig“. */
    INCOMPLETE,
}

/** Stored form of [DocInfo] (ADR 0067); fields as there. */
@Serializable
data class FileDoc(
    val kind: String,
    val from: String = "",
    val date: Long = 0,
    val amountCents: Long = 0,
    val currency: String = "",
    val until: Long = 0,
    val byPerson: Boolean = false,
    val rules: Int = 0,
    /** What a photo shows (ADR 0070), empty: nothing recognised or not looked at yet. */
    val topics: List<String> = emptyList(),
    /** Version of the image recognition, 0: not looked at yet. */
    val seen: Int = 0,
) {
    fun toInfo() = DocInfo(kind, from, date, amountCents, currency, until, byPerson, rules, topics.toTypedArray(), seen)

    companion object {
        fun of(i: DocInfo) = FileDoc(i.kind, i.from, i.date, i.amountCents, i.currency, i.until, i.byPerson, i.rules, i.topics.toList(), i.seen)
    }
}

/** Stored form of [FileTitle] (ADR 0091); fields as there. */
@Serializable
data class FileTitleData(val text: String, val byPerson: Boolean = false, val rules: Int = 0) {
    fun toInfo() = FileTitle(text, byPerson, rules)

    companion object {
        fun of(t: FileTitle) = FileTitleData(t.text, t.byPerson, t.rules)
    }
}

/** Order of the files of a storage (ADR 0091 §7); chosen per device, for all storages. */
enum class FileSort {
    /** Newest first (default, as before). */
    NEWEST,

    /** Title A–Z; without a title, the file name counts. */
    TITLE,

    /** File name A–Z. */
    NAME;

    /** [files] in this order; equal ones newest first. */
    fun sort(files: List<PersonalFile>): List<PersonalFile> = when (this) {
        NEWEST -> files.sortedByDescending { it.addedAt }
        TITLE -> files.sortedWith { a, b -> FileOrder.compare(a.shown, b.shown).takeIf { it != 0 } ?: b.addedAt.compareTo(a.addedAt) }
        NAME -> files.sortedWith { a, b -> FileOrder.compare(a.name, b.name).takeIf { it != 0 } ?: b.addedAt.compareTo(a.addedAt) }
    }

    companion object {
        fun of(name: String?): FileSort = entries.firstOrNull { it.name == name } ?: NEWEST
    }
}

/** How full a storage is online (ADR 0029), the way the relays count. */
data class StoreSpace(val used: Long, val quota: Long) {
    /** Share from 0 to 1. */
    val fraction: Float get() = if (quota <= 0) 0f else (used.toDouble() / quota).coerceIn(0.0, 1.0).toFloat()

    /** Nothing new fits in safely any more (a photo is at most a few MB in size). */
    val full: Boolean get() = quota > 0 && quota - used < FULL_MARGIN

    val nearlyFull: Boolean get() = !full && fraction >= NEARLY_FULL

    companion object {
        const val NEARLY_FULL = 0.9f
        const val FULL_MARGIN = 1L shl 20
    }
}

/**
 * Used online storage across all storages together (ADR 0079): „Meine Ablage“ and, in every circle, what one has
 * stored oneself. One for the whole app, filled by every sync; `null` as long as not known.
 */
class SharedStorage {
    private val _space = MutableStateFlow<StoreSpace?>(null)
    val space: StateFlow<StoreSpace?> = _space.asStateFlow()

    fun set(space: StoreSpace?) {
        if (space != null) _space.value = space
    }

    /** Full: new things are not even accepted. */
    val full: Boolean get() = _space.value?.full == true
}

/** Why a file is not online yet, as long as the sync knows it (only until the restart). */
enum class OnlineProblem {
    /** Larger than the relays currently accept (`max_object_bytes`). */
    TOO_LARGE,

    /** No relay accepted it, for instance because the quota is full. */
    NOT_ACCEPTED,

    /** The own online storage is full across all storages (ADR 0079). */
    STORAGE_FULL,
}

/**
 * „Meine Ablage“: on the phone (ADR 0040) and permanently on the relays (ADR 0041, the sync
 * is done by [app.anynear.app.PersonalFilesSync] via [listener]). The directory is in [store] (encrypted on the
 * device, [EncryptedKeyValueStore]) under [key], the files encrypted with the device key in
 * [files]. The storage of a circle (ADR 0049) is the same class with its own [key], see
 * [app.anynear.app.CircleFiles]. Writes blocking; call off the main thread.
 */
class PersonalFiles(
    private val store: KeyValueStore,
    private val files: AttachmentFiles,
    private val key: String = KEY,
    private val now: () -> Long,
) {
    private val deletedKey = "$key.deleted"
    private val checkedKey = "$key.checked"

    private val _all = MutableStateFlow(load())

    /** All files, also those in the bin; for the sync. */
    val all: StateFlow<List<PersonalFile>> = _all.asStateFlow()

    private val _list = MutableStateFlow(_all.value.filterNot { it.inBin })

    /** The storage without the bin, newest first. */
    val list: StateFlow<List<PersonalFile>> = _list.asStateFlow()

    private val _bin = MutableStateFlow(binOf(_all.value))

    /** Bin (ADR 0086), most recently deleted first. */
    val bin: StateFlow<List<PersonalFile>> = _bin.asStateFlow()

    private val _problems = MutableStateFlow<Map<String, OnlineProblem>>(emptyMap())

    /** Files that the sync could not upload, with the reason. It tries again by itself. */
    val onlineProblems: StateFlow<Map<String, OnlineProblem>> = _problems.asStateFlow()

    fun setOnlineProblem(id: String, problem: OnlineProblem?) {
        _problems.value = if (problem == null) _problems.value - id else _problems.value + (id to problem)
    }

    /** Is informed about changes by the user (not about those of the sync). */
    interface Listener {
        fun changed(file: PersonalFile)
        fun deleted(id: String)

        /** Put into the bin or taken back out (ADR 0086). */
        fun binChanged(file: PersonalFile) {}

        /** Only kind or details changed (ADR 0067): without uploading the file again. */
        fun docChanged(file: PersonalFile) {}

        /** Should be on the device again (ADR 0080): download. */
        fun wantOnDevice(file: PersonalFile) {}

        /** Fetch a file that is online only from the network without storing it; `null`: not possible right now. */
        suspend fun fetch(file: PersonalFile): ByteArray? = null
    }

    var listener: Listener? = null

    /** Files that still have to be deleted on the relays (deleted without internet). */
    val pendingDeletes: Set<String> get() = store.getString(deletedKey)?.split('\n')?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    /** Takes in a prepared attachment (photo shrunk, PDF checked). `null` if it is too large. */
    fun add(draft: DraftAttachment, name: String): PersonalFile? {
        if (draft.kind == AttachmentKind.AUDIO) return null
        if (draft.bytes.size > AttachmentLimits.MAX_PERSONAL_BYTES) return null
        val file = PersonalFile(
            id = Uuid.random().toString(),
            name = name.trim(),
            kind = draft.kind.name,
            size = draft.bytes.size.toLong(),
            addedAt = now(),
            width = draft.width,
            height = draft.height,
        )
        files.write(file.id, draft.bytes)
        save(listOf(file) + _all.value)
        listener?.changed(file)
        return file
    }

    /** From the sync: a file that was only on the relays. */
    fun addFromOnline(file: PersonalFile, bytes: ByteArray) {
        if (_all.value.any { it.id == file.id }) return
        files.write(file.id, bytes)
        save((_all.value + file.copy(online = true)).sortedByDescending { it.addedAt })
    }

    /**
     * From the sync: version is on the relays ([online]) or still has to get there. A newer name from there ([name]) wins;
     * [since] and [relays] say since when and where it is, [operators] at how many operators (`null`: unchanged).
     */
    fun markOnline(id: String, online: Boolean, name: String? = null, since: Long? = null, relays: List<String>? = null, operators: Int? = null) {
        val list = _all.value.map {
            if (it.id != id) return@map it
            val ops = operators ?: it.operators
            it.copy(
                online = online, name = name ?: it.name, onlineSince = since ?: it.onlineSince, relays = relays ?: it.relays,
                operators = ops, everSecured = it.everSecured || (online && ops >= SECURED_OPERATORS),
            )
        }
        if (list != _all.value) save(list)
    }

    /** When the sync last checked at the relays (Unix ms), 0: never yet. */
    val checkedAt: Long get() = store.getString(checkedKey)?.toLongOrNull() ?: 0

    /** From the sync: just checked at the relays. */
    fun checked() {
        store.putString(checkedKey, now().toString())
    }

    /** From the sync: deleted on the relays. */
    fun deleteDone(id: String) {
        store.putString(deletedKey, (pendingDeletes - id).joinToString("\n").ifEmpty { null })
    }

    fun bytes(id: String): ByteArray? = files.read(id)

    /** Is the file itself on this device? */
    fun onDevice(id: String): Boolean = files.exists(id)

    /** The file from the device or, if it is online only, from the network (ADR 0080); `null` if neither works. */
    suspend fun load(file: PersonalFile): ByteArray? = bytes(file.id) ?: listener?.fetch(file)

    /** Preview image of a file that is online only (JPEG), `null`: none. */
    fun thumbnail(id: String): ByteArray? = files.read(thumbName(id))

    /**
     * Online only (ADR 0080): takes the file off this device and keeps [thumbnail] (JPEG) as preview image. Works only
     * if it is already online; otherwise `false`.
     */
    fun setOnlineOnly(id: String, thumbnail: ByteArray?): Boolean {
        val file = _all.value.firstOrNull { it.id == id } ?: return false
        if (file.onlineOnly) return true
        if (!file.online) return false
        if (thumbnail != null) files.write(thumbName(id), thumbnail)
        save(_all.value.map { if (it.id == id) it.copy(onlineOnly = true) else it })
        files.delete(id)
        return true
    }

    /** Keep on this device again (ADR 0080); the sync downloads the file. */
    fun keepOnDevice(id: String) {
        val file = _all.value.firstOrNull { it.id == id && it.onlineOnly } ?: return
        save(_all.value.map { if (it.id == id) it.copy(onlineOnly = false) else it })
        listener?.wantOnDevice(file.copy(onlineOnly = false))
    }

    /** From the sync: the file is on the device again; the preview image is no longer needed. */
    fun downloaded(id: String, bytes: ByteArray) {
        if (_all.value.none { it.id == id && !it.onlineOnly }) return
        files.write(id, bytes)
        files.delete(thumbName(id))
    }

    /** As an attachment for a chat, even if it is online only (ADR 0080); `null` if it cannot be fetched. */
    suspend fun loadDraft(file: PersonalFile): DraftAttachment? {
        val bytes = load(file) ?: return null
        val kind = file.attachmentKind
        return DraftAttachment(kind, bytes, name = if (kind == AttachmentKind.IMAGE) "" else file.name, width = file.width, height = file.height)
    }

    /** As an attachment for a chat; `null` if the file is not on the device. */
    fun asDraft(file: PersonalFile): DraftAttachment? {
        val bytes = bytes(file.id) ?: return null
        val kind = file.attachmentKind
        return DraftAttachment(kind, bytes, name = if (kind == AttachmentKind.IMAGE) "" else file.name, width = file.width, height = file.height)
    }

    /**
     * Recognised or manually chosen kind and details (ADR 0067); they go to the relays with the next sync.
     * A manually chosen kind does not replace the recognition.
     */
    fun setDoc(id: String, doc: FileDoc) {
        val current = _all.value.firstOrNull { it.id == id } ?: return
        if (current.doc == doc) return
        if (current.doc?.byPerson == true && !doc.byPerson) return
        save(_all.value.map { if (it.id == id) it.copy(doc = doc, docPending = true) else it })
        _all.value.firstOrNull { it.id == id }?.let { listener?.docChanged(it) }
    }

    /** From the sync or without a change of the result: take over kind and details without uploading them. */
    fun docFromOnline(id: String, doc: FileDoc?) {
        val list = _all.value.map { if (it.id == id && !it.docPending) it.copy(doc = doc) else it }
        if (list != _all.value) save(list)
    }

    /** From the sync: [doc] and [title] are now on the relays. */
    fun docSent(id: String, doc: FileDoc?, title: FileTitleData? = null) {
        val list = _all.value.map { if (it.id == id && it.doc == doc && it.title == title) it.copy(docPending = false) else it }
        if (list != _all.value) save(list)
    }

    /**
     * Title made here or set by hand (ADR 0091); goes to the relays with the next sync like [setDoc]. A title set by
     * hand is not replaced by a made one; `null` or an empty one gives the title back to anynear.
     */
    fun setTitle(id: String, title: FileTitleData?) {
        val current = _all.value.firstOrNull { it.id == id } ?: return
        val clean = title?.takeIf { it.text.isNotBlank() }
        if (current.title == clean) return
        if (current.title?.byPerson == true && clean != null && !clean.byPerson) return
        save(_all.value.map { if (it.id == id) it.copy(title = clean, docPending = true) else it })
        _all.value.firstOrNull { it.id == id }?.let { listener?.docChanged(it) }
    }

    /** From the sync or without a change of the text: take over the title without uploading it. */
    fun titleFromOnline(id: String, title: FileTitleData?) {
        val list = _all.value.map { if (it.id == id && !it.docPending) it.copy(title = title) else it }
        if (list != _all.value) save(list)
    }

    /**
     * New content of the same file, for instance a rotated photo: replaces it on the device and goes up with the next
     * sync as a new version; name, kind and details stay. Only for files on the device (not online only).
     */
    fun replaceContent(id: String, draft: DraftAttachment): Boolean {
        val current = _all.value.firstOrNull { it.id == id && !it.onlineOnly } ?: return false
        if (draft.bytes.size > AttachmentLimits.MAX_PERSONAL_BYTES) return false
        files.write(id, draft.bytes)
        val changed = current.copy(size = draft.bytes.size.toLong(), width = draft.width, height = draft.height, online = false)
        save(_all.value.map { if (it.id == id) changed else it })
        listener?.changed(changed)
        return true
    }

    /**
     * From the sync: changed on another device or by someone in the circle (for instance rotated). If the file is
     * online only (ADR 0080), only the new preview image ([thumbnail]) comes instead of it.
     */
    fun contentFromOnline(id: String, size: Long, width: Int, height: Int, bytes: ByteArray?, thumbnail: ByteArray?) {
        val current = _all.value.firstOrNull { it.id == id } ?: return
        if (current.onlineOnly) {
            if (thumbnail != null) files.write(thumbName(id), thumbnail) else files.delete(thumbName(id))
        } else {
            files.write(id, bytes ?: return)
        }
        save(_all.value.map { if (it.id == id) it.copy(size = size, width = width, height = height) else it })
    }

    fun rename(id: String, name: String) {
        val clean = name.trim().takeIf { it.isNotEmpty() } ?: return
        save(_all.value.map { if (it.id == id) it.copy(name = clean, online = false) else it })
        _all.value.firstOrNull { it.id == id }?.let { listener?.changed(it) }
    }

    fun delete(id: String) {
        save(_all.value.filterNot { it.id == id })
        setOnlineProblem(id, null)
        files.delete(id)
        files.delete(thumbName(id))
        store.putString(deletedKey, (pendingDeletes + id).joinToString("\n"))
        listener?.deleted(id)
    }

    /**
     * Into the bin (ADR 0086): the file stays on the phone and online and still counts towards the storage until it
     * disappears after [BIN_DAYS] days. If it was never online, it is gone right away.
     */
    fun trash(id: String) {
        val file = _all.value.firstOrNull { it.id == id && !it.inBin } ?: return
        if (!file.online && file.onlineSince == 0L) return delete(id)
        val t = now()
        val moved = file.copy(trashedAt = t, purgeAt = t + BIN_DAYS * DAY, binPending = true)
        save(_all.value.map { if (it.id == id) moved else it })
        listener?.binChanged(moved)
    }

    /** From the bin back into the storage. */
    fun restore(id: String) {
        val file = _all.value.firstOrNull { it.id == id && it.inBin } ?: return
        val back = file.copy(trashedAt = 0, purgeAt = 0, binPending = true)
        save(_all.value.map { if (it.id == id) back else it })
        listener?.binChanged(back)
    }

    /** Finally delete everything in the bin. */
    fun emptyBin() {
        _bin.value.forEach { delete(it.id) }
    }

    /** From the sync: put into the bin or taken back out on another device or by someone in the circle. */
    fun binFromOnline(id: String, trashedAt: Long, purgeAt: Long) {
        val list = _all.value.map { if (it.id == id && !it.binPending) it.copy(trashedAt = trashedAt, purgeAt = purgeAt) else it }
        if (list != _all.value) save(list)
    }

    /** From the sync: the state of the bin is now on the relays; [purgeAt] as there. */
    fun binSent(id: String, inBin: Boolean, purgeAt: Long) {
        val list = _all.value.map { if (it.id == id && it.inBin == inBin) it.copy(binPending = false, purgeAt = if (inBin) purgeAt else 0) else it }
        if (list != _all.value) save(list)
    }

    /** Take what has expired in the bin off the phone even without internet (the relays clean up themselves). */
    fun purgeExpired() {
        val t = now()
        _all.value.filter { it.purgeAt in 1..<t }.forEach { deletedOnline(it.id) }
    }

    /** From the sync of a circle storage: someone else in the circle has deleted the file. */
    fun deletedOnline(id: String) {
        if (_all.value.none { it.id == id }) return
        save(_all.value.filterNot { it.id == id })
        setOnlineProblem(id, null)
        files.delete(id)
        files.delete(thumbName(id))
    }

    /** Take everything off the phone, without the relays (circle left); the directory itself disappears as well. */
    fun clear() {
        _all.value.forEach {
            files.delete(it.id)
            files.delete(thumbName(it.id))
        }
        _problems.value = emptyMap()
        _all.value = emptyList()
        _list.value = emptyList()
        _bin.value = emptyList()
        store.putString(key, null)
        store.putString(deletedKey, null)
        store.putString(checkedKey, null)
    }

    private fun load(): List<PersonalFile> =
        store.getString(key)?.let { runCatching { json.decodeFromString<List<PersonalFile>>(it) }.getOrNull() }.orEmpty()

    private fun save(list: List<PersonalFile>) {
        store.putString(key, json.encodeToString(list))
        _all.value = list
        _list.value = list.filterNot { it.inBin }
        _bin.value = binOf(list)
    }

    private fun binOf(list: List<PersonalFile>) = list.filter { it.inBin }.sortedByDescending { it.trashedAt }

    companion object {
        /** Preview image next to the file, in the same encrypted directory. */
        private fun thumbName(id: String) = "${id}_t"

        /** Days in the bin without a subscription, like `CoreTimings.trashKeep` in the core (ADR 0086). */
        const val BIN_DAYS = 7
        private const val DAY = 86_400_000L

        const val DIR_NAME = "personal-files"
        private const val KEY = "personal.files"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
