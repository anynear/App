package app.anynear

import app.anynear.app.AppController
import app.anynear.app.PlatformHooks
import app.anynear.contacts.Contact
import app.anynear.mesh.ChannelSetupResult
import app.anynear.mesh.Conversation
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
 * Limits and drafts of the chat on the full app controller without relays: the internet limit counts characters, and
 * content shared into a chat waits for the chat instead of being written into its stored draft.
 */
class ChatDraftAppTest {
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

    private fun <T> withController(block: suspend CoroutineScope.(AppController) -> T): T = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val controller = testController(scope, NoRadio(), Hooks(), LocalNet.offlineConfig(), LocalNet.offlineConnector)
            withTimeout(10.seconds) { while (controller.myDeviceId.value == null) delay(20) }
            block(this, controller)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun internetLimitCountsCharactersNotBytes() = withController { controller ->
        val circle = Conversation.Circle("kreis")
        // 4096 umlauts are 8192 bytes, 4096 emojis 16384 bytes: both are exactly 4096 characters.
        assertEquals(0, controller.textLeft(circle, "ä".repeat(4096), emptyList()))
        assertEquals(0, controller.textLeft(circle, "😀".repeat(4096), emptyList()))
        assertEquals(-1, controller.textLeft(circle, "a".repeat(4097), emptyList()))
        assertEquals(4088, controller.textLeft(circle, "  Grüße 👋🏽  ", emptyList()))
    }

    @Test
    fun sharedContentWaitsForTheChatInsteadOfOverwritingItsDraft() = withController { controller ->
        // Tester report (round 2): shared into the chat that was open, the photo only showed up after a restart. The
        // stored draft is the chat's own; shared content waits until the chat takes it over, also when it is open.
        val circle = Conversation.Circle("kreis")
        controller.saveDraftText(circle, "Hallo")
        val file = AppController.SharedContent.File("Inhalt".encodeToByteArray(), "Plan.txt", image = false)
        controller.receiveShared(AppController.SharedContent("  Schau mal ", listOf(file)))
        assertEquals(null, controller.putSharedIntoDraft(circle))
        assertTrue(controller.shared.value == null)
        assertEquals("Hallo", controller.draftText(circle), "stored draft untouched")
        assertTrue(controller.draftAttachments(circle).isEmpty(), "stored draft untouched")
        val waiting = controller.draftAdditions.value[circle].orEmpty()
        assertEquals(listOf("Schau mal"), waiting.map { it.text })
        assertEquals(listOf("Plan.txt"), waiting.single().attachments.map { it.name })
        // Taken once by the chat, then gone.
        assertEquals(1, controller.takeDraftAdditions(circle).size)
        assertTrue(controller.takeDraftAdditions(circle).isEmpty())
        assertTrue(controller.draftAdditions.value[circle] == null)
    }

    @Test
    fun forwardingMarksTheAddition() = withController { controller ->
        val circle = Conversation.Circle("kreis")
        controller.receiveShared(AppController.SharedContent("Weiter", emptyList(), forward = true))
        controller.putSharedIntoDraft(circle)
        assertTrue(controller.takeDraftAdditions(circle).single().passedOn)
    }
}
