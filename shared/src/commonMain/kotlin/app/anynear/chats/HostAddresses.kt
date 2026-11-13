package app.anynear.chats

/**
 * Addresses under which [host] is reachable (IPv4 4 bytes, IPv6 16 bytes), from the name service of the device. Empty
 * if the name cannot be resolved.
 */
internal expect suspend fun resolveHost(host: String): List<ByteArray>

/**
 * May a preview of [host] be loaded (ADR 0069)? Only if the name is resolved and none of its addresses
 * is in the own network (router, printer, NAS, the device itself).
 */
internal suspend fun isPublicHost(host: String): Boolean {
    val addresses = resolveHost(host)
    return addresses.isNotEmpty() && addresses.none(::isPrivateAddress)
}

/** Address in the own network or otherwise not reachable on the internet. */
internal fun isPrivateAddress(a: ByteArray): Boolean {
    fun b(i: Int) = a[i].toInt() and 0xFF
    return when (a.size) {
        4 -> b(0) == 0 || b(0) == 10 || b(0) == 127 ||
            (b(0) == 100 && b(1) in 64..127) || // shared (CGNAT)
            (b(0) == 169 && b(1) == 254) ||
            (b(0) == 172 && b(1) in 16..31) ||
            (b(0) == 192 && b(1) == 168) ||
            (b(0) == 192 && b(1) == 0 && b(2) == 0) ||
            b(0) >= 224 // multicast and reserved
        16 -> {
            val mapped = (0 until 10).all { b(it) == 0 } && b(10) == 0xFF && b(11) == 0xFF
            when {
                mapped -> isPrivateAddress(a.copyOfRange(12, 16))
                (0 until 15).all { b(it) == 0 } -> true // :: and ::1
                b(0) and 0xFE == 0xFC -> true // fc00::/7, own network
                b(0) == 0xFE && b(1) and 0xC0 == 0x80 -> true // fe80::/10, link
                b(0) == 0xFF -> true // Multicast
                else -> false
            }
        }
        else -> true
    }
}
