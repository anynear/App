package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.anynear.app.AppController
import app.anynear.app.FirmwareBlocker
import app.anynear.app.RadioSoftware
import app.anynear.app.RadioSoftwareState
import app.anynear.app.SoftwareCheck
import app.anynear.i18n.texts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Row on the radio page, as long as there is new software that the app can install over the current
 * connection (ADR 0056 §3). It stays even after „Später“. If the app cannot do the route (yet), it shows nothing: the
 * list names every stable Meshtastic release, also for devices whose route the app does not master yet.
 */
@Composable
internal fun RadioSoftwareSection(controller: AppController) {
    val software = controller.radioSoftware ?: return
    val state by software.state.collectAsState()
    val available = (state as? RadioSoftwareState.Available)?.takeIf { it.installable } ?: return
    var consent by remember { mutableStateOf(false) }
    AppSection {
        AppRow(
            title = texts.radioSoftware.title,
            subtitle = if (available.ready) texts.radioSoftware.ready else texts.radioSoftware.loading,
            icon = AppIcons.Aktualisieren,
            chevron = true,
            enabled = available.ready,
            onClick = { consent = true },
        )
    }
    if (consent) ConsentDialog(software) { consent = false }
}

/** Hint „Neue Software für dein Funkgerät“ until „Später“ is chosen; then only again after the next start. */
@Composable
internal fun RadioSoftwareNotice(controller: AppController) {
    val software = controller.radioSoftware ?: return
    val notice by software.notice.collectAsState()
    if (notice != null) ConsentDialog(software, onDismiss = software::postpone)
}

/** Asks every time (§3). The button waits as long as a condition is missing, and says which one. */
@Composable
private fun ConsentDialog(software: RadioSoftware, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var blocker by remember { mutableStateOf<FirmwareBlocker?>(null) }
    // Battery and waiting messages change while the dialog is open.
    LaunchedEffect(Unit) {
        while (true) {
            blocker = software.blocker()
            delay(BLOCKER_REFRESH)
        }
    }
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.radioSoftware.title) },
        text = { Text(blocker?.text() ?: texts.radioSoftware.noticeText) },
        confirmButton = {
            AppButton(texts.radioSoftware.updateNow, enabled = blocker == null, onClick = {
                scope.launch { blocker = software.install() }
            })
        },
        dismissButton = { AppButton(texts.common.later, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/**
 * During the installation a separate page „Lass anynear offen“, screen on; „Zurück“ asks first (§2 step 5).
 * Afterwards the result, on a repeated error with the rescue via USB (§2 step 7).
 */
@Composable
internal fun RadioSoftwareProgress(controller: AppController) {
    val software = controller.radioSoftware ?: return
    val state by software.state.collectAsState()
    when (val s = state) {
        is RadioSoftwareState.Installing -> InstallingScreen(software, s)
        is RadioSoftwareState.Done -> AppDialog(
            onDismissRequest = software::dismissResult,
            title = { Text(texts.radioSoftware.doneTitle) },
            text = { Text(if (s.settingsKept) texts.radioSoftware.doneText else texts.radioSoftware.doneSetUpAgain) },
            confirmButton = {
                AppButton(texts.common.ok, onClick = {
                    software.dismissResult()
                    if (!s.settingsKept) controller.requestDeviceSetup()
                })
            },
        )
        is RadioSoftwareState.Failed -> AppDialog(
            onDismissRequest = software::dismissResult,
            title = { Text(texts.radioSoftware.failedTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (s.wroteAnything) texts.radioSoftware.failedWrote else texts.radioSoftware.failedText)
                    if (s.attempts >= RadioSoftware.USB_AFTER) Text(texts.radioSoftware.usbText)
                }
            },
            confirmButton = { AppButton(texts.common.ok, software::dismissResult) },
        )
        else -> Unit
    }
}

@Composable
private fun InstallingScreen(software: RadioSoftware, state: RadioSoftwareState.Installing) {
    var leave by remember { mutableStateOf(false) }
    KeepScreenOn()
    Dialog(
        onDismissRequest = { leave = true },
        properties = AppFullScreenDialogProperties,
    ) {
        // Also in the window of the dialog: it covers the activity completely.
        KeepScreenOn()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(texts.radioSoftware.keepOpen, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                val progress = state.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Text(
                    when {
                        !state.writing -> texts.radioSoftware.preparing
                        progress != null && progress >= 1f -> texts.radioSoftware.checking
                        progress != null -> texts.radioSoftware.progress((progress * 100).toInt())
                        else -> texts.radioSoftware.preparing
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    if (leave) {
        AppDialog(
            onDismissRequest = { leave = false },
            title = { Text(texts.radioSoftware.leaveTitle) },
            text = { Text(if (state.writing) texts.radioSoftware.leaveWriting else texts.radioSoftware.leaveText) },
            confirmButton = { AppButton(texts.radioSoftware.stay, { leave = false }) },
            dismissButton = {
                if (!state.writing) {
                    AppButton(texts.common.cancel, onClick = {
                        leave = false
                        software.cancel()
                    }, kind = ButtonKind.Quiet)
                }
            },
        )
    }
}

/** Under „Erweitert“: firmware version and checking on request (§2 step 1), with the result. */
@Composable
internal fun RadioSoftwareAdvanced(controller: AppController) {
    val software = controller.radioSoftware
    val firmware by controller.radioFirmware.collectAsState()
    val result by (software?.checkResult ?: NoCheck).collectAsState()
    AppRow(
        title = firmware?.let { texts.radioSoftware.advancedVersion(it.version) } ?: texts.radioSoftware.advancedUnknown,
        subtitle = when {
            software?.checksEnabled != true -> null
            else -> result?.text() ?: texts.radioSoftware.checkNow
        },
        icon = AppIcons.Info,
        enabled = result != SoftwareCheck.Checking,
        onClick = software?.takeIf { it.checksEnabled }?.let { { it.check(force = true) } },
    )
}

private val NoCheck = kotlinx.coroutines.flow.MutableStateFlow<SoftwareCheck?>(null)

private fun SoftwareCheck.text(): String = when (this) {
    SoftwareCheck.Checking -> texts.radioSoftware.checkRunning
    SoftwareCheck.NoRadio -> texts.radioSoftware.notConnected
    SoftwareCheck.Failed -> texts.radioSoftware.checkFailed
    is SoftwareCheck.UpToDate -> texts.radioSoftware.upToDate
    is SoftwareCheck.Newer -> if (installable) texts.radioSoftware.newerAvailable(version) else texts.radioSoftware.newerNotYet(version)
}

private fun FirmwareBlocker.text(): String = when (this) {
    FirmwareBlocker.NOT_CONNECTED -> texts.radioSoftware.notConnected
    FirmwareBlocker.RADIO_BATTERY -> texts.radioSoftware.radioBattery
    FirmwareBlocker.PHONE_BATTERY -> texts.radioSoftware.phoneBattery
    FirmwareBlocker.MESSAGES_WAITING -> texts.radioSoftware.messagesWaiting
}

private val BLOCKER_REFRESH = 5.seconds
