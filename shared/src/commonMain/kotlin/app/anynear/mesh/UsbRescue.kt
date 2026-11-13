package app.anynear.mesh

import app.anynear.anmp.core.firmware.EspInfo
import app.anynear.anmp.core.firmware.FlashImage
import app.anynear.anmp.core.firmware.UsbFamily

/**
 * Rescue via USB cable on the phone (ADR 0056 §2 step 7), platform-dependent. The app talks to a running radio
 * over the serial interface (meshtastic-sdk); an nRF52 in the bootloader shows up as a USB drive ([Uf2Drive]).
 */
interface UsbRescue {
    /**
     * Looks for a radio on the USB port, asks for permission and reads name and software. `null` if none is
     * connected or the permission is missing.
     */
    suspend fun find(): UsbRadio?

    /** Resets the radio to factory settings, including all pairings. */
    suspend fun factoryReset(radio: UsbRadio): Boolean

    /** Sends an nRF52 into the bootloader; afterwards it shows up as a USB drive. */
    suspend fun enterBootloader(radio: UsbRadio): Boolean

    /**
     * Switches Wi-Fi off and Bluetooth on (one restart). On an ESP32 Meshtastic can only use one of the two, so a
     * radio set up for Wi-Fi is invisible to the Bluetooth search.
     */
    suspend fun enableBluetooth(radio: UsbRadio): Boolean = false

    /**
     * Stationary in the Wi-Fi (ADR 0094 §3): writes Wi-Fi name and password and switches Wi-Fi on (Bluetooth goes off,
     * one restart). The password only goes to the radio.
     */
    suspend fun enableWifi(radio: UsbRadio, ssid: String, password: String): Boolean = false

    /**
     * Whether the phone sees any device on its USB port at all. `false` means nothing reaches the app: a cable that only
     * charges, USB host (OTG) switched off on the phone, or the radio is not powered. Then no permission or software
     * can help, and the dialog says what to check.
     */
    suspend fun anyDevice(): Boolean = true

    /**
     * Restarts the radio into the ROM loader and reports its chip and built-in memory; `null` if it does not answer
     * there (ESP32 only).
     */
    suspend fun detectChip(): EspInfo? = null

    /** Writes [images] over the ROM loader (ADR 0094 §2) and restarts the radio; [onProgress] 0..1. */
    suspend fun flash(images: List<FlashImage>, flashSize: Long, onProgress: (Float) -> Unit): Boolean = false

    /**
     * nRF52 or RP2040 without Meshtastic: asks the software on it to start its bootloader (open the port at 1200 baud
     * and close it again, as the Arduino cores do), so that its drive shows up. Best effort, `false` if not possible.
     */
    suspend fun touchBootloader(radio: UsbRadio): Boolean = false
}

/** A radio connected via USB; [port] is the platform's identifier, [nodeNum] the node number. */
data class UsbRadio(
    val port: String,
    val name: String?,
    val firmware: RadioFirmware?,
    val nodeNum: Int? = null,
    /** `false`: something with a serial interface is plugged in, but no Meshtastic answers (e.g. factory software). */
    val answered: Boolean = true,
    /** Bluetooth is off on the radio (Wi-Fi on or Bluetooth disabled), so the Bluetooth search cannot find it. */
    val bluetoothOff: Boolean = false,
    /** Wi-Fi is on: the radio is used stationary in the Wi-Fi (ADR 0094 §3). */
    val wifiOn: Boolean = false,
    /** Set up for anynear: Meshtastic runs and the radio profile fits (ADR 0094 §1). */
    val setUp: Boolean = false,
    /** Chip family from the USB IDs; tells how Meshtastic gets onto a radio that does not answer. */
    val family: UsbFamily = UsbFamily.UNKNOWN,
    /** Already in its bootloader: shows up as a USB drive (nRF52, RP2040), the software goes onto it as a file. */
    val inBootloader: Boolean = false,
    /** USB IDs of the device on the cable (0: unknown); tell the USB-serial chip of the board. */
    val vendorId: Int = 0,
    val productId: Int = 0,
) {
    /**
     * This is the radio's name over Bluetooth after the reset: Meshtastic resets the short name to the last four
     * hex digits and then announces itself as `Meshtastic_xxxx` (firmware `getDeviceName`).
     */
    val nameAfterReset: String? get() = nodeNum?.let { "Meshtastic" + bluetoothSuffix }

    /**
     * End of the radio's Bluetooth name (`_xxxx`, the last four hex digits of the node number, firmware `getDeviceName`),
     * so that the search recognizes exactly the radio on the cable and not the neighbour's.
     */
    val bluetoothSuffix: String? get() = nodeNum?.let { "_" + (it and 0xFFFF).toString(16).padStart(4, '0') }
}

/**
 * The USB drive of an nRF52 in the bootloader (UF2). Copying the `.uf2` onto it installs the software; the device then
 * restarts on its own.
 */
interface Uf2Drive {
    /** Content of `INFO_UF2.TXT`; `null` if it is not a UF2 drive. */
    suspend fun info(): String?

    /** Writes [bytes] as [name] to the drive. */
    suspend fun write(name: String, bytes: ByteArray): Boolean
}

/** The rescue of this platform; `null` where there is none (iOS). [log] receives steps and errors. */
internal expect fun platformUsbRescue(log: (String) -> Unit): UsbRescue?
