package app.anynear.app

import platform.Network.nw_interface_type_wifi
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_uses_interface_type
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState
import platform.darwin.dispatch_get_main_queue
import kotlin.concurrent.Volatile

/** Below 20 % battery without charger the search pauses (ADR 0071 item 5); Wi-Fi means: net via Wi-Fi, not costly. */
internal actual fun platformFinderConditions(): FinderConditions = IosFinderConditions

private object IosFinderConditions : FinderConditions {
    @Volatile
    private var wifi = false

    init {
        // Observe right at the start so that the first question already has the answer.
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path ->
            wifi = nw_path_uses_interface_type(path, nw_interface_type_wifi) && !nw_path_is_expensive(path)
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }

    override fun batteryLow(): Boolean {
        val device = UIDevice.currentDevice
        device.batteryMonitoringEnabled = true
        val level = device.batteryLevel
        if (level < 0f) return false
        val charging = device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
            device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateFull
        return level < 0.2f && !charging
    }

    override fun onWifi(): Boolean = wifi

    override fun charging(): Boolean {
        val device = UIDevice.currentDevice
        device.batteryMonitoringEnabled = true
        return device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
            device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateFull
    }
}
