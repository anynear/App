package app.anynear.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import app.anynear.AndroidPlatform

/** Below 20 % battery without charging cable the search pauses (ADR 0071 item 5); Wi-Fi here means: unmetered. */
internal actual fun platformFinderConditions(): FinderConditions = object : FinderConditions {
    override fun batteryLow(): Boolean {
        val battery = AndroidPlatform.requireContext().getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return false
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level in 0 until LOW_BATTERY && !battery.isCharging
    }

    override fun onWifi(): Boolean {
        val connectivity = AndroidPlatform.requireContext().getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    override fun charging(): Boolean {
        val battery = AndroidPlatform.requireContext().getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return false
        return battery.isCharging
    }
}

private const val LOW_BATTERY = 20
