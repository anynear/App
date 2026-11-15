package app.anynear.mesh

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import app.anynear.AndroidPlatform
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
import java.util.concurrent.Executors

actual fun scanNetworkDevices(): Flow<FoundDevice> = callbackFlow {
    val nsd = AndroidPlatform.requireContext().getSystemService(Context.NSD_SERVICE) as NsdManager
    // Before Android 14, NsdManager resolves only one service at a time. A single-thread executor
    // queues the resolutions one after another.
    val resolver = Executors.newSingleThreadExecutor()

    fun emit(info: NsdServiceInfo) {
        val host = info.ipv4Address() ?: return
        val address = NetworkTransport.Address(host, info.port.takeIf { it > 0 } ?: NetworkTransport.DEFAULT_PORT)
        val txt = info.attributes.orEmpty().mapValues { (_, v) -> v?.decodeToString().orEmpty() }
        val device = NetworkTransport.deviceFor(address, name = NetworkTransport.radioName(txt))
        trySendBlocking(FoundDevice(device.identifier, device.name, rssi = null, connection = FoundDevice.Connection.NETWORK))
    }

    val discovery = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(service: NsdServiceInfo) {
            // A failure here runs on the executor's own thread: uncaught, it ends the whole app. Since the
            // suggestion search stops after the first radio, a second one still resolving is the normal case.
            runCatching { resolver.execute { runCatching { resolveBlocking(nsd, service)?.let(::emit) } } }
        }

        override fun onServiceLost(service: NsdServiceInfo) = Unit
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            close(IllegalStateException("WLAN-Suche fehlgeschlagen ($errorCode)"))
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
    }

    nsd.discoverServices(MESHTASTIC_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
    awaitClose {
        runCatching { nsd.stopServiceDiscovery(discovery) }
        resolver.shutdownNow()
    }
}

@Suppress("DEPRECATION") // resolveService is deprecated from API 34 on, but available on all versions.
private fun resolveBlocking(nsd: NsdManager, service: NsdServiceInfo): NsdServiceInfo? {
    val lock = Object()
    var result: NsdServiceInfo? = null
    var done = false
    nsd.resolveService(
        service,
        object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) = synchronized(lock) {
                result = info
                done = true
                lock.notifyAll()
            }

            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = synchronized(lock) {
                done = true
                lock.notifyAll()
            }
        },
    )
    synchronized(lock) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            try {
                lock.wait(remaining)
            } catch (e: InterruptedException) {
                // Search stopped (shutdownNow): give up on this one instead of ending the app.
                Thread.currentThread().interrupt()
                return null
            }
        }
    }
    return result
}

@Suppress("DEPRECATION")
private fun NsdServiceInfo.ipv4Address(): String? {
    val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) hostAddresses else listOfNotNull(host)
    return addresses.filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress
}
