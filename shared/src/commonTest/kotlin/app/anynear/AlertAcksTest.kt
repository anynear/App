package app.anynear

import app.anynear.alerts.AlertAcks
import app.anynear.circles.ProfileRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** „Gesehen“ on alerts: applies to the person, goes to all own devices, nothing gets lost (ADR 0065). */
class AlertAcksTest {
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun laterChangeWinsPerAlert() {
        val phone = mapOf("a" to 100L, "b" to 100L)
        val tablet = mapOf("a" to -200L, "c" to 150L)
        val merged = AlertAcks.merge(phone, tablet)
        assertFalse(AlertAcks.isAcked(merged, "a"), "später wieder eingeblendet")
        assertTrue(AlertAcks.isAcked(merged, "b"))
        assertTrue(AlertAcks.isAcked(merged, "c"))
        assertEquals(merged, AlertAcks.merge(tablet, phone), "Reihenfolge egal")
    }

    @Test
    fun oldEntriesFallAway() {
        val now = 100 * day
        val acks = mapOf("alt" to now - 31 * day, "neu" to now - day, "zurück" to -(now - 31 * day))
        assertEquals(setOf("neu"), AlertAcks.prune(acks, now).keys)
    }

    @Test
    fun brokenValueCountsAsEmpty() {
        assertEquals(emptyMap(), AlertAcks.decode("kaputt"))
        assertEquals(mapOf("a" to 5L), AlertAcks.decode(AlertAcks.encode(mapOf("a" to 5L))))
    }

    @Test
    fun ackFromOtherDeviceIsMergedNotReplaced() {
        val now = 10 * day
        val phone = ProfileRepository(InMemoryStore())
        phone.setAlertAcks(mapOf("a" to now - 10))
        val tablet = ProfileRepository(InMemoryStore())
        tablet.setAlertAcks(mapOf("b" to now - 5))

        val pushBack = tablet.applyPersonalSettings(phone.personalSettings(), now)
        assertEquals(setOf("a", "b"), tablet.alertAcks.value.keys)
        assertTrue(pushBack, "das Tablet kennt mehr und schreibt zurück")

        assertFalse(phone.applyPersonalSettings(tablet.personalSettings(), now), "danach gleich, nichts zurückschreiben")
        assertEquals(tablet.alertAcks.value, phone.alertAcks.value)
    }
}
