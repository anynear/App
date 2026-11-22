package app.anynear.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.anynear.anmp.core.SosCall
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.sos.Sos
import app.anynear.sos.SosHelpers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The SOS sign in the header of every main page, next to the radio (ADR 0121 §13). One tap opens the page where the
 * person holds a button to call its helpers; five quick taps open it with a countdown that sends by itself.
 */
@Composable
internal fun SosHeaderButton(controller: AppController) {
    val sos = controller.sos ?: return
    val calls by sos.calls.collectAsState()
    val st = texts.sos
    var taps by remember { mutableStateOf(0) }
    // A single tap waits a moment for more: five in a row are the quick way.
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        if (taps >= QUICK_TAPS) {
            taps = 0
            sos.countDown()
            return@LaunchedEffect
        }
        delay(TAP_WAIT_MILLIS)
        taps = 0
        sos.open()
    }
    // Someone asks to have this person as SOS contact: the number on the button leads to the question.
    val asks by sos.asks.collectAsState()
    val running = calls.any { !it.ended }
    val helpers by sos.helpers.collectAsState()
    // Not set up yet (19.11.2026): an empty ring with a dot instead of the full red button, so that one sees at a
    // glance that it does not work yet and that something is left to do.
    val ready = !helpers.isEmpty
    val red = AppTheme.colors.alarm
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        // A call runs (19.11.2026): the sign pulses, a ring that grows and fades, so that one sees it is active.
        if (running && ready) {
            val pulse = rememberInfiniteTransition()
            val grow by pulse.animateFloat(1f, 1.55f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart))
            val fade by pulse.animateFloat(0.55f, 0f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart))
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = grow; scaleY = grow; alpha = fade }.clip(CircleShape).background(red))
        }
        Box(
            Modifier.fillMaxSize().clip(CircleShape)
                .then(if (ready) Modifier.background(red.copy(alpha = if (running) 1f else 0.92f)) else Modifier.border(2.dp, red.copy(alpha = 0.55f), CircleShape))
                .clickable(onClickLabel = if (ready) st.buttonLabel else st.buttonSetUp, role = Role.Button) { if (ready) taps++ else sos.open() }
                .then(if (asks.isNotEmpty()) Modifier.semantics { stateDescription = st.openAsksCount(asks.size) } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(st.button, color = if (ready) AppTheme.colors.onAlarm else red.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
        // Outside the round clip, so that the dot sits on the ring whole.
        // Open questions win over the dot: they are the next thing to do. The number is in the spoken state already.
        if (asks.isNotEmpty()) OpenAsksBadge(asks.size, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
        else if (!ready) Box(Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(BrandPalette.Signalorange))
    }
}

/** The number of open SOS questions, as on the tabs. Silent for screen readers: the holder says it. */
@Composable
private fun OpenAsksBadge(count: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Badge(containerColor = scheme.secondary, contentColor = scheme.onSecondary, modifier = modifier.clearAndSetSemantics {}) {
        Text(if (count > 99) "99+" else count.toString())
    }
}

private const val QUICK_TAPS = 5
private const val TAP_WAIT_MILLIS = 450L

/** `true` while a call runs, the own one or one of someone we help. */
@Composable
internal fun sosRunning(controller: AppController): Boolean {
    val sos = controller.sos ?: return false
    val calls by sos.calls.collectAsState()
    val raising by sos.raising.collectAsState()
    val failed by sos.failed.collectAsState()
    return raising || failed || calls.any { !it.ended }
}

/** A line across the top of the app while a call runs, the own one or one of someone we help; a tap opens it. */
@Composable
internal fun SosBanner(controller: AppController) {
    val sos = controller.sos ?: return
    val calls by sos.calls.collectAsState()
    val raising by sos.raising.collectAsState()
    val failed by sos.failed.collectAsState()
    val call = calls.firstOrNull { !it.ended && !it.own } ?: calls.firstOrNull { !it.ended }
    if (call == null) {
        // Going out, or it did not go out: said on every page as well.
        if (!raising && !failed) return
        Surface(color = AppTheme.colors.alarm, contentColor = AppTheme.colors.onAlarm, modifier = Modifier.fillMaxWidth().clickable { sos.open() }) {
            Text(if (raising) texts.sos.sending else texts.sos.failedBanner, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
        return
    }
    Surface(color = AppTheme.colors.alarm, contentColor = AppTheme.colors.onAlarm, modifier = Modifier.fillMaxWidth().clickable { if (call.own) sos.open() else sos.open(call.id) }) {
        Text(
            when {
                call.own -> if (call.plan != null) texts.sos.ownMissedBanner else texts.sos.ownBanner
                call.plan != null -> sos.alarmTexts(call).first
                else -> texts.sos.activeBanner(sos.name(call.owner))
            },
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/**
 * The question of someone who asks this person to be its SOS contact (ADR 0121 §25, ADR 0123 §3), opened from a list
 * of open questions; never by itself. Yes lets its calls ring here; no, or no answer, and they are only shown.
 */
@Composable
private fun SosAskDialog(sos: Sos) {
    val st = texts.sos
    val asks by sos.asks.collectAsState()
    val asking by sos.asking.collectAsState()
    val scope = rememberCoroutineScope()
    val ask = asks.firstOrNull { it.person == asking } ?: return
    val name = sos.name(ask.person)
    AppDialog(
        onDismissRequest = sos::closeAsk,
        title = { Text(st.askTitle(name)) },
        text = { Text(st.askText(name)) },
        confirmButton = { AppButton(texts.map.sharingAccept, { scope.launch { sos.answer(ask.person, true) } }) },
        dismissButton = { AppButton(texts.map.sharingDecline, { scope.launch { sos.answer(ask.person, false) } }, kind = ButtonKind.Quiet) },
    )
}

/** The full-screen page of [Sos.screen]: calling, the running own call, or the call of someone we help. */
@Composable
internal fun SosScreenHost(controller: AppController) {
    val sos = controller.sos ?: return
    val screen by sos.screen.collectAsState()
    val calls by sos.calls.collectAsState()
    SosAskDialog(sos)
    val id = screen ?: return
    Dialog(onDismissRequest = sos::close, properties = AppFullScreenDialogProperties) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            val other = calls.firstOrNull { it.id == id && !it.own }
            val own = calls.filter { it.own && !it.ended }.minByOrNull { if (it.plan == null) 0 else 1 }
            when {
                other != null -> HelperPage(controller, sos, other)
                own != null -> RunningPage(controller, sos, own)
                else -> RaisePage(controller, sos)
            }
        }
    }
}

@Composable
private fun PageFrame(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            AppButton(texts.sos.close, onClose, kind = ButtonKind.Quiet)
        }
        content()
    }
}

/** The page for calling: the helpers it goes to, the button to hold, and the honest sentence about 112. */
@Composable
private fun RaisePage(controller: AppController, sos: Sos) {
    val st = texts.sos
    val helpers by sos.helpers.collectAsState()
    val counting by sos.counting.collectAsState()
    val circles by controller.circles.collectAsState()
    val people by controller.people.collectAsState()
    var choosing by remember { mutableStateOf(false) }
    // The call itself runs in [Sos], not in this page: closing the page must never stop it (19.11.2026).
    val busy by sos.raising.collectAsState()
    val failed by sos.failed.collectAsState()
    val left by sos.countdownLeft.collectAsState()
    if (helpers.isEmpty || choosing) {
        HelpersPage(controller, sos, onDone = { choosing = false })
        return
    }
    val names = circles.filter { it.id in helpers.circles }.map { it.name } + people.filter { it.deviceId in helpers.persons }.map { it.name }
    PageFrame(st.title, sos::close) {
        Text(st.goesTo(names.joinToString(", ").ifEmpty { "?" }), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        // Who said yes (ADR 0121 §25): only their phones ring; without a yes a call is just a notice there.
        val contacts by sos.contacts.collectAsState()
        val yes = contacts.count { it.state == app.anynear.anmp.core.SosContact.AGREED }
        Text(
            if (yes == 0) st.nobodyAgreed else st.agreedOf(yes, contacts.size),
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = if (yes == 0) AppTheme.colors.alarm else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (counting) {
            Text(st.countdown(left), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            AppButton(st.cancel, sos::cancelCountDown, Modifier.fillMaxWidth(), kind = ButtonKind.Main)
        } else if (busy) {
            androidx.compose.material3.CircularProgressIndicator(color = AppTheme.colors.alarm)
            Text(st.sending, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(st.sendingGoesOn, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        } else {
            HoldButton(st.button, st.hold, AppTheme.colors.alarm, AppTheme.colors.onAlarm, onHeld = sos::raiseNow)
            Text(st.hold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        if (failed && !busy) Text(st.failed, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text(st.whatGoes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(st.not112, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        AppButton(st.dial112, sos::dialEmergency, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
        val asks by sos.asks.collectAsState()
        if (asks.isEmpty()) AppButton(st.chooseHelpers, { choosing = true }, kind = ButtonKind.Quiet)
        else BadgedBox(badge = { OpenAsksBadge(asks.size) }, Modifier.semantics { stateDescription = st.openAsksCount(asks.size) }) {
            AppButton(st.chooseHelpers, { choosing = true }, kind = ButtonKind.Quiet)
        }
    }
}

/** Who the helpers are: circles and single persons, several of each. */
@Composable
private fun HelpersPage(controller: AppController, sos: Sos, onDone: () -> Unit) {
    val st = texts.sos
    val saved by sos.helpers.collectAsState()
    val circles by controller.circles.collectAsState()
    val people by controller.people.collectAsState()
    var chosenCircles by remember { mutableStateOf(saved.circles) }
    var chosenPersons by remember { mutableStateOf(saved.persons) }
    val shownCircles = controller.lifeSigns?.choices(circles) ?: circles.filter { !it.removed }
    val search = rememberPeopleSearch(controller, people.filter { it.deviceId != null })
    val shownPeople = search.all
    PageFrame(st.noHelpersTitle, sos::close) {
        // Open questions wait here, on top: they are what is left to do (ADR 0123 §6).
        val openAsks by sos.asks.collectAsState()
        if (openAsks.isNotEmpty()) AppSection(st.openAsks) {
            openAsks.forEach { a -> AppRow(title = st.askTitle(sos.name(a.person)), icon = AppIcons.Person, chevron = true, onClick = { sos.askNow(a.person) }) }
        }
        Text(st.noHelpersText, style = MaterialTheme.typography.bodyLarge)
        if (shownCircles.isEmpty() && shownPeople.isEmpty()) Text(st.nobodyYet, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (shownCircles.isNotEmpty()) AppSection(st.circles) {
            shownCircles.forEach { c ->
                val on = c.id in chosenCircles
                AppRow(
                    title = c.name, leading = { CircleAvatar(controller, c.id, c.image, 40.dp) },
                    trailing = { Checkbox(checked = on, onCheckedChange = null) },
                    onClick = { chosenCircles = if (on) chosenCircles - c.id else chosenCircles + c.id },
                )
            }
        }
        if (shownPeople.isNotEmpty()) AppSection(st.persons) {
            PeopleSearchField(search, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            search.found.forEach { p ->
                val id = p.deviceId!!
                val on = id in chosenPersons
                AppRow(
                    title = p.name, leading = { PersonAvatar(controller, p.peer, p.image, 40.dp) },
                    trailing = { Checkbox(checked = on, onCheckedChange = null) },
                    onClick = { chosenPersons = if (on) chosenPersons - id else chosenPersons + id },
                )
            }
        }
        // Whose SOS contact this person is, and the way to take it back.
        val agreed by sos.agreed.collectAsState()
        val scope = rememberCoroutineScope()
        if (agreed.isNotEmpty()) AppSection(st.contactFor) {
            agreed.forEach { person ->
                AppRow(title = sos.name(person), subtitle = st.withdraw, icon = AppIcons.Person, onClick = { scope.launch { sos.answer(person, false) } })
            }
        }
        Text(st.not112, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppButton(
            st.save,
            {
                // Who was ended through a circle stays left out (ADR 0123 §3), unless chosen alone now.
                sos.setHelpers(SosHelpers(chosenCircles, chosenPersons, saved.except - chosenPersons))
                onDone()
            },
            // Nobody chosen can be saved only to take the helpers away again; the button in the header then shows „not set up“.
            Modifier.fillMaxWidth(), kind = ButtonKind.Main, enabled = chosenCircles.isNotEmpty() || chosenPersons.isNotEmpty() || !saved.isEmpty,
        )
    }
}

/** The own running call: who looks after it, and the all-clear, again by holding. */
@Composable
private fun RunningPage(controller: AppController, sos: Sos, call: SosCall) {
    val st = texts.sos
    val scope = rememberCoroutineScope()
    // The call came from the own check-in nobody confirmed (ADR 0083 §5.4): say why the helpers were warned.
    val circle = call.plan?.let { call.circles.firstOrNull() ?: call.persons.firstOrNull()?.let { p -> app.anynear.lifesigns.LifeSigns.PERSON_PREFIX + p } }
    PageFrame(if (circle != null) st.ownMissedTitle else st.runningTitle, sos::close) {
        if (circle != null) {
            Text(st.ownMissedText(controller.lifeSigns?.circle(circle).orEmpty()), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (controller.lifeSigns?.sharesPlace(circle) != true) Text(st.ownMissedNoPlace, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        } else {
            // The clear answer after calling (19.11.2026): it went out, and it goes on without this page.
            Icon(AppIcons.Haken, contentDescription = null, tint = AppTheme.colors.safe, modifier = Modifier.size(56.dp))
            Text(st.sentConfirm, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Text(st.runningSince(formatTime(call.start)), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        Text(caringText(sos, call), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        // With how many helpers it arrived (ADR 0083 §3.4); after three minutes with nobody, the hint to call 112.
        val late = call.arrived.isEmpty() && sos.nowMillis() - call.start > 180_000L
        Text(
            if (late) st.notArrived else st.arrived(call.arrived.size, sos.helperCount(call)),
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = if (late) AppTheme.colors.alarm else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HoldButton(texts.lifeSigns.allGood, st.endHold, AppTheme.colors.safe, Color.White, onHeld = { scope.launch { sos.end() } })
        Text(st.endHold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(st.not112, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        AppButton(st.dial112, sos::dialEmergency, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
    }
}

private fun caringText(sos: Sos, call: SosCall): String {
    val names = call.caring.map(sos::name)
    return when (names.size) {
        0 -> texts.sos.nobodyCares
        1 -> texts.sos.cares(names.single())
        else -> texts.sos.caresMany(names.joinToString(", "))
    }
}

/** The call of someone we help: where, since when, the battery, and the answer „Ich kümmere mich“. */
@Composable
private fun HelperPage(controller: AppController, sos: Sos, call: SosCall) {
    val st = texts.sos
    val scope = rememberCoroutineScope()
    val me by controller.myPersonId.collectAsState()
    val name = sos.name(call.owner)
    // A check-in nobody confirmed (ADR 0083 §5.4): the page says which and why, the rest is the same call.
    val statuses by (controller.lifeSigns?.statuses ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()
    val plan = call.plan?.let { id -> statuses.firstOrNull { it.plan.id == id }?.plan }
    val title = when {
        call.ended -> st.endedTitle(name)
        call.plan != null -> sos.alarmTexts(call).first
        else -> st.needsHelp(name)
    }
    PageFrame(title, sos::close) {
        if (call.ended) {
            Text(st.ended, style = MaterialTheme.typography.titleMedium)
            return@PageFrame
        }
        if (call.plan != null) {
            val unreachable = call.reason == app.anynear.anmp.core.AliveStatus.REASON_UNREACHABLE
            Text(
                if (unreachable) st.unreachableNote(formatTime(plan?.lastAt ?: call.lastAt)) else st.missedNote(plan?.label.orEmpty()),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            listOfNotNull(if (call.plan != null) st.expectedSince(formatTime(plan?.due ?: call.start)) else st.since(formatTime(call.start)), call.battery?.let { st.battery(it) }).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
        )
        val place = call.position
        if (place != null) {
            SpotMap(controller, place.latitude, place.longitude, name) {
                controller.showOnMap(AppController.MapFocus(place.latitude, place.longitude, name))
                sos.close()
            }
            Text(
                st.placeFrom(formatTime(place.at)) + (place.accuracy?.let { " · " + formatAccuracy(it) } ?: ""),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(st.noPlace, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(caringText(sos, call), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (me in call.caring) {
            Text(st.youCare, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.safe)
        } else {
            AppButton(st.iCare, { scope.launch { sos.care(call.id) } }, Modifier.fillMaxWidth(), kind = ButtonKind.Main)
        }
        if (place != null) {
            AppButton(st.showOnMap, {
                controller.showOnMap(AppController.MapFocus(place.latitude, place.longitude, name))
                sos.close()
            }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = AppIcons.Karte)
        }
        AppButton(st.dial112, sos::dialEmergency, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet)
    }
}

/**
 * A large round button that acts only after being held for [Sos.HOLD_MILLIS]; a ring fills meanwhile (ADR 0083 §2).
 * Safer against false alarms than tapping, and possible with one hand.
 */
@Composable
private fun HoldButton(label: String, description: String, color: Color, onColor: Color, onHeld: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier.size(220.dp).semantics { contentDescription = description; role = Role.Button }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val filling = scope.launch {
                            progress.animateTo(1f, tween(Sos.HOLD_MILLIS.toInt(), easing = LinearEasing))
                            onHeld()
                        }
                        tryAwaitRelease()
                        if (progress.value < 1f) {
                            filling.cancel()
                            progress.snapTo(0f)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            drawCircle(color, radius = size.minDimension / 2 - stroke * 1.4f)
            drawArc(
                color, startAngle = -90f, sweepAngle = 360f * progress.value, useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text(label, color = onColor, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
    }
}
