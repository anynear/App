package app.anynear

import app.anynear.mesh.BluetoothBonding
import kotlin.test.Test
import kotlin.test.assertEquals

class BluetoothBondingTest {
    @Test
    fun failuresCountUntilPairingWorks() {
        BluetoothBonding.succeeded()
        BluetoothBonding.failed()
        BluetoothBonding.failed()
        assertEquals(2, BluetoothBonding.failures.value)
        BluetoothBonding.succeeded()
        assertEquals(0, BluetoothBonding.failures.value)
    }

    @Test
    fun waitingFollowsPairing() {
        BluetoothBonding.setWaiting(true)
        assertEquals(true, BluetoothBonding.waiting.value)
        BluetoothBonding.setWaiting(false)
        assertEquals(false, BluetoothBonding.waiting.value)
    }
}
