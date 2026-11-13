package app.anynear.mesh

import app.anynear.anmp.core.firmware.FirmwarePath
import android.content.Context
import com.juul.kable.AndroidPeripheral
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import app.anynear.AndroidPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.coroutineScope
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

internal actual fun platformFirmwareFlasher(log: (String) -> Unit): FirmwareFlasher? =
    AndroidPlatform.context?.let { AndroidFirmwareFlasher(it, log) }

/** Android: nRF52 with the Nordic DFU library, ESP32 over Wi-Fi (TCP) and Bluetooth (Kable). */
internal class AndroidFirmwareFlasher(private val context: Context, private val log: (String) -> Unit = {}) : FirmwareFlasher {
    override val paths: Set<FirmwarePath> = buildSet {
        add(FirmwarePath.ESP32_WIFI)
        add(FirmwarePath.ESP32_BLE)
        // The DFU service belongs to the app (manifest); without it no nRF52.
        if (AndroidPlatform.dfuService != null) add(FirmwarePath.NRF52_DFU)
    }

    /**
     * Legacy DFU like Meshtastic-Android v2.7.10: the library jumps into the bootloader via the DFU service of the
     * running device, looks for it (same or next address) and flashes the `-ota.zip`. The pairing stays.
     */
    override suspend fun nrf52Dfu(address: String, name: String, zip: ByteArray, onProgress: (Float) -> Unit): FirmwareInstallResult {
        val service = AndroidPlatform.dfuService ?: return FirmwareInstallResult.Unsupported
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "radio-update.zip").apply { writeBytes(zip) }
        }
        var started = false
        try {
            return suspendCancellableCoroutine { cont ->
                var lastLogged = -1
                val listener = object : DfuProgressListenerAdapter() {
                    override fun onDeviceConnecting(deviceAddress: String) = log("DFU: verbinde mit dem Funkgerät")

                    override fun onDeviceConnected(deviceAddress: String) = log("DFU: verbunden")

                    override fun onEnablingDfuMode(deviceAddress: String) {
                        started = true
                        log("DFU: Funkgerät springt in den Bootloader")
                    }

                    override fun onDfuProcessStarting(deviceAddress: String) {
                        started = true
                        log("DFU: Übertragung beginnt")
                    }

                    override fun onProgressChanged(deviceAddress: String, percent: Int, speed: Float, avgSpeed: Float, currentPart: Int, partsTotal: Int) {
                        started = true
                        if (percent / 10 != lastLogged / 10) {
                            lastLogged = percent
                            log("DFU: $percent %")
                        }
                        onProgress(percent / 100f)
                    }

                    override fun onFirmwareValidating(deviceAddress: String) = log("DFU: Funkgerät prüft die Software")

                    override fun onDeviceDisconnecting(deviceAddress: String?) = log("DFU: trenne")

                    override fun onDfuCompleted(deviceAddress: String) {
                        log("DFU: fertig")
                        if (cont.isActive) cont.resume(FirmwareInstallResult.Written)
                    }

                    override fun onDfuAborted(deviceAddress: String) {
                        log("DFU: abgebrochen")
                        if (cont.isActive) cont.resume(FirmwareInstallResult.Failed(wroteAnything = started))
                    }

                    override fun onError(deviceAddress: String, error: Int, errorType: Int, message: String?) {
                        log("DFU: Fehler $error (Art $errorType): ${message ?: "ohne Text"}")
                        if (cont.isActive) cont.resume(FirmwareInstallResult.Failed(wroteAnything = started))
                    }
                }
                DfuServiceListenerHelper.registerProgressListener(context, listener)
                DfuServiceInitiator.createDfuNotificationChannel(context)
                val controller = DfuServiceInitiator(address)
                    .setDeviceName(name)
                    .setKeepBond(true)
                    .setForceDfu(false)
                    .setForceScanningForNewAddressInLegacyDfu(true)
                    .setPrepareDataObjectDelay(400)
                    .setPacketsReceiptNotificationsValue(8)
                    .setScanTimeout(2000)
                    .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true)
                    .disableResume()
                    .setForeground(true)
                    .setZip(file.absolutePath)
                    .start(context, service)
                cont.invokeOnCancellation {
                    // Once the device has entered the bootloader, never abort: it would stay stuck there. The service
                    // keeps running in the foreground, even with the screen locked.
                    if (!started) controller.abort()
                    DfuServiceListenerHelper.unregisterProgressListener(context, listener)
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { file.delete() }
        }
    }

    override suspend fun esp32Ota(target: OtaTarget, image: ByteArray, sha256Hex: String, onProgress: (Float) -> Unit): FirmwareInstallResult =
        when (target) {
            is OtaTarget.Wifi -> wifi(target.host, image, sha256Hex, onProgress)
            is OtaTarget.Ble -> ble(target.address, image, sha256Hex, onProgress)
        }

    // --- ESP32 over Wi-Fi: loader announces itself by UDP broadcast on 3232, data over TCP 3232 ---

    private suspend fun wifi(knownHost: String, image: ByteArray, sha256Hex: String, onProgress: (Float) -> Unit): FirmwareInstallResult {
        val host = discoverLoader() ?: run {
            // If the loader does not announce itself, it usually has the same address as before.
            delay(WIFI_FALLBACK_WAIT)
            knownHost
        }
        repeat(WIFI_CONNECT_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(RETRY_PAUSE)
            val socket = withContext(Dispatchers.IO) {
                runCatching { Socket().apply { connect(InetSocketAddress(host, OTA_PORT), 5_000) } }.getOrNull()
            } ?: return@repeat
            log("OTA: verbunden mit dem Lader über WLAN ($host)")
            return try {
                Esp32OtaProtocol.run(TcpChannel(socket), image, sha256Hex, ackPerChunk = false, onProgress).also { log("OTA: Ergebnis $it") }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { runCatching { socket.close() } }
            }
        }
        log("OTA: Lader im WLAN nicht erreicht ($host)")
        return FirmwareInstallResult.Failed(wroteAnything = false)
    }

    /** Listens for up to [DISCOVERY_WINDOW] for `Meshtastic_XXXX <name>` and returns the sender address. */
    private suspend fun discoverLoader(): String? = withContext(Dispatchers.IO) {
        runCatching {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(OTA_PORT))
                socket.soTimeout = DISCOVERY_WINDOW.inWholeMilliseconds.toInt()
                val buf = ByteArray(256)
                val until = System.currentTimeMillis() + DISCOVERY_WINDOW.inWholeMilliseconds
                while (System.currentTimeMillis() < until) {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length).trim()
                    if (LOADER_BEACON.matches(text)) return@runCatching packet.address.hostAddress
                }
                null
            }
        }.getOrNull()
    }

    private class TcpChannel(private val socket: Socket) : OtaChannel {
        private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
        private val out = socket.getOutputStream()
        override val maxChunk: Int = 1024

        override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
            out.write(bytes)
            out.flush()
            // Like the official app: leave a short pause, the loader writes the flash alongside.
            if (bytes.size == maxChunk) delay(10)
        }

        override suspend fun readLine(timeout: Duration): String? = withContext(Dispatchers.IO) {
            socket.soTimeout = timeout.inWholeMilliseconds.toInt()
            try {
                reader.readLine()?.trim()
            } catch (e: SocketTimeoutException) {
                null
            }
        }
    }

    // --- ESP32 over Bluetooth: loader with its own service, same or next address ---

    private suspend fun ble(address: String, image: ByteArray, sha256Hex: String, onProgress: (Float) -> Unit): FirmwareInstallResult {
        delay(BLE_REBOOT_WAIT)
        val wanted = setOf(address.uppercase(), nextAddress(address))
        repeat(BLE_CONNECT_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(RETRY_PAUSE)
            val advertisement = withTimeoutOrNull(BLE_SCAN_TIMEOUT) {
                Scanner { filters { match { services = listOf(OTA_SERVICE) } } }
                    .advertisements.first { it.identifier.uppercase() in wanted }
            } ?: return@repeat
            val peripheral = Peripheral(advertisement) {}
            try {
                withTimeoutOrNull(BLE_CONNECT_TIMEOUT) { peripheral.connect() } ?: return@repeat
                (peripheral as? AndroidPeripheral)?.requestMtu(517)
                val maxNoResponse = peripheral.maximumWriteValueLengthForType(WriteType.WithoutResponse)
                return coroutineScope {
                    val lines = Channel<String>(Channel.UNLIMITED)
                    val buffer = StringBuilder()
                    val observer = launch {
                        peripheral.observe(OTA_TX).collect { chunk ->
                            buffer.append(chunk.decodeToString())
                            while (true) {
                                val end = buffer.indexOf("\n")
                                if (end < 0) break
                                lines.trySend(buffer.substring(0, end).trim())
                                buffer.deleteRange(0, end + 1)
                            }
                        }
                    }
                    delay(500)
                    val channel = object : OtaChannel {
                        override val maxChunk: Int = minOf(512, maxNoResponse)
                        private var first = true

                        override suspend fun write(bytes: ByteArray) {
                            if (first) {
                                // The header line with acknowledgement, in pieces if it is longer than one transfer.
                                first = false
                                bytes.toList().chunked(maxNoResponse).forEach { peripheral.write(OTA_RX, it.toByteArray(), WriteType.WithResponse) }
                            } else {
                                peripheral.write(OTA_RX, bytes, WriteType.WithoutResponse)
                            }
                        }

                        override suspend fun readLine(timeout: Duration): String? = withTimeoutOrNull(timeout) { lines.receive() }
                    }
                    log("OTA: verbunden mit dem Lader über Bluetooth (${advertisement.identifier})")
                    val result = Esp32OtaProtocol.run(channel, image, sha256Hex, ackPerChunk = true, onProgress).also { log("OTA: Ergebnis $it") }
                    observer.cancel()
                    result
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@repeat
            } finally {
                withContext(NonCancellable) { runCatching { peripheral.disconnect() } }
            }
        }
        return FirmwareInstallResult.Failed(wroteAnything = false)
    }

    companion object {
        private const val OTA_PORT = 3232
        private val LOADER_BEACON = Regex("""^Meshtastic_[0-9A-F]{4}\s+\S{1,32}$""")
        private val DISCOVERY_WINDOW = 3.seconds
        private val WIFI_FALLBACK_WAIT = 8.seconds
        private const val WIFI_CONNECT_ATTEMPTS = 10
        private val RETRY_PAUSE = 2.seconds

        private val OTA_SERVICE = Uuid.parse("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val OTA_TX = characteristicOf(Uuid.parse("4fafc201-1fb5-459e-8fcc-c5c9c331914b"), Uuid.parse("62ec0272-3ec5-11eb-b378-0242ac130003"))
        private val OTA_RX = characteristicOf(Uuid.parse("4fafc201-1fb5-459e-8fcc-c5c9c331914b"), Uuid.parse("62ec0272-3ec5-11eb-b378-0242ac130005"))
        private val BLE_REBOOT_WAIT = 5.seconds
        private val BLE_SCAN_TIMEOUT = 20.seconds
        private val BLE_CONNECT_TIMEOUT = 15.seconds
        private const val BLE_CONNECT_ATTEMPTS = 5

        /** Bluetooth address plus one in the last byte (bootloader and loader sometimes use the next one). */
        internal fun nextAddress(address: String): String {
            val parts = address.uppercase().split(':')
            if (parts.size != 6) return address.uppercase()
            val last = (parts.last().toInt(16) + 1) and 0xFF
            return (parts.dropLast(1) + last.toString(16).uppercase().padStart(2, '0')).joinToString(":")
        }
    }
}
