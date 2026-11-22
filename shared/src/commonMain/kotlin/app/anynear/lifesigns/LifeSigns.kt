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
import app.anynear.location.LocationSharing
import app.anynear.location.ShareLevel
import app.anynear.ui.formatDayAndTime
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
    /** Who sees the place „wenn etwas ist“ (ADR 0121 §10); without it no place is ever handed to the core. */
    private val sharing: LocationSharing? = null,
    /** The one check of the location permission (ADR 0125): asked when a check-in that sends the place starts. */
    val access: app.anynear.location.LocationAccess? = null,
    /**
     * `true`: a check-in nobody confirmed rings as a call for the helpers ([app.anynear.sos.Sos], ADR 0083 §5.4), so
     * this class shows neither its loud alarm nor the quiet hint before it: only the call warns the helpers.
     */
    private val alarmIsCall: Boolean = false,
    /** Where the phone was before a place, oldest first; goes along only into such a call. */
    private val trailBefore: (app.anynear.location.Place) -> List<app.anynear.location.Place> = { emptyList() },
    /** The circles we share with a person, for what it sees of the own place (ADR 0121 §13). */
    private val circlesOf: (personId: String) -> List<String> = { emptyList() },
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
        // Switched on or off in the settings or on another own device: hand in or forget the place at once.
        sharing?.let { s ->
            scope.launch { s.default.collect { placeChanged() } }
            scope.launch { s.circles.collect { placeChanged() } }
            // While the own check-in runs the phone keeps locating (ADR 0125 §7); what it finds is handed in at once.
            scope.launch {
                s.placeUpdates.collect { place ->
                    val own = _statuses.value.firstOrNull { it.own && it.phase != AliveStatus.ENDED } ?: return@collect
                    runCatching { updatePlace(own.plan.id, place) }.onFailure { if (it is CancellationException) throw it }
                }
            }
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
        own?.let { refreshPlace(it, t) }
        locateLive(own)
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

    /**
     * Starts in [circleId], or with [person] for that one person instead (ADR 0121 §23); a running own one ends.
     * `false` if it did not work.
     */
    suspend fun start(template: LifeSignTemplate, circleId: String, customHours: Int = 2, customMinutes: Int = 30, person: String? = null): Boolean {
        val r = request(template, now(), customHours, customMinutes)
        val plan = source.start(circleId, r.label, r.until, r.quiet, r.firstDue, person) ?: return false
        if (person == null) store.putString(KEY_CIRCLE, circleId)
        if (r.singleQuestion) store.putString(KEY_SINGLE, json.encodeToString(listSerializer, (singles() + plan.id).takeLast(20)))
        log("Lebenszeichen gestartet: ${r.label}")
        placeTriedAt = 0L
        evaluate()
        askAccess(targetOf(plan))
        return true
    }

    /**
     * Starts a check-in with an own [label] in [circleId] until [untilMillis], asking every [quietMillis], e.g. for
     * „Begleite mich“ (ADR 0121 §8). The plan ID, `null` if it did not work.
     */
    suspend fun startTimed(circleId: String, label: String, untilMillis: Long, quietMillis: Long): String? {
        val plan = source.start(circleId, label, untilMillis, quietMillis.coerceIn(AliveLogic.MIN_QUIET, AliveLogic.MAX_QUIET), null) ?: return null
        store.putString(KEY_CIRCLE, circleId)
        log("Lebenszeichen gestartet: $label")
        placeTriedAt = 0L
        evaluate()
        askAccess(targetOf(plan))
        return plan.id
    }

    /** „Alles gut“: confirms the own one; with only one question it ends. */
    suspend fun confirm(planId: String) {
        if (planId in singles()) source.end(planId) else source.confirm(planId)
        hooks.cancelAlertNotifications(channelOf(planId))
        // The person just held the phone: a good moment to learn where it is.
        placeTriedAt = 0L
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
        val all = status.plan.peer?.let { listOf(it) } ?: helpersOf(status.plan.circleId)
        val heard = status.plan.heard.map { it.person }.filter { it in all }
        return all to heard
    }

    fun name(person: String): String = nameOf(person).ifBlank { "?" }

    /** Did this person sign off as a helper for [status] („Ich kann gerade nicht“)? */
    fun meAway(status: AliveStatus): Boolean = me()?.let { it in status.away } == true

    fun openExactAlarmSettings() = hooks.openExactAlarmSettings()

    /** Name of what a check-in goes to: a circle, or the person ([targetOf]). */
    fun circle(target: String): String = if (target.startsWith(PERSON_PREFIX)) name(target.removePrefix(PERSON_PREFIX)) else circleName(target)

    /** What the person lets [target] (a circle or a person, [targetOf]) see of its place. */
    private fun levelOf(target: String): ShareLevel? = sharing?.let { s ->
        if (target.startsWith(PERSON_PREFIX)) target.removePrefix(PERSON_PREFIX).let { s.levelFor(it, circlesOf(it), evenPaused = true) } else s.level(target)
    }

    // --- Place „wenn etwas ist“ (ADR 0121 §10) ------------------------------------------------

    private val placeLock = Mutex()
    private var placeTriedAt = 0L

    /** Do the helpers in [circleId] see where the person is, if nobody confirms? */
    // Also while everything is paused (20.11.2026): a check-in nobody confirms is a call for help, and that always goes.
    fun sharesPlace(circleId: String): Boolean = sharing?.let { levelOf(circleId) }.let { it != null && it != ShareLevel.Off }

    /** Opens the place of someone else's check-in in the maps app of the system; only after a tap. */
    fun openMap(status: AliveStatus) {
        val place = status.plan.position ?: return
        hooks.openMap(place.latitude, place.longitude, name(status.plan.owner))
    }

    /**
     * Looks where the phone is for the own running check-in, without holding up questions and warnings: right after
     * the start and after „Alles gut“, every [PLACE_ASK_GAP_MILLIS] while the phone asks or knows no place yet,
     * otherwise every [PLACE_GAP_MILLIS]. In the background the system usually gives nothing; then the last place stays,
     * with its time.
     */
    private fun refreshPlace(status: AliveStatus, t: Long) {
        if (sharing == null) return
        val soon = status.phase == AliveStatus.ASK || (status.plan.position == null && sharesPlace(targetOf(status.plan)))
        val gap = if (soon) PLACE_ASK_GAP_MILLIS else PLACE_GAP_MILLIS
        if (placeTriedAt != 0L && t - placeTriedAt < gap) return
        placeTriedAt = t
        scope.launch { runCatching { updatePlace(status.plan.id) }.onFailure { if (it is CancellationException) throw it } }
    }

    /** What the own running check-in last told the system about locating, so it is told only on a change. */
    private var liveText: String? = null

    /**
     * The phone keeps locating while the own check-in runs and its place may go to the helpers (ADR 0125 §7), with the
     * check-in's notification (Android) or the system's sign (iPhone); the place still leaves the phone only with
     * „nicht bestätigt“.
     */
    private fun locateLive(own: AliveStatus?) {
        val text = own?.takeIf { sharing != null && sharesPlace(targetOf(it.plan)) }?.let { app.anynear.i18n.texts.lifeSigns.locatingNotice }
        if (text == liveText) return
        liveText = text
        hooks.setLocationInUse(IN_USE, text)
    }

    /** A check-in that may send the place asks once for what it needs (ADR 0125 §3); it runs either way. */
    private fun askAccess(target: String) {
        val access = access ?: return
        if (!sharesPlace(target)) return
        val need = app.anynear.location.LocationNeed.PreciseWhileActive
        if (access.state(need) == app.anynear.location.LocationAccessState.Ok) return
        scope.launch { access.ensure(need, app.anynear.location.LocationPurpose.LifeSign) }
    }

    private fun placeChanged() {
        val own = _statuses.value.firstOrNull { it.own && it.phase != AliveStatus.ENDED } ?: return
        placeTriedAt = now()
        scope.launch { runCatching { updatePlace(own.plan.id) }.onFailure { if (it is CancellationException) throw it } }
    }

    /**
     * Hands the core the place for the own check-in [planId] if the person shares it with that circle „wenn etwas
     * ist“; otherwise the core forgets it. The core sends it only with „nicht bestätigt“.
     */
    private suspend fun updatePlace(planId: String, given: app.anynear.location.Place? = null) = placeLock.withLock {
        val sharing = sharing ?: return@withLock
        val plan = source.statuses(now()).firstOrNull { it.own && it.plan.id == planId && it.phase != AliveStatus.ENDED }?.plan ?: return@withLock
        if (levelOf(targetOf(plan)) != ShareLevel.WhenNeeded) {
            if (plan.position != null) source.setPlace(plan.id, null)
            return@withLock
        }
        val place = given ?: sharing.place() ?: return@withLock
        // The system may answer with an older place than the one already handed in.
        if (plan.position?.let { it.at >= place.atMillis } == true) return@withLock
        source.setPlace(plan.id, place, trailBefore(place))
    }

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
                        shown != first && shown != again -> { show(plan.id, targetOf(plan), lt.askTitle, lt.askText(plan.label), AlertPriority.Alarm, AlertLoudness.Loud, ACTION_OK, lt.allGood, AlertSound.Question); mark(first) }
                        shown == first && t >= plan.due + AliveLogic.ASK_AGAIN -> { show(plan.id, targetOf(plan), lt.askTitle, lt.askText(plan.label), AlertPriority.Alarm, AlertLoudness.Loud, ACTION_OK, lt.allGood, AlertSound.Question); mark(again) }
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
        val volume = loudness(targetOf(plan))
        when (s.phase) {
            AliveStatus.ENDED -> if (shown.isNotEmpty() && shown != "ended") {
                val title = if (plan.state == AnmpAliveState.TIMEOUT) lt.timedOut(who) else lt.ended(who)
                if (volume != LifeSignLoudness.Off) show(plan.id, targetOf(plan), title, lt.endedText, AlertPriority.Info, AlertLoudness.Normal)
                mark("ended")
            }
            AliveStatus.RUNNING -> when {
                shown.isEmpty() -> {
                    // Older check-ins (e.g. from the history of a new circle) are taken over quietly.
                    if (volume != LifeSignLoudness.Off && t - plan.start < 3_600_000L) {
                        show(plan.id, targetOf(plan), lt.started(who), lt.startedText(plan.label, formatTime(plan.until)), AlertPriority.Info, AlertLoudness.Normal)
                    }
                    mark("start")
                }
                shown.startsWith("hint") || shown.startsWith("alarm") -> {
                    if (volume != LifeSignLoudness.Off) show(plan.id, targetOf(plan), lt.confirmedAgain(who), "", AlertPriority.Info, AlertLoudness.Normal)
                    mark("ok:${plan.due}")
                }
                plan.state == AnmpAliveState.BATTERY && shown != "battery:${plan.lastAt}" -> {
                    if (volume != LifeSignLoudness.Off) show(plan.id, targetOf(plan), lt.battery(who, plan.battery ?: 0), "", AlertPriority.Info, AlertLoudness.Normal)
                    mark("battery:${plan.lastAt}")
                }
            }
            // With the alarm as a call there is no hint before it (20.11.2026): only the call warns the helpers.
            AliveStatus.HINT -> if (!alarmIsCall && shown != "hint:${plan.due}" && shown != "alarm:${plan.due}") {
                val title = if (s.reason == AliveStatus.REASON_MISSED) lt.missedTitle(who) else lt.hintTitle(who)
                if (volume != LifeSignLoudness.Off) show(plan.id, targetOf(plan), title, lt.hintText, AlertPriority.Warning, AlertLoudness.Normal, ACTION_LOOKING, lt.looking)
                mark("hint:${plan.due}")
            }
            AliveStatus.ALARM -> if (shown != "alarm:${plan.due}") {
                val (title, text) = if (s.reason == AliveStatus.REASON_MISSED) {
                    lt.missedTitle(who) to listOfNotNull(lt.missedText, plan.position?.let { lt.placeIncluded(formatDayAndTime(it.at, t)) }).joinToString(" ")
                } else {
                    // With the place, if the alarm the phone left at the relays brought one (ADR 0083 §5.7).
                    lt.unreachableTitle(who) to listOfNotNull(lt.unreachableText(formatTime(plan.lastAt)), plan.position?.let { lt.placeIncluded(formatDayAndTime(it.at, t)) }).joinToString(" ")
                }
                // If someone is already checking, it no longer rings loudly for the others (ADR 0083 §4).
                val loud = volume == LifeSignLoudness.Loud && s.looking.isEmpty()
                // As a call for the helpers the alarm rings there, once, with place, battery and „Ich kümmere mich“.
                if (alarmIsCall) hooks.cancelAlertNotifications(channelOf(plan.id))
                else if (volume != LifeSignLoudness.Off) {
                    show(plan.id, targetOf(plan), title, text, AlertPriority.Alarm, if (loud) AlertLoudness.Loud else AlertLoudness.Normal, ACTION_LOOKING, lt.looking)
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
                key = channelOf(planId) + "/n", channelId = channelOf(planId), channelName = circle(circleId), title = title, text = text,
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
        /** The reason for [PlatformHooks.setLocationInUse] while the own check-in runs. */
        const val IN_USE = "lifesign"

        /** ID of a check-in's notifications; tapping opens „Meldungen“. */
        const val CHANNEL_PREFIX = "alive:"
        const val ACTION_OK = "ok"
        const val ACTION_LOOKING = "looking"
        const val ACTION_END = "end"
        private const val KEY_CIRCLE = "lifesigns.circle"
        internal const val KEY_SHOWN = "lifesigns.shown"
        internal const val KEY_LOUDNESS = "lifesigns.loudness"
        private const val KEY_SINGLE = "lifesigns.single"

        /** How often the app looks for the place while the own check-in just runs. */
        private const val PLACE_GAP_MILLIS = 10 * 60_000L

        /** … and while the phone asks „Alles gut?“ (the place goes along if nobody answers) or knows no place yet. */
        private const val PLACE_ASK_GAP_MILLIS = 60_000L

        fun channelOf(planId: String) = CHANNEL_PREFIX + planId

        /** Prefix of a person in place of a circle ID, where a check-in goes to one person ([targetOf]). */
        const val PERSON_PREFIX = "p:"

        /**
         * What a check-in goes to, as the key for its name, its loudness and the place: the circle ID, or
         * [PERSON_PREFIX] and the other person for one that goes to a single person (ADR 0121 §23).
         */
        fun targetOf(plan: app.anynear.anmp.core.AlivePlan): String = plan.peer?.let { PERSON_PREFIX + it } ?: plan.circleId

        /** How loud check-ins of [circleId] are on this phone, read without a running app (notification extension). */
        fun loudnessIn(store: KeyValueStore, circleId: String): LifeSignLoudness {
            val map = store.getString(KEY_LOUDNESS)?.let { runCatching { Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it) }.getOrNull() }.orEmpty()
            return map[circleId]?.let { v -> LifeSignLoudness.entries.firstOrNull { it.name == v } } ?: LifeSignLoudness.Loud
        }
    }
}

/**
 * What the iOS notification extension shows for a packet of someone's check-in (ADR 0117), so that a locked iPhone is
 * treated like a phone whose app runs (20.11.2026): only the call for the helpers rings (ADR 0083 §5.4).
 * - The alarm the relays released ([AnmpAlive.guard]) is that call ([call]): it rings as one, „nicht erreichbar“.
 * - „Nicht bestätigt“ of a phone that calls the helpers itself ([AnmpAlive.calls]) shows nothing: the call follows when
 *   the alarm is due, and the extension rings for it then ([app.anynear.sos.sosWake]).
 * - „Nicht bestätigt“ of an older version, which sends no call, rings at once as before; that alarm counts as the
 *   first one of the call the app opens later.
 * - Everything else (start, battery, end, a late confirmation) as [lifeSignWake].
 */
fun checkInWake(
    store: KeyValueStore,
    planId: String,
    circleId: String,
    who: String,
    circleName: String,
    alive: AnmpAlive,
    call: String?,
    atMillis: Long,
    now: Long,
): AlertNotification? = when {
    alive.guard && call != null -> app.anynear.sos.sosWake(store, call, circleId, who, fromCheckIn = true, unreachable = true, atMillis = now)
    alive.state == AnmpAliveState.MISSED && alive.calls -> null
    else -> lifeSignWake(store, planId, circleId, who, circleName, alive, atMillis, now)?.also { n ->
        if (n.priority == AlertPriority.Alarm && call != null) app.anynear.sos.Sos.markShown(store, call)
    }
}

/**
 * Notification of the iOS notification extension for a sign of life of another person (ADR 0117, ADR 0083): the same
 * texts, loudness and „shown“ marks as [LifeSigns] uses for helpers, so that the app does not show it again once it
 * runs. Only what a packet itself says: start, battery, no confirmation, a late confirmation after a warning, end, and
 * „nicht erreichbar“ when the relays release the alarm the person's phone left with them (ADR 0083 §5.7; the core
 * hands it over only if nothing newer is known). The hint by time needs the running app. `null`: nothing to show.
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
    val (title, text, priority, loud) = when {
        // Left at the relays by the phone and released because nothing came since [atMillis]: the same alarm the app
        // shows when its own deadline runs out, so the same mark.
        alive.guard -> {
            if (shown == "alarm:$due") return null
            mark("alarm:$due")
            val place = alive.position?.takeIf { it.plausible }?.let { lt.placeIncluded(formatDayAndTime(it.at * 1000, now)) }
            Quad(lt.unreachableTitle(who), listOfNotNull(lt.unreachableText(formatTime(atMillis)), place).joinToString(" "), AlertPriority.Alarm, volume == LifeSignLoudness.Loud)
        }
        else -> stateWake(alive, shown, due, atMillis, now, who, lt, volume, ::mark) ?: return null
    }
    if (volume == LifeSignLoudness.Off) return null
    val loudness = if (loud) AlertLoudness.Loud else AlertLoudness.Normal
    return AlertNotification(
        key = LifeSigns.channelOf(planId) + "/n", channelId = LifeSigns.channelOf(planId), channelName = circleName, title = title, text = text,
        priority = priority, loudness = loudness, sound = if (loud) AlertSound.Alarm else AlertSound.Hint, timestampMillis = atMillis,
    )
}

/** What a packet of a sign of life says by its state; `null`: nothing to show. */
private fun stateWake(
    alive: AnmpAlive, shown: String, due: Long, atMillis: Long, now: Long, who: String, lt: app.anynear.i18n.LifeSignTexts, volume: LifeSignLoudness,
    mark: (String) -> Unit,
): Quad? {
    return when (alive.state) {
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
            val place = alive.position?.takeIf { it.plausible }?.let { lt.placeIncluded(formatDayAndTime(it.at * 1000, now)) }
            Quad(lt.missedTitle(who), listOfNotNull(lt.missedText, place).joinToString(" "), AlertPriority.Alarm, volume == LifeSignLoudness.Loud)
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
}

private data class Quad(val title: String, val text: String, val priority: AlertPriority, val loud: Boolean)
