package app.anynear.mesh

/** iOS: no way yet (ADR 0056, Android first). */
internal actual fun platformFirmwareFlasher(log: (String) -> Unit): FirmwareFlasher? = null
