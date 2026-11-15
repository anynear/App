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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.mesh.Conversation
import app.anynear.i18n.texts

/**
 * „Kreise“ tab (ADR 0100): at the top, of equal rank, „Kreis gründen“ and „Einladung annehmen“, below them
 * all circles with picture, name and who is in them. Tapping opens the circle detail ([CircleDetailScreen]),
 * from there „Nachricht schreiben“ leads into the circle chat.
 */
@Composable
internal fun CirclesScreen(controller: AppController, onOpenCircle: (String) -> Unit, onOpenChat: (Conversation) -> Unit) {
    val circles by controller.circles.collectAsState()
    // Recompute as soon as someone signs in.
    val circleMembers by controller.circleMembers.collectAsState()
    val internetOnly by controller.internetOnlyCircles.collectAsState()
    var mode by remember { mutableStateOf<CircleAction?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = circles.filter { matchesSearch(query, it.name) }

    Column(Modifier.fillMaxSize()) {
        // Invitations mostly arrive as a link that opens the app by itself, so accepting one by hand is in the ⋮ menu (ADR 0101).
        AppHeader(texts.setup.tabCircles, menu = listOf(MenuAction(texts.people.acceptInvite, AppIcons.Link) { mode = CircleAction.Menu }))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AppButton(texts.people.startCircle, { mode = CircleAction.Create }, Modifier.fillMaxWidth(), kind = ButtonKind.Main, icon = AppIcons.Plus)
            if (circles.isEmpty()) {
                Text(
                    texts.people.circleExplained,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            } else {
                SearchField(query, { query = it }, texts.people.searchCircles)
                if (shown.isEmpty()) NoSearchResults()
                else AppSection(texts.people.yourCircles) {
                    shown.forEach { circle ->
                        AppRow(
                            title = circle.name,
                            subtitle = circleSummary(circle, circleMembers[circle.id].orEmpty().size)
                                .let { if (circle.id in internetOnly) "$it · ${texts.people.internetOnlyShort}" else it },
                            leading = { CircleAvatar(controller, circle.id, circle.image, 48.dp) },
                            chevron = true,
                            onClickLabel = texts.chat.viewCircle,
                            onClick = { onOpenCircle(circle.id) },
                        )
                    }
                }
            }
        }
    }

    val close = { mode = null }
    when (mode) {
        CircleAction.Menu -> ActionsDialog(texts.people.acceptInvite, onDismiss = close) {
            AppRow(title = texts.people.pasteLink, subtitle = texts.people.pasteInviteLinkHint, icon = AppIcons.Link, onClick = { mode = CircleAction.Join })
            AppRow(title = texts.people.scanQrCode, subtitle = texts.people.inviteFromOtherPhone, icon = AppIcons.Kamera, onClick = { mode = null; scanning = true })
        }
        CircleAction.Create -> CreateCircleDialog(controller, onDismiss = close, onCreated = { onOpenChat(Conversation.Circle(it)) })
        CircleAction.Join -> CircleInputDialog(
            title = texts.people.joinCircle,
            confirm = texts.people.joinCircle,
            limit = null,
            singleLine = false,
            label = texts.people.inviteLink,
            placeholder = "https://anynear.app/k#…",
            onDismiss = close,
            onConfirm = { controller.joinCircle(it) },
        )
        null -> Unit
    }
    if (scanning) QrScanDialog(controller, onDismiss = { scanning = false })
}

private enum class CircleAction { Menu, Create, Join }

/** Found a new circle: the same dialog from „Kreise“ and from „Neue Nachricht“. */
@Composable
internal fun CreateCircleDialog(controller: AppController, onDismiss: () -> Unit, onCreated: (circleId: String) -> Unit = {}) {
    CircleInputDialog(
        title = texts.people.startNewCircle,
        confirm = texts.people.startCircle,
        limit = CircleNameLimit,
        singleLine = true,
        label = texts.people.circleName,
        placeholder = texts.people.circleNamePlaceholder,
        onDismiss = onDismiss,
        // After founding straight into the new circle chat (there: „Lade jemanden ein“).
        onConfirm = { controller.createCircle(it, onCreated = onCreated) },
    )
}

/** One field with confirmation: found a circle (name) or join (link). [onConfirm] returns an error or `null`. */
@Composable
private fun CircleInputDialog(
    title: String,
    confirm: String,
    limit: TextLimit?,
    singleLine: Boolean,
    label: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> String?,
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = limit?.accept(input, it) ?: it; error = null },
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 2,
                    label = { Text(label) },
                    placeholder = { Text(placeholder) },
                    supportingText = limit?.hint(input)?.let { { TextLimitHint(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            AppButton(confirm, enabled = input.isNotBlank(), onClick = {
                error = onConfirm(input)
                if (error == null) onDismiss()
            })
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}
