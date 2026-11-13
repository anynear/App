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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.app.Support
import app.anynear.app.SupportKind
import app.anynear.i18n.texts
import app.anynear.mesh.Conversation
import kotlinx.coroutines.launch

/** Troubleshooting (⋮ menu): report an error as a chat with the support (ADR 0065), below it the log. */
@Composable
internal fun TroubleshootingScreen(controller: AppController, onBack: () -> Unit, onOpenChat: (Conversation) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppHeader(texts.support.troubleshooting, leading = { HeaderBackButton(onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SupportFormSection(controller, SupportKind.Problem, onOpenChat)
            AppSection(texts.support.logSection) {
                CrashReportsRow(controller, onOpenChat)
                DebugLogRow(controller)
            }
        }
    }
}

/**
 * Shared form for errors and questions. Sending creates a separate circle with the support and opens it; without
 * a configured support, sending is off and a sentence says why. On an error the content stays.
 */
@Composable
internal fun SupportFormSection(controller: AppController, kind: SupportKind, onOpenChat: (Conversation) -> Unit) {
    var subject by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var withLog by rememberSaveable { mutableStateOf(true) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val entries by controller.debugLog.entries.collectAsState()
    val scope = rememberCoroutineScope()
    val available = controller.supportAvailable
    val problem = kind == SupportKind.Problem
    AppSection(
        if (problem) texts.support.reportSection else texts.support.askSection,
        footer = if (available) (if (problem) texts.support.reportFooter else texts.support.askFooter) else texts.support.notAvailable,
    ) {
        SectionContent {
            OutlinedTextField(
                subject, { subject = it.take(Support.MAX_SUBJECT) }, singleLine = true, enabled = !busy,
                label = { Text(texts.support.subject) }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                description, { description = it }, minLines = 4, enabled = !busy,
                label = { Text(texts.support.description) }, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (problem) {
            AppRow(
                title = texts.support.attachLog,
                subtitle = texts.support.attachLogHint,
                icon = AppIcons.Dokument,
                trailing = { Switch(checked = withLog, onCheckedChange = { withLog = it }, enabled = !busy) },
            )
        }
        SectionContent {
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            AppButton(
                if (busy) texts.support.sending else texts.support.send,
                {
                    busy = true
                    error = null
                    scope.launch {
                        val log = if (problem && withLog) debugLogText(entries, controller.appVersion, controller.streamStats.value) else null
                        when (val r = controller.sendSupportRequest(kind, subject, description, log)) {
                            is AppController.SupportResult.Sent -> {
                                subject = ""
                                description = ""
                                controller.showNotice(texts.support.sent)
                                onOpenChat(r.conversation)
                            }
                            is AppController.SupportResult.Failed -> error = r.reason
                        }
                        busy = false
                    }
                },
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Main,
                icon = AppIcons.Senden,
                enabled = available && !busy && subject.isNotBlank() && description.isNotBlank(),
            )
        }
    }
}
