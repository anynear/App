package app.anynear.app

import app.anynear.data.ObservedKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Settings of the app in the network (ADR 0106). Every stored setting is of one kind: [PERSON] (the same on all own
 * devices, via the profile space), [DEVICE] (in the network under this device's ID, taken over by a phone that
 * replaces it) or neither (stays on the device; the list of what never goes to the network is in ADR 0106 §5).
 * Settings that already have their own way (name, picture, dark mode and the like in [app.anynear.circles.ProfileRepository])
 * are not listed here.
 */
internal class SettingsSync(
    private val scope: CoroutineScope,
    private val store: ObservedKeyValueStore,
    private val target: Target,
) {
    /** The two places in the core: profile space and device space. */
    interface Target {
        suspend fun setPersonSettings(values: Map<String, ByteArray>)

        suspend fun setDeviceSettings(values: Map<String, ByteArray>)

        suspend fun takeOverDevice(deviceId: String): Map<String, ByteArray>?
    }

    /** Pushes the current values once (the core keeps only changes) and then every change. */
    fun start() {
        scope.launch {
            target.setDeviceSettings(DEVICE.associateWith(::encoded))
            target.setPersonSettings(PERSON.associate { PREFIX + it to encoded(it) })
        }
        scope.launch {
            store.changes.collect { key ->
                when (key) {
                    in DEVICE -> target.setDeviceSettings(mapOf(key to encoded(key)))
                    in PERSON -> target.setPersonSettings(mapOf(PREFIX + key to encoded(key)))
                }
            }
        }
    }

    /**
     * Person settings from another own device (profile space). They are stored right away and take effect with the
     * next start of the app. @return `true` if anything changed here.
     */
    fun applyPerson(settings: Map<String, ByteArray>): Boolean {
        var changed = false
        for ((name, bytes) in settings) {
            val key = name.removePrefix(PREFIX).takeIf { name.startsWith(PREFIX) && it in PERSON } ?: continue
            val value = decoded(bytes) ?: continue
            if (store.getString(key) != value.value) {
                store.putString(key, value.value)
                changed = true
            }
        }
        return changed
    }

    /**
     * This phone replaces the own device [deviceId] (ADR 0106 §7): its device settings are stored here. They take effect
     * after a restart. `false` if nothing was stored for it or the network did not answer.
     */
    suspend fun takeOver(deviceId: String): Boolean {
        val taken = target.takeOverDevice(deviceId) ?: return false
        for ((key, bytes) in taken) {
            if (key !in DEVICE) continue
            decoded(bytes)?.let { store.putString(key, it.value) }
        }
        return true
    }

    private fun encoded(key: String): ByteArray = (store.getString(key)?.let { "1$it" } ?: "0").encodeToByteArray()

    /** A stored value, `null` inside = removed; `null` = unreadable. */
    private class Value(val value: String?)

    private fun decoded(bytes: ByteArray): Value? {
        val s = bytes.decodeToString()
        return when {
            s == "0" -> Value(null)
            s.startsWith("1") -> Value(s.drop(1))
            else -> null
        }
    }

    companion object {
        /** Prefix of these settings among the person's settings in the profile space. */
        const val PREFIX = "kv:"

        /** The same on all own devices. */
        val PERSON = setOf(
            "lifesigns.circle", "lifesigns.loudness", "lifesigns.single",
            "alerts.settings",
            "radio_charge_reminder",
            "files.sort",
            // Who sees the own location (ADR 0121 §10): asked once per person, not per device
            "location.share", "location.share.circles", "location.share.persons", "location.share.until", "location.share.paused",
            "location.share.source", "location.share.approved", "sos.helpers", "location.places",
        )

        /** Bound to one device, in the network under its device ID. */
        val DEVICE = setOf(
            // Radio
            "paired_device.identifier", "paired_device.name", "paired_device.own_contact_code",
            "paired_device.suggestion_postponed_until", "paired_device.skipped_setup", "paired_device.known",
            "paired_device.known_names", "paired_device.disconnected",
            "radio.lastHwModel", "radioCheck.results", "own_node_num", "radio_relay_for_others",
            "charge.seen", "charge.level", "charge.lowShown", "charge.planned", "charge.awayShownFor",
            // Alerts
            "alerts.region", "alerts.alarmHintUntil", "alerts.notified", "alerts.seen",
            // Search for documents on the phone
            "files.find.card_dismissed", "files.find.watch", "files.find.watch_asked", "files.find.watch_sources",
            "files.find.watch_notice",
            // Postponed notices, chosen circle
            "notification_hint_postponed_until", "bluetooth_hint_postponed_until", "background_hint_postponed_until",
            "location_hint_postponed_until", "location_sent", "location_escort", "location_roll_call", "sos_shown", "sos_asks_told", "location_presence", "location_last_own", "location_visits", "location_places_declined",
            "selected_circle",
        )
    }
}
