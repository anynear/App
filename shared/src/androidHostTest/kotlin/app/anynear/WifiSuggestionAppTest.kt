package app.anynear

import app.anynear.app.AppController
import app.anynear.app.PlatformHooks
import app.anynear.contacts.Contact
import app.anynear.data.PairedDeviceRepository
import app.anynear.mesh.ChannelSetupResult
import app.anynear.mesh.DeviceSetupCheck
import app.anynear.mesh.DeviceSetupResult
import app.anynear.mesh.FoundDevice
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * The search on Wi-Fi on its own (suggestion of a radio): on Android 17 every search without the local network
 * permission opens the system dialog „Gerät zum Verbinden auswählen“. People without a radio must never see it
 * unasked, so the app only searches on Wi-Fi by itself once a radio was used here and the permission is there.
 */
class WifiSuggestionAppTest {
    private class NoRadio : MeshLink {
        override val state: StateFlow<LinkState> = MutableStateFlow(LinkState.Connecting)
        override val ownDevice: StateFlow<OwnDevice?> = MutableStateFlow(null)
        override val circleChannels: StateFlow<List<RadioChannel>?> = MutableStateFlow(null)
        override val knownNames: StateFlow<Map<Int, String>> = MutableStateFlow(emptyMap())
        override val incoming = emptyFlow<IncomingText>()
        override fun start(device: PairedDevice) = Unit
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

    /** Local network permission as on Android 17; asking grants it if [grantOnAsk]. */
    private class Hooks(allowed: Boolean, private val grantOnAsk: Boolean = true) : PlatformHooks {
        val localNetwork = MutableStateFlow(allowed)
        var asked = 0
        override val bluetoothPermissionGranted = MutableStateFlow(false)
        override fun requestBluetoothPermission() = Unit
        override val localNetworkAllowed: StateFlow<Boolean> = localNetwork
        override suspend fun requestLocalNetworkPermission(): Boolean {
            asked++
            if (grantOnAsk) localNetwork.value = true
            return localNetwork.value
        }
        override val internetAvailable = MutableStateFlow(true)
        override fun setBackgroundConnection(active: Boolean) = Unit
        override fun share(text: String) = Unit
    }

    private class App(val controller: AppController, val hooks: Hooks, val searches: AtomicInteger)

    private val wifiRadio = FoundDevice("tcp:192.168.1.20:4403", "anynear Test", rssi = null, connection = FoundDevice.Connection.NETWORK)

    /** [usedBefore]: a radio was assigned here before and released again (still under „Meine Funkgeräte“). */
    private fun app(scope: CoroutineScope, hooks: Hooks, usedBefore: Boolean): App = runBlocking {
        val store = ConcurrentStore()
        if (usedBefore) {
            PairedDeviceRepository(store).apply {
                pair(PairedDevice("AA:BB:CC:DD:EE:FF", "anynear Alt"))
                unpair()
            }
        }
        val searches = AtomicInteger()
        val controller = testController(
            scope, NoRadio(), hooks, LocalNet.offlineConfig(), LocalNet.offlineConnector, store = store,
            scanNetwork = {
                flow {
                    searches.incrementAndGet()
                    emit(wifiRadio)
                    awaitCancellation()
                }
            },
        )
        withTimeout(10.seconds) { while (controller.myDeviceId.value == null) delay(20) }
        controller.setOwnName("Testperson Anna")
        controller.finishSetup()
        App(controller, hooks, searches)
    }

    private suspend fun eventually(check: () -> Boolean) = withTimeout(5.seconds) { while (!check()) delay(20) }

    private fun withScope(block: suspend (CoroutineScope) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            block(scope)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun withoutRadioNoSearchOnWifiByItself() = withScope { scope ->
        // Even with the permission: who never used a radio gets no search on Wi-Fi on their own.
        val app = app(scope, Hooks(allowed = true), usedBefore = false)
        delay(1_000)
        assertEquals(0, app.searches.get(), "ohne Funkgerät keine WLAN-Suche von selbst")
        assertNull(app.controller.suggestedDevice.value)
        assertEquals(0, app.hooks.asked, "nicht ungefragt nach dem lokalen Netzwerk fragen")
    }

    @Test
    fun radioUsedBeforeIsSuggestedOnWifi() = withScope { scope ->
        val app = app(scope, Hooks(allowed = true), usedBefore = true)
        eventually { app.controller.suggestedDevice.value?.identifier == wifiRadio.identifier }
        assertEquals(1, app.searches.get())
    }

    @Test
    fun withoutLocalNetworkPermissionNoSearchByItself() = withScope { scope ->
        // Android 17 without the permission: the search would open the system's device picker each time.
        val app = app(scope, Hooks(allowed = false), usedBefore = true)
        delay(1_000)
        assertEquals(0, app.searches.get(), "ohne Erlaubnis keine WLAN-Suche von selbst")
        assertEquals(0, app.hooks.asked, "nicht ungefragt nach dem lokalen Netzwerk fragen")
    }

    @Test
    fun searchingOnPurposeAsksOnceThenSearchesOnWifi() = withScope { scope ->
        val app = app(scope, Hooks(allowed = false), usedBefore = false)
        app.controller.startScan()
        eventually { app.controller.scanResults.value.any { it.identifier == wifiRadio.identifier } }
        assertEquals(1, app.hooks.asked)
        app.controller.stopScan()
        eventually { !app.controller.isScanning.value }
        // Granted: the next search does not ask again.
        app.controller.startScan()
        eventually { app.searches.get() == 2 }
        assertEquals(1, app.hooks.asked)
    }

    @Test
    fun searchingOnPurposeDeclinedStillSearchesOnWifi() = withScope { scope ->
        // Declined: Android shows its own picker instead, which is fine because the person asked for the search.
        val app = app(scope, Hooks(allowed = false, grantOnAsk = false), usedBefore = false)
        app.controller.startScan()
        eventually { app.searches.get() == 1 }
        assertEquals(1, app.hooks.asked)
    }
}
