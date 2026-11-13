package app.anynear.mesh

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Detects a connection to the radio that keeps dropping shortly after connecting (hardware test 02.11.2026).
 * On Wi-Fi the firmware serves only one connection: if another device connects (such as the Meshtastic app or a
 * second anynear), anynear is kicked out, reconnects and displaces the other one, and so it goes back and forth every
 * minute. Once detected, the app steps back ([MeshLink.yieldRadio]) and asks.
 *
 * Unstable means: at least [needed] drops within [window], each after a connection shorter than [shortSession].
 * Once a connection lasts [shortSession], it is over.
 */
class ConnectionDrops(
    private val window: Duration = 5.minutes,
    private val shortSession: Duration = 2.minutes,
    private val needed: Int = 3,
) {
    private var connectedAt: Long? = null
    private val drops = ArrayDeque<Long>()
    private var holdUntil = 0L

    fun connected(nowMillis: Long) {
        connectedAt = nowMillis
    }

    fun lost(nowMillis: Long) {
        val since = connectedAt ?: return
        connectedAt = null
        if (nowMillis - since < shortSession.inWholeMilliseconds) drops.addLast(nowMillis)
    }

    /**
     * Taken over by hand: forget the drops so far and do not count as unstable for [hold]. The other device is
     * kicked out and, if it is an anynear, reconnects a few times until it steps back itself; this phone stays put.
     */
    fun takenOver(nowMillis: Long, hold: Duration) {
        drops.clear()
        connectedAt = null
        holdUntil = nowMillis + hold.inWholeMilliseconds
    }

    fun unstable(nowMillis: Long): Boolean {
        while (drops.isNotEmpty() && nowMillis - drops.first() > window.inWholeMilliseconds) drops.removeFirst()
        if (nowMillis < holdUntil) return false
        val holding = connectedAt?.let { nowMillis - it >= shortSession.inWholeMilliseconds } == true
        return !holding && drops.size >= needed
    }
}
