package app.anynear.location

import app.anynear.app.PlatformHooks
import app.anynear.data.KeyValueStore
import app.anynear.data.ObservedKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A place the phone located: degrees, when (ms) and how accurate in metres, if known (ADR 0121 §10). */
data class Place(val latitude: Double, val longitude: Double, val atMillis: Long, val accuracyMeters: Int? = null)

/**
 * How much of the own location someone else sees (ADR 0121 §3). Every level includes the ones before: with [Area] the
 * others see the area all the time and the exact place when something is wrong. [wire] is the level in ANMP.
 */
enum class ShareLevel(val id: String, val wire: Int) {
    Off("off", 0),
    WhenNeeded("when", 1),

    /** „Ungefähr immer, genau wenn etwas ist“. */
    Area("area", 2),
    Exact("exact", 3),
    ;

    /** The others see something all the time, not only when something is wrong. */
    val always: Boolean get() = this == Area || this == Exact

    companion object {
        /** `null` for nothing stored; a level a newer version set that this one does not know is at least [WhenNeeded]. */
        fun of(id: String?): ShareLevel? = when (id) {
            null, "" -> null
            else -> entries.firstOrNull { it.id == id } ?: WhenNeeded
        }

        fun ofWire(level: Int): ShareLevel = entries.firstOrNull { it.wire == level } ?: Off
    }
}

/**
 * Who sees where the person is (ADR 0121 §3, §10). The answer to the question in the setup and after the update is the
 * default for everyone ([default]); a circle can be set differently ([setLevel]). Both are settings of the person and
 * reach all own devices (ADR 0106); the permission of the system is per device ([allowed]).
 *
 * Nothing here sends a place. A check-in hands it to the core, which sends it only with „nicht bestätigt“
 * ([app.anynear.lifesigns.LifeSigns]).
 */
class LocationSharing(
    private val scope: CoroutineScope,
    private val store: KeyValueStore,
    private val hooks: PlatformHooks,
    private val now: () -> Long,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    private val _default = MutableStateFlow(ShareLevel.of(store.getString(KEY_DEFAULT)))

    /** The answer to the question; `null`: not answered yet. */
    val default: StateFlow<ShareLevel?> = _default.asStateFlow()

    private val _circles = MutableStateFlow(readCircles())

    /** Circles set differently from [default], by circle ID. */
    val circles: StateFlow<Map<String, ShareLevel>> = _circles.asStateFlow()

    private val _persons = MutableStateFlow(readMap(KEY_PERSONS))

    /** Persons set differently from their circles and the default, by person ID (ADR 0121 §2: always a counterpart). */
    val persons: StateFlow<Map<String, ShareLevel>> = _persons.asStateFlow()

    private val _until = MutableStateFlow(readUntil())

    /** Limited in time: `c:<circle>` or `p:<person>` → until when (ms) its own setting holds (ADR 0121 §3). */
    val until: StateFlow<Map<String, Long>> = _until.asStateFlow()

    private val _paused = MutableStateFlow(store.getString(KEY_PAUSED) == "1")

    /** „Alles pausieren“ (ADR 0121 §4.5): nobody sees anything until switched on again; the settings stay. */
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _source = MutableStateFlow(store.getString(KEY_SOURCE))

    /** The own device whose location counts (ADR 0121 §4.6), device ID; `null`: none chosen yet. */
    val source: StateFlow<String?> = _source.asStateFlow()

    private val _approved = MutableStateFlow(store.getString(KEY_APPROVED)?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty())

    /** Persons that may see the place all the time through a circle (ADR 0121 §5): who joins later is asked about first. */
    val approved: StateFlow<Set<String>> = _approved.asStateFlow()

    private val _postponedUntil = MutableStateFlow(store.getString(KEY_POSTPONED)?.toLongOrNull() ?: 0L)

    /** „Später“: the question stays away until then (ms). */
    val postponedUntil: StateFlow<Long> = _postponedUntil.asStateFlow()

    /** May the app read the exact location on this device? */
    val allowed: StateFlow<Boolean> get() = hooks.preciseLocationAllowed

    /** Answers from another own device arrive through the store (ADR 0106). */
    fun start() {
        val observed = store as? ObservedKeyValueStore ?: return
        scope.launch {
            observed.changes.collect { key ->
                when (key) {
                    KEY_DEFAULT -> _default.value = ShareLevel.of(store.getString(KEY_DEFAULT))
                    KEY_CIRCLES -> _circles.value = readCircles()
                    KEY_PERSONS -> _persons.value = readMap(KEY_PERSONS)
                    KEY_UNTIL -> _until.value = readUntil()
                    KEY_PAUSED -> _paused.value = store.getString(KEY_PAUSED) == "1"
                    KEY_SOURCE -> _source.value = store.getString(KEY_SOURCE)
                    KEY_APPROVED -> _approved.value = store.getString(KEY_APPROVED)?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty()
                }
            }
        }
    }

    /** What the members of [circleId] see: the circle's own setting, otherwise the default; nothing before the answer. */
    fun level(circleId: String): ShareLevel = held("c:$circleId", _circles.value[circleId]) ?: _default.value ?: ShareLevel.Off

    /** An own setting counts until its time is over. */
    private fun held(key: String, level: ShareLevel?): ShareLevel? =
        level?.takeIf { val t = _until.value[key]; t == null || t > now() }

    /**
     * What [personId] sees, who is in [circleIds] with us: nothing while paused; the person's own setting; otherwise
     * the most any of its circles gives. Seeing the place all the time through a circle needs the ok for this person
     * ([approved]); without it the person stays at „wenn etwas ist“.
     */
    fun levelFor(personId: String, circleIds: List<String>): ShareLevel {
        if (_paused.value) return ShareLevel.Off
        held("p:$personId", _persons.value[personId])?.let { return it }
        val most = circleIds.map(::level).maxByOrNull { it.wire } ?: _default.value ?: ShareLevel.Off
        return if (most.always && personId !in _approved.value) ShareLevel.WhenNeeded else most
    }

    /** Until when (ms) what [personId] sees is limited, 0 = until changed. */
    fun untilFor(personId: String, circleIds: List<String>): Long {
        held("p:$personId", _persons.value[personId])?.let { return _until.value["p:$personId"] ?: 0 }
        val level = levelFor(personId, circleIds)
        return circleIds.filter { level(it) == level }.map { _until.value["c:$it"]?.takeIf { t -> held("c:$it", _circles.value[it]) != null } ?: 0L }
            .let { times -> if (times.isEmpty() || times.any { it == 0L }) 0L else times.max() }
    }

    /** May the phone locate also after its restart, without the app being opened („Immer“, ADR 0121 §13)? */
    val background: StateFlow<Boolean> get() = hooks.backgroundLocationAllowed

    /** Asks the system for „Immer“; only offered where a level shares all the time. */
    fun allowBackground() = hooks.requestBackgroundLocation()

    /**
     * Says that a place leaves the phone for [reason] right now, with [text] for the person to see; `null` ends it.
     * While it holds, the phone keeps locating with the app in the background, and the system shows that it does.
     */
    fun inUse(reason: String, text: String?) = hooks.setLocationInUse(reason, text)

    /** Places the system delivers while [inUse] holds. */
    val placeUpdates: kotlinx.coroutines.flow.Flow<Place> get() = hooks.placeUpdates

    /** Sets what [personId] sees, apart from its circles; `null`: follow the circles again. [untilMillis] > 0 limits it. */
    fun setPersonLevel(personId: String, level: ShareLevel?, untilMillis: Long = 0) {
        val next = if (level == null) _persons.value - personId else _persons.value + (personId to level)
        writeMap(KEY_PERSONS, next)
        _persons.value = next
        setUntil("p:$personId", untilMillis.takeIf { level != null } ?: 0)
        if (level != null && level != ShareLevel.Off) askSystem(level)
    }

    /** The answer to „Tom ist neu in „Familie“. Soll er sehen, wo du bist?“ (ADR 0121 §5), also for everyone at once. */
    fun approve(personIds: Collection<String>, yes: Boolean = true) {
        val next = if (yes) _approved.value + personIds else _approved.value - personIds.toSet()
        store.putString(KEY_APPROVED, next.sorted().joinToString(",").takeIf { it.isNotEmpty() })
        _approved.value = next
        if (!yes) personIds.forEach { if (it !in _persons.value) setPersonLevel(it, ShareLevel.WhenNeeded) }
    }

    fun setPaused(paused: Boolean) {
        store.putString(KEY_PAUSED, if (paused) "1" else null)
        _paused.value = paused
    }

    /** „Mein Standort kommt von: diesem Handy“ (ADR 0121 §4.6). */
    fun setSource(deviceId: String?) {
        store.putString(KEY_SOURCE, deviceId)
        _source.value = deviceId
    }

    private fun setUntil(key: String, untilMillis: Long) {
        val next = if (untilMillis > 0) _until.value + (key to untilMillis) else _until.value - key
        if (next == _until.value) return
        store.putString(KEY_UNTIL, next.takeIf { it.isNotEmpty() }?.let { m -> json.encodeToString(mapSerializer, m.mapValues { it.value.toString() }) })
        _until.value = next
    }

    /** The system permission a level needs: the exact place for everything but the area alone. */
    private fun askSystem(level: ShareLevel) {
        if (level != ShareLevel.Off && !allowed.value) hooks.requestPreciseLocation()
    }

    /**
     * The answer to „Sollen deine Helfer sehen, wo du bist, wenn etwas ist?“ in the setup, after the update or in the
     * settings. With yes the app asks the system for the exact location, if it does not have it yet.
     */
    fun answer(level: ShareLevel) {
        store.putString(KEY_DEFAULT, level.id)
        _default.value = level
        askSystem(level)
    }

    /**
     * Sets [circleId] differently from the default; `null` or the default itself: follow the default again.
     * [untilMillis] > 0 limits it in time. [members] are the persons in the circle now: with a level that shows the
     * place all the time they are the ones it is for (ADR 0121 §5).
     */
    fun setLevel(circleId: String, level: ShareLevel?, untilMillis: Long = 0, members: Collection<String> = emptyList()) {
        val timed = untilMillis > 0
        val next = if (level == null || (level == _default.value && !timed)) _circles.value - circleId else _circles.value + (circleId to level)
        writeMap(KEY_CIRCLES, next)
        _circles.value = next
        setUntil("c:$circleId", untilMillis.takeIf { circleId in next } ?: 0)
        if (level != null && level.always) {
            approve(members)
            askSystem(level)
        }
    }

    private fun writeMap(key: String, map: Map<String, ShareLevel>) =
        store.putString(key, map.takeIf { it.isNotEmpty() }?.let { m -> json.encodeToString(mapSerializer, m.mapValues { it.value.id }) })

    /** „Später“ in the setup or on the dialog: ask again after [PAUSE_MILLIS]. */
    fun postpone() {
        val until = now() + PAUSE_MILLIS
        store.putString(KEY_POSTPONED, until.toString())
        _postponedUntil.value = until
    }

    /** Asks the system for the exact location on this device, e.g. from the settings. */
    fun allow() = hooks.requestPreciseLocation()

    /**
     * Where the phone is right now, as exact as the system allows; `null` if not allowed or not obtainable (in the
     * background the system usually gives nothing without „Immer“, ADR 0121 §10).
     */
    suspend fun place(): Place? = hooks.currentPlace()

    private fun readCircles(): Map<String, ShareLevel> = readMap(KEY_CIRCLES)

    private fun readUntil(): Map<String, Long> =
        store.getString(KEY_UNTIL)?.let { runCatching { json.decodeFromString(mapSerializer, it) }.getOrNull() }.orEmpty()
            .mapNotNull { (k, v) -> v.toLongOrNull()?.let { k to it } }.toMap()

    private fun readMap(key: String): Map<String, ShareLevel> =
        store.getString(key)?.let { runCatching { json.decodeFromString(mapSerializer, it) }.getOrNull() }.orEmpty()
            .mapNotNull { (circle, id) -> ShareLevel.of(id)?.let { circle to it } }.toMap()

    companion object {
        /** Person setting: the default level ([ShareLevel.id]). */
        const val KEY_DEFAULT = "location.share"

        /** Person setting: circle ID → level, only where it differs from the default. */
        const val KEY_CIRCLES = "location.share.circles"

        /** Person setting: person ID → level, where a person is set apart from its circles. */
        const val KEY_PERSONS = "location.share.persons"

        /** Person setting: `c:<circle>` / `p:<person>` → until when (ms) the own setting holds. */
        const val KEY_UNTIL = "location.share.until"

        /** Person setting: `1` while everything is paused. */
        const val KEY_PAUSED = "location.share.paused"

        /** Person setting: device ID of the own device whose location counts. */
        const val KEY_SOURCE = "location.share.source"

        /** Person setting: person IDs that may see the place all the time through a circle. */
        const val KEY_APPROVED = "location.share.approved"

        /** Device setting: until when the question is postponed. */
        const val KEY_POSTPONED = "location_hint_postponed_until"

        /** As long as the other postponed hints (three days). */
        const val PAUSE_MILLIS = 3 * 24 * 3_600_000L
    }
}
