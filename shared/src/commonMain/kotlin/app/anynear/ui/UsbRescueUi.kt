package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import app.anynear.AppConfig
import app.anynear.app.AppController
import app.anynear.app.UsbRescueFlow
import app.anynear.app.UsbRescueState
import app.anynear.app.UsbRescueStep
import app.anynear.app.UsbConnection
import app.anynear.app.UsbPurpose
import app.anynear.app.usagePlatform
import app.anynear.mesh.LinkState
import app.anynear.i18n.texts
import kotlinx.coroutines.launch

/** Under „Erweitert“: rescue via USB cable (ADR 0056), only where the platform supports USB. */
@Composable
internal fun UsbRescueRow(controller: AppController) {
    if (controller.usbRescue == null) return
    AppRow(
        title = texts.radioSoftware.usbRescue,
        subtitle = texts.radioSoftware.usbRescueHint,
        icon = AppIcons.Funkgeraet,
        chevron = true,
        onClick = controller::openUsbRescue,
    )
}

/** iPhone: no USB access, so where Android offers the cable, the way to the webapp on a computer (ADR 0095 §5). */
internal fun AppController.offersUsbOnTheWeb(): Boolean = usbRescue == null && usagePlatform == "ios"

/** Row on the radios page instead of „Per USB-Kabel verbinden“ (iPhone). */
@Composable
internal fun UsbWebRow(controller: AppController) {
    if (!controller.offersUsbOnTheWeb()) return
    val uri = LocalUriHandler.current
    AppRow(
        title = texts.radioSoftware.usbWebTitle,
        subtitle = texts.radioSoftware.usbWebHint,
        icon = AppIcons.Funkgeraet,
        chevron = true,
        onClick = { runCatching { uri.openUri(AppConfig.WEB_RADIO_SETUP_URL) } },
    )
}

/** Short note with a button to the webapp, during the first setup (iPhone). */
@Composable
internal fun UsbWebHint(controller: AppController) {
    if (!controller.offersUsbOnTheWeb()) return
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(texts.radioSoftware.usbWebTitle, style = MaterialTheme.typography.titleSmall)
        Text(texts.radioSoftware.usbWebHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppButton(
            texts.radioSoftware.usbWebOpen,
            onClick = { runCatching { uri.openUri(AppConfig.WEB_RADIO_SETUP_URL) } },
            kind = ButtonKind.Quiet,
            icon = AppIcons.Link,
        )
    }
}

/** The rescue dialog at app level: opened from „Erweitert“ or from the hint on pairing problems. */
@Composable
internal fun UsbRescueHost(controller: AppController) {
    val rescue = controller.usbRescue ?: return
    val open by controller.usbRescueOpen.collectAsState()
    if (open) UsbRescueDialog(controller, rescue, controller::closeUsbRescue)
}

@Composable
private fun UsbRescueDialog(controller: AppController, rescue: UsbRescueFlow, onClose: () -> Unit) {
    val state by rescue.state.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    val pickDrive = rememberUf2DrivePicker { drive -> scope.launch { rescue.write(drive) } }
    val t = texts.radioSoftware
    val uri = LocalUriHandler.current
    // Joining the Wi-Fi is a wait like installing: the dialog stays until the answer is there (FUT 14.11.2026).
    val busy = state is UsbRescueState.Searching || state is UsbRescueState.Working || state is UsbRescueState.Installing ||
        state is UsbRescueState.WifiJoining
    if (busy || state is UsbRescueState.Pairing) KeepScreenOn()
    val link by controller.linkState.collectAsState()

    AppDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(t.usbTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val s = state) {
                    // Plugging in, setting up, installing, way of use, connecting (ADR 0094): own file.
                    is UsbRescueState.Choose, is UsbRescueState.PickRadio, is UsbRescueState.Installing,
                    is UsbRescueState.ChooseMode, is UsbRescueState.WifiCredentials, is UsbRescueState.WifiJoining, is UsbRescueState.Pairing,
                    is UsbRescueState.Paired, is UsbRescueState.NotSeen,
                    -> UsbSetupContent(controller, rescue, s, link == LinkState.Connected, scope)
                    UsbRescueState.Start -> Text(t.usbRescueIntro)
                    UsbRescueState.Searching -> Busy(t.usbSearching)
                    UsbRescueState.NotFound -> Text(t.usbNotFound)
                    UsbRescueState.NoUsbDevice -> {
                        Text(t.usbNoDevice)
                        Text(t.usbNoDeviceHint, style = MaterialTheme.typography.bodySmall)
                    }
                    is UsbRescueState.NoSoftware -> {
                        // An ESP32 whose loader stayed silent: first the BOOT button, the computer only after that.
                        if (s.holdBoot) Text(t.usbHoldBoot)
                        Text(t.usbNoSoftware, style = if (s.holdBoot) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
                        AppButton(
                            t.usbOpenFlasher,
                            onClick = { runCatching { uri.openUri(AppConfig.MESHTASTIC_FLASHER_URL) } },
                            kind = ButtonKind.Quiet,
                            icon = AppIcons.Link,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is UsbRescueState.Found -> {
                        Text(t.usbFound(s.radio.name ?: s.radio.firmware?.hwModel.orEmpty(), s.radio.firmware?.version))
                        // First the gentle way: a radio on Wi-Fi only needs Bluetooth back, not a reset.
                        if (s.radio.bluetoothOff) {
                            Text(t.usbBluetoothOff)
                            AppButton(t.usbEnableBluetooth, onClick = { scope.launch { rescue.enableBluetooth(s.radio) } }, kind = ButtonKind.Main, modifier = Modifier.fillMaxWidth())
                        } else {
                            Text(t.usbBluetoothOn, style = MaterialTheme.typography.bodySmall)
                        }
                        // Change the way of use (on the move / stationary) without a reset (ADR 0094 §3).
                        AppButton(t.usbChangeMode, onClick = { rescue.changeMode(s.radio) }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
                        // ESP32: install Meshtastic anew over the ROM loader, model known from the radio.
                        s.reinstall?.let { install ->
                            AppButton(t.usbReinstallEsp, onClick = { scope.launch { rescue.install(install) } }, kind = ButtonKind.Quiet, modifier = Modifier.fillMaxWidth())
                        }
                        AppButton(t.usbFactoryReset, onClick = { confirmReset = true }, kind = ButtonKind.Danger, modifier = Modifier.fillMaxWidth())
                        if (s.uf2) {
                            AppButton(t.usbReinstall, onClick = { scope.launch { rescue.reinstall(s.radio) } }, kind = ButtonKind.Quiet, modifier = Modifier.fillMaxWidth())
                        }
                        if (s.bootloader) {
                            AppButton(t.usbUpdateBootloader, onClick = { scope.launch { rescue.updateBootloader(s.radio) } }, kind = ButtonKind.Quiet, modifier = Modifier.fillMaxWidth())
                            Text(t.usbUpdateBootloaderHint, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is UsbRescueState.Working -> Busy(s.step.text())
                    is UsbRescueState.PickDrive -> {
                        Text(t.usbPickDrive)
                        Text(t.usbDoubleTapReset, style = MaterialTheme.typography.bodySmall)
                    }
                    is UsbRescueState.Done -> Text(
                        when {
                            s.step == UsbRescueStep.BLUETOOTH -> t.usbBluetoothDone
                            s.step == UsbRescueStep.UPDATE_BOOTLOADER -> t.usbBootloaderUpdated
                            s.step != UsbRescueStep.RESET -> t.usbWriteDone
                            s.bluetoothName != null -> t.usbResetDoneNamed(s.bluetoothName)
                            else -> t.usbResetDone
                        },
                    )
                    is UsbRescueState.Failed -> Text(s.step.failure())
                }
                if (rescue.purpose == UsbPurpose.RESCUE && (state == UsbRescueState.Start || state == UsbRescueState.NotFound || state == UsbRescueState.NoUsbDevice)) {
                    Text(t.usbStuckIntro)
                    AppButton(t.usbStuck, onClick = { scope.launch { rescue.stuckInBootloader() } }, kind = ButtonKind.Quiet, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                UsbRescueState.Start, UsbRescueState.NotFound, UsbRescueState.NoUsbDevice, is UsbRescueState.NoSoftware, is UsbRescueState.Failed ->
                    AppButton(t.usbSearch, onClick = { scope.launch { rescue.search() } })
                is UsbRescueState.PickDrive -> AppButton(t.usbChooseDrive, onClick = pickDrive)
                is UsbRescueState.NotSeen -> AppButton(t.usbTryAgain, onClick = { rescue.inBackground { connect(s.radio, s.via) } })
                is UsbRescueState.Paired -> when {
                    // Over Wi-Fi and cable the app is connected once it says so; only Bluetooth still waits for the number.
                    link == LinkState.Connected || s.via != UsbConnection.BLUETOOTH -> AppButton(texts.common.ok, onClick = onClose)
                    s.via == UsbConnection.BLUETOOTH && controller.canOpenBluetoothSettings ->
                        AppButton(texts.settings.openBluetoothSettings, onClick = controller::openBluetoothSettings)
                    else -> Unit
                }
                is UsbRescueState.Done -> AppButton(
                    if (s.step == UsbRescueStep.RESET && controller.canOpenBluetoothSettings) texts.settings.openBluetoothSettings else texts.common.ok,
                    onClick = {
                        if (s.step == UsbRescueStep.RESET && controller.canOpenBluetoothSettings) controller.openBluetoothSettings()
                        // The radio restarts with Bluetooth: search for it right away.
                        if (s.step == UsbRescueStep.BLUETOOTH) controller.startScan()
                        onClose()
                    },
                )
                else -> Unit
            }
        },
        dismissButton = {
            if (!busy) AppButton(texts.common.close, onClose, kind = ButtonKind.Quiet)
        },
    )

    if (confirmReset) {
        val found = state as? UsbRescueState.Found
        AppDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(t.usbFactoryResetTitle) },
            text = { Text(t.usbFactoryResetText) },
            confirmButton = {
                AppButton(t.usbFactoryReset, kind = ButtonKind.Danger, onClick = {
                    confirmReset = false
                    found?.let { scope.launch { rescue.factoryReset(it.radio) } }
                })
            },
            dismissButton = { AppButton(texts.common.cancel, { confirmReset = false }, kind = ButtonKind.Quiet) },
        )
    }
}

@Composable
internal fun Busy(text: String) {
    Text(text)
    LinearProgressIndicator(Modifier.fillMaxWidth())
}

private fun UsbRescueStep.text(): String = when (this) {
    UsbRescueStep.RESET -> texts.radioSoftware.usbResetting
    UsbRescueStep.BOOTLOADER -> texts.radioSoftware.usbEnteringBootloader
    UsbRescueStep.DOWNLOAD -> texts.radioSoftware.loading
    UsbRescueStep.WRITE -> texts.radioSoftware.usbWriting
    UsbRescueStep.UPDATE_BOOTLOADER -> texts.radioSoftware.usbUpdatingBootloader
    UsbRescueStep.BLUETOOTH -> texts.radioSoftware.usbEnablingBluetooth
    UsbRescueStep.INSTALL -> texts.radioSoftware.usbInstalling
}

private fun UsbRescueStep.failure(): String = when (this) {
    UsbRescueStep.RESET -> texts.radioSoftware.usbResetFailed
    UsbRescueStep.BOOTLOADER -> texts.radioSoftware.usbBootloaderFailed
    UsbRescueStep.DOWNLOAD -> texts.radioSoftware.usbDownloadFailed
    UsbRescueStep.WRITE -> texts.radioSoftware.usbWriteFailed
    UsbRescueStep.UPDATE_BOOTLOADER -> texts.radioSoftware.usbBootloaderUpdateFailed
    UsbRescueStep.BLUETOOTH -> texts.radioSoftware.usbBluetoothFailed
    UsbRescueStep.INSTALL -> texts.radioSoftware.usbInstallFailed
}
