package app.anynear

import app.anynear.anmp.core.firmware.BootloaderRelease
import app.anynear.anmp.core.firmware.EspChip
import app.anynear.anmp.core.firmware.EspInfo
import app.anynear.anmp.core.firmware.FIRMWARE_LIST_DOMAIN
import app.anynear.anmp.core.firmware.FirmwareList
import app.anynear.anmp.core.firmware.FirmwarePath
import app.anynear.anmp.core.firmware.FirmwareRelease
import app.anynear.anmp.core.firmware.FlashImage
import app.anynear.anmp.core.firmware.FlashRegion
import app.anynear.anmp.core.firmware.RadioCatalog
import app.anynear.anmp.core.firmware.Uf2Info
import app.anynear.anmp.core.firmware.UsbFamily
import app.anynear.anmp.core.firmware.UsbInstallFile
import app.anynear.anmp.core.firmware.UsbInstallRelease
import app.anynear.andp.SigningBytes
import app.anynear.andp.SigningKey
import app.anynear.app.FirmwareSource
import app.anynear.app.RecommendedRadios
import app.anynear.app.RadioSoftware
import app.anynear.app.UsbRescueFlow
import app.anynear.app.UsbConnection
import app.anynear.app.UsbRescueState
import app.anynear.app.UsbRescueStep
import app.anynear.app.UsbPurpose
import app.anynear.contacts.Contact
import app.anynear.mesh.ChannelSetupResult
import app.anynear.mesh.DeviceSetupCheck
import app.anynear.mesh.DeviceSetupResult
import app.anynear.mesh.IncomingText
import app.anynear.mesh.LinkState
import app.anynear.mesh.MeshLink
import app.anynear.mesh.OwnDevice
import app.anynear.mesh.PairedDevice
import app.anynear.mesh.RadioChannel
import app.anynear.mesh.RadioFirmware
import app.anynear.mesh.RadioSend
import app.anynear.mesh.Uf2Drive
import app.anynear.mesh.UsbRadio
import app.anynear.mesh.UsbRescue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Rescue via USB cable (ADR 0056): flow without a device, with mocked USB and drive. */
class UsbRescueFlowTest {
    private val uf2 = ByteArray(1000) { it.toByte() }

    /** SHA-256 of [uf2] (as in RadioSoftwareTest). */
    private val UF2_SHA256 = "a8af099bf2e878609558dbf69d8f88f4a31040a8cf84b549a0cfa912f12ffc3f"

    private val t1000 = RadioFirmware("2.7.25.104df5f", "TRACKER_T1000_E", hasWifi = false, hasBluetooth = true)
    private val radio = UsbRadio("usb0", "anynear.app | Birne 48ac", t1000, nodeNum = 0x1a2b48ac)

    private class FakeUsb(var radio: UsbRadio?) : UsbRescue {
        var resets = 0
        var bootloader = 0
        override suspend fun find(): UsbRadio? = radio
        /** Something on the port, but not a radio (or permission denied). */
        var otherDevice = false
        override suspend fun anyDevice(): Boolean = radio != null || otherDevice
        override suspend fun factoryReset(radio: UsbRadio): Boolean = true.also { resets++ }
        override suspend fun enterBootloader(radio: UsbRadio): Boolean = true.also { bootloader++ }
        var bluetooth = 0
        var bluetoothWorks = true
        override suspend fun enableBluetooth(radio: UsbRadio): Boolean = bluetoothWorks.also { bluetooth++ }
        var chip: EspChip? = null
        var flashed: List<FlashImage>? = null
        /** What answers on the cable after writing Meshtastic. */
        var afterFlash: UsbRadio? = null
        val wifi = mutableListOf<Pair<String, String>>()
        /** Built-in flash and PSRAM the loader reports (ESP32-S3 eFuse). */
        var flashMb: Int? = null
        var psramMb: Int? = null
        override suspend fun detectChip(): EspInfo? = chip?.let { EspInfo(it, flashMb, psramMb) }
        override suspend fun flash(images: List<FlashImage>, flashSize: Long, onProgress: (Float) -> Unit): Boolean {
            flashed = images
            onProgress(1f)
            radio = afterFlash
            return true
        }
        override suspend fun enableWifi(radio: UsbRadio, ssid: String, password: String): Boolean = true.also { wifi += ssid to password }
        var touched = 0
        var touchWorks = true
        override suspend fun touchBootloader(radio: UsbRadio): Boolean = touchWorks.also { touched++ }
    }

    private class FakeDrive(private val info: String? = "UF2 Bootloader 0.9.1\nBoard-ID: nRF52840-T1000-E-v1") : Uf2Drive {
        val files = mutableMapOf<String, ByteArray>()
        override suspend fun info(): String? = info
        override suspend fun write(name: String, bytes: ByteArray): Boolean = true.also { files[name] = bytes }
    }

    @Test
    fun resetARunningRadio() = runTest {
        val usb = FakeUsb(radio)
        val flow = UsbRescueFlow(usb, software(), lastHwModel = { null })
        flow.search()
        val found = assertIs<UsbRescueState.Found>(flow.state.value)
        assertEquals(true, found.uf2, "für den T1000-E gibt es eine .uf2")
        flow.factoryReset(found.radio)
        assertEquals(UsbRescueState.Done(UsbRescueStep.RESET, "Meshtastic_48ac"), flow.state.value, "neuer Bluetooth-Name")
        assertEquals(1, usb.resets)
    }

    @Test
    fun nothingOnTheCable() = runTest {
        // The phone sees no USB device at all (charging-only cable, OTG off): say so instead of „allow access“.
        val flow = UsbRescueFlow(FakeUsb(null), software(), lastHwModel = { null })
        flow.search()
        assertEquals(UsbRescueState.NoUsbDevice, flow.state.value)
    }

    @Test
    fun searchStaysVisibleForAMoment() = runTest {
        // Nothing on the cable answers at once; „Suche …“ with its progress bar still shows for a moment.
        val flow = UsbRescueFlow(FakeUsb(null), software(), lastHwModel = { null })
        val job = launch { flow.search() }
        testScheduler.advanceTimeBy(500)
        testScheduler.runCurrent()
        assertEquals(UsbRescueState.Searching, flow.state.value)
        job.join()
        assertEquals(UsbRescueState.NoUsbDevice, flow.state.value)
    }

    @Test
    fun deviceThereButNoRadio() = runTest {
        val flow = UsbRescueFlow(FakeUsb(null).apply { otherDevice = true }, software(), lastHwModel = { null })
        flow.search()
        assertEquals(UsbRescueState.NotFound, flow.state.value)
    }

    @Test
    fun reinstallWritesTheVerifiedUf2() = runTest {
        val usb = FakeUsb(radio)
        val flow = UsbRescueFlow(usb, software(), lastHwModel = { null })
        flow.search()
        flow.reinstall(radio)
        assertEquals(UsbRescueState.PickDrive("TRACKER_T1000_E"), flow.state.value)
        val drive = FakeDrive()
        flow.write(drive)
        assertEquals(UsbRescueState.Done(UsbRescueStep.WRITE), flow.state.value)
        assertEquals(uf2.toList(), drive.files["firmware-tracker-t1000-e-2.7.26.54e0d8d.uf2"]?.toList())
    }

    @Test
    fun stuckWithKnownModel() = runTest {
        val flow = UsbRescueFlow(FakeUsb(null), software(), lastHwModel = { "TRACKER_T1000_E" })
        flow.stuckInBootloader()
        assertEquals(UsbRescueState.PickDrive("TRACKER_T1000_E"), flow.state.value)
    }

    @Test
    fun stuckWithUnknownModelAsks() = runTest {
        val flow = UsbRescueFlow(FakeUsb(null), software(), lastHwModel = { null })
        flow.stuckInBootloader()
        // The same choice as when setting up, only what goes onto a drive.
        val pick = assertIs<UsbRescueState.PickRadio>(flow.state.value)
        assertEquals(listOf("TRACKER_T1000_E"), pick.selection.all.map { it.hwModel })
        flow.pick(pick.selection.all.single())
        assertEquals(UsbRescueState.PickDrive("TRACKER_T1000_E"), flow.state.value)
    }

    @Test
    fun wrongDriveIsRefused() = runTest {
        val flow = UsbRescueFlow(FakeUsb(null), software(), lastHwModel = { "TRACKER_T1000_E" })
        flow.stuckInBootloader()
        val drive = FakeDrive(info = null)
        flow.write(drive)
        assertEquals(UsbRescueState.Failed(UsbRescueStep.WRITE), flow.state.value)
        assertNull(drive.files.values.firstOrNull())
    }

    @Test
    fun oldBootloaderIsRenewed() = runTest {
        val usb = FakeUsb(radio)
        val flow = UsbRescueFlow(usb, software(), lastHwModel = { null })
        flow.search()
        assertEquals(true, assertIs<UsbRescueState.Found>(flow.state.value).bootloader)
        flow.updateBootloader(radio)
        assertEquals(UsbRescueState.PickDrive("TRACKER_T1000_E", bootloader = true), flow.state.value)
        val drive = FakeDrive("UF2 Bootloader 0.9.1-5-g488711a lib/nrfx (v2.0.0)\nModel: Seeed T1000-E\nBoard-ID: nRF52840-T1000-E-v1\n")
        flow.write(drive)
        assertEquals(UsbRescueState.Done(UsbRescueStep.UPDATE_BOOTLOADER), flow.state.value)
        assertEquals(listOf("update-t1000_e_bootloader-0.9.2_nosd.uf2"), drive.files.keys.toList())
    }

    @Test
    fun currentBootloaderReinstallsTheSoftware() = runTest {
        val flow = UsbRescueFlow(FakeUsb(radio), software(), lastHwModel = { null })
        flow.search()
        flow.updateBootloader(radio)
        val drive = FakeDrive("UF2 Bootloader 0.9.2 lib/nrfx (v2.0.0)\nBoard-ID: nRF52840-T1000-E-v1\n")
        flow.write(drive)
        assertEquals(UsbRescueState.Done(UsbRescueStep.WRITE), flow.state.value)
        assertEquals(listOf("firmware-tracker-t1000-e-2.7.26.54e0d8d.uf2"), drive.files.keys.toList())
    }

    @Test
    fun foreignBoardNeverGetsTheBootloader() = runTest {
        val flow = UsbRescueFlow(FakeUsb(radio), software(), lastHwModel = { null })
        flow.search()
        flow.updateBootloader(radio)
        val drive = FakeDrive("UF2 Bootloader 0.6.1\nBoard-ID: nRF52840-TECHO-v1\n")
        flow.write(drive)
        assertNull(drive.files.keys.firstOrNull { "bootloader" in it }, "nie ein fremder Bootloader")
    }

    // --- RadioSoftware with a signed list (DFU file and .uf2 for the T1000-E) ---

    private suspend fun TestScope.software(): RadioSoftware {
        val key = SigningKey.generate()
        val releases = listOf(
            FirmwareRelease("TRACKER_T1000_E", "2.7.26.54e0d8d", FirmwarePath.NRF52_DFU,
                "${RadioSoftware.DOWNLOAD_PREFIX}firmware-2.7.26.54e0d8d/firmware-tracker-t1000-e-2.7.26.54e0d8d-ota.zip", 5, "00".repeat(32)),
            FirmwareRelease("TRACKER_T1000_E", "2.7.26.54e0d8d", FirmwarePath.USB,
                "${RadioSoftware.DOWNLOAD_PREFIX}firmware-2.7.26.54e0d8d/firmware-tracker-t1000-e-2.7.26.54e0d8d.uf2", uf2.size.toLong(), UF2_SHA256),
        )
        val bootloaders = listOf(
            BootloaderRelease("nRF52840-T1000-E-v1", listOf("TRACKER_T1000_E"), "0.9.2",
                "${RadioSoftware.BOOTLOADER_PREFIX}0.9.2/update-t1000_e_bootloader-0.9.2_nosd.uf2", uf2.size.toLong(), UF2_SHA256),
        )
        val usbInstalls = listOf(
            UsbInstallRelease("HELTEC_V3", "2.7.26.54e0d8d", "esp32s3", flashSize = 0x800000,
                files = listOf(UsbInstallFile("${RadioSoftware.DOWNLOAD_PREFIX}firmware-2.7.26.54e0d8d/firmware-heltec-v3-2.7.26.54e0d8d.factory.bin", uf2.size.toLong(), UF2_SHA256, 0)),
                erase = listOf(FlashRegion(0x9000, 0x10))),
            UsbInstallRelease("T_DECK", "2.7.26.54e0d8d", "esp32s3", flashSize = 0x1000000,
                files = listOf(UsbInstallFile("${RadioSoftware.DOWNLOAD_PREFIX}firmware-2.7.26.54e0d8d/firmware-t-deck-2.7.26.54e0d8d.factory.bin", uf2.size.toLong(), UF2_SHA256, 0))),
        )
        val list = Json.encodeToString(FirmwareList.serializer(), FirmwareList(1, releases = releases, bootloaders = bootloaders, usbInstalls = usbInstalls))
        val envelope = buildJsonObject {
            put("list", list)
            put("sig", key.sign(SigningBytes.of(FIRMWARE_LIST_DOMAIN, list)).toString())
        }.toString()
        val source = object : FirmwareSource {
            override val listKey: String = key.key.toString()
            override suspend fun lists(): List<String> = listOf(envelope)
            override suspend fun download(release: FirmwareRelease): ByteArray = uf2.copyOf()
        }
        return RadioSoftware(backgroundScope, NoLink, source, overNetwork = { false }, phoneBattery = { 80 }, messagesWaiting = { false }, appBuild = 1, now = { 0L })
    }

    private object NoLink : MeshLink {
        override val state: StateFlow<LinkState> = MutableStateFlow(LinkState.NoDevice)
        override val ownDevice: StateFlow<OwnDevice?> = MutableStateFlow(null)
        override val circleChannels: StateFlow<List<RadioChannel>?> = MutableStateFlow(null)
        override val setupCheck: StateFlow<DeviceSetupCheck?> = MutableStateFlow(null)
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

    @Test
    fun noAnswerMeansNoSoftware() = runTest {
        val flow = UsbRescueFlow(FakeUsb(UsbRadio("usb0", null, null, answered = false)), software = null, lastHwModel = { null })
        flow.search()
        assertEquals(UsbRescueState.NoSoftware(), flow.state.value)
    }

    @Test
    fun silentEsp32AsksForTheBootButton() = runTest {
        // Empty ESP32 whose ROM loader did not answer the reset over the lines: BOOT button by hand, then search again.
        val usb = FakeUsb(UsbRadio("usb0", null, null, answered = false, family = UsbFamily.ESP32))
        val flow = UsbRescueFlow(usb, software(), lastHwModel = { null })
        flow.reset(UsbPurpose.SETUP)
        flow.search()
        assertEquals(UsbRescueState.NoSoftware(holdBoot = true), flow.state.value)
        // Started with BOOT held: now the loader answers and the models for the chip are offered.
        usb.chip = EspChip.ESP32_S3
        flow.search()
        assertIs<UsbRescueState.PickRadio>(flow.state.value)
    }

    @Test
    fun emptyNrf52GoesToItsDriveNotToTheEspLoader() = runTest {
        // T1000-E or RAK4631 with factory software: no Meshtastic answers, but it is no ESP32 either.
        val usb = FakeUsb(UsbRadio("usb0", null, null, answered = false, family = UsbFamily.NRF52)).apply { chip = EspChip.ESP32_S3 }
        val flow = UsbRescueFlow(usb, software(), lastHwModel = { null })
        flow.reset(purpose = null)
        flow.search()
        val choose = assertIs<UsbRescueState.Choose>(flow.state.value)
        assertEquals(true, choose.canSetUp)
        flow.choose(UsbPurpose.SETUP, choose.radio)
        // First the choice of radio, only then into the bootloader.
        val pick = assertIs<UsbRescueState.PickRadio>(flow.state.value)
        assertEquals(0, usb.touched)
        assertEquals(listOf("TRACKER_T1000_E"), pick.selection.all.map { it.hwModel }, "nur .uf2, kein ESP32-Abbild")
        flow.pick(pick.selection.all.single())
        assertEquals(1, usb.touched, "ins Startprogramm gebeten")
        assertEquals(UsbRescueState.PickDrive("TRACKER_T1000_E"), flow.state.value)
        assertNull(usb.flashed)
        // Drive picked: the verified .uf2 goes onto it.
        val drive = FakeDrive()
        flow.write(drive)
        assertEquals(UsbRescueState.Done(UsbRescueStep.WRITE), flow.state.value)
        assertEquals(uf2.toList(), drive.files.values.single().toList())
    }

    @Test
    fun touchThatDoesNotWorkStillLeadsToTheDrive() = runTest {
        // Other software ignores the 1200 baud touch; the dialog then explains the double tap on reset.
        val usb = FakeUsb(UsbRadio("usb0", null, null, answered = false, family = UsbFamily.NRF52)).apply { touchWorks = false }
        val flow = UsbRescueFlow(usb, software(), lastHwModel = { "TRACKER_T1000_E" })
        flow.reset(UsbPurpose.SETUP)
        flow.search()
        flow.pick(assertIs<UsbRescueState.PickRadio>(flow.state.value).selection.all.single())
        assertEquals(1, usb.touched)
        assertEquals(UsbRescueState.PickDrive("TRACKER_T1000_E"), flow.state.value)
    }

    @Test
    fun deviceAlreadyInItsBootloaderSkipsTheTouch() = runTest {
        val usb = FakeUsb(UsbRadio("usb0", null, null, answered = false, family = UsbFamily.NRF52, inBootloader = true))
        val flow = UsbRescueFlow(usb, software(), lastHwModel = { "TRACKER_T1000_E" })
        flow.reset(UsbPurpose.RESCUE)
        flow.search()
        flow.pick(assertIs<UsbRescueState.PickRadio>(flow.state.value).selection.all.single())
        assertEquals(UsbRescueState.PickDrive("TRACKER_T1000_E"), flow.state.value)
        assertEquals(0, usb.touched)
    }

    @Test
    fun emptyHeltecV3IsRecognised() = runTest {
        // CP2102 on the cable, ESP32-S3 with 8 MB flash in the chip and no PSRAM: Heltec V3 is suggested.
        val usb = FakeUsb(UsbRadio("usb0", null, null, answered = false, family = UsbFamily.ESP32, vendorId = 0x10C4, productId = 0xEA60)).apply {
            chip = EspChip.ESP32_S3
            flashMb = 8
            psramMb = 0
        }
        val flow = UsbRescueFlow(usb, software(), lastHwModel = { null })
        flow.reset(UsbPurpose.SETUP)
        flow.search()
        val pick = assertIs<UsbRescueState.PickRadio>(flow.state.value)
        assertEquals("HELTEC_V3", pick.selection.preselected?.hwModel)
        assertEquals(listOf("HELTEC_V3", "T_DECK"), pick.selection.all.map { it.hwModel }, "die anderen bleiben wählbar")
    }

    @Test
    fun otherMemoryOrUsbChipIsNotTakenForAHeltecV3() = runTest {
        // Native USB and 8 MB PSRAM (e.g. T-Deck): no suggestion that is not backed by the values, the list as before.
        val usb = FakeUsb(UsbRadio("usb0", null, null, answered = false, family = UsbFamily.ESP32, vendorId = 0x303A, productId = 0x1001)).apply {
            chip = EspChip.ESP32_S3
            flashMb = 0
            psramMb = 8
        }
        val flow = UsbRescueFlow(usb, software(), lastHwModel = { "T_DECK" })
        flow.reset(UsbPurpose.SETUP)
        flow.search()
        val pick = assertIs<UsbRescueState.PickRadio>(flow.state.value)
        assertNull(pick.selection.preselected)
        assertEquals("T_DECK", pick.selection.all.first().hwModel, "zuletzt verbundenes zuerst")
    }

    @Test
    fun readableModelNames() {
        assertEquals("Heltec V3", app.anynear.ui.modelName("HELTEC_V3"))
        assertEquals("RAK4631", app.anynear.ui.modelName("RAK4631"))
        assertEquals("T Echo", app.anynear.ui.modelName("T_ECHO"))
        assertEquals("Tracker T1000 E", app.anynear.ui.modelName("TRACKER_T1000_E"))
    }

    @Test
    fun radioOnWifiGetsBluetoothBack() = runTest {
        val heltec = UsbRadio("usb0", "Meshtastic 1a2b", RadioFirmware("2.7.15", "HELTEC_V3", hasWifi = true, hasBluetooth = true), bluetoothOff = true)
        val usb = FakeUsb(heltec)
        val flow = UsbRescueFlow(usb, software = null, lastHwModel = { null })
        flow.search()
        val found = assertIs<UsbRescueState.Found>(flow.state.value)
        assertEquals(true, found.radio.bluetoothOff)
        flow.enableBluetooth(found.radio)
        assertEquals(1, usb.bluetooth)
        assertEquals(UsbRescueState.Done(UsbRescueStep.BLUETOOTH, "Meshtastic 1a2b"), flow.state.value)
        usb.bluetoothWorks = false
        flow.enableBluetooth(found.radio)
        assertEquals(UsbRescueState.Failed(UsbRescueStep.BLUETOOTH), flow.state.value)
    }

    @Test
    fun pluggedInLooksFirstThenOffersWhatFits() = runTest {
        val flow = UsbRescueFlow(FakeUsb(radio), software(), lastHwModel = { null })
        flow.reset(purpose = null)
        flow.search()
        val choose = assertIs<UsbRescueState.Choose>(flow.state.value)
        // Meshtastic runs, but the anynear profile is missing: set up yes, use no.
        assertEquals(true, choose.canSetUp)
        assertEquals(false, choose.canUse)
        flow.choose(UsbPurpose.RESCUE, choose.radio)
        assertIs<UsbRescueState.Found>(flow.state.value)

        val ready = UsbRescueState.Choose(radio.copy(setUp = true))
        assertEquals(false, ready.canSetUp)
        assertEquals(true, ready.canUse)
        val empty = UsbRescueState.Choose(UsbRadio("usb0", null, null, answered = false))
        assertEquals(true, empty.canSetUp)
        assertEquals(false, empty.canUse)
    }

    @Test
    fun setupInstallsMeshtasticAndAsksTheWayOfUse() = runTest {
        val heltec = UsbRadio("usb0", "Meshtastic 48ac", RadioFirmware("2.7.26.54e0d8d", "HELTEC_V3", hasWifi = true, hasBluetooth = true), nodeNum = 0x1a2b48ac)
        val usb = FakeUsb(UsbRadio("usb0", null, null, answered = false)).apply {
            chip = EspChip.ESP32_S3
            afterFlash = heltec
        }
        val connected = mutableListOf<UsbConnection>()
        val flow = UsbRescueFlow(usb, software(), lastHwModel = { null }, connect = { _, via -> connected += via; "Meshtastic_48ac" }, restartWait = {})
        flow.reset(UsbPurpose.SETUP)
        flow.search()
        val pick = assertIs<UsbRescueState.PickRadio>(flow.state.value)
        assertEquals(listOf("HELTEC_V3", "T_DECK"), pick.selection.all.map { it.hwModel })
        flow.pick(pick.selection.all.first())
        // First the erased area (0xFF), then the file at 0.
        assertEquals(listOf(0x9000L, 0L), usb.flashed?.map { it.offset })
        assertEquals(0xFF.toByte(), usb.flashed!!.first().bytes.first())
        val mode = assertIs<UsbRescueState.ChooseMode>(flow.state.value)
        assertEquals(true, mode.wifiPossible)
        flow.chooseStationary(mode.radio)
        assertIs<UsbRescueState.WifiCredentials>(flow.state.value)
        flow.enableWifi(mode.radio, " Zuhause ", "geheim123")
        assertEquals(listOf("Zuhause" to "geheim123"), usb.wifi)
        assertEquals(UsbRescueState.Paired("Meshtastic_48ac", UsbConnection.WIFI), flow.state.value)
        assertEquals(listOf(UsbConnection.WIFI), connected)
    }

    @Test
    fun onTheMoveTurnsBluetoothOnAndPairsTheRadioOnTheCable() = runTest {
        val heltec = UsbRadio("usb0", "Meshtastic 48ac", RadioFirmware("2.7.15", "HELTEC_V3", hasWifi = true, hasBluetooth = true), nodeNum = 0x1a2b48ac, bluetoothOff = true, wifiOn = true)
        val asked = mutableListOf<UsbRadio>()
        val usb = FakeUsb(heltec)
        val flow = UsbRescueFlow(usb, software = null, lastHwModel = { null }, connect = { r, _ -> asked += r; "Meshtastic_48ac" })
        flow.reset(UsbPurpose.SETUP)
        flow.search()
        val mode = assertIs<UsbRescueState.ChooseMode>(flow.state.value)
        flow.chooseMobile(mode.radio)
        assertEquals(1, usb.bluetooth)
        assertEquals(UsbRescueState.Paired("Meshtastic_48ac", UsbConnection.BLUETOOTH), flow.state.value)
        assertEquals("_48ac", asked.single().bluetoothSuffix)
        assertEquals(false, asked.single().bluetoothOff)
    }

    @Test
    fun useOverTheCableAndSayWhenSomethingDoesNotShowUp() = runTest {
        val flow = UsbRescueFlow(FakeUsb(radio.copy(setUp = true)), software = null, lastHwModel = { null }, connect = { _, _ -> null })
        flow.reset(purpose = null)
        flow.search()
        val choose = assertIs<UsbRescueState.Choose>(flow.state.value)
        flow.choose(UsbPurpose.USE, choose.radio)
        assertEquals(UsbRescueState.NotSeen(choose.radio, UsbConnection.USB), flow.state.value)
    }

    @Test
    fun switchingTheWayOfUseWorksInBothDirections() = runTest {
        val firmware = RadioFirmware("2.7.26.54e0d8d", "HELTEC_V3", hasWifi = true, hasBluetooth = true)
        val onBluetooth = UsbRadio("usb0", "Meshtastic 48ac", firmware, nodeNum = 0x1a2b48ac, setUp = true)
        val inWifi = onBluetooth.copy(wifiOn = true, bluetoothOff = true)
        assertEquals(true, UsbRescueState.Choose(onBluetooth).canSwitchToWifi)
        assertEquals(false, UsbRescueState.Choose(onBluetooth).canSwitchToBluetooth)
        assertEquals(true, UsbRescueState.Choose(inWifi).canSwitchToBluetooth)
        assertEquals(false, UsbRescueState.Choose(inWifi).canSwitchToWifi)
        // Without Wi-Fi in the radio (e.g. nRF52) there is nothing to switch to.
        assertEquals(false, UsbRescueState.Choose(radio.copy(setUp = true)).canSwitchToWifi)
        // Not set up yet: no switching, setting up comes first.
        assertEquals(false, UsbRescueState.Choose(onBluetooth.copy(setUp = false)).canSwitchToWifi)

        val usb = FakeUsb(onBluetooth)
        val flow = UsbRescueFlow(usb, software = null, lastHwModel = { null }, connect = { _, via -> "Meshtastic_48ac".takeIf { via == UsbConnection.WIFI } })
        flow.reset(purpose = null)
        flow.search()
        flow.chooseStationary(assertIs<UsbRescueState.Choose>(flow.state.value).radio)
        flow.enableWifi(onBluetooth, "Zuhause", "geheim123")
        assertEquals(listOf("Zuhause" to "geheim123"), usb.wifi)
        assertEquals(UsbRescueState.Paired("Meshtastic_48ac", UsbConnection.WIFI), flow.state.value)
    }

    // --- Choice of radio after ADR 0095: recommended radios from radios.json, recognised one preselected ---

    @Test
    fun emptyN35ShowsTheRecommendedRadiosWithTheRecognisedOnePreselected() = runTest {
        val heltec = UsbRadio("usb0", "Meshtastic 48ac", RadioFirmware("2.7.26.54e0d8d", "HELTEC_V3", hasWifi = true, hasBluetooth = true), nodeNum = 0x1a2b48ac)
        val usb = FakeUsb(UsbRadio("usb0", null, null, answered = false, family = UsbFamily.ESP32, vendorId = 0x10C4, productId = 0xEA60)).apply {
            chip = EspChip.ESP32_S3
            flashMb = 8
            psramMb = 0
            afterFlash = heltec
        }
        val flow = UsbRescueFlow(usb, software(), lastHwModel = { null }, radios = { RecommendedRadios.BUNDLED }, restartWait = {})
        flow.reset(purpose = null)
        flow.search()
        flow.choose(UsbPurpose.SETUP, assertIs<UsbRescueState.Choose>(flow.state.value).radio)
        val pick = assertIs<UsbRescueState.PickRadio>(flow.state.value)
        assertEquals(listOf("Meshnology N35"), pick.selection.recommended.map { it.radio?.name })
        assertEquals("HELTEC_V3", pick.selection.preselected?.hwModel)
        assertEquals(listOf("T_DECK"), pick.selection.others.map { it.hwModel }, "der Rest hinter „Anderes Meshtastic-fähiges Funkgerät“")
        // The nRF52 radios do not fit an ESP32-S3: greyed out with the reason, not to be chosen.
        assertEquals(listOf("sensecap-t1000-e", "sensecap-solar-node"), pick.unfit.map { it.id })
        assertEquals(0, usb.touched)
        // Main button: written over the ROM loader, then the way of use.
        flow.pick(pick.selection.preselected!!)
        assertEquals(listOf(0x9000L, 0L), usb.flashed?.map { it.offset })
        assertIs<UsbRescueState.ChooseMode>(flow.state.value)
    }

    @Test
    fun emptyT1000EGoesToItsDriveWithTheChosenModel() = runTest {
        // Seeed USB IDs: an nRF52, so only .uf2; the Solar Node has no file in the list and is left out, the N35 greyed.
        val usb = FakeUsb(UsbRadio("usb0", null, null, answered = false, family = UsbFamily.NRF52, vendorId = 0x2886, productId = 0x0057))
        val flow = UsbRescueFlow(usb, software(), lastHwModel = { null }, radios = { RecommendedRadios.BUNDLED })
        flow.reset(UsbPurpose.SETUP)
        flow.search()
        val pick = assertIs<UsbRescueState.PickRadio>(flow.state.value)
        assertEquals(listOf("TRACKER_T1000_E"), pick.selection.recommended.map { it.hwModel })
        assertEquals("SenseCAP T1000-E", pick.selection.preselected?.radio?.name)
        assertEquals(listOf("meshnology-n35"), pick.unfit.map { it.id })
        assertEquals(emptyList(), pick.selection.all.mapNotNull { it.install }, "kein ESP32-Abbild für ein nRF52")
        flow.pick(pick.selection.preselected!!)
        assertEquals(1, usb.touched)
        assertEquals(UsbRescueState.PickDrive("TRACKER_T1000_E"), flow.state.value)
        val drive = FakeDrive()
        flow.write(drive)
        assertEquals(UsbRescueState.Done(UsbRescueStep.WRITE), flow.state.value)
        assertEquals(listOf("firmware-tracker-t1000-e-2.7.26.54e0d8d.uf2"), drive.files.keys.toList())
    }

    @Test
    fun unrecognisedRadioPreselectsNothing() = runTest {
        // Generic Adafruit ID of a board that is not in radios.json: the recommended stay, nothing is chosen for the person.
        val usb = FakeUsb(UsbRadio("usb0", null, null, answered = false, family = UsbFamily.NRF52, vendorId = 0x239A, productId = 0x4321))
        val flow = UsbRescueFlow(usb, software(), lastHwModel = { null }, radios = { RecommendedRadios.BUNDLED })
        flow.reset(UsbPurpose.SETUP)
        flow.search()
        val pick = assertIs<UsbRescueState.PickRadio>(flow.state.value)
        assertNull(pick.selection.preselected)
        assertEquals(listOf("TRACKER_T1000_E"), pick.selection.recommended.map { it.hwModel })
    }

    @Test
    fun catalogThatFailsToLoadStillLeavesTheChoice() = runTest {
        // radios.json throws: the flow must not break, the built-in values still recognise the Heltec V3.
        val usb = FakeUsb(UsbRadio("usb0", null, null, answered = false, family = UsbFamily.ESP32, vendorId = 0x10C4, productId = 0xEA60)).apply {
            chip = EspChip.ESP32_S3
            flashMb = 8
            psramMb = 0
        }
        val flow = UsbRescueFlow(usb, software(), lastHwModel = { null }, radios = { error("offline") })
        flow.reset(UsbPurpose.SETUP)
        flow.search()
        val pick = assertIs<UsbRescueState.PickRadio>(flow.state.value)
        assertEquals(emptyList(), pick.selection.recommended)
        assertEquals(emptyList(), pick.unfit)
        assertEquals("HELTEC_V3", pick.selection.preselected?.hwModel)
    }

    @Test
    fun noSoftwareListMeansNoChoice() = runTest {
        val usb = FakeUsb(UsbRadio("usb0", null, null, answered = false, family = UsbFamily.NRF52, vendorId = 0x2886, productId = 0x0057))
        val flow = UsbRescueFlow(usb, software = null, lastHwModel = { null }, radios = { RecommendedRadios.BUNDLED })
        flow.reset(UsbPurpose.SETUP)
        flow.search()
        assertEquals(UsbRescueState.NoSoftware(), flow.state.value)
        assertEquals(0, usb.touched)
    }

    @Test
    fun bundledRadiosMatchTheWebsite() {
        // radios.json as the website delivers it (Website PR 65); the copy in the app must read the same.
        val json = """{"version":1,"radios":[
            {"id":"meshnology-n35","name":"Meshnology N35","hw_model":"HELTEC_V3","mcu":"esp32s3","image":"https://anynear.app/assets/devices/meshnology-n35.webp","use":{"de":"Unterwegs oder zu Hause","en":"On the go or at home"},"match":{"usb":["10c4:ea60"],"embedded_flash_mb":8,"psram_mb":0}},
            {"id":"sensecap-t1000-e","name":"SenseCAP T1000-E","hw_model":"TRACKER_T1000_E","mcu":"nrf52840","image":"https://anynear.app/assets/devices/sensecap-t1000-e.webp","use":{"de":"Mobiler Begleiter","en":"Mobile companion"},"match":{"usb":["239a:8029","239a:0029","239a:002a","239a:802a","2886:0057"]}},
            {"id":"sensecap-solar-node","name":"SenseCAP Solar Node","hw_model":"SEEED_SOLAR_NODE","mcu":"nrf52840","image":"https://anynear.app/assets/devices/sensecap-solar-node.webp","use":{"de":"Mehr Reichweite draußen","en":"More range outdoors"},"match":{"usb":["2886:0059"]}}
        ]}"""
        assertEquals(RadioCatalog.parse(json), RecommendedRadios.BUNDLED)
    }
}
