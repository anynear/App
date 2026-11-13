package app.anynear.mesh

import app.anynear.anmp.core.firmware.FirmwarePath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Transfers new software to the radio (ADR 0056 §5), platform-dependent. The admin command that sends an ESP32 into
 * loader mode is sent by [SdkMeshLink] itself beforehand; nRF52 jumps by itself via the bootloader service.
 * Procedures follow Meshtastic-Android (legacy DFU as in v2.7.10, ESP32 per the MeshtasticOTA loader from 2.7.18).
 */
interface FirmwareFlasher {
    /** Paths this platform supports. */
    val paths: Set<FirmwarePath>

    /**
     * nRF52 over Bluetooth: legacy DFU with a jump into the bootloader via its service in the running device. [zip] is
     * the `-ota.zip` from the release. The own connection to the radio must be disconnected beforehand.
     */
    suspend fun nrf52Dfu(address: String, name: String, zip: ByteArray, onProgress: (Float) -> Unit): FirmwareInstallResult

    /**
     * ESP32: [image] to the MeshtasticOTA loader, after the device has restarted into it. [target] says where the
     * loader can be found.
     */
    suspend fun esp32Ota(target: OtaTarget, image: ByteArray, sha256Hex: String, onProgress: (Float) -> Unit): FirmwareInstallResult
}

/** Where the ESP32 loader can be found after the restart. */
sealed interface OtaTarget {
    /** On Wi-Fi; [host] is the previous address of the radio, in case it does not announce itself. */
    data class Wifi(val host: String) : OtaTarget

    /** Over Bluetooth; [address] is the previous address (the loader uses it or the next one). */
    data class Ble(val address: String) : OtaTarget
}

/** The flasher of this platform; `null` where there is none (yet). [log] receives every step and every error. */
internal expect fun platformFirmwareFlasher(log: (String) -> Unit): FirmwareFlasher?

/** A text connection to the ESP32 loader: read lines, write bytes. */
interface OtaChannel {
    /** Largest amount a single [write] can send at once. */
    val maxChunk: Int

    suspend fun write(bytes: ByteArray)

    /** Next line without line ending; `null` after [timeout] or when the connection is closed. */
    suspend fun readLine(timeout: Duration): String?
}

/**
 * Loading protocol of the MeshtasticOTA loader, the same for Wi-Fi and Bluetooth: `OTA <size> <sha256>`, wait for `OK`
 * (before that possibly `ERASING`), file in chunks, over Bluetooth `ACK` after every chunk, `OK` at the end; `ERR …`
 * aborts.
 */
object Esp32OtaProtocol {
    suspend fun run(
        channel: OtaChannel,
        image: ByteArray,
        sha256Hex: String,
        ackPerChunk: Boolean,
        onProgress: (Float) -> Unit,
    ): FirmwareInstallResult = try {
        channel.write("OTA ${image.size} ${sha256Hex.lowercase()}\n".encodeToByteArray())
        if (!awaitOk(channel, ERASE_TIMEOUT)) {
            FirmwareInstallResult.Failed(wroteAnything = false)
        } else {
            onProgress(0f)
            var sent = 0
            var finished = false
            while (sent < image.size) {
                val end = minOf(sent + channel.maxChunk, image.size)
                channel.write(image.copyOfRange(sent, end))
                sent = end
                if (ackPerChunk) {
                    // Over Bluetooth the loader confirms every chunk; it answers the last one with OK right away.
                    when (channel.readLine(CHUNK_TIMEOUT)) {
                        "ACK" -> Unit
                        "OK" -> finished = sent == image.size
                        else -> return FirmwareInstallResult.Failed(wroteAnything = true)
                    }
                }
                onProgress(sent.toFloat() / image.size)
            }
            if (finished || awaitOk(channel, CHUNK_TIMEOUT, ignoreAck = true)) {
                FirmwareInstallResult.Written
            } else {
                FirmwareInstallResult.Failed(wroteAnything = true)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        FirmwareInstallResult.Failed(wroteAnything = true)
    }

    /** Waits for `OK`; `ERASING` (and a late `ACK` at the end) extends, `ERR …` or silence is an error. */
    private suspend fun awaitOk(channel: OtaChannel, timeout: Duration, ignoreAck: Boolean = false): Boolean {
        while (true) {
            val line = withTimeoutOrNull(timeout) { channel.readLine(timeout) } ?: return false
            when {
                line == "OK" || line.startsWith("OK ") -> return true
                line == "ERASING" -> continue
                ignoreAck && line == "ACK" -> continue
                line.isBlank() -> continue
                else -> return false
            }
        }
    }

    /** The loader erases the memory beforehand. */
    val ERASE_TIMEOUT: Duration = 60.seconds
    val CHUNK_TIMEOUT: Duration = 10.seconds
}
