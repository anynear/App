package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
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
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.people.addContact) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(texts.people.addContactText)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; feedback = null },
                    minLines = 3,
                    maxLines = 5,
                    placeholder = { Text("https://anynear.app/c#…") },
                    modifier = Modifier.fillMaxWidth(),
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
    AppDialog(
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
