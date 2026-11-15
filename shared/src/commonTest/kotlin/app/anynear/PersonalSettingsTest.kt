package app.anynear

import app.anynear.app.DarkMode
import app.anynear.circles.ProfileRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Settings of the person go to all own devices, settings of the device do not (ADR 0050). */
class PersonalSettingsTest {
    @Test
    fun onlySettingsOfThePersonGoToOtherDevices() {
        val phone = ProfileRepository(InMemoryStore())
        phone.setDarkMode(DarkMode.DARK)
        phone.setUsageStats(false)
        phone.postponeBluetoothHint(123)
        assertEquals(setOf("dark_mode", "usage_stats", "alerts_acked", "alerts_seen", "read_until"), phone.personalSettings().keys)

        val tablet = ProfileRepository(InMemoryStore())
        tablet.applyPersonalSettings(phone.personalSettings())
        assertEquals(DarkMode.DARK, tablet.darkMode.value)
        assertFalse(tablet.usageStats.value, "Nutzungsstatistik aus gilt auf allen Geräten (ADR 0045)")
        assertEquals(0L, tablet.bluetoothHintPostponedUntil.value)
    }

    @Test
    fun unknownOrBrokenValuesAreIgnored() {
        val tablet = ProfileRepository(InMemoryStore())
        tablet.applyPersonalSettings(mapOf("dark_mode" to "LILA", "radio_signature" to "vielleicht", "neu" to "x"))
        assertEquals(DarkMode.SYSTEM, tablet.darkMode.value)
        assertEquals(true, tablet.usageStats.value, "Vorgabe an")
    }
}
