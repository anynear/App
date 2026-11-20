package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.people.MemberDirectory
import app.anynear.i18n.texts
import app.anynear.mesh.Conversation
import app.anynear.mesh.PeerId
import app.anynear.mesh.Transport
import app.anynear.people.Reachability
import kotlin.time.Clock

/**
 * Detail page of a person (draft): picture, name and origin, „Nachricht schreiben“ as the main action, when
 * something last came from them and shared circles. Radios, „Kontakt entfernen“ (with or without the conversation) and
 * „Gespräch löschen“ are under „Erweitert“; the latter also for people who are no longer a contact. Everything is
 * derived from your own message history; whether the person's radio answers right now
 * is shown only by the radio check at the push of a button (ADR 0053). Reachable via the header of a conversation and
 * the members of a circle.
 */
@Composable
internal fun PersonDetailScreen(
    controller: AppController,
    peer: String,
    onBack: () -> Unit,
    onOpenCircle: (circleId: String) -> Unit,
    onWrite: () -> Unit,
    /** After removing without a shared circle: back to the list, not into the conversation with a now unknown person. */
    onRemoved: () -> Unit = onBack,
    /** All photos and files from the direct messages with the person. */
    onOpenChatFiles: () -> Unit,
    /** Opens the question „remove?“ right away (hint on an unreachable contact). */
    askRemove: Boolean = false,
) {
    val people by controller.people.collectAsState()
    val messages by controller.messages.collectAsState()
    val circles by controller.circles.collectAsState()
    val members by controller.members.collectAsState()
    val person = people.firstOrNull { it.peer == peer }
    val name = person?.name ?: controller.peerName(peer)
    val nodeNum = person?.nodeNum ?: PeerId.nodeNumOf(peer)
    val reach = remember(messages, peer, nodeNum) { Reachability.of(messages, peer, nodeNum) }
    val personCircles = person?.circleIds.orEmpty().mapNotNull { id -> circles.firstOrNull { it.id == id } }
    var advanced by remember { mutableStateOf(false) }
    var confirmRemove by remember(peer, askRemove) { mutableStateOf(askRemove) }
    var confirmDeleteHistory by remember { mutableStateOf(false) }
    var choosingCircle by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    // Own name only for people with anynear (person ID), not for support (ADR 0065).
    val supportPeer by controller.supportPeer.collectAsState()
    val renamable = person?.deviceId != null && person.peer != supportPeer
    // Removing exists for contacts you saved yourself: radio contacts without the app and app contacts from a
    // contact link or from direct messages (~direct). Whoever is known only through a circle stays as long as
    // this circle exists.
    val radioContact = person?.radioContact?.takeIf { person.deviceId == null }
    val deviceId = person?.deviceId
    // By person (ADR 0042): [deviceId] is the person ID, the entries carry device IDs.
    val directContact = person?.isIn(members, MemberDirectory.DIRECT_CONTACTS) == true
    // Own circles in which the person is still missing; inviting means: passing on their invitation link.
    // The conversation can always be deleted, even if the person is no longer a contact and nothing else is left here.
    val hasHistory = messages.any { it.conversation == Conversation.Direct(peer) }
    val inviteCircles = circles.filter { c -> !c.removed && (deviceId == null || person?.isIn(members, c.id) != true) }

    AppHeader(title = name, leading = { HeaderBackButton(onBack) })
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PersonAvatar(controller, peer, person?.image, 112.dp, enlargeable = true)
            Text(name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            // Own name: the name from the person's profile stays visible, small below it.
            if (person != null && person.hasOwnName && person.profileName.isNotBlank()) {
                Text(
                    texts.people.profileNameLine(person.profileName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                when {
                    // Radio without anynear (e.g. the Meshtastic app): say clearly that only radio works.
                    controller.isRadioOnly(peer) -> texts.people.radioOnlyContact
                    person != null -> originOf(person, circles.associate { c -> c.id to c.name }, members)
                    else -> texts.people.notAContactYet
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        AppButton(texts.chat.writeMessage, onWrite, Modifier.fillMaxWidth(), kind = ButtonKind.Main, icon = AppIcons.Nachrichten)
        if (renamable) {
            AppButton(texts.people.changeName, { renaming = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = AppIcons.Stift)
        }
        // Pass this contact on to another contact (ADR 0116); both are asked before they can write to each other.
        if (directContact) {
            AppButton(texts.people.shareContact, { sharing = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet, icon = AppIcons.Teilen)
        }

        // What this person sees of my location (ADR 0121 §13).
        person?.deviceId?.let { id ->
            LocationLevelRow(controller, name, personId = id, personCircles = person.circleIds.filter { it != app.anynear.people.MemberDirectory.DIRECT_CONTACTS })
        }

        // Only when something last came from the person, and by which route; no statement about now.
        AppSection(texts.people.lastReached, footer = texts.people.lastReachedFooter) {
            val at = reach.lastHeardMillis
            AppRow(
                title = at?.let { lastReachedLabel(it) } ?: texts.people.nothingReceivedFrom(name),
                subtitle = when {
                    at == null -> null
                    Transport.INTERNET in reach.lastHeardVia && Transport.RADIO in reach.lastHeardVia -> texts.people.viaInternetAndRadio
                    Transport.RADIO in reach.lastHeardVia -> texts.people.viaRadio
                    Transport.INTERNET in reach.lastHeardVia -> texts.people.viaInternet
                    else -> null
                },
                icon = AppIcons.Uhr,
            )
        }

        // Radio check (ADR 0053): check now whether the person's radio answers over radio.
        RadioCheckPersonSection(controller, peer, name)

        ChatFilesSection(controller, Conversation.Direct(peer), onOpenChatFiles)

        if (personCircles.isNotEmpty()) {
            AppSection(texts.people.sharedCircles) {
                personCircles.forEach { circle ->
                    AppRow(
                        title = circle.name,
                        leading = { CircleAvatar(controller, circle.id, circle.image, 40.dp) },
                        chevron = true,
                        onClick = { onOpenCircle(circle.id) },
                    )
                }
            }
        }

        val removable = radioContact != null || directContact
        // Known only from a circle: no removing, but a sentence on why the person is listed here.
        val onlyFromCircles = person != null && deviceId != null && !directContact && personCircles.isNotEmpty()
        if (hasHistory || person != null && (reach.radioNodes.isNotEmpty() || removable || onlyFromCircles || inviteCircles.isNotEmpty())) {
            AppSection {
                ExpandRow(texts.common.advanced, expanded = advanced, icon = AppIcons.Regler) { advanced = !advanced }
                if (advanced) {
                    reach.radioNodes.forEach { node ->
                        AppRow(title = texts.people.radioNamed(controller.displayName(node)), icon = AppIcons.Funk)
                    }
                    if (person != null && inviteCircles.isNotEmpty()) {
                        AppRow(
                            title = texts.people.inviteToCircle,
                            icon = AppIcons.Teilen,
                            chevron = true,
                            onClick = { if (inviteCircles.size == 1) controller.showInvite(inviteCircles.single().id) else choosingCircle = true },
                        )
                    }
                    if (hasHistory) {
                        AppRow(title = texts.people.deleteHistory, icon = AppIcons.Schliessen, danger = true, onClick = { confirmDeleteHistory = true })
                    }
                    if (removable) {
                        AppRow(title = texts.people.removeContact, icon = AppIcons.Verlassen, danger = true, onClick = { confirmRemove = true })
                    } else if (onlyFromCircles) {
                        Text(
                            texts.people.staysWhileSharedCircle(name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }

    if (renaming && person != null) {
        ContactNameDialog(controller, person, onDismiss = { renaming = false })
    }

    if (choosingCircle) {
        ActionsDialog(texts.people.inviteWhichCircle(name), onDismiss = { choosingCircle = false }) {
            inviteCircles.forEach { circle ->
                AppRow(
                    title = circle.name,
                    leading = { CircleAvatar(controller, circle.id, circle.image, 40.dp) },
                    onClick = {
                        choosingCircle = false
                        controller.showInvite(circle.id)
                    },
                )
            }
        }
    }

    if (sharing) {
        ShareContactDialog(controller, peer, name, onDismiss = { sharing = false })
    }

    if (confirmRemove && (radioContact != null || directContact)) {
        // What applies afterwards, honestly: a shared circle keeps the person visible, nothing is blocked. The conversation
        // stays or goes along, depending on the button.
        val text = when {
            radioContact != null -> texts.people.removeRadioContactText
            personCircles.isNotEmpty() -> texts.people.removeDirectContactInCircleText(name, personCircles.joinToString(", ") { it.name })
            else -> texts.people.removeDirectContactText(name)
        }
        AppDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(texts.people.removeMemberTitle(name)) },
            text = { Text(text, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                val remove = { withHistory: Boolean ->
                    confirmRemove = false
                    if (radioContact != null) controller.removeContact(radioContact.nodeNum) else deviceId?.let(controller::removeDirectContact)
                    if (withHistory) controller.deleteDirectHistory(peer)
                    // Without a shared circle the person no longer exists in contacts; back to the list.
                    if (personCircles.isEmpty()) onRemoved()
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(texts.people.removeKeepHistory, { remove(false) }, Modifier.fillMaxWidth(), kind = ButtonKind.Danger)
                    if (hasHistory) AppButton(texts.people.removeWithHistory, { remove(true) }, Modifier.fillMaxWidth(), kind = ButtonKind.Danger)
                }
            },
            dismissButton = { AppButton(texts.common.cancel, { confirmRemove = false }, kind = ButtonKind.Quiet) },
        )
    }

    if (confirmDeleteHistory) {
        DeleteHistoryDialog(controller, peer, name, onDismiss = { confirmDeleteHistory = false }) {
            // Whoever is no longer a contact has nothing left here afterwards; back to the list instead of
            // into an empty conversation.
            if (person == null) onRemoved()
        }
    }
}

/**
 * „Namen ändern“: an own name for [person] that only applies to oneself (on all own devices) and covers the name from
 * their profile everywhere. „Zurücksetzen“ shows the profile name again.
 */
@Composable
private fun ContactNameDialog(controller: AppController, person: app.anynear.people.Person, onDismiss: () -> Unit) {
    var name by remember(person.peer) { mutableStateOf(person.name) }
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.people.changeName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = ContactNameLimit.accept(name, it) },
                    singleLine = true,
                    label = { Text(texts.people.contactNameLabel) },
                    placeholder = person.profileName.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                    supportingText = ContactNameLimit.hint(name)?.let { { TextLimitHint(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(texts.people.contactNameHint, style = MaterialTheme.typography.bodySmall)
                if (person.hasOwnName) {
                    Text(texts.people.profileNameLine(person.profileName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppButton(texts.people.resetName, {
                        controller.setContactName(person.peer, "")
                        onDismiss()
                    }, kind = ButtonKind.Quiet)
                }
            }
        },
        confirmButton = {
            AppButton(texts.common.done, onClick = {
                // Empty or the profile name again: no own name.
                controller.setContactName(person.peer, if (name.trim() == person.profileName.trim()) "" else name)
                onDismiss()
            })
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/** Delete the conversation with [peer] on this device, from the person page and from „Chats“ (long press). */
@Composable
internal fun DeleteHistoryDialog(controller: AppController, peer: String, name: String, onDismiss: () -> Unit, onDeleted: () -> Unit = {}) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.people.deleteHistoryTitle(name)) },
        text = { Text(texts.people.deleteHistoryText(name), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            AppButton(texts.files.delete, {
                onDismiss()
                controller.deleteDirectHistory(peer)
                onDeleted()
            }, kind = ButtonKind.Danger)
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/** „Heute, 14:05“, „Gestern, 14:05“, „Montag, 14:05“, otherwise with the date: when something last arrived. */
internal fun lastReachedLabel(atMillis: Long, nowMillis: Long = Clock.System.now().toEpochMilliseconds()): String =
    formatDayAndTime(atMillis, nowMillis).replaceFirstChar { it.uppercase() }

/** When [peer] was last reachable, for the header of chat and detail page; `null` if never yet. */
@Composable
internal fun rememberLastSeen(controller: AppController, peer: String): String? {
    val people by controller.people.collectAsState()
    val messages by controller.messages.collectAsState()
    val nodeNum = people.firstOrNull { it.peer == peer }?.nodeNum ?: PeerId.nodeNumOf(peer)
    val at = remember(messages, peer, nodeNum) { Reachability.of(messages, peer, nodeNum).lastHeardMillis }
    return at?.let { lastSeenLabel(it) }
}

/** „Zuletzt erreichbar heute, 14:05“, „… gestern, 14:05“, „… Montag, 14:05“, otherwise with the date. */
internal fun lastSeenLabel(atMillis: Long, nowMillis: Long = Clock.System.now().toEpochMilliseconds()): String =
    texts.people.lastSeen(formatDayAndTime(atMillis, nowMillis))

/**
 * „Kontakt teilen“ (ADR 0116): choose who gets [name] introduced. Only own app contacts, not circles or other apps; the
 * introduced person is asked before anything is passed on.
 */
@Composable
private fun ShareContactDialog(controller: AppController, peer: String, name: String, onDismiss: () -> Unit) {
    val people by controller.people.collectAsState()
    val members by controller.members.collectAsState()
    val others = people.filter { p ->
        p.peer != peer && p.isIn(members, MemberDirectory.DIRECT_CONTACTS)
    }
    ActionsDialog(texts.people.shareContactWho(name), onDismiss = onDismiss) {
        Text(
            texts.people.shareContactHint(name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (others.isEmpty()) {
            Text(texts.people.noOtherContacts, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        others.forEach { other ->
            AppRow(
                title = other.name,
                leading = { PersonAvatar(controller, other.peer, other.image, 40.dp) },
                onClick = {
                    onDismiss()
                    controller.introduce(peer, other.peer)
                },
            )
        }
    }
}
