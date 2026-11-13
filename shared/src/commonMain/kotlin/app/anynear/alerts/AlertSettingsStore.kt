package app.anynear.alerts

import app.anynear.data.KeyValueStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Settings of the alerts on this device: per channel notifications, sound and from when on loud, the district for
 * official warnings (from the approximate location, never the location itself), up to when each channel was read.
 * Lives in the app's encrypted storage, which the iOS extension also reads
 * (sound in the wake-up). Does not go to other devices.
 */
class AlertSettingsStore(private val store: KeyValueStore) {
    fun settings(): Map<String, ChannelSettings> = read(SETTINGS_KEY, settingsSerializer).orEmpty()

    fun saveSettings(value: Map<String, ChannelSettings>) = write(SETTINGS_KEY, settingsSerializer, value)

    /** Settings of [channel], otherwise the default for its kind. */
    fun settingsFor(channel: AlertChannel): ChannelSettings = settings()[channel.id] ?: defaultSettings(channel)

    /** District for official warnings; `null`: still unknown. */
    fun region(): AlertRegion? = read(REGION_KEY, AlertRegion.serializer())

    fun saveRegion(value: AlertRegion?) {
        if (value == null) store.putString(REGION_KEY, null) else write(REGION_KEY, AlertRegion.serializer(), value)
    }

    /** Federal states formerly chosen by hand: no longer apply, the district now comes from the location. */
    fun forgetChosenStates() {
        if (store.getString(OLD_AREA_KEY) != null) store.putString(OLD_AREA_KEY, null)
    }

    fun seen(): Map<String, Long> = read(SEEN_KEY, seenSerializer).orEmpty()

    fun saveSeen(value: Map<String, Long>) = write(SEEN_KEY, seenSerializer, value)

    /**
     * Is [key] (channel/alert) notifying for the first time? Remembers the last few so that an alert the
     * iOS extension has already shown does not ring again when the app fetches it.
     */
    fun firstNotification(key: String): Boolean {
        if (notified(key)) return false
        markNotified(key)
        return true
    }

    /** Has [key] already notified? Without remembering [key]. */
    fun notified(key: String): Boolean = key in read(NOTIFIED_KEY, notifiedSerializer).orEmpty()

    /** [key] has notified, e.g. the one alert that a wake-up shows on iOS. */
    fun markNotified(key: String) {
        val list = read(NOTIFIED_KEY, notifiedSerializer).orEmpty()
        if (key !in list) write(NOTIFIED_KEY, notifiedSerializer, (list + key).takeLast(MAX_NOTIFIED))
    }

    /** Until when the “Nicht stören” hint rests (ms). */
    fun hintPostponedUntil(): Long = store.getString(HINT_KEY)?.toLongOrNull() ?: 0L

    fun postponeHint(until: Long) = store.putString(HINT_KEY, until.toString())

    private fun <T> read(key: String, serializer: kotlinx.serialization.KSerializer<T>): T? =
        store.getString(key)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    private fun <T> write(key: String, serializer: kotlinx.serialization.KSerializer<T>, value: T) =
        store.putString(key, json.encodeToString(serializer, value))

    private companion object {
        const val SETTINGS_KEY = "alerts.settings"
        const val REGION_KEY = "alerts.region"
        const val OLD_AREA_KEY = "alerts.area"
        const val SEEN_KEY = "alerts.seen"
        const val HINT_KEY = "alerts.alarmHintUntil"
        const val NOTIFIED_KEY = "alerts.notified"
        const val MAX_NOTIFIED = 200
        val notifiedSerializer = ListSerializer(String.serializer())
        val json = Json { ignoreUnknownKeys = true }
        val settingsSerializer = MapSerializer(String.serializer(), ChannelSettings.serializer())
        val seenSerializer = MapSerializer(String.serializer(), Long.serializer())
    }
}
