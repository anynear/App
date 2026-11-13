package app.anynear.app

import app.anynear.anmp.core.CircleState
import app.anynear.anmp.core.AnmpCore
import app.anynear.attachments.AttachmentFiles
import app.anynear.data.KeyValueStore
import app.anynear.data.PersonalFiles
import app.anynear.data.SharedStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The storages of the circles (ADR 0049): per circle the same [PersonalFiles] as “Meine Ablage”, with its own
 * directory and its own sync ([PersonalFilesSync] with the circle's space). All files lie in [files] (IDs are
 * random, hence unique). If one leaves a circle or is removed, its storage silently disappears from the phone;
 * online it stays for the others.
 */
class CircleFiles(
    private val scope: CoroutineScope,
    private val core: AnmpCore,
    private val store: KeyValueStore,
    private val files: AttachmentFiles,
    private val now: () -> Long,
    /** Reads and recognises the circles' files too (ADR 0067). */
    private val docReading: DocReading? = null,
    /** Usage across all storages (ADR 0079). */
    private val storage: SharedStorage? = null,
    private val log: (String) -> Unit,
) {
    private val open = mutableMapOf<String, Pair<PersonalFiles, PersonalFilesSync>>()
    private val reading = mutableMapOf<String, Job>()
    private val _byCircle = MutableStateFlow<Map<String, PersonalFiles>>(emptyMap())

    /** Storage per circle ID, only for circles one is in. */
    val byCircle: StateFlow<Map<String, PersonalFiles>> = _byCircle.asStateFlow()

    fun start() {
        scope.launch(Dispatchers.Default) { core.circles.collect(::update) }
    }

    fun of(circleId: String): PersonalFiles? = _byCircle.value[circleId]

    /** Sync right away, for example when opening a circle's storage. */
    fun syncNow(circleId: String) {
        open[circleId]?.second?.syncNow()
    }

    private fun update(circles: List<CircleState>) {
        val active = circles.filter { !it.removed }.map { it.id }.toSet()
        for (id in active - open.keys) {
            val list = PersonalFiles(store, files, key = keyOf(id), now = now)
            val sync = PersonalFilesSync(scope, core, list, PersonalFilesSync.Remote.circle(core, id), shared = true, storage = storage, log = log)
            sync.start()
            open[id] = list to sync
            docReading?.watch(list)?.let { reading[id] = it }
        }
        for (id in open.keys - active) {
            val (list, sync) = open.remove(id) ?: continue
            reading.remove(id)?.cancel()
            sync.stop()
            list.clear()
        }
        // Also circles that were left while the app was not running.
        val known = store.getString(IDS_KEY)?.split('\n')?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        for (id in known - active) PersonalFiles(store, files, key = keyOf(id), now = now).clear()
        if (known != active) store.putString(IDS_KEY, active.joinToString("\n").ifEmpty { null })
        _byCircle.value = open.mapValues { it.value.first }
    }

    companion object {
        const val DIR_NAME = "circle-files"
        private const val IDS_KEY = "circle.files.ids"

        private fun keyOf(circleId: String) = "circle.files.$circleId"
    }
}
