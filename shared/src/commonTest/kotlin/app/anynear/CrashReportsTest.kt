package app.anynear

import app.anynear.app.CrashRecorder
import app.anynear.app.CrashReport
import app.anynear.app.CrashReports
import app.anynear.data.TextFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReportsTest {
    private class MemoryFile(var content: String? = null) : TextFile {
        override fun read() = content
        override fun write(content: String) {
            this.content = content
        }
    }

    private fun report(at: Long, error: String = "java.lang.IllegalStateException: kaputt", trace: String = "at app.anynear.A.b(A.kt:1)") =
        CrashReport(CrashReport.Kind.Crash, at, "0.3.0-abc1234", "android", error = error, trace = trace)

    @Test
    fun scrubRemovesLinkSecretsKeysAndDataDirs() {
        val text = "open https://anynear.app/k#Zm9vYmFyYmF6cXV4MTIzNDU2Nzg5MA and key 0123456789abcdef0123456789abcdef in /data/user/0/app.anynear/files/x.bin"
        val clean = CrashReports.scrub(text, listOf("/data/user/0/app.anynear"))
        assertEquals("open https://anynear.app/k#… and key … in …/files/x.bin", clean)
    }

    @Test
    fun scrubKeepsTraceLinesAndUuids() {
        val trace = "\tat app.anynear.app.AppController\$start\$1.invokeSuspend(AppController.kt:812)\n" +
            "{\"binaryUUID\":\"3F2504E0-4F89-11D3-9A0C-0305E82C3301\",\"address\":4310012345}"
        assertEquals(trace, CrashReports.scrub(trace))
    }

    @Test
    fun sameCrashesAreMergedWithCount() {
        var list = CrashReports.add(emptyList(), report(1_000), now = 1_000)
        list = CrashReports.add(list, report(2_000, error = "java.lang.IllegalStateException: anderer Text"), now = 2_000)
        assertEquals(1, list.size)
        assertEquals(2, list.single().count)
        assertEquals(1_000, list.single().atMillis)
        assertEquals(2_000, list.single().lastMillis)
        list = CrashReports.add(list, report(3_000, trace = "at app.anynear.C.d(C.kt:9)"), now = 3_000)
        assertEquals(2, list.size)
    }

    @Test
    fun keepsAtMostFiveAndDropsOldOnes() {
        var list = emptyList<CrashReport>()
        repeat(7) { i -> list = CrashReports.add(list, report(i * 1_000L, trace = "at app.anynear.X.f$i(X.kt:$i)"), now = i * 1_000L) }
        assertEquals(CrashReports.MAX_REPORTS, list.size)
        assertEquals(6_000, list.last().lastMillis)
        val later = 6_000 + CrashReports.MAX_AGE_MILLIS + 1
        list = CrashReports.add(list, report(later, trace = "at app.anynear.Y.g(Y.kt:1)"), now = later)
        assertEquals(1, list.size)
    }

    @Test
    fun clipKeepsTextUnderLimit() {
        val big = report(0).copy(log = List(500) { "-${it}s Tag: " + "x".repeat(300) })
        val clipped = CrashReports.clip(big)
        assertTrue(CrashReports.text(clipped).encodeToByteArray().size <= CrashReports.MAX_BYTES)
        assertTrue(clipped.log.size <= CrashReports.LOG_LINES)
    }

    @Test
    fun textHasKindBuildAndLog() {
        val text = CrashReports.text(report(0).copy(count = 3, lastMillis = 86_400_000, log = listOf("-2s Start: los")))
        val lines = text.lines()
        assertEquals("kind: Crash", lines[0])
        assertEquals("first: 1970-01-01T00:00:00Z", lines[1])
        assertEquals("last: 1970-01-02T00:00:00Z (3x)", lines[2])
        assertEquals("build: 0.3.0-abc1234", lines[3])
        assertTrue(text.contains("-2s Start: los"))
    }

    @Test
    fun isoDateIsCorrectAfter2000() {
        // 08.11.2026 12:34:56 UTC
        assertEquals("2026-11-08T12:34:56Z", CrashReports.isoUtc(1_794_141_296_000))
    }

    @Test
    fun anrKeepsOnlyMainThread() {
        val traces = "----- pid 1 -----\n\n\"Signal Catcher\" daemon\n  at x\n\n\"main\" prio=5 tid=1 Blocked\n  at app.anynear.A.b(A.kt:1)\n\n\"other\"\n  at y\n"
        assertEquals("\"main\" prio=5 tid=1 Blocked\n  at app.anynear.A.b(A.kt:1)", CrashReports.anrMainThread(traces))
    }

    @Test
    fun recorderWritesSurvivesRestartAndClears() {
        val file = MemoryFile()
        var now = 10_000L
        val recorder = CrashRecorder(file, "android", { "0.3.0-abc1234" }, "Android 16", "Pixel", listOf("/data/app"), { now })
        recorder.logLines = { listOf("-1s Lesen: /data/app/files/a") }
        recorder.record(IllegalStateException("kaputt"))
        assertEquals(1, recorder.reports.value.size)
        assertEquals("-1s Lesen: …/files/a", recorder.reports.value.single().log.single())
        assertTrue(recorder.reports.value.single().trace.contains("CrashReportsTest"))
        val again = CrashRecorder(file, "android", { "0.3.0-abc1234" }, null, null, now = { now })
        assertEquals(1, again.reports.value.size)
        assertTrue(again.hasCrashBetween(9_000, 11_000))
        assertFalse(again.hasCrashBetween(11_001, 20_000))
        again.clear()
        assertTrue(CrashRecorder(file, "android", { null }, null, null, now = { now }).reports.value.isEmpty())
    }

    @Test
    fun brokenFileGivesNoReportsInsteadOfCrash() {
        val recorder = CrashRecorder(MemoryFile("{kaputt"), "ios", { null }, null, null, now = { 0 })
        assertTrue(recorder.reports.value.isEmpty())
    }

    @Test
    fun cardShowsOnlyCrashesOfTheRunningVersion() {
        // Tablet 15.11.2026: three crashes of 10026, fixed in 10030, still on the card after the update.
        val old = report(1_000).copy(build = "0.9.0-pre.10026", count = 3)
        assertEquals(emptyList(), CrashReports.forCard(listOf(old), "0.9.0-pre.10030"))
        // The same crash again in the new version merges and takes its build: on the card again.
        val again = CrashReports.add(listOf(old), report(2_000).copy(build = "0.9.0-pre.10030"), now = 2_000)
        assertEquals(4, CrashReports.forCard(again, "0.9.0-pre.10030").single().count)
        // Version unknown: all of them.
        assertEquals(listOf(old), CrashReports.forCard(listOf(old), null))
    }
}
