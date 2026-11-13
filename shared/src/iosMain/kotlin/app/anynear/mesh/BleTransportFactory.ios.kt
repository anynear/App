package app.anynear.mesh

import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.toIdentifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.meshtastic.sdk.RadioTransport
import org.meshtastic.sdk.transport.ble.BleConstants
import org.meshtastic.sdk.transport.ble.BleTransport
import kotlin.time.Duration.Companion.seconds

// iOS knows no MAC addresses. CoreBluetooth returns already known devices via their UUID
// (retrievePeripherals). If the device is unknown to the system (e.g. after a reinstall),
// it is briefly searched for specifically.
actual suspend fun createBleTransport(device: PairedDevice): RadioTransport {
    val identifier = device.identifier.toIdentifier()
    val peripheral = try {
        Peripheral(identifier)
    } catch (e: NoSuchElementException) {
        val ad = withTimeout(20.seconds) {
            Scanner { filters { match { services = listOf(BleConstants.MESH_SERVICE_UUID) } } }
                .advertisements
                .first { it.identifier == identifier }
        }
        Peripheral(ad)
    }
    return BleTransport(peripheral = peripheral, address = device.identifier)
}
