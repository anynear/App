package app.anynear.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import app.anynear.AndroidPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.sdk.RadioTransport
import org.meshtastic.sdk.transport.ble.BleTransport
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// autoConnect: Android connects as soon as the paired device is back in range,
// without needing a fresh advertisement scan (avoids GATT error 133).
actual suspend fun createBleTransport(device: PairedDevice): RadioTransport {
    ensureBonded(device.identifier)
    return BleTransport(address = device.identifier) { autoConnectIf { true } }
}

/**
 * Pairs before connecting if the radio is not paired, and waits until the PIN has been entered
 * (at most [BOND_WAIT]). Without this step Android only pairs on the first encrypted access; if
 * connecting fails before that, the “Kopplungsanfrage” disappears again before anyone can enter the PIN (for example
 * after new software on the radio). If pairing does not succeed, the app connects anyway; the next attempt asks
 * again.
 */
@SuppressLint("MissingPermission") // The connection only runs with the Bluetooth permission granted.
private suspend fun ensureBonded(address: String) {
    val context = AndroidPlatform.context ?: return
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
    val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
    try {
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            BluetoothBonding.succeeded()
            return
        }
        if (device.bondState != BluetoothDevice.BOND_BONDING) announceDiscovery(adapter)
        BluetoothBonding.setWaiting(true)
        val bonded = try {
            withTimeoutOrNull(BOND_WAIT) { awaitBond(context, device) } == true
        } finally {
            BluetoothBonding.setWaiting(false)
        }
        if (bonded) BluetoothBonding.succeeded() else BluetoothBonding.failed()
    } catch (e: SecurityException) {
        // Without the permission, connecting decides for itself; that then asks for the permission.
    }
}

/**
 * Android shows the PIN request as a window in front only if the phone was searching for Bluetooth devices
 * shortly before; otherwise often only as a notification that people overlook (first setup of a Heltec V3, 12.11.2026).
 * A short search right before pairing brings the window to the front. It is stopped again before pairing, because
 * a running search slows down pairing and connecting.
 */
@SuppressLint("MissingPermission")
private suspend fun announceDiscovery(adapter: BluetoothAdapter) {
    runCatching {
        if (!adapter.isDiscovering && adapter.startDiscovery()) {
            delay(DISCOVERY_ANNOUNCE)
            adapter.cancelDiscovery()
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun awaitBond(context: Context, device: BluetoothDevice) = suspendCancellableCoroutine { cont ->
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val changed = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
            if (changed.address != device.address) return
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            if (state == BluetoothDevice.BOND_BONDED || state == BluetoothDevice.BOND_NONE) {
                runCatching { context.unregisterReceiver(this) }
                if (cont.isActive) cont.resume(state == BluetoothDevice.BOND_BONDED)
            }
        }
    }
    ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
    cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
    // If pairing is already running, just wait for it; otherwise start one. If that fails right away, do not wait.
    if (device.bondState != BluetoothDevice.BOND_BONDING && !device.createBond()) {
        runCatching { context.unregisterReceiver(receiver) }
        if (cont.isActive) cont.resume(false)
    }
}

/** How long entering the PIN may take. */
private val BOND_WAIT = 2.minutes

/** How long the short search before pairing runs, enough for the system to note that it searched. */
private val DISCOVERY_ANNOUNCE = 1.seconds
