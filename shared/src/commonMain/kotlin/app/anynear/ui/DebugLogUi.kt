package app.anynear.ui

import app.anynear.anmp.core.StreamStats
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.anynear.AppConfig
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.app.DebugLog
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/** Log as text, one line per entry with the time to the second, oldest first. */
internal fun debugLogText(entries: List<DebugLog.Entry>, version: String?, stream: StreamStats? = null): String = buildString {
    appendLine(texts.settings.logHeader(version))
    if (stream != null && stream.connects + stream.drops.size > 0) appendLine(streamSummary(stream))
    entries.forEach { e ->
        val seconds = (e.atMillis / 1000 % 60).toString().padStart(2, '0')
        appendLine("${formatTime(e.atMillis)}:$seconds ${e.tag}: ${e.message}")
    }
}

/** File name for the download: „anynear-protokoll-2026-11-11-1432.txt“ (UTC), so several downloads don't overwrite each other. */
internal fun debugLogFileName(atMillis: Long): String {
    val stamp = Instant.fromEpochMilliseconds(atMillis).toString()
    val date = stamp.take(10)
    val time = stamp.drop(11).take(5).replace(":", "")
    val base = texts.support.logFileName.substringBeforeLast('.')
    return "${AppConfig.APP_NAME}-$base-$date-$time.txt"
}

/** Under „Erweitert“: view, share and download the log for troubleshooting, without a USB cable. */
@Composable
internal fun DebugLogRow(controller: AppController) {
    val entries by controller.debugLog.entries.collectAsState()
    val stream by controller.streamStats.collectAsState()
    var open by rememberSaveable { mutableStateOf(false) }
    AppRow(
        title = texts.settings.debugLog,
        subtitle = if (entries.isEmpty()) texts.settings.logEmpty else texts.settings.logEntries(entries.size),
        icon = AppIcons.Dokument,
        chevron = true,
        onClick = { open = true },
    )
    if (!open) return
    val text = debugLogText(entries, controller.appVersion, stream)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var hint by remember { mutableStateOf<String?>(null) }
    val download: () -> Unit = {
        scope.launch {
            val name = debugLogFileName(Clock.System.now().toEpochMilliseconds())
            hint = when (val result = saveToDevice(text.encodeToByteArray(), name, "text/plain")) {
                is SaveResult.Saved -> texts.chat.savedIn(result.where)
                SaveResult.Handed -> null
                SaveResult.Failed -> texts.chat.saveFailed
            }
        }
    }
    // Newest lines at the bottom, so go straight there.
    LaunchedEffect(entries.size) { scroll.scrollTo(scroll.maxValue) }
    AppDialog(
        onDismissRequest = { open = false; hint = null },
        title = { Text(texts.settings.log) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    texts.settings.logInfo,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(scroll),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(texts.chat.download, download, kind = ButtonKind.Quiet, icon = AppIcons.Speichern, enabled = entries.isNotEmpty())
                    AppButton(texts.settings.clearLog, controller.debugLog::clear, kind = ButtonKind.Quiet, enabled = entries.isNotEmpty())
                }
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { AppButton(texts.common.share, { controller.shareDebugLog(text) }, enabled = entries.isNotEmpty()) },
        dismissButton = { AppButton(texts.common.close, { open = false; hint = null }, kind = ButtonKind.Quiet) },
    )
}

/**
 * Counters of receiving via the stream (ADR 0072 §6) as one technical line at the top of the log, like the lines of
 * the core without translation: home relay, connections, drops by reason, switches, catch-up finds, last durations.
 */
internal fun streamSummary(s: StreamStats): String =
    "Stream: home=${s.home.ifEmpty { "-" }} connects=${s.connects} drops=${s.drops.entries.joinToString(",") { "${it.key}:${it.value}" }.ifEmpty { "0" }} " +
        "switches=${s.homeSwitches} catchups=${s.catchUps} missed=${s.missed} last=${s.lastSeconds.takeLast(5).joinToString(",") { "${it}s" }}"
