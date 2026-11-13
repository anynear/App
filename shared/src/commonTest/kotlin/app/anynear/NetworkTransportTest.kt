package app.anynear

import app.anynear.app.AppController
import app.anynear.mesh.FoundDevice
import app.anynear.mesh.FoundDevice.Connection
import app.anynear.mesh.NetworkTransport
import app.anynear.mesh.NetworkTransport.Address
import app.anynear.mesh.PairedDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkTransportTest {
    @Test
    fun acceptsTypicalHandwrittenAddresses() {
        assertEquals(Address("192.168.1.50", 4403), NetworkTransport.parse("192.168.1.50"))
        assertEquals(Address("192.168.1.50", 4403), NetworkTransport.parse("  http://192.168.1.50/ "))
        assertEquals(Address("192.168.1.50", 4500), NetworkTransport.parse("192.168.1.50:4500"))
        assertEquals(Address("meshtastic.local", 4403), NetworkTransport.parse("Meshtastic.local"))
    }

    @Test
    fun rejectsNonsense() {
        listOf("", "   ", "192.168.1", "192.168.1.300", "hallo welt", "192.168.1.50:99999", "a/b", "-bad-.local", "::1")
            .forEach { assertNull(NetworkTransport.parse(it), "sollte abgelehnt werden: '$it'") }
    }

    @Test
    fun networkDeviceRoundTrip() {
        val device = NetworkTransport.deviceFor(Address("192.168.1.50", 4403))
        assertTrue(NetworkTransport.isNetworkDevice(device))
        assertEquals(Address("192.168.1.50", 4403), NetworkTransport.addressOf(device))
        assertFalse(NetworkTransport.isNetworkDevice(PairedDevice("AA:BB:CC:DD:EE:FF", "BLE")))
    }

    @Test
    fun nearestBluetoothFirstThenNetwork() {
        val sorted = AppController.sortFound(
            listOf(
                FoundDevice("n", "Netz", rssi = null, connection = Connection.NETWORK),
                FoundDevice("far", "Fern", rssi = -90, connection = Connection.BLUETOOTH),
                FoundDevice("near", "Nah", rssi = -40, connection = Connection.BLUETOOTH),
            ),
        )
        assertEquals(listOf("near", "far", "n"), sorted.map { it.identifier })
    }

    @Test
    fun radioNameMatchesBluetoothName() {
        assertEquals("k3ed_7840", NetworkTransport.radioName(mapOf("shortname" to "k3ed", "id" to "!db517840")))
        assertEquals("k3ed", NetworkTransport.radioName(mapOf("shortname" to "k3ed")))
        assertNull(NetworkTransport.radioName(mapOf("id" to "!db517840")))
        assertNull(NetworkTransport.radioName(emptyMap()))
    }

    @Test
    fun sameRadioOverBothConnectionsAppearsOnceAsBluetooth() {
        val wifi = FoundDevice("tcp:192.168.1.50:4403", "k3ed_7840", rssi = null, connection = Connection.NETWORK)
        val ble = FoundDevice("AA:BB", "K3ED_7840", rssi = -50, connection = Connection.BLUETOOTH)

        assertEquals(listOf("AA:BB"), AppController.mergeFound(listOf(wifi), ble).map { it.identifier })
        assertEquals(listOf("AA:BB"), AppController.mergeFound(listOf(ble), wifi).map { it.identifier })

        val other = FoundDevice("tcp:192.168.1.51:4403", "ab12_0001", rssi = null, connection = Connection.NETWORK)
        assertEquals(listOf("AA:BB", "tcp:192.168.1.51:4403"), AppController.mergeFound(listOf(ble), other).map { it.identifier })
    }
}
