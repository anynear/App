package app.anynear.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How often pairing with the radio has most recently failed in a row (Android, before connecting). Usually
 * the only thing that helps then is to remove the radio in the Bluetooth settings and pair it again, for instance after
 * it has received new software.
 */
object BluetoothBonding {
    private val _failures = MutableStateFlow(0)
    val failures: StateFlow<Int> = _failures.asStateFlow()

    private val _waiting = MutableStateFlow(false)

    /**
     * Pairing has been started and waits for the PIN (Android). The phone asks for the number from the radio's
     * display; some phones only put the request into the notifications, so the app shows a way to the Bluetooth
     * settings meanwhile.
     */
    val waiting: StateFlow<Boolean> = _waiting.asStateFlow()

    internal fun setWaiting(value: Boolean) {
        _waiting.value = value
    }

    internal fun failed() {
        _failures.value++
    }

    internal fun succeeded() {
        _failures.value = 0
    }
}
