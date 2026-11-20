package app.anynear

import app.anynear.mesh.isClosedSdkChannel
import app.anynear.mesh.radioSdkContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.meshtastic.proto.Channel
import org.meshtastic.proto.NodeInfo
import org.meshtastic.sdk.ConfigBundle
import org.meshtastic.sdk.DeviceStorage
import org.meshtastic.sdk.Frame
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RadioTransport
import org.meshtastic.sdk.SessionPasskey
import org.meshtastic.sdk.StorageProvider
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.TransportState
import org.meshtastic.sdk.connectAndAwaitReady
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Crash report (test round 2): after „Handshake timed out during stage: connectAndAwaitReady“ the app ended with
 * `ClosedSendChannelException` from the SDK coroutine `meshtastic-transport-observer`. It only happens while that
 * coroutine is still active when the SDK's channel is already closed ([AbortingRadio] shows one way there).
 */
class RadioConnectCrashTest {
    /**
     * A radio whose connect is aborted inside the transport with a `CancellationException` (one way there; which way
     * the real BLE connection took in the report is not known). meshtastic-sdk 0.2.0 passes it on out of its engine
     * loop: the loop ends and closes its channel (`cleanup`), but its sibling coroutines under the same `SupervisorJob`
     * keep running. `cleanup` then disconnects the transport, and the coroutine watching the transport state sends the
     * new state into the closed channel. That only goes wrong while the caller of `connect` has not yet cancelled the
     * SDK's coroutines (a race between threads, more likely on a slow phone); [beforeAbort] holds the caller's thread
     * up until that send has failed ([sendFailed]), so that it happens every time.
     */
    private class AbortingRadio(private val beforeAbort: () -> Unit) : RadioTransport {
        override val identity = TransportIdentity("test:aborting")
        private val inner = MutableStateFlow<TransportState>(TransportState.Disconnected)

        /** Released once the SDK failed to pass a state on (its channel was closed), the moment of the crash. */
        val sendFailed = CountDownLatch(1)

        @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
        override val state: StateFlow<TransportState> = object : StateFlow<TransportState> {
            override val value get() = inner.value
            override val replayCache get() = inner.replayCache
            override suspend fun collect(collector: FlowCollector<TransportState>): Nothing = inner.collect { state ->
                try {
                    collector.emit(state)
                } catch (e: Throwable) {
                    sendFailed.countDown()
                    throw e
                }
            }
        }

        override suspend fun connect() {
            inner.value = TransportState.Connecting
            beforeAbort()
            throw CancellationException("connect aborted by the transport")
        }
        override suspend fun disconnect() {
            // A state the SDK cannot have passed on before (a new exception each time). StateFlow only passes on what
            // differs from the last value: if the SDK passed on the initial „Disconnected“ and then missed „Connecting“
            // (its coroutine was slower than the engine, as on a busy CI machine), a final „Disconnected“ would never
            // be sent, and the test would wait in vain.
            inner.value = TransportState.Error(IllegalStateException("disconnected"), recoverable = false)
        }
        override suspend fun send(frame: Frame) = Unit
        override fun frames(): Flow<Frame> = flow { awaitCancellation() }
    }

    private object NoStorage : StorageProvider {
        override suspend fun activate(identity: TransportIdentity): DeviceStorage = object : DeviceStorage {
            override suspend fun loadNodes(): Map<NodeId, NodeInfo> = emptyMap()
            override suspend fun saveNode(node: NodeInfo) = Unit
            override suspend fun removeNode(nodeId: NodeId) = Unit
            override suspend fun loadConfig(): ConfigBundle? = null
            override suspend fun saveConfig(config: ConfigBundle) = Unit
            override suspend fun loadChannels(): List<Channel> = emptyList()
            override suspend fun saveChannels(channels: List<Channel>) = Unit
            override suspend fun recordOwnNode(nodeNum: NodeId, firmwareVersion: String) = Unit
            override suspend fun clear() = Unit
            override suspend fun saveSessionPasskey(passkey: SessionPasskey) = Unit
            override suspend fun loadSessionPasskey(): SessionPasskey? = null
            override suspend fun saveHeartbeat(nodeId: NodeId, epochMillis: Long) = Unit
            override suspend fun loadHeartbeats(): Map<NodeId, Long> = emptyMap()
            override fun close() = Unit
        }
    }

    /**
     * As the app's connection loop: connect (fails), then disconnect; with [context] for the SDK's coroutines. Waits
     * until the exception handler of [context] has put something into [handled].
     */
    private fun connectAndFail(context: CoroutineContext, handled: List<Any>) = runBlocking {
        val callerThread = Executors.newSingleThreadExecutor()
        try {
            // The caller's thread is busy right when the transport gives up, until the SDK's send has failed.
            lateinit var radio: AbortingRadio
            radio = AbortingRadio(beforeAbort = { callerThread.execute { radio.sendFailed.await(10, TimeUnit.SECONDS) } })
            val rc = RadioClient.Builder().transport(radio).storage(NoStorage).skipNodeDb().coroutineContext(context).build()
            withContext(callerThread.asCoroutineDispatcher()) {
                // The app's connection loop gets an ordinary failure and tries again.
                assertFailsWith<MeshtasticException.Transport> { rc.connectAndAwaitReady(timeout = 2.seconds) }
                rc.disconnect()
            }
            // The exception handler runs right after the failed send, on Dispatchers.Default.
            withTimeout(10.seconds) { while (handled.isEmpty()) delay(20) }
        } finally {
            callerThread.shutdown()
        }
    }

    @Test
    fun withTheSdkDefaultTheClosedChannelIsUncaught() {
        // The SDK's default context (SupervisorJob + Dispatchers.Default) has no handler: the exception would reach
        // the platform and end the app. Recorded here instead, so that it does not leak into other tests.
        val uncaught = Collections.synchronizedList(mutableListOf<Pair<String?, Throwable>>())
        connectAndFail(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { context, e -> uncaught += context[CoroutineName]?.name to e }, uncaught)
        // As in the crash report: from the coroutine that watches the transport state.
        val (coroutine, crash) = uncaught.single()
        assertEquals("meshtastic-transport-observer", coroutine)
        assertTrue(crash is ClosedSendChannelException, crash.stackTraceToString())
    }

    @Test
    fun withTheAppContextItIsOnlyLogged() {
        val swallowed = Collections.synchronizedList(mutableListOf<Throwable>())
        connectAndFail(radioSdkContext { swallowed += it }, swallowed)
        assertTrue(swallowed.single() is ClosedSendChannelException, "logged: $swallowed")
    }

    @Test
    fun onlyThisCaseIsCaught() {
        assertTrue(isClosedSdkChannel(CoroutineName("meshtastic-transport-observer"), ClosedSendChannelException("closed")))
        // Other errors of the SDK and the same error in the app's own coroutines still end in a crash report (ADR 0082).
        assertFalse(isClosedSdkChannel(CoroutineName("meshtastic-engine-actor"), IllegalStateException("bug")))
        assertFalse(isClosedSdkChannel(CoroutineName("app"), ClosedSendChannelException("closed")))
        assertFalse(isClosedSdkChannel(EmptyCoroutineContext, ClosedSendChannelException("closed")))
    }
}
