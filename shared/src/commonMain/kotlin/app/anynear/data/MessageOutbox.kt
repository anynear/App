package app.anynear.data

import app.anynear.mesh.MessageStatus
import app.anynear.mesh.RadioSend
import app.anynear.mesh.Transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Queue for outgoing messages so that shaky connections stay invisible to the
 * user.
 *
 * Rules against duplicates:
 * - As long as the radio is not reachable, the message waits here („Wird gesendet …“).
 * - Only what the radio has **demonstrably not yet
 *   accepted** (never reached [MessageStatus.HANDED_TO_RADIO]) is resent automatically. Once it has
 *   accepted the message, the radio itself takes care of the delivery.
 *
 * Now only for Meshtastic text to radios without anynear, only in memory. ANMP messages wait permanently in the
 * queue of the ANMP core (ADR 0057).
 */
class MessageOutbox(
    private val scope: CoroutineScope,
    private val store: MessageStore,
    /** `true` if sending is possible now (radio connected). */
    private val canTransmit: StateFlow<Boolean>,
    /** Hands the message [localId] to the radio, in the channel of the circle [circleId]. Throws if that is not possible now. */
    private val transmit: suspend (localId: String, text: String, to: Int?, circleId: String) -> RadioSend,
    /** This long is waited for further states (e.g. delivery receipt) after the handover. */
    private val trackingWindow: Duration,
    private val maxAttempts: Int = 20,
    /** `false`: no longer give the message to the radio (e.g. long since delivered over the internet). */
    private val stillWanted: (localId: String) -> Boolean = { true },
) {
    private class Pending(val localId: String, val text: String, val to: Int?, val circleId: String, var attempts: Int = 0)

    private val queue = ArrayDeque<Pending>()
    private val mutex = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    fun start() {
        scope.launch {
            merge(wake.receiveAsFlow(), canTransmit.filter { it }.map { })
                .collect { drain() }
        }
    }

    fun enqueue(localId: String, text: String, to: Int?, circleId: String) {
        scope.launch {
            mutex.withLock { queue.addLast(Pending(localId, text, to, circleId)) }
            wake.trySend(Unit)
        }
    }

    /** Is nothing waiting for the radio any more? Before updating the radio (ADR 0056). */
    suspend fun isEmpty(): Boolean = mutex.withLock { queue.isEmpty() }

    /** Try again, e.g. because a circle channel has just been fully set up on the radio. */
    fun wake() {
        wake.trySend(Unit)
    }

    private suspend fun drain() {
        while (canTransmit.value) {
            val next = mutex.withLock { queue.firstOrNull() } ?: return
            if (!stillWanted(next.localId)) {
                mutex.withLock { queue.remove(next) }
                continue
            }
            val sent = try {
                transmit(next.localId, next.text, next.to, next.circleId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return // Not possible right now; continue on the next connect.
            }
            mutex.withLock { queue.remove(next) }
            next.attempts++
            if (sent.packetId != 0) store.setRadioPacket(next.localId, sent.packetId)
            scope.launch { track(next, sent.statuses) }
        }
    }

    private suspend fun track(pending: Pending, statuses: Flow<MessageStatus>) {
        var acceptedByRadio = false
        var requeue = false
        withTimeoutOrNull(trackingWindow) {
            statuses
                .takeWhile { status ->
                    if (status != MessageStatus.QUEUED && status != MessageStatus.FAILED) acceptedByRadio = true
                    // Radio never accepted the message (e.g. connection gone during the handover):
                    // silently queue it again, the display stays „Wird gesendet …“.
                    requeue = status == MessageStatus.FAILED && !acceptedByRadio && pending.attempts < maxAttempts
                    !requeue
                }
                .collect { store.updateStatus(pending.localId, it, Transport.RADIO) }
        }
        if (requeue) {
            mutex.withLock { queue.addFirst(pending) }
            wake.trySend(Unit)
        }
    }
}
