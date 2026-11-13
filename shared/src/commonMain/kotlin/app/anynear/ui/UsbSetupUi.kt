package app.anynear.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.anynear.anmp.core.firmware.RecommendedRadio
import app.anynear.app.AppController
import app.anynear.app.RecommendedRadios
import app.anynear.app.UsbConnection
import app.anynear.app.UsbPurpose
import app.anynear.app.UsbRescueFlow
import app.anynear.app.UsbRescueState
import app.anynear.i18n.Language
import app.anynear.i18n.currentLanguage
import app.anynear.i18n.texts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The steps at the USB cable from ADR 0094 inside the dialog: the choice after plugging in (with ways that are not
 * possible greyed out and why), the model for installing Meshtastic, the progress, the way of use with the honest note
 * about power cuts, the Wi-Fi access and connecting.
 */
@Composable
internal fun UsbSetupContent(controller: AppController, rescue: UsbRescueFlow, state: UsbRescueState, connected: Boolean, scope: CoroutineScope) {
    val t = texts.radioSoftware
    when (state) {
        is UsbRescueState.Choose -> {
            Text(t.usbChooseText)
            // New, empty or with other software: say so, setting up installs Meshtastic.
            if (!state.radio.answered) Hint(t.usbNoMeshtasticYet)
            AppButton(
                t.usbChooseSetup,
                onClick = { scope.launch { rescue.choose(UsbPurpose.SETUP, state.radio) } },
                kind = if (state.canSetUp) ButtonKind.Main else ButtonKind.Secondary,
                enabled = state.canSetUp,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!state.canSetUp) Hint(t.usbSetupNotPossible)
            AppButton(
                t.usbChooseUse,
                onClick = { scope.launch { rescue.choose(UsbPurpose.USE, state.radio) } },
                kind = if (state.canUse) ButtonKind.Main else ButtonKind.Secondary,
                enabled = state.canUse,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!state.canUse) Hint(t.usbUseNotPossible)
            // Switch the way of use in both directions with one tap (ADR 0094 §3): a radio in the Wi-Fi back to
            // Bluetooth (power cut), a radio on Bluetooth into the Wi-Fi (stationary at home).
            if (state.canSwitchToBluetooth) {
                AppButton(t.usbSwitchToBluetooth, onClick = { scope.launch { rescue.chooseMobile(state.radio) } }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
            }
            if (state.canSwitchToWifi) {
                AppButton(t.usbSwitchToWifi, onClick = { rescue.chooseStationary(state.radio) }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
                Hint(t.usbModePowerNote)
            }
            AppButton(t.usbChooseRescue, onClick = { scope.launch { rescue.choose(UsbPurpose.RESCUE, state.radio) } }, kind = ButtonKind.Quiet, modifier = Modifier.fillMaxWidth())
            Hint(t.usbChooseRescueHint)
        }
        is UsbRescueState.PickRadio -> PickRadio(state, rescue, scope)
        is UsbRescueState.Installing -> {
            Text(if (state.progress == null) t.usbInstallDownloading else t.usbInstalling)
            val progress = state.progress
            if (progress == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            Hint(t.usbInstallKeepPlugged)
        }
        is UsbRescueState.ChooseMode -> {
            Text(t.usbModeTitle)
            AppButton(t.usbModeMobile, onClick = { scope.launch { rescue.chooseMobile(state.radio) } }, kind = ButtonKind.Main, modifier = Modifier.fillMaxWidth())
            Hint(t.usbModeMobileHint)
            if (state.wifiPossible) {
                AppButton(t.usbModeStationary, onClick = { rescue.chooseStationary(state.radio) }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
                Hint(t.usbModeStationaryHint)
                Text(t.usbModePowerNote, style = MaterialTheme.typography.bodySmall)
            }
        }
        is UsbRescueState.WifiCredentials -> WifiForm(state, rescue, scope)
        is UsbRescueState.Pairing -> when (state.via) {
            UsbConnection.BLUETOOTH -> {
                Busy(t.usbPairing)
                Hint(t.usbPairingHint)
            }
            UsbConnection.WIFI -> Busy(t.usbPairingWifi)
            UsbConnection.USB -> Busy(t.usbPairingUsb)
        }
        is UsbRescueState.Paired -> when {
            state.via == UsbConnection.USB -> Text(t.usbPairedUsb(state.name))
            state.via == UsbConnection.WIFI -> Text(t.usbPairedWifi(state.name))
            connected -> Text(t.usbPairedConnected(state.name))
            else -> {
                Text(t.usbPaired(state.name))
                if (controller.canOpenBluetoothSettings) Hint(texts.settings.pairingWaitingNoWindow)
            }
        }
        is UsbRescueState.NotSeen -> when (state.via) {
            UsbConnection.BLUETOOTH -> Text(t.usbNotSeen)
            UsbConnection.WIFI -> {
                Text(t.usbNotSeenWifi)
                AppButton(t.usbWifiAgain, onClick = { rescue.chooseStationary(state.radio) }, kind = ButtonKind.Quiet, modifier = Modifier.fillMaxWidth())
            }
            UsbConnection.USB -> Text(t.usbNotSeenUsb)
        }
        else -> Unit
    }
}

/**
 * Which radio gets Meshtastic (ADR 0095 §3): the recommended radios with picture, name and what they are good for, the
 * recognised one preselected (a good default instead of a question, ADR 0007), radios on another chip greyed out with the
 * reason. Every other model of the list sits behind the small „Anderes Meshtastic-fähiges Funkgerät“. One main button
 * for both ways: an ESP32 is written over the cable, an nRF52 continues with its drive.
 */
@Composable
private fun PickRadio(state: UsbRescueState.PickRadio, rescue: UsbRescueFlow, scope: CoroutineScope) {
    val t = texts.radioSoftware
    val selection = state.selection
    var chosen by remember(selection) { mutableStateOf(selection.preselected) }
    // The full list right away when nothing recommended fits, or the recognised radio is not among the recommended.
    var showOthers by remember(selection) {
        mutableStateOf(selection.recommended.isEmpty() || selection.preselected?.let { it !in selection.recommended } == true)
    }
    Text(t.usbPickInstall)
    selection.recommended.forEach { option ->
        RadioCard(
            name = option.radio?.name ?: modelName(option.hwModel),
            detail = option.radio?.useText(),
            image = option.radio?.image,
            selected = chosen == option,
            onClick = { chosen = option },
        )
    }
    state.unfit.forEach { radio ->
        RadioCard(name = radio.name, detail = t.usbRadioUnfit, image = radio.image, selected = false, enabled = false, onClick = {})
    }
    if (showOthers) {
        selection.others.forEach { option ->
            RadioCard(name = modelName(option.hwModel), detail = null, image = null, selected = chosen == option, onClick = { chosen = option }, compact = true)
        }
    }
    val pick = chosen
    AppButton(
        t.usbInstallMeshtastic,
        onClick = { pick?.let { scope.launch { rescue.pick(it) } } },
        kind = ButtonKind.Main,
        enabled = pick != null,
        modifier = Modifier.fillMaxWidth(),
    )
    if (pick != null && pick == selection.preselected) Hint(t.usbRecognisedHint) else Hint(t.usbPickInstallHint)
    if (!showOthers && selection.others.isNotEmpty()) {
        AppButton(t.usbOtherRadio, onClick = { showOthers = true }, kind = ButtonKind.Quiet, modifier = Modifier.fillMaxWidth())
    }
}

/** „Good for“ line of a recommended radio in the language of the app, English if it is missing (ADR 0095 §2). */
private fun RecommendedRadio.useText(): String? {
    val code = if (currentLanguage() == Language.GERMAN) "de" else "en"
    return (use[code] ?: use["en"])?.takeIf { it.isNotBlank() }
}

/** One radio to choose: picture (the radio icon while it loads or if it cannot be loaded), name and a short line. */
@Composable
private fun RadioCard(
    name: String,
    detail: String?,
    image: String?,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) scheme.secondaryContainer else scheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f).semantics { this.selected = selected },
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!compact) RadioPicture(image)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        }
    }
}

/** Picture of a recommended radio from the website; any failure (offline, broken file) just keeps the icon. */
@Composable
private fun RadioPicture(url: String?) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.Default) {
            RecommendedRadios.image(url)?.let { bytes -> runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
        }
    }
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
        } else {
            Icon(AppIcons.Funkgeraet, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WifiForm(state: UsbRescueState.WifiCredentials, rescue: UsbRescueFlow, scope: CoroutineScope) {
    val t = texts.radioSoftware
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Text(t.usbWifiText)
    OutlinedTextField(
        value = ssid,
        onValueChange = { ssid = it },
        singleLine = true,
        label = { Text(t.usbWifiName) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        singleLine = true,
        label = { Text(t.usbWifiPassword) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.failed) Text(t.usbWifiFailed, color = MaterialTheme.colorScheme.error)
    AppButton(
        t.usbWifiConnect,
        onClick = { scope.launch { rescue.enableWifi(state.radio, ssid, password) } },
        kind = ButtonKind.Main,
        enabled = ssid.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Readable name of a Meshtastic hardware model: `HELTEC_V3` → „Heltec V3“, `T_ECHO` → „T Echo“, `RAK4631` stays. */
internal fun modelName(hwModel: String): String = hwModel.split('_').filter { it.isNotEmpty() }.joinToString(" ") { part ->
    when {
        part.length <= 2 || part.any { it.isDigit() } && part.length <= 3 -> part.uppercase()
        part.any { it.isDigit() } && part.takeWhile { it.isLetter() }.length <= 3 -> part.uppercase()
        else -> part.lowercase().replaceFirstChar { it.uppercase() }
    }
}
