package app.anynear.app

import app.anynear.data.KeyValueStore
import app.anynear.mesh.NotReadyException
import app.anynear.mesh.RadioCheckReply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Last result of a radio check for a radio (ADR 0053). [answered] `false`: no answer within the waiting time. */
@Serializable
data class RadioCheckResult(
    val nodeNum: Int,
    val atMillis: Long,
    val answered: Boolean,
    val hops: Int? = null,
    val snr: Float? = null,
    val rssi: Int? = null,
    /** Duration until the answer. */
    val durationMillis: Long? = null,
)

/** Signal level for „Mehr dazu“ (ADR 0043, proposal for SHORT_SLOW; set the thresholds on hardware). */
enum class SignalLevel {
    GOOD, MEDIUM, WEAK;

    companion object {
        fun of(snr: Float): SignalLevel = when {
            snr >= 0f -> GOOD
            snr >= -7.5f -> MEDIUM
            else -> WEAK
        }
    }
}

/**
 * Radio check (ADR 0053): checks at the push of a button whether other people's radios answer over radio, with upper
 * limits for the shared airtime. Per radio at most one check every [Limits.perRadio], one group check (circle, all
 * contacts) per group at most every [Limits.perGroup], between two packets at least [Limits.gap]. Never in the
 * background on its own. The last result per radio stays stored.
 */
class RadioChecks(
    private val scope: CoroutineScope,
    private val store: KeyValueStore,
    private val nowMillis: () -> Long,
    /** Sends a check to the radio and waits for the answer; `null` = no answer. */
    private val send: suspend (nodeNum: Int, timeout: Duration) -> RadioCheckReply?,
    private val limits: Limits = Limits(),
    private val log: (String) -> Unit = {},
) {
    class Limits(
        val perRadio: Duration = 10.minutes,
        val perGroup: Duration = 1.hours,
        val gap: Duration = 10.seconds,
        val timeout: Duration = 60.seconds,
    )

    @Serializable
    private data class Saved(val results: List<RadioCheckResult> = emptyList(), val groups: Map<String, Long> = emptyMap())

    private val saved = load()
    private val _results = MutableStateFlow(saved.results.associateBy { it.nodeNum })

    /** Last result per radio. */
    val results: StateFlow<Map<Int, RadioCheckResult>> = _results.asStateFlow()

    private val _running = MutableStateFlow<Set<Int>>(emptySet())

    /** Radios that are being checked right now or are waiting in the queue. */
    val running: StateFlow<Set<Int>> = _running.asStateFlow()

    private val _groups = MutableStateFlow(saved.groups)

    private val _runningGroups = MutableStateFlow<Set<String>>(emptySet())

    /** Groups with a running group check. */
    val runningGroups: StateFlow<Set<String>> = _runningGroups.asStateFlow()

    /** When a group check last began per group; as a flow so that the UI sees the lock. */
    val groupStarts: StateFlow<Map<String, Long>> = _groups.asStateFlow()

    private val turn = Mutex()
    private var lastSentMillis: Long? = null

    /** From when [nodeNum] may be checked again; `null` = now. */
    fun nextCheckAt(nodeNum: Int, now: Long = nowMillis()): Long? =
        _results.value[nodeNum]?.let { it.atMillis + limits.perRadio.inWholeMilliseconds }?.takeIf { it > now }

    /** From when the group check [group] is allowed again; `null` = now. */
    fun nextGroupCheckAt(group: String, now: Long = nowMillis()): Long? =
        _groups.value[group]?.let { it + limits.perGroup.inWholeMilliseconds }?.takeIf { it > now }

    /** Checks the radios of a person that are currently allowed. @return `false` if none was allowed. */
    fun check(nodes: List<Int>): Boolean {
        val due = dueOf(nodes)
        if (due.isEmpty()) return false
        run(due, group = null)
        return true
    }

    /**
     * Group check over [nodes] (circle [group] or all contacts). Radios with a fresh result keep it.
     * @return `false` if the group is still locked.
     */
    fun checkGroup(group: String, nodes: List<Int>): Boolean {
        if (nextGroupCheckAt(group) != null || group in _runningGroups.value) return false
        _groups.update { it + (group to nowMillis()) }
        save()
        run(dueOf(nodes), group)
        return true
    }

    private fun dueOf(nodes: List<Int>): List<Int> =
        nodes.distinct().filter { it != 0 && it !in _running.value && nextCheckAt(it) == null }

    private fun run(nodes: List<Int>, group: String?) {
        _running.update { it + nodes }
        if (group != null) _runningGroups.update { it + group }
        scope.launch {
            try {
                for ((i, node) in nodes.withIndex()) {
                    val ok = turn.withLock { checkOne(node) }
                    if (!ok) {
                        // Radio no longer connected: do not check the rest, do not invent a result.
                        _running.update { it - nodes.drop(i).toSet() }
                        break
                    }
                }
            } finally {
                _running.update { it - nodes.toSet() }
                if (group != null) _runningGroups.update { it - group }
            }
        }
    }

    /** @return `false` if the own radio was not ready. */
    private suspend fun checkOne(node: Int): Boolean {
        lastSentMillis?.let { last ->
            val wait = last + limits.gap.inWholeMilliseconds - nowMillis()
            if (wait > 0) delay(wait)
        }
        val started = nowMillis()
        lastSentMillis = started
        val reply = try {
            send(node, limits.timeout)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NotReadyException) {
            log("Funkcheck: Funkgerät nicht bereit (${e.message})")
            return false
        } catch (e: Exception) {
            log("Funkcheck abgebrochen: ${e.message}")
            return false
        }
        val now = nowMillis()
        val result = if (reply == null) {
            RadioCheckResult(node, now, answered = false)
        } else {
            RadioCheckResult(node, now, answered = true, hops = reply.hops, snr = reply.snr, rssi = reply.rssi, durationMillis = now - started)
        }
        log("Funkcheck: ${if (reply == null) "keine Antwort" else "Antwort, hops=${reply.hops}"}")
        _results.update { it + (node to result) }
        _running.update { it - node }
        save()
        return true
    }

    private fun load(): Saved =
        store.getString(KEY)?.let { runCatching { json.decodeFromString(Saved.serializer(), it) }.getOrNull() } ?: Saved()

    private fun save() {
        val now = nowMillis()
        // Nobody needs old locks of the groups any more.
        val groups = _groups.value.filterValues { now - it < limits.perGroup.inWholeMilliseconds }
        store.putString(KEY, json.encodeToString(Saved.serializer(), Saved(_results.value.values.toList(), groups)))
    }

    private companion object {
        const val KEY = "radioCheck.results"
        val json = Json { ignoreUnknownKeys = true }
    }
}
