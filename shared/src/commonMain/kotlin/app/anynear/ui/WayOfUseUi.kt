package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.app.AppController.WayChange
import app.anynear.i18n.texts
import app.anynear.mesh.UsbDevices

/**
 * „So nutzt du es“ on the page of the assigned radio (ADR 0094 §3): unterwegs (Bluetooth), stationär im WLAN or per
 * USB-Kabel, and while it is connected the switch between Bluetooth and Wi-Fi without cable.
 */
@Composable
internal fun WayOfUseSection(controller: AppController) {
    val paired by controller.pairedDevice.collectAsState()
    val canSwitch by controller.canSwitchWay.collectAsState()
    val device = paired ?: return
    val s = texts.settings
    val inWifi = controller.radioInWifi
    val usb = UsbDevices.isUsbDevice(device)
    var askWifi by rememberSaveable { mutableStateOf(false) }
    var askBluetooth by rememberSaveable { mutableStateOf(false) }

    AppSection(s.wayTitle, footer = if (canSwitch && !inWifi) s.wayPowerNote else null) {
        AppRow(
            title = when {
                usb -> s.wayUsb
                inWifi -> s.wayWifi
                else -> s.wayBluetooth
            },
            icon = AppIcons.Funkgeraet,
        )
        if (canSwitch) {
            if (inWifi) {
                AppRow(title = texts.radioSoftware.usbSwitchToBluetooth, icon = AppIcons.Funk, chevron = true, onClick = { askBluetooth = true })
            } else {
                AppRow(title = texts.radioSoftware.usbSwitchToWifi, icon = AppIcons.Internet, chevron = true, onClick = { askWifi = true })
            }
        }
    }

    if (askWifi) WifiDialog(onDismiss = { askWifi = false }) { ssid, password ->
        askWifi = false
        controller.switchWay(ssid, password)
    }
    if (askBluetooth) {
        AppDialog(
            onDismissRequest = { askBluetooth = false },
            title = { Text(s.wayBluetoothTitle) },
            text = { Text(s.wayBluetoothText) },
            confirmButton = {
                AppButton(texts.radioSoftware.usbSwitchToBluetooth, onClick = {
                    askBluetooth = false
                    controller.switchWay(null, null)
                })
            },
            dismissButton = { AppButton(texts.common.cancel, { askBluetooth = false }, kind = ButtonKind.Quiet) },
        )
    }
    WayChangeDialog(controller)
}

@Composable
private fun WifiDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    val s = texts.settings
    val t = texts.radioSoftware
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.usbSwitchToWifi) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t.usbWifiText)
                OutlinedTextField(value = ssid, onValueChange = { ssid = it }, singleLine = true, label = { Text(t.usbWifiName) }, modifier = Modifier.fillMaxWidth())
                PasswordField(value = password, onValueChange = { password = it }, label = t.usbWifiPassword, modifier = Modifier.fillMaxWidth())
                // Honest: with a wrong password the radio is out of reach until the cable resets it.
                Text(s.wayWifiWarning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { AppButton(t.usbWifiConnect, onClick = { onConfirm(ssid, password) }, enabled = ssid.isNotBlank()) },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/** Progress and result of switching, also after leaving the page (the dialog stays until „OK“). */
@Composable
private fun WayChangeDialog(controller: AppController) {
    val change by controller.wayChange.collectAsState()
    val current = change ?: return
    val s = texts.settings
    val working = current is WayChange.Working
    if (working) KeepScreenOn()
    AppDialog(
        onDismissRequest = { if (!working) controller.dismissWayChange() },
        title = { Text(s.wayTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (current) {
                    is WayChange.Working -> {
                        Busy(if (current.toWifi) s.wayWorkingWifi else s.wayWorkingBluetooth)
                        if (!current.toWifi) Text(texts.radioSoftware.usbPairingHint, style = MaterialTheme.typography.bodySmall)
                    }
                    is WayChange.Done -> Text(if (current.toWifi) s.wayDoneWifi(current.name) else s.wayDoneBluetooth(current.name))
                    is WayChange.NotSeen -> {
                        Text(if (current.toWifi) s.wayNotSeenWifi else s.wayNotSeenBluetooth)
                        // Without Bluetooth only the cable reaches the radio again: offer it right here (FUT 14.11.2026).
                        if (current.toWifi && controller.usbRescue != null) {
                            AppButton(texts.radioSoftware.usbConnectRow, onClick = {
                                controller.dismissWayChange()
                                controller.openUsbPlugged()
                            }, kind = ButtonKind.Secondary, icon = AppIcons.Funkgeraet, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    WayChange.Failed -> Text(s.wayFailed)
                }
            }
        },
        confirmButton = { if (!working) AppButton(texts.common.ok, onClick = controller::dismissWayChange) },
    )
}
