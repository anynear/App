package app.anynear.mesh

import org.meshtastic.sdk.RadioTransport

/**
 * Builds the BLE transport for an already assigned device, without the user having to search again.
 * Android: via the MAC address with `autoConnect`. iOS: via the remembered CoreBluetooth UUID.
 */
expect suspend fun createBleTransport(device: PairedDevice): RadioTransport
