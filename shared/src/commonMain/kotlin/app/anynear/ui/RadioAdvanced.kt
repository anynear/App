package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.mesh.DeviceName

/**
 * Content of „Erweitert“ on the radio page (ADR 0006/0007), collapsed there so that everyday use
 * stays plain. Change the name (with a suggestion from the node ID), signature over radio (ADR 0021) and
 * apply the settings of the app again.
 */
@Composable
internal fun RadioAdvanced(controller: AppController, connected: Boolean) {
    val own by controller.ownDevice.collectAsState()
    val suggestion by controller.deviceNameSuggestion.collectAsState()
    val takenNames by controller.takenDeviceNames.collectAsState()
    val running by controller.deviceBusy.collectAsState()
    val pendingName by controller.pendingDeviceName.collectAsState()
    // Without a connection the section stays open, field and buttons are disabled.
    val busy = running || !connected
    val current = DeviceName.suffixOf(own?.longName)
    var suffix by remember(current) { mutableStateOf(current ?: suggestion.orEmpty()) }
    var confirmReapply by remember { mutableStateOf(false) }
    val valid = DeviceName.isValidSuffix(suffix)
    val taken = DeviceName.isTaken(suffix, takenNames)

    SectionContent {
        // On the restart during a change the connection is gone briefly; that is intended.
        if (!connected && !running) {
            Text(
                texts.settings.editOnlyWhenConnected,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The device is just taking over a name and restarting: show the new one, not the old one.
        val applying = pendingName?.let(DeviceName::suffixOf)
        OutlinedTextField(
            value = applying ?: suffix,
            onValueChange = { suffix = DeviceNameLimit.accept(suffix, it) },
            label = { Text(texts.settings.radioName) },
            prefix = { Text(DeviceName.PREFIX) },
            singleLine = true,
            enabled = connected && !running,
            isError = connected && !running && !valid,
            supportingText = when {
                applying != null -> ({ Text(texts.settings.radioNameApplying) })
                running || !connected -> null
                !valid -> ({ Text(texts.settings.radioNameInvalid) })
                taken -> ({ Text(texts.settings.radioNameTaken) })
                else -> DeviceNameLimit.hint(suffix)?.let { { TextLimitHint(it) } }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                texts.settings.saveName,
                { controller.renameDevice(suffix) },
                enabled = valid && !busy && DeviceName.normalize(suffix) != current,
            )
            suggestion?.takeIf { it != suffix }?.let { proposed ->
                AppButton(texts.settings.suggestion, { suffix = proposed }, kind = ButtonKind.Secondary, enabled = !busy)
            }
        }
    }
    val relay by controller.relayForOthers.collectAsState()
    AppRow(
        title = texts.settings.relayForOthers,
        subtitle = if (relay) texts.settings.relayForOthersOn else texts.settings.relayForOthersOff,
        icon = AppIcons.Funk,
        trailing = { Switch(checked = relay, onCheckedChange = controller::setRelayForOthers) },
        onClick = { controller.setRelayForOthers(!relay) },
    )
    val charge by controller.chargeReminderOn.collectAsState()
    AppRow(
        title = texts.settings.chargeReminder,
        subtitle = if (charge) texts.settings.chargeReminderOn else texts.settings.chargeReminderOff,
        icon = AppIcons.Akku,
        trailing = { Switch(checked = charge, onCheckedChange = controller::setChargeReminder) },
        onClick = { controller.setChargeReminder(!charge) },
    )
    AppRow(
        title = texts.settings.reapplySettings,
        subtitle = texts.settings.reapplyHint,
        icon = AppIcons.Aktualisieren,
        enabled = !busy,
        onClick = { confirmReapply = true },
    )
    RadioSoftwareAdvanced(controller)
    UsbRescueRow(controller)

    if (confirmReapply) {
        AppDialog(
            onDismissRequest = { confirmReapply = false },
            title = { Text(texts.settings.reapplyTitle) },
            text = { Text(texts.settings.reapplyText) },
            confirmButton = {
                AppButton(texts.settings.reapply, onClick = {
                    confirmReapply = false
                    controller.reapplyRadioProfile()
                })
            },
            dismissButton = { AppButton(texts.common.cancel, { confirmReapply = false }, kind = ButtonKind.Quiet) },
        )
    }
}
