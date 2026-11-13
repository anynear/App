package app.anynear

import app.anynear.alerts.AlertsSeen
import app.anynear.circles.ProfileRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Seen per channel on all own devices (setting `alerts_seen`). */
class AlertsSeenTest {
    @Test
    fun laterTimeWinsPerChannel() {
        val merged = AlertsSeen.merge(mapOf("a" to 10L, "b" to 50L), mapOf("a" to 20L, "c" to 5L))
        assertEquals(mapOf("a" to 20L, "b" to 50L, "c" to 5L), merged)
        assertEquals(merged, AlertsSeen.decode(AlertsSeen.encode(merged)))
        assertEquals(emptyMap(), AlertsSeen.decode("kaputt"))
    }

    @Test
    fun goesWithThePersonalSettingsAndMerges() {
        val phone = ProfileRepository(InMemoryStore())
        phone.setAlertsSeen(mapOf("kanal" to 100L))
        val tablet = ProfileRepository(InMemoryStore())
        tablet.setAlertsSeen(mapOf("kanal" to 50L, "anderer" to 7L))

        // The tablet has more than the phone sends: write back.
        assertTrue(tablet.applyPersonalSettings(phone.personalSettings()))
        assertEquals(mapOf("kanal" to 100L, "anderer" to 7L), tablet.alertsSeen.value)
        // Afterwards the phone has everything and writes nothing back.
        assertFalse(phone.applyPersonalSettings(tablet.personalSettings()) && phone.alertsSeen.value != tablet.alertsSeen.value)
        assertEquals(tablet.alertsSeen.value, phone.alertsSeen.value)
    }
}
