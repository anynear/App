package app.anynear

import app.anynear.anmp.core.firmware.FIRMWARE_LIST_DOMAIN
import app.anynear.anmp.core.firmware.FirmwareRelease
import app.anynear.anmp.core.firmware.FirmwareList
import app.anynear.anmp.core.firmware.FirmwarePath
import app.anynear.andp.SigningBytes
import app.anynear.andp.SigningKey
import app.anynear.anmp.AnmpInfo
import app.anynear.app.FirmwareBlocker
import app.anynear.app.FirmwareSource
import app.anynear.app.RadioSoftware
import app.anynear.app.RadioSoftwareState
import app.anynear.app.SoftwareCheck
import app.anynear.app.firmwareBlocker
import app.anynear.app.pathsOverConnection
import app.anynear.app.pickRelease
import app.anynear.contacts.Contact
import app.anynear.mesh.ChannelSetupResult
import app.anynear.mesh.DeviceSetupCheck
import app.anynear.mesh.DeviceSetupResult
import app.anynear.mesh.FirmwareInstallResult
import app.anynear.mesh.IncomingText
import app.anynear.mesh.LinkState
import app.anynear.mesh.MeshLink
import app.anynear.mesh.OwnDevice
import app.anynear.mesh.PairedDevice
import app.anynear.mesh.RadioChannel
import app.anynear.mesh.RadioFirmware
import app.anynear.mesh.RadioSend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RadioSoftwareTest {
    private val image = ByteArray(1000) { it.toByte() }

    /** SHA-256 of [image]. */
    private val IMAGE_SHA256 = "a8af099bf2e878609558dbf69d8f88f4a31040a8cf84b549a0cfa912f12ffc3f"
    private val heltec = RadioFirmware("3.5.6.d55c08d", "HELTEC_V3", hasWifi = true, hasBluetooth = true)

    @Test
    fun picksTheNewestMatchingRelease() {
        val list = FirmwareList(
            releases = listOf(
                release("3.5.7.1111111"),
                release("3.6.1.2222222", path = FirmwarePath.ESP32_BLE),
                release("3.6.1.2222222", path = FirmwarePath.ESP32_WIFI),
                release("3.7.0.3333333", hwModel = "T_ECHO", path = FirmwarePath.NRF52_DFU),
                release("3.5.5.0000000"),
            ),
        )
        val overWifi = pickRelease(list, heltec, setOf(FirmwarePath.ESP32_WIFI), appBuild = 100)
        assertEquals("3.6.1.2222222", overWifi?.version)
        assertEquals(FirmwarePath.ESP32_WIFI, overWifi?.path)
        // No way over the current connection: still the newest version, so that the instructions appear.
        assertEquals(FirmwarePath.ESP32_BLE, pickRelease(list, heltec, emptySet(), appBuild = 100)?.path)
        assertNull(pickRelease(list, heltec.copy(version = "3.6.1.2222222"), setOf(FirmwarePath.ESP32_WIFI), appBuild = 100))
    }

    @Test
    fun onlyLinksToMeshtastic() {
        val list = FirmwareList(releases = listOf(release("3.6.0.1111111", url = "https://example.org/firmware.bin")))
        assertNull(pickRelease(list, heltec, setOf(FirmwarePath.ESP32_WIFI), appBuild = 100))
    }

    @Test
    fun notTooNewForTheApp() {
        val list = FirmwareList(releases = listOf(release("3.5.9.1111111", minAppBuild = 500)))
        assertNull(pickRelease(list, heltec, setOf(FirmwarePath.ESP32_WIFI), appBuild = 100))
        assertEquals("3.5.9.1111111", pickRelease(list, heltec, setOf(FirmwarePath.ESP32_WIFI), appBuild = 500)?.version)
    }

    @Test
    fun pathsFollowTheConnection() {
        assertEquals(setOf(FirmwarePath.ESP32_WIFI), pathsOverConnection(network = true, heltec))
        assertEquals(setOf(FirmwarePath.ESP32_BLE, FirmwarePath.NRF52_DFU), pathsOverConnection(network = false, heltec))
        assertEquals(emptySet(), pathsOverConnection(network = true, heltec.copy(hasWifi = false)))
        // Before 3.7.18 no OTA via the phone, only once via USB.
        assertEquals(emptySet(), pathsOverConnection(network = true, heltec.copy(version = "2.7.17.1234567")))
        assertEquals(setOf(FirmwarePath.NRF52_DFU), pathsOverConnection(network = false, heltec.copy(version = "2.7.15.567b8ea")))
    }

    @Test
    fun conditionsBeforeInstalling() {
        assertNull(firmwareBlocker(connected = true, radioBattery = 101, phoneBattery = 30, messagesWaiting = false), "am Strom")
        assertNull(firmwareBlocker(connected = true, radioBattery = 50, phoneBattery = null, messagesWaiting = false))
        assertEquals(FirmwareBlocker.NOT_CONNECTED, firmwareBlocker(false, 100, 100, false))
        assertEquals(FirmwareBlocker.RADIO_BATTERY, firmwareBlocker(true, 49, 100, false))
        assertEquals(FirmwareBlocker.RADIO_BATTERY, firmwareBlocker(true, null, 100, false), "unbekannt zählt nicht")
        assertEquals(FirmwareBlocker.PHONE_BATTERY, firmwareBlocker(true, 80, 29, false))
        assertEquals(FirmwareBlocker.MESSAGES_WAITING, firmwareBlocker(true, 80, 80, true))
    }

    @Test
    fun checkingOnRequestSaysWhatItFound() = runTest {
        val key = SigningKey.generate()
        val link = FakeLink()
        val source = FakeSource(key, signed(key, list(release("3.6.0.1111111"))))
        val software = software(link, source)
        assertNull(software.checkResult.value, "niemand hat gefragt")

        software.check(force = true)
        assertEquals(SoftwareCheck.Checking, software.checkResult.value)
        runCurrent()
        assertEquals(SoftwareCheck.Newer("3.6.0.1111111", installable = true), software.checkResult.value)

        // As today: the app cannot do this path yet, but says so.
        link.firmwarePaths = emptySet()
        software.check(force = true)
        runCurrent()
        assertEquals(SoftwareCheck.Newer("3.6.0.1111111", installable = false), software.checkResult.value)

        link.firmware.value = heltec.copy(version = "3.6.0.1111111")
        software.check(force = true)
        runCurrent()
        assertEquals(SoftwareCheck.UpToDate("3.6.0.1111111"), software.checkResult.value)

        source.offline = true
        software.check(force = true)
        runCurrent()
        assertEquals(SoftwareCheck.Failed, software.checkResult.value)

        source.offline = false
        link.state.value = LinkState.Searching
        software.check(force = true)
        runCurrent()
        assertEquals(SoftwareCheck.NoRadio, software.checkResult.value)
    }

    @Test
    fun checkingWithoutSpaceFails() = runTest {
        val software = software(FakeLink(), FakeSource("{}", key = null))
        software.check(force = true)
        assertEquals(SoftwareCheck.Failed, software.checkResult.value)
    }

    @Test
    fun withoutKeyTheAppDoesNotLook() = runTest {
        val source = FakeSource("{}", key = null)
        val software = software(FakeLink(), source)
        software.start()
        software.check(force = true)
        runCurrent()
        assertEquals(0, source.listCalls)
        assertFalse(software.checksEnabled)
    }

    @Test
    fun loadsInTheBackgroundAndAsksEveryTime() = runTest {
        val key = SigningKey.generate()
        val link = FakeLink()
        val software = software(link, FakeSource(key, signed(key, list(release("3.6.0.1111111")))))
        software.start()
        runCurrent()
        val available = assertIs<RadioSoftwareState.Available>(software.state.value)
        assertTrue(available.installable)
        assertTrue(available.ready, "im Hintergrund geladen und geprüft")
        assertNotNull(software.notice.value)
        assertEquals(0, link.installs, "nichts ohne Einverständnis")

        // „Später“: notice gone, it stays on the radio page.
        software.postpone()
        runCurrent()
        assertNull(software.notice.value)
        assertIs<RadioSoftwareState.Available>(software.state.value)
    }

    @Test
    fun wrongFileIsNeverOffered() = runTest {
        val key = SigningKey.generate()
        val bad = release("3.6.0.1111111").copy(sha256 = "00".repeat(32))
        val software = software(FakeLink(), FakeSource(key, signed(key, list(bad))))
        software.start()
        runCurrent()
        val available = assertIs<RadioSoftwareState.Available>(software.state.value)
        assertFalse(available.ready)
        assertNull(software.notice.value)
    }

    @Test
    fun installsAndChecksTheNewVersion() = runTest {
        val key = SigningKey.generate()
        val link = FakeLink()
        val software = software(link, FakeSource(key, signed(key, list(release("3.6.0.1111111")))))
        software.start()
        runCurrent()
        assertNull(software.install())
        runCurrent()
        assertEquals(1, link.installs)
        assertEquals(image.toList(), link.written?.toList())
        val done = assertIs<RadioSoftwareState.Done>(software.state.value)
        assertTrue(done.settingsKept)
        software.dismissResult()
        runCurrent()
        assertEquals(RadioSoftwareState.Idle, software.state.value, "jetzt aktuell")
    }

    @Test
    fun notWithoutTheConditions() = runTest {
        val key = SigningKey.generate()
        val link = FakeLink(battery = 20)
        val software = software(link, FakeSource(key, signed(key, list(release("3.6.0.1111111")))))
        software.start()
        runCurrent()
        assertEquals(FirmwareBlocker.RADIO_BATTERY, software.install())
        assertEquals(0, link.installs)
    }

    @Test
    fun leavingBeforeWritingCancels() = runTest {
        val key = SigningKey.generate()
        val link = FakeLink(holdBeforeWriting = CompletableDeferred())
        val software = software(link, FakeSource(key, signed(key, list(release("3.6.0.1111111")))))
        software.start()
        runCurrent()
        software.install()
        runCurrent()
        assertIs<RadioSoftwareState.Installing>(software.state.value)
        software.onBackground()
        runCurrent()
        assertIs<RadioSoftwareState.Available>(software.state.value)
    }

    @Test
    fun leavingWhileWritingGoesOn() = runTest {
        val key = SigningKey.generate()
        val link = FakeLink(holdWhileWriting = CompletableDeferred())
        val software = software(link, FakeSource(key, signed(key, list(release("3.6.0.1111111")))))
        software.start()
        runCurrent()
        software.install()
        runCurrent()
        assertTrue(assertIs<RadioSoftwareState.Installing>(software.state.value).writing)
        software.onBackground()
        software.cancel()
        runCurrent()
        assertIs<RadioSoftwareState.Installing>(software.state.value)
        link.holdWhileWriting!!.complete(Unit)
        runCurrent()
        assertIs<RadioSoftwareState.Done>(software.state.value)
    }

    @Test
    fun failuresAreCountedForTheUsbRescue() = runTest {
        val key = SigningKey.generate()
        val link = FakeLink(result = FirmwareInstallResult.Failed(wroteAnything = true))
        val software = software(link, FakeSource(key, signed(key, list(release("3.6.0.1111111")))))
        software.start()
        runCurrent()
        repeat(RadioSoftware.USB_AFTER) {
            software.install()
            runCurrent()
            if (it < RadioSoftware.USB_AFTER - 1) software.dismissResult()
            runCurrent()
        }
        val failed = assertIs<RadioSoftwareState.Failed>(software.state.value)
        assertEquals(RadioSoftware.USB_AFTER, failed.attempts)
        assertTrue(failed.wroteAnything)
    }

    private fun TestScope.software(link: FakeLink, source: FirmwareSource) = RadioSoftware(
        scope = backgroundScope,
        link = link,
        source = source,
        overNetwork = { true },
        phoneBattery = { 80 },
        messagesWaiting = { false },
        appBuild = 100,
        now = { 0L },
    )

    private fun release(
        version: String,
        hwModel: String = "HELTEC_V3",
        path: FirmwarePath = FirmwarePath.ESP32_WIFI,
        minAppBuild: Long? = null,
        url: String = "${RadioSoftware.DOWNLOAD_PREFIX}firmware-$version/firmware-$hwModel-$version.bin",
    ) = FirmwareRelease(hwModel, version, path, url, image.size.toLong(), IMAGE_SHA256, minAppBuild)

    private fun list(vararg releases: FirmwareRelease, issued: Long = 1): String =
        Json.encodeToString(FirmwareList.serializer(), FirmwareList(issued, releases = releases.toList()))

    private suspend fun signed(key: SigningKey, list: String): String = buildJsonObject {
        put("list", list)
        put("sig", key.sign(SigningBytes.of(FIRMWARE_LIST_DOMAIN, list)).toString())
    }.toString()

    /** Space on the relays with one list, signed by [key]. */
    private inner class FakeSource(private val body: String, key: SigningKey?) : FirmwareSource {
        constructor(key: SigningKey, body: String) : this(body, key)

        /** No relay answers. */
        var offline = false

        var listCalls = 0
        override val listKey: String? = key?.key?.toString()
        override suspend fun lists(): List<String>? {
            listCalls++
            if (offline) return null
            return listOf(body)
        }
        override suspend fun download(release: FirmwareRelease): ByteArray = image.copyOf()
    }

    /** Heltec V3 over Wi-Fi, on power; installs and afterwards reports the new version. */
    private inner class FakeLink(
        battery: Int = 101,
        private val result: FirmwareInstallResult = FirmwareInstallResult.Written,
        val holdBeforeWriting: CompletableDeferred<Unit>? = null,
        val holdWhileWriting: CompletableDeferred<Unit>? = null,
    ) : MeshLink {
        var installs = 0
        var written: ByteArray? = null
        override val firmware = MutableStateFlow<RadioFirmware?>(heltec)
        override var firmwarePaths: Set<FirmwarePath> = setOf(FirmwarePath.ESP32_WIFI)

        override suspend fun installFirmware(path: FirmwarePath, image: ByteArray, onProgress: (Float) -> Unit): FirmwareInstallResult {
            installs++
            holdBeforeWriting?.await()
            onProgress(0f)
            holdWhileWriting?.await()
            written = image
            onProgress(1f)
            if (result == FirmwareInstallResult.Written) firmware.value = heltec.copy(version = "3.6.0.1111111")
            return result
        }

        override val state = MutableStateFlow<LinkState>(LinkState.Connected)
        override val ownDevice: StateFlow<OwnDevice?> = MutableStateFlow(OwnDevice(1, "anynear.app | Test", "PYTE", battery))
        override val circleChannels: StateFlow<List<RadioChannel>?> =
            MutableStateFlow(listOf(RadioChannel(1, AnmpInfo.RADIO_CHANNEL_NAME, ByteArray(32).toByteString())))
        override val setupCheck: StateFlow<DeviceSetupCheck?> = MutableStateFlow(DeviceSetupCheck(DeviceSetupCheck.Kind.OK, emptySet(), "anynear.app | Test"))
        override val knownNames: StateFlow<Map<Int, String>> = MutableStateFlow(emptyMap())
        override val incoming: Flow<IncomingText> = emptyFlow()
        override fun start(device: PairedDevice) = Unit
        override suspend fun stop() = Unit
        override suspend fun probe(device: PairedDevice) = false
        override fun send(text: String, to: Int?, channelIndex: Int): RadioSend = error("nicht im Test")
        override suspend fun sendData(payload: ByteArray, to: Int?, channelIndex: Int): RadioSend = error("nicht im Test")
        override suspend fun applyRadioProfile(nameSuffix: String, force: Boolean) = DeviceSetupResult.NotConnected
        override suspend fun renameDevice(nameSuffix: String) = DeviceSetupResult.NotConnected
        override suspend fun ensureChannel(name: String, psk: ByteArray) = ChannelSetupResult.NotConnected
        override suspend fun removeChannel(name: String) = false
        override fun ownContact(): Contact? = null
        override suspend fun registerContact(contact: Contact) = false
    }
}
