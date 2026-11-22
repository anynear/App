package app.anynear.sos

import app.anynear.alerts.AlertLoudness
import app.anynear.alerts.AlertNotification
import app.anynear.alerts.AlertPriority
import app.anynear.alerts.AlertSound
import app.anynear.anmp.core.AlivePosition
import app.anynear.anmp.core.AliveStatus
import app.anynear.anmp.core.AnmpCore
import app.anynear.anmp.core.CoreEvent
import app.anynear.anmp.core.SosAsk
import app.anynear.anmp.core.SosCall
import app.anynear.anmp.core.SosContact
import app.anynear.app.PlatformHooks
import app.anynear.data.KeyValueStore
import app.anynear.data.ObservedKeyValueStore
import app.anynear.i18n.texts
import app.anynear.lifesigns.LifeSignLoudness
import app.anynear.location.LocationAccess
import app.anynear.location.LocationAccessState
import app.anynear.location.LocationNeed
import app.anynear.location.LocationPurpose
import app.anynear.location.LocationSharing
import app.anynear.location.Place
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Interface of the app to the core for calling the helpers (kind sos); replaceable in tests. */
interface SosSource {
    /** ID of a call whenever it changed. */
    val changes: Flow<String>

    fun calls(): List<SosCall>

    suspend fun raise(circleIds: List<String>, personIds: List<String>, place: Place?, battery: Int?, trail: List<Place>): String?

    suspend fun update(id: String, place: Place?, battery: Int?, trail: List<Place>): Boolean

    suspend fun end(id: String): Boolean

    suspend fun care(id: String): Boolean

    // Contacts for calls need a yes, once (ADR 0121 §25).

    /** A person whenever something about the contacts changed: asked, answered, taken back. */
    val contactChanges: Flow<String> get() = kotlinx.coroutines.flow.emptyFlow()

    /** Whom the own person asked, and what they said. */
    fun contacts(): List<SosContact> = emptyList()

    /** Who asked us and has no answer yet. */
    fun asks(): List<SosAsk> = emptyList()

    /** Whose calls we agreed to be woken for. */
    fun agreed(): List<String> = emptyList()

    suspend fun ask(circleIds: List<String>, personIds: List<String>): Boolean = false

    /** As [ask], but [except] is never asked, also not through a circle (ADR 0123 §3). */
    suspend fun ask(circleIds: List<String>, personIds: List<String>, except: List<String>): Boolean = ask(circleIds, personIds.filter { it !in except })

    suspend fun answer(person: String, agree: Boolean): Boolean = false

    suspend fun forget(person: String): Boolean = false

    /** The asker ends being counted (ADR 0123 §3): [person] learns it. */
    suspend fun endContact(person: String): Boolean = forget(person)

    /** Returns once what is stored is loaded: before that the lists above are empty, although something is known. */
    suspend fun ready() = Unit
}

/** [SosSource] on the ANMP core. */
class CoreSosSource(private val core: AnmpCore) : SosSource {
    private fun Place.wire() = AlivePosition(latitude, longitude, atMillis, accuracyMeters)

    override val changes: Flow<String> = core.events.filterIsInstance<CoreEvent.SosChanged>().map { it.id }

    override fun calls(): List<SosCall> = core.sosCalls()

    override suspend fun raise(circleIds: List<String>, personIds: List<String>, place: Place?, battery: Int?, trail: List<Place>): String? =
        core.raiseSos(circleIds, personIds, place?.wire(), battery, trail.map { it.wire() })

    override suspend fun update(id: String, place: Place?, battery: Int?, trail: List<Place>): Boolean =
        core.updateSos(id, place?.wire(), battery, trail.map { it.wire() })

    override suspend fun end(id: String): Boolean = core.endSos(id)

    override suspend fun care(id: String): Boolean = core.careSos(id)

    override val contactChanges: Flow<String> = kotlinx.coroutines.flow.merge(
        core.events.filterIsInstance<CoreEvent.SosContactsChanged>().map { it.person },
        core.events.filterIsInstance<CoreEvent.SosAsked>().map { it.person },
    )

    override fun contacts(): List<SosContact> = core.sosContacts()

    override fun asks(): List<SosAsk> = core.sosAsks()

    override fun agreed(): List<String> = core.sosAgreed()

    override suspend fun ask(circleIds: List<String>, personIds: List<String>): Boolean = core.askSosContacts(circleIds, personIds)

    override suspend fun ask(circleIds: List<String>, personIds: List<String>, except: List<String>): Boolean =
        core.askSosContacts(circleIds, personIds, except = except)

    override suspend fun answer(person: String, agree: Boolean): Boolean = core.answerSosAsk(person, agree)

    override suspend fun forget(person: String): Boolean = core.forgetSosContact(person)

    override suspend fun endContact(person: String): Boolean = core.endSosContact(person)

    override suspend fun ready() {
        core.identity.first { it != null }
    }
}

/** The card for an SOS question of the other person (ADR 0123 §5). */
const val CARD_ASKED_US = 1

/** The own person asked a single person to be its SOS contact. */
const val CARD_WE_ASKED = 2
const val CARD_THEY_YES = 3
const val CARD_THEY_NO = 4
const val CARD_WE_YES = 5
const val CARD_WE_NO = 6

/** The own person is no longer the other's SOS contact: its yes was taken back, or the other ended it. */
const val CARD_OVER_FOR_US = 7

/** The other is no longer the own person's SOS contact. */
const val CARD_OVER_FOR_THEM = 8

/**
 * The cards one change brings about one person (ADR 0123 §5): from whether it asked us ([askedBefore], [asked]),
 * whether we agreed to it ([agreedBefore], [agreed]), and what it answered to our question ([contactBefore],
 * [contact], states of [SosContact]). [chosen]: we chose this person alone, not through a circle.
 */
internal fun sosCards(
    askedBefore: Boolean, asked: Boolean, agreedBefore: Boolean, agreed: Boolean, contactBefore: Int?, contact: Int?, chosen: Boolean,
): List<Int> = buildList {
    if (!askedBefore && asked) add(CARD_ASKED_US)
    if (!agreedBefore && agreed) add(CARD_WE_YES)
    else if (askedBefore && !asked && !agreed) add(CARD_WE_NO)
    if (agreedBefore && !agreed) add(CARD_OVER_FOR_US)
    if (contactBefore != contact) when (contact) {
        SosContact.ASKED -> if (chosen && contactBefore == null) add(CARD_WE_ASKED)
        SosContact.AGREED -> add(CARD_THEY_YES)
        SosContact.REFUSED -> add(if (contactBefore == SosContact.AGREED) CARD_OVER_FOR_THEM else CARD_THEY_NO)
        null -> if (contactBefore == SosContact.AGREED) add(CARD_OVER_FOR_THEM)
    }
}

/** Who is called: circles and single persons (ADR 0121 §13, „deine Helfer“). */
data class SosHelpers(
    val circles: Set<String> = emptySet(),
    val persons: Set<String> = emptySet(),
    /**
     * Members of a chosen circle the own person ended as SOS contact (ADR 0123 §3): not asked again through the circle.
     * Kept as `x:` entries in the same setting; older apps skip them.
     */
    val except: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = circles.isEmpty() && persons.isEmpty()
}

/**
 * Calling the helpers (ADR 0083 §2–§4, ADR 0121 §13): one tap away on every main page. The person chooses once who
 * its helpers are, circles and single persons. A call goes to all of them with the place, the battery and where the
 * phone was before, and the place follows while the call runs. Helpers are woken loudly and say who looks after it.
 *
 * A check-in nobody confirmed is such a call, too (ADR 0083 §5.4): the core opens it when the helpers' alarm is due,
 * with the same ID from the phone, from the alarm it left at the relays and from the helper's own deadline, so it rings
 * once. It goes to the circle of the check-in, as loud as the helper set check-ins of that circle, and carries a place
 * only if the person shares it with that circle „wenn etwas ist“.
 *
 * It warns the helpers. It is not the emergency number 112, and the app says so.
 */
class Sos(
    private val scope: CoroutineScope,
    private val source: SosSource,
    private val store: KeyValueStore,
    private val hooks: PlatformHooks,
    private val sharing: LocationSharing?,
    private val now: () -> Long,
    private val nameOf: (personId: String) -> String,
    /** The persons in a circle besides the own one, to say with how many helpers a call should arrive. */
    var membersOf: (circleId: String) -> Collection<String> = { emptyList() },
) {
    /** How loud check-ins of a circle are on this phone (ADR 0083 §1); a call that came from one follows it. */
    var lifeSignLoudness: (circleId: String) -> LifeSignLoudness = { LifeSignLoudness.Loud }

    /** Does the person share its place with this circle if a check-in stays unconfirmed (ADR 0121 §10)? */
    var sharesPlaceWith: (circleId: String) -> Boolean = { false }

    /** „Alles gut“ for the own check-in [planId]: confirms it, which ends the call that came from it. */
    var confirmCheckIn: (suspend (planId: String) -> Unit)? = null

    enum class Raise { Sent, NoHelpers, Failed }

    private val _helpers = MutableStateFlow(readHelpers())

    /** The helpers the person chose; a setting of the person on all its devices (ADR 0106). */
    val helpers: StateFlow<SosHelpers> = _helpers.asStateFlow()

    private val _calls = MutableStateFlow(source.calls())

    /** The own call and those of persons we help; ended ones stay for a day. */
    val calls: StateFlow<List<SosCall>> = _calls.asStateFlow()

    /**
     * What the full-screen page shows: `""` the own page (calling, or the running own call), the ID of someone
     * else's call, `null` closed.
     */
    private val _screen = MutableStateFlow<String?>(null)
    val screen: StateFlow<String?> = _screen.asStateFlow()

    /** `true` while the countdown of a call raised by five taps runs (ADR 0083 §3). */
    private val _counting = MutableStateFlow(false)
    val counting: StateFlow<Boolean> = _counting.asStateFlow()

    /** Where this phone was, the newest last, for the trail of a call. Stays on the phone until a call is raised. */
    private val trail = ArrayDeque<Place>()

    private val _contacts = MutableStateFlow(source.contacts())

    /** Whom the own person asked to be its SOS contact, and what they said (ADR 0121 §25). */
    val contacts: StateFlow<List<SosContact>> = _contacts.asStateFlow()

    private val _asks = MutableStateFlow(source.asks())

    /** Who asks us to be its SOS contact and has no answer yet. */
    val asks: StateFlow<List<SosAsk>> = _asks.asStateFlow()

    private val _agreed = MutableStateFlow(source.agreed())

    /** Whose calls this phone rings for: only theirs. A call of anyone else is shown without the alarm. */
    val agreed: StateFlow<List<String>> = _agreed.asStateFlow()

    /**
     * A step about SOS contacts with [person], for the card in the chat (ADR 0123 §5): one of [CARD_ASKED_US] …
     * [CARD_OVER_FOR_THEM]. Taken from the change of what the core knows, so that a step on another own device counts too.
     */
    var onCard: (person: String, card: Int) -> Unit = { _, _ -> }

    /**
     * Cards only once the core loaded what it knows. Compared with the state the cards last saw, kept on this device
     * ([KEY_CARDS_SEEN]): what happened while the app was closed gets its card at the next start, too.
     */
    private var watching = false

    private fun refreshContacts() {
        _contacts.value = source.contacts()
        _asks.value = source.asks()
        _agreed.value = source.agreed()
        if (watching) {
            val now = Triple(_contacts.value.associate { it.person to it.state }, _asks.value.map { it.person }.toSet(), _agreed.value.toSet())
            // Nothing seen yet (the first start with cards): no news, only the state to compare with from now on.
            readSeen()?.let { before -> if (before != now) cardsFor(before) }
            writeSeen(now)
        }
        // Asked for the first time: a quiet notice, the answer is given in the app.
        val told = store.getString(KEY_ASKS_TOLD)?.split(',')?.toSet().orEmpty()
        val open = _asks.value.map { it.person }
        for (person in open) if (person !in told) {
            hooks.showAlertNotification(
                AlertNotification(
                    key = "sos-ask/$person", channelId = ASK_CHANNEL, channelName = texts.sos.title, title = texts.sos.askTitle(name(person)), text = texts.sos.askNotice,
                    priority = AlertPriority.Info, loudness = AlertLoudness.Normal, sound = AlertSound.Hint, timestampMillis = now(),
                ),
            )
        }
        // Answered, here or on another device: the notice has nothing left to say.
        if (open.isEmpty() && told.isNotEmpty()) hooks.cancelAlertNotifications(ASK_CHANNEL)
        if (open.toSet() != told) store.putString(KEY_ASKS_TOLD, open.takeLast(40).joinToString(",").takeIf { it.isNotEmpty() })
    }

    /** Asks everyone chosen who was not asked yet; the core leaves out who has an answer or was asked before. */
    private fun askContacts() {
        val to = _helpers.value
        if (to.isEmpty) return
        scope.launch { runCatching { if (to.except.isEmpty()) source.ask(to.circles.toList(), to.persons.toList()) else source.ask(to.circles.toList(), to.persons.toList(), to.except.toList()) } }
    }

    private val _asking = MutableStateFlow<String?>(null)

    /**
     * The open question the person tapped on, shown as a dialog; `null` for none. A question never opens by itself
     * (ADR 0123 §6): it waits in the list „Anfragen“, on the SOS page and as a card in the chat.
     */
    val asking: StateFlow<String?> = _asking.asStateFlow()

    private fun readSeen(): Triple<Map<String, Int>, Set<String>, Set<String>>? {
        val raw = store.getString(KEY_CARDS_SEEN) ?: return null
        val parts = raw.split('|').associate { it.substringBefore(':') to it.substringAfter(':', "").split(',').filter(String::isNotEmpty) }
        val contacts = parts["c"].orEmpty().mapNotNull { e -> e.substringAfterLast('=').toIntOrNull()?.let { e.substringBeforeLast('=') to it } }.toMap()
        return Triple(contacts, parts["a"].orEmpty().toSet(), parts["g"].orEmpty().toSet())
    }

    private fun writeSeen(seen: Triple<Map<String, Int>, Set<String>, Set<String>>) {
        val (contacts, asks, agreed) = seen
        store.putString(KEY_CARDS_SEEN, "c:" + contacts.entries.joinToString(",") { "${it.key}=${it.value}" } + "|a:" + asks.joinToString(",") + "|g:" + agreed.joinToString(","))
    }

    private fun cardsFor(before: Triple<Map<String, Int>, Set<String>, Set<String>>) {
        val (contactsBefore, asksBefore, agreedBefore) = before
        val contacts = _contacts.value.associate { it.person to it.state }
        val asks = _asks.value.map { it.person }.toSet()
        val agreed = _agreed.value.toSet()
        val chosen = _helpers.value.persons
        // A set: a person in several of these lists gets its cards once, not once per list.
        for (p in (contactsBefore.keys + contacts.keys + asksBefore + asks + agreedBefore + agreed).toSet()) {
            val cards = sosCards(p in asksBefore, p in asks, p in agreedBefore, p in agreed, contactsBefore[p], contacts[p], p in chosen)
            cards.forEach { runCatching { onCard(p, it) } }
        }
    }

    /** Shows the question of [person], from a list of open questions. */
    fun askNow(person: String) { _asking.value = person }

    fun closeAsk() { _asking.value = null }

    /**
     * The own person no longer counts [person] as its SOS contact (ADR 0123 §3): it is taken out of the chosen ones,
     * the person learns it and its yes ends.
     */
    suspend fun endContact(person: String): Boolean {
        val h = _helpers.value
        // Chosen alone: taking it out of the chosen ones tells it ([setHelpers]).
        if (person in h.persons) {
            setHelpers(h.copy(persons = h.persons - person, except = h.except + person.takeIf { p -> h.circles.any { p in membersOf(it) } }.let(::setOfNotNull)))
            return true
        }
        // Chosen through a circle: left out from now on, so that the next start does not ask again.
        if (h.circles.any { person in membersOf(it) }) {
            setHelpers(h.copy(except = h.except + person))
            return true
        }
        return source.endContact(person).also { refreshContacts() }
    }

    /** „Als SOS-Kontakt fragen“ at the page of a person (ADR 0123 §7): chosen alone, and asked. */
    fun askPerson(person: String) {
        val h = _helpers.value
        if (person !in h.persons || person in h.except) setHelpers(h.copy(persons = h.persons + person, except = h.except - person))
    }

    /** The own answer to [person], also later to take a yes back. */
    suspend fun answer(person: String, agree: Boolean): Boolean = source.answer(person, agree).also {
        if (_asking.value == person) _asking.value = null
        refreshContacts()
    }

    /** How many of those asked said yes. */
    fun agreedCount(): Int = _contacts.value.count { it.state == SosContact.AGREED }

    fun start() {
        // The permission can be taken back in the system's settings at any time: the line follows it.
        scope.launch {
            kotlinx.coroutines.flow.combine(hooks.locationAllowed, hooks.preciseLocationAllowed) { _, _ -> }.collect { refreshPrecise() }
        }
        scope.launch { source.changes.collect { refresh() } }
        scope.launch { source.contactChanges.collect { refreshContacts() } }
        // Once the core has loaded what it knows: the calls, the open questions and whose calls ring here. Read earlier,
        // the lists are empty, and after a restart a call of someone this person said yes to would stay quiet
        // (found on a tablet, 20.11.2026).
        scope.launch {
            source.ready()
            refresh()
            watching = true
            refreshContacts()
            // Everyone chosen who has no answer yet is asked; the core leaves out who was asked a short while ago.
            askContacts()
        }
        (store as? ObservedKeyValueStore)?.let { observed ->
            // Chosen on another own device or in the browser (ADR 0123 §3): who is new gets the question from here, too;
            // the core leaves out who was asked a short while ago.
            scope.launch {
                observed.changes.collect {
                    if (it != KEY_HELPERS) return@collect
                    _helpers.value = readHelpers()
                    refreshPrecise()
                    askContacts()
                }
            }
        }
        scope.launch {
            // While an own call runs the place follows (ADR 0083 §3.5); otherwise the phone only remembers a short trail.
            while (true) {
                delay(TICK_MILLIS)
                runCatching { tick() }.onFailure { if (it is CancellationException) throw it }
            }
        }
        // While an own call runs the system delivers places also with the app in the background (ADR 0121 §14).
        scope.launch {
            hooks.placeUpdates.collect { place ->
                if (trail.lastOrNull()?.atMillis != place.atMillis) {
                    trail.addLast(place)
                    while (trail.size > TRAIL) trail.removeFirst()
                }
                val running = own() ?: return@collect
                if (now() - lastUpdateAt >= UPDATE_GAP_MILLIS && mayLocate(running)) {
                    lastUpdateAt = now()
                    runCatching { source.update(running.id, place, hooks.batteryPercent(), trail.toList().dropLast(1)) }
                }
            }
        }
        refresh()
    }

    private var lastUpdateAt = 0L

    /** The own running call; one raised by hand before one that came from a check-in. */
    fun own(): SosCall? = _calls.value.filter { it.own && !it.ended }.minByOrNull { if (it.plan == null) 0 else 1 }

    /**
     * A call raised by hand carries the place: the person asked for help. One that came from a check-in goes to that
     * circle and carries it only if the person shares its place with the circle „wenn etwas ist“.
     */
    private fun mayLocate(call: SosCall): Boolean =
        call.plan == null || (call.circles.all { sharesPlaceWith(it) } && call.persons.all { sharesPlaceWith(app.anynear.lifesigns.LifeSigns.PERSON_PREFIX + it) })

    /** Title and text of the alarm for [call]: a call by hand, or a check-in nobody confirmed. */
    fun alarmTexts(call: SosCall): Pair<String, String> {
        val who = name(call.owner)
        return when {
            call.plan == null -> texts.sos.alarmTitle(who) to texts.sos.alarmText
            call.reason == AliveStatus.REASON_UNREACHABLE -> texts.sos.unreachableTitle(who) to texts.sos.unreachableText
            else -> texts.sos.missedTitle(who) to texts.sos.missedText
        }
    }

    private fun loudnessOf(call: SosCall): LifeSignLoudness =
        // A check-in for this person alone came directly: then the loudness set for that person counts.
        if (call.plan != null) lifeSignLoudness(call.via ?: (app.anynear.lifesigns.LifeSigns.PERSON_PREFIX + call.owner)) else LifeSignLoudness.Loud

    fun name(personId: String): String = nameOf(personId).ifBlank { "?" }

    fun nowMillis(): Long = now()

    /** How many helpers the own [call] went to; at least as many as confirmed it (ADR 0083 §3.4). */
    fun helperCount(call: SosCall): Int = maxOf((call.circles.flatMap(membersOf) + call.persons).toSet().size, call.arrived.size)

    fun setHelpers(helpers: SosHelpers) {
        val before = _helpers.value
        store.putString(KEY_HELPERS, (helpers.circles.map { "c:$it" } + helpers.persons.map { "p:$it" } + helpers.except.map { "x:$it" }).sorted().joinToString(",").takeIf { it.isNotEmpty() })
        _helpers.value = helpers
        refreshPrecise()
        // Who is no longer chosen is forgotten as a contact; who is new gets the question (ADR 0121 §25).
        val still = (helpers.circles.flatMap(membersOf) + helpers.persons).toSet() - helpers.except
        val gone = (before.circles.flatMap(membersOf) + before.persons).toSet() - before.except - still
        scope.launch {
            // They learn it (ADR 0123 §3): their yes ends with it.
            gone.forEach { runCatching { source.endContact(it) } }
            refreshContacts()
        }
        askContacts()
    }

    /** The one check of the phone's location permission (ADR 0125); `null` in tests that do without. */
    var access: LocationAccess? = null
        set(value) {
            field = value
            refreshPrecise()
        }

    private val _needsPrecise = MutableStateFlow(false)

    /**
     * SOS contacts are chosen, but the phone gives no precise location (22.11.2026, ADR 0125): permission missing or only
     * approximate, also when it was taken back in the system's settings later. The app says so on every page until it is
     * allowed again or SOS is switched off; a call still goes out without it, never blocked.
     */
    val needsPrecise: StateFlow<Boolean> = _needsPrecise.asStateFlow()

    private fun refreshPrecise() {
        val precise = access?.state(LocationNeed.Precise)?.let { it == LocationAccessState.Ok } ?: hooks.preciseLocationAllowed.value
        _needsPrecise.value = !_helpers.value.isEmpty && !precise
    }

    /** The next step towards the precise location, without the sheet: switch on, the system's question or its settings. */
    fun fixPrecise() {
        val a = access
        if (a != null) a.fix(LocationNeed.Precise, LocationPurpose.SosContact)
        else if (!hooks.locationServicesOn()) hooks.openLocationSettings() else hooks.requestPreciseLocation()
    }

    /**
     * When SOS contacts are chosen: the sheet once (ADR 0125 §4), then the system; `true` if the precise place is
     * allowed then. The choice is saved either way: it never waits on this.
     */
    suspend fun askPrecise(): Boolean {
        val a = access ?: return hooks.preciseLocationAllowed.value
        return a.ensure(LocationNeed.Precise, LocationPurpose.SosContact) == LocationAccessState.Ok
    }

    /**
     * „SOS ausschalten“ (22.11.2026): all SOS contacts are taken out, and they learn it; nothing rings any more and the
     * sign in the header shows „not set up“. A running own call is ended with „Alles gut“ first, never by this.
     */
    fun switchOff(): Boolean {
        if (own() != null) return false
        setHelpers(SosHelpers())
        return true
    }

    /** Opens the page: for calling, or the running own call. */
    fun open() {
        _screen.value = ""
    }

    /** Opens someone else's call, e.g. from its alarm. */
    fun open(id: String) {
        _screen.value = id
        hooks.cancelAlertNotifications(channelOf(id))
    }

    fun close() {
        // Closing the page stops a countdown, never a call that is going out or running.
        cancelCountDown()
        _screen.value = null
    }

    private val _raising = MutableStateFlow(false)

    /** `true` while a call is going out: it goes on when the page is closed (19.11.2026). */
    val raising: StateFlow<Boolean> = _raising.asStateFlow()

    private val _failed = MutableStateFlow(false)

    /** The last call did not go out on any try; cleared with the next one. */
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    private val _left = MutableStateFlow(0)

    /** Seconds left of the countdown while [counting]. */
    val countdownLeft: StateFlow<Int> = _left.asStateFlow()

    private var countJob: kotlinx.coroutines.Job? = null

    /** Five quick taps (ADR 0121 §13): the page opens with a countdown that sends unless the person cancels. */
    fun countDown() {
        _screen.value = ""
        if (_helpers.value.isEmpty || own() != null || _raising.value || _counting.value) return
        _counting.value = true
        _left.value = COUNTDOWN_SECONDS
        countJob = scope.launch {
            while (_left.value > 0) {
                delay(1000)
                _left.value = _left.value - 1
            }
            raiseNow()
        }
    }

    fun cancelCountDown() {
        countJob?.cancel()
        countJob = null
        _counting.value = false
    }

    /**
     * Calls the helpers, independent of any page: once started it is not stopped by closing the page or leaving the
     * app. If nothing goes out, it tries again for a while ([RAISE_TRIES]) before it says so ([failed]).
     */
    fun raiseNow() {
        if (_raising.value) return
        _counting.value = false
        countJob = null
        _failed.value = false
        _raising.value = true
        scope.launch {
            var result = Raise.Failed
            try {
                for (i in 0 until RAISE_TRIES) {
                    result = raise()
                    if (result != Raise.Failed) break
                    delay(RAISE_RETRY_MILLIS)
                }
            } finally {
                _raising.value = false
                _failed.value = result == Raise.Failed
            }
        }
    }

    /** One try to call the helpers, with the place the phone knows already; a fresh one follows by itself. */
    suspend fun raise(): Raise {
        _counting.value = false
        val to = _helpers.value
        if (to.isEmpty) return Raise.NoHelpers
        // Not waiting for the locating: the call goes out at once, the fresh place right after.
        // By hand always goes to all helpers; a call that came from a check-in keeps running next to it.
        val known = trail.lastOrNull()
        val id = source.raise(to.circles.toList(), to.persons.toList(), known, hooks.batteryPercent(), trail.toList().dropLast(if (known != null) 1 else 0))
        refresh()
        if (id == null) return Raise.Failed
        scope.launch {
            val place = runCatching { locate() }.getOrNull() ?: return@launch
            if (place.atMillis != known?.atMillis) runCatching { source.update(id, place, hooks.batteryPercent(), trail.toList().dropLast(1)) }
        }
        return Raise.Sent
    }

    /** „Alles gut“: the helpers get the all-clear. */
    suspend fun end(): Boolean {
        val call = own() ?: return false
        // The call of a check-in ends by confirming the check-in (a check-in with one question ends with it).
        val confirm = confirmCheckIn
        if (call.plan != null && confirm != null) {
            runCatching { confirm(call.plan!!) }.onFailure { if (it is CancellationException) throw it }
            refresh()
            if (own()?.id != call.id) return true
        }
        return source.end(call.id).also { refresh() }
    }

    /** „Ich kümmere mich“ for someone else's call; its alarm goes quiet here. */
    suspend fun care(id: String): Boolean {
        hooks.cancelAlertNotifications(channelOf(id))
        return source.care(id).also { refresh() }
    }

    fun dialEmergency() = hooks.dial(EMERGENCY_NUMBER)

    private suspend fun locate(): Place? {
        val place = runCatching { sharing?.place() }.getOrNull() ?: return trail.lastOrNull()
        if (trail.lastOrNull()?.let { it.atMillis == place.atMillis } != true) {
            trail.addLast(place)
            while (trail.size > TRAIL) trail.removeFirst()
        }
        return place
    }

    /**
     * Rings again for calls nobody looks after yet (ADR 0083 §4): every [RING_GAP_MILLIS], at most [RING_TIMES] more
     * times, so that a helper who missed the first alarm hears it. Called with every tick.
     */
    fun ringAgain() {
        val rings = HashMap<String, Pair<Int, Long>>()
        store.getString(KEY_RINGS)?.split(',')?.forEach { e ->
            val parts = e.split('|')
            if (parts.size == 3) rings[parts[0]] = Pair(parts[1].toIntOrNull() ?: 0, parts[2].toLongOrNull() ?: 0L)
        }
        val open = _calls.value.filter { !it.own && !it.ended && it.caring.isEmpty() && it.owner in _agreed.value }
        val next = HashMap<String, Pair<Int, Long>>()
        for (call in open) {
            // Counted from the first alarm, which the phone showed when the call came.
            val known = rings[call.id] ?: Pair(0, firstRing[call.id] ?: now())
            val times = known.first
            val at = known.second
            if (times < RING_TIMES && now() - at >= RING_GAP_MILLIS - 5_000) {
                val (title, text) = alarmTexts(call)
                if (loudnessOf(call) != LifeSignLoudness.Off) notify(call, title, text, loud = loudnessOf(call) == LifeSignLoudness.Loud)
                next[call.id] = Pair(times + 1, now())
            } else {
                next[call.id] = Pair(times, at)
            }
        }
        val text = next.entries.joinToString(",") { "${it.key}|${it.value.first}|${it.value.second}" }.takeIf { it.isNotEmpty() }
        if (text != store.getString(KEY_RINGS)) store.putString(KEY_RINGS, text)
    }

    /** When the first alarm of a call rang on this phone, until [ringAgain] keeps it. */
    private val firstRing = HashMap<String, Long>()

    private suspend fun tick() {
        ringAgain()
        val running = own()
        if (running != null) {
            val place = if (mayLocate(running)) locate() else null
            source.update(running.id, place, hooks.batteryPercent(), if (mayLocate(running)) trail.toList().dropLast(if (place != null) 1 else 0) else emptyList())
        } else if (!_helpers.value.isEmpty && sharing?.allowed?.value == true) {
            // A short trail for the moment a call is raised; it never leaves the phone otherwise.
            locate()
        }
    }

    private fun refresh() {
        val all = source.calls()
        _calls.value = all
        // The phone keeps locating for as long as the own call runs, and says so.
        hooks.setLocationInUse(IN_USE, if (all.any { it.own && !it.ended }) texts.sos.runningTitle else null)
        val shown = store.getString(KEY_SHOWN)?.split(',')?.toSet().orEmpty()
        val alarmed = shown.toMutableSet()
        for (call in all) {
            if (call.own) continue
            val me = call.caring.isNotEmpty()
            when {
                call.ended && "end:${call.id}" !in shown -> {
                    hooks.cancelAlertNotifications(channelOf(call.id))
                    // For a check-in the app already says „hat bestätigt: alles gut“.
                    if (call.id in shown && call.plan == null) notify(call, texts.sos.endedTitle(name(call.owner)), texts.sos.endedText, loud = false)
                    alarmed += "end:${call.id}"
                }
                !call.ended && call.id !in shown -> {
                    val (title, text) = alarmTexts(call)
                    val volume = loudnessOf(call)
                    // A check-in someone already looks after („Ich schaue nach“ after the hint) does not ring loudly any more.
                    val quiet = call.plan != null && call.caring.isNotEmpty()
                    // Only for someone this person said yes to (ADR 0121 §25): any other call is shown, quietly.
                    val agreedTo = call.owner in _agreed.value
                    if (volume != LifeSignLoudness.Off) notify(call, title, text, loud = volume == LifeSignLoudness.Loud && !quiet && agreedTo)
                    firstRing[call.id] = now()
                    alarmed += call.id
                }
                // Someone looks after it: the alarm stops ringing here, the call stays visible (ADR 0083 §4).
                !call.ended && me && "care:${call.id}" !in shown -> {
                    hooks.cancelAlertNotifications(channelOf(call.id))
                    alarmed += "care:${call.id}"
                }
            }
        }
        if (alarmed != shown) store.putString(KEY_SHOWN, alarmed.toList().takeLast(60).joinToString(","))
    }

    private fun notify(call: SosCall, title: String, text: String, loud: Boolean) {
        hooks.showAlertNotification(
            AlertNotification(
                key = channelOf(call.id) + "/n", channelId = channelOf(call.id), channelName = texts.sos.title, title = title, text = text,
                priority = if (loud) AlertPriority.Alarm else AlertPriority.Info, loudness = if (loud) AlertLoudness.Loud else AlertLoudness.Normal,
                sound = if (loud) AlertSound.Sos else AlertSound.Hint, timestampMillis = now(),
            ),
        )
    }

    /** Where this phone was, oldest first, for a check-in's call (ADR 0083 §5.4); stays on the phone until then. */
    fun trailBefore(place: Place): List<Place> = trail.filter { it.atMillis < place.atMillis }

    private fun readHelpers(): SosHelpers {
        val parts = store.getString(KEY_HELPERS)?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
        return SosHelpers(
            parts.filter { it.startsWith("c:") }.map { it.drop(2) }.toSet(), parts.filter { it.startsWith("p:") }.map { it.drop(2) }.toSet(),
            parts.filter { it.startsWith("x:") }.map { it.drop(2) }.toSet(),
        )
    }

    companion object {
        /**
         * The iOS notification extension rang for a check-in nobody confirmed (ADR 0117): the call [id] that alarm is
         * counts as shown, so the app does not ring a second time for it when it opens.
         */
        fun markShown(store: KeyValueStore, id: String) {
            val shown = store.getString(KEY_SHOWN)?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
            if (id !in shown) store.putString(KEY_SHOWN, (shown + id).takeLast(60).joinToString(","))
        }

        /** Person setting: `c:<circle>` and `p:<person>`, comma-separated. */
        const val KEY_HELPERS = "sos.helpers"

        /** Device setting: calls whose alarm was shown here. */
        const val KEY_SHOWN = "sos_shown"

        /** Device setting: whose question to be an SOS contact this phone already announced. */
        const val KEY_ASKS_TOLD = "sos_asks_told"

        /** Device setting: the state of SOS contacts the cards in the chat last saw (ADR 0123 §5). */
        const val KEY_CARDS_SEEN = "sos_cards_seen"
        private const val ASK_CHANNEL = "sos-ask"
        private const val RAISE_TRIES = 12
        private const val RAISE_RETRY_MILLIS = 5_000L

        /** Device setting: how often the phone rang again for a call, and when last. */
        const val KEY_RINGS = "sos_rings"
        const val RING_TIMES = 5
        const val RING_GAP_MILLIS = 120_000L

        /** ID of a call's notifications; tapping opens the call. */
        const val CHANNEL_PREFIX = "sos:"

        const val EMERGENCY_NUMBER = "112"
        const val TICK_MILLIS = 120_000L
        const val COUNTDOWN_SECONDS = 5
        const val HOLD_MILLIS = 3_000L
        private const val TRAIL = 11
        private const val IN_USE = "sos"
        private const val UPDATE_GAP_MILLIS = 60_000L

        fun channelOf(id: String) = CHANNEL_PREFIX + id
    }
}

/**
 * Notification of the iOS notification extension for a call for the helpers (ADR 0117, ADR 0083 §2 and §5.4), so that a
 * locked iPhone rings like a phone whose app runs: once per call, with the sound of a call. A call raised by hand is
 * always loud; one that came from a check-in ([fromCheckIn], [unreachable]: nothing came from the phone any more) is as
 * loud as the helper set check-ins of [circleId]. The call counts as shown, so the app does not ring again for it when
 * it opens. `null`: shown before, or the circle is set to „aus“.
 */
fun sosWake(store: KeyValueStore, callId: String, circleId: String?, who: String, fromCheckIn: Boolean, unreachable: Boolean, atMillis: Long): AlertNotification? {
    if (store.getString(Sos.KEY_SHOWN)?.split(',')?.contains(callId) == true) return null
    Sos.markShown(store, callId)
    // [circleId]: for a check-in for this person alone the key of the person ([app.anynear.lifesigns.LifeSigns.PERSON_PREFIX]).
    val volume = if (fromCheckIn && circleId != null) app.anynear.lifesigns.LifeSigns.loudnessIn(store, circleId) else LifeSignLoudness.Loud
    if (volume == LifeSignLoudness.Off) return null
    val loud = volume == LifeSignLoudness.Loud
    val (title, text) = when {
        !fromCheckIn -> texts.sos.alarmTitle(who) to texts.sos.alarmText
        unreachable -> texts.sos.unreachableTitle(who) to texts.sos.unreachableText
        else -> texts.sos.missedTitle(who) to texts.sos.missedText
    }
    return AlertNotification(
        key = Sos.channelOf(callId) + "/n", channelId = Sos.channelOf(callId), channelName = texts.sos.title, title = title, text = text,
        priority = AlertPriority.Alarm, loudness = if (loud) AlertLoudness.Loud else AlertLoudness.Normal,
        sound = if (loud) AlertSound.Sos else AlertSound.Hint, timestampMillis = atMillis,
    )
}
