package app.anynear

import app.anynear.data.PairedDeviceRepository
import app.anynear.mesh.PairedDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** „Meine Funkgeräte“ and „Trennen“. */
class PairedDevicesTest {
    private val a = PairedDevice("AA:BB", "Küche")
    private val b = PairedDevice("tcp:192.168.1.5:4403", "Garten")

    @Test
    fun remembersAllDevicesWithNamesNewestFirst() {
        val store = InMemoryStore()
        val repo = PairedDeviceRepository(store)
        repo.pair(a)
        repo.pair(b)

        assertEquals(listOf(b, a), repo.knownDevices.value)
        assertEquals(setOf(a.identifier, b.identifier), repo.known.value)
        // Still there after a restart.
        assertEquals(listOf(b, a), PairedDeviceRepository(store).knownDevices.value)
    }

    @Test
    fun forgetRemovesOneDeviceAndUnpairsIfCurrent() {
        val repo = PairedDeviceRepository(InMemoryStore())
        repo.pair(a)
        repo.pair(b)

        repo.forget(a.identifier)
        assertEquals(listOf(b), repo.knownDevices.value)
        assertEquals(b, repo.device.value)

        repo.forget(b.identifier)
        assertTrue(repo.knownDevices.value.isEmpty())
        assertNull(repo.device.value)
    }

    @Test
    fun oldStoreWithOnlyIdentifiersKeepsWorking() {
        val store = InMemoryStore()
        store.putString("paired_device.known", "AA:BB\nCC:DD")
        store.putString("paired_device.identifier", "CC:DD")
        store.putString("paired_device.name", "Auto")

        val repo = PairedDeviceRepository(store)
        assertEquals(listOf(PairedDevice("CC:DD", "Auto"), PairedDevice("AA:BB", "")), repo.knownDevices.value)
    }

    @Test
    fun disconnectKeepsDeviceAndSurvivesRestart() {
        val store = InMemoryStore()
        val repo = PairedDeviceRepository(store)
        repo.pair(a)
        repo.setDisconnected(true)

        val again = PairedDeviceRepository(store)
        assertEquals(a, again.device.value)
        assertTrue(again.disconnected.value)

        // Pairing again or switching reconnects.
        again.pair(b)
        assertFalse(again.disconnected.value)
    }

    @Test
    fun rememberedNameOfTheRadioStaysAndBeatsStandIns() {
        val store = InMemoryStore()
        val repo = PairedDeviceRepository(store, placeholderNames = setOf("Meshtastic-Funkgerät"))
        repo.pair(PairedDevice("AA:BB", ""))
        // Connected: the radio reports its own name.
        repo.rememberName("AA:BB", " anynear Birne ")
        assertEquals(PairedDevice("AA:BB", "anynear Birne"), repo.device.value)
        repo.pair(b)
        // Not connected any more, still with its name, also after a restart.
        assertEquals(PairedDevice("AA:BB", "anynear Birne"), PairedDeviceRepository(store).knownDevices.value.last())
        // Connecting again with an empty or stand-in name from the search keeps the remembered one.
        repo.pair(PairedDevice("AA:BB", "Meshtastic-Funkgerät"))
        assertEquals("anynear Birne", repo.device.value?.name)
        repo.pair(PairedDevice("AA:BB", ""))
        assertEquals("anynear Birne", repo.knownDevices.value.first().name)
        // Blank names change nothing.
        repo.rememberName("AA:BB", "  ")
        assertEquals("anynear Birne", repo.device.value?.name)
    }
}
