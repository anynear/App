package app.anynear.app

import app.anynear.anmp.core.firmware.EspInfo
import app.anynear.anmp.core.firmware.FirmwareVersion
import app.anynear.anmp.core.firmware.RadioCatalog
import app.anynear.anmp.core.firmware.RadioOption
import app.anynear.anmp.core.firmware.RadioSelection
import app.anynear.anmp.core.firmware.RecommendedRadio
import app.anynear.anmp.core.firmware.Uf2Info
import app.anynear.anmp.core.firmware.UsbFamily
import app.anynear.anmp.core.firmware.UsbInstallRelease
import app.anynear.anmp.core.firmware.UsbModelHints
import app.anynear.anmp.core.firmware.selectRadios
import app.anynear.mesh.RadioWifiStatus
import app.anynear.mesh.Uf2Drive
import app.anynear.mesh.UsbRadio
import app.anynear.mesh.UsbRescue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Where the dialog at the USB cable currently stands (rescue ADR 0056 §2 step 7, setting up and using ADR 0094). */
sealed interface UsbRescueState {
    /**
     * Just plugged in and looked at: set up, use over the cable or rescue? [radio] is what answered on the cable
     * ([UsbRadio.answered] `false`: no Meshtastic yet).
     */
    data class Choose(val radio: UsbRadio) : UsbRescueState {
        /**
         * Already set up for anynear, e.g. still from before the app was installed anew: setting up stays possible
         * (only the way of use is chosen again), but using it over the cable is the main way then (FUT 14.11.2026).
         */
        val alreadySetUp: Boolean get() = radio.answered && radio.setUp

        /** Using over the cable only once it is set up. */
        val canUse: Boolean get() = radio.answered && radio.setUp

        /** Set up and in the Wi-Fi: back to Bluetooth, e.g. during a power cut (ADR 0094 §3). */
        val canSwitchToBluetooth: Boolean get() = canUse && radio.wifiOn

        /** Set up, on Bluetooth and able to do Wi-Fi: into the Wi-Fi, stationary at home. */
        val canSwitchToWifi: Boolean get() = canUse && !radio.wifiOn && radio.firmware?.hasWifi == true
    }

    data object Start : UsbRescueState
    data object Searching : UsbRescueState

    /** No radio found on the cable or permission denied. */
    data object NotFound : UsbRescueState

    /** The phone sees no USB device at all: cable only charges, OTG off, radio without power. */
    data object NoUsbDevice : UsbRescueState

    /**
     * Plugged in, but no Meshtastic answers and the app cannot install it (chip not recognised, nothing in the list for
     * this chip). [holdBoot]: an ESP32 whose ROM loader did not answer; holding its BOOT button while plugging it in
     * starts the loader by hand, then „Suchen“ again. Otherwise only the instructions for the computer remain.
     */
    data class NoSoftware(val holdBoot: Boolean = false) : UsbRescueState

    /**
     * Radio is running and connected via USB. [uf2]: reinstalling the software is possible (nRF52, `.uf2` in the list);
     * [bootloader]: for this model there is a boot program in the list; [reinstall]: an ESP32 image for this model is in
     * the list (ADR 0094 §2).
     */
    data class Found(val radio: UsbRadio, val uf2: Boolean, val bootloader: Boolean = false, val reinstall: UsbInstallRelease? = null) : UsbRescueState

    data class Working(val step: UsbRescueStep) : UsbRescueState

    /**
     * Now choose the drive of the radio; [hwModel] determines the file, `null`: model still to be chosen.
     * [bootloader]: the boot program goes onto the drive, not the software.
     */
    data class PickDrive(val hwModel: String?, val bootloader: Boolean = false) : UsbRescueState

    /** Done; after the reset [bluetoothName], under which the radio is now listed in the Bluetooth settings. */
    data class Done(val step: UsbRescueStep, val bluetoothName: String? = null) : UsbRescueState

    data class Failed(val step: UsbRescueStep) : UsbRescueState

    /**
     * Installing Meshtastic: which radio is it (ADR 0095 §3)? [selection] holds the recommended radios that can be
     * installed here (picture, name, „good for“ line), the recognised one preselected, and every other model of the list
     * behind „Anderes Meshtastic-fähiges Funkgerät“. Each option goes its own way: an ESP32 over the ROM loader, an nRF52
     * onto its drive. [unfit]: recommended radios that do not match the chip on the cable, shown greyed out with the
     * reason.
     */
    data class PickRadio(val selection: RadioSelection, val unfit: List<RecommendedRadio> = emptyList()) : UsbRescueState

    /** Installing Meshtastic: [progress] 0..1 while writing, `null` while downloading. */
    data class Installing(val model: String, val progress: Float?) : UsbRescueState

    /** Meshtastic runs: on the move (Bluetooth) or stationary in the Wi-Fi (ADR 0094 §3)? */
    data class ChooseMode(val radio: UsbRadio) : UsbRescueState {
        val wifiPossible: Boolean get() = radio.firmware?.hasWifi == true
    }

    /**
     * Stationary: Wi-Fi name and password. [failed]: writing them did not work. [notJoined]: the radio took them but did
     * not get into the Wi-Fi (wrong password, wrong name or too far from the router). [ssid]: what was entered last.
     */
    data class WifiCredentials(val radio: UsbRadio, val failed: Boolean = false, val ssid: String = "", val notJoined: Boolean = false) : UsbRescueState

    /** Name and password are on the radio; it restarts and the app asks over the cable whether it got into [ssid]. */
    data class WifiJoining(val radio: UsbRadio, val ssid: String) : UsbRescueState

    /** Looking for the radio on the chosen way and connecting it; over Bluetooth the phone then asks for the number. */
    data class Pairing(val radio: UsbRadio, val via: UsbConnection) : UsbRescueState

    /** Connected (or pairing) as [name] over [via]; [ssid]: the Wi-Fi it is in, if known. */
    data class Paired(val name: String, val via: UsbConnection, val ssid: String? = null) : UsbRescueState

    /** The radio did not show up on the chosen way (Bluetooth: probably connected to another phone). */
    data class NotSeen(val radio: UsbRadio, val via: UsbConnection) : UsbRescueState
}

/** Why the dialog at the USB cable is open. */
enum class UsbPurpose {
    /** Set up a new radio: software there? On the move or stationary? Then connect it. */
    SETUP,

    /** Use the radio over the cable as the normal connection (ADR 0094 §4). */
    USE,

    /** Rescue a radio: reset, reinstall the software. */
    RESCUE,
}

/** How the app then talks to the radio set up on the cable. */
enum class UsbConnection { BLUETOOTH, WIFI, USB }

enum class UsbRescueStep {
    RESET,
    BOOTLOADER,
    DOWNLOAD,
    WRITE,

    /** Renew the boot program (writing the bootloader). */
    UPDATE_BOOTLOADER,

    /** Wi-Fi off, Bluetooth on, so that the Bluetooth search finds the radio. */
    BLUETOOTH,

    /** Write Meshtastic over the ROM loader (ESP32, ADR 0094 §2). */
    INSTALL,
}

/**
 * Rescue via USB cable: find the radio, reset it to factory settings (also deletes pairings), send an nRF52 into the
 * bootloader and write the `.uf2` from the approved list onto its drive. If a device is already stuck in the
 * bootloader, it continues directly with the drive; the model then comes from the last connected radio or from
 * a selection. An old boot program (bootloader) makes the update over Bluetooth fail; the rescue
 * renews it the same way if the list names a newer one for the board on the drive.
 */
class UsbRescueFlow(
    private val usb: UsbRescue,
    private val software: RadioSoftware?,
    /** Model of the last connected radio, if known. */
    private val lastHwModel: () -> String?,
    private val log: (String) -> Unit = {},
    /**
     * Finds the radio on the cable on the chosen way and connects it; returns its name or `null` if it did not show
     * up. Comes from the app (search and pairing are there).
     */
    private val connect: suspend (UsbRadio, UsbConnection) -> String? = { _, _ -> null },
    /**
     * The recommended radios (`radios.json`, [RadioCatalog.URL]); `null` if they cannot be loaded, then only the values
     * built into ANMP recognise a model ([UsbModelHints.FALLBACK]).
     */
    private val radios: suspend () -> RadioCatalog? = { null },
    /** Pause while the radio restarts after installing; tests pass zero. */
    private val restartWait: suspend () -> Unit = { kotlinx.coroutines.delay(RESTART_WAIT_MS) },
    /**
     * Where steps run that must not end with the dialog (joining the Wi-Fi, connecting): the app's scope. Closing the
     * dialog by mistake cancelled them before (FUT 14.11.2026).
     */
    private val background: CoroutineScope = CoroutineScope(SupervisorJob()),
    /** Pause between two questions „im WLAN?“ over the cable; tests pass zero. */
    private val wifiPollWait: suspend () -> Unit = { kotlinx.coroutines.delay(WIFI_POLL_WAIT_MS) },
) {
    private val _state = MutableStateFlow<UsbRescueState>(UsbRescueState.Start)
    val state: StateFlow<UsbRescueState> = _state.asStateFlow()

    /** What the dialog is doing; `null` while the person still chooses ([UsbRescueState.Choose]). */
    var purpose: UsbPurpose? = UsbPurpose.RESCUE
        private set

    private var hwModel: String? = null
    private var forBootloader = false

    /** The radio on the cable while its model is chosen ([UsbRescueState.PickRadio]); `null` when stuck in the bootloader. */
    private var choosing: UsbRadio? = null

    private var detached: Job? = null

    /** Is a step still running that goes on without the dialog ([inBackground])? Opening the dialog then shows it. */
    val runningInBackground: Boolean get() = detached?.isActive == true

    /** Runs [step] in the app's scope, so that it goes on and shows its result also if the dialog is closed meanwhile. */
    fun inBackground(step: suspend UsbRescueFlow.() -> Unit) {
        detached?.cancel()
        detached = background.launch { step() }
    }

    /** Fresh start; [purpose] `null`: look first, then ask (just plugged in). */
    fun reset(purpose: UsbPurpose? = UsbPurpose.RESCUE) {
        detached?.cancel()
        detached = null
        this.purpose = purpose
        _state.value = UsbRescueState.Start
        hwModel = null
        forBootloader = false
        choosing = null
    }

    /** The person's choice after plugging in, for the radio already looked at. */
    suspend fun choose(purpose: UsbPurpose, radio: UsbRadio) {
        this.purpose = purpose
        proceed(radio)
    }

    suspend fun search() {
        _state.value = UsbRescueState.Searching
        val started = TimeSource.Monotonic.markNow()
        val radio = guard { usb.find() }
        // Without a device the search is over in a moment; show it long enough that tapping „Suchen“ visibly does
        // something, also when the answer is the same as before.
        (MIN_SEARCH_SHOWN - started.elapsedNow()).takeIf { it.isPositive() }?.let { kotlinx.coroutines.delay(it) }
        if (radio == null) {
            _state.value = if (guard { usb.anyDevice() } == false) UsbRescueState.NoUsbDevice else UsbRescueState.NotFound
            return
        }
        if (purpose == null) {
            _state.value = UsbRescueState.Choose(radio)
            return
        }
        proceed(radio)
    }

    private suspend fun proceed(radio: UsbRadio) {
        when (purpose) {
            UsbPurpose.SETUP -> if (radio.answered) _state.value = UsbRescueState.ChooseMode(radio) else installMeshtastic(radio)
            UsbPurpose.USE -> connect(radio, UsbConnection.USB)
            else -> {
                if (!radio.answered) {
                    installMeshtastic(radio)
                    return
                }
                hwModel = radio.firmware?.hwModel
                val uf2 = hwModel?.let { software?.usbReleases(it)?.isNotEmpty() } == true
                val bootloader = hwModel?.let { model -> guard { software?.bootloaders() }.orEmpty().any { model in it.hwModels } } == true
                val reinstall = hwModel?.let { model -> guard { software?.usbInstalls() }.orEmpty().firstOrNull { it.hwModel == model } }
                _state.value = UsbRescueState.Found(radio, uf2, bootloader, reinstall)
            }
        }
    }

    /**
     * No Meshtastic answers on the cable (new, empty or other software): the way depends on the chip. An nRF52 or RP2040
     * (or a device already in its bootloader) gets the `.uf2` onto its drive; an ESP32 (or something unknown) is
     * recognised over its ROM loader. Either way the person first confirms which radio it is (ADR 0095 §3).
     */
    private suspend fun installMeshtastic(radio: UsbRadio) {
        if (radio.inBootloader || radio.family.usesDrive) offerRadios(radio, info = null) else offerInstall(radio)
    }

    /** No Meshtastic on an ESP32: recognise the chip over the ROM loader, then offer the radios for it. */
    private suspend fun offerInstall(radio: UsbRadio) {
        _state.value = UsbRescueState.Searching
        val info = guard { usb.detectChip() }
        if (info == null) {
            log("ROM-Lader antwortet nicht")
            _state.value = UsbRescueState.NoSoftware(holdBoot = radio.family == UsbFamily.ESP32)
            return
        }
        offerRadios(radio, info)
    }

    /**
     * The choice of radio (ADR 0095 §3) for [radio] on the cable: [info] from the ROM loader (ESP32), `null` for a
     * family that installs over its drive. Nothing to install for this chip: only the computer remains.
     */
    private suspend fun offerRadios(radio: UsbRadio?, info: EspInfo?) {
        _state.value = UsbRescueState.Searching
        val list = software?.let { guard { it.list() } }
        val catalog = guard { radios() }
        val last = lastHwModel()
        val vendorId = radio?.vendorId ?: 0
        val productId = radio?.productId ?: 0
        var selection = list?.let { selectRadios(catalog, it, info, vendorId, productId, last) }
        // On a drive only a `.uf2` helps (stuck in the bootloader, or a family the USB IDs do not tell).
        val onDrive = radio == null || radio.inBootloader || radio.family.usesDrive
        if (selection != null && onDrive) selection = selection.onlyDrive()
        if (selection == null || selection.all.isEmpty()) {
            log("nichts aufzuspielen für ${info?.chip?.mcu ?: radio?.family ?: "unbekanntes Gerät"}")
            _state.value = UsbRescueState.NoSoftware()
            return
        }
        // Recommended radios on another chip: greyed out with the reason, only when the chip on the cable is known.
        val chipKnown = info != null || (radio != null && radio.family != UsbFamily.UNKNOWN)
        val offered = selection.all.map { it.hwModel }.toSet()
        val unfit = if (!chipKnown) emptyList() else catalog?.radios.orEmpty().filter { r ->
            r.hwModel !in offered && (info?.let { it.chip.mcu != r.mcu } ?: !radio!!.family.fits(r.mcu))
        }
        log(
            "Modell erkannt: ${selection.recognised.joinToString { it.hwModel }.ifEmpty { "keins" }} " +
                "(USB ${vendorId.toString(16)}:${productId.toString(16)}, ${info?.chip?.mcu ?: radio?.family})",
        )
        choosing = radio
        _state.value = UsbRescueState.PickRadio(selection, unfit)
    }

    /**
     * The person chose [option] (ADR 0095 §3): an ESP32 image is written over the ROM loader; for a `.uf2` the radio is
     * asked into its bootloader (unless it is there already) and the person then picks its drive.
     */
    suspend fun pick(option: RadioOption) {
        option.install?.let { return install(it) }
        if (option.uf2 == null) return
        hwModel = option.hwModel
        forBootloader = false
        val radio = choosing
        if (radio != null && !radio.inBootloader) {
            _state.value = UsbRescueState.Working(UsbRescueStep.BOOTLOADER)
            if (guard { usb.touchBootloader(radio) } != true) log("Startprogramm nicht per Software erreichbar, Reset zweimal drücken")
        }
        _state.value = UsbRescueState.PickDrive(option.hwModel)
    }

    /** Download [install], write it over the ROM loader, wait for the restart and continue with the way of use. */
    suspend fun install(install: UsbInstallRelease) {
        purpose = UsbPurpose.SETUP
        _state.value = UsbRescueState.Installing(install.hwModel, progress = null)
        val images = guard { software?.fetchUsbInstall(install) }
        if (images == null) {
            _state.value = UsbRescueState.Failed(UsbRescueStep.DOWNLOAD)
            return
        }
        val written = guard {
            usb.flash(images, install.flashSize) { p -> _state.value = UsbRescueState.Installing(install.hwModel, p) }
        } == true
        if (!written) {
            _state.value = UsbRescueState.Failed(UsbRescueStep.INSTALL)
            return
        }
        // Meshtastic starts for the first time (sets up its storage); then it answers on the cable.
        _state.value = UsbRescueState.Installing(install.hwModel, 1f)
        repeat(RESTART_TRIES) {
            restartWait()
            val radio = guard { usb.find() }
            if (radio != null && radio.answered) {
                _state.value = UsbRescueState.ChooseMode(radio)
                return
            }
        }
        _state.value = UsbRescueState.Failed(UsbRescueStep.INSTALL)
    }

    /** On the move: Bluetooth on if needed, then find and pair it over Bluetooth. */
    suspend fun chooseMobile(radio: UsbRadio) {
        if (radio.bluetoothOff) {
            _state.value = UsbRescueState.Working(UsbRescueStep.BLUETOOTH)
            if (guard { usb.enableBluetooth(radio) } != true) {
                _state.value = UsbRescueState.Failed(UsbRescueStep.BLUETOOTH)
                return
            }
        }
        connect(radio.copy(bluetoothOff = false, wifiOn = false), UsbConnection.BLUETOOTH)
    }

    /** From „Retten“: choose the way of use anew (on the move / stationary) without setting it up again. */
    fun changeMode(radio: UsbRadio) {
        purpose = UsbPurpose.SETUP
        _state.value = UsbRescueState.ChooseMode(radio)
    }

    /** Stationary: ask for the Wi-Fi next (also „Auf WLAN umstellen“ right after plugging in). */
    fun chooseStationary(radio: UsbRadio) {
        purpose = UsbPurpose.SETUP
        _state.value = UsbRescueState.WifiCredentials(radio)
    }

    /**
     * Wi-Fi name and password onto the radio, then ask it over the cable whether it got into the Wi-Fi: in it, connect
     * at the address it reports; not in it after [WIFI_POLL_TRIES], back to the form with a clear message; no answer
     * at all (older software), find it in the Wi-Fi as before.
     */
    suspend fun enableWifi(radio: UsbRadio, ssid: String, password: String) {
        val name = ssid.trim()
        _state.value = UsbRescueState.WifiJoining(radio, name)
        if (guard { usb.enableWifi(radio, name, password) } != true) {
            _state.value = UsbRescueState.WifiCredentials(radio, failed = true, ssid = name)
            return
        }
        val inWifi = radio.copy(wifiOn = true, bluetoothOff = true)
        when (val joined = waitForWifi(inWifi)) {
            null -> connect(inWifi, UsbConnection.WIFI, name)
            else -> if (joined.connected) {
                connect(inWifi.copy(wifiAddress = joined.ip), UsbConnection.WIFI, name)
            } else {
                log("nicht ins WLAN gekommen")
                _state.value = UsbRescueState.WifiCredentials(radio, ssid = name, notJoined = true)
            }
        }
    }

    /**
     * Asks until the radio reports itself in the Wi-Fi, [WIFI_POLL_TRIES] are used up or [WIFI_JOIN_LIMIT] has passed. Returns the last answer:
     * connected, not connected, or `null` if it never answered (restart takes longer, or it does not tell).
     */
    private suspend fun waitForWifi(radio: UsbRadio): RadioWifiStatus? {
        var last: RadioWifiStatus? = null
        val started = TimeSource.Monotonic.markNow()
        repeat(WIFI_POLL_TRIES) {
            // Asking over a cable that does not answer yet takes a while itself: stop at the limit, not only by count.
            if (started.elapsedNow() > WIFI_JOIN_LIMIT) return last
            wifiPollWait()
            val status = guard { usb.wifiStatus(radio) }
            if (status?.connected == true) return status
            if (status != null) last = status
        }
        return last
    }

    /** Find and connect the radio on [via] (also „Nochmal“ after [UsbRescueState.NotSeen]); [ssid]: the Wi-Fi, if known. */
    suspend fun connect(radio: UsbRadio, via: UsbConnection, ssid: String? = null) {
        _state.value = UsbRescueState.Pairing(radio, via)
        val name = guard { connect.invoke(radio, via) }
        _state.value = if (name != null) UsbRescueState.Paired(name, via, ssid) else UsbRescueState.NotSeen(radio, via)
    }

    suspend fun factoryReset(radio: UsbRadio) {
        _state.value = UsbRescueState.Working(UsbRescueStep.RESET)
        _state.value = if (guard { usb.factoryReset(radio) } == true) {
            UsbRescueState.Done(UsbRescueStep.RESET, radio.nameAfterReset)
        } else {
            UsbRescueState.Failed(UsbRescueStep.RESET)
        }
    }

    /** From „Retten“: switch a radio on Wi-Fi back to Bluetooth without setting it up anew. */
    suspend fun enableBluetooth(radio: UsbRadio) {
        _state.value = UsbRescueState.Working(UsbRescueStep.BLUETOOTH)
        _state.value = if (guard { usb.enableBluetooth(radio) } == true) {
            UsbRescueState.Done(UsbRescueStep.BLUETOOTH, radio.name)
        } else {
            UsbRescueState.Failed(UsbRescueStep.BLUETOOTH)
        }
    }

    /** Send a running nRF52 into the bootloader, then let the person choose the drive. */
    suspend fun reinstall(radio: UsbRadio) = enterBootloader(radio, bootloader = false)

    /** Like [reinstall], but a newer boot program goes onto the drive (if there is one). */
    suspend fun updateBootloader(radio: UsbRadio) = enterBootloader(radio, bootloader = true)

    private suspend fun enterBootloader(radio: UsbRadio, bootloader: Boolean) {
        forBootloader = bootloader
        _state.value = UsbRescueState.Working(UsbRescueStep.BOOTLOADER)
        _state.value = if (guard { usb.enterBootloader(radio) } == true) {
            UsbRescueState.PickDrive(hwModel, bootloader)
        } else {
            UsbRescueState.Failed(UsbRescueStep.BOOTLOADER)
        }
    }

    /**
     * Device is already stuck in the bootloader (drive there, green light): continue with the last known model,
     * otherwise the person picks the radio like when setting up.
     */
    suspend fun stuckInBootloader() {
        forBootloader = false
        choosing = null
        hwModel = lastHwModel()?.takeIf { software?.usbReleases(it)?.isNotEmpty() == true }
        val model = hwModel
        if (model != null) _state.value = UsbRescueState.PickDrive(model) else offerRadios(null, info = null)
    }

    /** Drive chosen: check, load and verify the file, write it onto the drive. */
    suspend fun write(drive: Uf2Drive?) {
        if (forBootloader) return writeBootloader(drive)
        val model = hwModel
        if (drive == null || model == null || guard { drive.info() } == null) {
            _state.value = UsbRescueState.Failed(UsbRescueStep.WRITE)
            return
        }
        _state.value = UsbRescueState.Working(UsbRescueStep.DOWNLOAD)
        val release = software?.usbReleases(model)?.firstOrNull()
        val bytes = release?.let { guard { software.fetchVerified(it) } }
        if (release == null || bytes == null) {
            _state.value = UsbRescueState.Failed(UsbRescueStep.DOWNLOAD)
            return
        }
        _state.value = UsbRescueState.Working(UsbRescueStep.WRITE)
        val name = release.url.substringAfterLast('/')
        _state.value = if (guard { drive.write(name, bytes) } == true) {
            UsbRescueState.Done(UsbRescueStep.WRITE)
        } else {
            UsbRescueState.Failed(UsbRescueStep.WRITE)
        }
    }

    /**
     * Renew the boot program: board and version from INFO_UF2.TXT. Only an entry for exactly this
     * board (and, if known, this model) is written, and only if it is newer; a foreign bootloader would make the device
     * unusable. If none is newer, the software is installed anew: otherwise the device is stuck in the bootloader, and
     * only a written file restarts it.
     */
    private suspend fun writeBootloader(drive: Uf2Drive?) {
        val info = drive?.let { guard { it.info() } }?.let(Uf2Info::parse)
        if (drive == null || info?.boardId == null) {
            _state.value = UsbRescueState.Failed(UsbRescueStep.WRITE)
            return
        }
        _state.value = UsbRescueState.Working(UsbRescueStep.DOWNLOAD)
        val model = hwModel
        val release = guard { software?.bootloaders() }.orEmpty()
            .firstOrNull { it.boardId == info.boardId && (model == null || model in it.hwModels) }
        val installed = info.version?.let(FirmwareVersion::parse)
        val offered = release?.version?.let(FirmwareVersion::parse)
        if (release == null || offered == null || (installed != null && FirmwareVersion.compare(installed, offered) >= 0)) {
            log("kein neueres Startprogramm für ${info.boardId} (${info.version}), spiele die Software neu auf")
            forBootloader = false
            return write(drive)
        }
        val bytes = guard { software?.fetchVerified(release.asDownload()) }
        if (bytes == null) {
            _state.value = UsbRescueState.Failed(UsbRescueStep.DOWNLOAD)
            return
        }
        log("Startprogramm ${info.version} → ${release.version} für ${info.boardId}")
        _state.value = UsbRescueState.Working(UsbRescueStep.UPDATE_BOOTLOADER)
        val name = release.url.substringAfterLast('/')
        _state.value = if (guard { drive.write(name, bytes) } == true) {
            UsbRescueState.Done(UsbRescueStep.UPDATE_BOOTLOADER)
        } else {
            UsbRescueState.Failed(UsbRescueStep.UPDATE_BOOTLOADER)
        }
    }

    /** Also catch hard errors (Error) of the USB libraries: the rescue must never terminate the app. */
    private suspend fun <T> guard(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        log("${e::class.simpleName}: ${e.message}")
        null
    }

    private companion object {
        const val RESTART_WAIT_MS = 5_000L
        const val RESTART_TRIES = 8

        /** The radio restarts (some seconds) and joins the Wi-Fi (some more): up to about 45 s, then it says so. */
        const val WIFI_POLL_WAIT_MS = 3_000L
        const val WIFI_POLL_TRIES = 12
        val WIFI_JOIN_LIMIT = 60.seconds
        val MIN_SEARCH_SHOWN = 1200.milliseconds
    }
}

/** Only the options that go onto a drive (`.uf2`); the preselection only if it is one of them. */
private fun RadioSelection.onlyDrive(): RadioSelection {
    val recommended = recommended.filter { it.uf2 != null }
    val recognised = recognised.filter { it.uf2 != null }
    return RadioSelection(recommended, others.filter { it.uf2 != null }, recognised, preselected?.takeIf { it.uf2 != null })
}
