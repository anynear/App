package app.anynear.mesh

import android.hardware.usb.UsbManager
import app.anynear.AndroidPlatform
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.RadioTransport

/** The first radio on the USB port, with permission asked if needed (ADR 0094 §4). */
actual suspend fun createUsbTransport(device: PairedDevice): RadioTransport {
    val context = AndroidPlatform.context ?: throw MeshtasticException.Transport("kein Kontext", null)
    val driver = AndroidUsbRescue(context).serialDriver() ?: throw MeshtasticException.Transport("kein Funkgerät am USB-Kabel", null)
    return UsbSerialTransport(context.getSystemService(UsbManager::class.java), driver)
}
