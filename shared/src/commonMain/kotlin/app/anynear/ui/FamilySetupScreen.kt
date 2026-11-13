package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.mesh.PeerId
import kotlinx.coroutines.launch

private enum class SetupStep { Welcome, Name, Guide, Choice }

/** Start via a contact or invitation link: entry, name, confirmation. */
private enum class LinkStep { Start, Name, Guide, Confirm }

/**
 * First start as a guided setup, one question per screen (UX concept 3.7, ADR 0073):
 * 1. Welcome 2. Name with an optional picture 3. Notifications, background, official warnings and optionally
 * the radio, as far as there is something to do on this device ([GuideStep]) 4. What to start with: only two ways,
 * share the own contact (system share menu) or look around. Circles, invitations and contacts come later in the app;
 * an invitation link opens the setup on its own. A circle and a radio are not a prerequisite, every step has „Später“.
 * If the app came via a link, entry, name, the same steps without the radio and a confirmation follow; only the
 * confirmation adds or joins.
 */
@Composable
internal fun FamilySetupScreen(controller: AppController, modifier: Modifier = Modifier) {
    val invite by controller.pendingInvite.collectAsState()
    val contact by controller.pendingContact.collectAsState()
    val savedName by controller.ownName.collectAsState()
    val ownContactLink by controller.ownContactLink.collectAsState()
    var step by rememberSaveable { mutableStateOf(SetupStep.Welcome) }
    var name by rememberSaveable { mutableStateOf(savedName) }
    var error by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf(false) }
    // Steps after the name (ADR 0073): determined when leaving the name, so that „Schritt x von y“ stays stable.
    var guide by rememberSaveable { mutableStateOf("") }
    var guideIndex by rememberSaveable { mutableStateOf(0) }
    val guideSteps = guide.split(',').filter { it.isNotEmpty() }.map(GuideStep::valueOf)
    // Preview for the step indicator at the name, before the steps are fixed.
    // On iOS the answer whether notifications are already allowed only comes shortly after the start.
    val notificationsNeeded by controller.notificationPermissionNeeded.collectAsState()
    val expectedGuide = remember(notificationsNeeded) { guideSteps(controller, withRadio = true).size }
    val total = 3 + guideSteps.size

    // New link: whoever already has a name and is just choosing goes straight to the confirmation.
    val linkKey = invite?.circleId ?: contact?.url
    var linkStep by remember(linkKey) {
        mutableStateOf(if (savedName.isNotBlank() && step >= SetupStep.Choice) LinkStep.Confirm else LinkStep.Start)
    }

    fun go(next: SetupStep) {
        error = null
        step = next
    }

    /** After the name: determine the steps and go to the first, otherwise straight on. */
    fun startGuide(withRadio: Boolean): Boolean {
        val steps = guideSteps(controller, withRadio)
        guide = steps.joinToString(",") { it.name }
        guideIndex = 0
        return steps.isNotEmpty()
    }

    /** Last step before the end of the setup or the confirmation (back). */
    fun lastGuide(): Boolean {
        if (guideSteps.isEmpty()) return false
        guideIndex = guideSteps.lastIndex
        return true
    }

    fun saveName(): Boolean {
        if (name.isBlank()) return false
        controller.setOwnName(name)
        return true
    }

    fun dismissLink() {
        error = null
        if (invite != null) controller.dismissPendingInvite() else controller.dismissPendingContact()
        step = if (controller.ownName.value.isBlank()) SetupStep.Welcome else SetupStep.Choice
    }

    val back: () -> Unit = when {
        linkKey != null -> when (linkStep) {
            LinkStep.Start -> ::dismissLink
            LinkStep.Name -> { { linkStep = LinkStep.Start } }
            LinkStep.Guide -> { { if (guideIndex > 0) guideIndex-- else linkStep = LinkStep.Name } }
            LinkStep.Confirm -> { { error = null; linkStep = if (lastGuide()) LinkStep.Guide else LinkStep.Name } }
        }
        else -> when (step) {
            SetupStep.Welcome -> { {} }
            SetupStep.Name -> { { go(SetupStep.Welcome) } }
            SetupStep.Guide -> { { if (guideIndex > 0) guideIndex-- else go(SetupStep.Name) } }
            SetupStep.Choice -> { { if (lastGuide()) go(SetupStep.Guide) else go(SetupStep.Name) } }
        }
    }
    SystemBackHandler(enabled = linkKey != null || step != SetupStep.Welcome, onBack = back)

    // Every page starts at the top, even if the previous one was scrolled.
    val scroll = key(linkKey, step, linkStep, guideIndex) { rememberScrollState() }
    Column(
        modifier.verticalScroll(scroll).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val pendingInvite = invite
        val pendingContact = contact
        when {
            pendingInvite != null || pendingContact != null -> when (linkStep) {
                LinkStep.Start -> if (pendingInvite != null) {
                    Welcome(
                        // The link names only the circle, not reliably who invites.
                        title = texts.setup.inviteToCircle(pendingInvite.circleName),
                        text = texts.setup.inviteStartText,
                        top = { CircleAvatar(controller, pendingInvite.circleId, image = null, size = 96.dp) },
                        action = texts.common.next,
                        onAction = { linkStep = LinkStep.Name },
                        dismiss = texts.common.notNow,
                        onDismiss = ::dismissLink,
                    )
                } else if (pendingContact != null) {
                    Welcome(
                        title = texts.setup.contactFrom(pendingContact.name),
                        text = texts.setup.contactStartText(pendingContact.name),
                        top = { PersonAvatar(controller, pendingContact.url, image = null, size = 96.dp) },
                        action = texts.common.next,
                        onAction = { linkStep = LinkStep.Name },
                        dismiss = texts.common.notNow,
                        onDismiss = ::dismissLink,
                    )
                }
                LinkStep.Name -> NameStep(
                    controller = controller,
                    name = name,
                    onNameChange = { name = it; error = null },
                    stepLabel = null,
                    onNext = { if (saveName()) linkStep = if (startGuide(withRadio = false)) LinkStep.Guide else LinkStep.Confirm },
                    onBack = { linkStep = LinkStep.Start },
                )
                LinkStep.Guide -> guideSteps.getOrNull(guideIndex)?.let { current ->
                    GuideStepScreen(
                        controller = controller,
                        step = current,
                        stepLabel = null,
                        onDone = { if (guideIndex < guideSteps.lastIndex) guideIndex++ else linkStep = LinkStep.Confirm },
                        onBack = back,
                    )
                }
                LinkStep.Confirm -> if (pendingInvite != null) {
                    Confirm(
                        top = { CircleAvatar(controller, pendingInvite.circleId, image = null, size = 72.dp) },
                        title = texts.setup.joinCircleConfirmTitle(pendingInvite.circleName),
                        points = listOf(
                            texts.setup.joinPointName(savedName),
                            texts.setup.joinPointChat,
                            texts.setup.joinPointLeave,
                        ),
                        action = texts.setup.join,
                        onAction = { error = controller.acceptPendingInvite(savedName) },
                        dismiss = texts.setup.dontJoin,
                        onDismiss = ::dismissLink,
                        onBack = back,
                        error = error,
                    )
                } else if (pendingContact != null) {
                    Confirm(
                        top = { PersonAvatar(controller, pendingContact.url, image = null, size = 72.dp) },
                        title = texts.setup.addContactConfirmTitle(pendingContact.name),
                        points = listOf(
                            texts.setup.contactPointName(pendingContact.name, savedName),
                            texts.setup.contactPointDirect,
                            texts.setup.contactPointConfirm,
                        ),
                        action = texts.setup.addAndWrite,
                        onAction = { error = controller.acceptPendingContact(savedName) },
                        secondary = texts.setup.onlyAdd,
                        onSecondary = { error = controller.acceptPendingContact(savedName, openChat = false) },
                        dismiss = texts.setup.dontAdd,
                        onDismiss = ::dismissLink,
                        onBack = back,
                        error = error,
                    )
                }
            }

            step == SetupStep.Welcome -> Welcome(
                title = texts.setup.welcomeTitle,
                text = texts.setup.welcomeText,
                top = { BrandMark(size = 96.dp) },
                action = texts.setup.letsGo,
                onAction = { go(SetupStep.Name) },
                // New phone or tablet: with the other device, the emergency card (ADR 0042) or a backup (ADR 0027).
                dismiss = texts.devices.returning,
                onDismiss = { returning = true },
                tagline = true,
                note = texts.setup.usageStatsNote,
            )

            step == SetupStep.Name -> NameStep(
                controller = controller,
                name = name,
                onNameChange = { name = it; error = null },
                stepLabel = texts.setup.step(2, 3 + if (guide.isEmpty()) expectedGuide else guideSteps.size),
                onNext = { if (saveName()) go(if (startGuide(withRadio = true)) SetupStep.Guide else SetupStep.Choice) },
                onBack = { go(SetupStep.Welcome) },
            )

            step == SetupStep.Guide -> guideSteps.getOrNull(guideIndex)?.let { current ->
                GuideStepScreen(
                    controller = controller,
                    step = current,
                    stepLabel = texts.setup.step(3 + guideIndex, total),
                    onDone = { if (guideIndex < guideSteps.lastIndex) guideIndex++ else go(SetupStep.Choice) },
                    onBack = back,
                )
            }

            step == SetupStep.Choice -> {
                StepLabel(texts.setup.step(total, total))
                Text(texts.setup.howToStart, style = MaterialTheme.typography.headlineMedium)
                // Only two ways, so that nobody has to think about circles or links yet.
                ChoiceCard(AppIcons.Teilen, texts.people.shareMyContact, texts.setup.choiceShareText, enabled = ownContactLink != null) {
                    controller.shareOwnContact()
                    controller.finishSetup()
                }
                ChoiceCard(AppIcons.Suche, texts.setup.lookAround, texts.setup.choiceLookAroundText) { controller.finishSetup() }
                BackButton(back)
            }
        }
    }
    if (restoring) RestoreBackupDialog(controller) { restoring = false }
    if (returning) ReturningDialog(controller, onBackupFile = { restoring = true }.takeIf { controller.backupAvailable }) { returning = false }
}

@Composable
private fun StepLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** First screen (also when starting via a link): picture, title, one sentence, main action. */
@Composable
private fun Welcome(
    title: String,
    text: String,
    top: @Composable () -> Unit,
    action: String,
    onAction: () -> Unit,
    dismiss: String? = null,
    onDismiss: () -> Unit = {},
    tagline: Boolean = false,
    note: String? = null,
) {
    Spacer(Modifier.height(32.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { top() }
    Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    val claim = texts.setup.tagline
    if (tagline && claim != null) {
        Text(
            claim,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(16.dp))
    PrimaryButton(action, onClick = onAction)
    dismiss?.let { AppButton(it, onDismiss, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet) }
    note?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** „Wie sollen dich andere sehen?“: name (required) and picture (optional). */
@Composable
private fun NameStep(
    controller: AppController,
    name: String,
    onNameChange: (String) -> Unit,
    stepLabel: String?,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val image by controller.ownImage.collectAsState()
    val myDevice by controller.myDeviceId.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val pickPhoto = rememberPhotoPicker(onError = { error = it }) { bytes ->
        scope.launch {
            busy = true
            error = controller.setOwnImage(bytes)
            busy = false
        }
    }
    stepLabel?.let { StepLabel(it) }
    Text(texts.setup.howOthersSeeYou, style = MaterialTheme.typography.headlineMedium)
    PhotoPickerRow(
        avatar = { PersonAvatar(controller, myDevice?.let(PeerId::device) ?: "ich", image, 72.dp) },
        busy = busy,
        hasPhoto = image != null,
        onPick = pickPhoto,
        onReset = controller::resetOwnImage,
    )
    OutlinedTextField(
        value = name,
        onValueChange = { onNameChange(OwnNameLimit.accept(name, it)) },
        singleLine = true,
        label = { Text(texts.setup.yourName) },
        supportingText = OwnNameLimit.hint(name)?.let { { TextLimitHint(it) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        modifier = Modifier.fillMaxWidth(),
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    Text(
        texts.setup.photoOptional,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    PrimaryButton(texts.common.next, enabled = name.isNotBlank(), onClick = onNext)
    BackButton(onBack)
}

/** Confirmation before adding or joining: what applies afterwards, in three short points. */
@Composable
private fun Confirm(
    top: @Composable () -> Unit,
    title: String,
    points: List<String>,
    action: String,
    onAction: () -> Unit,
    dismiss: String,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    error: String?,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    top()
    Text(title, style = MaterialTheme.typography.headlineMedium)
    AppSection {
        points.forEach { point ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(AppIcons.Haken, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(point, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    Spacer(Modifier.height(8.dp))
    PrimaryButton(action, onClick = onAction)
    secondary?.let { AppButton(it, onSecondary, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary) }
    AppButton(dismiss, onDismiss, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet)
    BackButton(onBack)
}

/** The one main action of the step, in signal yellow. */
@Composable
private fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    AppButton(label, onClick, Modifier.fillMaxWidth(), kind = ButtonKind.Main, enabled = enabled)
}

/** Choice in the last step: icon and title in one row, the explanation below across the full width. */
@Composable
private fun ChoiceCard(icon: ImageVector, title: String, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RowIcon(icon)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(AppIcons.Weiter, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    AppButton(texts.common.back, onClick, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet, icon = AppIcons.Zurueck)
}
