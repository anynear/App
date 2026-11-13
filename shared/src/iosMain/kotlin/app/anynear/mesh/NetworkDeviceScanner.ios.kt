package app.anynear.mesh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.darwin.NSObject

// Needs NSLocalNetworkUsageDescription and NSBonjourServices (_meshtastic._tcp) in Info.plist.
actual fun scanNetworkDevices(): Flow<FoundDevice> = callbackFlow {
    val delegate = BonjourDelegate { trySend(it) }
    val browser = NSNetServiceBrowser()
    browser.delegate = delegate
    browser.searchForServicesOfType("$MESHTASTIC_SERVICE_TYPE.", inDomain = "local.")
    awaitClose {
        browser.stop()
        delegate.stopAll()
    }
}.flowOn(Dispatchers.Main) // Bonjour callbacks need the run loop of the main thread.

private class BonjourDelegate(
    private val onFound: (FoundDevice) -> Unit,
) : NSObject(), NSNetServiceBrowserDelegateProtocol, NSNetServiceDelegateProtocol {
    // Strong references, otherwise the service is released before resolving.
    private val services = mutableListOf<NSNetService>()

    override fun netServiceBrowser(browser: NSNetServiceBrowser, didFindService: NSNetService, moreComing: Boolean) {
        services += didFindService
        didFindService.delegate = this
        didFindService.resolveWithTimeout(5.0)
    }

    override fun netServiceDidResolveAddress(sender: NSNetService) {
        val host = sender.hostName?.trimEnd('.') ?: return
        val port = sender.port.toInt().takeIf { it > 0 } ?: NetworkTransport.DEFAULT_PORT
        val address = NetworkTransport.parse("$host:$port") ?: return
        val device = NetworkTransport.deviceFor(address, name = NetworkTransport.radioName(sender.txtEntries()))
        onFound(FoundDevice(device.identifier, device.name, rssi = null, connection = FoundDevice.Connection.NETWORK))
    }

    @Suppress("UNCHECKED_CAST")
    private fun NSNetService.txtEntries(): Map<String, String> {
        val data = TXTRecordData() ?: return emptyMap()
        val dict = NSNetService.dictionaryFromTXTRecordData(data) as Map<Any?, Any?>
        return dict.entries.mapNotNull { (k, v) ->
            val value = (v as? NSData)?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding) as String? }
            (k as? String)?.let { key -> value?.let { key to it } }
        }.toMap()
    }

    fun stopAll() {
        services.forEach { it.stop() }
        services.clear()
    }
}
