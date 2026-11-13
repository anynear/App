package app.anynear.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.mesh.PairedDevice

/**
 * „Meine Funkgeräte“: all radios that have been assigned here before. With [onOpenPaired] the assigned one comes
 * first (greyed out while not connected, tap opens its page); without it (guided setup) it is left out. Those
 * nearby (just found via Bluetooth or in the Wi-Fi) have „Verbinden“, the others are greyed out as „Nicht in der
 * Nähe“. The bin icon forgets a device (with a confirmation).
 * [search]: search by itself while this section is shown.
 */
@Composable
internal fun KnownRadiosSection(controller: AppController, search: Boolean = false, onOpenPaired: (() -> Unit)? = null) {
    val known by controller.knownDevices.collectAsState()
    val paired by controller.pairedDevice.collectAsState()
    val found by controller.scanResults.collectAsState()
    val scanning by controller.isScanning.collectAsState()
    val linkState by controller.linkState.collectAsState()
    val disconnected by controller.radioDisconnected.collectAsState()
    val connected = paired != null && linkState == app.anynear.mesh.LinkState.Connected && !disconnected
    var forgetting by remember { mutableStateOf<PairedDevice?>(null) }
    val others = known.filter { it.identifier != paired?.identifier }
    if (search && others.isNotEmpty()) {
        DisposableEffect(Unit) {
            controller.startScan()
            onDispose { controller.stopScan() }
        }
    }
    val current = paired?.takeIf { onOpenPaired != null }
    if (others.isEmpty() && current == null) return
    val nearby = others.mapNotNull { device -> found.firstOrNull { it.identifier == device.identifier }?.let { device to it } }
    val away = others.filter { device -> found.none { it.identifier == device.identifier } }

    AppSection(texts.settings.myRadios, footer = texts.settings.myRadiosFooter) {
        if (current != null && onOpenPaired != null) PairedRadioRow(controller, current, onOpenPaired)
        nearby.forEach { (device, seen) ->
            AppRow(
                // No name remembered yet (older entry): the name it announces right now is better than „Funkgerät“.
                title = device.name.ifBlank { seen.name }.ifBlank { texts.settings.unnamedRadio },
                subtitle = texts.settings.radioNearby,
                icon = AppIcons.Funkgeraet,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppButton(texts.settings.reconnectRadio, {
                            // The remembered name is the radio's own; the announced one only fills a gap.
                            controller.pairKnown(PairedDevice(device.identifier, device.name.ifBlank { seen.name }))
                        }, kind = ButtonKind.Secondary)
                        Spacer(Modifier.width(4.dp))
                        ForgetButton { forgetting = device }
                    }
                },
            )
        }
        away.forEach { device ->
            AppRow(
                title = device.displayName(),
                // „Wird gesucht …“ only while the app is really looking for a radio, otherwise plainly „Nicht verbunden“:
                // whether it is nearby the app does not know without a search.
                subtitle = if (scanning && !connected) texts.settings.radioLooking else texts.settings.radioNotConnected,
                icon = AppIcons.Funkgeraet,
                enabled = false,
                trailing = { ForgetButton { forgetting = device } },
            )
        }
    }

    forgetting?.let { device ->
        ConfirmDialog(
            title = texts.settings.forgetRadioTitle(device.displayName()),
            text = texts.settings.forgetRadioText,
            confirm = texts.settings.confirmForget,
            onConfirm = {
                forgetting = null
                controller.forgetDevice(device.identifier)
            },
            onDismiss = { forgetting = null },
        )
    }
}

/** Found devices that are already under „Meine Funkgeräte“ are not shown again by „Funkgerät verbinden“. */
@Composable
internal fun rememberKnownIds(controller: AppController): Set<String> {
    val known by controller.knownDevices.collectAsState()
    return remember(known) { known.map { it.identifier }.toSet() }
}

@Composable
private fun ForgetButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(AppIcons.Papierkorb, contentDescription = texts.settings.forgetRadio, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun PairedDevice.displayName(): String = name.ifBlank { texts.settings.unnamedRadio }
