package app.anynear.mesh

import app.anynear.anmp.core.firmware.Esp32Loader
import app.anynear.anmp.core.firmware.EspInfo
import app.anynear.anmp.core.firmware.FlashImage
import app.anynear.anmp.core.firmware.SerialLine
import app.anynear.anmp.core.firmware.UsbFamily
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import app.anynear.AndroidPlatform
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.proto.Config
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.connectAndAwaitReady
import org.meshtastic.sdk.storage.sqldelight.SqlDelightStorageProvider
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal actual fun platformUsbRescue(log: (String) -> Unit): UsbRescue? = AndroidPlatform.context?.let { AndroidUsbRescue(it, log) }

/** Android: radio via USB OTG, meshtastic-sdk over [UsbSerialTransport] (usb-serial-for-android). */
internal class AndroidUsbRescue(private val context: Context, private val log: (String) -> Unit = {}) : UsbRescue {
    private val usb = context.getSystemService(UsbManager::class.java)

    override suspend fun find(): UsbRadio? {
        // The first device the app can talk to; other USB devices (e.g. a hub) come after it.
        val device = usb.deviceList.values.let { all -> all.firstOrNull { safely("Treiber") { UsbSerialTransport.driverFor(it) } != null } ?: all.firstOrNull() }
        if (device == null) {
            log("kein USB-Gerät angeschlossen")
            return null
        }
        val family = UsbFamily.of(device.vendorId, device.productId)
        log("USB-Gerät ${"%04x".format(device.vendorId)}:${"%04x".format(device.productId)} ${device.productName ?: ""} ($family)")
        val port = device.deviceName
        // A USB drive on an nRF52 or RP2040: it waits in its bootloader (UF2), Meshtastic is not running. The software
        // goes onto the drive as a file; nothing to open here, so no permission needed.
        if (family != UsbFamily.ESP32 && hasMassStorage(device)) {
            log("Laufwerk am Kabel: Funkgerät wartet im Startprogramm")
            return UsbRadio(port = port, name = null, firmware = null, answered = false, family = family, inBootloader = true, vendorId = device.vendorId, productId = device.productId)
        }
        if (!usb.hasPermission(device) && !requestPermission(device)) {
            log("Zugriff nicht erlaubt")
            return null
        }
        val driver = safely("Treiber") { UsbSerialTransport.driverFor(device) }
        if (driver == null) {
            log("kein serieller Anschluss an diesem Gerät")
            return null
        }
        log("Treiber ${driver::class.simpleName}")
        // Meshtastic answers within seconds; an empty radio or other software sends no frames at all.
        return withClient(port, quietAfter = QUIET_AFTER) { rc ->
            val meta = (rc.admin.getDeviceMetadata() as? AdminResult.Success)?.value
            val configs = rc.configBundle.value?.configs.orEmpty()
            val wifiOn = configs.firstNotNullOfOrNull { it.network }?.wifi_enabled == true
            val bluetoothDisabled = configs.firstNotNullOfOrNull { it.bluetooth }?.enabled == false
            val snapshot = RadioProfile.DeviceConfigSnapshot(configs, rc.configBundle.value?.moduleConfigs.orEmpty(), rc.channels.value.orEmpty(), rc.ownNode.value?.user)
            val setUp = configs.any { it.lora != null } && (RadioProfile.deviations(snapshot) - ProfileDeviation.NAME).isEmpty()
            UsbRadio(
                port = port,
                name = rc.ownNode.value?.user?.long_name?.takeIf { it.isNotBlank() },
                nodeNum = rc.ownNode.value?.num?.takeIf { it != 0 },
                firmware = meta?.takeIf { it.firmware_version.isNotBlank() }?.let {
                    RadioFirmware(it.firmware_version, it.hw_model.name, hasWifi = it.hasWifi, hasBluetooth = it.hasBluetooth)
                },
                bluetoothOff = meta?.hasBluetooth != false && (wifiOn || bluetoothDisabled),
                wifiOn = wifiOn,
                setUp = setUp,
                family = family,
                vendorId = device.vendorId,
                productId = device.productId,
            )
        } ?: UsbRadio(port = port, name = null, firmware = null, answered = false, family = family, vendorId = device.vendorId, productId = device.productId).also {
            // A serial interface is there, but nothing answers like a radio: factory software or another device.
            log("keine Antwort über USB, vermutlich keine Meshtastic-Software")
        }
    }

    override suspend fun anyDevice(): Boolean = usb.deviceList.isNotEmpty()

    override suspend fun enableBluetooth(radio: UsbRadio): Boolean =
        withClient(radio.port) { rc ->
            val configs = rc.configBundle.value?.configs.orEmpty()
            val network = (configs.firstNotNullOfOrNull { it.network }?.newBuilder() ?: Config.NetworkConfig.Builder())
                .also { it.wifi_enabled = false }.build()
            val bluetooth = (configs.firstNotNullOfOrNull { it.bluetooth }?.newBuilder() ?: Config.BluetoothConfig.Builder())
                .also { it.enabled = true }.build()
            // One transaction, one restart; each value on its own would restart the radio after the first one.
            rc.admin.editSettings {
                setConfig(Config.Builder().also { it.network = network }.build())
                setConfig(Config.Builder().also { it.bluetooth = bluetooth }.build())
            } is AdminResult.Success
        } == true


    override suspend fun enableWifi(radio: UsbRadio, ssid: String, password: String): Boolean =
        withClient(radio.port) { rc ->
            val configs = rc.configBundle.value?.configs.orEmpty()
            val network = (configs.firstNotNullOfOrNull { it.network }?.newBuilder() ?: Config.NetworkConfig.Builder())
                .also {
                    it.wifi_enabled = true
                    it.wifi_ssid = ssid
                    it.wifi_psk = password
                }.build()
            rc.admin.editSettings { setConfig(Config.Builder().also { it.network = network }.build()) } is AdminResult.Success
        } == true

    override suspend fun detectChip(): EspInfo? = withLoader { loader, nativeUsb ->
        val chip = loader.connect(nativeUsb)
        loader.hardReset()
        loader.info ?: EspInfo(chip)
    }

    override suspend fun flash(images: List<FlashImage>, flashSize: Long, onProgress: (Float) -> Unit): Boolean =
        withLoader { loader, nativeUsb ->
            loader.connect(nativeUsb)
            loader.write(images, flashSize, onProgress)
            true
        } == true

    /**
     * The „1200 baud touch“ of the Arduino cores (Adafruit nRF52, RP2040): open the port at 1200 baud, drop DTR, close.
     * Software built on them then restarts into its bootloader; after that the drive shows up. Other software ignores it.
     */
    override suspend fun touchBootloader(radio: UsbRadio): Boolean = withContext(Dispatchers.IO) {
        val device = usb.deviceList[radio.port] ?: return@withContext false
        if (!usb.hasPermission(device) && !requestPermission(device)) return@withContext false
        val driver = safely("Treiber") { UsbSerialTransport.driverFor(device) } ?: return@withContext false
        val connection = usb.openDevice(device) ?: return@withContext false
        val port = driver.ports.first()
        val touched = safely("Startprogramm") {
            port.open(connection)
            port.setParameters(1200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            runCatching { port.dtr = true }
            delay(50)
            runCatching { port.dtr = false }
            true
        } == true
        runCatching { port.close() }
        // The device leaves the cable and comes back as a drive.
        if (touched) delay(BOOTLOADER_WAIT)
        touched
    }

    private fun hasMassStorage(device: UsbDevice): Boolean =
        (0 until device.interfaceCount).any { device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE }

    /** Driver of the first USB radio with permission (asked if needed), for using it over the cable. */
    suspend fun serialDriver(): UsbSerialDriver? {
        val device = usb.deviceList.values.firstOrNull { safely("Treiber") { UsbSerialTransport.driverFor(it) } != null } ?: return null
        if (!usb.hasPermission(device) && !requestPermission(device)) return null
        return UsbSerialTransport.driverFor(device)
    }

    /**
     * The serial port of the first USB radio, raw for the ROM loader (no Meshtastic frames); the block learns whether
     * it is Espressif's own USB ([UsbFamily.nativeEspUsb]).
     */
    private suspend fun <T> withLoader(block: suspend (Esp32Loader, Boolean) -> T): T? = withContext(Dispatchers.IO) {
        val device = usb.deviceList.values.firstOrNull { safely("Treiber") { UsbSerialTransport.driverFor(it) } != null } ?: return@withContext null
        if (!usb.hasPermission(device) && !requestPermission(device)) return@withContext null
        val driver = UsbSerialTransport.driverFor(device) ?: return@withContext null
        val connection = usb.openDevice(device) ?: return@withContext null
        val port = driver.ports.first()
        try {
            port.open(connection)
            port.setParameters(Esp32Loader.ROM_BAUD, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            block(Esp32Loader(UsbLine(port), { zlib(it) }, log = log), UsbFamily.nativeEspUsb(device.vendorId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log("ROM-Lader: ${e::class.simpleName}: ${e.message}")
            null
        } finally {
            runCatching { port.close() }
        }
    }

    override suspend fun factoryReset(radio: UsbRadio): Boolean =
        // preserveBleBonds = false: factory_reset_device, also deletes the pairings (true would be the settings only).
        withClient(radio.port) { rc -> rc.admin.factoryReset(preserveBleBonds = false) is AdminResult.Success } == true

    override suspend fun enterBootloader(radio: UsbRadio): Boolean =
        withClient(radio.port) { rc -> rc.admin.enterDfuMode() is AdminResult.Success } == true

    /**
     * Short connection over USB, without a NodeDB on the phone. [quietAfter]: give up early if not a single Meshtastic
     * frame came by then (empty radio, other software), instead of waiting for the whole [CONNECT_TIMEOUT].
     */
    private suspend fun <T> withClient(port: String, quietAfter: Duration? = null, block: suspend (RadioClient) -> T): T? {
        var rc: RadioClient? = null
        return try {
            withTimeout(CONNECT_TIMEOUT) {
                val device = usb.deviceList[port] ?: error("USB-Gerät $port nicht mehr da")
                val driver = UsbSerialTransport.driverFor(device) ?: error("kein serieller Anschluss")
                val transport = UsbSerialTransport(usb, driver)
                rc = RadioClient.Builder()
                    .transport(transport)
                    .storage(SqlDelightStorageProvider(baseDir = ""))
                    .skipNodeDb()
                    .build()
                coroutineScope {
                    val watchdog = quietAfter?.let { quiet ->
                        launch {
                            delay(quiet)
                            if (!transport.heardFrame) throw IllegalStateException("$quiet ohne Antwort")
                        }
                    }
                    rc!!.connectAndAwaitReady()
                    watchdog?.cancel()
                }
            }
            val result = block(rc!!)
            // Commands go out without a reply; leave a little time before the connection closes.
            delay(1.seconds)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log("Verbindung über USB: ${e::class.simpleName}: ${e.message}")
            null
        } finally {
            withContext(NonCancellable) { runCatching { rc?.disconnect() } }
        }
    }

    private inline fun <T> safely(what: String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        log("$what: ${e::class.simpleName}: ${e.message}")
        null
    }

    private suspend fun requestPermission(device: UsbDevice): Boolean = withTimeoutOrNull(PERMISSION_WAIT) {
        suspendCancellableCoroutine { cont ->
            val action = "${context.packageName}.USB_PERMISSION"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    runCatching { context.unregisterReceiver(this) }
                    if (cont.isActive) cont.resume(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
            val intent = Intent(action).setPackage(context.packageName)
            usb.requestPermission(device, PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_MUTABLE))
        }
    } == true

    companion object {
        private val CONNECT_TIMEOUT = 30.seconds

        /** Meshtastic sends its first frames within a few seconds of the request, even right after a restart. */
        private val QUIET_AFTER = 12.seconds

        /** Time for the device to restart into its bootloader and show up as a drive. */
        private val BOOTLOADER_WAIT = 3.seconds
        private val PERMISSION_WAIT = 2.minutes
    }
}

/** The bootloader drive, chosen by the person in Android's file picker dialog (folder access). */
internal class AndroidUf2Drive(private val context: Context, private val tree: Uri) : Uf2Drive {
    private val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    override suspend fun info(): String? = withContext(Dispatchers.IO) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1).equals("INFO_UF2.TXT", ignoreCase = true)) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                    return@withContext context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }
            }
        }
        null
    }

    override suspend fun write(name: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val file = DocumentsContract.createDocument(context.contentResolver, root, "application/octet-stream", name) ?: return@withContext false
        var written = false
        try {
            context.contentResolver.openOutputStream(file)?.use { out ->
                out.write(bytes)
                out.flush()
                written = true
            }
            written
        } catch (e: Exception) {
            // The device restarts right after the last data; then only closing still fails.
            written
        }
    }
}

/** [SerialLine] over a usb-serial-for-android port. */
private class UsbLine(private val port: UsbSerialPort) : SerialLine {
    private val buffer = ByteArray(4096)

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) { port.write(bytes, 2_000) }

    override suspend fun read(timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val n = port.read(buffer, timeoutMs)
        if (n <= 0) ByteArray(0) else buffer.copyOf(n)
    }

    override suspend fun setBaud(baud: Int) = withContext(Dispatchers.IO) {
        port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
    }

    override suspend fun setLines(dtr: Boolean, rts: Boolean) = withContext(Dispatchers.IO) {
        runCatching { port.dtr = dtr }
        runCatching { port.rts = rts }
        Unit
    }
}

/** zlib stream with header, as the ROM's compressed writing expects. */
private fun zlib(bytes: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.BEST_COMPRESSION)
    deflater.setInput(bytes)
    deflater.finish()
    val out = ByteArrayOutputStream(bytes.size / 2 + 64)
    val chunk = ByteArray(64 * 1024)
    while (!deflater.finished()) out.write(chunk, 0, deflater.deflate(chunk))
    deflater.end()
    return out.toByteArray()
}
