package app.anynear.location

import app.anynear.app.PlatformHooks
import app.anynear.data.KeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** What an action needs of the phone's location (ADR 0125 §1). */
enum class LocationNeed {
    /** The area: official warnings, radio area. */
    Approximate,

    /** The exact place „beim Verwenden der App“: sending the place, SOS, check-in. */
    Precise,

    /** The exact place while an action runs, started with the app open; the system shows that it locates. */
    PreciseWhileActive,

    /** „Immer“: the levels that keep the place up to date. */
    Always,
}

/** Why the app asks (ADR 0125 §2): one sentence each, and the sheet comes once per purpose (§4). */
enum class LocationPurpose(val id: String) {
    SosContact("sos_contact"),
    LifeSign("lifesign"),
    Share("share"),
    SendPlace("send_place"),
    Level("level"),
    ;

    /** On the iPhone the exact place must stay after the app is closed: only the settings give that (ADR 0125 §6). */
    val lasting: Boolean get() = this == SosContact || this == LifeSign
}

/** What the phone allows for a [LocationNeed]. */
enum class LocationAccessState {
    /** Everything the need asks for. */
    Ok,

    /** Something, but not all: only the area, or the exact place without „Immer“. */
    Limited,

    /** Nothing. */
    Missing,

    /** Location is switched off on the whole device. */
    Off,
}

/**
 * The one place where the app checks the phone's location permission for an action and asks for it (ADR 0125 §2):
 * [state] says where the phone stands, [ensure] shows the sheet once per purpose and then goes step by step
 * through the system (switch on, the system's question, its settings), [fix] does the steps without the sheet, e.g.
 * from a hint line. Nothing here ever holds up an action: callers go on with whatever the answer is.
 */
class LocationAccess(
    private val hooks: PlatformHooks,
    /** Small state of this device; the keys here are not among the settings of the person, the permission is per device. */
    private val store: KeyValueStore,
    /** iPhone: the exact place that stays after closing the app is only switched on in the app's settings. */
    private val lastingPreciseOnlyInSettings: () -> Boolean = { false },
    /** How long [ensure] waits for the system's answer after „Erlauben“. */
    private val waitMillis: Long = WAIT_MILLIS,
) {
    /** A sheet the app shows right now, answered with [answer]. */
    data class Ask(val need: LocationNeed, val purpose: LocationPurpose)

    private val _asking = MutableStateFlow<Ask?>(null)

    /** The sheet that waits for an answer; `null`: none. */
    val asking: StateFlow<Ask?> = _asking.asStateFlow()

    private var pending: CompletableDeferred<Boolean>? = null
    private val sheet = Mutex()

    /** Changes whenever the phone's permissions change; for the screens, which then read [state] anew. */
    val changes: kotlinx.coroutines.flow.Flow<Any> =
        combine(hooks.locationAllowed, hooks.preciseLocationAllowed, hooks.backgroundLocationAllowed) { a, b, c -> Triple(a, b, c) }

    fun state(need: LocationNeed): LocationAccessState = when {
        !hooks.locationServicesOn() -> LocationAccessState.Off
        need == LocationNeed.Approximate -> if (hooks.locationAllowed.value || hooks.preciseLocationAllowed.value) LocationAccessState.Ok else LocationAccessState.Missing
        !hooks.preciseLocationAllowed.value -> if (hooks.locationAllowed.value) LocationAccessState.Limited else LocationAccessState.Missing
        need == LocationNeed.Always && !hooks.backgroundLocationAllowed.value -> LocationAccessState.Limited
        else -> LocationAccessState.Ok
    }

    /** Has the sheet for [purpose] been shown on this device? After that only the hint line asks (ADR 0125 §4). */
    fun asked(purpose: LocationPurpose): Boolean = store.getString(KEY_ASKED + purpose.id) == "1"

    /** Counts the sheet for [purpose] as shown, e.g. when an older question of the app took its place. */
    fun markAsked(purpose: LocationPurpose) = store.putString(KEY_ASKED + purpose.id, "1")

    /**
     * Checks what [need] asks for; if something is missing and the sheet for [purpose] has not been shown yet, shows it
     * and, after „Erlauben“, goes through the steps and waits for the system. Returns where the phone stands then.
     */
    suspend fun ensure(need: LocationNeed, purpose: LocationPurpose): LocationAccessState {
        val before = state(need)
        if (before == LocationAccessState.Ok || asked(purpose)) return before
        val yes = sheet.withLock {
            // Another sheet for the same purpose may have been answered meanwhile.
            if (asked(purpose)) return state(need)
            markAsked(purpose)
            val answer = CompletableDeferred<Boolean>()
            pending = answer
            _asking.value = Ask(need, purpose)
            try {
                answer.await()
            } finally {
                pending = null
                _asking.value = null
            }
        }
        return if (yes) fixAndWait(need, purpose) else state(need)
    }

    /** The answer to the sheet: „Erlauben“ ([yes]) or „Ohne Standort weiter“. */
    fun answer(yes: Boolean) {
        pending?.complete(yes)
    }

    /** The next step towards [need], without the sheet: switch on, the system's question or its settings. */
    fun fix(need: LocationNeed, purpose: LocationPurpose) {
        when {
            !hooks.locationServicesOn() -> hooks.openLocationSettings()
            need == LocationNeed.Approximate -> hooks.requestLocation()
            !hooks.preciseLocationAllowed.value ->
                if (purpose.lasting && hooks.locationAllowed.value && lastingPreciseOnlyInSettings()) hooks.openLocationSettings()
                else hooks.requestPreciseLocation()
            need == LocationNeed.Always && !hooks.backgroundLocationAllowed.value -> hooks.requestBackgroundLocation()
        }
    }

    /**
     * [fix] and wait until the phone allows [need] or [waitMillis] is over; for „Immer“ the exact place comes first,
     * then a second step.
     */
    suspend fun fixAndWait(need: LocationNeed, purpose: LocationPurpose): LocationAccessState {
        repeat(STEPS) {
            val before = state(need)
            if (before == LocationAccessState.Ok) return before
            fix(need, purpose)
            withTimeoutOrNull(waitMillis) {
                combine(hooks.locationAllowed, hooks.preciseLocationAllowed, hooks.backgroundLocationAllowed) { _, _, _ -> state(need) }
                    .first { it != before }
            } ?: return state(need)
        }
        return state(need)
    }

    private val _alwaysLost = MutableStateFlow(false)

    /**
     * „Immer“ was allowed while a level needs it, and is not any more (ADR 0125 §5): taken away in the system's settings.
     * Shown once per loss.
     */
    val alwaysLost: StateFlow<Boolean> = _alwaysLost.asStateFlow()

    /** Called whenever the permissions or the levels change; [needed]: a level of this phone shows the place all the time. */
    fun checkAlways(needed: Boolean) {
        when {
            !needed -> {
                store.putString(KEY_ALWAYS_HAD, null)
                _alwaysLost.value = false
            }
            state(LocationNeed.Always) == LocationAccessState.Ok -> {
                if (store.getString(KEY_ALWAYS_HAD) == null) store.putString(KEY_ALWAYS_HAD, "1")
                _alwaysLost.value = false
            }
            store.getString(KEY_ALWAYS_HAD) == "1" -> _alwaysLost.value = true
        }
    }

    /** „Wieder erlauben“ or „So lassen“: the hint is done until „Immer“ was allowed again and is lost again. */
    fun acknowledgeAlwaysLost() {
        store.putString(KEY_ALWAYS_HAD, null)
        _alwaysLost.value = false
    }

    companion object {
        const val WAIT_MILLIS = 120_000L
        private const val KEY_ALWAYS_HAD = "location.access.always_had"
        private const val STEPS = 3
        private const val KEY_ASKED = "location.access.asked."
    }
}
