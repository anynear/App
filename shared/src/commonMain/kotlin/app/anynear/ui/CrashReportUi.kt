package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.mesh.Conversation
import kotlinx.coroutines.launch

/**
 * Card at the top of „Nachrichten“ after a crash (ADR 0082): one sentence, „Ansehen und senden“ and „Nicht senden“.
 * No dialog at start; sending happens only from within the dialog.
 */
@Composable
internal fun CrashCard(controller: AppController, onOpenChat: (Conversation) -> Unit) {
    val reports by controller.crashReports.collectAsState()
    if (reports.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    AppSection(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        SectionContent {
            Text(texts.support.crashCardTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                texts.support.crashCardText(reports.sumOf { it.count }),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppButton(texts.support.crashReview, { open = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            AppButton(texts.support.crashDontSend, controller::dismissCrashReports, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet)
        }
    }
    if (open) CrashReportDialog(controller, onDismiss = { open = false }, onOpenChat = onOpenChat)
}

/** Row under „Fehlerbehebung“, as long as there are reports. */
@Composable
internal fun CrashReportsRow(controller: AppController, onOpenChat: (Conversation) -> Unit) {
    val reports by controller.crashReports.collectAsState()
    if (reports.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    AppRow(
        title = texts.support.crashRow,
        subtitle = texts.support.crashRowCount(reports.sumOf { it.count }),
        icon = AppIcons.Dokument,
        chevron = true,
        onClick = { open = true },
    )
    if (open) CrashReportDialog(controller, onDismiss = { open = false }, onOpenChat = onOpenChat, withDelete = true)
}

/**
 * View and send a report: short sentence, optional note, expandable „Was geschickt wird“. Without a support identity
 * it says „Teilen“ instead of „Senden“ and the report stays where it is.
 */
@Composable
private fun CrashReportDialog(
    controller: AppController,
    onDismiss: () -> Unit,
    onOpenChat: (Conversation) -> Unit,
    withDelete: Boolean = false,
) {
    val entries by controller.debugLog.entries.collectAsState()
    var note by rememberSaveable { mutableStateOf("") }
    var showText by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val available = controller.supportAvailable
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(texts.support.crashDialogTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (available) texts.support.crashDialogInfo else texts.support.notAvailable, style = MaterialTheme.typography.bodyMedium)
                if (available) {
                    OutlinedTextField(
                        note, { note = it }, minLines = 2, enabled = !busy,
                        label = { Text(texts.support.crashNote) }, modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppButton(texts.support.crashWhatIsSent, { showText = !showText }, kind = ButtonKind.Quiet)
                if (showText) {
                    Text(
                        controller.crashReportText(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    )
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                if (withDelete) {
                    AppButton(texts.support.crashDelete, { controller.dismissCrashReports(); onDismiss() }, kind = ButtonKind.Quiet, enabled = !busy)
                }
            }
        },
        confirmButton = {
            if (available) {
                AppButton(
                    if (busy) texts.support.sending else texts.support.send,
                    {
                        busy = true
                        error = null
                        scope.launch {
                            val log = debugLogText(entries, controller.appVersion, controller.streamStats.value)
                            when (val r = controller.sendCrashReports(note, log)) {
                                is AppController.SupportResult.Sent -> {
                                    controller.showNotice(texts.support.sent)
                                    onDismiss()
                                    onOpenChat(r.conversation)
                                }
                                is AppController.SupportResult.Failed -> error = r.reason
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    icon = AppIcons.Senden,
                )
            } else {
                AppButton(texts.common.share, controller::shareCrashReports)
            }
        },
        dismissButton = { AppButton(texts.common.close, onDismiss, kind = ButtonKind.Quiet, enabled = !busy) },
    )
}
