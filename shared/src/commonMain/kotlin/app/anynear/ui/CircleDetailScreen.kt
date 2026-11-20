package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.circles.Circle
import app.anynear.i18n.texts
import app.anynear.mesh.Conversation
import app.anynear.mesh.PeerId
import app.anynear.people.CircleMember
import kotlinx.coroutines.launch

/**
 * Detail page of a circle (UX concept): picture, name and who is in it, „Nachricht schreiben“ as the
 * main action, next to it invite and edit, then the members. Rare things (remove a member, invalidate old
 * invitation links) are under „Erweitert“, „Kreis verlassen“ separately below. Every member may do all
 * of this (ADR 0012). Reachable via „Kreise“ and the header of the circle chat.
 */
@Composable
internal fun CircleDetailScreen(
    controller: AppController,
    circleId: String,
    onBack: () -> Unit,
    onOpenPerson: (peer: String) -> Unit,
    onWrite: () -> Unit,
    /** Storage of this circle (ADR 0049). */
    onOpenFiles: () -> Unit,
    /** All photos and files from the circle chat. */
    onOpenChatFiles: () -> Unit,
) {
    val circles by controller.circles.collectAsState()
    val all by controller.members.collectAsState()
    val ownName by controller.ownName.collectAsState()
    val ownImage by controller.ownImage.collectAsState()
    val myDevice by controller.myDeviceId.collectAsState()
    val internetOnly by controller.internetOnlyCircles.collectAsState()
    val circle = circles.firstOrNull { it.id == circleId }
    // Left: the page no longer exists.
    LaunchedEffect(circle == null) { if (circle == null) onBack() }
    if (circle == null) return
    // One line per person (ADR 0042); one's own further devices only as a note in the own line.
    val byCircle by controller.circleMembers.collectAsState()
    val myPerson by controller.myPersonId.collectAsState()
    val members = byCircle[circle.id].orEmpty()
    val ownDevices = remember(all, myPerson, myDevice) { controller.ownDevicesIn(circle.id) }
    val support by controller.supportPeer.collectAsState()
    val isSupport = remember(support, members, circle.name) { controller.isSupportCircle(circle.id) }
    var editing by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<CircleMember?>(null) }
    var choosingRemoval by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }

    AppHeader(title = circle.name, leading = { HeaderBackButton(onBack) })
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CircleAvatar(controller, circle.id, circle.image, 112.dp, enlargeable = true)
            Text(circle.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(
                circleSummary(circle, members.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        // No room on the radio (ADR 0038): only one sentence, the app swaps on its own.
        if (circle.id in internetOnly && !circle.removed) {
            Text(
                texts.people.internetOnlyExplained,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (circle.removed) {
            Text(texts.people.removedFromThisCircle, style = MaterialTheme.typography.bodyLarge)
        } else {
            // Still alone in the circle: inviting is the main action, writing follows afterwards.
            val alone = members.isEmpty()
            AppButton(
                if (alone) texts.people.invite else texts.chat.writeMessage,
                if (alone) ({ controller.showInvite(circle.id) }) else onWrite,
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Main,
                icon = if (alone) AppIcons.Teilen else AppIcons.Nachrichten,
            )
            // One below the other instead of as tiles: fully readable even with large font.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (alone) {
                    AppButton(texts.chat.writeMessage, onWrite, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = AppIcons.Nachrichten)
                } else {
                    AppButton(texts.people.invite, { controller.showInvite(circle.id) }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = AppIcons.Teilen)
                }
                AppButton(texts.people.editNameAndPicture, { editing = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = AppIcons.Stift)
            }

            ChatFilesSection(controller, Conversation.Circle(circle.id), onOpenChatFiles)

            // Support requests (ADR 0065) need no storage: users do not see it there, support does.
            if (!isSupport) AppSection {
                AppRow(title = texts.files.circleFiles, subtitle = circleFilesSummary(controller, circle.id), icon = AppIcons.Dokument, chevron = true, onClick = onOpenFiles)
            }

            // What this circle sees of my location (ADR 0121 §13).
            if (!isSupport) LocationLevelRow(controller, circle.name, circleId = circle.id)

            AppSection(
                texts.people.members,
                footer = if (members.isEmpty()) texts.people.noMembersYet else null,
            ) {
                AppRow(
                    title = texts.people.ownMember(ownName),
                    subtitle = if (ownDevices > 1) texts.people.ownDevicesInCircle(ownDevices) else null,
                    leading = { PersonAvatar(controller, PeerId.device(myDevice.orEmpty()), ownImage, 40.dp, enlargeable = true) },
                )
                members.forEach { m ->
                    AppRow(
                        title = m.name,
                        // Own name ([app.anynear.people.ContactNames]): the name from the profile small below it.
                        subtitle = m.profileName.takeIf { it != m.name && it.isNotBlank() }?.let(texts.people::profileNameLine),
                        leading = { PersonAvatar(controller, PeerId.device(m.personId), m.image, 40.dp, enlargeable = true) },
                        chevron = true,
                        onClickLabel = texts.people.viewPerson,
                        onClick = { onOpenPerson(PeerId.device(m.personId)) },
                    )
                }
            }

            // Group check (ADR 0053): the radios of all members one after the other.
            if (members.isNotEmpty()) {
                val messages by controller.messages.collectAsState()
                val people by controller.people.collectAsState()
                val radios = remember(members, messages, people) { members.flatMap { controller.knownRadios(PeerId.device(it.personId)) }.distinct() }
                RadioCheckGroupSection(controller, "circle:${circle.id}", radios)
            }

            AppSection {
                ExpandRow(texts.common.advanced, expanded = advanced, icon = AppIcons.Regler) { advanced = !advanced }
                if (advanced) {
                    if (members.isNotEmpty()) {
                        AppRow(title = texts.people.removeMember, icon = AppIcons.Person, onClick = { choosingRemoval = true })
                    }
                    AppRow(title = texts.people.invalidateOldInviteLinks, icon = AppIcons.Schloss, onClick = { confirmReset = true })
                }
            }
        }

        // Leaving clearly separated from everything else, always with a confirmation prompt.
        AppSection {
            AppRow(
                title = if (circle.removed) texts.people.removeFromList else texts.people.leaveCircle,
                icon = AppIcons.Verlassen,
                danger = true,
                onClick = { leaving = true },
            )
        }
    }

    if (choosingRemoval) {
        ActionsDialog(texts.people.whoToRemove, onDismiss = { choosingRemoval = false }) {
            members.forEach { m ->
                AppRow(
                    title = m.name,
                    leading = { PersonAvatar(controller, PeerId.device(m.personId), m.image, 40.dp) },
                    onClick = { choosingRemoval = false; removing = m },
                )
            }
        }
    }
    if (editing) EditCircleDialog(controller, circle.id, onDismiss = { editing = false })
    if (leaving) LeaveCircleDialog(controller, circle, onDismiss = { leaving = false })
    removing?.let { m ->
        AppDialog(
            onDismissRequest = { removing = null },
            title = { Text(texts.people.removeMemberTitle(m.name)) },
            text = {
                Text(texts.people.removeMemberText(m.name, circle.name), Modifier.verticalScroll(rememberScrollState()))
            },
            confirmButton = {
                AppButton(texts.common.remove, kind = ButtonKind.Danger, onClick = {
                    controller.removePerson(circle.id, m)
                    removing = null
                })
            },
            dismissButton = { AppButton(texts.common.cancel, { removing = null }, kind = ButtonKind.Quiet) },
        )
    }
    if (confirmReset) {
        AppDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(texts.people.invalidateInviteLinksTitle) },
            text = {
                Text(texts.people.invalidateInviteLinksText)
            },
            confirmButton = {
                AppButton(texts.people.invalidate, onClick = {
                    controller.resetInviteLink(circle.id)
                    confirmReset = false
                })
            },
            dismissButton = { AppButton(texts.common.cancel, { confirmReset = false }, kind = ButtonKind.Quiet) },
        )
    }
}

/** Briefly who is in the circle: for the „Kreise“ list, the detail page and the header of the circle chat. */
internal fun circleSummary(circle: Circle, others: Int): String = when {
    circle.removed -> texts.people.summaryRemoved
    others == 0 -> texts.people.summaryNobodyInvited
    else -> texts.people.summaryYouAndOthers(others)
}

/** Leave a circle (or remove it from the list once removed), from the circle page and from „Chats“ (long press). */
@Composable
internal fun LeaveCircleDialog(controller: AppController, circle: Circle, onDismiss: () -> Unit) {
    val last = remember(circle.id) { controller.aloneIn(circle.id) }
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (circle.removed) texts.people.removeCircleTitle else texts.people.leaveCircleTitle(circle.name)) },
        text = {
            // Only what really happens (retention rule), without a promise for all contacts. Scrollable so that the
            // whole text stays readable even with large font.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (circle.removed) texts.people.removeCircleText else texts.people.leaveCircleText)
                // The last one to leave takes the circle storage along (ADR 0049 §5); that must be clear beforehand.
                if (!circle.removed && last) Text(texts.people.leaveCircleLastNote, color = MaterialTheme.colorScheme.error)
                if (!circle.removed) {
                    // Corresponds to the rule in MemberDirectory: whoever has an entry under "~direct" stays
                    // (contact link or direct messages in one of the two directions) or is in another circle.
                    Text(
                        texts.people.leaveCircleContactsNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            AppButton(if (circle.removed) texts.common.remove else texts.common.leave, kind = ButtonKind.Danger, onClick = {
                onDismiss()
                controller.leaveCircle(circle.id)
            })
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/** Change name and picture of the circle. Everyone in the circle sees the change and who made it. */
@Composable
private fun EditCircleDialog(controller: AppController, circleId: String, onDismiss: () -> Unit) {
    val circles by controller.circles.collectAsState()
    val circle = circles.firstOrNull { it.id == circleId } ?: return
    var name by remember(circleId) { mutableStateOf(circle.name) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pickPhoto = rememberPhotoPicker(onError = { error = it }) { bytes ->
        scope.launch {
            busy = true
            error = controller.setCircleImage(circleId, bytes)
            busy = false
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.people.editCircle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoPickerRow(
                    avatar = { CircleAvatar(controller, circle.id, circle.image, 72.dp, enlargeable = true) },
                    busy = busy,
                    hasPhoto = circle.image != null,
                    onPick = pickPhoto,
                    onReset = { controller.resetCircleImage(circleId) },
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = CircleNameLimit.accept(name, it); error = null },
                    singleLine = true,
                    label = { Text(texts.people.circleName) },
                    supportingText = CircleNameLimit.hint(name)?.let { { TextLimitHint(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(texts.people.everyoneSeesChange, style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            AppButton(texts.common.done, onClick = {
                error = controller.renameCircle(circleId, name)
                if (error == null) onDismiss()
            })
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}
