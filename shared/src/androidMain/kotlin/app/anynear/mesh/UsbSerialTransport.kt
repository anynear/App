package app.anynear.mesh

import app.anynear.anmp.core.firmware.UsbFamily
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import org.meshtastic.sdk.Frame
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.RadioTransport
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.TransportSpec
import org.meshtastic.sdk.TransportState
import org.meshtastic.sdk.WireFraming

/**
 * Radio on the phone's USB port as a [RadioTransport] for meshtastic-sdk, over usb-serial-for-android like
 * Meshtastic-Android (CDC-ACM for nRF52 and ESP32-S3, plus bridges such as CP210x and CH340). Sequence like the SDK's
 * serial transport: 115200 8N1, DTR and RTS set (never toggle: that resets some boards), four wake-up bytes,
 * frames `0x94 0xC3 <length>` unchanged in both directions.
 */
internal class UsbSerialTransport(private val usb: UsbManager, private val driver: UsbSerialDriver) : RadioTransport {
    override val identity: TransportIdentity = TransportIdentity.of(TransportSpec.SerialAndroid(driver.device.deviceName))

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val frames = Channel<Frame>(capacity = 64)

    /** At least one Meshtastic frame came in: something on the cable speaks Meshtastic. */
    @Volatile
    var heardFrame = false
        private set
    private var port: UsbSerialPort? = null
    private var reader: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun connect() = withContext(Dispatchers.IO) {
        _state.value = TransportState.Connecting
        try {
            val connection = usb.openDevice(driver.device) ?: throw MeshtasticException.Transport("USB-Gerät nicht zu öffnen (Erlaubnis?)", null)
            val p = driver.ports.first()
            p.open(connection)
            p.setParameters(BAUD, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            runCatching { p.dtr = true }
            runCatching { p.rts = true }
            p.write(WAKE, WRITE_TIMEOUT_MS)
            port = p
            reader = scope.launch { read(p) }
            _state.value = TransportState.Connected
        } catch (e: MeshtasticException) {
            _state.value = TransportState.Error(e, true)
            close()
            throw e
        } catch (e: Exception) {
            _state.value = TransportState.Error(e, true)
            close()
            throw MeshtasticException.Transport("USB-Verbindung: ${e.message}", e)
        }
    }

    override suspend fun disconnect() {
        _state.value = TransportState.Disconnected
        reader?.cancelAndJoin()
        close()
    }

    override suspend fun send(frame: Frame) {
        val p = port ?: throw MeshtasticException.Transport("USB nicht verbunden", null)
        withContext(Dispatchers.IO) {
            try {
                p.write(frame.bytes.toByteArray(), WRITE_TIMEOUT_MS)
            } catch (e: Exception) {
                throw MeshtasticException.Transport("USB senden: ${e.message}", e)
            }
        }
    }

    override fun frames(): Flow<Frame> = frames.consumeAsFlow()

    private fun CoroutineScope.read(p: UsbSerialPort) {
        val assembler = FrameAssembler {
            heardFrame = true
            frames.trySend(it)
        }
        val buf = ByteArray(maxOf(p.readEndpoint?.maxPacketSize ?: 64, 64) * 8)
        try {
            while (isActive) {
                val n = p.read(buf, READ_TIMEOUT_MS)
                if (n > 0) assembler.feed(buf, n)
            }
        } catch (e: Exception) {
            frames.close(e)
        }
    }

    private fun close() {
        runCatching { port?.close() }
        port = null
    }

    /** Splits the byte stream into frames `0x94 0xC3 <length high> <length low> <payload>`, header included. */
    internal class FrameAssembler(private val onFrame: (Frame) -> Unit) {
        private var state = 0
        private var length = 0
        private var payload = ByteArray(0)
        private var have = 0

        fun feed(bytes: ByteArray, count: Int = bytes.size) {
            for (i in 0 until count) feed(bytes[i])
        }

        private fun feed(b: Byte) {
            when (state) {
                0 -> if (b == WireFraming.MAGIC_0) state = 1
                1 -> state = when (b) {
                    WireFraming.MAGIC_1 -> 2
                    WireFraming.MAGIC_0 -> 1
                    else -> 0
                }
                2 -> {
                    length = (b.toInt() and 0xFF) shl 8
                    state = 3
                }
                3 -> {
                    length = length or (b.toInt() and 0xFF)
                    when {
                        length > WireFraming.MAX_PAYLOAD_SIZE -> state = 0
                        length == 0 -> {
                            emit()
                            state = 0
                        }
                        else -> {
                            payload = ByteArray(length)
                            have = 0
                            state = 4
                        }
                    }
                }
                else -> {
                    payload[have++] = b
                    if (have == length) {
                        emit()
                        state = 0
                    }
                }
            }
        }

        private fun emit() {
            val header = byteArrayOf(WireFraming.MAGIC_0, WireFraming.MAGIC_1, (length shr 8).toByte(), (length and 0xFF).toByte())
            onFrame(Frame((header + payload.copyOf(if (length == 0) 0 else length)).toByteString()))
        }
    }

    companion object {
        private const val BAUD = 115_200
        private const val WRITE_TIMEOUT_MS = 2_000
        private const val READ_TIMEOUT_MS = 200
        private val WAKE = ByteArray(4) { WireFraming.MAGIC_0 }

        /** The library's list plus the CH340K (0x1A86:0x7522) of newer boards, a CH34x without CDC-ACM. */
        private val prober by lazy {
            UsbSerialProber(UsbSerialProber.getDefaultProbeTable().addProduct(UsbFamily.QINHENG, 0x7522, Ch34xSerialDriver::class.java))
        }

        /**
         * Driver for a plugged-in device: known bridges via the library's list, otherwise CDC-ACM (native USB
         * of nRF52, ESP32-S3, RP2040, CH9102/CH343), as Meshtastic-Android does for its devices.
         */
        fun driverFor(device: android.hardware.usb.UsbDevice): UsbSerialDriver? =
            prober.probeDevice(device)
                ?: CdcAcmSerialDriver(device).takeIf { it.ports.isNotEmpty() }
    }
}
