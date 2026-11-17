package app.anynear.lifesigns

import app.anynear.alerts.AlertLoudness
import app.anynear.alerts.AlertNotification
import app.anynear.alerts.AlertPriority
import app.anynear.alerts.AlertSound
import app.anynear.alerts.NotificationAction
import app.anynear.anmp.AnmpAlive
import app.anynear.anmp.AnmpAliveState
import app.anynear.anmp.AnmpCareState
import app.anynear.anmp.core.AliveLogic
import app.anynear.anmp.core.AliveStatus
import app.anynear.app.CheckInNotification
import app.anynear.circles.Circle
import app.anynear.app.PlatformHooks
import app.anynear.data.KeyValueStore
import app.anynear.i18n.texts
import app.anynear.ui.formatFutureDayAndTime
import app.anynear.ui.formatTime
import app.anynear.ui.nextLocalHour
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Templates to start with (ADR 0083 §5.1). Movement counts only in a later step (ADR 0083, order). */
enum class LifeSignTemplate { Work, Outside, Home, Daily, Custom }

/** How warnings from a circle appear on this device (ADR 0083 §1). */
enum class LifeSignLoudness { Loud, Normal, Off }

/** Values a template starts with. [singleQuestion]: ask once, „Alles gut“ ends it. */
data class LifeSignRequest(val label: String, val until: Long, val quiet: Long, val firstDue: Long?, val singleQuestion: Boolean)

/**
 * Check-ins (ADR 0083 §5) on this device: start with templates in a circle of choice, question „Alles gut?“ for own
 * ones, hint and alarm for those of others, and waking at the next deadline. The core computes the deadlines; here is
 * only what the person sees and hears. Lives as long as the process.
 */
class LifeSigns(
    private val scope: CoroutineScope,
    private val source: LifeSignSource,
    private val store: KeyValueStore,
    private val hooks: PlatformHooks,
    private val now: () -> Long,
    /** Name of a person from the circles, otherwise empty. */
    private val nameOf: (String) -> String,
    private val circleName: (String) -> String,
    /** Other persons in the circle, without the own one. */
    val helpersOf: (String) -> List<String>,
    /** Own person ID once the core runs. */
    private val me: () -> String? = { null },
    /** Support circles (ADR 0065) are no place for a check-in. */
    private val isSupport: (String) -> Boolean = { false },
    private val log: (String) -> Unit = {},
) {
    private val _statuses = MutableStateFlow<List<AliveStatus>>(emptyList())

    /** All known check-ins with their status, also ended ones. */
    val statuses: StateFlow<List<AliveStatus>> = _statuses.asStateFlow()

    private val _exactMissing = MutableStateFlow(false)

    /** Android: exact alarms not allowed while something runs. */
    val exactAlarmMissing: StateFlow<Boolean> = _exactMissing.asStateFlow()

    private val _fullScreen = MutableStateFlow(false)

    /** Full screen with one big button „Alles gut“ (ADR 0083 §5.2), e.g. after tapping the question. */
    val fullScreen: StateFlow<Boolean> = _fullScreen.asStateFlow()

    fun openFullScreen() {
        if (_statuses.value.any { it.own && it.phase != AliveStatus.ENDED }) _fullScreen.value = true
    }

    fun closeFullScreen() {
        _fullScreen.value = false
    }

    /** Running own check-in, e.g. for the tile in the quick settings. */
    fun ownRunning(): String? = _statuses.value.firstOrNull { it.own && it.phase != AliveStatus.ENDED }?.plan?.id

    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())
    private val listSerializer = ListSerializer(String.serializer())

    fun start() {
        scope.launch {
            source.changes.collect { runCatching { evaluate() }.onFailure { if (it is CancellationException) throw it } }
        }
        // While the process runs, look at the next deadline (at least every minute); in deep sleep the alarm wakes it.
        scope.launch {
            while (true) {
                val next = runCatching { evaluate() }.getOrNull()
                val wait = next?.let { it - now() } ?: 60_000L
                delay(wait.coerceIn(1_000L, 60_000L))
            }
        }
    }

    /** Ask the core what is due, read the state anew, notify, set the alarm. Returns the next time. */
    suspend fun evaluate(): Long? = lock.withLock {
        runCatching { source.tick() }.onFailure { if (it is CancellationException) throw it }
        val t = now()
        val list = source.statuses(t)
        _statuses.value = list
        list.forEach { notifyFor(it, t) }
        val own = list.firstOrNull { it.own && it.phase != AliveStatus.ENDED }
        hooks.showCheckIn(own?.let { checkInOf(it, t) })
        if (own == null) _fullScreen.value = false
        val next = source.nextAt(t)
        hooks.scheduleExactWake(next)
        _exactMissing.value = next != null && !hooks.exactAlarmsAllowed()
        next
    }

    // --- Start and answer ----------------------------------------------------------------------

    /** Circle the person chose last. */
    fun defaultCircle(): String? = store.getString(KEY_CIRCLE)

    /**
     * Circles to choose from when starting: not removed, no support circle. Also circles where no other member is
     * known yet (nobody has written there since joining); whoever is in it still gets the check-in.
     */
    fun choices(circles: List<Circle>): List<Circle> = circles.filter { !it.removed && !isSupport(it.id) }

    /** Preselected circle: the last one chosen, otherwise the first with known helpers, otherwise the first. */
    fun preselect(choices: List<Circle>): String? =
        defaultCircle()?.takeIf { id -> choices.any { it.id == id } }
            ?: choices.firstOrNull { helpersOf(it.id).isNotEmpty() }?.id
            ?: choices.firstOrNull()?.id

    fun request(template: LifeSignTemplate, at: Long = now(), customHours: Int = 2, customMinutes: Int = 30): LifeSignRequest {
        val t = texts.lifeSigns
        val hour = 3_600_000L
        val minute = 60_000L
        return when (template) {
            LifeSignTemplate.Work -> LifeSignRequest(t.templateWork, at + 8 * hour, 20 * minute, null, singleQuestion = false)
            LifeSignTemplate.Outside -> LifeSignRequest(t.templateOutside, at + 2 * hour, 30 * minute, null, singleQuestion = false)
            LifeSignTemplate.Home -> {
                val morning = nextLocalHour(at, 7, AliveLogic.MIN_QUIET)
                LifeSignRequest(t.templateHome, morning + hour, (morning - at).coerceIn(AliveLogic.MIN_QUIET, AliveLogic.MAX_QUIET), morning, singleQuestion = true)
            }
            LifeSignTemplate.Daily -> {
                val first = nextLocalHour(at, 10, AliveLogic.MIN_QUIET)
                LifeSignRequest(t.templateDaily, at + 30 * 24 * hour, AliveLogic.MAX_QUIET, first, singleQuestion = false)
            }
            LifeSignTemplate.Custom -> LifeSignRequest(
                t.templateCustom, at + customHours.coerceIn(1, 24) * hour, customMinutes.coerceIn(15, 24 * 60) * minute, null, singleQuestion = false,
            )
        }
    }

    /** Starts in [circleId]; a running own one ends. `false` if it did not work. */
    suspend fun start(template: LifeSignTemplate, circleId: String, customHours: Int = 2, customMinutes: Int = 30): Boolean {
        val r = request(template, now(), customHours, customMinutes)
        val plan = source.start(circleId, r.label, r.until, r.quiet, r.firstDue) ?: return false
        store.putString(KEY_CIRCLE, circleId)
        if (r.singleQuestion) store.putString(KEY_SINGLE, json.encodeToString(listSerializer, (singles() + plan.id).takeLast(20)))
        log("Lebenszeichen gestartet: ${r.label}")
        evaluate()
        return true
    }

    /** „Alles gut“: confirms the own one; with only one question it ends. */
    suspend fun confirm(planId: String) {
        if (planId in singles()) source.end(planId) else source.confirm(planId)
        hooks.cancelAlertNotifications(channelOf(planId))
        evaluate()
    }

    /** „Bin fertig“. */
    suspend fun end(planId: String) {
        source.end(planId)
        hooks.cancelAlertNotifications(channelOf(planId))
        evaluate()
    }

    /** As a helper: „Ich schaue nach“, „Ich kann gerade nicht“, „Ich bin wieder da“ ([AnmpCareState]). */
    suspend fun care(planId: String, state: Int) {
        source.care(planId, state)
        if (state == AnmpCareState.LOOKING || state == AnmpCareState.AWAY) hooks.cancelAlertNotifications(channelOf(planId))
        evaluate()
    }

    /** From the platform's alarm: check deadlines now. */
    fun wake() {
        scope.launch { runCatching { evaluate() } }
    }

    /** Button in a notification ([NotificationAction.id]). */
    fun onAction(id: String) {
        val (what, planId) = id.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return
        scope.launch {
            when (what) {
                ACTION_OK -> confirm(planId)
                ACTION_LOOKING -> care(planId, AnmpCareState.LOOKING)
                ACTION_END -> end(planId)
            }
        }
    }

    fun loudness(circleId: String): LifeSignLoudness =
        loudnessMap()[circleId]?.let { v -> LifeSignLoudness.entries.firstOrNull { it.name == v } } ?: LifeSignLoudness.Loud

    fun setLoudness(circleId: String, value: LifeSignLoudness) {
        store.putString(KEY_LOUDNESS, json.encodeToString(mapSerializer, loudnessMap() + (circleId to value.name)))
    }

    /** Helpers in the plan's circle and who of them knows (receipt). */
    fun helpers(status: AliveStatus): Pair<List<String>, List<String>> {
        val all = helpersOf(status.plan.circleId)
        val heard = status.plan.heard.map { it.person }.filter { it in all }
        return all to heard
    }

    fun name(person: String): String = nameOf(person).ifBlank { "?" }

    /** Did this person sign off as a helper for [status] („Ich kann gerade nicht“)? */
    fun meAway(status: AliveStatus): Boolean = me()?.let { it in status.away } == true

    fun openExactAlarmSettings() = hooks.openExactAlarmSettings()

    fun circle(circleId: String): String = circleName(circleId)

    // --- Notifications ------------------------------------------------------------------------

    private fun notifyFor(s: AliveStatus, t: Long) {
        val plan = s.plan
        val shown = shownMap()[plan.id].orEmpty()
        fun mark(v: String) = store.putString(KEY_SHOWN, json.encodeToString(mapSerializer, (shownMap() + (plan.id to v)).entries.toList().takeLast(50).associate { it.key to it.value }))
        val lt = texts.lifeSigns
        if (s.own) {
            when (s.phase) {
                AliveStatus.ASK -> {
                    val first = "ask:${plan.due}"
                    val again = "ask2:${plan.due}"
                    when {
                        shown != first && shown != again -> { show(plan.id, plan.circleId, lt.askTitle, lt.askText(plan.label), AlertPriority.Alarm, AlertLoudness.Loud, ACTION_OK, lt.allGood, AlertSound.Question); mark(first) }
                        shown == first && t >= plan.due + AliveLogic.ASK_AGAIN -> { show(plan.id, plan.circleId, lt.askTitle, lt.askText(plan.label), AlertPriority.Alarm, AlertLoudness.Loud, ACTION_OK, lt.allGood, AlertSound.Question); mark(again) }
                    }
                }
                else -> if (shown.startsWith("ask")) {
                    hooks.cancelAlertNotifications(channelOf(plan.id))
                    mark("ok:${plan.due}")
                }
            }
            return
        }
        val who = name(plan.owner)
        val volume = loudness(plan.circleId)
        when (s.phase) {
            AliveStatus.ENDED -> if (shown.isNotEmpty() && shown != "ended") {
                val title = if (plan.state == AnmpAliveState.TIMEOUT) lt.timedOut(who) else lt.ended(who)
                if (volume != LifeSignLoudness.Off) show(plan.id, plan.circleId, title, lt.endedText, AlertPriority.Info, AlertLoudness.Normal)
                mark("ended")
            }
            AliveStatus.RUNNING -> when {
                shown.isEmpty() -> {
                    // Older check-ins (e.g. from the history of a new circle) are taken over quietly.
                    if (volume != LifeSignLoudness.Off && t - plan.start < 3_600_000L) {
                        show(plan.id, plan.circleId, lt.started(who), lt.startedText(plan.label, formatTime(plan.until)), AlertPriority.Info, AlertLoudness.Normal)
                    }
                    mark("start")
                }
                shown.startsWith("hint") || shown.startsWith("alarm") -> {
                    if (volume != LifeSignLoudness.Off) show(plan.id, plan.circleId, lt.confirmedAgain(who), "", AlertPriority.Info, AlertLoudness.Normal)
                    mark("ok:${plan.due}")
                }
                plan.state == AnmpAliveState.BATTERY && shown != "battery:${plan.lastAt}" -> {
                    if (volume != LifeSignLoudness.Off) show(plan.id, plan.circleId, lt.battery(who, plan.battery ?: 0), "", AlertPriority.Info, AlertLoudness.Normal)
                    mark("battery:${plan.lastAt}")
                }
            }
            AliveStatus.HINT -> if (shown != "hint:${plan.due}" && shown != "alarm:${plan.due}") {
                val title = if (s.reason == AliveStatus.REASON_MISSED) lt.missedTitle(who) else lt.hintTitle(who)
                if (volume != LifeSignLoudness.Off) show(plan.id, plan.circleId, title, lt.hintText, AlertPriority.Warning, AlertLoudness.Normal, ACTION_LOOKING, lt.looking)
                mark("hint:${plan.due}")
            }
            AliveStatus.ALARM -> if (shown != "alarm:${plan.due}") {
                val (title, text) = if (s.reason == AliveStatus.REASON_MISSED) {
                    lt.missedTitle(who) to lt.missedText
                } else {
                    lt.unreachableTitle(who) to lt.unreachableText(formatTime(plan.lastAt))
                }
                // If someone is already checking, it no longer rings loudly for the others (ADR 0083 §4).
                val loud = volume == LifeSignLoudness.Loud && s.looking.isEmpty()
                if (volume != LifeSignLoudness.Off) {
                    show(plan.id, plan.circleId, title, text, AlertPriority.Alarm, if (loud) AlertLoudness.Loud else AlertLoudness.Normal, ACTION_LOOKING, lt.looking)
                }
                mark("alarm:${plan.due}")
            }
        }
    }

    private fun checkInOf(s: AliveStatus, t: Long): CheckInNotification {
        val lt = texts.lifeSigns
        val text = if (s.phase == AliveStatus.ASK) lt.askNow else lt.nextQuestion(formatFutureDayAndTime(s.plan.due, t))
        return CheckInNotification(
            s.plan.id, lt.section + " · " + s.plan.label, text, "$ACTION_OK:${s.plan.id}", lt.allGood, "$ACTION_END:${s.plan.id}", lt.done,
        )
    }

    private fun show(
        planId: String,
        circleId: String,
        title: String,
        text: String,
        priority: AlertPriority,
        loudness: AlertLoudness,
        action: String? = null,
        actionLabel: String = "",
        sound: AlertSound? = null,
    ) {
        hooks.showAlertNotification(
            AlertNotification(
                key = channelOf(planId) + "/n", channelId = channelOf(planId), channelName = circleName(circleId), title = title, text = text,
                priority = priority, loudness = loudness, sound = sound ?: if (loudness == AlertLoudness.Loud) AlertSound.Alarm else AlertSound.Hint,
                timestampMillis = now(), actions = listOfNotNull(action?.let { NotificationAction("$it:$planId", actionLabel) }),
            ),
        )
    }

    private fun shownMap(): Map<String, String> = read(KEY_SHOWN) { json.decodeFromString(mapSerializer, it) } ?: emptyMap()

    private fun loudnessMap(): Map<String, String> = read(KEY_LOUDNESS) { json.decodeFromString(mapSerializer, it) } ?: emptyMap()

    private fun singles(): List<String> = read(KEY_SINGLE) { json.decodeFromString(listSerializer, it) } ?: emptyList()

    private fun <T> read(key: String, parse: (String) -> T): T? = store.getString(key)?.let { runCatching { parse(it) }.getOrNull() }

    companion object {
        /** ID of a check-in's notifications; tapping opens „Meldungen“. */
        const val CHANNEL_PREFIX = "alive:"
        const val ACTION_OK = "ok"
        const val ACTION_LOOKING = "looking"
        const val ACTION_END = "end"
        private const val KEY_CIRCLE = "lifesigns.circle"
        internal const val KEY_SHOWN = "lifesigns.shown"
        internal const val KEY_LOUDNESS = "lifesigns.loudness"
        private const val KEY_SINGLE = "lifesigns.single"

        fun channelOf(planId: String) = CHANNEL_PREFIX + planId
    }
}

/**
 * Notification of the iOS notification extension for a sign of life of another person (ADR 0117, ADR 0083): the same
 * texts, loudness and „shown“ marks as [LifeSigns] uses for helpers, so that the app does not show it again once it
 * runs. Only what a packet itself says: start, battery, no confirmation, a late confirmation after a warning, end.
 * Warnings by time (hint, „nicht erreichbar“) need the running app. `null`: nothing to show.
 */
fun lifeSignWake(
    store: KeyValueStore,
    planId: String,
    circleId: String,
    who: String,
    circleName: String,
    alive: AnmpAlive,
    atMillis: Long,
    now: Long,
): AlertNotification? {
    val json = Json { ignoreUnknownKeys = true }
    val map = MapSerializer(String.serializer(), String.serializer())
    fun read(key: String) = store.getString(key)?.let { runCatching { json.decodeFromString(map, it) }.getOrNull() }.orEmpty()
    val shown = read(LifeSigns.KEY_SHOWN)[planId].orEmpty()
    fun mark(v: String) = store.putString(
        LifeSigns.KEY_SHOWN,
        json.encodeToString(map, (read(LifeSigns.KEY_SHOWN) + (planId to v)).entries.toList().takeLast(50).associate { it.key to it.value }),
    )
    val volume = read(LifeSigns.KEY_LOUDNESS)[circleId]?.let { v -> LifeSignLoudness.entries.firstOrNull { it.name == v } } ?: LifeSignLoudness.Loud
    val lt = texts.lifeSigns
    val due = alive.due * 1000
    val (title, text, priority, loud) = when (alive.state) {
        AnmpAliveState.START -> {
            if (shown.isNotEmpty()) return null
            mark("start")
            // Older check-ins are taken over quietly, as in the app.
            if (now - alive.start * 1000 >= 3_600_000L) return null
            Quad(lt.started(who), lt.startedText(alive.label.orEmpty(), formatTime(alive.until * 1000)), AlertPriority.Info, false)
        }
        AnmpAliveState.MISSED -> {
            if (shown == "alarm:$due") return null
            mark("alarm:$due")
            Quad(lt.missedTitle(who), lt.missedText, AlertPriority.Alarm, volume == LifeSignLoudness.Loud)
        }
        AnmpAliveState.BATTERY -> {
            if (shown == "battery:$atMillis") return null
            mark("battery:$atMillis")
            Quad(lt.battery(who, (alive.battery ?: 0).coerceIn(0, 100)), "", AlertPriority.Info, false)
        }
        AnmpAliveState.END, AnmpAliveState.TIMEOUT -> {
            if (shown.isEmpty() || shown == "ended") return null
            mark("ended")
            Quad(if (alive.state == AnmpAliveState.TIMEOUT) lt.timedOut(who) else lt.ended(who), lt.endedText, AlertPriority.Info, false)
        }
        else -> {
            // A confirmation only matters to someone who was warned.
            if (!shown.startsWith("hint") && !shown.startsWith("alarm")) return null
            mark("ok:$due")
            Quad(lt.confirmedAgain(who), "", AlertPriority.Info, false)
        }
    }
    if (volume == LifeSignLoudness.Off) return null
    val loudness = if (loud) AlertLoudness.Loud else AlertLoudness.Normal
    return AlertNotification(
        key = LifeSigns.channelOf(planId) + "/n", channelId = LifeSigns.channelOf(planId), channelName = circleName, title = title, text = text,
        priority = priority, loudness = loudness, sound = if (loud) AlertSound.Alarm else AlertSound.Hint, timestampMillis = atMillis,
    )
}

private data class Quad(val title: String, val text: String, val priority: AlertPriority, val loud: Boolean)
