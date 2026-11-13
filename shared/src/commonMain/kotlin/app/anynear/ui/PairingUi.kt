package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts

/**
 * While pairing waits for the number from the radio's display: what to do, and on Android the direct way to the
 * Bluetooth settings in case the phone only put the request into the notifications.
 */
@Composable
internal fun PairingWaitingSection(controller: AppController) {
    val waiting by controller.bluetoothPairingWaiting.collectAsState()
    if (!waiting) return
    val settings = controller.canOpenBluetoothSettings
    AppSection(footer = if (settings) texts.settings.pairingWaitingNoWindow else null) {
        AppRow(title = texts.settings.pairingWaiting, subtitle = texts.settings.pairingWaitingText, icon = AppIcons.Funkgeraet)
        if (settings) {
            AppRow(title = texts.settings.openBluetoothSettings, icon = AppIcons.Funk, chevron = true, onClick = controller::openBluetoothSettings)
        }
    }
}

/** Row under „Braucht Hilfe“ on the radio page, as long as the pairing keeps failing. */
@Composable
internal fun PairingTroubleRow(controller: AppController) {
    AppRow(
        title = texts.settings.pairingFails,
        subtitle = texts.settings.pairingFailsText,
        icon = AppIcons.Funkgeraet,
        chevron = controller.canOpenBluetoothSettings,
        onClick = if (controller.canOpenBluetoothSettings) controller::openBluetoothSettings else null,
    )
}

/**
 * Pairing keeps failing (pairing loop): one hint per app start and level. Level 1 recommends removing the
 * radio in the Bluetooth settings and pairing it anew; level 2 (that did not help) the rescue via
 * USB cable, which opens directly.
 */
@Composable
internal fun PairingTroubleDialog(controller: AppController) {
    if (!controller.canOpenBluetoothSettings) return
    val level by controller.pairingHint.collectAsState()
    if (level == 0) return
    val rescue = controller.usbRescue != null
    val dismiss = controller::dismissPairingHint
    val openSettings = {
        dismiss()
        controller.openBluetoothSettings()
    }
    val openRescue = {
        dismiss()
        controller.openUsbRescue()
    }
    val stillFails = level >= 2 && rescue
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (stillFails) texts.settings.pairingStillFails else texts.settings.pairingFails) },
        // Second way in the content, not in the button bar: three stacked buttons do not fit there (hardware test 03.11.2026).
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (stillFails) texts.settings.pairingStillFailsText else texts.settings.pairingFailsText)
                when {
                    stillFails -> AppButton(texts.settings.openBluetoothSettings, openSettings, kind = ButtonKind.Quiet, modifier = Modifier.fillMaxWidth())
                    rescue -> AppButton(texts.radioSoftware.usbRescue, openRescue, kind = ButtonKind.Quiet, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (stillFails) {
                AppButton(texts.radioSoftware.usbRescue, onClick = openRescue)
            } else {
                AppButton(texts.settings.openBluetoothSettings, onClick = openSettings)
            }
        },
        dismissButton = { AppButton(texts.common.later, dismiss, kind = ButtonKind.Quiet) },
    )
}

/**
 * The radio on Wi-Fi is used by another phone or app and the app has stepped back: connect here (the other one is
 * disconnected) or leave it there. Comes up by itself; after „Dort lassen“ the radio page still offers „Hier verbinden“.
 */
@Composable
internal fun RadioInUseDialog(controller: AppController) {
    AlertDialog(
        onDismissRequest = controller::leaveRadioElsewhere,
        title = { Text(texts.settings.radioInUseTitle) },
        text = { Text(texts.settings.radioInUseText, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { AppButton(texts.settings.radioInUseConnectHere, controller::takeOverRadio) },
        dismissButton = { AppButton(texts.settings.radioInUseLeave, controller::leaveRadioElsewhere, kind = ButtonKind.Quiet) },
    )
}
