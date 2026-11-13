package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import app.anynear.app.AppController
import app.anynear.chats.findAppLink
import app.anynear.i18n.texts
import app.anynear.mesh.Conversation
import app.anynear.mesh.PeerId
import app.anynear.people.Member
import app.anynear.people.MemberDirectory
import app.anynear.people.Person

/**
 * Tab „Kontakte“: at the top yourself with „Teilen“, then whoever wrote to you without being a contact,
 * then all contacts with their origin. Tapping opens the conversation; the detail page is reached via the
 * header in the conversation. At the bottom floats the main action „Kontakt hinzufügen“.
 */
@Composable
/** Under „Menschen“ → contacts; the header with the switch is drawn by [PeopleScreen]. */
internal fun ContactsScreen(controller: AppController, onOpenChat: (peer: String) -> Unit, onOpenProfile: () -> Unit) {
    val people by controller.people.collectAsState()
    val messages by controller.messages.collectAsState()
    val members by controller.members.collectAsState()
    val ownName by controller.ownName.collectAsState()
    val ownImage by controller.ownImage.collectAsState()
    val myDevice by controller.myDeviceId.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showOwn by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val circles by controller.circles.collectAsState()
    val circleNames = circles.associate { it.id to it.name }
    // Space below the list, as high as the button (grows with the font), so that the last entry stays clear.
    var buttonHeight by remember { mutableStateOf(MainButtonHeight) }
    val density = LocalDensity.current

    // Do not swallow direct messages from people who are not (yet) known.
    val knownPeers = people.map { it.peer }.toSet()
    val strangers = messages.asSequence()
        .mapNotNull { (it.conversation as? Conversation.Direct)?.peer }
        .filter { it !in knownPeers }
        .distinct()
        .toList()

    val supportPeer by controller.supportPeer.collectAsState()
    // The support identity is not a contact to write to; questions go through the form (ADR 0065).
    val shownPeople = people.filter { person -> person.peer != supportPeer && matchesSearch(query, person.name, *person.circleIds.map(circleNames::get).toTypedArray()) }
    val shownStrangers = strangers.filter { matchesSearch(query, controller.peerName(it)) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = buttonHeight + 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    // Yourself: leads to „Mein Profil“; passing on your own contact is the easiest way to
                    // a new contact, hence additionally as a button (the same dialog as in the profile).
                    // Button below the row, not next to it: name and sentence keep their width even with a large font.
                    AppSection {
                        AppRow(
                            title = texts.people.ownMember(ownName),
                            subtitle = texts.settings.myProfile,
                            leading = { myDevice?.let { PersonAvatar(controller, PeerId.device(it), ownImage, 48.dp) } },
                            chevron = true,
                            onClick = onOpenProfile,
                        )
                        AppButton(
                            texts.people.shareMyContact,
                            { showOwn = true },
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            kind = ButtonKind.Secondary,
                            icon = AppIcons.Teilen,
                        )
                    }
                }
                if (people.isNotEmpty() || strangers.isNotEmpty()) {
                    item { SearchField(query, { query = it }, texts.people.searchContacts) }
                }
                if (shownStrangers.isNotEmpty()) item {
                    AppSection(texts.people.wroteToYou, footer = texts.people.notInContactsYet) {
                        shownStrangers.forEach { peer ->
                            AppRow(
                                title = controller.peerName(peer),
                                leading = { PersonAvatar(controller, peer, null, 48.dp) },
                                onClickLabel = texts.people.openConversation,
                                onClick = { onOpenChat(peer) },
                            )
                        }
                    }
                }
                when {
                    people.isEmpty() && strangers.isEmpty() -> item {
                        Text(
                            texts.people.contactsEmpty,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    shownPeople.isEmpty() && shownStrangers.isEmpty() -> item { NoSearchResults() }
                    shownPeople.isNotEmpty() -> item {
                        AppSection(texts.people.allContacts) {
                            shownPeople.forEach { person ->
                                AppRow(
                                    title = person.name,
                                    subtitle = originOf(person, circleNames, members),
                                    leading = { PersonAvatar(controller, person.peer, person.image, 48.dp) },
                                    onClickLabel = texts.people.openConversation,
                                    onClick = { onOpenChat(person.peer) },
                                )
                            }
                        }
                    }
                }
            }
            AppButton(
                texts.people.addContact,
                { showMenu = true },
                kind = ButtonKind.Main,
                icon = AppIcons.Plus,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .onSizeChanged { buttonHeight = with(density) { it.height.toDp() } },
            )
        }
    }

    if (showMenu) AddContactFlow(controller, onDismiss = { showMenu = false }, onOpenChat = onOpenChat)
    if (showOwn) OwnCodeDialog(controller, onDismiss = { showOwn = false })
}

/**
 * How you know a person, in one line: „direkter Kontakt“, the shared circles or both.
 * A radio without the app is reachable „nur über Funk“.
 */
internal fun originOf(person: Person, circleNames: Map<String, String>, members: List<Member>): String {
    if (person.deviceId == null) return texts.people.radioOnly
    val names = person.circleIds.mapNotNull(circleNames::get)
    val added = members.any { it.deviceId == person.deviceId && it.circleId == MemberDirectory.DIRECT_CONTACTS }
    return when {
        names.isEmpty() -> texts.chat.addedByYou
        added -> (listOf(texts.chat.addedByYou) + names).joinToString(" · ")
        else -> names.joinToString(" · ")
    }
}

private enum class AddContactStep { Menu, Paste, Scan, Own }

/**
 * „Kontakt hinzufügen“: choice of the three ways (paste link, scan QR code, pass on your own
 * contact) and the respective dialog. From „Kontakte“ and from „Neue Nachricht“ the same flow.
 */
@Composable
internal fun AddContactFlow(controller: AppController, onDismiss: () -> Unit, onOpenChat: (peer: String) -> Unit) {
    var step by remember { mutableStateOf(AddContactStep.Menu) }
    when (step) {
        AddContactStep.Menu -> ActionsDialog(texts.people.addContact, onDismiss = onDismiss) {
            AppRow(title = texts.people.pasteLink, subtitle = texts.people.pasteLinkHint, icon = AppIcons.Link, onClick = { step = AddContactStep.Paste })
            AppRow(title = texts.people.scanQrCode, subtitle = texts.people.scanContactHint, icon = AppIcons.Kamera, onClick = { step = AddContactStep.Scan })
            AppRow(title = texts.people.myContact, subtitle = texts.people.myContactHint, icon = AppIcons.QrCode, onClick = { step = AddContactStep.Own })
        }
        AddContactStep.Paste -> AddContactDialog(controller, onDismiss = onDismiss, onOpenChat = onOpenChat)
        AddContactStep.Scan -> QrScanDialog(controller, onDismiss = onDismiss)
        AddContactStep.Own -> OwnCodeDialog(controller, onDismiss = onDismiss)
    }
}

@Composable
private fun AddContactDialog(controller: AppController, onDismiss: () -> Unit, onOpenChat: (peer: String) -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.people.addContact) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(texts.people.addContactText)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; feedback = null },
                    minLines = 3,
                    placeholder = { Text("https://anynear.app/c#…") },
                )
                feedback?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
        },
        confirmButton = {
            AppButton(texts.common.next, onClick = {
                // Contact and invitation links as when scanning: first the confirmation with picture and name.
                val link = findAppLink(code)
                if (link == null) {
                    feedback = texts.status.contactLinkBroken
                    return@AppButton
                }
                scope.launch { controller.receiveLink(link)?.let { feedback = it } ?: onDismiss() }
            })
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

@Composable
internal fun OwnCodeDialog(controller: AppController, onDismiss: () -> Unit) {
    val link by controller.ownContactLink.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.people.myContact) },
        text = {
            val current = link
            if (current == null) {
                Text(texts.people.preparingContact)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(texts.people.ownContactText)
                    // Face to face: scan the QR code with the camera; otherwise share the link.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrCode(current, size = 200.dp) }
                    PublicContactSection(controller)
                }
            }
        },
        confirmButton = {
            if (link != null) AppButton(texts.people.shareLink, { controller.shareOwnContact(); onDismiss() }, icon = AppIcons.Teilen)
        },
        dismissButton = { link?.let { CopyButton(it) } ?: AppButton(texts.common.done, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/** Only for the support identity: contact link for publishing with an authorization for one year (ADR 0084 §6). */
@Composable
private fun PublicContactSection(controller: AppController) {
    val channels by (controller.alerts?.channels ?: NoAlertChannels).collectAsState()
    if (!controller.isSupportIdentity(channels)) return
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf<String?>(null) }
    // After revoking, a new link only on the next opening: within the same second it would be the same (and blocked).
    LaunchedEffect(Unit) { link = controller.publicContactLink() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(texts.people.publicContact, style = MaterialTheme.typography.titleSmall)
        Text(texts.people.publicContactText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        link?.let { current ->
            CodeBox(current)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CopyButton(current)
                AppButton(texts.people.publicContactRevoke, {
                    scope.launch {
                        controller.revokePublicContactLink()
                        link = null
                    }
                }, kind = ButtonKind.Quiet)
            }
        }
    }
}

private val NoAlertChannels = kotlinx.coroutines.flow.MutableStateFlow(emptyList<app.anynear.alerts.AlertChannel>())

/** The code itself: selectable, but small and muted. The intended way is via [CopyButton]. */
@Composable
private fun CodeBox(code: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            Text(
                code,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}

@Composable
private fun CopyButton(code: String) {
    @Suppress("DEPRECATION") // LocalClipboard needs platform-specific ClipEntry objects.
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    AppButton(
        if (copied) texts.people.copied else texts.common.copy,
        {
            clipboard.setText(AnnotatedString(code))
            copied = true
        },
        kind = ButtonKind.Quiet,
        icon = if (copied) AppIcons.Haken else AppIcons.Kopieren,
    )
}
