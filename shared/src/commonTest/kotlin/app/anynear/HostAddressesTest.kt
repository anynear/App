package app.anynear

import app.anynear.chats.LinkPreviews
import app.anynear.chats.isPrivateAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Preview only from hosts on the internet (ADR 0069): addresses in the own network are recognised. */
class HostAddressesTest {
    private fun v4(vararg b: Int) = ByteArray(4) { b[it].toByte() }

    private fun v6(vararg head: Int) = ByteArray(16) { if (it < head.size) head[it].toByte() else 0 }

    @Test
    fun privateIpv4() {
        listOf(v4(10, 0, 0, 1), v4(127, 0, 0, 1), v4(192, 168, 1, 1), v4(172, 16, 0, 1), v4(172, 31, 255, 1), v4(169, 254, 1, 1), v4(100, 64, 0, 1), v4(0, 0, 0, 0), v4(224, 0, 0, 1))
            .forEach { assertTrue(isPrivateAddress(it), it.joinToString(".") { b -> (b.toInt() and 0xFF).toString() }) }
        listOf(v4(93, 184, 216, 34), v4(172, 32, 0, 1), v4(100, 128, 0, 1), v4(8, 8, 8, 8)).forEach { assertFalse(isPrivateAddress(it)) }
    }

    @Test
    fun privateIpv6() {
        assertTrue(isPrivateAddress(ByteArray(16).also { it[15] = 1 })) // ::1
        assertTrue(isPrivateAddress(v6(0xFD, 0x12))) // fd12::
        assertTrue(isPrivateAddress(v6(0xFE, 0x80))) // fe80::
        assertTrue(isPrivateAddress(ByteArray(16) { i -> when (i) { 10, 11 -> 0xFF.toByte(); 12 -> 192.toByte(); 13 -> 168.toByte(); else -> 0 } }))
        assertFalse(isPrivateAddress(v6(0x20, 0x01, 0x0D, 0xB8, 0, 1)))
    }

    @Test
    fun lookupHostKeepsWww() {
        assertEquals("www.example.org", LinkPreviews.lookupHostOf("https://www.Example.org:8443/a"))
        assertEquals("example.org", LinkPreviews.hostOf("https://www.example.org/a"))
    }
}
