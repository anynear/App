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
import kotlin.test.assertTrue
import app.anynear.app.Reminder
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Radio on Wi-Fi taken by another phone or app (hardware test 02.11.2026): after a few quick drops the app steps back
 * instead of kicking the other one out every minute, and asks. „Hier verbinden“ connects again. Full app controller
 * without relays.
 */
class RadioInUseAppTest {
    private class WifiRadio : MeshLink {
        val started = java.util.concurrent.atomic.AtomicInteger()
        val yielded = java.util.concurrent.atomic.AtomicInteger()
        override val state = MutableStateFlow<LinkState>(LinkState.Connecting)
        override val ownDevice: StateFlow<OwnDevice?> = MutableStateFlow(null)
        override val circleChannels: StateFlow<List<RadioChannel>?> = MutableStateFlow(null)
        override val knownNames: StateFlow<Map<Int, String>> = MutableStateFlow(emptyMap())
        override val incoming = emptyFlow<IncomingText>()
        override fun start(device: PairedDevice) {
            started.incrementAndGet()
            state.value = LinkState.Connecting
        }
        override suspend fun stop() { state.value = LinkState.NoDevice }
        override suspend fun yieldRadio() {
            yielded.incrementAndGet()
            state.value = LinkState.InUseElsewhere
        }
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

        /** Connected, kicked out shortly after, as when another app takes the radio. */
        suspend fun displaced(times: Int) = repeat(times) {
            state.value = LinkState.Connected
            delay(60)
            state.value = LinkState.Lost
            delay(60)
        }
    }

    private class Hooks : PlatformHooks {
        val reminders = java.util.concurrent.CopyOnWriteArrayList<Reminder>()
        override val bluetoothPermissionGranted = MutableStateFlow(false)
        override fun requestBluetoothPermission() = Unit
        override val internetAvailable = MutableStateFlow(true)
        override fun setBackgroundConnection(active: Boolean) = Unit
        override fun share(text: String) = Unit
        // Only this one: the charge reminder (ADR 0085) also schedules itself on connecting.
        override fun showReminder(reminder: Reminder) { if (reminder.id == AppController.RADIO_IN_USE_ID) reminders += reminder }
    }

    private class App(val controller: AppController, val hooks: Hooks, val radio: WifiRadio)

    private fun app(scope: CoroutineScope): App = runBlocking {
        val store = ConcurrentStore()
        PairedDeviceRepository(store).pair(PairedDevice("tcp:192.168.1.20:4403", "anynear Test"))
        val hooks = Hooks()
        val radio = WifiRadio()
        val controller = testController(
            scope, radio, hooks, LocalNet.offlineConfig(), LocalNet.offlineConnector, store = store,
            scanNetwork = { flow { awaitCancellation() } },
        )
        withTimeout(10.seconds) { while (controller.myDeviceId.value == null) delay(20) }
        controller.setOwnName("Testperson Anna")
        controller.finishSetup()
        withTimeout(5.seconds) { while (radio.started.get() == 0) delay(20) }
        App(controller, hooks, radio)
    }

    private suspend fun eventually(check: () -> Boolean) = withTimeout(5.seconds) { while (!check()) delay(20) }

    @Test
    fun stepsBackAndAsksInsteadOfFighting() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val app = app(scope)
            val c = app.controller
            app.radio.displaced(2)
            delay(200)
            assertEquals(0, app.radio.yielded.get(), "zwei Abbrüche sind noch Zufall")
            app.radio.displaced(1)
            eventually { c.linkState.value == LinkState.InUseElsewhere && c.radioInUsePrompt.value }
            assertTrue(c.radioInUseElsewhere.value)
            assertTrue(app.hooks.reminders.isEmpty(), "App im Vordergrund: die Frage reicht, keine Benachrichtigung")

            // „Dort lassen“: no more question, still stepped back, no new connection.
            c.leaveRadioElsewhere()
            eventually { !c.radioInUsePrompt.value }
            assertEquals(1, app.radio.started.get())

            // „Hier verbinden“: connects again, and the next drops right after do not make this phone step back.
            c.takeOverRadio()
            eventually { app.radio.started.get() == 2 && !c.radioInUseElsewhere.value }
            app.radio.displaced(3)
            delay(200)
            assertEquals(1, app.radio.yielded.get(), "nach dem Übernehmen bleibt dieses Handy dran")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun inTheBackgroundANotificationAsks() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val app = app(scope)
            app.controller.setInForeground(false)
            app.radio.displaced(3)
            eventually { app.hooks.reminders.isNotEmpty() }
            assertEquals(1, app.hooks.reminders.size)
            assertTrue(app.controller.radioInUsePrompt.value, "beim Öffnen kommt die Frage")
        } finally {
            scope.cancel()
        }
    }
}
