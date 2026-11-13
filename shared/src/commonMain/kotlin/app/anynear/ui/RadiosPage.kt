package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.mesh.DeviceSetupCheck
import app.anynear.mesh.FoundDevice
import app.anynear.mesh.LinkState
import app.anynear.mesh.NetworkTransport
import app.anynear.mesh.UsbDevices
import app.anynear.mesh.PairedDevice

/**
 * Page „Funkgeräte“: at the top „Meine Funkgeräte“ (the assigned one first, greyed out while it is not connected),
 * below „Neues Funkgerät koppeln“ with the radios found nearby and a button to search. To switch, one disconnects the
 * own radio and pairs or connects another one; there is no separate „Anderes Funkgerät einrichten“ any more.
 * Everything about the assigned radio itself (battery, software, radio check, advanced) is on [onOpenPaired].
 */
@Composable
internal fun RadiosPage(controller: AppController, onOpenPaired: () -> Unit) {
    val paired by controller.pairedDevice.collectAsState()
    val state by controller.linkState.collectAsState()
    val granted by controller.bluetoothPermissionGranted.collectAsState()

    // Search by itself on opening while no radio is connected; otherwise only with „Suchen“.
    LaunchedEffect(granted) {
        if (paired == null || state != LinkState.Connected) controller.startScan()
    }
    DisposableEffect(Unit) { onDispose { controller.stopScan() } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PairingWaitingSection(controller)
        KnownRadiosSection(controller, onOpenPaired = onOpenPaired)
        if (paired != null) RadioHelpSection(controller)
        NewRadiosSection(controller)
        AdvancedAddressEntry(controller)
    }
}

/**
 * State of the assigned radio for its row: text and whether it is connected. Not connected means greyed out with
 * „Nicht verbunden“, not an endless „Verbindung wird aufgebaut …“ while the radio is switched off or far away.
 */
@Composable
internal fun pairedRadioStatus(controller: AppController): Pair<String, Boolean> {
    val state by controller.linkState.collectAsState()
    val disconnected by controller.radioDisconnected.collectAsState()
    val busy by controller.deviceBusy.collectAsState()
    val setupNeeded by controller.deviceSetupNeeded.collectAsState()
    val connected = state == LinkState.Connected && !disconnected
    val paired by controller.pairedDevice.collectAsState()
    // How the app talks to it, only when not over Bluetooth (ADR 0094): „im WLAN“ or „per USB-Kabel“.
    val way = paired?.let { d ->
        when {
            NetworkTransport.isNetworkDevice(d) -> texts.settings.radioViaWifi
            UsbDevices.isUsbDevice(d) -> texts.settings.radioViaUsb
            else -> null
        }
    }
    val text = when {
        disconnected -> texts.settings.radioDisconnected
        // The restart while taking over changes is intended; „Nicht verbunden“ would only worry.
        busy -> texts.settings.radioApplying
        connected -> when (setupNeeded?.kind) {
            null -> listOfNotNull(state.label(), way).joinToString(" · ")
            DeviceSetupCheck.Kind.UPDATE -> texts.settings.radioUpdateAvailable
            else -> texts.settings.radioNotSetUp
        }
        state == LinkState.BluetoothUnavailable || state == LinkState.InUseElsewhere -> state.label()
        else -> texts.settings.radioNotConnected
    }
    return text to connected
}

/** Name of the assigned radio as the radio itself reports it, otherwise as it was found. */
@Composable
internal fun pairedRadioName(controller: AppController, device: PairedDevice): String {
    val own by controller.ownDevice.collectAsState()
    val pendingName by controller.pendingDeviceName.collectAsState()
    return pendingName ?: own?.longName?.takeIf { it.isNotBlank() } ?: device.name.ifBlank { texts.settings.unnamedRadio }
}

/** Row of the assigned radio under „Meine Funkgeräte“; opens its page. After „Trennen“ it offers „Verbinden“. */
@Composable
internal fun PairedRadioRow(controller: AppController, device: PairedDevice, onOpen: () -> Unit) {
    val disconnected by controller.radioDisconnected.collectAsState()
    val busy by controller.deviceBusy.collectAsState()
    val (status, connected) = pairedRadioStatus(controller)
    AppRow(
        title = pairedRadioName(controller, device),
        subtitle = status,
        icon = AppIcons.Funkgeraet,
        muted = !connected && !busy,
        trailing = {
            if (disconnected) {
                AppButton(texts.settings.reconnectRadio, controller::reconnectRadio, kind = ButtonKind.Secondary)
            } else {
                StatusDot(connected)
            }
        },
        chevron = true,
        onClick = onOpen,
    )
}

/**
 * Help for the assigned radio (pairing fails, used elsewhere, Bluetooth not allowed, not set up, problems on the
 * radio). Only shown when there is something to do.
 */
@Composable
internal fun RadioHelpSection(controller: AppController) {
    val state by controller.linkState.collectAsState()
    val circleProblems by controller.radioProblems.collectAsState()
    val channelProblem by controller.radioChannelProblem.collectAsState()
    // The same notice is on every circle; here only once, and also without a circle.
    val problems = androidx.compose.runtime.remember(circleProblems, channelProblem) {
        (listOfNotNull(channelProblem) + circleProblems.values).distinct()
    }
    val needed by controller.deviceSetupNeeded.collectAsState()
    val busy by controller.deviceBusy.collectAsState()
    // While the device is taking over changes, do not offer „Einrichten“ again already.
    val setupNeeded = needed?.takeUnless { busy }
    // Stored, but Bluetooth not allowed (e.g. after a reinstall): allow it directly here.
    val bluetoothMissing = state == LinkState.BluetoothUnavailable
    val inUseElsewhere by controller.radioInUseElsewhere.collectAsState()
    val pairingTrouble by controller.pairingTrouble.collectAsState()
    if (setupNeeded == null && problems.isEmpty() && !bluetoothMissing && !inUseElsewhere && !pairingTrouble) return

    AppSection(texts.settings.needsHelp) {
        if (pairingTrouble) PairingTroubleRow(controller)
        if (inUseElsewhere) {
            AppRow(title = texts.settings.radioInUseTitle, subtitle = texts.settings.radioInUseText, icon = AppIcons.Funkgeraet)
            AppRow(title = texts.settings.radioInUseConnectHere, icon = AppIcons.Funkgeraet, chevron = true, onClick = controller::takeOverRadio)
        }
        if (bluetoothMissing) {
            AppRow(
                title = texts.setup.allowBluetooth,
                subtitle = texts.chat.allowBluetoothText,
                icon = AppIcons.Funkgeraet,
                chevron = true,
                onClick = controller::requestBluetoothPermission,
            )
        }
        problems.forEach { AppRow(title = it, danger = true) }
        setupNeeded?.let { check ->
            val isUpdate = check.kind == DeviceSetupCheck.Kind.UPDATE
            AppRow(
                title = if (isUpdate) texts.settings.updateRadio else texts.settings.setUpRadio,
                subtitle = if (isUpdate) texts.settings.updateRadioHint else texts.settings.setUpRadioHint,
                icon = AppIcons.Funkgeraet,
                chevron = true,
                onClick = controller::requestDeviceSetup,
            )
        }
    }
}

/**
 * „Neues Funkgerät koppeln“: radios found nearby (Bluetooth) or in the Wi-Fi that are not yet under „Meine
 * Funkgeräte“, the state of the search and a button to search (again). Pairing one while another is assigned
 * switches to it; the previous one stays under „Meine Funkgeräte“.
 */
@Composable
internal fun NewRadiosSection(controller: AppController) {
    val granted by controller.bluetoothPermissionGranted.collectAsState()
    val scanning by controller.isScanning.collectAsState()
    val allFound by controller.scanResults.collectAsState()
    val knownIds = rememberKnownIds(controller)
    val found = allFound.filter { it.identifier !in knownIds }

    AppSection(texts.settings.pairNewRadio, footer = texts.settings.pairNewRadioFooter) {
        found.forEach { device ->
            AppRow(
                title = device.name.ifBlank { texts.settings.unnamedRadio },
                subtitle = device.connectionLabel(),
                icon = AppIcons.Funkgeraet,
                trailing = { AppButton(texts.settings.pairRadio, { controller.pair(device) }, kind = ButtonKind.Secondary) },
                onClick = { controller.pair(device) },
            )
        }
        when {
            scanning -> AppRow(
                title = if (found.isEmpty()) texts.setup.searchingRadio else texts.setup.stillSearching,
                leading = {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                },
            )
            found.isEmpty() -> AppRow(title = texts.setup.noRadioFound, icon = AppIcons.Funkgeraet, muted = true)
        }
        // Not found or a new radio without software: the cable shows what is missing (Android).
        if (controller.usbRescue != null) {
            AppRow(
                title = texts.radioSoftware.usbConnectRow,
                subtitle = texts.radioSoftware.usbConnectRowHint,
                icon = AppIcons.Funkgeraet,
                chevron = true,
                onClick = { controller.openUsbSetup() },
            )
        }
        // iPhone: no cable, the webapp on a computer does it (ADR 0095 §5).
        UsbWebRow(controller)
        if (!granted) {
            AppRow(
                title = texts.setup.searchBluetooth,
                subtitle = texts.setup.searchBluetoothWhy,
                icon = AppIcons.Funkgeraet,
                chevron = true,
                onClick = controller::requestBluetoothPermission,
            )
        }
    }
    AppButton(
        texts.settings.searchRadios,
        controller::startScan,
        modifier = Modifier.fillMaxWidth(),
        kind = ButtonKind.Secondary,
        icon = AppIcons.Aktualisieren,
        enabled = !scanning,
    )
}

private fun FoundDevice.connectionLabel() = when (connection) {
    FoundDevice.Connection.BLUETOOTH -> texts.setup.foundNearbyBluetooth
    FoundDevice.Connection.NETWORK -> texts.setup.foundOnWifi
}
