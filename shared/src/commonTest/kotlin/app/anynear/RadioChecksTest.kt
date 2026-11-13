package app.anynear

import app.anynear.app.RadioChecks
import app.anynear.app.SignalLevel
import app.anynear.mesh.NotReadyException
import app.anynear.mesh.RadioCheckReply
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Radio check (ADR 0053): result, upper limits, gap between packets, stored result. */
@OptIn(ExperimentalCoroutinesApi::class)
class RadioChecksTest {
    private val start = 1_800_000_000_000L

    private class Radio(val test: TestScope) {
        val answers = mutableMapOf<Int, RadioCheckReply?>()
        val sentAt = mutableListOf<Pair<Int, Long>>()
        var ready = true

        suspend fun send(node: Int, timeout: Duration): RadioCheckReply? {
            if (!ready) throw NotReadyException("weg")
            sentAt += node to test.testScheduler.currentTime
            val answer = answers[node]
            delay(if (answer == null) timeout else 3.seconds)
            return answer
        }
    }

    private fun TestScope.checks(radio: Radio, store: InMemoryStore = InMemoryStore()) =
        RadioChecks(backgroundScope, store, { start + testScheduler.currentTime }, radio::send)

    /** The checks run in the backgroundScope, which advanceUntilIdle does not wait for. */
    private fun TestScope.settle() {
        advanceTimeBy(5.minutes)
        runCurrent()
    }

    @Test
    fun answerAndNoAnswerAreRemembered() = runTest {
        val radio = Radio(this).apply { answers[5] = RadioCheckReply(hops = 2, snr = -3f, rssi = -100) }
        val checks = checks(radio)
        assertTrue(checks.check(listOf(5, 6)))
        assertEquals(setOf(5, 6), checks.running.value)
        settle()
        val five = checks.results.value.getValue(5)
        assertTrue(five.answered)
        assertEquals(2, five.hops)
        assertEquals(3_000L, five.durationMillis)
        assertFalse(checks.results.value.getValue(6).answered)
        assertEquals(emptySet(), checks.running.value)
    }

    @Test
    fun atMostOneCheckPerRadioEveryTenMinutes() = runTest {
        val radio = Radio(this).apply { answers[5] = RadioCheckReply(0, 2f, null) }
        val checks = checks(radio)
        checks.check(listOf(5))
        settle()
        assertFalse(checks.check(listOf(5)), "zu früh")
        assertEquals(1, radio.sentAt.size)
        advanceTimeBy(10.minutes)
        assertNull(checks.nextCheckAt(5))
        assertTrue(checks.check(listOf(5)))
        settle()
        assertEquals(2, radio.sentAt.size)
    }

    @Test
    fun groupCheckOncePerHourWithGapBetweenPackets() = runTest {
        val radio = Radio(this).apply { listOf(1, 2, 3).forEach { answers[it] = RadioCheckReply(1, 0f, null) } }
        val checks = checks(radio)
        assertTrue(checks.checkGroup("circle:a", listOf(1, 2, 3)))
        assertFalse(checks.checkGroup("circle:a", listOf(1, 2, 3)), "läuft schon")
        settle()
        val times = radio.sentAt.map { it.second }
        assertEquals(3, times.size)
        times.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 10_000, "mindestens 10 s Abstand") }
        assertFalse(checks.checkGroup("circle:a", listOf(1, 2, 3)), "einmal pro Stunde")
        // Another group may, but fresh results are not sent again.
        assertTrue(checks.checkGroup("all", listOf(1, 2, 3)))
        settle()
        assertEquals(3, radio.sentAt.size)
    }

    @Test
    fun withoutConnectedRadioNothingIsInvented() = runTest {
        val radio = Radio(this).apply { ready = false }
        val checks = checks(radio)
        checks.check(listOf(5, 6))
        settle()
        assertTrue(checks.results.value.isEmpty())
        assertEquals(emptySet(), checks.running.value)
        assertTrue(checks.check(listOf(5)), "darf gleich wieder")
    }

    @Test
    fun lastResultSurvivesRestart() = runTest {
        val store = InMemoryStore()
        val radio = Radio(this).apply { answers[5] = RadioCheckReply(0, 1f, -90) }
        checks(radio, store).check(listOf(5))
        settle()
        val again = checks(radio, store)
        assertEquals(-90, again.results.value.getValue(5).rssi)
        assertFalse(again.check(listOf(5)), "Obergrenze gilt auch nach Neustart")
    }

    @Test
    fun signalLevels() {
        assertEquals(SignalLevel.GOOD, SignalLevel.of(0f))
        assertEquals(SignalLevel.MEDIUM, SignalLevel.of(-7.5f))
        assertEquals(SignalLevel.WEAK, SignalLevel.of(-8f))
    }
}
