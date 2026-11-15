package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.app.BackgroundRestriction
import app.anynear.app.CommunityOffer
import app.anynear.mesh.LinkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import app.anynear.i18n.texts
import kotlinx.coroutines.launch

/** Steps of the guided setup after the name (ADR 0073), in this order. */
internal enum class GuideStep { EmergencyCard, Notifications, Background, Alerts, DoNotDisturb, Radio }

/**
 * Which steps currently have something to do on this device. Determined once on entry, so that the
 * step indicator stays stable. [withRadio]: without when starting via a link (ADR 0073 §7).
 */
internal fun guideSteps(controller: AppController, withRadio: Boolean): List<GuideStep> {
    controller.checkBackgroundRestriction()
    val alerts = controller.alerts
    alerts?.checkAccess()
    return planGuide(
        cardMissing = controller.emergencyCardMissing,
        notificationsNeeded = controller.notificationPermissionNeeded.value,
        backgroundRestricted = controller.backgroundRestriction.value != null,
        officialAlertsAvailable = alerts?.curatedLink != null,
        officialAlertsOn = alerts?.curatedChannel?.value != null,
        doNotDisturbMissing = alerts?.accessMissing?.value == true,
        withRadio = withRadio,
        radioPaired = controller.pairedDevice.value != null,
    )
}

/** Selection of the steps (ADR 0073 §2): what is done or what the system does not ask is dropped. */
internal fun planGuide(
    cardMissing: Boolean,
    notificationsNeeded: Boolean,
    backgroundRestricted: Boolean,
    officialAlertsAvailable: Boolean,
    officialAlertsOn: Boolean,
    doNotDisturbMissing: Boolean,
    withRadio: Boolean,
    radioPaired: Boolean,
): List<GuideStep> = buildList {
    // ADR 0093: the emergency card first, so that the root key leaves the phone before anything else happens.
    if (cardMissing) add(GuideStep.EmergencyCard)
    if (notificationsNeeded) add(GuideStep.Notifications)
    if (backgroundRestricted) add(GuideStep.Background)
    if (officialAlertsAvailable && !officialAlertsOn) add(GuideStep.Alerts)
    // Android: alarms only get through „Nicht stören“ with access; asked here instead of a hint right afterwards.
    if (doNotDisturbMissing) add(GuideStep.DoNotDisturb)
    if (withRadio && !radioPaired) add(GuideStep.Radio)
}

/** One step of the guided setup. [onDone] leads on, no matter what was decided. */
@Composable
internal fun GuideStepScreen(
    controller: AppController,
    step: GuideStep,
    stepLabel: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    when (step) {
        GuideStep.EmergencyCard -> EmergencyCardStep(controller, stepLabel, onDone, onBack)
        GuideStep.Notifications -> PermissionStep(
            icon = AppIcons.Glocke,
            stepLabel = stepLabel,
            title = texts.setup.notificationsTitle,
            text = texts.setup.notificationsText,
            action = texts.setup.notificationsAllow,
            onAction = {
                controller.allowNotifications()
                onDone()
            },
            onLater = {
                // Decided here: the card in messages does not ask again right after the setup.
                controller.postponeNotificationHint()
                onDone()
            },
            onBack = onBack,
        )
        GuideStep.Background -> {
            val restriction by controller.backgroundRestriction.collectAsState()
            // Back from the system setting the app checks by itself; if everything is clear, it goes on.
            LaunchedEffect(restriction) { if (restriction == null) onDone() }
            val current = restriction ?: return
            // The same sentences as the hint after the setup (BackgroundHintDialog).
            PermissionStep(
                icon = AppIcons.Akku,
                stepLabel = stepLabel,
                title = texts.setup.backgroundTitle,
                text = if (current == BackgroundRestriction.STOPPED_IN_BACKGROUND) texts.setup.backgroundStopped else texts.setup.backgroundText,
                note = when (current) {
                    BackgroundRestriction.BATTERY_OPTIMIZED -> texts.setup.backgroundTapAllow
                    BackgroundRestriction.BACKGROUND_RESTRICTED -> texts.setup.backgroundTapBattery
                    BackgroundRestriction.STOPPED_IN_BACKGROUND -> texts.setup.backgroundTapStopped
                },
                action = texts.setup.openSetting,
                onAction = controller::openBackgroundSettings,
                onLater = {
                    // Decided here: the hint does not come again right after the setup.
                    controller.postponeBackgroundHint()
                    onDone()
                },
                onBack = onBack,
            )
        }
        GuideStep.Alerts -> AlertsStep(controller, stepLabel, onDone, onBack)
        GuideStep.DoNotDisturb -> DoNotDisturbStep(controller, stepLabel, onDone, onBack)
        GuideStep.Radio -> RadioStep(controller, stepLabel, onDone, onBack)
    }
}

/**
 * ADR 0093: the emergency card during setup. Shows code and QR, the person writes it down and types it once (the same
 * dialog as in the settings); „Später“ leaves a card in „Nachrichten“ until it is done.
 */
@Composable
private fun EmergencyCardStep(controller: AppController, stepLabel: String?, onDone: () -> Unit, onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    PermissionStep(
        icon = AppIcons.Schluessel,
        stepLabel = stepLabel,
        title = texts.setup.cardTitle,
        text = texts.setup.cardText,
        note = texts.setup.cardNote,
        action = texts.setup.cardCreate,
        onAction = { open = true },
        onLater = onDone,
        onBack = onBack,
    )
    if (open) EmergencyCardDialog(controller) {
        open = false
        if (!controller.emergencyCardMissing) onDone()
    }
}

@Composable
private fun AlertsStep(controller: AppController, stepLabel: String?, onDone: () -> Unit, onBack: () -> Unit) {
    val alerts = controller.alerts
    if (alerts == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    PermissionStep(
        icon = AppIcons.Achtung,
        stepLabel = stepLabel,
        title = texts.setup.alertsTitle,
        text = texts.setup.alertsText,
        note = texts.setup.alertsLocationNote,
        action = if (failed) texts.common.next else texts.setup.alertsTurnOn,
        busy = busy,
        error = texts.setup.alertsFailed.takeIf { failed },
        onAction = {
            if (failed) {
                onDone()
            } else {
                busy = true
                scope.launch {
                    // Subscribes to the channel and asks for the approximate location (only for the district).
                    val joined = alerts.enableCurated()
                    alerts.clearNotice()
                    busy = false
                    if (joined) onDone() else failed = true
                }
            }
        },
        onLater = onDone.takeIf { !failed },
        onBack = onBack,
    )
}

/** Android: access to „Nicht stören“, so that alarms ring there too. The same sentences as the hint (AlarmAccessDialog). */
@Composable
private fun DoNotDisturbStep(controller: AppController, stepLabel: String?, onDone: () -> Unit, onBack: () -> Unit) {
    val alerts = controller.alerts
    val missing by (alerts?.accessMissing ?: NoAccessMissing).collectAsState()
    // Back from the system setting the app checks by itself (MainActivity.onResume); if access is there, it goes on.
    LaunchedEffect(missing) { if (!missing) onDone() }
    if (alerts == null || !missing) return
    PermissionStep(
        icon = AppIcons.Glocke,
        stepLabel = stepLabel,
        title = texts.alerts.accessTitle,
        text = texts.alerts.accessText,
        action = texts.setup.openSetting,
        onAction = alerts::openAccessSettings,
        onLater = {
            // Decided here: the hint does not come again right after the setup.
            alerts.postponeAccessHint()
            onDone()
        },
        onBack = onBack,
    )
}

private val NoAccessMissing: StateFlow<Boolean> = MutableStateFlow(false)

private enum class RadioPage { Ask, Search, Buy, Connecting, Public, Community }

/** Optional at the end: connect a radio or see where to get one. */
@Composable
private fun RadioStep(controller: AppController, stepLabel: String?, onDone: () -> Unit, onBack: () -> Unit) {
    var page by remember { mutableStateOf(RadioPage.Ask) }
    val paired by controller.pairedDevice.collectAsState()
    val uri = LocalUriHandler.current
    val publicRadio = controller.publicRadio
    val offer by (publicRadio?.offer ?: NoOffer).collectAsState()
    // Paired: wait for the connection, then the questions on public radio (ADR 0073 §6a). Whether the device still
    // has to be set up (radio profile), the app asks afterwards by itself.
    LaunchedEffect(paired) {
        if (paired != null && page == RadioPage.Search) {
            controller.stopScan()
            page = if (publicRadio == null) RadioPage.Ask.also { onDone() } else RadioPage.Connecting
        }
    }
    LaunchedEffect(page) {
        if (page != RadioPage.Connecting) return@LaunchedEffect
        val connected = withTimeoutOrNull(CONNECT_WAIT) { controller.linkState.first { it == LinkState.Connected } } != null
        if (!connected) {
            // Takes too long: go on; switch and offer then come as before on the radio page or as a card.
            onDone()
            return@LaunchedEffect
        }
        // The offer of the radio community needs the channels from the device; they come shortly after the connection.
        withTimeoutOrNull(OFFER_WAIT) { publicRadio?.offer?.first { it != null } }
        page = RadioPage.Public
    }
    fun leaveSearch() {
        controller.stopScan()
        page = RadioPage.Ask
    }
    // After connecting there is no way back to the search; the device is paired.
    SystemBackHandler(enabled = page == RadioPage.Search || page == RadioPage.Buy) { if (page == RadioPage.Search) leaveSearch() else page = RadioPage.Ask }
    when (page) {
        RadioPage.Ask -> {
            StepHeader(AppIcons.Funkgeraet, stepLabel, texts.setup.radioTitle)
            Text(texts.setup.radioText, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            AppButton(texts.setup.radioYes, { page = RadioPage.Search }, Modifier.fillMaxWidth(), kind = ButtonKind.Main)
            AppButton(texts.setup.radioNotYet, { page = RadioPage.Buy }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            AppButton(texts.common.later, onDone, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet)
            GuideBackButton(onBack)
        }
        RadioPage.Search -> {
            Text(texts.setup.radioSearchTitle, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            // The same search as under settings; only it asks for Bluetooth.
            Box(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 560.dp)) { SetupScreen(controller, inset = false) }
            AppButton(texts.common.later, { controller.stopScan(); onDone() }, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet)
            GuideBackButton(::leaveSearch)
        }
        RadioPage.Buy -> {
            StepHeader(AppIcons.Funkgeraet, null, texts.setup.radioBuyTitle)
            Text(texts.setup.radioBuyText, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            AppButton(
                texts.setup.radioBuyLink,
                { runCatching { uri.openUri(texts.setup.radiosPageUrl) } },
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Secondary,
                icon = AppIcons.Link,
            )
            AppButton(texts.setup.radioContinueWithout, onDone, Modifier.fillMaxWidth(), kind = ButtonKind.Main)
            GuideBackButton { page = RadioPage.Ask }
        }
        RadioPage.Connecting -> {
            StepHeader(AppIcons.Funkgeraet, null, texts.setup.radioConnecting)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(32.dp)) }
            // Pairing waits for the number from the display: say so and offer the way to the Bluetooth settings.
            PairingWaitingSection(controller)
            AppButton(texts.common.later, onDone, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet)
        }
        RadioPage.Public -> {
            StepHeader(AppIcons.Funk, null, texts.setup.publicTitle)
            Text(texts.setup.publicText, style = MaterialTheme.typography.bodyLarge)
            Text(texts.setup.publicNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            AppButton(texts.setup.publicYes, {
                publicRadio?.setShown(true)
                if (offer != null) page = RadioPage.Community else onDone()
            }, Modifier.fillMaxWidth(), kind = ButtonKind.Main)
            AppButton(texts.setup.publicNo, {
                publicRadio?.setShown(false)
                // Without public chats the radio community's chat would be invisible too: do not ask about it any more.
                if (offer != null) publicRadio?.declineCommunity()
                onDone()
            }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
        }
        RadioPage.Community -> {
            val current = offer
            if (current == null) {
                LaunchedEffect(Unit) { onDone() }
                return
            }
            // The same sentences as the card in the overview (ADR 0054 §1a).
            StepHeader(AppIcons.Funk, null, texts.publicRadio.offerTitle(current.name))
            if (current.freeSlots > 0) {
                Text(texts.publicRadio.offerText(current.name, current.freeSlots), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                AppButton(texts.publicRadio.offerYes, { publicRadio?.acceptCommunity(); onDone() }, Modifier.fillMaxWidth(), kind = ButtonKind.Main)
                AppButton(texts.publicRadio.offerNo, { publicRadio?.declineCommunity(); onDone() }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            } else {
                Text(texts.publicRadio.offerNoSlot(current.name), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                AppButton(texts.common.next, { publicRadio?.dismissNoSlot(); onDone() }, Modifier.fillMaxWidth(), kind = ButtonKind.Main)
            }
            GuideBackButton { page = RadioPage.Public }
        }
    }
}

private val NoOffer: StateFlow<CommunityOffer?> = MutableStateFlow(null)
private val CONNECT_WAIT = 30.seconds
private val OFFER_WAIT = 5.seconds

/** Permission step: explain first, then the system asks (after the main button). „Später“ is always there. */
@Composable
private fun PermissionStep(
    icon: ImageVector,
    stepLabel: String?,
    title: String,
    text: String,
    action: String,
    onAction: () -> Unit,
    onLater: (() -> Unit)?,
    onBack: () -> Unit,
    note: String? = null,
    busy: Boolean = false,
    error: String? = null,
) {
    StepHeader(icon, stepLabel, title)
    Text(text, style = MaterialTheme.typography.bodyLarge)
    note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    Spacer(Modifier.height(8.dp))
    AppButton(action, onAction, Modifier.fillMaxWidth(), kind = ButtonKind.Main, enabled = !busy)
    onLater?.let { AppButton(texts.common.later, it, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet, enabled = !busy) }
    GuideBackButton(onBack)
}

@Composable
private fun StepHeader(icon: ImageVector, stepLabel: String?, title: String) {
    Spacer(Modifier.height(16.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        stepLabel?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun GuideBackButton(onClick: () -> Unit) {
    AppButton(texts.common.back, onClick, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet, icon = AppIcons.Zurueck)
}
