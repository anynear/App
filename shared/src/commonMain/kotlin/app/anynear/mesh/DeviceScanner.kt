package app.anynear.mesh

import com.juul.kable.Scanner
import app.anynear.i18n.texts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.meshtastic.sdk.transport.ble.BleConstants

/** Searches for Meshtastic radios in Bluetooth range (filter on the Meshtastic service UUID). */
class DeviceScanner {
    fun scanBluetooth(): Flow<FoundDevice> =
        Scanner {
            filters { match { services = listOf(BleConstants.MESH_SERVICE_UUID) } }
        }.advertisements.map { ad ->
            FoundDevice(
                identifier = ad.identifier.toString(),
                name = ad.name ?: texts.status.bluetoothRadioFallbackName,
                rssi = ad.rssi,
                connection = FoundDevice.Connection.BLUETOOTH,
            )
        }
}
