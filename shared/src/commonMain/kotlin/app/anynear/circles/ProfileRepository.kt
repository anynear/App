package app.anynear.circles

import app.anynear.alerts.AlertAcks
import app.anynear.alerts.AlertsSeen
import app.anynear.app.DarkMode
import app.anynear.data.KeyValueStore
import app.anynear.images.ImageRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Own display name and own image (the same in all circles) and the last known own radio. */
class ProfileRepository(private val store: KeyValueStore) {
    /** Own image with the time of the last change; image `null` = generated image. */
    @Serializable
    data class OwnImage(val image: ImageRef? = null, val ts: Long = 0)

    private val _ownImage = MutableStateFlow(
        store.getString(IMAGE_KEY)?.let { runCatching { json.decodeFromString(OwnImage.serializer(), it) }.getOrNull() } ?: OwnImage(),
    )
    val ownImage: StateFlow<OwnImage> = _ownImage.asStateFlow()

    fun setOwnImage(image: ImageRef?, ts: Long) {
        val value = OwnImage(image, ts)
        store.putString(IMAGE_KEY, json.encodeToString(OwnImage.serializer(), value))
        _ownImage.value = value
    }

    private val _ownName = MutableStateFlow(store.getString(NAME_KEY).orEmpty())
    val ownName: StateFlow<String> = _ownName.asStateFlow()

    private val _ownNodeNum = MutableStateFlow(store.getString(NODE_KEY)?.toIntOrNull() ?: 0)

    /** Own radio, last known (0: none). Is kept when the radio is currently off. */
    val ownNodeNum: StateFlow<Int> = _ownNodeNum.asStateFlow()

    private val _backgroundHintPostponedUntil = MutableStateFlow(store.getString(BACKGROUND_HINT_KEY)?.toLongOrNull() ?: 0L)

    /** Until then (ms) the app does not ask again for „Im Hintergrund erlauben“. */
    val backgroundHintPostponedUntil: StateFlow<Long> = _backgroundHintPostponedUntil.asStateFlow()

    fun postponeBackgroundHint(untilMillis: Long) {
        store.putString(BACKGROUND_HINT_KEY, untilMillis.toString())
        _backgroundHintPostponedUntil.value = untilMillis
    }

    private val _setupDone = MutableStateFlow(store.getString(SETUP_DONE_KEY) == "true")

    /**
     * Setup completed (name, then circle, contact or „Erst einmal umsehen“). Is kept even without a
     * circle, for instance after leaving the last one. A name alone does not count.
     */
    val setupDone: StateFlow<Boolean> = _setupDone.asStateFlow()

    fun markSetupDone() {
        if (_setupDone.value) return
        store.putString(SETUP_DONE_KEY, "true")
        _setupDone.value = true
    }

    /**
     * Installation from before the flag: derive it once from [usedBefore] (circle or self-written message)
     * and store it. After that only the stored flag decides.
     */
    fun migrateSetupDone(usedBefore: Boolean) {
        if (store.getString(SETUP_DONE_KEY) != null) return
        store.putString(SETUP_DONE_KEY, usedBefore.toString())
        _setupDone.value = usedBefore
    }

    private val _notificationHintPostponedUntil = MutableStateFlow(store.getString(NOTIFICATION_HINT_KEY)?.toLongOrNull() ?: 0L)

    /** Until then (ms) the app does not show the card „Benachrichtigungen erlauben“. */
    val notificationHintPostponedUntil: StateFlow<Long> = _notificationHintPostponedUntil.asStateFlow()

    fun postponeNotificationHint(untilMillis: Long) {
        store.putString(NOTIFICATION_HINT_KEY, untilMillis.toString())
        _notificationHintPostponedUntil.value = untilMillis
    }

    private val _bluetoothHintPostponedUntil = MutableStateFlow(store.getString(BLUETOOTH_HINT_KEY)?.toLongOrNull() ?: 0L)

    /** Until then (ms) the app does not show the card „Bluetooth erlauben“. */
    val bluetoothHintPostponedUntil: StateFlow<Long> = _bluetoothHintPostponedUntil.asStateFlow()

    fun postponeBluetoothHint(untilMillis: Long) {
        store.putString(BLUETOOTH_HINT_KEY, untilMillis.toString())
        _bluetoothHintPostponedUntil.value = untilMillis
    }

    fun setOwnName(name: String) {
        val trimmed = name.trim().take(MAX_OWN_NAME)
        store.putString(NAME_KEY, trimmed)
        _ownName.value = trimmed
    }

    private val _relayForOthers = MutableStateFlow(store.getString(RELAY_FOR_OTHERS_KEY) != "false")

    /**
     * Forward for others (ADR 0051): pass radio packets of others to the internet and be a patron for others. Default
     * on, can be switched off under „Erweitert“.
     */
    val relayForOthers: StateFlow<Boolean> = _relayForOthers.asStateFlow()

    fun setRelayForOthers(on: Boolean) {
        store.putString(RELAY_FOR_OTHERS_KEY, on.toString())
        _relayForOthers.value = on
    }

    private val _chargeReminder = MutableStateFlow(store.getString(CHARGE_REMINDER_KEY) != "false")

    /** Remind to charge the radio (ADR 0085). Default on, only for this phone, can be switched off under „Erweitert“. */
    val chargeReminder: StateFlow<Boolean> = _chargeReminder.asStateFlow()

    fun setChargeReminder(on: Boolean) {
        store.putString(CHARGE_REMINDER_KEY, on.toString())
        _chargeReminder.value = on
    }

    private val _usageStats = MutableStateFlow(store.getString(USAGE_STATS_KEY) != "false")

    /** Share usage statistics (ADR 0045). Default on, can be switched off under „Datenschutz“; applies to the person. */
    val usageStats: StateFlow<Boolean> = _usageStats.asStateFlow()

    fun setUsageStats(on: Boolean) {
        store.putString(USAGE_STATS_KEY, on.toString())
        _usageStats.value = on
    }

    private val _alertAcks = MutableStateFlow(AlertAcks.decode(store.getString(AlertAcks.SETTING_KEY)))

    /** Acknowledged alerts („Gesehen“), apply to the person on all own devices ([AlertAcks]). */
    val alertAcks: StateFlow<Map<String, Long>> = _alertAcks.asStateFlow()

    fun setAlertAcks(acks: Map<String, Long>) {
        if (acks == _alertAcks.value) return
        store.putString(AlertAcks.SETTING_KEY, AlertAcks.encode(acks))
        _alertAcks.value = acks
    }

    private val _alertsSeen = MutableStateFlow(AlertsSeen.decode(store.getString(AlertsSeen.SETTING_KEY)))

    /** Until when each channel has been seen, for the person on all own devices ([AlertsSeen]). */
    val alertsSeen: StateFlow<Map<String, Long>> = _alertsSeen.asStateFlow()

    fun setAlertsSeen(seen: Map<String, Long>) {
        if (seen == _alertsSeen.value) return
        store.putString(AlertsSeen.SETTING_KEY, AlertsSeen.encode(seen))
        _alertsSeen.value = seen
    }

    private val _darkMode = MutableStateFlow(
        store.getString(DARK_MODE_KEY)?.let { name -> DarkMode.entries.firstOrNull { it.name == name } } ?: DarkMode.SYSTEM,
    )

    /** Light or dark, default like the phone. */
    val darkMode: StateFlow<DarkMode> = _darkMode.asStateFlow()

    fun setDarkMode(mode: DarkMode) {
        store.putString(DARK_MODE_KEY, mode.name)
        _darkMode.value = mode
    }

    fun setOwnNodeNum(nodeNum: Int) {
        if (_ownNodeNum.value == nodeNum) return
        store.putString(NODE_KEY, nodeNum.toString())
        _ownNodeNum.value = nodeNum
    }

    /**
     * Settings that apply to the person and go to all own devices (ADR 0050), as name → value. Everything
     * else here (notices, radio nodes, setup) stays on the device. Every new setting is classified here.
     */
    fun personalSettings(): Map<String, String> = mapOf(
        DARK_MODE_KEY to _darkMode.value.name,
        USAGE_STATS_KEY to _usageStats.value.toString(),
        AlertAcks.SETTING_KEY to AlertAcks.encode(_alertAcks.value),
        AlertsSeen.SETTING_KEY to AlertsSeen.encode(_alertsSeen.value),
    )

    /** If a setting of the person changes, the new state is here. */
    val personalSettingsChanged: kotlinx.coroutines.flow.Flow<Map<String, String>> =
        kotlinx.coroutines.flow.combine(_darkMode, _usageStats, _alertAcks, _alertsSeen) { _, _, _, _ -> personalSettings() }

    /**
     * Takes over settings of the person from another own device; unknown ones are left alone. Acknowledged
     * alerts are merged instead of replaced. @return `true` if the own state afterwards contains more than
     * the received one and therefore has to be written back.
     */
    fun applyPersonalSettings(values: Map<String, String>, now: Long = kotlin.time.Clock.System.now().toEpochMilliseconds()): Boolean {
        values[DARK_MODE_KEY]?.let { v -> DarkMode.entries.firstOrNull { it.name == v } }?.let(::setDarkMode)
        values[USAGE_STATS_KEY]?.toBooleanStrictOrNull()?.let(::setUsageStats)
        // Seen channels: per channel the later time wins.
        var newer = false
        values[AlertsSeen.SETTING_KEY]?.let(AlertsSeen::decode)?.let { remote ->
            val merged = AlertsSeen.merge(_alertsSeen.value, remote)
            setAlertsSeen(merged)
            newer = merged != remote
        }
        val remote = values[AlertAcks.SETTING_KEY]?.let(AlertAcks::decode) ?: return newer
        val merged = AlertAcks.prune(AlertAcks.merge(_alertAcks.value, remote), now)
        setAlertAcks(merged)
        return newer || merged != AlertAcks.prune(remote, now)
    }

    /** Does this state come from a restored backup (ADR 0027)? */
    val restoredFromBackup: Boolean get() = store.getString(RESTORED_KEY) != null

    fun markRestoredFromBackup(atMillis: Long) {
        store.putString(RESTORED_KEY, atMillis.toString())
    }

    companion object {
        /** How long the own name may be; contact links truncate to exactly as much. */
        const val MAX_OWN_NAME = 40

        private const val NAME_KEY = "own_name"
        private const val RESTORED_KEY = "restored_from_backup_at"
        private const val NODE_KEY = "own_node_num"
        private const val RELAY_FOR_OTHERS_KEY = "radio_relay_for_others"
        private const val CHARGE_REMINDER_KEY = "radio_charge_reminder"
        private const val USAGE_STATS_KEY = "usage_stats"
        private const val BACKGROUND_HINT_KEY = "background_hint_postponed_until"
        private const val IMAGE_KEY = "own_image.v1"
        private const val DARK_MODE_KEY = "dark_mode"
        private const val SETUP_DONE_KEY = "setup_done"
        private const val NOTIFICATION_HINT_KEY = "notification_hint_postponed_until"
        private const val BLUETOOTH_HINT_KEY = "bluetooth_hint_postponed_until"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
