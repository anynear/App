package app.anynear

import app.anynear.anmp.core.StreamStats
import app.anynear.app.DebugLog
import app.anynear.ui.debugLogFileName
import app.anynear.ui.debugLogText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebugLogTest {
    @Test
    fun keepsOnlyTheNewestLines() {
        var t = 0L
        val log = DebugLog(max = 3, now = { t++ })
        repeat(5) { log.add("Lesen", "Zeile $it") }
        assertEquals(listOf("Zeile 2", "Zeile 3", "Zeile 4"), log.entries.value.map { it.message })
        log.clear()
        assertTrue(log.entries.value.isEmpty())
    }

    @Test
    fun textHasVersionAndSeconds() {
        val text = debugLogText(listOf(DebugLog.Entry(61_000, "Lesen", "gesendet")), "0.1.0-beta.abc")
        val lines = text.lines()
        assertEquals("anynear-Protokoll, Version 0.1.0-beta.abc", lines[0])
        assertTrue(lines[1].endsWith(":01 Lesen: gesendet"), lines[1])
    }

    @Test
    fun streamCountersStandUnderTheHeader() {
        val stats = StreamStats(home = "de-nue-01", connects = 3, drops = mapOf("network" to 2), homeSwitches = 1, catchUps = 4, missed = 1, lastSeconds = listOf(600, 1800))
        val lines = debugLogText(emptyList(), "0.3.0", stats).lines()
        assertEquals("Stream: home=de-nue-01 connects=3 drops=network:2 switches=1 catchups=4 missed=1 last=600s,1800s", lines[1])
        // Without any stream nothing is added.
        assertEquals(1, debugLogText(emptyList(), "0.3.0", StreamStats()).lines().count { it.isNotEmpty() })
    }

    @Test
    fun downloadNameHasDateAndTime() {
        // 2026-11-11T14:32:05Z
        assertEquals("anynear-protokoll-2026-11-11-1432.txt", debugLogFileName(1_794_407_525_000))
    }
}
