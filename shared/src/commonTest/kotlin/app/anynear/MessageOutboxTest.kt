package app.anynear

import app.anynear.data.MessageOutbox
import app.anynear.data.MessageStore
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.RadioSend
import app.anynear.mesh.MessageStatus.FAILED
import app.anynear.mesh.MessageStatus.HANDED_TO_RADIO
import app.anynear.mesh.MessageStatus.QUEUED
import app.anynear.mesh.MessageStatus.RELAYED
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class MessageOutboxTest {
    private val store = MessageStore()
    private val online = MutableStateFlow(false)
    private val sent = mutableListOf<String>()

    /** Answers of the radio per handover attempt. */
    private val answers = ArrayDeque<List<MessageStatus>>()

    private fun TestScope.outbox(): MessageOutbox {
        store.addOutgoing(ChatMessage("m1", Conversation.Circle("k1"), 1, "Hallo", 0, outgoing = true, status = QUEUED))
        return MessageOutbox(
            scope = backgroundScope,
            store = store,
            canTransmit = online,
            transmit = { _, text, _, _ ->
                check(online.value) { "nicht verbunden" }
                sent += text
                RadioSend(sent.size, flowOf(*(answers.removeFirstOrNull() ?: listOf(HANDED_TO_RADIO, RELAYED)).toTypedArray()))
            },
            trackingWindow = 2.minutes,
        ).also { it.start() }
    }

    private val status get() = store.messages.value.single().status

    @Test
    fun waitsWhileOfflineAndSendsOnceOnReconnect() = runTest {
        val outbox = outbox()
        outbox.enqueue("m1", "Hallo", to = null, circleId = "k1")
        runCurrent()
        assertEquals(emptyList(), sent)
        assertEquals(QUEUED, status)

        online.value = true
        runCurrent()
        assertEquals(listOf("Hallo"), sent)
        assertEquals(RELAYED, status)
    }

    @Test
    fun retriesSilentlyWhenRadioNeverAcceptedIt() = runTest {
        online.value = true
        answers += listOf(QUEUED, FAILED) // Connection dropped during handover
        val outbox = outbox()
        outbox.enqueue("m1", "Hallo", to = null, circleId = "k1")
        runCurrent()

        assertEquals(listOf("Hallo", "Hallo"), sent)
        assertEquals(RELAYED, status)
    }

    @Test
    fun neverResendsWhatTheRadioAlreadyAccepted() = runTest {
        online.value = true
        answers += listOf(HANDED_TO_RADIO, FAILED)
        val outbox = outbox()
        outbox.enqueue("m1", "Hallo", to = null, circleId = "k1")
        runCurrent()

        assertEquals(listOf("Hallo"), sent)
        // A real failure after acceptance by the radio is shown honestly but not retried.
        assertEquals(FAILED, status)
    }
}
