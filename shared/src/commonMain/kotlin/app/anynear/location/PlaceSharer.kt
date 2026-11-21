package app.anynear.location

import kotlin.concurrent.Volatile
import app.anynear.anmp.core.AlivePosition
import app.anynear.anmp.core.AnmpCore
import app.anynear.anmp.core.CoreEvent
import app.anynear.data.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** What another person shares of its place with us (ADR 0121 §3), as last heard. */
data class SharedPlace(
    val personId: String,
    val level: ShareLevel,
    /** Where it is: the point, or with [radiusMeters] the centre of the area it is somewhere in. `null`: nothing now. */
    val place: Place? = null,
    val radiusMeters: Int? = null,
    /** Until when (ms) the level holds, 0 = until changed. */
    val untilMillis: Long = 0,
    val atMillis: Long = 0,
    /** The name the person gave the place it is at, and since when (ms). */
    val label: String? = null,
    val sinceMillis: Long = 0,
) {
    /** An area, never to be drawn as a point (ADR 0121 §3). */
    val isArea: Boolean get() = place != null && radiusMeters != null
}

/** A person the own place can go to: its person ID for the core and the circles it shares with us. */
data class ShareTarget(val personId: String, val name: String, val circleIds: List<String>)

/** Interface of the app to the core for shared places (kind position); replaceable in tests. */
interface PlaceSource {
    /** Person ID whenever what it shares changed. */
    val changes: Flow<String>

    /** Person ID of someone asking to be shown where we are (ADR 0121 §4.4). */
    val asks: Flow<String>

    fun places(): List<SharedPlace>

    suspend fun share(personId: String, level: ShareLevel, place: Place?, radiusMeters: Int?, untilMillis: Long, label: String? = null, sinceMillis: Long = 0): Boolean

    suspend fun ask(personId: String): Boolean
}

/** [PlaceSource] on the ANMP core. */
class CorePlaceSource(private val core: AnmpCore) : PlaceSource {
    override val changes: Flow<String> = core.events.filterIsInstance<CoreEvent.PlaceChanged>().map { it.person }
    override val asks: Flow<String> = core.events.filterIsInstance<CoreEvent.PlaceAsked>().map { it.person }

    override fun places(): List<SharedPlace> = core.places().map { p ->
        SharedPlace(
            p.person, ShareLevel.ofWire(p.level),
            p.position?.let { Place(it.latitude, it.longitude, it.at, it.accuracy) }, p.radius, p.until, p.at, p.label, p.since,
        )
    }

    override suspend fun share(personId: String, level: ShareLevel, place: Place?, radiusMeters: Int?, untilMillis: Long, label: String?, sinceMillis: Long): Boolean =
        core.sharePlace(personId, level.wire, place?.let { AlivePosition(it.latitude, it.longitude, it.atMillis, it.accuracyMeters) }, radiusMeters, untilMillis, label, sinceMillis)

    override suspend fun ask(personId: String): Boolean = core.askPlace(personId)
}

/**
 * Sends the own place to the persons that may see it and keeps what the others share (ADR 0121 §3–§5). It goes to
 * single persons, never to a circle. Only the device chosen as the source sends ([LocationSharing.source]), and only
 * what the level says: nothing, the level alone, a fixed area, or the point.
 *
 * The app locates while it is open; without the system permission „Immer“ nothing new leaves the phone in the
 * background, and the others see from when the last place is (ADR 0121 §10).
 */
class PlaceSharer(
    private val scope: CoroutineScope,
    private val source: PlaceSource,
    private val sharing: LocationSharing,
    private val store: KeyValueStore,
    private val ownDeviceId: () -> String?,
    private val now: () -> Long,
    /** The places the person named: their name goes along where the person is at one (ADR 0121 §13). */
    private val saved: SavedPlaces? = null,
) {
    /** The persons of the own circles and contacts; set by the app once it knows them. */
    var targets: () -> List<ShareTarget> = { emptyList() }

    @Serializable
    internal data class Sent(val level: Int, val lat: Double? = null, val lon: Double? = null, val radius: Int? = null, val until: Long = 0, val at: Long = 0, val label: String? = null)

    private val json = Json { ignoreUnknownKeys = true }
    private val sentSerializer = MapSerializer(String.serializer(), Sent.serializer())
    private var sent: Map<String, Sent> =
        store.getString(KEY_SENT)?.let { runCatching { json.decodeFromString(sentSerializer, it) }.getOrNull() }.orEmpty()
    private val lock = Mutex()

    /** What was known of one person when everything was paused, kept so that it is still there after a restart. */
    @Serializable
    private data class Kept(
        val person: String, val level: String, val lat: Double? = null, val lon: Double? = null, val placeAt: Long = 0, val accuracy: Int? = null,
        val radius: Int? = null, val until: Long = 0, val at: Long = 0, val label: String? = null, val since: Long = 0,
    )

    private val keptSerializer = kotlinx.serialization.builtins.ListSerializer(Kept.serializer())

    private fun loadKept(): Map<String, SharedPlace> =
        store.getString(KEY_PAUSED_PLACES)?.let { runCatching { json.decodeFromString(keptSerializer, it) }.getOrNull() }.orEmpty().mapNotNull { k ->
            val level = ShareLevel.entries.firstOrNull { it.id == k.level } ?: return@mapNotNull null
            val place = if (k.lat != null && k.lon != null) Place(k.lat, k.lon, k.placeAt, k.accuracy) else null
            k.person to SharedPlace(k.person, level, place, k.radius, k.until, k.at, k.label, k.since)
        }.toMap()

    private fun saveKept(places: Map<String, SharedPlace>?) = store.putString(
        KEY_PAUSED_PLACES,
        places?.values?.map { p ->
            Kept(p.personId, p.level.id, p.place?.latitude, p.place?.longitude, p.place?.atMillis ?: 0, p.place?.accuracyMeters, p.radiusMeters, p.untilMillis, p.atMillis, p.label, p.sinceMillis)
        }?.let { json.encodeToString(keptSerializer, it) },
    )

    private val _places = MutableStateFlow(seen(loadKept()))

    /**
     * What is shown of the others: everything, or while everything is paused only what was known when pausing
     * (20.11.2026). What [held] has stays as it is; it is kept on the phone and survives a restart.
     */
    private fun seen(held: Map<String, SharedPlace>): Map<String, SharedPlace> {
        val all = source.places().associateBy { it.personId }
        if (!sharing.paused.value) return all
        val since = sharing.pausedAt()
        return all.mapNotNull { (id, place) -> (held[id] ?: place.takeIf { it.atMillis <= since })?.let { id to it } }.toMap()
    }

    /** What the others share with us, by person ID. */
    val places: StateFlow<Map<String, SharedPlace>> = _places.asStateFlow()

    private val _asking = MutableStateFlow<List<String>>(emptyList())

    /** Persons whose request „Standort anfragen“ is not answered yet. */
    val asking: StateFlow<List<String>> = _asking.asStateFlow()

    private val _own = MutableStateFlow<Place?>(null)

    /** Where this phone is, as last located; stays on the phone. */
    val own: StateFlow<Place?> = _own.asStateFlow()

    /**
     * „Meinen Standort teilen“ for a time (ADR 0121 §13): the circle [circleId] or the person [personId] sees the exact
     * place until [untilMillis]. With a destination the sharing ends by itself near it. [planId] is the check-in that
     * watches over it, if the person wanted one.
     */
    @Serializable
    data class Escort(
        val circleId: String? = null,
        val planId: String? = null,
        val untilMillis: Long = 0,
        val personId: String? = null,
        val destLatitude: Double? = null,
        val destLongitude: Double? = null,
        val destLabel: String? = null,
    ) {
        val hasDestination: Boolean get() = destLatitude != null && destLongitude != null
    }

    private val _escort = MutableStateFlow(
        store.getString(KEY_ESCORT)?.let { raw ->
            runCatching { json.decodeFromString(Escort.serializer(), raw) }.getOrNull()
                // The form before destinations: circle|plan|until.
                ?: raw.split('|').takeIf { it.size == 3 }?.let { Escort(it[0], it[1].ifEmpty { null }, it[2].toLongOrNull() ?: 0) }
        },
    )
    val escort: StateFlow<Escort?> = _escort.asStateFlow()

    /** Called when the person arrived at the destination of [Escort]: the app ends the check-in and says so in the conversation. */
    var onArrived: suspend (Escort) -> Unit = {}

    /** Someone asks to see the place: the app tells the person, also when the map is not open (ADR 0121 §26). */
    var onAsked: (personId: String) -> Unit = {}

    /** Every request is answered: the notice about them goes. */
    var onAnswered: () -> Unit = {}

    /** Starts or ends sharing for a time: the counterpart is set to the exact place until then, and back to what it was. */
    fun setEscort(escort: Escort?, members: Collection<String> = emptyList()) {
        val before = _escort.value
        if (before != null && before != escort) {
            before.circleId?.let { sharing.setLevel(it, null) }
            before.personId?.let { sharing.setPersonLevel(it, null, quiet = true) }
        }
        if (escort != null) {
            escort.circleId?.let { sharing.setLevel(it, ShareLevel.Exact, escort.untilMillis, members) }
            escort.personId?.let {
                sharing.approve(listOf(it))
                sharing.setPersonLevel(it, ShareLevel.Exact, escort.untilMillis, quiet = true)
            }
        }
        store.putString(KEY_ESCORT, escort?.let { json.encodeToString(Escort.serializer(), it) })
        _escort.value = escort
    }

    /** „Alle da?“ (ADR 0121 §8): asked in [circleId] at [sinceMillis]; who wrote there since has answered. */
    data class RollCall(val circleId: String, val sinceMillis: Long)

    private val _rollCall = MutableStateFlow(
        store.getString(KEY_ROLL_CALL)?.split('|')?.takeIf { it.size == 2 }?.let { RollCall(it[0], it[1].toLongOrNull() ?: 0) },
    )
    val rollCall: StateFlow<RollCall?> = _rollCall.asStateFlow()

    fun setRollCall(call: RollCall?) {
        store.putString(KEY_ROLL_CALL, call?.let { "${it.circleId}|${it.sinceMillis}" })
        _rollCall.value = call
    }

    /** `true` on the device whose location counts; with none chosen yet, the first that shares all the time becomes it. */
    val isSource: Boolean get() = sharing.source.value.let { it == null || it == ownDeviceId() }

    fun start() {
        scope.launch { source.changes.collect { _places.value = seen(_places.value) } }
        // Pausing keeps what is on the screen; going on shows what came in since.
        scope.launch {
            sharing.paused.collect { paused ->
                // Pausing keeps what is known right now, also over a restart; an app started while paused has it already.
                if (!paused) saveKept(null) else if (store.getString(KEY_PAUSED_PLACES) == null) saveKept(_places.value)
                _places.value = seen(_places.value)
            }
        }
        scope.launch {
            source.asks.collect { p ->
                if (p !in _asking.value) {
                    _asking.value = _asking.value + p
                    runCatching { onAsked(p) }
                }
            }
        }
        scope.launch {
            merge(sharing.default, sharing.circles, sharing.persons, sharing.until, sharing.paused, sharing.source, sharing.approved, sharing.allowed)
                .collect { sync() }
        }
        // The first tries above come before the app knows its own device; once it does, the levels are brought up to date.
        scope.launch {
            while (ownDeviceId() == null) delay(START_WAIT_MILLIS)
            sync()
        }
        scope.launch {
            while (true) {
                delay(TICK_MILLIS)
                // Only an action keeps locating (ADR 0121 §24): sharing for a time, „Alle teilen“, a way with a destination.
                // A level alone is brought up to date by [touch], when something happens anyway.
                // And once an hour without anything happening (20.11.2026), so that a place never gets older than that
                // while the phone lets the app run.
                // That hour stretches while the phone rests in one place, up to [IDLE_MAX_MILLIS], and with a low battery
                // the app does not locate just for this at all.
                if (live()) {
                    sync()
                } else if (now() - syncedAt >= idleGap && (battery() ?: 100) >= LOW_BATTERY) {
                    idleTick = true
                    sync()
                }
            }
        }
        // While a place is shared the system delivers places also with the app in the background (ADR 0121 §13).
        scope.launch {
            sharing.placeUpdates.collect {
                _own.value = it
            rememberOwn(it)
                saved?.onPlace(it)
                sync()
            }
        }
    }

    /** Persons that are new in a circle shared all the time and not asked about yet (ADR 0121 §5). */
    fun newcomers(): List<ShareTarget> = targets().filter { t ->
        t.personId !in sharing.approved.value && t.personId !in sharing.persons.value && t.circleIds.any { sharing.level(it).always }
    }

    /** Who sees something right now, with the level, for „Wer sieht mich?“ and the line on the map. */
    fun visibleTo(): List<Pair<ShareTarget, ShareLevel>> =
        targets().map { it to sharing.levelFor(it.personId, it.circleIds) }.filter { it.second != ShareLevel.Off }

    /** Locates now, for the own dot on the map; the place stays on the phone. */
    suspend fun locate(): Place? = sharing.place()?.also {
        _own.value = it
            rememberOwn(it)
        saved?.onPlace(it)
    }

    /** „Standort anfragen“ (ADR 0121 §4.4). */
    suspend fun request(personId: String): Boolean = source.ask(personId)

    /** Answer to a request: „Einmal zeigen“ ([minutes] = 0), for some time, or `null` for „Nein“. */
    fun answer(personId: String, minutes: Int?) {
        _asking.value = _asking.value - personId
        if (_asking.value.isEmpty()) runCatching { onAnswered() }
        if (minutes == null) return
        sharing.approve(listOf(personId))
        sharing.setPersonLevel(personId, ShareLevel.Exact, now() + (if (minutes == 0) ONCE_MILLIS else minutes * 60_000L), quiet = true)
    }

    /** Sends what changed since the last time; called on every change of the settings and every few minutes. */
    suspend fun sync() = lock.withLock {
        try {
            syncLocked()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Tried again with the next tick.
        }
    }

    @Volatile private var syncedAt = 0L

    /** How long the app waits without anything happening before it brings the levels up to date; see [nextIdleGap]. */
    @Volatile private var idleGap = IDLE_MILLIS
    @Volatile private var idleTick = false
    private var idlePlace: Place? = null

    /** The battery in percent, if the phone says; with a low one the app does not locate without a reason. */
    var battery: () -> Int? = { null }

    private suspend fun syncLocked() {
        syncedAt = now()
        // „Begleite mich“ ends by itself: the circle goes back to what it was.
        _escort.value?.takeIf { it.untilMillis <= now() }?.let { setEscort(null) }
        // Right after the start the app does not know yet which device it is: that try does not count.
        if (ownDeviceId() == null) {
            missed = true
            return
        }
        // Someone sees the place for good: the system reports clear moves, so that it follows without the clock.
        sharing.watchMoves(isSource && !sharing.paused.value && targets().any { sharing.levelFor(it.personId, it.circleIds).always })
        if (!isSource) return sharing.inUse(IN_USE, null)
        // Paused: nothing goes out, not even that it is paused; the others keep what they saw last.
        if (sharing.paused.value) {
            running = false
            return sharing.inUse(IN_USE, null)
        }
        val all = targets()
        if (all.isEmpty()) return sharing.inUse(IN_USE, null)
        val wanted = all.associateWith { sharing.levelFor(it.personId, it.circleIds) }
        // Tell the system, and through it the person, for as long as an action runs: only then the phone keeps locating
        // with the app in the background and shows its notification (ADR 0121 §24). A level without an end does not.
        val t0 = now()
        val seeing = wanted.filter { (target, level) -> level.always && sharing.untilFor(target.personId, target.circleIds) > t0 }.keys.map { it.name }
        running = seeing.isNotEmpty()
        sharing.inUse(IN_USE, if (seeing.isEmpty()) null else app.anynear.i18n.texts.map.sharingNotice(seeing.take(3).joinToString(", ") + if (seeing.size > 3) " …" else ""))
        if (wanted.values.any { it.always } && sharing.source.value == null) ownDeviceId()?.let(sharing::setSource)
        val place = if (wanted.values.any { it.always }) locate() else null
        missed = place == null && wanted.values.any { it.always }
        if (place != null) {
            val moved = idlePlace?.let { distanceMeters(it.latitude, it.longitude, place.latitude, place.longitude) > REST_METERS } ?: true
            idleGap = nextIdleGap(idleGap, moved, quiet = idleTick)
            if (moved) idlePlace = place
        }
        idleTick = false
        // Near the destination the sharing ends by itself (ADR 0121 §13); the levels below are then what they were before.
        val going = _escort.value
        if (place != null && going != null && going.hasDestination &&
            distanceMeters(place.latitude, place.longitude, going.destLatitude!!, going.destLongitude!!) <= maxOf(ARRIVE_METRES, (place.accuracyMeters ?: 0).toDouble().coerceAtMost(300.0))
        ) {
            setEscort(null)
            runCatching { onArrived(going) }
            return syncAgain()
        }
        val t = now()
        var changed = false
        for ((target, level) in wanted) {
            val until = sharing.untilFor(target.personId, target.circleIds)
            // At a place the person named: who sees the point reads the name; who sees the area only if the person said so.
            val here = saved?.here()?.takeIf { place != null }
            val name = here?.first?.takeIf { level == ShareLevel.Exact || it.withArea }?.name
            val next = when {
                !level.always || place == null -> Sent(level.wire, until = until, at = t)
                level == ShareLevel.Area -> areaOf(place.latitude, place.longitude, sent[target.personId]?.takeIf { it.radius != null && it.lat != null && it.lon != null }?.let { it.lat!! to it.lon!! }).let { (lat, lon) -> Sent(level.wire, lat, lon, AREA_RADIUS, until, t, name) }
                else -> Sent(level.wire, place.latitude, place.longitude, null, until, t, name)
            }
            val last = sent[target.personId]
            // The phone could not locate itself (at night in the background, battery saver): it says nothing rather than
            // a level without a point, which took the last place from the others (21.11.2026).
            if (level.always && place == null && keepsLast(last, next)) continue
            if (!differs(last, next)) continue
            val shown = if (next.lat != null && next.lon != null && place != null) {
                Place(next.lat, next.lon, place.atMillis, place.accuracyMeters.takeIf { next.radius == null })
            } else {
                null
            }
            if (source.share(target.personId, level, shown, next.radius, until, next.label, here?.second?.takeIf { next.label != null } ?: 0)) {
                sent = sent + (target.personId to next)
                changed = true
            }
        }
        if (changed) store.putString(KEY_SENT, json.encodeToString(sentSerializer, sent))
    }

    /** What rides along with a direct message (ADR 0121 §20): what [sync] would send this person, as it is known now. */
    class Riding internal constructor(val level: ShareLevel, val place: Place, val radiusMeters: Int?, val untilMillis: Long, val label: String?, val sinceMillis: Long, internal val sent: Sent)

    /**
     * The place for a direct message to [personId], or `null`: only for someone who sees the place all the time, only
     * from the device the place comes from, and only a place the phone knows already and that is fresh (a message never
     * waits for the locating). Over the internet only when it is news, as in [sync]; over [radio] always, there it
     * costs no packet of its own. Call [rode] once the message went out with it.
     */
    fun riding(personId: String, radio: Boolean): Riding? {
        if (!isSource) return null
        val target = targets().firstOrNull { it.personId == personId } ?: return null
        val level = sharing.levelFor(target.personId, target.circleIds).takeIf { it.always } ?: return null
        val place = _own.value?.takeIf { now() - it.atMillis < RIDE_FRESH_MILLIS } ?: return null
        val until = sharing.untilFor(target.personId, target.circleIds)
        val here = saved?.here()
        val name = here?.first?.takeIf { level == ShareLevel.Exact || it.withArea }?.name
        val last = sent[personId]
        val next = if (level == ShareLevel.Area) {
            areaOf(place.latitude, place.longitude, last?.takeIf { it.radius != null && it.lat != null && it.lon != null }?.let { it.lat!! to it.lon!! })
                .let { (lat, lon) -> Sent(level.wire, lat, lon, AREA_RADIUS, until, now(), name) }
        } else {
            Sent(level.wire, place.latitude, place.longitude, null, until, now(), name)
        }
        if (!radio && !differs(last, next)) return null
        return Riding(level, Place(next.lat!!, next.lon!!, place.atMillis, place.accuracyMeters.takeIf { next.radius == null }), next.radius, until, name, here?.second?.takeIf { name != null } ?: 0, next)
    }

    /** The message with [riding] went out to [personId]: [sync] need not send the same again. */
    fun rode(personId: String, riding: Riding) {
        sent = sent + (personId to riding.sent)
        store.putString(KEY_SENT, json.encodeToString(sentSerializer, sent))
    }

    private var rememberedAt = 0L

    /** Keeps where the phone was last on this device, so that the map opens there (20.11.2026); it never leaves it. */
    private fun rememberOwn(place: Place) {
        if (now() - rememberedAt < 60_000L) return
        rememberedAt = now()
        store.putString(KEY_LAST_OWN, "${place.latitude},${place.longitude}")
    }

    /** Where the phone is, or was when it last knew: for the first view of the map. */
    fun lastOwn(): Pair<Double, Double>? = _own.value?.let { it.latitude to it.longitude }
        ?: store.getString(KEY_LAST_OWN)?.split(',')?.takeIf { it.size == 2 }?.let { p -> p[0].toDoubleOrNull()?.let { lat -> p[1].toDoubleOrNull()?.let { lon -> lat to lon } } }

    @Volatile private var running = false

    /** `true` while an action shares the place live: a level with an end, e.g. „Standort teilen“ for an hour. */
    fun live(): Boolean = running

    private var touchedAt = 0L

    /** The last [sync] wanted the place and the phone had none: the next [touch] tries again at once. */
    @Volatile private var missed = false

    /**
     * Something happened anyway: the app was opened, a message was sent or the app was woken because something came
     * in. A good moment to bring up to date what the levels „Ungefähr“ and „Genau“ show, without locating all the
     * time (ADR 0121 §24). At most every [TOUCH_GAP_MILLIS].
     */
    fun touch() {
        val t = now()
        if (t - touchedAt < TOUCH_GAP_MILLIS) return
        touchedAt = t
        scope.launch {
            sync()
            if (missed) touchedAt = 0
        }
    }

    /** After the sharing ended by itself: once more with the levels as they are now. */
    private suspend fun syncAgain() = syncLocked()

    /** [next] has no point, but the others still see [last]'s with the same level and end: nothing new to tell. */
    private fun keepsLast(last: Sent?, next: Sent): Boolean =
        last != null && last.lat != null && last.level == next.level && last.until == next.until

    private fun differs(last: Sent?, next: Sent): Boolean {
        // Someone who never saw anything needs no „aus“.
        if (last == null) return next.level != ShareLevel.Off.wire
        if (last.level != next.level || last.until != next.until || (last.lat == null) != (next.lat == null) || last.radius != next.radius || last.label != next.label) return true
        val age = next.at - last.at
        if (next.lat == null || next.lon == null || last.lat == null || last.lon == null) return age > LEVEL_REPEAT_MILLIS
        // An area: only a new cell is news, and now and then that it still holds.
        if (next.radius != null) return last.lat != next.lat || last.lon != next.lon || age > AREA_REPEAT_MILLIS
        return distanceMeters(last.lat, last.lon, next.lat, next.lon) > MOVE_METERS || age > POINT_REPEAT_MILLIS
    }

    companion object {
        /** Device setting: what was sent last to whom, so that only changes go out. */
        const val KEY_SENT = "location_sent"
        private const val KEY_PAUSED_PLACES = "location_paused_places"

        /** Without anything happening the levels are brought up to date this often. */
        const val IDLE_MILLIS = 3_600_000L

        /** At rest the wait doubles up to this: at night or at the desk a place is asked for four times a day, not 24. */
        const val IDLE_MAX_MILLIS = 6 * 3_600_000L

        /** Within this distance the phone counts as resting. */
        private const val REST_METERS = 100.0

        /** Below this many percent the app does not locate without anything happening. */
        private const val LOW_BATTERY = 15

        /**
         * The next wait after a place was read: back to an hour once the phone moved; doubled, up to
         * [IDLE_MAX_MILLIS], when a check without anything happening ([quiet]) found it where it was; otherwise as it is.
         */
        internal fun nextIdleGap(gap: Long, moved: Boolean, quiet: Boolean): Long = when {
            moved -> IDLE_MILLIS
            quiet -> (gap * 2).coerceAtMost(IDLE_MAX_MILLIS)
            else -> gap
        }

        /** Device setting: where the phone was last, for the first view of the map. */
        const val KEY_LAST_OWN = "location_last_own"

        /** Device settings: a running „Begleite mich“ and a running „Alle da?“. */
        const val KEY_ESCORT = "location_escort"
        const val KEY_ROLL_CALL = "location_roll_call"

        private const val IN_USE = "share"

        const val TICK_MILLIS = 2 * 60_000L

        /** Channel of the notice that someone asks to see the place; a tap leads to the map. */
        const val ASK_CHANNEL = "place-ask"
        private const val START_WAIT_MILLIS = 500L

        /** A level is brought up to date at most this often. */
        const val TOUCH_GAP_MILLIS = 10 * 60_000L

        /** „Einmal zeigen“ holds this long, then the point is gone at the other side. */
        const val ONCE_MILLIS = 15 * 60_000L

        /** How near the destination counts as arrived. */
        const val ARRIVE_METRES = 120.0

        private const val MOVE_METERS = 50.0
        private const val POINT_REPEAT_MILLIS = 10 * 60_000L

        /** A place older than this does not ride along with a message. */
        private const val RIDE_FRESH_MILLIS = 20 * 60_000L
        private const val AREA_REPEAT_MILLIS = 6 * 3_600_000L
        private const val LEVEL_REPEAT_MILLIS = 20L * 24 * 3_600_000L

        /**
         * „Ungefähr“ is a honeycomb (ADR 0121 §13): fixed hexagons, about 2 km from side to side. [AREA_RADIUS] is from
         * the centre to a corner. The cells are laid out in metres with a fixed stretch for the longitude (the one of
         * 51° north), so that they are the same for everyone and never move.
         */
        const val AREA_RADIUS = 1155
        private const val METRES_PER_LAT = 111_200.0
        private const val METRES_PER_LON = 111_200.0 * 0.6293

        /** How far beyond its cell someone must be before the cell changes: a border at the front door shall not show. */
        const val AREA_STICKY_METRES = 200.0

        /**
         * The centre of the fixed cell [latitude]/[longitude] lies in (ADR 0121 §3, §13): a grid, no random offset,
         * which could be averaged out over many packets. With [last], the centre sent before, the person stays in that
         * cell until it is [AREA_STICKY_METRES] beyond it.
         */
        fun areaOf(latitude: Double, longitude: Double, last: Pair<Double, Double>? = null): Pair<Double, Double> {
            val x = longitude * METRES_PER_LON
            val y = latitude * METRES_PER_LAT
            if (last != null) {
                val dx = x - last.second * METRES_PER_LON
                val dy = y - last.first * METRES_PER_LAT
                if (sqrt(dx * dx + dy * dy) < AREA_RADIUS * 0.866 + AREA_STICKY_METRES) return last
            }
            // Pointy-top hexagons in axial coordinates, rounded in cube coordinates.
            val q = (sqrt(3.0) / 3 * x - y / 3) / AREA_RADIUS
            val r = (2.0 / 3 * y) / AREA_RADIUS
            var rq = kotlin.math.round(q)
            var rr = kotlin.math.round(r)
            val rs = kotlin.math.round(-q - r)
            val dq = kotlin.math.abs(rq - q)
            val dr = kotlin.math.abs(rr - r)
            val ds = kotlin.math.abs(rs + q + r)
            if (dq > dr && dq > ds) rq = -rr - rs else if (dr > ds) rr = -rq - rs
            val cx = AREA_RADIUS * sqrt(3.0) * (rq + rr / 2)
            val cy = AREA_RADIUS * 1.5 * rr
            return cy / METRES_PER_LAT to cx / METRES_PER_LON
        }

        /** The six corners (latitude, longitude) of the cell around [latitude]/[longitude] with [radius] metres to a corner. */
        fun areaCorners(latitude: Double, longitude: Double, radius: Int): List<Pair<Double, Double>> = List(6) { i ->
            val angle = PI / 180 * (60 * i + 30)
            latitude + radius * kotlin.math.sin(angle) / METRES_PER_LAT to longitude + radius * cos(angle) / METRES_PER_LON
        }

        /** Distance in metres; at the distances of a map of the own people the flat approximation suffices. */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dy = (lat2 - lat1) * 111_200.0
            val dx = (lon2 - lon1) * 111_200.0 * cos((lat1 + lat2) / 2 * PI / 180)
            return sqrt(dx * dx + dy * dy)
        }
    }
}
