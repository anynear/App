package app.anynear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import app.anynear.AppConfig
import app.anynear.app.AppController
import app.anynear.mesh.Conversation
import app.anynear.app.SupportKind
import app.anynear.app.AppUpdateState
import app.anynear.app.BackgroundRestriction
import app.anynear.app.RelayState
import app.anynear.i18n.texts
import app.anynear.i18n.Language
import app.anynear.i18n.currentLanguage
import app.anynear.mesh.DeviceSetupCheck
import app.anynear.mesh.LinkState
import app.anynear.mesh.MeshLink
import app.anynear.mesh.PeerId
import kotlinx.coroutines.launch

internal enum class ConnectionsPage { Main, Internet, Radio, RadioDetail }

/** Title of the hint in the ⋮ menu if there is something to do under „Über anynear“; otherwise `null`. */
internal fun updateHint(state: AppUpdateState): String? = when (state) {
    is AppUpdateState.Available -> texts.settings.newVersionAvailable(state.version.ifBlank { null })
    is AppUpdateState.NeedsPermission -> texts.settings.newVersionAvailable(state.version.ifBlank { null })
    is AppUpdateState.MovedToNewApp -> texts.appMove.movedRow
    is AppUpdateState.ReinstallNeeded -> texts.appMove.reinstallRow
    else -> null
}

/**
 * „Mein Profil“: name and picture (apply in all circles) and your own contact. The same page from the ⋮ menu.
 * No message to yourself.
 */
@Composable
internal fun ProfileScreen(controller: AppController, onBack: () -> Unit) {
    val name by controller.ownName.collectAsState()
    val image by controller.ownImage.collectAsState()
    val myDevice by controller.myDeviceId.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        AppHeader(texts.settings.myProfile, leading = { HeaderBackButton(onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box {
                    myDevice?.let { PersonAvatar(controller, PeerId.device(it), image, 112.dp, enlargeable = true) }
                    CameraBadge(Modifier.align(Alignment.BottomEnd))
                }
                Text(
                    name.ifBlank { texts.settings.yourName },
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(texts.settings.profileSubtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            AppButton(texts.settings.editProfile, { editing = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = AppIcons.Stift)
            // The same dialog as under „Kontakt hinzufügen“: QR code, share link, copy.
            AppSection(texts.people.myContact, footer = texts.people.ownContactText) {
                SectionContent {
                    AppButton(texts.people.shareMyContact, { sharing = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Main, icon = AppIcons.Teilen)
                    Text(texts.settings.shareContactHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (editing) EditProfileDialog(controller, onDismiss = { editing = false })
    if (sharing) OwnCodeDialog(controller, onDismiss = { sharing = false })
}

/**
 * Connections and radio: internet and radio with their state; the radio has its own subpages
 * (connect, manage). It is only an additional sending route, not an identity.
 */
@Composable
internal fun ConnectionsScreen(controller: AppController, onBack: () -> Unit, start: ConnectionsPage = ConnectionsPage.Main) {
    val paired by controller.pairedDevice.collectAsState()
    var page by remember { mutableStateOf(start) }

    // Radio released or forgotten while its page is open: back to the overview of the radios.
    LaunchedEffect(paired) {
        if (paired == null && page == ConnectionsPage.RadioDetail) page = ConnectionsPage.Radio
    }
    // The page of the own radio leads back to „Funkgeräte“; came directly from „Mehr“ to a subpage: back leads there again.
    val back: () -> Unit = {
        when {
            page == ConnectionsPage.RadioDetail -> page = ConnectionsPage.Radio
            page != start -> page = start
            else -> onBack()
        }
    }
    SystemBackHandler(enabled = page != start, onBack = back)

    Column(Modifier.fillMaxSize()) {
        when (page) {
            ConnectionsPage.Radio -> {
                AppHeader(texts.settings.radios, leading = { HeaderBackButton(back) })
                RadiosPage(controller, onOpenPaired = { page = ConnectionsPage.RadioDetail })
            }
            ConnectionsPage.RadioDetail -> {
                AppHeader(texts.settings.radio, leading = { HeaderBackButton(back) })
                RadioPage(controller)
            }
            ConnectionsPage.Internet -> {
                AppHeader(texts.common.internet, leading = { HeaderBackButton(back) })
                InternetPage(controller)
            }
            ConnectionsPage.Main -> {
                AppHeader(texts.settings.connections, leading = { HeaderBackButton(onBack) })
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ConnectionSection(
                        controller,
                        onOpenInternet = { page = ConnectionsPage.Internet },
                        onOpenRadio = { page = ConnectionsPage.Radio },
                    )
                }
            }
        }
    }
}

/** Settings: appearance, language (follows the phone), notifications, emergency card, advanced with troubleshooting. */
@Composable
internal fun SettingsPageScreen(controller: AppController, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppHeader(texts.settings.title, leading = { HeaderBackButton(onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AppSection(texts.settings.appearance) { DarkModeRow(controller) }
            // Only explanatory: there is no own language choice or jump into the system setting.
            AppSection(texts.settings.language, footer = texts.settings.languageFooter) {
                AppRow(title = texts.settings.language, subtitle = texts.settings.languageCurrent, icon = AppIcons.Internet)
            }
            NotificationSection(controller)
            AppSection { EmergencyCardRow(controller) }
            LocationSection(controller)
            PrivacySection(controller)
            // Rarely needed and cannot be undone: at the very bottom, each with a confirmation (ADR 0046).
            if (controller.resetAvailable) StartOverSection(controller)
        }
    }
}

/** Privacy: usage statistics on or off and what was sent last (ADR 0045). */
@Composable
private fun PrivacySection(controller: AppController) {
    val on by controller.usageStatsOn.collectAsState()
    val last by controller.usageStatsLast.collectAsState()
    var confirmOff by remember { mutableStateOf(false) }
    // Switching off only after a confirmation: the switch is quickly flipped by accident.
    fun switch(to: Boolean) = if (to) controller.setUsageStats(true) else confirmOff = true
    AppSection(texts.settings.privacy) {
        AppRow(
            title = texts.settings.usageStats,
            subtitle = when {
                controller.usageStatsTestMode -> texts.settings.usageStatsTestMode
                on -> texts.settings.usageStatsOn
                else -> texts.settings.usageStatsOff
            },
            icon = AppIcons.Info,
            trailing = { Switch(checked = on, onCheckedChange = ::switch) },
            onClick = { switch(!on) },
        )
        AppRow(
            title = texts.settings.usageStatsSent,
            subtitle = last?.let { texts.settings.usageStatsReport(it.devices, it.radios, it.circles, it.channels, it.nina, it.platform, it.version) }
                ?: texts.settings.usageStatsNone,
            icon = AppIcons.Dokument,
        )
    }
    if (confirmOff) {
        AppDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text(texts.settings.usageStatsOffTitle) },
            text = { Text(texts.settings.usageStatsOffText) },
            confirmButton = {
                AppButton(texts.settings.usageStatsKeep, { confirmOff = false })
            },
            dismissButton = {
                AppButton(texts.settings.usageStatsOffConfirm, {
                    confirmOff = false
                    controller.setUsageStats(false)
                }, kind = ButtonKind.Quiet)
            },
        )
    }
}

/** „Neu anfangen“: reset the device (only here) or delete all data (here and in the network). */
@Composable
private fun StartOverSection(controller: AppController) {
    var dialog by remember { mutableStateOf<StartOver?>(null) }
    // Opened via the link https://anynear.app/s#erase: show the confirmation right away (deletes only after „Alles löschen“).
    val eraseRequest by controller.eraseRequest.collectAsState()
    LaunchedEffect(eraseRequest) {
        if (eraseRequest) {
            dialog = StartOver.Erase
            controller.consumeEraseRequest()
        }
    }
    AppSection(texts.settings.startOver) {
        AppRow(texts.settings.resetDevice, subtitle = texts.settings.resetDeviceHint, icon = AppIcons.Aktualisieren, danger = true, onClick = { dialog = StartOver.Reset })
        AppRow(texts.settings.eraseAll, subtitle = texts.settings.eraseAllHint, icon = AppIcons.Achtung, danger = true, onClick = { dialog = StartOver.Erase })
    }
    dialog?.let { StartOverDialog(controller, it) { dialog = null } }
}

private enum class StartOver { Reset, Erase }

@Composable
private fun StartOverDialog(controller: AppController, kind: StartOver, onDismiss: () -> Unit) {
    var details by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val erase = kind == StartOver.Erase
    // Deleting everything also removes the other own devices: only after the device lock.
    val confirmErase = app.anynear.ui.rememberLockCheck {
        scope.launch {
            busy = true
            error = null
            error = controller.eraseEverything()
            busy = false
        }
    }
    AppDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (erase) texts.settings.eraseTitle else texts.settings.resetTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (erase) texts.settings.eraseText else texts.settings.resetText)
                if (!erase && controller.resetLosesPerson) Text(texts.settings.resetLosesPerson, color = MaterialTheme.colorScheme.error)
                if (details) Text(if (erase) texts.settings.eraseMore else texts.settings.resetMore, style = MaterialTheme.typography.bodySmall)
                else AppButton(texts.common.moreInfo, { details = true }, kind = ButtonKind.Quiet)
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(texts.settings.erasing)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            AppButton(if (erase) texts.settings.eraseConfirm else texts.settings.resetConfirm, {
                if (!erase) controller.resetThisDevice()
                else confirmErase(texts.settings.eraseReason)
            }, kind = ButtonKind.Danger, enabled = !busy)
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet, enabled = !busy) },
    )
}

/** Help: explanations that already exist in the app, and a question to the support (as its own chat). */
@Composable
internal fun HelpScreen(controller: AppController, onBack: () -> Unit, onOpenChat: (Conversation) -> Unit) {
    var open by rememberSaveable { mutableStateOf(-1) }
    Column(Modifier.fillMaxSize()) {
        AppHeader(texts.settings.help, leading = { HeaderBackButton(onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AppSection(texts.settings.helpInShort, footer = texts.settings.helpMoreNotAvailable) {
                listOf(
                    texts.settings.helpWhatIsCircle to texts.people.circleExplained,
                    texts.settings.helpWhatIsContactCode to texts.people.ownContactText,
                    texts.settings.helpNeedRadio to texts.settings.connectRadioHint,
                    texts.settings.helpHowMessages to texts.settings.helpHowMessagesAnswer,
                    texts.settings.helpInternetDown to texts.settings.helpInternetDownAnswer,
                ).forEachIndexed { i, (question, answer) ->
                    ExpandRow(question, expanded = open == i) { open = if (open == i) -1 else i }
                    if (open == i) {
                        Text(answer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
            SupportFormSection(controller, SupportKind.Question, onOpenChat)
        }
    }
}

/** About anynear: version, update, license, legal pages. */
@Composable
internal fun AboutScreen(controller: AppController, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppHeader(texts.settings.aboutApp, leading = { HeaderBackButton(onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OldAppSection(controller)
            AppUpdateSection(controller)
            LegalSection()
        }
    }
}

/** Legal notice, privacy and terms: the pages of the website in the language of the app, as in the web app. */
@Composable
private fun LegalSection() {
    val uri = LocalUriHandler.current
    val german = currentLanguage() == Language.GERMAN
    AppSection(texts.settings.legal, footer = texts.settings.legalFooter) {
        listOf(
            Triple(texts.settings.legalNotice, AppIcons.Info, if (german) AppConfig.LEGAL_NOTICE_URL_DE else AppConfig.LEGAL_NOTICE_URL_EN),
            Triple(texts.settings.privacy, AppIcons.Schloss, if (german) AppConfig.PRIVACY_URL_DE else AppConfig.PRIVACY_URL_EN),
            Triple(texts.settings.terms, AppIcons.Dokument, if (german) AppConfig.TERMS_URL_DE else AppConfig.TERMS_URL_EN),
        ).forEach { (title, icon, url) ->
            AppRow(title = title, icon = icon, chevron = true, onClick = { runCatching { uri.openUri(url) } })
        }
    }
}

/**
 * Notifications: the permission of the system (no own muting) and, if Android would put the app
 * to sleep, the way to the battery setting. Both existed so far only as a one-time hint.
 */
@Composable
private fun NotificationSection(controller: AppController) {
    val needed by controller.notificationPermissionNeeded.collectAsState()
    val restriction by controller.backgroundRestriction.collectAsState()
    AppSection(texts.settings.notifications) {
        AppRow(
            title = texts.settings.notificationsOnOff,
            subtitle = if (needed) texts.settings.notificationsNotAllowed else texts.settings.notificationsInPhoneSettings,
            icon = AppIcons.Nachrichten,
            chevron = true,
            onClick = controller::openNotificationSettings,
        )
        if (restriction != null) {
            AppRow(
                title = texts.setup.backgroundTitle,
                subtitle = if (restriction == BackgroundRestriction.STOPPED_IN_BACKGROUND) texts.settings.backgroundStopped else texts.settings.backgroundRestricted,
                icon = AppIcons.Akku,
                chevron = true,
                onClick = controller::openBackgroundSettings,
            )
        }
    }
}

/** Small camera at the bottom right of the picture: the photo can be changed here. */
@Composable
internal fun CameraBadge(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(24.dp)
            .background(scheme.primary, CircleShape)
            .border(2.dp, scheme.surfaceContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Kamera, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(13.dp))
    }
}

/** Change name and picture, built like „Kreis bearbeiten“. */
@Composable
private fun EditProfileDialog(controller: AppController, onDismiss: () -> Unit) {
    val saved by controller.ownName.collectAsState()
    val image by controller.ownImage.collectAsState()
    val myDevice by controller.myDeviceId.collectAsState()
    var name by remember(saved) { mutableStateOf(saved) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pickPhoto = rememberPhotoPicker(onError = { error = it }) { bytes ->
        scope.launch {
            busy = true
            error = controller.setOwnImage(bytes)
            busy = false
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.settings.editProfileTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoPickerRow(
                    avatar = { myDevice?.let { PersonAvatar(controller, PeerId.device(it), image, 72.dp, enlargeable = true) } },
                    busy = busy,
                    hasPhoto = image != null,
                    onPick = pickPhoto,
                    onReset = controller::resetOwnImage,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = OwnNameLimit.accept(name, it); error = null },
                    singleLine = true,
                    label = { Text(texts.settings.yourName) },
                    supportingText = OwnNameLimit.hint(name)?.let { { TextLimitHint(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(texts.settings.nameAppliesEverywhere, style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            AppButton(texts.common.done, enabled = name.isNotBlank(), onClick = {
                if (name.trim() != saved) controller.setOwnName(name)
                onDismiss()
            })
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/** Picture on the left, on the right „Foto wählen“ and possibly „Standardbild“. For yourself and for circles. */
@Composable
internal fun PhotoPickerRow(
    avatar: @Composable () -> Unit,
    busy: Boolean,
    hasPhoto: Boolean,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        avatar()
        Column {
            AppButton(
                if (busy) texts.settings.preparingPhoto else texts.settings.choosePhoto,
                onPick,
                kind = ButtonKind.Quiet,
                icon = AppIcons.Kamera,
                enabled = !busy,
            )
            if (hasPhoto) AppButton(texts.settings.defaultPhoto, onReset, kind = ButtonKind.Quiet, icon = AppIcons.Schliessen)
        }
    }
}

@Composable
private fun ConnectionSection(controller: AppController, onOpenInternet: () -> Unit, onOpenRadio: () -> Unit) {
    val online by controller.onlineReachable.collectAsState()
    val internet by controller.internetAvailable.collectAsState()
    val paired by controller.pairedDevice.collectAsState()
    val setupNeeded by controller.deviceSetupNeeded.collectAsState()

    AppSection(texts.settings.internetAndRadio, footer = if (online) null else texts.settings.offlineFooter) {
        AppRow(
            title = texts.common.internet,
            subtitle = internetLabel(online, internet),
            icon = AppIcons.Internet,
            trailing = { StatusDot(online) },
            chevron = true,
            onClick = onOpenInternet,
        )
        val device = paired
        if (device == null) {
            AppRow(
                title = texts.settings.connectRadio,
                subtitle = texts.settings.connectRadioHint,
                icon = AppIcons.Funkgeraet,
                chevron = true,
                onClick = onOpenRadio,
            )
        } else {
            val (status, connected) = pairedRadioStatus(controller)
            AppRow(
                title = pairedRadioName(controller, device),
                subtitle = status,
                icon = AppIcons.Funkgeraet,
                trailing = { StatusDot(connected && setupNeeded == null) },
                chevron = true,
                onClick = onOpenRadio,
            )
        }
    }
}

private fun internetLabel(online: Boolean, internet: Boolean) = when {
    online -> texts.settings.connected
    internet -> texts.settings.appUnreachable
    else -> texts.settings.noInternet
}

/**
 * Subpage „Internet“: state and per mailbox whether the app currently reaches it (ID like
 * fr-par-01, as far as the list names it, ADR 0028). Built like the page „Funkgerät“.
 */
@Composable
private fun InternetPage(controller: AppController) {
    val online by controller.onlineReachable.collectAsState()
    val internet by controller.internetAvailable.collectAsState()
    val relays by controller.relayStatuses.collectAsState()
    val reached = relays.count { it.state == RelayState.REACHED }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AppSection(footer = if (online) null else texts.settings.offlineFooter) {
            AppRow(
                title = internetLabel(online, internet),
                subtitle = texts.settings.mailboxesReached(reached, relays.size),
                icon = AppIcons.Internet,
                trailing = { StatusDot(online) },
            )
        }
        AppSection(texts.settings.mailboxes, footer = texts.settings.mailboxesFooter) {
            relays.forEach { relay ->
                AppRow(
                    title = relay.label.ifBlank { texts.settings.mailbox },
                    subtitle = listOf(relay.hostPort.substringBeforeLast(':'), relay.state.label()).joinToString(" · "),
                    trailing = { StatusDot(relay.state == RelayState.REACHED) },
                )
            }
        }
    }
}

private fun RelayState.label() = when (this) {
    RelayState.REACHED -> texts.settings.relayReached
    RelayState.UNREACHABLE -> texts.settings.relayUnreachable
    RelayState.UNUSED -> texts.settings.relayUnused
}

/** Key of the limit for the group check across all contacts (ADR 0053). */
private const val RADIO_CHECK_ALL = "all"

/**
 * Subpage „Funkgerät“ of the assigned radio: state, disconnect/connect, battery, software, radio check, advanced.
 * Help with the connection and switching to another radio are on the overview „Funkgeräte“.
 */
@Composable
private fun RadioPage(controller: AppController) {
    val paired by controller.pairedDevice.collectAsState()
    val state by controller.linkState.collectAsState()
    val own by controller.ownDevice.collectAsState()
    val internetOnly by controller.internetOnlyCircles.collectAsState()
    val busy by controller.deviceBusy.collectAsState()
    val servedByPatron by controller.servedByPatron.collectAsState()
    var advanced by remember { mutableStateOf(false) }
    val disconnected by controller.radioDisconnected.collectAsState()
    val device = paired ?: return
    val connected = state == LinkState.Connected && !disconnected
    val (status, _) = pairedRadioStatus(controller)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AppSection(
            footer = when {
                disconnected -> texts.settings.radioDisconnectedHint
                busy -> texts.settings.radioApplyingHint
                else -> state.hint(appName)
            },
        ) {
            AppRow(
                title = pairedRadioName(controller, device),
                subtitle = status,
                icon = AppIcons.Funkgeraet,
                muted = !connected && !busy,
                trailing = { StatusDot(connected) },
            )
            // Disconnect without forgetting; afterwards it says „Verbinden“ here.
            if (disconnected) {
                AppRow(title = texts.settings.reconnectRadio, icon = AppIcons.Funk, chevron = true, onClick = controller::reconnectRadio)
            } else {
                AppRow(title = texts.settings.disconnectRadio, icon = AppIcons.Verlassen, onClick = controller::disconnectRadio)
            }
            own?.batteryLevel?.let { level ->
                AppRow(title = texts.settings.battery, subtitle = if (level > 100) texts.settings.charging else texts.settings.batteryLevel(level), icon = AppIcons.Akku)
            }
            // Without internet another anynear device nearby has taken over the delivery (ADR 0051).
            if (servedByPatron && connected) AppRow(title = texts.settings.servedByPatron, icon = AppIcons.Funk)
        }

        PairingWaitingSection(controller)
        RadioHelpSection(controller)

        // How it is used and switching without cable (ADR 0094 §3).
        WayOfUseSection(controller)

        // New software (ADR 0056): stays visible here, even after „Später“.
        RadioSoftwareSection(controller)

        // Group check (ADR 0053) across all contacts with a known radio.
        val people by controller.people.collectAsState()
        val messages by controller.messages.collectAsState()
        val radios = remember(people, messages) { people.flatMap { controller.knownRadios(it.peer) }.distinct() }
        RadioCheckGroupSection(controller, RADIO_CHECK_ALL, radios)

        // More circles than slots (ADR 0038): no problem, only a hint. The app distributes by itself.
        if (internetOnly.isNotEmpty()) {
            val otherSlots by controller.otherRadioChannelSlots.collectAsState()
            AppSection(footer = texts.settings.circlesInternetOnlyHint(MeshLink.CIRCLE_SLOTS - otherSlots)) {
                AppRow(title = texts.settings.circlesInternetOnly(internetOnly.size), icon = AppIcons.Internet)
            }
        }

        // Public radio of the area (ADR 0055), default on.
        controller.publicRadio?.let { publicRadio ->
            val publicShown by publicRadio.shown.collectAsState()
            AppSection {
                AppRow(
                    title = texts.publicRadio.toggle,
                    subtitle = if (publicShown) texts.publicRadio.toggleOn else texts.publicRadio.toggleOff,
                    icon = AppIcons.Funk,
                    trailing = { Switch(checked = publicShown, onCheckedChange = publicRadio::setShown) },
                    onClick = { publicRadio.setShown(!publicShown) },
                )
                // Radio community (ADR 0054 §1a): change the answer to the offer later; only once it has been asked.
                val community by publicRadio.community.collectAsState()
                community?.let { on ->
                    AppRow(
                        title = texts.publicRadio.community(AppConfig.MeshHessen.NAME),
                        subtitle = if (on) texts.publicRadio.communityOn else texts.publicRadio.communityOff,
                        icon = AppIcons.Funk,
                        trailing = { Switch(checked = on, onCheckedChange = publicRadio::setCommunity) },
                        onClick = { publicRadio.setCommunity(!on) },
                    )
                }
            }
        }

        // Shared radio (ADR 0021): what comes over radio is received only by whoever's phone is currently connected.
        AppSection(footer = texts.settings.sharedRadioFooter) {
            ExpandRow(texts.common.advanced, expanded = advanced, icon = AppIcons.Regler) { advanced = !advanced }
            if (advanced) RadioAdvanced(controller, connected = connected)
        }
    }
}
