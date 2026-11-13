package app.anynear.app

import app.anynear.anmp.core.firmware.BootloaderRelease
import app.anynear.anmp.core.firmware.FirmwareDownloads
import app.anynear.anmp.core.firmware.FirmwareList
import app.anynear.anmp.core.firmware.FirmwarePath
import app.anynear.anmp.core.firmware.FirmwareRelease
import app.anynear.anmp.core.firmware.FirmwareVersion
import app.anynear.anmp.core.firmware.FlashImage
import app.anynear.anmp.core.firmware.UsbInstallRelease
import app.anynear.anmp.core.firmware.fetchUsbInstall
import app.anynear.anmp.core.firmware.fetchVerified
import app.anynear.anmp.core.firmware.newestFirmwareList
import app.anynear.andp.Key
import app.anynear.andp.Sig
import app.anynear.andp.SigningBytes
import app.anynear.anmp.AnmpInfo
import app.anynear.anmp.core.AnmpCore
import app.anynear.core.sha256
import app.anynear.mesh.DeviceSetupCheck
import app.anynear.mesh.FirmwareInstallResult
import app.anynear.mesh.LinkState
import app.anynear.mesh.MeshLink
import app.anynear.mesh.RadioFirmware
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Why no update is possible right now (ADR 0056 §2 step 3); the notice stays, the button waits. */
enum class FirmwareBlocker {
    /** The radio is not connected directly to this phone. */
    NOT_CONNECTED,

    /** Radio not on power and below 50 % or battery level unknown. */
    RADIO_BATTERY,

    /** Phone below 30 %. */
    PHONE_BATTERY,

    /** Own messages are still waiting for the radio. */
    MESSAGES_WAITING,
}

/** State of the software of the own radio (ADR 0056), as the radio page shows it. */
sealed interface RadioSoftwareState {
    /** Nothing to do or nothing known yet. */
    data object Idle : RadioSoftwareState

    /**
     * [release] is newer than the software on the radio. [installable]: the app can install it over the current
     * connection; otherwise there are only the instructions via USB. [ready]: loaded and verified. Whether it is
     * possible right now is told by [RadioSoftware.blocker].
     */
    data class Available(val release: FirmwareRelease, val installable: Boolean, val ready: Boolean) : RadioSoftwareState

    /** Running; [writing]: the radio is already being written to, cancelling is then no longer without consequences. */
    data class Installing(val version: String, val writing: Boolean, val progress: Float?) : RadioSoftwareState

    /** Installed; [settingsKept]: radio profile and radio channel are still there (ADR 0006, ADR 0051). */
    data class Done(val version: String, val settingsKept: Boolean) : RadioSoftwareState

    /** Went wrong; from [RadioSoftware.USB_AFTER] failed attempts on, the page shows the rescue via USB. */
    data class Failed(val release: FirmwareRelease, val attempts: Int, val wroteAnything: Boolean) : RadioSoftwareState
}

/** Result of „Nach neuer Software sehen“ under „Erweitert“. */
sealed interface SoftwareCheck {
    data object Checking : SoftwareCheck

    /** No radio connected or its software version not read yet. */
    data object NoRadio : SoftwareCheck

    /** No answer from the relays or no valid list. */
    data object Failed : SoftwareCheck

    /** No newer approved release for this model; [version] is running on the radio. */
    data class UpToDate(val version: String) : SoftwareCheck

    /** [version] is approved; [installable]: the app can install it over the current connection. */
    data class Newer(val version: String, val installable: Boolean) : SoftwareCheck
}

/** Fetches lists and files (ADR 0056 §1a); replaceable in tests. Everything is verified in [RadioSoftware]. */
interface FirmwareSource {
    /** Key that signs the list; `null` as long as the space has not been created. */
    val listKey: String?

    /** All signed lists that can be found; `null` without an answer. */
    suspend fun lists(): List<String>?

    /** The file of [release]; `null` if it cannot be obtained right now. */
    suspend fun download(release: FirmwareRelease): ByteArray?
}

/**
 * Newest release for [device] that is newer than the installed one and fits [appBuild]; with several paths for
 * the same version, the first one from [possible]. `null` if there is nothing newer.
 */
fun pickRelease(list: FirmwareList, device: RadioFirmware, possible: Set<FirmwarePath>, appBuild: Long?): FirmwareRelease? {
    val newer = list.releases.filter { release ->
        release.hwModel == device.hwModel &&
            release.url.startsWith(RadioSoftware.DOWNLOAD_PREFIX) &&
            release.minAppBuild.let { min -> min == null || appBuild == null || appBuild >= min } &&
            FirmwareVersion.isNewer(release.version, device.version)
    }
    val best = newer.maxWithOrNull { a, b -> FirmwareVersion.compare(FirmwareVersion.parse(a.version)!!, FirmwareVersion.parse(b.version)!!) } ?: return null
    val sameVersion = newer.filter { it.version == best.version }
    return sameVersion.firstOrNull { it.path in possible } ?: sameVersion.first()
}

/**
 * Paths that can work over the current connection: Wi-Fi only over a network connection, Bluetooth only over
 * Bluetooth, USB never. ESP32 only from [FirmwareVersion.ESP32_OTA_SINCE] on: older firmware does not know `ota_request`
 * and does not have the MeshtasticOTA loader; it has to be updated once via USB. Whether the app already masters the
 * path is told by [MeshLink.firmwarePaths].
 */
fun pathsOverConnection(network: Boolean, device: RadioFirmware): Set<FirmwarePath> = buildSet {
    val esp32Ota = FirmwareVersion.parse(device.version)?.let { FirmwareVersion.compare(it, FirmwareVersion.ESP32_OTA_SINCE) >= 0 } == true
    if (network && device.hasWifi && esp32Ota) add(FirmwarePath.ESP32_WIFI)
    if (!network && device.hasBluetooth) {
        if (esp32Ota) add(FirmwarePath.ESP32_BLE)
        add(FirmwarePath.NRF52_DFU)
    }
}

/** Conditions before installing (ADR 0056 §2 step 3); `null` if everything fits. */
fun firmwareBlocker(connected: Boolean, radioBattery: Int?, phoneBattery: Int?, messagesWaiting: Boolean): FirmwareBlocker? = when {
    !connected -> FirmwareBlocker.NOT_CONNECTED
    radioBattery == null || radioBattery < RadioSoftware.MIN_RADIO_BATTERY -> FirmwareBlocker.RADIO_BATTERY
    phoneBattery != null && phoneBattery < RadioSoftware.MIN_PHONE_BATTERY -> FirmwareBlocker.PHONE_BATTERY
    messagesWaiting -> FirmwareBlocker.MESSAGES_WAITING
    else -> null
}

/**
 * New software for the own radio (ADR 0056): check (daily, on connecting, on request), load and verify in the
 * background, but install only with consent and only as long as the app is open.
 *
 * Without a space on the relays (not created yet, §1a) there is no key, and the app does not even check. It can
 * install only paths from [MeshLink.firmwarePaths]; for all others there are the instructions via USB.
 */
class RadioSoftware(
    private val scope: CoroutineScope,
    private val link: MeshLink,
    private val source: FirmwareSource,
    /** Is the radio connected over Wi-Fi (not Bluetooth)? */
    private val overNetwork: () -> Boolean,
    private val phoneBattery: () -> Int?,
    /** Are own messages still waiting for the radio? */
    private val messagesWaiting: suspend () -> Boolean,
    private val appBuild: Long?,
    private val now: () -> Long,
) {
    private val lock = Mutex()
    private val list = MutableStateFlow<FirmwareList?>(null)
    private val image = MutableStateFlow<Pair<FirmwareRelease, ByteArray>?>(null)
    private val run = MutableStateFlow<RadioSoftwareState?>(null)
    private val attempts = mutableMapOf<String, Int>()
    private var lastCheck: Long? = null
    private var installJob: Job? = null
    private val listKey: Key? = source.listKey?.let { runCatching { Key.parse(it) }.getOrNull() }

    /** Does the app check at all? Only once the key of the list is settled. */
    val checksEnabled: Boolean get() = listKey != null

    private val _checkResult = MutableStateFlow<SoftwareCheck?>(null)

    /** Result of the last check on request ([check] with `force`); `null` as long as nobody has asked. */
    val checkResult: StateFlow<SoftwareCheck?> = _checkResult.asStateFlow()

    private val _postponed = MutableStateFlow(false)

    /** „Später“: the notice is gone until the next start of the app; on the radio page it stays (§3). */
    val postponed: StateFlow<Boolean> = _postponed.asStateFlow()

    /** State for the radio page. */
    val state: StateFlow<RadioSoftwareState> =
        combine(list, link.firmware, link.state, image, run) { l, device, _, loaded, running ->
            if (running != null) return@combine running
            if (l == null || device == null) return@combine RadioSoftwareState.Idle
            val possible = pathsOverConnection(overNetwork(), device) intersect link.firmwarePaths
            val release = pickRelease(l, device, possible, appBuild) ?: return@combine RadioSoftwareState.Idle
            RadioSoftwareState.Available(release, installable = release.path in possible, ready = loaded?.first == release)
        }.stateIn(scope, SharingStarted.Eagerly, RadioSoftwareState.Idle)

    /** Notice „Neue Software für dein Funkgerät“, as long as „Später“ has not been chosen. */
    val notice: StateFlow<RadioSoftwareState.Available?> =
        combine(state, _postponed) { s, later -> (s as? RadioSoftwareState.Available)?.takeIf { !later && it.ready && it.installable } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    fun start() {
        if (listKey == null) return
        // Check on connecting (new radio or new software), at most hourly.
        scope.launch {
            link.firmware.filterNotNull().distinctUntilChanged().collect { check(force = false) }
        }
        scope.launch {
            while (true) {
                delay(CHECK_INTERVAL)
                check(force = false)
            }
        }
        // Load and verify a found version right away in the background (§2 step 2).
        scope.launch {
            state.collect { s -> if (s is RadioSoftwareState.Available && !s.ready && s.installable) download(s.release) }
        }
    }

    /**
     * Check; without [force] at most every [MIN_CHECK_GAP]. With [force] (on request) the result is afterwards in
     * [checkResult].
     */
    fun check(force: Boolean) {
        val key = listKey ?: run {
            if (force) _checkResult.value = SoftwareCheck.Failed
            return
        }
        if (force) _checkResult.value = SoftwareCheck.Checking
        scope.launch {
            lock.withLock {
                val last = lastCheck
                if (!force && last != null && now() - last < MIN_CHECK_GAP.inWholeMilliseconds) return@launch
                lastCheck = now()
            }
            val found = source.lists()?.let { newestFirmwareList(it, key) }
            found?.takeIf { it.issued >= (list.value?.issued ?: 0) }?.let { list.value = it }
            if (force) _checkResult.value = outcome(list.value.takeIf { found != null })
        }
    }

    private fun outcome(current: FirmwareList?): SoftwareCheck {
        val device = link.firmware.value?.takeIf { link.state.value == LinkState.Connected } ?: return SoftwareCheck.NoRadio
        current ?: return SoftwareCheck.Failed
        val possible = pathsOverConnection(overNetwork(), device) intersect link.firmwarePaths
        val release = pickRelease(current, device, possible, appBuild) ?: return SoftwareCheck.UpToDate(device.version)
        return SoftwareCheck.Newer(release.version, installable = release.path in possible)
    }

    /** Conditions for the button; the page asks when displaying, [install] once more before the start. */
    suspend fun blocker(): FirmwareBlocker? = firmwareBlocker(
        connected = link.state.value == LinkState.Connected,
        radioBattery = link.ownDevice.value?.batteryLevel,
        phoneBattery = phoneBattery(),
        messagesWaiting = messagesWaiting(),
    )

    fun postpone() {
        _postponed.value = true
    }

    /**
     * Install with consent (§2 step 5). Call only while the page „Lass anynear offen“ is visible.
     * Returns the reason if it is not possible right now.
     */
    suspend fun install(): FirmwareBlocker? {
        val s = state.value as? RadioSoftwareState.Available ?: return null
        if (!s.installable) return null
        blocker()?.let { return it }
        val loaded = image.value?.takeIf { it.first == s.release } ?: return null
        installJob = scope.launch { runInstall(loaded.first, loaded.second) }
        return null
    }

    /**
     * The app goes into the background. Before writing it cancels; after that it continues as long as the system lets
     * the app, and resumes on coming back (§2 step 5).
     */
    fun onBackground() {
        val running = run.value as? RadioSoftwareState.Installing ?: return
        if (!running.writing) cancel()
    }

    /** Cancel as long as nothing has been written yet. */
    fun cancel() {
        val running = run.value as? RadioSoftwareState.Installing ?: return
        if (running.writing) return
        installJob?.cancel()
        installJob = null
        run.value = null
    }

    /** Result seen: back to the normal state. */
    fun dismissResult() {
        if (run.value is RadioSoftwareState.Done || run.value is RadioSoftwareState.Failed) run.value = null
    }

    private suspend fun download(release: FirmwareRelease) {
        image.value = (fetchVerified(release) ?: return).let { release to it }
    }

    /** Loads the file of [release] and checks size and SHA-256; `null` if it cannot be obtained or does not match. */
    suspend fun fetchVerified(release: FirmwareRelease): ByteArray? = fetchVerified(release, source::download)

    /** The current list; fetches it first if there is none yet. `null` without key or answer. */
    private suspend fun currentList(): FirmwareList? {
        val key = listKey ?: return null
        if (list.value == null) source.lists()?.let { newestFirmwareList(it, key) }?.let { list.value = it }
        return list.value
    }

    /**
     * The `.uf2` for the rescue via USB ([FirmwarePath.USB]) from the current list, for [hwModel] or for all
     * models, sorted by model. Fetches the list beforehand if there is none yet.
     */
    suspend fun usbReleases(hwModel: String? = null): List<FirmwareRelease> = currentList()?.usbReleases(hwModel).orEmpty()

    /** Boot programs from the current list, only with a link at [BOOTLOADER_PREFIX]; fetches the list first if needed. */
    suspend fun bootloaders(): List<BootloaderRelease> = currentList()?.usableBootloaders().orEmpty()

    /**
     * Complete images for the first install over USB (ADR 0094 §2), for the chip [mcu] or all, only with links at
     * Meshtastic and the first file at address 0. Fetches the list first if needed.
     */
    suspend fun usbInstalls(mcu: String? = null): List<UsbInstallRelease> = currentList()?.usbInstallsFor(mcu).orEmpty()

    /** The whole current list, e.g. for the choice of model with `radios.json` ([selectRadios]). */
    suspend fun list(): FirmwareList? = currentList()

    /**
     * Downloads and checks every file of [install] and returns what is written in this order: first the erased areas
     * (as 0xFF), then the files. `null` if a file is missing or does not match.
     */
    suspend fun fetchUsbInstall(install: UsbInstallRelease): List<FlashImage>? = fetchUsbInstall(install, source::download)

    private suspend fun runInstall(release: FirmwareRelease, bytes: ByteArray) {
        run.value = RadioSoftwareState.Installing(release.version, writing = false, progress = null)
        val result = try {
            link.installFirmware(release.path, bytes) { progress ->
                run.value = RadioSoftwareState.Installing(release.version, writing = true, progress = progress)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirmwareInstallResult.Failed(wroteAnything = (run.value as? RadioSoftwareState.Installing)?.writing == true)
        }
        run.value = when (result) {
            FirmwareInstallResult.Written -> verify(release)
            is FirmwareInstallResult.Failed -> failed(release, result.wroteAnything)
            FirmwareInstallResult.Unsupported -> null
        }
    }

    /** §2 step 6: new version reported, radio profile and radio channel still there. */
    private suspend fun verify(release: FirmwareRelease): RadioSoftwareState {
        run.value = RadioSoftwareState.Installing(release.version, writing = true, progress = 1f)
        val reported = withTimeoutOrNull(VERIFY_TIMEOUT) {
            link.firmware.first { it != null && it.version == release.version }
        }
        if (reported == null) return failed(release, wroteAnything = true)
        attempts.remove(release.version)
        image.value = null
        // After the restart the connection reads profile and channels anew; only then is the comparison meaningful.
        val read = withTimeoutOrNull(SETTINGS_TIMEOUT) {
            combine(link.setupCheck, link.circleChannels) { check, channels -> check?.let { it to channels.orEmpty() } }.filterNotNull().first()
        }
        val settingsKept = read != null && read.first.kind == DeviceSetupCheck.Kind.OK && read.second.any { it.name == AnmpInfo.RADIO_CHANNEL_NAME }
        return RadioSoftwareState.Done(release.version, settingsKept)
    }

    private fun failed(release: FirmwareRelease, wroteAnything: Boolean): RadioSoftwareState {
        val n = (attempts[release.version] ?: 0) + 1
        attempts[release.version] = n
        return RadioSoftwareState.Failed(release, n, wroteAnything)
    }

    companion object {
        /** Loading happens only from here: Meshtastic stores every file of a release individually there (§1a). */
        const val DOWNLOAD_PREFIX = FirmwareDownloads.DOWNLOAD_PREFIX

        /** The app loads boot programs (bootloaders) only from here, as Meshtastic recommends them. */
        const val BOOTLOADER_PREFIX = FirmwareDownloads.BOOTLOADER_PREFIX

        const val MIN_RADIO_BATTERY = 50
        const val MIN_PHONE_BATTERY = 30

        /** After this many failed attempts the page shows the rescue via USB (§2 step 7). */
        const val USB_AFTER = 2

        val CHECK_INTERVAL: Duration = 24.hours
        val MIN_CHECK_GAP: Duration = 1.hours
        val VERIFY_TIMEOUT: Duration = 5.minutes
        val SETTINGS_TIMEOUT: Duration = 1.minutes
    }
}

/**
 * [FirmwareSource]: the signed list from the space on the relays (ADR 0056 §1a), the file via its link at
 * Meshtastic. The verification of signature, size and SHA-256 is done by [RadioSoftware].
 */
class RelayFirmwareSource(private val core: AnmpCore, private val client: HttpClient = HttpClient()) : FirmwareSource {
    override val listKey: String? get() = core.radioSoftwareKey

    override suspend fun lists(): List<String>? = core.radioSoftwareLists()?.map { it.decodeToString() }

    override suspend fun download(release: FirmwareRelease): ByteArray? {
        if (!FirmwareDownloads.allowed(release.url)) return null
        return fetch(release.url) { it.readRawBytes() }
    }

    private suspend fun <T> fetch(url: String, read: suspend (HttpResponse) -> T): T? = try {
        val response = client.get(url)
        if (response.status.isSuccess()) read(response) else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
