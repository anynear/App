package app.anynear.mesh

import kotlinx.coroutines.flow.Flow

/** Meshtastic devices announce themselves on Wi-Fi via mDNS/Bonjour under this service type. */
internal const val MESHTASTIC_SERVICE_TYPE = "_meshtastic._tcp"

/**
 * Finds Meshtastic radios on the same Wi-Fi (Android: NsdManager, iOS: Bonjour).
 * Returns [FoundDevice] with [FoundDevice.Connection.NETWORK].
 */
expect fun scanNetworkDevices(): Flow<FoundDevice>
