package app.anynear.mesh

import app.anynear.i18n.texts
import org.meshtastic.sdk.RadioTransport
import org.meshtastic.sdk.transport.tcp.TcpTransport

/**
 * Connection to a radio with Wi-Fi switched on via the Meshtastic TCP interface.
 *
 * Important for context: with Meshtastic on ESP32, enabled Wi-Fi switches Bluetooth off,
 * and the connection needs a working home network (router with power). For the emergency,
 * Bluetooth remains the more reliable path.
 */
object NetworkTransport {
    const val DEFAULT_PORT = 4403
    private const val PREFIX = "tcp:"

    data class Address(val host: String, val port: Int)

    /**
     * Reads a manually entered address in a fault-tolerant way:
     * "192.168.1.50", "meshtastic.local", "http://192.168.1.50/", "192.168.1.50:4403".
     * @return `null` if the input is not a sensible address.
     */
    fun parse(input: String): Address? {
        var text = input.trim().lowercase()
        text = text.removePrefix("http://").removePrefix("https://").removePrefix("tcp://").trimEnd('/')
        if (text.isEmpty() || text.any { it.isWhitespace() || it == '/' }) return null

        val host: String
        val port: Int
        if (text.count { it == ':' } == 1) {
            host = text.substringBefore(':')
            port = text.substringAfter(':').toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        } else if (text.contains(':')) {
            return null // IPv6 not supported for now
        } else {
            host = text
            port = DEFAULT_PORT
        }
        return if (isValidHost(host)) Address(host, port) else null
    }

    private fun isValidHost(host: String): Boolean {
        val parts = host.split('.')
        if (parts.all { p -> p.isNotEmpty() && p.all(Char::isDigit) }) {
            return parts.size == 4 && parts.all { it.length <= 3 && it.toInt() in 0..255 }
        }
        return host.length <= 253 && parts.all { label ->
            label.isNotEmpty() && label.length <= 63 && label.first() != '-' && label.last() != '-' &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }

    /**
     * Name of a radio found on Wi-Fi from the mDNS TXT entries. The mDNS service name is just
     * "Meshtastic" for all devices. But the firmware announces `shortname` and `id` (e.g. "!db517840").
     * From that comes the same name under which the device appears via Bluetooth ("k3ed_7840"),
     * so the same device can be recognised in both searches.
     */
    fun radioName(txt: Map<String, String>): String? {
        val short = txt["shortname"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val suffix = txt["id"]?.trim()?.removePrefix("!")?.takeIf { it.length >= 4 }?.takeLast(4)?.lowercase()
        return if (suffix != null) "${short}_$suffix" else short
    }

    fun deviceFor(address: Address, name: String? = null): PairedDevice = PairedDevice(
        identifier = "$PREFIX${address.host}:${address.port}",
        name = name ?: texts.status.wifiRadioName(address.host),
    )

    fun isNetworkDevice(device: PairedDevice): Boolean = device.identifier.startsWith(PREFIX)

    fun addressOf(device: PairedDevice): Address? =
        if (isNetworkDevice(device)) parse(device.identifier.removePrefix(PREFIX)) else null

    fun create(device: PairedDevice): RadioTransport {
        val address = checkNotNull(addressOf(device)) { "Keine gültige Netzwerkadresse: ${device.identifier}" }
        return TcpTransport(host = address.host, port = address.port)
    }
}

/** Chooses the transport matching the assigned device: Wi-Fi, USB cable or Bluetooth. */
suspend fun createTransport(device: PairedDevice): RadioTransport = when {
    NetworkTransport.isNetworkDevice(device) -> NetworkTransport.create(device)
    UsbDevices.isUsbDevice(device) -> createUsbTransport(device)
    else -> createBleTransport(device)
}
