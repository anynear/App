package app.anynear.location

import app.anynear.andp.B64
import app.anynear.anmp.core.AnmpCore
import app.anynear.anmp.core.CirclePlace
import app.anynear.anmp.core.CoreEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlin.random.Random

/** A place this phone knows by name: an own one or one of a circle ([circleId]). */
data class KnownPlace(val id: String, val name: String, val latitude: Double, val longitude: Double, val radiusMeters: Int, val circleId: String? = null) {
    companion object {
        /**
         * The place around the point among [places], the nearest if several overlap (ADR 0121 §21). This is how a
         * reader's phone names where someone is: from the places it knows itself, so nothing new leaves any phone.
         */
        fun at(places: Collection<KnownPlace>, latitude: Double, longitude: Double): KnownPlace? = places
            .map { it to PlaceSharer.distanceMeters(latitude, longitude, it.latitude, it.longitude) }
            .filter { (place, metres) -> metres <= place.radiusMeters }.minByOrNull { it.second }?.first
    }
}

/**
 * The places of the circles (ADR 0121 §21): „Zu Hause“ or „Garten“ set up once for everyone in a circle. They live in
 * the circle and every member may change them.
 */
class CirclePlaces(private val scope: CoroutineScope, private val core: AnmpCore) {
    private val _all = MutableStateFlow<Map<String, List<CirclePlace>>>(emptyMap())

    /** The places by circle; circles without places are left out. */
    val all: StateFlow<Map<String, List<CirclePlace>>> = _all.asStateFlow()

    fun start() {
        scope.launch { core.events.filterIsInstance<CoreEvent.CirclePlacesChanged>().collect { refresh() } }
        scope.launch { core.circles.collect { refresh() } }
    }

    private fun refresh() {
        _all.value = core.circles.value.filterNot { it.removed }.associate { it.id to it.places }.filterValues { it.isNotEmpty() }
    }

    /** All of them as places this phone knows. */
    fun known(): List<KnownPlace> = _all.value.flatMap { (circle, list) -> list.map { KnownPlace(it.id, it.name, it.latitude, it.longitude, it.radius, circle) } }

    suspend fun add(circleId: String, name: String, latitude: Double, longitude: Double, radiusMeters: Int = 150): Boolean =
        core.setCirclePlaces(circleId, core.circlePlaces(circleId) + CirclePlace(B64.encode(Random.nextBytes(8)), name, latitude, longitude, radiusMeters))

    suspend fun remove(circleId: String, id: String): Boolean = core.setCirclePlaces(circleId, core.circlePlaces(circleId).filter { it.id != id })
}
