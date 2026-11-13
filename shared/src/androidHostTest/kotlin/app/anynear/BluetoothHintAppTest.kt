package app.anynear

import app.anynear.contacts.Contact
import app.anynear.app.AppController
import app.anynear.app.PlatformHooks
import app.anynear.attachments.AttachmentFiles
import app.anynear.attachments.BinaryFiles
import app.anynear.circles.CircleRepository
import app.anynear.circles.ProfileRepository
import app.anynear.contacts.ContactRepository
import app.anynear.data.DraftStore
import app.anynear.data.MessageStore
import app.anynear.data.PairedDeviceRepository
import app.anynear.images.AvatarImages
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
import app.anynear.core.b64
import app.anynear.people.MemberDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Radio stored, but Bluetooth not allowed (e.g. after reinstalling or restoring):
 * the app asks for it instead of silently not connecting. Full app controller without relays.
 */
class BluetoothHintAppTest {
    private class NoRadio : MeshLink {
        val started = java.util.concurrent.atomic.AtomicInteger()
        override val state: StateFlow<LinkState> = MutableStateFlow(LinkState.Connecting)
        override val ownDevice: StateFlow<OwnDevice?> = MutableStateFlow(null)
        override val circleChannels: StateFlow<List<RadioChannel>?> = MutableStateFlow(null)
        override val knownNames: StateFlow<Map<Int, String>> = MutableStateFlow(emptyMap())
        override val incoming = emptyFlow<IncomingText>()
        override fun start(device: PairedDevice) { started.incrementAndGet() }
        override suspend fun stop() = Unit
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

    /** Bluetooth not allowed at first; „Erlauben“ counts the system prompts. */
    private class Hooks : PlatformHooks {
        val granted = MutableStateFlow(false)
        var asked = 0
        override val bluetoothPermissionGranted = granted
        override fun requestBluetoothPermission() { asked++ }
        override val internetAvailable = MutableStateFlow(true)
        override fun setBackgroundConnection(active: Boolean) = Unit
        override fun share(text: String) = Unit
    }

    private class App(val controller: AppController, val hooks: Hooks, val radio: NoRadio)

    private fun app(scope: CoroutineScope, device: PairedDevice): App = runBlocking {
        val store = ConcurrentStore()
        // As after a restore: the radio is already stored.
        PairedDeviceRepository(store).pair(device)
        val hooks = Hooks()
        val radio = NoRadio()
        val controller = testController(
            scope, radio, hooks, LocalNet.offlineConfig(), LocalNet.offlineConnector, store = store,
            scanNetwork = { flow { awaitCancellation() } },
        )
        withTimeout(10.seconds) { while (controller.myDeviceId.value == null) delay(20) }
        controller.setOwnName("Testperson Anna")
        controller.finishSetup()
        App(controller, hooks, radio)
    }

    private suspend fun eventually(check: () -> Boolean) = withTimeout(5.seconds) { while (!check()) delay(20) }

    @Test
    fun bluetoothRadioWithoutPermissionAsksForIt() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val app = app(scope, PairedDevice("AA:BB:CC:DD:EE:FF", "anynear Test"))
            val c = app.controller
            eventually { c.linkState.value == LinkState.BluetoothUnavailable && c.bluetoothHint.value }
            assertEquals(0, app.radio.started.get(), "ohne Erlaubnis keine Verbindung")
            // „Erlauben“: the system prompt once, after that no card any more (as with notifications).
            c.allowBluetoothFromHint()
            assertEquals(1, app.hooks.asked)
            eventually { !c.bluetoothHint.value }
            // Allowed: connect, the state comes from the radio.
            app.hooks.granted.value = true
            eventually { app.radio.started.get() == 1 && c.linkState.value == LinkState.Connecting }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun laterHidesTheCard() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val c = app(scope, PairedDevice("AA:BB:CC:DD:EE:FF", "anynear Test")).controller
            eventually { c.bluetoothHint.value }
            c.postponeBluetoothHint()
            eventually { !c.bluetoothHint.value }
            // The radio page keeps offering it: the state stays „Bluetooth nicht erlaubt“.
            assertEquals(LinkState.BluetoothUnavailable, c.linkState.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun wifiRadioNeedsNoBluetooth() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val app = app(scope, PairedDevice("tcp:192.168.1.20:4403", "anynear Test"))
            eventually { app.radio.started.get() == 1 }
            delay(200)
            assertFalse(app.controller.bluetoothHint.value, "WLAN-Funkgerät braucht kein Bluetooth")
        } finally {
            scope.cancel()
        }
    }
}
