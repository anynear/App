package app.anynear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anynear.anmp.AnmpCareState
import app.anynear.anmp.core.AliveStatus
import app.anynear.circles.Circle
import app.anynear.i18n.texts
import app.anynear.lifesigns.LifeSignLoudness
import app.anynear.lifesigns.LifeSignTemplate
import app.anynear.lifesigns.LifeSigns
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Section „Lebenszeichen“ on the map (ADR 0083 §1, §5): run the own one, watch others. Starting is in the „+“ of a chat
 * (ADR 0121 §23). Without a running one the section is not shown at all.
 */
@Composable
internal fun LifeSignsSection(
    lifeSigns: LifeSigns,
) {
    val all by lifeSigns.statuses.collectAsState()
    val exactMissing by lifeSigns.exactAlarmMissing.collectAsState()
    val fullScreen by lifeSigns.fullScreen.collectAsState()
    val now = rememberSecondNow()
    val own = all.firstOrNull { it.own && it.phase != AliveStatus.ENDED }
    val watched = all.filter { !it.own && it.phase != AliveStatus.ENDED }
    var ending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lt = texts.lifeSigns
    if (own != null || watched.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(lt.section, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        watched.forEach { WatchedCard(lifeSigns, it, now) }
        if (own != null) {
            OwnCard(lifeSigns, own, now, onConfirm = { scope.launch { lifeSigns.confirm(own.plan.id) } }, onEnd = { ending = true })
        }
        if (exactMissing) {
            AppSection(footer = lt.exactAlarmHint) {
                AppButton(lt.exactAlarmAllow, { lifeSigns.openExactAlarmSettings() }, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet)
            }
        }
    }
    if (fullScreen && own != null) {
        FullScreenCheckIn(lifeSigns, own, now, onConfirm = { scope.launch { lifeSigns.confirm(own.plan.id) } }, onEnd = { ending = true })
    }
    if (ending && own != null) {
        AppDialog(
            onDismissRequest = { ending = false },
            title = { Text(lt.done) },
            text = { Text(lt.endConfirm) },
            confirmButton = {
                AppButton(lt.done, {
                    ending = false
                    scope.launch { lifeSigns.end(own.plan.id) }
                })
            },
            dismissButton = { AppButton(texts.common.cancel, { ending = false }, kind = ButtonKind.Quiet) },
        )
    }
}

/**
 * Only the running own check-in with „Alles gut“ and „Bin fertig“, e.g. on the block screen of a required update
 * (ADR 0096 §5); nothing if none runs.
 */
@Composable
internal fun RunningLifeSignCard(lifeSigns: LifeSigns) {
    val all by lifeSigns.statuses.collectAsState()
    val fullScreen by lifeSigns.fullScreen.collectAsState()
    val own = all.firstOrNull { it.own && it.phase != AliveStatus.ENDED } ?: return
    val now = rememberSecondNow()
    var ending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lt = texts.lifeSigns
    OwnCard(lifeSigns, own, now, onConfirm = { scope.launch { lifeSigns.confirm(own.plan.id) } }, onEnd = { ending = true })
    if (fullScreen) {
        FullScreenCheckIn(lifeSigns, own, now, onConfirm = { scope.launch { lifeSigns.confirm(own.plan.id) } }, onEnd = { ending = true })
    }
    if (ending) {
        AppDialog(
            onDismissRequest = { ending = false },
            title = { Text(lt.done) },
            text = { Text(lt.endConfirm) },
            confirmButton = {
                AppButton(lt.done, {
                    ending = false
                    scope.launch { lifeSigns.end(own.plan.id) }
                })
            },
            dismissButton = { AppButton(texts.common.cancel, { ending = false }, kind = ButtonKind.Quiet) },
        )
    }
}

@Composable
private fun OwnCard(lifeSigns: LifeSigns, s: AliveStatus, now: Long, onConfirm: () -> Unit, onEnd: () -> Unit) {
    val lt = texts.lifeSigns
    val asking = s.phase == AliveStatus.ASK
    val (container, content) = if (asking) AppTheme.colors.warningContainer to AppTheme.colors.onWarningContainer else
        MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurface
    val (helpers, heard) = lifeSigns.helpers(s)
    Surface(shape = MaterialTheme.shapes.medium, color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                listOf(s.plan.label, lifeSigns.circle(LifeSigns.targetOf(s.plan))).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
            )
            if (asking) {
                Text(lt.askNow, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            } else {
                Text(lt.runningSince(formatTime(s.plan.start)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(lt.nextQuestion(formatFutureDayAndTime(s.plan.due, now)), style = MaterialTheme.typography.bodyMedium)
            }
            // Honest: only from receipts (ADR 0083 §5.1).
            Text(
                if (heard.isEmpty()) lt.heardByNobody else lt.heardBy(heard.size, helpers.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (s.notArriving) Text(lt.notArriving, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (s.looking.isNotEmpty()) Text(lt.lookingBy(s.looking.joinToString(", ") { lifeSigns.name(it) }), style = MaterialTheme.typography.bodyMedium)
            if (s.away.isNotEmpty()) Text(lt.awayBy(s.away.joinToString(", ") { lifeSigns.name(it) }), style = MaterialTheme.typography.bodyMedium)
            // Honest about the place too (ADR 0121 §10): from when the one is that would go along, or that there is none.
            if (lifeSigns.sharesPlace(LifeSigns.targetOf(s.plan))) {
                Text(
                    s.plan.position?.let { lt.ownPlaceShared(formatDayAndTime(it.at, now)) } ?: lt.ownPlaceUnknown,
                    style = MaterialTheme.typography.bodyMedium,
                )
                // What the phone does not allow, as a line to fix it (ADR 0125 §4, §5).
                lifeSigns.access?.let {
                    LocationAccessRow(it, app.anynear.location.LocationNeed.PreciseWhileActive, app.anynear.location.LocationPurpose.LifeSign, texts.location.lifeSignMissing)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                AppButton(lt.allGood, onConfirm, Modifier.weight(1f), kind = if (asking) ButtonKind.Main else ButtonKind.Primary, icon = AppIcons.Haken)
                AppButton(lt.done, onEnd, Modifier.weight(1f), kind = ButtonKind.Secondary)
            }
            AppButton(lt.bigButton, { lifeSigns.openFullScreen() }, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet)
        }
    }
}

@Composable
private fun WatchedCard(lifeSigns: LifeSigns, s: AliveStatus, now: Long) {
    val lt = texts.lifeSigns
    val scope = rememberCoroutineScope()
    val who = lifeSigns.name(s.plan.owner)
    val (container, content) = when (s.phase) {
        AliveStatus.ALARM -> AppTheme.colors.alarmContainer to AppTheme.colors.onAlarmContainer
        AliveStatus.HINT -> AppTheme.colors.warningContainer to AppTheme.colors.onWarningContainer
        else -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurface
    }
    val meAway = lifeSigns.meAway(s)
    var loudness by remember(LifeSigns.targetOf(s.plan)) { mutableStateOf(lifeSigns.loudness(LifeSigns.targetOf(s.plan))) }
    Surface(shape = MaterialTheme.shapes.medium, color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(lifeSigns.circle(LifeSigns.targetOf(s.plan)), style = MaterialTheme.typography.labelMedium)
            val title = when (s.phase) {
                AliveStatus.ALARM -> if (s.reason == AliveStatus.REASON_MISSED) lt.missedTitle(who) else lt.unreachableTitle(who)
                AliveStatus.HINT -> if (s.reason == AliveStatus.REASON_MISSED) lt.missedTitle(who) else lt.hintTitle(who)
                else -> lt.watchTitle(who, s.plan.label)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                when (s.phase) {
                    AliveStatus.ALARM -> if (s.reason == AliveStatus.REASON_MISSED) lt.missedText else lt.unreachableText(formatTime(s.plan.lastAt))
                    AliveStatus.HINT -> lt.hintText
                    else -> lt.lastSign(formatDayAndTime(s.plan.confirmedAt.takeIf { it > 0 } ?: s.plan.start, now))
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (meAway) Text(lt.awayActive, style = MaterialTheme.typography.bodyMedium)
            if (s.looking.isNotEmpty()) Text(lt.lookingBy(s.looking.joinToString(", ") { lifeSigns.name(it) }), style = MaterialTheme.typography.bodyMedium)
            // The place that came with „nicht bestätigt“ (ADR 0121 §10), with its time and how accurate it is.
            s.plan.position?.let { place ->
                Text(
                    listOfNotNull(lt.placeFrom(formatDayAndTime(place.at, now)), place.accuracy?.let { lt.placeAccuracy(formatAccuracy(it)) }).joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                AppButton(lt.showOnMap, { lifeSigns.openMap(s) }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (s.phase == AliveStatus.HINT || s.phase == AliveStatus.ALARM) {
                    AppButton(lt.looking, { scope.launch { lifeSigns.care(s.plan.id, AnmpCareState.LOOKING) } }, Modifier.weight(1f), kind = ButtonKind.Main)
                } else if (meAway) {
                    AppButton(lt.back, { scope.launch { lifeSigns.care(s.plan.id, AnmpCareState.BACK) } }, Modifier.weight(1f), kind = ButtonKind.Secondary)
                } else {
                    AppButton(lt.away, { scope.launch { lifeSigns.care(s.plan.id, AnmpCareState.AWAY) } }, Modifier.weight(1f), kind = ButtonKind.Quiet)
                }
            }
            // How loud warnings from this circle are here (ADR 0083 §1).
            Text(lt.loudness, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeSignLoudness.entries.forEach { v ->
                    FilterChip(
                        selected = loudness == v,
                        // Clearly selected, also on the coloured card for hint and alarm.
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        onClick = {
                            lifeSigns.setLoudness(LifeSigns.targetOf(s.plan), v)
                            loudness = v
                        },
                        label = { Text(when (v) { LifeSignLoudness.Loud -> lt.loud; LifeSignLoudness.Normal -> lt.normal; LifeSignLoudness.Off -> lt.off }) },
                    )
                }
            }
        }
    }
}

/**
 * Starting a check-in (ADR 0083 §5.1). From the map the person chooses the circle. From the „+“ of a conversation
 * (ADR 0121 §13, §23) it is settled where it goes: [fixedCircle], or [fixedPerson] (person ID) with [fixedName] for a
 * check-in that goes to that one person.
 */
@Composable
internal fun LifeSignStartDialog(
    lifeSigns: LifeSigns,
    circles: List<Circle>,
    onDismiss: () -> Unit,
    onCreateCircle: () -> Unit = {},
    fixedCircle: String? = null,
    fixedPerson: String? = null,
    fixedName: String = "",
) {
    val lt = texts.lifeSigns
    val fixed = fixedCircle != null || fixedPerson != null
    val usable = if (fixed) circles.filter { it.id == fixedCircle } else circles
    var template by remember { mutableStateOf(LifeSignTemplate.Work) }
    var circleId by remember { mutableStateOf(fixedCircle ?: lifeSigns.preselect(usable)) }
    var hours by remember { mutableStateOf(2) }
    var minutes by remember { mutableStateOf(30) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(lt.startTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // The explanation stood under the start button before; since starting is in the ⋮ menu it is here (ADR 0101).
                Text(lt.intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (usable.isEmpty() && fixedPerson == null) {
                    // No circle yet: straight to creating one (afterwards its chat opens with „Lade jemanden ein“).
                    Text(lt.noCircle)
                    AppButton(lt.createCircle, onCreateCircle, Modifier.fillMaxWidth(), kind = ButtonKind.Main, icon = AppIcons.Plus)
                    return@Column
                }
                Text(lt.startText)
                LifeSignTemplate.entries.forEach { t ->
                    val (name, info) = when (t) {
                        LifeSignTemplate.Work -> lt.templateWork to lt.templateWorkText
                        LifeSignTemplate.Outside -> lt.templateOutside to lt.templateOutsideText
                        LifeSignTemplate.Home -> lt.templateHome to lt.templateHomeText
                        LifeSignTemplate.Daily -> lt.templateDaily to lt.templateDailyText
                        LifeSignTemplate.Custom -> lt.templateCustom to lt.customText(hours, minutes)
                    }
                    Row(Modifier.fillMaxWidth().clickable { template = t }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = template == t, onClick = { template = t })
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(info, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (template == LifeSignTemplate.Custom) {
                    Text(lt.customHours, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 4, 8, 12).forEach { h -> FilterChip(hours == h, { hours = h }, label = { Text("$h") }) }
                    }
                    Text(lt.customMinutes, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 30, 60, 120).forEach { m -> FilterChip(minutes == m, { minutes = m }, label = { Text("$m") }) }
                    }
                }
                if (fixed) {
                    Text(lt.goesToOne(fixedName), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                } else Text(lt.goesTo, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                if (!fixed) usable.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { circleId = c.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = circleId == c.id, onClick = { circleId = c.id })
                        val known = lifeSigns.helpersOf(c.id).size
                        Text(if (known > 0) lt.circleWithCount(c.name, known) else lt.circleNobodyYet(c.name), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(lt.notEmergency, style = MaterialTheme.typography.bodySmall)
                if (failed) Text(lt.startFailed, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            if (usable.isNotEmpty() || fixedPerson != null) AppButton(lt.startNow, {
                val id = circleId ?: if (fixedPerson != null) "" else return@AppButton
                busy = true
                scope.launch {
                    val ok = lifeSigns.start(template, id, hours, minutes, person = fixedPerson)
                    busy = false
                    if (ok) onDismiss() else failed = true
                }
            }, enabled = (circleId != null || fixedPerson != null) && !busy)
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/**
 * Full screen with one big button (ADR 0083 §5.2): for outdoors, with gloves or without glasses. Over everything,
 * also after tapping the question or the ongoing notification.
 */
@Composable
private fun FullScreenCheckIn(lifeSigns: LifeSigns, s: AliveStatus, now: Long, onConfirm: () -> Unit, onEnd: () -> Unit) {
    val lt = texts.lifeSigns
    val asking = s.phase == AliveStatus.ASK
    val (helpers, heard) = lifeSigns.helpers(s)
    Dialog(onDismissRequest = { lifeSigns.closeFullScreen() }, properties = AppFullScreenDialogProperties) {
        Surface(Modifier.fillMaxSize(), color = if (asking) AppTheme.colors.warningContainer else MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(listOf(s.plan.label, lifeSigns.circle(LifeSigns.targetOf(s.plan))).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (asking) lt.askNow else lt.nextQuestion(formatFutureDayAndTime(s.plan.due, now)),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    )
                    Text(if (heard.isEmpty()) lt.heardByNobody else lt.heardBy(heard.size, helpers.size), style = MaterialTheme.typography.bodyLarge)
                    if (s.notArriving) Text(lt.notArriving, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
                BigRoundButton(lt.allGood, AppIcons.Haken, onConfirm)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    AppButton(lt.done, onEnd, Modifier.weight(1f), kind = ButtonKind.Secondary)
                    AppButton(lt.close, { lifeSigns.closeFullScreen() }, Modifier.weight(1f), kind = ButtonKind.Quiet)
                }
            }
        }
    }
}

/**
 * One button, as big as it gets (ADR 0083 §5.2): „Alles gut“ in the check-in's full screen and on the alarm full screen
 * over the lock screen, there also „Ich schaue nach“.
 */
@Composable
internal fun BigRoundButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth(0.85f).widthIn(max = 360.dp).aspectRatio(1f).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(72.dp))
                Text(label, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Clock for texts that change by the minute („Nächste Frage: …“). */
@Composable
private fun rememberSecondNow(): Long {
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = Clock.System.now().toEpochMilliseconds()
        }
    }
    return now
}
