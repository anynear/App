package app.anynear

import app.anynear.app.PlatformHooks
import app.anynear.contacts.Contact
import app.anynear.mesh.ChannelSetupResult
import app.anynear.mesh.DeviceSetupCheck
import app.anynear.mesh.DeviceSetupResult
import app.anynear.mesh.IncomingText
import app.anynear.mesh.LinkState
import app.anynear.mesh.MeshLink
import app.anynear.mesh.NotReadyException
import app.anynear.mesh.OwnDevice
import app.anynear.mesh.PairedDevice
import app.anynear.mesh.RadioChannel
import app.anynear.mesh.RadioSend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * iOS restarts within the process (tester report: after the emergency card code the app closed): the old controller
 * ends completely, a new one on the same stores comes up as the same device. Full app controller without relays.
 */
class RestartInProcessAppTest {
    private class NoRadio : MeshLink {
        val stopped = java.util.concurrent.atomic.AtomicInteger()
        override val state: StateFlow<LinkState> = MutableStateFlow(LinkState.NoDevice)
        override val ownDevice: StateFlow<OwnDevice?> = MutableStateFlow(null)
        override val circleChannels: StateFlow<List<RadioChannel>?> = MutableStateFlow(null)
        override val knownNames: StateFlow<Map<Int, String>> = MutableStateFlow(emptyMap())
        override val incoming = emptyFlow<IncomingText>()
        override fun start(device: PairedDevice) = Unit
        override suspend fun stop() { stopped.incrementAndGet() }
        override suspend fun probe(device: PairedDevice) = false
        override fun send(text: String, to: Int?, channelIndex: Int): RadioSend = throw NotReadyException("kein Funkgerät")
        override suspend fun sendData(payload: ByteArray, to: Int?, channelIndex: Int): RadioSend = throw NotReadyException("kein Funkgerät")
        override val setupCheck: StateFlow<DeviceSetupCheck?> = MutableStateFlow(null)
        override suspend fun applyRadioProfile(nameSuffix: String, force: Boolean) = DeviceSetupResult.NotConnected
        override suspend fun renameDevice(nameSuffix: String) = DeviceSetupResult.NotConnected
        override suspend fun ensureChannel(name: String, psk: ByteArray) = ChannelSetupResult.NotConnected
        override suspend fun removeChannel(name: String) = false
        override fun ownContact(): Contact? = null
        override suspend fun registerContact(contact: Contact) = false
    }

    private class Hooks : PlatformHooks {
        override val bluetoothPermissionGranted = MutableStateFlow(true)
        override fun requestBluetoothPermission() = Unit
        override val internetAvailable = MutableStateFlow(true)
        override fun setBackgroundConnection(active: Boolean) = Unit
        override fun share(text: String) = Unit
    }

    @Test
    fun shutdownEndsTheControllerAndANewOneContinuesAsTheSameDevice() = runBlocking {
        val store = ConcurrentStore()
        val secrets = ConcurrentSecrets()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val radio = NoRadio()
            val first = testController(firstScope, radio, Hooks(), LocalNet.offlineConfig(), LocalNet.offlineConnector, store, secrets)
            val device = withTimeout(10.seconds) { while (first.myDeviceId.value == null) delay(20); first.myDeviceId.value }
            first.setOwnName("Testperson Anna")

            val stopsBefore = radio.stopped.get()
            first.shutdown()
            assertEquals(stopsBefore + 1, radio.stopped.get(), "Funkgerät getrennt")
            assertTrue(firstScope.coroutineContext[Job]!!.isCancelled, "alte Arbeit beendet")

            val second = testController(secondScope, NoRadio(), Hooks(), LocalNet.offlineConfig(), LocalNet.offlineConnector, store, secrets)
            withTimeout(10.seconds) { while (second.myDeviceId.value == null) delay(20) }
            assertEquals(device, second.myDeviceId.value, "dasselbe Gerät nach dem Neustart")
        } finally {
            firstScope.cancel()
            secondScope.cancel()
        }
    }
}
