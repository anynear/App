package app.anynear.mesh

import org.meshtastic.sdk.RadioTransport

/**
 * A radio used over the USB cable as a normal connection (ADR 0094 §4, Android). Identifier `usb:<node number in hex>`:
 * USB devices get a new name with every plugging in, the node number stays.
 */
object UsbDevices {
    const val PREFIX = "usb:"

    fun isUsbDevice(device: PairedDevice): Boolean = device.identifier.startsWith(PREFIX)

    fun deviceFor(nodeNum: Int, name: String): PairedDevice = PairedDevice(PREFIX + nodeNum.toUInt().toString(16), name)
}

/** Connection to the radio on the USB cable; fails while none is plugged in (the link then tries again). */
expect suspend fun createUsbTransport(device: PairedDevice): RadioTransport
