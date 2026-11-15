package app.anynear.app

import app.anynear.alerts.GeoPoint
import app.anynear.anmp.core.RadioList
import app.anynear.anmp.core.RadioRegion
import app.anynear.data.KeyValueStore
import app.anynear.anmp.core.radio.RadioTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex

/**
 * Radio area of the own radio (ADR 0054 §1b): the region of the signed radio list whose radio settings (LoRa region,
 * preset, frequency) the radio uses. Automatic by the approximate location, with hysteresis at borders
 * ([RadioList.regionFor]), or chosen by hand. The location never leaves the phone and is not stored, only the name of
 * the area found. Outside all areas the radio keeps the area it had. Without any area the target is
 * [RadioTarget.DEFAULT] for the comparison only: the app writes no radio settings until an area is [known].
 */
class RadioAreas(
    scope: CoroutineScope,
    private val store: KeyValueStore,
    private val radioList: StateFlow<RadioList?>,
    /** May the app read the approximate location? */
    val locationAllowed: StateFlow<Boolean>,
    private val locate: suspend () -> GeoPoint?,
    private val log: (String) -> Unit = {},
    /** Is location on for the whole device? Off, the permission alone finds nothing. */
    private val servicesOn: () -> Boolean = { true },
) {
    private val _choice = MutableStateFlow(store.getString(CHOICE_KEY)?.takeIf { it.isNotEmpty() })
    private val _located = MutableStateFlow(store.getString(LOCATED_KEY)?.takeIf { it.isNotEmpty() })
    private val locateLock = Mutex()

    /** Area chosen by hand; `null`: automatic by location. */
    val choice: StateFlow<String?> = _choice.asStateFlow()

    /** Area found by location (also while one is chosen by hand); `null` while unknown. */
    val located: StateFlow<String?> = _located.asStateFlow()

    /** Areas of the list this app can apply, in the order of the list. */
    val areas: StateFlow<List<RadioRegion>> =
        radioList.map(::usable).stateIn(scope, SharingStarted.Eagerly, usable(radioList.value))

    /** Radio settings for the radio: the chosen area, else the located one, else the default. */
    val target: StateFlow<RadioTarget> =
        combine(radioList, _choice, _located) { list, chosen, found -> targetOf(list, chosen, found) }
            .stateIn(scope, SharingStarted.Eagerly, targetOf(radioList.value, _choice.value, _located.value))

    /** Is an area set, chosen or located? Without one the app writes no radio settings (ADR 0054 §1b). */
    val known: StateFlow<Boolean> =
        target.map { it.area != null }.stateIn(scope, SharingStarted.Eagerly, target.value.area != null)

    private val _locationOff = MutableStateFlow(false)

    /** Automatic with permission, but location is off for the device: the area cannot be found. */
    val locationOff: StateFlow<Boolean> = _locationOff.asStateFlow()

    /** Choose an area by name, or `null` for automatic. */
    fun choose(area: String?) {
        _choice.value = area
        store.putString(CHOICE_KEY, area)
    }

    /**
     * Automatic only: look up the approximate location and take the area for it. Does nothing without permission, list
     * or location; never asks for the permission itself.
     */
    suspend fun refresh() {
        if (_choice.value != null || !locationAllowed.value) return
        _locationOff.value = !servicesOn()
        if (_locationOff.value) return
        val list = radioList.value ?: return
        if (!locateLock.tryLock()) return
        try {
            val point = try {
                locate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Funkgebiet: Standort ${e::class.simpleName}")
                null
            } ?: return
            val found = list.regionFor(point.latitude, point.longitude, _located.value)?.name ?: return
            if (found != _located.value) {
                log("Funkgebiet: $found")
                _located.value = found
                store.putString(LOCATED_KEY, found)
            }
        } finally {
            locateLock.unlock()
        }
    }

    private fun usable(list: RadioList?) = list?.regions.orEmpty().filter { RadioTarget.of(it) != null }

    internal companion object {
        const val CHOICE_KEY = "radio.area.choice"
        const val LOCATED_KEY = "radio.area.located"

        /** A chosen area that left the list counts as automatic; an unknown located one as the default. */
        fun targetOf(list: RadioList?, chosen: String?, located: String?): RadioTarget {
            list ?: return RadioTarget.DEFAULT
            val area = chosen?.let(list::region) ?: located?.let(list::region) ?: return RadioTarget.DEFAULT
            return RadioTarget.of(area) ?: RadioTarget.DEFAULT
        }
    }
}
