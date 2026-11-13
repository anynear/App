package app.anynear.mesh

import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.RadioTransport

/** iOS cannot talk to a radio over USB (ADR 0094). */
actual suspend fun createUsbTransport(device: PairedDevice): RadioTransport =
    throw MeshtasticException.Transport("USB gibt es auf iOS nicht", null)
