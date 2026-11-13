package app.anynear

import app.anynear.mesh.UsbSerialTransport
import org.meshtastic.sdk.Frame
import kotlin.test.Test
import kotlin.test.assertEquals

/** Frame on the serial line as in the SDK: `0x94 0xC3 <length>` plus payload, header stays in the frame. */
class UsbSerialTransportTest {
    private fun frame(payload: ByteArray) = byteArrayOf(0x94.toByte(), 0xC3.toByte(), (payload.size shr 8).toByte(), payload.size.toByte()) + payload

    @Test
    fun splitsTheStreamIntoFrames() {
        val got = mutableListOf<Frame>()
        val assembler = UsbSerialTransport.FrameAssembler { got += it }
        val a = frame(byteArrayOf(1, 2, 3))
        val b = frame(ByteArray(300) { it.toByte() })
        // Noise before it (console output), frame spread over several reads.
        val stream = "INFO boot\n".encodeToByteArray() + a + b
        assembler.feed(stream.copyOfRange(0, 15))
        assembler.feed(stream.copyOfRange(15, stream.size))
        assertEquals(listOf(a.toList(), b.toList()), got.map { it.bytes.toByteArray().toList() })
    }

    @Test
    fun skipsOversizedLength() {
        val got = mutableListOf<Frame>()
        val assembler = UsbSerialTransport.FrameAssembler { got += it }
        assembler.feed(byteArrayOf(0x94.toByte(), 0xC3.toByte(), 0x7F, 0x7F) + frame(byteArrayOf(9)))
        assertEquals(listOf(frame(byteArrayOf(9)).toList()), got.map { it.bytes.toByteArray().toList() })
    }
}
