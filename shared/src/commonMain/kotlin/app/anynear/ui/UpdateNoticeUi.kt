package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.app.AppUpdateState
import app.anynear.app.UpdateNotice
import app.anynear.i18n.texts

/**
 * One band for every reason to update (ADR 0096 §4, together with `clients.min` from ADR 0061 decision 13):
 * the day from which only the new version works, or „Bitte aktualisiere …“. Buttons „Aktualisieren“
 * (only where the app can do something) and „Später“.
 */
@Composable
internal fun UpdateBanner(controller: AppController, notice: UpdateNotice) {
    val text = when (notice) {
        is UpdateNotice.Soon -> texts.system.updateSoon(formatDayMonth(notice.from))
        UpdateNotice.Needed -> texts.system.updateNeeded
        else -> return
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        val state by controller.appUpdate.collectAsState()
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            // Started from here: show the same progress as under „Über anynear“, not a silent download.
            val current = state
            val running = current is AppUpdateState.Downloading || current is AppUpdateState.NeedsPermission ||
                current is AppUpdateState.Installing || current is AppUpdateState.Failed
            if (running) UpdateProgress(current, intro = "")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppButton(texts.common.later, controller::postponeUpdateNotice, kind = ButtonKind.Quiet)
                val busy = current is AppUpdateState.Downloading || current is AppUpdateState.Installing
                if (controller.canStartUpdate && !busy) AppButton(texts.setup.update, controller::startUpdate, kind = ButtonKind.Quiet)
            }
        }
    }
}

/**
 * Full screen over the app when the network requires a newer build and there is a way to it (ADR 0096 §5, §6).
 * Only the interface is covered; the core keeps receiving, notifying and alarming. Reachable from here: the update,
 * a running check-in (ADR 0083) and saving a backup (ADR 0027). Back does not close it.
 */
@Composable
internal fun UpdateBlockScreen(controller: AppController) {
    // Back does nothing here: it must not uncover the app.
    SystemBackHandler(enabled = true, onBack = {})
    val state by controller.appUpdate.collectAsState()
    var backingUp by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Column(Modifier.widthIn(max = MAX_CONTENT_WIDTH), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    texts.system.updateBlockTitle,
                    Modifier.fillMaxWidth().semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(texts.system.updateBlockText, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                // Own updater (Android `direct`) or Google Play: the progress of the same flow as under „Über anynear“.
                val current = state
                if (current is AppUpdateState.Downloading || current is AppUpdateState.NeedsPermission ||
                    current is AppUpdateState.Installing || current is AppUpdateState.Failed
                ) {
                    UpdateProgress(current, intro = "")
                }
                val busy = current is AppUpdateState.Downloading || current is AppUpdateState.Installing
                AppButton(texts.settings.updateNow, controller::startUpdate, Modifier.fillMaxWidth(), kind = ButtonKind.Main, enabled = !busy)
                controller.lifeSigns?.let { RunningLifeSignCard(it) }
                if (controller.backupAvailable) {
                    AppButton(texts.system.saveBackup, { backingUp = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = AppIcons.Speichern)
                }
            }
        }
    }
    if (backingUp) CreateBackupDialog(controller) { backingUp = false }
}
