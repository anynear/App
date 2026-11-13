package app.anynear.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.app.AppController.ManualConnectResult
import app.anynear.mesh.FoundDevice
import kotlinx.coroutines.launch

/**
 * One-time assignment of your own radio. Searches automatically via Bluetooth and in the Wi-Fi.
 * [inset]: own margin; without where the frame already has it (guided setup).
 */
@Composable
internal fun SetupScreen(controller: AppController, inset: Boolean = true) {
    val granted by controller.bluetoothPermissionGranted.collectAsState()
    val scanning by controller.isScanning.collectAsState()
    val allFound by controller.scanResults.collectAsState()
    val knownIds = rememberKnownIds(controller)
    val found = allFound.filter { it.identifier !in knownIds }

    // Search immediately on opening, the user does not have to press anything.
    LaunchedEffect(granted) { controller.startScan() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = if (inset) 24.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(Modifier.padding(top = if (inset) 24.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    texts.setup.radioIntro,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        // Radios used before first: those nearby with „Verbinden“, the others below.
        item { KnownRadiosSection(controller) }

        // Newly found devices. How they were found is only a side note.
        if (found.isNotEmpty()) {
            item {
                Text(
                    if (found.size == 1) texts.setup.yourRadio else texts.setup.whichRadio,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            itemsIndexed(found, key = { _, device -> device.identifier }) { index, device ->
                FoundDeviceCard(device, highlighted = index == 0, onUse = { controller.pair(device) })
            }
            item {
                Text(
                    texts.setup.nameOnDisplay + " " + texts.settings.pairNewRadioFooter,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SearchStatus(scanning = scanning, nothingFound = allFound.isEmpty(), onRetry = controller::startScan)
        }

        if (!granted) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(texts.setup.searchBluetooth, style = MaterialTheme.typography.titleMedium)
                        Text(
                            texts.setup.searchBluetoothWhy,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        AppButton(texts.setup.allowBluetooth, controller::requestBluetoothPermission, kind = ButtonKind.Secondary)
                    }
                }
            }
        }

        // Not found, for example a new radio without software or with Wi-Fi instead of Bluetooth: the cable shows why (Android).
        if (controller.usbRescue != null) {
            item {
                AppButton(
                    texts.radioSoftware.usbConnectRow,
                    { controller.openUsbSetup() },
                    kind = ButtonKind.Quiet,
                    icon = AppIcons.Funkgeraet,
                )
            }
        }
        // iPhone: no cable, the webapp on a computer does it (ADR 0095 §5).
        if (controller.offersUsbOnTheWeb()) {
            item { UsbWebHint(controller) }
        }

        item { AdvancedAddressEntry(controller) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SearchStatus(scanning: Boolean, nothingFound: Boolean, onRetry: () -> Unit) {
    when {
        scanning -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                if (nothingFound) texts.setup.searchingRadio else texts.setup.stillSearching,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        nothingFound -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                texts.setup.noRadioFound,
                style = MaterialTheme.typography.bodyLarge,
            )
            AppButton(texts.setup.searchAgain, onRetry, icon = AppIcons.Aktualisieren)
        }
        else -> AppButton(texts.setup.searchAgain, onRetry, kind = ButtonKind.Quiet, icon = AppIcons.Aktualisieren)
    }
}

@Composable
private fun FoundDeviceCard(device: FoundDevice, highlighted: Boolean, onUse: () -> Unit) {
    Card(
        onClick = onUse,
        modifier = Modifier.fillMaxWidth(),
        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(AppIcons.Funkgeraet, contentDescription = null, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    when (device.connection) {
                        FoundDevice.Connection.BLUETOOTH -> texts.setup.foundNearbyBluetooth
                        FoundDevice.Connection.NETWORK -> texts.setup.foundOnWifi
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppButton(texts.setup.connect, onUse, kind = if (highlighted) ButtonKind.Main else ButtonKind.Secondary)
        }
    }
}

/**
 * For experienced users, deliberately collapsed: enter the address by hand.
 * The address is only taken over if a radio really answers there.
 */
@Composable
internal fun AdvancedAddressEntry(controller: AppController) {
    var expanded by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (!expanded) {
        AppButton(texts.setup.connectByAddressAdvanced, { expanded = true }, kind = ButtonKind.Quiet, icon = AppIcons.Regler)
        return
    }

    fun submit() {
        if (checking) return
        checking = true
        problem = null
        scope.launch {
            problem = when (controller.connectByAddress(input)) {
                ManualConnectResult.Connected -> null
                ManualConnectResult.InvalidAddress -> texts.setup.invalidAddress
                ManualConnectResult.NotReachable -> texts.setup.addressNotReachable
            }
            checking = false
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(texts.setup.connectByAddress, style = MaterialTheme.typography.titleMedium)
            Text(
                texts.setup.connectByAddressHelp,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    problem = null
                },
                singleLine = true,
                enabled = !checking,
                isError = problem != null,
                placeholder = { Text(texts.setup.addressPlaceholder) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(if (checking) texts.setup.checkingConnection else texts.setup.connect, ::submit, enabled = !checking && input.isNotBlank())
                if (checking) CircularProgressIndicator(Modifier.size(24.dp))
                AppButton(texts.common.close, { expanded = false }, kind = ButtonKind.Quiet, enabled = !checking)
            }
        }
    }
}
