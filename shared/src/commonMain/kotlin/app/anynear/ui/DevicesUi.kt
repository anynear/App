package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anynear.anmp.core.DeviceInfo
import app.anynear.anmp.core.RecoverResult
import app.anynear.app.AppController
import app.anynear.data.platformDeviceName
import app.anynear.i18n.texts
import app.anynear.passwords.UnlockResult
import app.anynear.passwords.copySecret
import app.anynear.passwords.rememberDeviceCheck
import app.anynear.passwords.rememberPasswordUnlock
import io.github.alexzhirkevich.qrose.ImageFormat
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import io.github.alexzhirkevich.qrose.toByteArray
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** „Meine Geräte“ (ADR 0042): own devices, add, remove, lock out a device. The emergency card is in the settings. */
@Composable
internal fun MyDevicesScreen(controller: AppController, onBack: () -> Unit) {
    val devices by controller.ownDevices.collectAsState()
    val me by controller.myDeviceId.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<DeviceInfo?>(null) }
    var lockingOut by remember { mutableStateOf<DeviceInfo?>(null) }

    Column(Modifier.fillMaxSize()) {
        AppHeader(texts.devices.title, leading = { HeaderBackButton(onBack) })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            AppSection(footer = texts.devices.footer) {
                val now = Clock.System.now().toEpochMilliseconds()
                devices.forEach { d ->
                    val own = d.deviceId == me
                    AppRow(
                        title = d.name.ifBlank { if (own) texts.devices.thisDevice else texts.devices.unnamed },
                        subtitle = if (own) texts.devices.thisDevice.takeIf { d.name.isNotBlank() } else d.added.takeIf { it > 0 }?.let { texts.devices.added(formatDayAndTime(it, now)) },
                        icon = AppIcons.Person,
                        trailing = if (own) null else ({ AppButton(texts.devices.remove, { removing = d }, kind = ButtonKind.Quiet) }),
                    )
                }
                AppRow(title = texts.devices.add, icon = AppIcons.Plus, onClick = { adding = true })
            }
        }
    }
    if (adding) AddDeviceFlow(controller) { adding = false }
    // Removing another own device locks the person out there: only after the device lock (ADR 0042).
    val confirmRemove = rememberLockCheck { removing?.let { controller.removeDevice(it.deviceId) }; removing = null }
    removing?.let { d ->
        AppDialog(
            onDismissRequest = { removing = null },
            title = { Text(texts.devices.removeTitle(d.name.ifBlank { texts.devices.unnamed })) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(texts.devices.removeText)
                    if (controller.canLockOut) AppButton(texts.devices.lockOut, { lockingOut = d; removing = null }, kind = ButtonKind.Quiet)
                }
            },
            confirmButton = { AppButton(texts.devices.remove, { confirmRemove(texts.devices.removeReason) }, kind = ButtonKind.Danger) },
            dismissButton = { AppButton(texts.common.cancel, { removing = null }, kind = ButtonKind.Quiet) },
        )
    }
    lockingOut?.let { d -> LockOutDialog(controller, d) { lockingOut = null } }
}

/**
 * Card at the top of „Nachrichten“ while the emergency card is missing (ADR 0093), e.g. after „Später“ in the setup.
 * Goes away once the card is confirmed.
 */
@Composable
internal fun EmergencyCardReminder(controller: AppController) {
    var missing by remember { mutableStateOf(controller.emergencyCardMissing) }
    if (!missing) return
    var open by rememberSaveable { mutableStateOf(false) }
    AppSection(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        SectionContent {
            Text(texts.setup.cardReminderTitle, style = MaterialTheme.typography.titleMedium)
            Text(texts.setup.cardReminderText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppButton(texts.setup.cardCreate, { open = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
        }
    }
    if (open) EmergencyCardDialog(controller) {
        open = false
        missing = controller.emergencyCardMissing
    }
}

/** Emergency card as its own row in the settings, not under „Meine Geräte“. */
@Composable
internal fun EmergencyCardRow(controller: AppController) {
    var card by rememberSaveable { mutableStateOf(false) }
    AppRow(
        title = if (controller.hasEmergencyCard) texts.devices.cardRenew else texts.devices.cardCreate,
        subtitle = if (controller.hasEmergencyCard) texts.devices.cardDone else texts.devices.cardFooter,
        icon = AppIcons.Schluessel,
        chevron = true,
        onClick = { card = true },
    )
    if (card) EmergencyCardDialog(controller) { card = false }
}

private enum class AddDeviceStep { Menu, Scan }

/**
 * „Gerät hinzufügen“: built like „Kontakt hinzufügen“, but only with the camera. The QR code on the new device
 * leads like a tapped link to [PendingPairingDialog]; there is nothing to paste (FUT B3).
 */
@Composable
internal fun AddDeviceFlow(controller: AppController, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(AddDeviceStep.Menu) }
    when (step) {
        AddDeviceStep.Menu -> ActionsDialog(texts.devices.add, onDismiss = onDismiss) {
            Text(texts.devices.addText, modifier = Modifier.padding(bottom = 8.dp))
            AppRow(title = texts.people.scanQrCode, subtitle = texts.devices.scanHint, icon = AppIcons.Kamera, onClick = { step = AddDeviceStep.Scan })
        }
        AddDeviceStep.Scan -> QrScanDialog(controller, help = texts.devices.scanHelp, onDismiss = onDismiss)
    }
}

/**
 * Add a new own device from its code ([link], scanned or tapped), then confirm with the device lock.
 * If there are passwords, the same prompt opens their key; they come along (ADR 0039, 0042 §5).
 */
@Composable
internal fun AddDeviceDialog(controller: AppController, link: String, onDismiss: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // ADR 0093: before the device lock, compare the code with the one on the new device.
    var compare by remember { mutableStateOf<String?>(null) }

    fun add(passwordKey: ByteArray?) {
        scope.launch {
            busy = true
            error = controller.addDevice(link.trim(), passwordKey)
            busy = false
            if (error == null) {
                controller.showNotice(texts.devices.added)
                onDismiss()
            }
        }
    }
    val unlockPasswords = rememberPasswordUnlock { r ->
        when (r) {
            is UnlockResult.Unlocked -> add(r.key)
            UnlockResult.KeyLost -> add(null)
            UnlockResult.NoDeviceLock -> error = texts.devices.addNeedsLock
            UnlockResult.Cancelled -> Unit
        }
    }
    val check = rememberDeviceCheck { ok ->
        when (ok) {
            true -> add(null)
            null -> error = texts.devices.addNeedsLock
            false -> Unit
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (compare != null) texts.devices.compareTitle else texts.devices.addConfirmTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val c = compare
                if (c != null) {
                    Text(texts.devices.compareText)
                    Text(c, style = MaterialTheme.typography.displaySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text(texts.devices.addConfirmText, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(texts.devices.addConfirmText)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) CircularProgressIndicator()
            }
        },
        confirmButton = {
            if (compare == null) {
                AppButton(texts.devices.add, {
                    if (!controller.isPairLink(link)) {
                        error = texts.devices.addInvalid
                    } else {
                        scope.launch { compare = controller.pairingCode(link) ?: run { error = texts.devices.addInvalid; null } }
                    }
                }, kind = ButtonKind.Primary)
            } else {
                AppButton(texts.devices.compareYes, {
                    if (controller.passwordsNeedDeviceLock) unlockPasswords(texts.devices.addReason) else check(texts.devices.addReason)
                }, kind = ButtonKind.Primary, enabled = !busy)
            }
        },
        dismissButton = {
            if (compare == null) {
                AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet)
            } else {
                AppButton(texts.devices.compareNo, {
                    controller.showNotice(texts.devices.compareMismatch)
                    onDismiss()
                }, kind = ButtonKind.Quiet, enabled = !busy)
            }
        },
    )
}

/** Lock out a stolen device for good: new person key with the code of the emergency card (ADR 0042 §6). */
@Composable
internal fun LockOutDialog(controller: AppController, device: DeviceInfo, onDismiss: () -> Unit) {
    val needsCode = controller.lockOutNeedsCode
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lockOut = rememberLockCheck {
        scope.launch {
            busy = true
            val ok = controller.lockOutDevices(listOf(device.deviceId), code.takeIf { needsCode })
            busy = false
            if (ok) {
                controller.showNotice(texts.devices.lockOutDone)
                onDismiss()
            } else {
                error = if (needsCode) texts.devices.lockOutFailed else texts.devices.lockOutFailedNoCode
            }
        }
    }
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.devices.lockOut) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (needsCode) texts.devices.lockOutText else texts.devices.lockOutTextNoCode)
                if (needsCode) CardCodeField({ code = it; error = null })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) CircularProgressIndicator()
            }
        },
        confirmButton = {
            AppButton(texts.devices.lockOut, { lockOut(texts.devices.lockOutReason) }, kind = ButtonKind.Danger)
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/**
 * Create or renew the emergency card: show the code, have it written down, type it once (ADR 0023, 0042 §2). A card
 * not yet confirmed is shown again with the same code, also after closing the dialog or a restart of the app; the
 * code itself is not kept in the saved state of the screen, it comes from the core.
 */
@Composable
internal fun EmergencyCardDialog(controller: AppController, onDismiss: () -> Unit) {
    var oldCode by remember { mutableStateOf("") }
    var code by remember { mutableStateOf<String?>(null) }
    var looked by remember { mutableStateOf(false) }
    var typing by rememberSaveable { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val needsOld = controller.hasEmergencyCard

    fun create() {
        scope.launch {
            busy = true
            code = controller.createEmergencyCard(oldCode.takeIf { needsOld })
            busy = false
            if (code == null) error = texts.devices.cardFailed
        }
    }
    LaunchedEffect(Unit) {
        code = controller.pendingEmergencyCard()
        looked = true
        if (code == null) {
            typing = false
            if (!needsOld) create()
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.devices.cardTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val c = code
                when {
                    !looked -> Unit
                    c == null && needsOld -> {
                        Text(texts.devices.cardOldCode)
                        CardCodeField({ oldCode = it; error = null })
                    }
                    c != null && !typing -> {
                        Text(texts.devices.cardShowText)
                        SelectionContainer {
                            Text(c, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                        }
                        QrCode(c, size = 160.dp)
                        CardCopyAndPrint(c)
                    }
                    c != null -> {
                        Text(texts.devices.cardTypeText)
                        CardCodeField({ typed = it; error = null })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) CircularProgressIndicator()
            }
        },
        confirmButton = {
            val c = code
            when {
                !looked -> Unit
                c == null && needsOld -> AppButton(texts.common.next, ::create, kind = ButtonKind.Primary)
                c != null && !typing -> AppButton(texts.devices.cardNext, { typing = true }, kind = ButtonKind.Primary)
                c != null -> AppButton(texts.common.done, {
                    scope.launch {
                        if (controller.confirmEmergencyCard(typed)) onDismiss() else error = texts.devices.cardWrong
                    }
                }, kind = ButtonKind.Primary)
            }
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/**
 * „Kopieren“ (e.g. into a password manager; marked as confidential like passwords) and „Drucken“ (one A4 page via the
 * print dialog of the system) for the shown code of the emergency card.
 */
@Composable
private fun CardCopyAndPrint(code: String) {
    var copied by remember(code) { mutableStateOf(false) }
    val print = rememberHtmlPrint()
    val qr = rememberQrCodePainter(code)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppButton(
            if (copied) texts.devices.cardCopied else texts.common.copy,
            {
                copySecret(code)
                copied = true
            },
            kind = ButtonKind.Quiet,
            icon = if (copied) AppIcons.Haken else AppIcons.Kopieren,
        )
        AppButton(
            texts.devices.cardPrint,
            {
                val png = runCatching { qr.toByteArray(600, 600, ImageFormat.PNG) }.getOrNull()
                val date = formatDateTime(Clock.System.now().toEpochMilliseconds()).substringBefore(",")
                print(texts.devices.cardPrintTitle, emergencyCardHtml(code, png, texts.devices.cardPrintTitle, texts.devices.cardPrintText, texts.devices.cardPrintDate(date)))
            },
            kind = ButtonKind.Quiet,
            icon = AppIcons.Drucken,
        )
    }
}

/** Scanned code of a new own device: confirm and add. */
@Composable
internal fun PendingPairingDialog(controller: AppController) {
    val link by controller.pendingPairing.collectAsState()
    link?.let { AddDeviceDialog(controller, it, controller::dismissPairing) }
}

/**
 * After the emergency card or adding this phone (ADR 0106 §7): „Ersetzt dieses Handy eins deiner Geräte?“ The chosen
 * device's settings come over and it leaves „Meine Geräte“, without a second question. Then the app restarts so that
 * everything reads the settings.
 */
@Composable
internal fun ReplaceDeviceDialog(controller: AppController) {
    val offer by controller.replaceOffer.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val list = offer ?: return

    fun choose(c: AppController.ReplaceCandidate?) {
        scope.launch {
            busy = true
            failed = false
            val ok = controller.replaceDevice(c?.deviceId)
            busy = false
            when {
                !ok -> failed = true
                c != null -> controller.finishReplace()
            }
        }
    }

    AppDialog(
        onDismissRequest = {},
        title = { Text(texts.devices.replaceTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(texts.devices.replaceText, style = MaterialTheme.typography.bodyMedium)
                list.forEach { c ->
                    AppRow(
                        c.name.ifBlank { texts.devices.unnamed },
                        subtitle = if (c.listed) null else texts.devices.replaceRemoved,
                        icon = AppIcons.Person,
                        chevron = true,
                        onClick = { if (!busy) choose(c) },
                    )
                }
                AppRow(texts.devices.replaceNone, onClick = { if (!busy) choose(null) })
                if (busy) Text(texts.devices.replaceWorking, style = MaterialTheme.typography.bodyMedium)
                if (failed) Text(texts.devices.replaceFailed, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {},
    )
}

/** An own device was added that was not added here (ADR 0042 §6); after „Nein“ the offer to lock it out. */
@Composable
internal fun UnknownDeviceDialog(controller: AppController) {
    val offer by controller.lockOutOffer.collectAsState()
    offer?.let { LockOutDialog(controller, it, controller::dismissLockOutOffer) }
    val device by controller.unknownDevice.collectAsState()
    val notMine = rememberLockCheck { controller.answerUnknownDevice(false) }
    val d = device ?: return
    AppDialog(
        onDismissRequest = {},
        title = { Text(texts.devices.unknownTitle(d.name.ifBlank { texts.devices.unnamed })) },
        text = { Text(texts.devices.unknownText) },
        confirmButton = { AppButton(texts.devices.unknownYes, { controller.answerUnknownDevice(true) }, kind = ButtonKind.Primary) },
        dismissButton = { AppButton(texts.devices.unknownNo, { notMine(texts.devices.removeReason) }, kind = ButtonKind.Danger) },
    )
}

/**
 * Runs [action] once the device lock is confirmed, and also when there is no device lock (nothing to protect then,
 * and the owner must not be stuck); nothing on cancel. For steps that act on the person's other devices (ADR 0042):
 * someone holding an unlocked phone must not lock its owner out.
 */
@Composable
internal fun rememberLockCheck(action: () -> Unit): (reason: String) -> Unit {
    val check = rememberDeviceCheck { ok -> if (ok != false) action() }
    return check
}

/**
 * The browser (an own device without its own lock) wants to open the passwords (ADR 0059 §6). „Öffnen“ only after the
 * device lock of this phone; without a device lock it does not work.
 */
@Composable
internal fun PasswordRequestDialog(controller: AppController) {
    val request by controller.passwordRequest.collectAsState()
    val r = request ?: return
    var error by remember(r) { mutableStateOf<String?>(null) }
    val check = rememberDeviceCheck { ok ->
        when (ok) {
            true -> controller.answerPasswordRequest(true)
            null -> error = texts.devices.addNeedsLock
            false -> Unit
        }
    }
    AppDialog(
        onDismissRequest = {},
        title = { Text(texts.devices.passwordRequestTitle(r.deviceName.ifBlank { texts.devices.unnamed })) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(texts.devices.passwordRequestText)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { AppButton(texts.devices.passwordRequestYes, { check(texts.devices.passwordRequestReason) }, kind = ButtonKind.Primary) },
        dismissButton = { AppButton(texts.devices.passwordRequestNo, { controller.answerPasswordRequest(false) }, kind = ButtonKind.Quiet) },
    )
}

/**
 * This device was removed by another own one: the deletion is already scheduled (ADR 0046) and happens on the
 * next opening; „Jetzt löschen“ restarts the app right away.
 */
@Composable
internal fun RevokedScreen(controller: AppController) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(texts.devices.revokedTitle, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(texts.devices.revokedText, textAlign = TextAlign.Center)
        if (controller.resetAvailable) AppButton(texts.devices.revokedDelete, controller::resetThisDevice, kind = ButtonKind.Danger)
    }
}

/** „Alle Daten löschen“ waits until every leave and revocation is at a relay (ADR 0046); no way around it. */
@Composable
internal fun EraseWaitingScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(texts.settings.eraseWaitingTitle, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(texts.settings.eraseWaitingText, textAlign = TextAlign.Center)
    }
}

/**
 * „Ich habe anynear schon“ during setup: with the other device (QR) or with the emergency card.
 */
@Composable
internal fun ReturningDialog(controller: AppController, onDismiss: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    when (page) {
        1 -> PairingDialog(controller, onDismiss)
        2 -> RecoverDialog(controller, onDismiss)
        else -> AppDialog(
            onDismissRequest = onDismiss,
            title = { Text(texts.devices.returningTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppRow(texts.devices.returningOtherDevice, subtitle = texts.devices.returningOtherDeviceText, icon = AppIcons.QrCode, chevron = true, onClick = { page = 1 })
                    AppRow(texts.devices.returningCard, subtitle = texts.devices.returningCardText, icon = AppIcons.Schluessel, chevron = true, onClick = { page = 2 })
                }
            },
            confirmButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
        )
    }
}

/** New device: show the code and wait for the handover from the other device; afterwards the app restarts. */
@Composable
private fun PairingDialog(controller: AppController, onDismiss: () -> Unit) {
    // Prefilled like the web app names itself; the same name is used when the field is left empty.
    var name by remember { mutableStateOf(platformDeviceName()) }
    var link by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.devices.pairingTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val l = link
                if (l == null) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(texts.devices.newDeviceName, maxLines = 1) },
                        placeholder = { Text(texts.devices.newDeviceNameHint) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(texts.devices.pairingText)
                    QrCode(l)
                    code?.let {
                        Text(texts.devices.pairingCodeText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Text(it, style = MaterialTheme.typography.displaySmall)
                    }
                    Text(texts.devices.pairingWaiting, style = MaterialTheme.typography.bodySmall)
                    CircularProgressIndicator()
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (link == null) {
                AppButton(texts.common.next, {
                    scope.launch {
                        link = controller.pairingOffer(name.trim())
                        code = controller.ownPairingCode()
                        if (!controller.awaitPairing()) {
                            link = null
                            error = texts.devices.pairingTimeout
                        }
                    }
                }, kind = ButtonKind.Primary)
            }
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/** Restore with the code of the emergency card; afterwards the app restarts. */
@Composable
private fun RecoverDialog(controller: AppController, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.devices.recoverTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(texts.devices.recoverText)
                CardCodeField({ code = it; error = null })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) CircularProgressIndicator()
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            AppButton(texts.devices.recoverButton, {
                scope.launch {
                    busy = true
                    error = when (controller.recover(code)) {
                        is RecoverResult.Recovered -> null
                        RecoverResult.Invalid -> texts.devices.recoverInvalid
                        RecoverResult.NotFound -> texts.devices.recoverNotFound
                        RecoverResult.Later -> texts.devices.recoverLater
                    }
                    busy = false
                }
            }, kind = ButtonKind.Primary, modifier = Modifier.fillMaxWidth())
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}
