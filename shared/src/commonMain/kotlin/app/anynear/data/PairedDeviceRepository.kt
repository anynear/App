package app.anynear.data

import app.anynear.mesh.PairedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PairedDeviceRepository(
    private val store: KeyValueStore,
    /** Names the search uses when a radio announces none („Meshtastic-Funkgerät“); never better than a remembered one. */
    private val placeholderNames: Set<String> = emptySet(),
) {
    private val _device = MutableStateFlow(load())
    val device: StateFlow<PairedDevice?> = _device.asStateFlow()

    private val _ownContactCode = MutableStateFlow(store.getString(KEY_OWN_CODE))

    /**
     * Last known own contact code of this radio. Contains no secrets (only device ID,
     * name, public key) and may therefore be stored. This way it is also available during short
     * connection gaps.
     */
    val ownContactCode: StateFlow<String?> = _ownContactCode.asStateFlow()

    fun rememberOwnContactCode(code: String) {
        if (_ownContactCode.value == code) return
        store.putString(KEY_OWN_CODE, code)
        _ownContactCode.value = code
    }

    private val _suggestionPostponedUntil = MutableStateFlow(store.getString(KEY_SUGGESTION_POSTPONED)?.toLongOrNull() ?: 0L)

    /**
     * Until when the notice „Funkgerät gefunden“ rests after „Später“ (milliseconds since 1970). Formerly a
     * „Nicht jetzt“ applied forever per device; that also hid the own device permanently.
     */
    val suggestionPostponedUntil: StateFlow<Long> = _suggestionPostponedUntil.asStateFlow()

    fun postponeSuggestion(untilMillis: Long) {
        store.putString(KEY_SUGGESTION_POSTPONED, untilMillis.toString())
        _suggestionPostponedUntil.value = untilMillis
    }

    private val _skippedSetup = MutableStateFlow(store.getString(KEY_SKIPPED_SETUP))

    /**
     * Deviations from the device profile ([app.anynear.mesh.DeviceSetupCheck.signature]) for which
     * the dialog „Funkgerät einrichten“ no longer comes up by itself: declined or already set up
     * once without the device having taken over everything. Applies to the paired device.
     */
    val skippedSetup: StateFlow<String?> = _skippedSetup.asStateFlow()

    fun skipSetup(signature: String?) {
        store.putString(KEY_SKIPPED_SETUP, signature)
        _skippedSetup.value = signature
    }

    private val _knownDevices = MutableStateFlow(loadKnown())

    /**
     * Radios that were assigned here before, with the name from back then (newest first). They are listed under
     * „Meine Funkgeräte“ even if they are not connected right now, and can be forgotten individually.
     */
    val knownDevices: StateFlow<List<PairedDevice>> = _knownDevices.asStateFlow()

    /**
     * Identifiers of [knownDevices] (like [PairedDevice.identifier]). Only they are offered via Bluetooth automatically:
     * a foreign device within radio range is not the own one.
     */
    private val knownIds = MutableStateFlow(_knownDevices.value.map { it.identifier }.toSet())
    val known: StateFlow<Set<String>> = knownIds.asStateFlow()

    private val _disconnected = MutableStateFlow(store.getString(KEY_DISCONNECTED) == "1")

    /**
     * Disconnected by hand („Trennen“): the radio stays assigned, but the app does not connect until one presses
     * „Verbinden“ again. Also applies after a restart.
     */
    val disconnected: StateFlow<Boolean> = _disconnected.asStateFlow()

    fun setDisconnected(value: Boolean) {
        if (_disconnected.value == value) return
        store.putString(KEY_DISCONNECTED, if (value) "1" else null)
        _disconnected.value = value
    }

    fun pair(device: PairedDevice) {
        // The entry under „Meine Funkgeräte“ decides the name: a remembered one stays (see remember).
        val named = remember(device)
        store.putString(KEY_ID, named.identifier)
        store.putString(KEY_NAME, named.name)
        forgetOwnContactCode()
        skipSetup(null)
        setDisconnected(false)
        _device.value = named
    }

    /** Takes [identifier] out of „Meine Funkgeräte“. The currently assigned device is also released in the process. */
    fun forget(identifier: String) {
        if (_device.value?.identifier == identifier) unpair()
        saveKnown(_knownDevices.value.filter { it.identifier != identifier })
    }

    /**
     * The radio's own name (as it reports it when connected) for [identifier], so that „Meine Funkgeräte“ shows it
     * later too, also when the radio is not connected. Blank names change nothing.
     */
    fun rememberName(identifier: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val known = _knownDevices.value
        if (known.none { it.identifier == identifier && it.name != clean }) return
        saveKnown(known.map { if (it.identifier == identifier) it.copy(name = clean) else it })
        if (_device.value?.identifier == identifier) {
            store.putString(KEY_NAME, clean)
            _device.value = _device.value?.copy(name = clean)
        }
    }

    private fun remember(device: PairedDevice): PairedDevice {
        val previous = _knownDevices.value.firstOrNull { it.identifier == device.identifier }
        // A remembered name (the radio's own) wins over an empty one or a stand-in from the search.
        val entry = if (previous != null && previous.name.isNotBlank() && (device.name.isBlank() || device.name in placeholderNames)) previous else device
        saveKnown(listOf(entry) + _knownDevices.value.filter { it.identifier != device.identifier })
        return entry
    }

    private fun saveKnown(devices: List<PairedDevice>) {
        // Old field (identifiers only) stays as it was; the names are next to it (no new format, no migration).
        store.putString(KEY_KNOWN, devices.joinToString("\n") { it.identifier }.ifEmpty { null })
        store.putString(KEY_KNOWN_NAMES, devices.joinToString("\n") { "${it.identifier}\t${clean(it.name)}" }.ifEmpty { null })
        _knownDevices.value = devices
        knownIds.value = devices.map { it.identifier }.toSet()
    }

    private fun loadKnown(): List<PairedDevice> {
        val names = store.getString(KEY_KNOWN_NAMES)?.split('\n').orEmpty()
            .mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 && it[0].isNotEmpty() }?.let { it[0] to it[1] } }
            .toMap()
        val current = load()
        val ids = (listOfNotNull(current?.identifier) + store.getString(KEY_KNOWN)?.split('\n').orEmpty()).filter { it.isNotEmpty() }.distinct()
        return ids.map { id -> PairedDevice(id, names[id]?.takeIf { it.isNotEmpty() } ?: current?.takeIf { it.identifier == id }?.name.orEmpty()) }
    }

    private fun clean(name: String) = name.replace('\t', ' ').replace('\n', ' ')

    fun unpair() {
        setDisconnected(false)
        store.putString(KEY_ID, null)
        store.putString(KEY_NAME, null)
        forgetOwnContactCode()
        skipSetup(null)
        _device.value = null
    }

    private fun forgetOwnContactCode() {
        store.putString(KEY_OWN_CODE, null)
        _ownContactCode.value = null
    }

    private fun load(): PairedDevice? {
        val id = store.getString(KEY_ID) ?: return null
        return PairedDevice(identifier = id, name = store.getString(KEY_NAME).orEmpty())
    }

    private companion object {
        const val KEY_ID = "paired_device.identifier"
        const val KEY_NAME = "paired_device.name"
        const val KEY_OWN_CODE = "paired_device.own_contact_code"
        const val KEY_SUGGESTION_POSTPONED = "paired_device.suggestion_postponed_until"
        const val KEY_SKIPPED_SETUP = "paired_device.skipped_setup"
        const val KEY_KNOWN = "paired_device.known"
        const val KEY_KNOWN_NAMES = "paired_device.known_names"
        const val KEY_DISCONNECTED = "paired_device.disconnected"
    }
}
