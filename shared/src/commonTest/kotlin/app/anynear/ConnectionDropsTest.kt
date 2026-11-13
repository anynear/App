package app.anynear

import app.anynear.mesh.ConnectionDrops
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionDropsTest {
    private val s = 1_000L

    @Test
    fun anotherAppTakingTheRadioEveryMinuteIsUnstable() {
        // Hardware test 02.11.2026: connected, disconnected after 60 s, connected again 3 s later, over and over.
        val drops = ConnectionDrops()
        var t = 0L
        repeat(2) {
            drops.connected(t)
            t += 60 * s
            drops.lost(t)
            t += 3 * s
        }
        assertFalse(drops.unstable(t), "zwei Abbrüche sind noch Zufall")
        drops.connected(t)
        t += 60 * s
        drops.lost(t)
        assertTrue(drops.unstable(t))
    }

    @Test
    fun aConnectionThatHoldsEndsIt() {
        val drops = ConnectionDrops()
        var t = 0L
        repeat(3) {
            drops.connected(t)
            t += 60 * s
            drops.lost(t)
            t += 3 * s
        }
        drops.connected(t)
        assertTrue(drops.unstable(t + 30 * s))
        assertFalse(drops.unstable(t + 120 * s), "hält zwei Minuten")
    }

    @Test
    fun rareLongConnectionsAreNotUnstable() {
        // Radio briefly off or out of range, but every connection lasted long.
        val drops = ConnectionDrops()
        var t = 0L
        repeat(5) {
            drops.connected(t)
            t += 10 * 60 * s
            drops.lost(t)
            t += 5 * s
        }
        assertFalse(drops.unstable(t))
    }

    @Test
    fun oldDropsExpire() {
        val drops = ConnectionDrops()
        var t = 0L
        repeat(3) {
            drops.connected(t)
            t += 60 * s
            drops.lost(t)
            t += 3 * s
        }
        assertTrue(drops.unstable(t))
        assertFalse(drops.unstable(t + 6 * 60 * s), "nach fünf Minuten ohne Abbruch vorbei")
    }

    @Test
    fun afterTakingOverThisPhoneStaysPut() {
        // „Hier verbinden“: the other anynear is kicked out and reconnects a few times; this phone must not step back.
        val drops = ConnectionDrops()
        var t = 0L
        repeat(3) {
            drops.connected(t)
            t += 60 * s
            drops.lost(t)
            t += 3 * s
        }
        assertTrue(drops.unstable(t))
        drops.takenOver(t, kotlin.time.Duration.parse("10m"))
        repeat(3) {
            drops.connected(t)
            t += 60 * s
            drops.lost(t)
            t += 3 * s
        }
        assertFalse(drops.unstable(t), "nach dem Übernehmen bleibt dieses Handy dran")
        t += 10 * 60 * s
        repeat(3) {
            drops.connected(t)
            t += 60 * s
            drops.lost(t)
            t += 3 * s
        }
        assertTrue(drops.unstable(t), "danach zählt es wieder")
    }
}
