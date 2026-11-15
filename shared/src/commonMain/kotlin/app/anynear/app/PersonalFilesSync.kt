package app.anynear.app

import app.anynear.anmp.core.BlobResult
import app.anynear.anmp.core.CoreEvent
import app.anynear.anmp.core.AnmpCore
import app.anynear.anmp.core.StorageFullException
import app.anynear.anmp.core.StoredFile
import app.anynear.anmp.core.TooLargeException
import app.anynear.data.FileDoc
import app.anynear.data.FileTitleData
import app.anynear.data.OnlineProblem
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.AttachmentLimits
import app.anynear.attachments.shrinkImage
import app.anynear.data.PersonalFile
import app.anynear.data.PersonalFiles
import app.anynear.data.SharedStorage
import app.anynear.data.StoreSpace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes

/**
 * Keeps “Meine Ablage” in sync with the own space on the relays (ADR 0041): new and renamed items go up,
 * deleted ones are deleted there, what only lies there (e.g. on a new device) comes
 * down. Runs after the start, after every change and every [INTERVAL] minutes (in the background every
 * [BACKGROUND_INTERVAL]); without internet it waits. What
 * did not go up (too large, not accepted) is retried by every sync; the file page shows the reason.
 *
 * The storage of a circle (ADR 0049) is synced by the same class with the circle's [remote], [shared]: if a file
 * that was already online is missing there, someone else in the circle has deleted it; it then disappears here too
 * instead of going up again.
 */
internal class PersonalFilesSync(
    private val scope: CoroutineScope,
    private val core: AnmpCore,
    private val files: PersonalFiles,
    private val remote: Remote = Remote.personal(core),
    private val shared: Boolean = false,
    /** Usage across all storages (ADR 0079), refreshed after every sync. */
    private val storage: SharedStorage? = null,
    private val log: (String) -> Unit,
) : PersonalFiles.Listener {
    /** Where the files lie online: the own space (“Meine Ablage”) or that of a circle. */
    interface Remote {
        /** Ref of [app.anynear.anmp.core.CoreEvent.Stored]; `null`: not possible right now (e.g. no longer in the circle). */
        suspend fun put(file: PersonalFile, bytes: ByteArray): String?

        /** Change name and details of [file] without uploading again (ADR 0067); `null`: not possible this way. */
        suspend fun update(online: StoredFile, file: PersonalFile): String?
        suspend fun list(): List<StoredFile>?
        suspend fun fetch(f: StoredFile): BlobResult
        suspend fun delete(file: String): Boolean

        /** Into the bin (ADR 0086) or back; `false`: not possible right now. */
        suspend fun trash(file: String): Boolean
        suspend fun restore(file: String): Boolean

        companion object {
            fun personal(core: AnmpCore) = object : Remote {
                override suspend fun put(file: PersonalFile, bytes: ByteArray) =
                    core.storePut(file.id, bytes, file.name, file.attachmentKind.wire, file.addedAt, file.width, file.height, file.doc?.toInfo(), file.title?.toInfo())
                override suspend fun update(online: StoredFile, file: PersonalFile) = core.storeUpdate(online, file.name, file.doc?.toInfo(), file.title?.toInfo())
                override suspend fun list() = core.storeList()
                override suspend fun fetch(f: StoredFile) = core.storeFetch(f)
                override suspend fun delete(file: String) = core.storeDelete(file)
                override suspend fun trash(file: String) = core.storeTrash(file)
                override suspend fun restore(file: String) = core.storeRestore(file)
            }

            fun circle(core: AnmpCore, circleId: String) = object : Remote {
                override suspend fun put(file: PersonalFile, bytes: ByteArray) =
                    core.circleStorePut(circleId, file.id, bytes, file.name, file.attachmentKind.wire, file.addedAt, file.width, file.height, file.doc?.toInfo(), file.title?.toInfo())
                override suspend fun update(online: StoredFile, file: PersonalFile) = core.circleStoreUpdate(circleId, online, file.name, file.doc?.toInfo(), file.title?.toInfo())
                override suspend fun list() = core.circleStoreList(circleId)
                override suspend fun fetch(f: StoredFile) = core.circleStoreFetch(circleId, f)
                override suspend fun delete(file: String) = core.circleStoreDelete(circleId, file)
                override suspend fun trash(file: String) = core.circleStoreTrash(circleId, file)
                override suspend fun restore(file: String) = core.circleStoreRestore(circleId, file)
            }
        }
    }

    private val jobs = mutableListOf<Job>()

    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Upload is running: ref from the core → file ID. */
    private val uploading = mutableMapOf<String, String>()

    /** Details and title that go up with the running upload of a file ID (ADR 0067, 0091). */
    private val sendingDoc = mutableMapOf<String, Pair<FileDoc?, FileTitleData?>>()

    /** Entries from the last listing so that opening an online-only file does not have to list everything first. */
    private var lastOnline: Map<String, StoredFile> = emptyMap()

    fun start() {
        files.listener = this
        jobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            core.events.collect { e ->
                when (e) {
                    is CoreEvent.Stored -> uploadDone(e.ref, ok = true)
                    is CoreEvent.SendFailed -> uploadDone(e.ref, ok = false)
                    else -> Unit
                }
            }
        }
        jobs += scope.launch {
            // Only once the core is running (identity loaded).
            core.identity.first { it != null }
            while (true) {
                runCatching { sync() }.onFailure { if (it is CancellationException) throw it; log("Ablage: ${it.message}") }
                // Less often in the background (battery); back in the foreground right away, changes immediately anyway.
                val interval = if (core.inBackground.value) BACKGROUND_INTERVAL else INTERVAL
                withTimeoutOrNull(interval) {
                    merge(wake.receiveAsFlow(), core.inBackground.drop(1).filter { !it }.map { }).first()
                }
            }
        }
    }

    /** Ends the sync (circle left). */
    fun stop() {
        files.listener = null
        jobs.forEach { it.cancel() }
    }

    /** Sync right away, for example when opening the storage. */
    fun syncNow() {
        wake.trySend(Unit)
    }

    override fun changed(file: PersonalFile) {
        // Online only (ADR 0080): renaming works without the file, as a new entry at the next sync.
        if (file.onlineOnly) wake.trySend(Unit) else scope.launch { upload(file) }
    }

    override fun wantOnDevice(file: PersonalFile) {
        wake.trySend(Unit)
    }

    override suspend fun fetch(file: PersonalFile): ByteArray? {
        val online = lastOnline[file.id] ?: remote.list()?.also { list -> lastOnline = list.associateBy { it.file } }?.firstOrNull { it.file == file.id }
            ?: return null
        return (runCatching { remote.fetch(online) }.getOrNull() as? BlobResult.Found)?.plaintext
    }

    override fun deleted(id: String) {
        wake.trySend(Unit)
    }

    override fun docChanged(file: PersonalFile) {
        wake.trySend(Unit)
    }

    override fun binChanged(file: PersonalFile) {
        wake.trySend(Unit)
    }

    private suspend fun sync() {
        withContext(Dispatchers.Default) { files.purgeExpired() }
        for (id in files.pendingDeletes) if (remote.delete(id)) files.deleteDone(id)
        // Bin (ADR 0086): what was put in or taken out here goes up first.
        for (local in files.all.value.filter { it.binPending }) {
            if (local.inBin) remote.trash(local.id) else remote.restore(local.id)
        }
        val online = remote.list() ?: return
        core.storageUsage()?.let { storage?.set(StoreSpace(it.used, it.quota)) }
        val byId = online.associateBy { it.file }
        lastOnline = byId
        val deleted = files.pendingDeletes
        for (local in files.all.value) {
            val r = byId[local.id]
            // Bin (ADR 0086): the own state has arrived up there, or the one from there applies.
            if (r != null && local.binPending && local.inBin == (r.trashed > 0)) {
                withContext(Dispatchers.Default) { files.binSent(local.id, local.inBin, r.purgeAt) }
            } else if (r != null && !local.binPending && local.inBin != (r.trashed > 0)) {
                withContext(Dispatchers.Default) { files.binFromOnline(local.id, r.trashed, r.purgeAt) }
            }
            when {
                // In the bin and no longer up there: expired or permanently deleted, do not upload again.
                r == null && local.inBin -> withContext(Dispatchers.Default) { files.deletedOnline(local.id) }
                // Otherwise nothing with files in the bin: uploading would take them out again up there.
                local.inBin -> Unit
                // Deleted in a circle by someone else: was already online and no longer is.
                r == null && shared && local.online -> withContext(Dispatchers.Default) { files.deletedOnline(local.id) }
                // Online only (ADR 0080) and no longer there: deleted on another device; uploading is not possible anyway.
                r == null && local.onlineOnly -> withContext(Dispatchers.Default) { files.deletedOnline(local.id) }
                r == null -> if (local.id !in uploading.values) upload(local)
                // Online-only file renamed here (ADR 0080): new entry without the file.
                !local.online && local.onlineOnly -> if (local.id !in uploading.values) sendDoc(r, local)
                // Renamed here, the new name is not up there yet.
                !local.online && r.name != local.name -> Unit
                // Changed here (e.g. rotated), the new version is not up there yet: do not overwrite from there.
                !local.online && contentDiffers(local, r) -> if (local.id !in uploading.values) upload(local)
                // Kind or details changed here (ADR 0067): only write a new entry, without uploading again.
                local.docPending -> if (local.id !in uploading.values) sendDoc(r, local)
                // Renamed later on another device with the same storage: the newer name applies.
                else -> {
                    // Changed on another device or by someone in the circle (e.g. rotated): fetch the new version.
                    if (contentDiffers(local, r)) takeContent(local, r)
                    files.markOnline(local.id, true, r.name.takeIf { it != local.name }, since = r.changed, relays = r.relays, operators = r.operators)
                    // Kind and details from there (other device, someone in the circle) as soon as they differ.
                    val theirs = r.doc?.let(FileDoc::of)
                    if (theirs != null && theirs != local.doc) files.docFromOnline(local.id, theirs)
                    // Title from there (ADR 0091); none there (taken back, or lost through an older app): none here
                    // either, reading then makes it again.
                    val theirTitle = r.title?.let(FileTitleData::of)
                    if (theirTitle != local.title) files.titleFromOnline(local.id, theirTitle)
                }
            }
        }
        files.checked()
        // Kept on the device again (ADR 0080) or on a new device: fetch missing files.
        for (local in files.all.value) {
            if (local.onlineOnly || files.onDevice(local.id)) continue
            val r = byId[local.id] ?: continue
            when (val got = remote.fetch(r)) {
                is BlobResult.Found -> withContext(Dispatchers.Default) { files.downloaded(local.id, got.plaintext) }
                else -> log("Ablage: ${local.id.take(8)} noch nicht ladbar")
            }
        }
        val known = files.all.value.map { it.id }.toSet()
        for (r in online) {
            if (r.file in known || r.file in deleted) continue
            val kind = AttachmentKind.entries.firstOrNull { it.wire == r.type && it != AttachmentKind.AUDIO } ?: continue
            when (val got = remote.fetch(r)) {
                is BlobResult.Found -> withContext(Dispatchers.Default) {
                    files.addFromOnline(
                        PersonalFile(
                            r.file, r.name, kind.name, r.size, r.added, r.width, r.height, online = true, onlineSince = r.changed,
                            relays = r.relays, operators = r.operators, everSecured = r.secured, doc = r.doc?.let(FileDoc::of), trashedAt = r.trashed, purgeAt = r.purgeAt,
                            title = r.title?.let(FileTitleData::of),
                        ),
                        got.plaintext,
                    )
                }
                else -> log("Ablage: ${r.file.take(8)} noch nicht ladbar")
            }
        }
    }

    /** The version up there has different content than the one here (size or dimensions, a rotated photo has both new). */
    private fun contentDiffers(local: PersonalFile, online: StoredFile): Boolean =
        online.size != local.size || online.width != local.width || online.height != local.height

    private suspend fun takeContent(local: PersonalFile, online: StoredFile) {
        val got = remote.fetch(online) as? BlobResult.Found ?: return log("Ablage: neue Fassung von ${local.id.take(8)} noch nicht ladbar")
        withContext(Dispatchers.Default) {
            // Online only (ADR 0080): only a new thumbnail as when switching (only photos are changed).
            val thumbnail = if (local.onlineOnly && local.attachmentKind == AttachmentKind.IMAGE) {
                shrinkImage(got.plaintext, AttachmentLimits.THUMBNAIL_EDGE, AttachmentLimits.THUMBNAIL_QUALITY)?.bytes
            } else {
                null
            }
            files.contentFromOnline(local.id, online.size, online.width, online.height, got.plaintext, thumbnail)
        }
    }

    private suspend fun upload(file: PersonalFile) {
        val bytes = withContext(Dispatchers.Default) { files.bytes(file.id) } ?: return
        val ref = runCatching {
            remote.put(file, bytes)
        }.getOrElse {
            if (it is CancellationException) throw it
            if (it is TooLargeException) files.setOnlineProblem(file.id, OnlineProblem.TOO_LARGE)
            if (it is StorageFullException) files.setOnlineProblem(file.id, OnlineProblem.STORAGE_FULL)
            log("Ablage: ${file.id.take(8)} nicht hochgeladen (${it.message})")
            return
        } ?: return
        if (files.onlineProblems.value[file.id] in setOf(OnlineProblem.TOO_LARGE, OnlineProblem.STORAGE_FULL)) files.setOnlineProblem(file.id, null)
        uploading[ref] = file.id
        sendingDoc[file.id] = file.doc to file.title
    }

    /** New kind or details of [file] to [online]; if that is not possible without a new upload, the whole file. */
    private suspend fun sendDoc(online: StoredFile, file: PersonalFile) {
        val ref = runCatching { remote.update(online, file) }.getOrElse {
            if (it is CancellationException) throw it
            log("Ablage: Angaben zu ${file.id.take(8)} nicht geschrieben (${it.message})")
            return
        } ?: return upload(file)
        uploading[ref] = file.id
        sendingDoc[file.id] = file.doc to file.title
    }

    private suspend fun uploadDone(ref: String, ok: Boolean) {
        val id = uploading.remove(ref) ?: return
        val sent = sendingDoc.remove(id)
        files.setOnlineProblem(id, if (ok) null else OnlineProblem.NOT_ACCEPTED)
        if (ok) {
            withContext(Dispatchers.Default) {
                files.markOnline(id, true)
                files.docSent(id, sent?.first, sent?.second)
            }
            // An older version (from before the renaming) is cleared away by the next sync.
            wake.trySend(Unit)
        } else {
            log("Ablage: ${id.take(8)} von keinem Relay angenommen (voll?)")
        }
    }

    companion object {
        private val INTERVAL = 15.minutes
        private val BACKGROUND_INTERVAL = 60.minutes
    }
}
