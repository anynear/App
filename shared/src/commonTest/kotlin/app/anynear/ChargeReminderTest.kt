package app.anynear

import app.anynear.app.ChargeReminder
import app.anynear.app.NoOpPlatformHooks
import app.anynear.app.Reminder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** Reminder to charge the radio (ADR 0085). */
class ChargeReminderTest {
    private class Hooks : NoOpPlatformHooks() {
        val shown = mutableListOf<Reminder>()
        val cancelled = mutableListOf<String>()

        override fun showReminder(reminder: Reminder) {
            shown += reminder
        }

        override fun cancelReminder(id: String) {
            cancelled += id
        }
    }

    private var now = 1_000_000_000L
    private val store = InMemoryStore()
    private val hooks = Hooks()
    private val reminder = ChargeReminder(store, hooks) { now }

    private fun lows() = hooks.shown.filter { it.id == ChargeReminder.LOW_ID }

    private fun aways() = hooks.shown.filter { it.id == ChargeReminder.AWAY_ID }

    @Test
    fun lowBatteryOnceUntilRecovered() {
        reminder.update(enabled = true, connected = true, battery = 40)
        assertEquals(0, lows().size)
        reminder.update(enabled = true, connected = true, battery = 20)
        reminder.update(enabled = true, connected = true, battery = 19)
        reminder.update(enabled = true, connected = true, battery = 21)
        reminder.update(enabled = true, connected = true, battery = 18)
        assertEquals(1, lows().size, "pendelt um die Schwelle: nur einmal")
        assertNull(lows().single().atMillis)
        reminder.update(enabled = true, connected = true, battery = 101)
        reminder.update(enabled = true, connected = true, battery = 15)
        assertEquals(2, lows().size, "nach dem Laden wieder")
    }

    @Test
    fun noLowWarningWithoutLevelOrWhenOff() {
        reminder.update(enabled = true, connected = true, battery = null)
        reminder.update(enabled = true, connected = true, battery = 0)
        reminder.update(enabled = false, connected = true, battery = 5)
        assertEquals(0, lows().size)
    }

    @Test
    fun awayPlannedFromLastConnection() {
        reminder.update(enabled = true, connected = true, battery = 64)
        assertEquals(now + 14.days.inWholeMilliseconds, aways().last().atMillis)
        assertTrue("64" in aways().last().text)

        // Disconnected later: scheduled from the last connection, not from now.
        val seen = now
        now += 2.hours.inWholeMilliseconds
        reminder.update(enabled = true, connected = false, battery = null)
        assertEquals(seen + 14.days.inWholeMilliseconds, aways().last().atMillis)
    }

    @Test
    fun awayReplannedAtMostHourlyWhileConnected() {
        reminder.update(enabled = true, connected = true, battery = 80)
        now += 10 * 60_000L
        reminder.update(enabled = true, connected = true, battery = 79)
        assertEquals(1, aways().size)
        now += 1.hours.inWholeMilliseconds
        reminder.update(enabled = true, connected = true, battery = 78)
        assertEquals(2, aways().size)
    }

    @Test
    fun overdueAfterRestartShownOnce() {
        reminder.update(enabled = true, connected = true, battery = 70)
        now += 20.days.inWholeMilliseconds
        // Alarm lost after a restart: show at once on start …
        reminder.update(enabled = true, connected = false, battery = null)
        assertNull(aways().last().atMillis)
        val count = aways().size
        // … but not again on the next start.
        reminder.update(enabled = true, connected = false, battery = null)
        assertEquals(count, aways().size)
    }

    @Test
    fun offCancelsPlannedReminder() {
        reminder.update(enabled = true, connected = true, battery = 70)
        reminder.update(enabled = false, connected = false, battery = null)
        assertEquals(listOf(ChargeReminder.AWAY_ID), hooks.cancelled)
    }

    @Test
    fun neverConnectedPlansNothing() {
        reminder.update(enabled = true, connected = false, battery = null)
        assertTrue(hooks.shown.isEmpty())
    }
}
