package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.app.RadioCheckResult
import app.anynear.app.SignalLevel
import app.anynear.i18n.texts
import app.anynear.mesh.LinkState
import kotlinx.coroutines.delay
import kotlin.time.Clock
import app.anynear.mesh.radioNumberLabel

/**
 * Radio check for a person (ADR 0053): „Über Funk prüfen“ and the last result per radio, one sentence with
 * names, details under „Mehr dazu“. Without a connected radio only the hint, without a known radio of the
 * person only one sentence; entirely without a paired radio the section does not exist.
 */
@Composable
internal fun RadioCheckPersonSection(controller: AppController, peer: String, name: String) {
    val paired by controller.pairedDevice.collectAsState()
    val messages by controller.messages.collectAsState()
    val people by controller.people.collectAsState()
    if (paired == null) return
    val radios = remember(messages, people, peer) { controller.knownRadios(peer) }
    if (radios.isEmpty()) {
        AppSection(texts.people.overRadio) { AppRow(title = texts.people.noRadioKnown(name), icon = AppIcons.Funk) }
        return
    }
    RadioCheckBlock(controller, radios, group = null)
}

/**
 * Group check (ADR 0053 §3) across the radios of [nodes], e.g. of all members of a circle or of all contacts
 * ([group] as the key of the limit). Result as a list with „2 von 3 antworten“.
 */
@Composable
internal fun RadioCheckGroupSection(controller: AppController, group: String, nodes: List<Int>) {
    val paired by controller.pairedDevice.collectAsState()
    if (paired == null) return
    if (nodes.isEmpty()) {
        AppSection(texts.people.overRadio) { AppRow(title = texts.people.noRadiosKnownHere, icon = AppIcons.Funk) }
        return
    }
    RadioCheckBlock(controller, nodes, group)
}

@Composable
private fun RadioCheckBlock(controller: AppController, nodes: List<Int>, group: String?) {
    val ready by controller.radioCheckReady.collectAsState()
    val linkState by controller.linkState.collectAsState()
    val results by controller.radioChecks.results.collectAsState()
    val running by controller.radioChecks.running.collectAsState()
    val runningGroups by controller.radioChecks.runningGroups.collectAsState()
    val groupStarts by controller.radioChecks.groupStarts.collectAsState()
    val messages by controller.messages.collectAsState()
    val people by controller.people.collectAsState()
    // Names of the users per radio search the history; only anew when something changes.
    val labels = remember(nodes, messages, people) { nodes.associateWith { radioLabel(controller, it) } }
    var details by remember { mutableStateOf<RadioCheckResult?>(null) }
    // So that the lock opens again by itself, recompute once per minute.
    var now by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = Clock.System.now().toEpochMilliseconds()
        }
    }

    val nodeResults = nodes.mapNotNull { results[it] }
    val groupRunning = group != null && group in runningGroups
    val againAt: Long? = if (group != null) {
        groupStarts[group]?.let { controller.radioChecks.nextGroupCheckAt(group, now) }
    } else {
        nodes.mapNotNull { controller.radioChecks.nextCheckAt(it, now) }.takeIf { it.size == nodes.size }?.minOrNull()
    }
    val busy = groupRunning || nodes.any { it in running }

    AppSection(
        texts.people.overRadio,
        footer = when {
            !ready -> null
            groupRunning -> texts.people.groupCheckRunning
            againAt != null && !busy -> texts.people.checkAgainFrom(formatTime(againAt))
            else -> texts.people.radioCheckFooter
        },
    ) {
        if (!ready) {
            // Connected, but the channel `anynear` is still missing: „verbinde zuerst“ would be wrong
            // (hardware test 02.11.2026).
            val title = if (linkState == LinkState.Connected) texts.people.radioNotSetUp else texts.people.connectRadioFirst
            AppRow(title = title, icon = AppIcons.Funkgeraet)
        } else {
            AppRow(
                title = if (group != null) texts.people.whoAnswersOverRadio else texts.people.checkOverRadio,
                icon = AppIcons.Funk,
                enabled = !busy && againAt == null,
                onClick = {
                    if (group != null) controller.radioChecks.checkGroup(group, nodes) else controller.radioChecks.check(nodes)
                },
            )
        }
        if (group != null && nodeResults.isNotEmpty()) {
            Text(
                texts.people.answersCount(nodeResults.count { it.answered }, nodes.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        nodes.forEach { node ->
            val radio = labels[node] ?: radioLabel(controller, node)
            val result = results[node]
            when {
                node in running -> AppRow(title = texts.people.radioChecking(radio), icon = AppIcons.Uhr)
                result != null -> AppRow(
                    title = resultSentence(radio, result),
                    subtitle = lastReachedLabel(result.atMillis, now),
                    icon = AppIcons.Funk,
                    chevron = true,
                    onClickLabel = texts.common.moreInfo,
                    onClick = { details = result },
                )
            }
        }
    }

    details?.let { result ->
        val radio = labels[result.nodeNum] ?: radioLabel(controller, result.nodeNum)
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text(resultSentence(radio, result)) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val lines = buildList {
                        if (!result.answered) add(texts.people.noAnswerExplained)
                        result.snr?.let { add(texts.people.signalLine(SignalLevel.of(it), it, result.rssi)) }
                        result.hops?.let { add(texts.people.forwardsLine(it)) }
                        result.durationMillis?.let { add(texts.people.answerAfter((it + 500) / 1000)) }
                        add(texts.people.checkedAt(lastReachedLabel(result.atMillis, now)))
                        add(texts.people.radioNumber(radioNumberLabel(result.nodeNum)))
                    }
                    lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = { AppButton(texts.common.ok, { details = null }, kind = ButtonKind.Quiet) },
        )
    }
}

/** „Das Funkgerät von Paul“, for shared radios all names (ADR 0022); never „Paul antwortet“. */
private fun radioLabel(controller: AppController, node: Int): String =
    texts.people.radioOf(controller.radioUsers(node), controller.displayName(node))

private fun resultSentence(radio: String, result: RadioCheckResult): String = when {
    !result.answered -> texts.people.radioNoAnswer(radio)
    result.hops == null -> texts.people.radioAnswers(radio)
    result.hops == 0 -> texts.people.radioAnswersDirect(radio)
    else -> texts.people.radioAnswersVia(radio, result.hops)
}
