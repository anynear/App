package app.anynear

import app.anynear.mesh.Esp32OtaProtocol
import app.anynear.mesh.FirmwareInstallResult
import app.anynear.mesh.OtaChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

/** Upload protocol of the MeshtasticOTA loader (ADR 0056) against a replica loader. */
class Esp32OtaProtocolTest {
    private val image = ByteArray(2500) { it.toByte() }

    /** Answers the header line with [header], every chunk with ACK (the last: [last]), at the end with [final]. */
    private class FakeLoader(
        override val maxChunk: Int,
        private val ackPerChunk: Boolean,
        private val header: List<String> = listOf("ERASING", "OK"),
        private val last: String = "OK",
        private val final: List<String> = listOf("OK"),
    ) : OtaChannel {
        val written = mutableListOf<ByteArray>()
        private val replies = ArrayDeque<String>()
        private var size = 0
        private var received = 0

        override suspend fun write(bytes: ByteArray) {
            written += bytes
            if (written.size == 1) {
                size = bytes.decodeToString().trim().split(' ')[1].toInt()
                replies += header
                return
            }
            received += bytes.size
            if (ackPerChunk) replies += if (received == size) last else "ACK"
            if (!ackPerChunk && received == size) replies += final
        }

        override suspend fun readLine(timeout: Duration): String? = replies.removeFirstOrNull()
    }

    @Test
    fun bluetoothWithAckPerChunk() = runTest {
        val loader = FakeLoader(maxChunk = 512, ackPerChunk = true)
        val progress = mutableListOf<Float>()
        val result = Esp32OtaProtocol.run(loader, image, "AB".repeat(32), ackPerChunk = true) { progress += it }
        assertEquals(FirmwareInstallResult.Written, result)
        assertEquals("OTA 2500 ${"ab".repeat(32)}\n", loader.written.first().decodeToString())
        assertEquals(image.toList(), loader.written.drop(1).flatMap { it.toList() })
        assertEquals(listOf(0f, 512f / 2500, 1024f / 2500, 1536f / 2500, 2048f / 2500, 1f), progress)
    }

    @Test
    fun wifiWithoutAcks() = runTest {
        val loader = FakeLoader(maxChunk = 1024, ackPerChunk = false, final = listOf("ACK", "OK"))
        assertEquals(FirmwareInstallResult.Written, Esp32OtaProtocol.run(loader, image, "ab".repeat(32), ackPerChunk = false) {})
        assertEquals(4, loader.written.size)
    }

    @Test
    fun hashRejectedBeforeWriting() = runTest {
        val loader = FakeLoader(maxChunk = 512, ackPerChunk = true, header = listOf("ERR Hash Rejected"))
        assertEquals(FirmwareInstallResult.Failed(wroteAnything = false), Esp32OtaProtocol.run(loader, image, "ab".repeat(32), ackPerChunk = true) {})
        assertEquals(1, loader.written.size, "nichts übertragen")
    }

    @Test
    fun hashMismatchAfterWriting() = runTest {
        val loader = FakeLoader(maxChunk = 1024, ackPerChunk = false, final = listOf("ERR Hash Mismatch"))
        val result = Esp32OtaProtocol.run(loader, image, "ab".repeat(32), ackPerChunk = false) {}
        assertEquals(FirmwareInstallResult.Failed(wroteAnything = true), result)
    }

    @Test
    fun silenceIsNoSuccess() = runTest {
        val loader = FakeLoader(maxChunk = 1024, ackPerChunk = false, final = emptyList())
        assertTrue(Esp32OtaProtocol.run(loader, image, "ab".repeat(32), ackPerChunk = false) {} is FirmwareInstallResult.Failed)
    }
}
