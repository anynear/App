package app.anynear.app

import app.anynear.data.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ADR 0045 §1b: the daily report starts on the second day the stats run on a device. */
class UsageStatsFirstDayTest {
    private class MemoryStore : KeyValueStore {
        private val map = mutableMapOf<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    private fun report(day: String) = UsageReport(day, 1, 0, 0, 0, false, "android", "0.8.0")

    @Test
    fun freshInstallWaitsForTheNextDay() {
        val store = MemoryStore()
        assertTrue(UsageStats.isFirstDay(store, null, "2026-11-13"))
        assertTrue(UsageStats.isFirstDay(store, null, "2026-11-13"))
        assertFalse(UsageStats.isFirstDay(store, null, "2026-11-14"))
        assertFalse(UsageStats.isFirstDay(store, null, "2026-12-01"))
    }

    @Test
    fun deviceThatReportedBeforeMissesNoDay() {
        val store = MemoryStore()
        assertFalse(UsageStats.isFirstDay(store, report("2026-11-12"), "2026-11-13"))
        assertFalse(UsageStats.isFirstDay(store, null, "2026-11-14"))
    }

    @Test
    fun clockBeforeTheFirstDayStillWaits() {
        val store = MemoryStore()
        assertTrue(UsageStats.isFirstDay(store, null, "2026-11-13"))
        assertTrue(UsageStats.isFirstDay(store, null, "2026-11-12"))
    }
}
