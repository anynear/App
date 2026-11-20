package app.anynear.location

import app.anynear.data.KeyValueStore
import app.anynear.data.ObservedKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong

/**
 * A place the person named (ADR 0121 §13): „Zu Hause“, „Arbeit“, or a name of its own. A point with [radiusMeters]
 * around it. Who sees the person's exact place reads the name instead of a place name; with [withArea] also who sees
 * only the area, which says more than the area alone, so the person decides it per place.
 */
@Serializable
data class SavedPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 150,
    val withArea: Boolean = false,
)

/**
 * The places of the person (ADR 0121 §13) and where the phone is among them. The places are a setting of the person on
 * all its devices; where the phone is and how often it was where stay on this device and never leave it.
 *
 * The phone proposes places: where it was on several nights is asked about as „Zu Hause“, where it was on several
 * working days as „Arbeit“. It only knows what it located, that is while the app was open or a place was shared.
 */
class SavedPlaces(
    private val scope: CoroutineScope,
    private val store: KeyValueStore,
    private val now: () -> Long,
    /** The next local midnight after [atMillis], for the time of day and the day of the week without a time zone table. */
    private val nextMidnight: (atMillis: Long) -> Long,
) {
    enum class Kind { Home, Work }

    /** A place the phone proposes to save: where it is now, and as what. */
    data class Proposal(val kind: Kind, val latitude: Double, val longitude: Double, val cell: String)

    /** Where the phone is among the saved places, and since when (ms). */
    data class Presence(val placeId: String, val sinceMillis: Long)

    @Serializable
    private data class Visits(val nights: List<Long> = emptyList(), val workdays: List<Long> = emptyList(), val lat: Double = 0.0, val lon: Double = 0.0)

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(SavedPlace.serializer())
    private val visitsSerializer = MapSerializer(String.serializer(), Visits.serializer())

    private val _places = MutableStateFlow(read())
    val places: StateFlow<List<SavedPlace>> = _places.asStateFlow()

    private val _presence = MutableStateFlow(
        store.getString(KEY_PRESENCE)?.split('|')?.takeIf { it.size == 2 }?.let { Presence(it[0], it[1].toLongOrNull() ?: 0) },
    )
    val presence: StateFlow<Presence?> = _presence.asStateFlow()

    private val _proposal = MutableStateFlow<Proposal?>(null)
    val proposal: StateFlow<Proposal?> = _proposal.asStateFlow()

    private var visits: Map<String, Visits> = store.getString(KEY_VISITS)?.let { runCatching { json.decodeFromString(visitsSerializer, it) }.getOrNull() }.orEmpty()

    fun start() {
        val observed = store as? ObservedKeyValueStore ?: return
        scope.launch { observed.changes.collect { if (it == KEY_PLACES) _places.value = read() } }
    }

    /** The saved place the phone is at right now, with since when; `null` if at none. */
    fun here(): Pair<SavedPlace, Long>? = _presence.value?.let { p -> _places.value.firstOrNull { it.id == p.placeId }?.let { it to p.sinceMillis } }

    /** The saved place around the point, the nearest if several overlap. */
    fun at(latitude: Double, longitude: Double): SavedPlace? = _places.value
        .map { it to PlaceSharer.distanceMeters(latitude, longitude, it.latitude, it.longitude) }
        .filter { (place, metres) -> metres <= place.radiusMeters }.minByOrNull { it.second }?.first

    fun save(place: SavedPlace) {
        write(_places.value.filter { it.id != place.id } + place)
        if (_proposal.value?.let { PlaceSharer.distanceMeters(it.latitude, it.longitude, place.latitude, place.longitude) < NEAR_METRES } == true) _proposal.value = null
    }

    fun remove(id: String) {
        write(_places.value.filter { it.id != id })
        if (_presence.value?.placeId == id) setPresence(null)
    }

    /** „Nein“ to a proposal: the phone does not ask about this spot again. */
    fun dismiss(proposal: Proposal) {
        val dismissed = store.getString(KEY_DISMISSED)?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
        store.putString(KEY_DISMISSED, (dismissed + proposal.cell).takeLast(40).joinToString(","))
        _proposal.value = null
    }

    /**
     * The phone located [place]: is it at a saved place, and since when; and how often it was here, for a proposal.
     * An inaccurate fix (worse than [MAX_ACCURACY]) is no evidence for either.
     */
    fun onPlace(place: Place) {
        if ((place.accuracyMeters ?: 0) > MAX_ACCURACY) return
        val at = at(place.latitude, place.longitude)
        val before = _presence.value
        when {
            at == null -> if (before != null) setPresence(null)
            before?.placeId != at.id -> setPresence(Presence(at.id, place.atMillis))
        }
        count(place)
    }

    private fun setPresence(presence: Presence?) {
        store.putString(KEY_PRESENCE, presence?.let { "${it.placeId}|${it.sinceMillis}" })
        _presence.value = presence
    }

    private fun count(place: Place) {
        val midnight = nextMidnight(place.atMillis)
        val hour = (24 - ((midnight - place.atMillis) / 3_600_000.0)).toInt().coerceIn(0, 23)
        // The day this local day is, counted since 1970; 1970-01-01 was a Thursday.
        val day = (midnight / 86_400_000.0).roundToLong() - 1
        val weekday = ((day + 4) % 7).toInt()
        val night = hour >= 22 || hour < 6
        val work = weekday in 1..5 && hour in 9..16
        val cell = cellOf(place.latitude, place.longitude)
        if (night || work) {
            val v = visits[cell] ?: Visits(lat = place.latitude, lon = place.longitude)
            val keepFrom = day - KEEP_DAYS
            // A night belongs to the evening it began on.
            val nightOf = if (hour < 6) day - 1 else day
            val next = v.copy(
                nights = (if (night) (v.nights + nightOf).distinct() else v.nights).filter { it >= keepFrom },
                workdays = (if (work) (v.workdays + day).distinct() else v.workdays).filter { it >= keepFrom },
            )
            if (next != v) {
                visits = (visits + (cell to next)).filterValues { it.nights.isNotEmpty() || it.workdays.isNotEmpty() }
                store.putString(KEY_VISITS, json.encodeToString(visitsSerializer, visits))
            }
        }
        _proposal.value = propose(place, cell)
    }

    private fun propose(place: Place, cell: String): Proposal? {
        val v = visits[cell] ?: return null
        if (cell in store.getString(KEY_DISMISSED)?.split(',').orEmpty()) return null
        // Not where a place is saved already, or right next to one.
        if (_places.value.any { PlaceSharer.distanceMeters(place.latitude, place.longitude, it.latitude, it.longitude) < NEAR_METRES }) return null
        val kind = when {
            v.nights.size >= SEEN_TIMES -> Kind.Home
            v.workdays.size >= SEEN_TIMES -> Kind.Work
            else -> return null
        }
        return Proposal(kind, place.latitude, place.longitude, cell)
    }

    private fun read(): List<SavedPlace> = store.getString(KEY_PLACES)?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()

    private fun write(list: List<SavedPlace>) {
        store.putString(KEY_PLACES, list.takeIf { it.isNotEmpty() }?.let { json.encodeToString(listSerializer, it) })
        _places.value = list
    }

    companion object {
        /** Person setting: the saved places. */
        const val KEY_PLACES = "location.places"

        /** Device settings: where the phone is, how often it was where, and what the person declined. */
        const val KEY_PRESENCE = "location_presence"
        const val KEY_VISITS = "location_visits"
        const val KEY_DISMISSED = "location_places_declined"

        const val MAX_ACCURACY = 100
        private const val NEAR_METRES = 250.0
        private const val SEEN_TIMES = 3
        private const val KEEP_DAYS = 21

        /** Spots of about 200 m the phone counts its stays in. */
        fun cellOf(latitude: Double, longitude: Double): String = "${(latitude / 0.002).roundToLong()}:${(longitude / 0.003).roundToLong()}"
    }
}
