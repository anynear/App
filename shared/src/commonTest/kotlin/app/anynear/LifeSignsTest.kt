package app.anynear

import app.anynear.alerts.AlertLoudness
import app.anynear.alerts.AlertNotification
import app.anynear.alerts.AlertPriority
import app.anynear.alerts.AlertSound
import app.anynear.app.CheckInNotification
import app.anynear.circles.Circle
import app.anynear.anmp.AnmpAliveState
import app.anynear.app.NoOpPlatformHooks
import app.anynear.anmp.AnmpCareState
import app.anynear.anmp.core.AliveCare
import app.anynear.anmp.core.AliveLogic
import app.anynear.anmp.core.AlivePlan
import app.anynear.anmp.core.AlivePosition
import app.anynear.anmp.AnmpPosition
import app.anynear.location.LocationSharing
import app.anynear.location.Place
import app.anynear.location.ShareLevel
import app.anynear.anmp.core.AliveStatus
import app.anynear.lifesigns.LifeSignLoudness
import app.anynear.lifesigns.LifeSignSource
import app.anynear.lifesigns.LifeSignTemplate
import app.anynear.lifesigns.LifeSigns
import app.anynear.lifesigns.lifeSignWake
import app.anynear.anmp.AnmpAlive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Check-ins in the app (ADR 0083 §5): templates, notifications per phase, buttons, alarm. */
class LifeSignsTest {
    private val min = 60_000L
    private val t0 = 1_800_000_000_000L

    private class FakeSource : LifeSignSource {
        override val changes = MutableSharedFlow<String>()
        var list: List<AliveStatus> = emptyList()
        var next: Long? = null
        val calls = mutableListOf<String>()
        override fun statuses(now: Long) = list
        override fun nextAt(now: Long) = next
        var startedFor: String? = null
        override suspend fun start(circleId: String, label: String, until: Long, quiet: Long, firstDue: Long?, person: String?): AlivePlan {
            calls += "start $circleId $label ${until - firstDue.let { 0 }} $quiet ${firstDue != null}"
            startedFor = person
            return AlivePlan("p1", circleId, "me", label, 0, until, firstDue ?: quiet, 5 * 60_000L, 10 * 60_000L, quiet, state = AnmpAliveState.START, lastAt = 0, peer = person)
        }
        override suspend fun confirm(planId: String): Boolean { calls += "confirm $planId"; return true }
        override suspend fun end(planId: String): Boolean { calls += "end $planId"; return true }
        override suspend fun care(planId: String, state: Int): Boolean { calls += "care $planId $state"; return true }
        override suspend fun tick(): Long? = next
        var trail: List<Place> = emptyList()
        override suspend fun setPlace(planId: String, place: Place?, trail: List<Place>): Boolean {
            calls += "place $planId " + (place?.let { "${it.latitude},${it.longitude}" } ?: "none")
            this.trail = trail
            return true
        }
    }

    private class Hooks : NoOpPlatformHooks() {
        val shown = mutableListOf<AlertNotification>()
        val cancelled = mutableListOf<String>()
        var wake: Long? = -1
        var checkIn: CheckInNotification? = null
        override fun showCheckIn(checkIn: CheckInNotification?) { this.checkIn = checkIn }
        override fun showAlertNotification(notification: AlertNotification) { shown += notification }
        override fun cancelAlertNotifications(channelId: String) { cancelled += channelId }
        override fun scheduleExactWake(atMillis: Long?) { wake = atMillis }
        var place: Place? = null
        var askedForExact = 0
        val opened = mutableListOf<String>()
        override suspend fun currentPlace(): Place? = place
        override fun requestPreciseLocation() { askedForExact++ }
        override fun openMap(latitude: Double, longitude: Double, label: String) { opened += "$latitude,$longitude $label" }
    }

    private fun plan(owner: String = "anna", state: Int = AnmpAliveState.START, start: Long = t0, due: Long = t0 + 20 * min) = AlivePlan(
        id = "p1", circleId = "c1", owner = owner, label = "Allein arbeiten", start = start, until = start + 8 * 60 * min, due = due,
        hint = 5 * min, alarm = 10 * min, quiet = 20 * min, state = state, lastAt = start,
    )

    private fun status(p: AlivePlan, own: Boolean, now: Long, me: String = if (own) p.owner else "ich") = AliveLogic.status(p, own, me, now)

    private fun setup(now: () -> Long): Triple<FakeSource, Hooks, LifeSigns> {
        val source = FakeSource()
        val hooks = Hooks()
        val signs = LifeSigns(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), source, InMemoryStore(), hooks, now,
            nameOf = { if (it == "anna") "Anna" else "" }, circleName = { "Familie" }, helpersOf = { listOf("ben", "carla") }, me = { "ich" },
        )
        return Triple(source, hooks, signs)
    }

    @Test
    fun templatesSetSensibleValues() {
        val (_, _, signs) = setup { t0 }
        val work = signs.request(LifeSignTemplate.Work, t0)
        assertEquals(t0 + 8 * 60 * min, work.until)
        assertEquals(20 * min, work.quiet)
        val outside = signs.request(LifeSignTemplate.Outside, t0)
        assertEquals(t0 + 2 * 60 * min, outside.until)
        val home = signs.request(LifeSignTemplate.Home, t0)
        assertTrue(home.singleQuestion)
        val first = assertNotNull(home.firstDue)
        assertTrue(first > t0 && first <= t0 + 25 * 60 * min)
        assertTrue(home.until > first)
        val daily = signs.request(LifeSignTemplate.Daily, t0)
        assertEquals(AliveLogic.MAX_QUIET, daily.quiet)
        // Custom values: never ask below 15 minutes.
        assertEquals(15 * min, signs.request(LifeSignTemplate.Custom, t0, customHours = 3, customMinutes = 5).quiet)
    }

    @Test
    fun helperIsToldAtStartThenHintThenLoudAlarmThenAllGood() = runTest {
        var now = t0
        val (source, hooks, signs) = setup { now }
        val p = plan()
        source.list = listOf(status(p, false, now))
        signs.evaluate()
        assertEquals(AlertLoudness.Normal, hooks.shown.single().loudness)

        now = p.due + 6 * min
        source.list = listOf(status(p, false, now))
        signs.evaluate()
        val hint = hooks.shown.last()
        assertEquals(AlertPriority.Warning, hint.priority)
        assertEquals(AlertLoudness.Normal, hint.loudness)
        assertEquals("looking:p1", hint.actions.single().id)

        now = p.due + 11 * min
        source.list = listOf(status(p, false, now))
        signs.evaluate()
        assertEquals(AlertLoudness.Loud, hooks.shown.last().loudness)
        assertEquals(AlertPriority.Alarm, hooks.shown.last().priority)
        // Nothing twice while nothing changes.
        signs.evaluate()
        assertEquals(3, hooks.shown.size)

        val confirmed = p.copy(state = AnmpAliveState.OK, lastAt = now, due = now + 20 * min)
        source.list = listOf(status(confirmed, false, now))
        signs.evaluate()
        assertEquals(4, hooks.shown.size)
        assertEquals(AlertPriority.Info, hooks.shown.last().priority)
        // All notifications of a check-in replace each other.
        assertEquals(1, hooks.shown.map { it.key }.distinct().size)
    }

    /** ADR 0083 §5.4: the alarm is a call for the helpers and rings there; here neither a hint nor an alarm. */
    @Test
    fun withTheAlarmAsACallTheHelperGetsNeitherHintNorAlarmHere() = runTest {
        var now = t0
        val source = FakeSource()
        val hooks = Hooks()
        val signs = LifeSigns(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), source, InMemoryStore(), hooks, { now },
            nameOf = { if (it == "anna") "Anna" else "" }, circleName = { "Familie" }, helpersOf = { listOf("ben") }, me = { "ich" }, alarmIsCall = true,
        )
        val p = plan()
        source.list = listOf(status(p, false, now))
        signs.evaluate()
        now = p.due + 6 * min
        source.list = listOf(status(p, false, now))
        signs.evaluate()
        // No hint: only the start was announced.
        assertEquals(1, hooks.shown.size)

        now = p.due + 11 * min
        source.list = listOf(status(p, false, now))
        signs.evaluate()
        signs.evaluate()
        // No alarm from here either; the call rings.
        assertEquals(1, hooks.shown.size)
        assertTrue(LifeSigns.channelOf("p1") in hooks.cancelled)

        // „Alles gut“ after all is still said here, once.
        val confirmed = p.copy(state = AnmpAliveState.OK, lastAt = now, due = now + 20 * min)
        source.list = listOf(status(confirmed, false, now))
        signs.evaluate()
        assertEquals(2, hooks.shown.size)
        assertEquals(AlertPriority.Info, hooks.shown.last().priority)
    }

    /** 20.11.2026: the locked iPhone rings only for the call, like a phone whose app runs. */
    @Test
    fun theExtensionWaitsForTheCallAndRingsAtOnceOnlyForAnOlderPhone() {
        fun wire(state: Int, calls: Boolean = false, guard: Boolean = false) = AnmpAlive(
            ByteArray(8) { 1 }, state, t0 / 1000, (t0 + 8 * 60 * min) / 1000, (t0 + 20 * min) / 1000, 300, 600, 1200, guard = guard, calls = calls,
        )
        val now = t0 + 23 * min
        // The phone calls the helpers itself: „nicht bestätigt“ shows nothing and marks nothing.
        val store = InMemoryStore()
        assertNull(app.anynear.lifesigns.checkInWake(store, "p1", "c1", "Anna", "Familie", wire(AnmpAliveState.MISSED, calls = true), "call-1", now, now))
        assertNull(store.getString("sos_shown"))
        // An older phone sends no call: its „nicht bestätigt“ rings at once, and counts as the alarm of the call.
        val old = assertNotNull(app.anynear.lifesigns.checkInWake(store, "p1", "c1", "Anna", "Familie", wire(AnmpAliveState.MISSED), "call-1", now, now))
        assertEquals(AlertLoudness.Loud, old.loudness)
        assertEquals(LifeSigns.channelOf("p1"), old.channelId)
        assertTrue("call-1" in store.getString("sos_shown").orEmpty())

        // The alarm the relays released is the call: it rings as one, once.
        val other = InMemoryStore()
        val guard = assertNotNull(app.anynear.lifesigns.checkInWake(other, "p1", "c1", "Anna", "Familie", wire(AnmpAliveState.MISSED, calls = true, guard = true), "call-2", t0, t0 + 31 * min))
        assertEquals(app.anynear.sos.Sos.channelOf("call-2"), guard.channelId)
        assertEquals(AlertSound.Sos, guard.sound)
        assertEquals("Anna ist nicht erreichbar", guard.title)
        assertNull(app.anynear.lifesigns.checkInWake(other, "p1", "c1", "Anna", "Familie", wire(AnmpAliveState.MISSED, calls = true, guard = true), "call-2", t0, t0 + 31 * min))
        // A start is still announced quietly.
        assertEquals(AlertLoudness.Normal, app.anynear.lifesigns.checkInWake(InMemoryStore(), "p1", "c1", "Anna", "Familie", wire(AnmpAliveState.START), null, t0, t0)?.loudness)
    }

    /** ADR 0121 §23: a check-in for one person: that person is the helper, and name and loudness hang on the person. */
    @Test
    fun aCheckInForOnePersonHasThatPersonAsItsOnlyHelper() = runTest {
        var now = t0
        val (source, hooks, signs) = setup { now }
        assertTrue(signs.start(LifeSignTemplate.Outside, "", person = "anna"))
        assertEquals("anna", source.startedFor)
        // The circle chosen last stays what it was: a person is no circle to remember.
        assertNull(signs.defaultCircle())

        val p = plan().copy(circleId = "", peer = "anna")
        assertEquals("p:anna", LifeSigns.targetOf(p))
        assertEquals("c1", LifeSigns.targetOf(plan()))
        assertEquals("Anna", signs.circle(LifeSigns.targetOf(p)))
        assertEquals(listOf("anna"), signs.helpers(status(p.copy(owner = "ich", peer = "anna"), true, now)).first)

        // What Anna started for me alone: announced under her name, and silenced for her alone.
        source.list = listOf(status(p, false, now))
        signs.evaluate()
        assertEquals("Anna", hooks.shown.single().channelName)
        signs.setLoudness(LifeSigns.targetOf(p), LifeSignLoudness.Off)
        assertEquals(LifeSignLoudness.Off, signs.loudness("p:anna"))
        assertEquals(LifeSignLoudness.Loud, signs.loudness("c1"))
        now += 60 * min
        source.list = listOf(status(p.copy(state = AnmpAliveState.END, lastAt = now), false, now))
        signs.evaluate()
        assertEquals(1, hooks.shown.size)
    }

    @Test
    fun checkInsAreSortedIntoTheChatByTheirStart() {
        val first = status(plan(start = t0), false, t0)
        val second = status(plan(start = t0 + 30 * min).copy(id = "p2"), false, t0 + 30 * min)
        val messages = listOf("m1" to t0 - min, "m2" to t0 + 10 * min, "m3" to t0 + 20 * min)
        val cards = app.anynear.ui.lifeSignCardsOf(listOf(second, first), messages)
        // Before the first message that is newer; after the last one under "".
        assertEquals(listOf("p1"), cards["m2"]?.map { it.plan.id })
        assertEquals(listOf("p2"), cards[""]?.map { it.plan.id })
        assertEquals(setOf("m2", ""), cards.keys)
        assertTrue(app.anynear.ui.lifeSignCardsOf(emptyList(), messages).isEmpty())
    }

    @Test
    fun someoneLookingKeepsTheAlarmQuietAndOffSilencesTheCircle() = runTest {
        var now = t0 + 31 * min
        val (source, hooks, signs) = setup { now }
        val p = plan().copy(care = listOf(AliveCare("ben", AnmpCareState.LOOKING, t0 + 30 * min)))
        source.list = listOf(status(p, false, now))
        signs.evaluate()
        assertEquals(AlertLoudness.Normal, hooks.shown.last().loudness)

        val (source2, hooks2, signs2) = setup { now }
        signs2.setLoudness("c1", LifeSignLoudness.Off)
        source2.list = listOf(status(plan(), false, now))
        signs2.evaluate()
        assertTrue(hooks2.shown.isEmpty())
        assertEquals(LifeSignLoudness.Off, signs2.loudness("c1"))
    }

    @Test
    fun ownPhoneAsksLoudlyAsksAgainAndClearsAfterConfirming() = runTest {
        var now = t0 + 20 * min
        val (source, hooks, signs) = setup { now }
        val p = plan(owner = "ich")
        source.list = listOf(status(p, true, now))
        source.next = now + 3 * min
        signs.evaluate()
        val ask = hooks.shown.single()
        assertEquals(AlertLoudness.Loud, ask.loudness)
        assertEquals("ok:p1", ask.actions.single().id)
        // Own friendly sound: not the same as the alarm one hears as a helper.
        assertEquals(AlertSound.Question, ask.sound)
        assertEquals(now + 3 * min, hooks.wake)
        // Ongoing notification with „Alles gut“ and „Bin fertig“, also for the lock screen.
        val checkIn = assertNotNull(hooks.checkIn)
        assertEquals("ok:p1", checkIn.okAction)
        assertEquals("end:p1", checkIn.endAction)
        signs.openFullScreen()
        assertTrue(signs.fullScreen.value)
        assertEquals("p1", signs.ownRunning())

        now += AliveLogic.ASK_AGAIN
        signs.evaluate()
        assertEquals(2, hooks.shown.size)

        signs.onAction("ok:p1")
        assertTrue("confirm p1" in source.calls)
        source.list = listOf(status(p.copy(state = AnmpAliveState.OK, lastAt = now, due = now + 20 * min), true, now))
        signs.evaluate()
        assertTrue(LifeSigns.channelOf("p1") in hooks.cancelled)

        source.list = listOf(status(p.copy(state = AnmpAliveState.END, lastAt = now), true, now))
        signs.evaluate()
        assertEquals(null, hooks.checkIn)
        assertTrue(!signs.fullScreen.value)
    }

    @Test
    fun startRemembersTheCircleAndASingleQuestionEndsWithAllGood() = runTest {
        val (source, _, signs) = setup { t0 }
        assertTrue(signs.start(LifeSignTemplate.Home, "c1"))
        assertEquals("c1", signs.defaultCircle())
        signs.confirm("p1")
        assertTrue("end p1" in source.calls)
        assertTrue(source.calls.none { it == "confirm p1" })
    }

    @Test
    fun supportCirclesAreNoChoiceAndCirclesWithoutKnownHelpersAre() {
        val signs = LifeSigns(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), FakeSource(), InMemoryStore(), Hooks(), { t0 },
            nameOf = { "" }, circleName = { "" }, helpersOf = { if (it == "anna") listOf("anna-person") else emptyList() },
            isSupport = { it == "support" },
        )
        val support = Circle("support", "Support: Frage", "s")
        val anna = Circle("anna", "Anna", "s")
        val fresh = Circle("fresh", "Neu", "s")
        val gone = Circle("gone", "Alt", "s", removed = true)
        val choices = signs.choices(listOf(support, fresh, anna, gone))
        assertEquals(listOf("fresh", "anna"), choices.map { it.id })
        // Nothing chosen before: the first circle with known helpers, not simply the first one.
        assertEquals("anna", signs.preselect(choices))
        assertEquals("fresh", signs.preselect(listOf(fresh)))
        assertEquals(null, signs.preselect(emptyList()))
        // Only support: nothing to choose, the dialog offers to create a circle.
        assertTrue(signs.choices(listOf(support)).isEmpty())
    }

    @Test
    fun helpersAndHeardComeFromReceiptsOfCircleMembers() {
        val (_, _, signs) = setup { t0 }
        val p = plan(owner = "ich").copy(heard = listOf(AliveCare("ben", 0, t0), AliveCare("fremd", 0, t0)))
        val (all, heard) = signs.helpers(status(p, true, t0))
        assertEquals(listOf("ben", "carla"), all)
        assertEquals(listOf("ben"), heard)
        assertTrue(signs.meAway(status(plan().copy(care = listOf(AliveCare("ich", AnmpCareState.AWAY, t0))), false, t0)))
    }

    /** The iOS notification extension shows what a packet says, with the marks of the app (ADR 0117). */
    @Test
    fun theExtensionShowsAStartOnceAndTheAppDoesNotRepeatIt() = runTest {
        val store = InMemoryStore()
        fun wire(state: Int, due: Long = t0 + 20 * min) = AnmpAlive(
            ByteArray(16), state, t0 / 1000, (t0 + 8 * 60 * min) / 1000, due / 1000, 300, 600, 1200, label = "Allein arbeiten",
        )
        val start = assertNotNull(lifeSignWake(store, "p1", "c1", "Anna", "Familie", wire(AnmpAliveState.START), t0, t0))
        assertEquals("alive:p1", start.channelId)
        assertEquals(AlertLoudness.Normal, start.loudness)
        assertTrue("Anna" in start.title)
        // Twice the same wake-up: nothing new.
        assertNull(lifeSignWake(store, "p1", "c1", "Anna", "Familie", wire(AnmpAliveState.START), t0, t0))
        // The app later evaluates with the same store and does not show the start again.
        val hooks = Hooks()
        val source = FakeSource().apply { list = listOf(status(plan(), false, t0)) }
        val signs = LifeSigns(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), source, store, hooks, { t0 },
            nameOf = { "Anna" }, circleName = { "Familie" }, helpersOf = { listOf("ich") }, me = { "ich" },
        )
        signs.evaluate()
        assertTrue(hooks.shown.isEmpty())
        // No confirmation: loud alarm, unless the circle is set to off.
        val missed = assertNotNull(lifeSignWake(store, "p1", "c1", "Anna", "Familie", wire(AnmpAliveState.MISSED), t0 + 30 * min, t0 + 30 * min))
        assertEquals(AlertPriority.Alarm, missed.priority)
        assertEquals(AlertLoudness.Loud, missed.loudness)
        signs.setLoudness("c1", LifeSignLoudness.Off)
        assertNull(lifeSignWake(store, "p1", "c1", "Anna", "Familie", wire(AnmpAliveState.MISSED, due = t0 + 60 * min), t0 + 70 * min, t0 + 70 * min))
        // A confirmation without an earlier warning shows nothing.
        assertNull(lifeSignWake(InMemoryStore(), "p2", "c1", "Anna", "Familie", wire(AnmpAliveState.OK), t0, t0))
    }

    /** ADR 0121 §10: the place goes to the core only if the person shares it with that circle „wenn etwas ist“. */
    @Test
    fun thePlaceIsHandedInOnlyWhenThePersonSharesItWithThatCircle() = runTest {
        var now = t0
        val store = InMemoryStore()
        val hooks = Hooks().apply { place = Place(50.1106, 8.6821, t0 - min, 12) }
        val source = FakeSource()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val sharing = LocationSharing(scope, store, hooks) { now }
        val signs = LifeSigns(
            scope, source, store, hooks, { now },
            nameOf = { "" }, circleName = { "Familie" }, helpersOf = { listOf("ben") }, me = { "ich" }, sharing = sharing,
        )
        val own = plan(owner = "ich")
        source.list = listOf(status(own, true, now))

        // Not answered yet: nobody sees anything, so nothing is handed in.
        signs.evaluate()
        assertTrue(source.calls.none { it.startsWith("place") })
        assertTrue(!signs.sharesPlace("c1"))

        // „Ja, wenn etwas ist“: the app asks the system and hands the place to the core, which keeps it for „nicht bestätigt“.
        val place = hooks.place
        hooks.place = null
        sharing.answer(ShareLevel.WhenNeeded)
        assertEquals(1, hooks.askedForExact)
        assertTrue(signs.sharesPlace("c1"))
        now += 11 * min
        signs.evaluate()
        // The system knows no place yet (no fix, or the app is in the background): nothing handed in, tried again soon.
        assertTrue(source.calls.none { it.startsWith("place") })
        hooks.place = place
        now += 30_000L
        signs.evaluate()
        assertTrue(source.calls.none { it.startsWith("place") })
        now += 31_000L
        signs.evaluate()
        assertEquals("place p1 50.1106,8.6821", source.calls.last { it.startsWith("place") })

        // The system answers with the same place again: not worth another hand-in.
        source.list = listOf(status(own.copy(position = AlivePosition(50.1106, 8.6821, t0 - min, 12)), true, now))
        val before = source.calls.size
        now += 11 * min
        signs.evaluate()
        assertEquals(before, source.calls.size)

        // This circle shall never see it: the core forgets the place.
        sharing.setLevel("c1", ShareLevel.Off)
        now += 11 * min
        signs.evaluate()
        assertEquals("place p1 none", source.calls.last())
    }

    /** ADR 0121 §10: the warning says that a place came along, and a button opens it in the maps app. */
    @Test
    fun aHelperLearnsThatThePlaceCameAlong() = runTest {
        val at = t0 + 20 * min
        val missed = plan(state = AnmpAliveState.MISSED).copy(lastAt = t0 + 23 * min, position = AlivePosition(50.1106, 8.6821, at, 12))
        val (source, hooks, signs) = setup { t0 + 31 * min }
        source.list = listOf(status(missed, false, t0 + 31 * min))
        signs.evaluate()
        val alarm = hooks.shown.last()
        assertEquals(AlertPriority.Alarm, alarm.priority)
        assertTrue("Standort" in alarm.text || "location" in alarm.text, alarm.text)
        signs.openMap(source.list.single())
        assertEquals(listOf("50.1106,8.6821 Anna"), hooks.opened)

        // The notification extension of the iPhone says the same from the packet alone.
        val wire = AnmpAlive(
            ByteArray(16), AnmpAliveState.MISSED, t0 / 1000, (t0 + 8 * 60 * min) / 1000, (t0 + 20 * min) / 1000, 300, 600, 1200,
            label = "Allein arbeiten", position = AnmpPosition(501_106, 86_821, at / 1000, 12),
        )
        val woken = assertNotNull(lifeSignWake(InMemoryStore(), "p9", "c1", "Anna", "Familie", wire, t0 + 23 * min, t0 + 23 * min))
        assertTrue("Standort" in woken.text || "location" in woken.text, woken.text)
        // Without a place the text stays as it was.
        val bare = AnmpAlive(ByteArray(16), AnmpAliveState.MISSED, t0 / 1000, (t0 + 8 * 60 * min) / 1000, (t0 + 20 * min) / 1000, 300, 600, 1200)
        val plain = assertNotNull(lifeSignWake(InMemoryStore(), "p8", "c1", "Anna", "Familie", bare, t0 + 23 * min, t0 + 23 * min))
        assertTrue("Standort" !in plain.text && "location" !in plain.text, plain.text)
    }

    /**
     * ADR 0083 §5.7: the phone is off. The alarm it left at the relays and the deadline on this phone are one alarm:
     * whichever comes first rings, the other adds nothing but the place.
     */
    @Test
    fun aPhoneThatWentSilentRingsOnceWithItsPlace() = runTest {
        val at = t0 + 2 * min
        val due = t0 + 20 * min
        val wire = AnmpAlive(
            ByteArray(16), AnmpAliveState.MISSED, t0 / 1000, (t0 + 8 * 60 * min) / 1000, due / 1000, 300, 600, 1200,
            label = "Allein arbeiten", position = AnmpPosition(501_106, 86_821, at / 1000, 12), guard = true,
        )
        // iPhone, app closed: the notification extension rings when the relays release the alarm.
        val store = InMemoryStore()
        val woken = assertNotNull(lifeSignWake(store, "p1", "c1", "Anna", "Familie", wire, t0, t0 + 30 * min))
        assertEquals(AlertPriority.Alarm, woken.priority)
        assertEquals(AlertLoudness.Loud, woken.loudness)
        val lt = app.anynear.i18n.texts.lifeSigns
        assertEquals(lt.unreachableTitle("Anna"), woken.title)
        assertTrue("Standort" in woken.text || "location" in woken.text, woken.text)
        // A second relay wakes for the same alarm: nothing new.
        assertNull(lifeSignWake(store, "p1", "c1", "Anna", "Familie", wire, t0, t0 + 30 * min))
        // The app opens and takes the alarm over from the core: it is the one already shown.
        val hooks = Hooks()
        val reported = plan().copy(guard = true, position = AlivePosition(50.1106, 8.6821, at, 12))
        val source = FakeSource().apply { list = listOf(status(reported, false, t0 + 30 * min)) }
        val signs = LifeSigns(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), source, store, hooks, { t0 + 30 * min },
            nameOf = { "Anna" }, circleName = { "Familie" }, helpersOf = { listOf("ich") }, me = { "ich" },
        )
        signs.evaluate()
        assertTrue(hooks.shown.isEmpty())

        // Android, deadline on this phone first: it rings without a place; the released alarm then adds the place to
        // the card and does not ring again.
        val (source2, hooks2, signs2) = setup { t0 + 31 * min }
        source2.list = listOf(status(plan(), false, t0 + 31 * min))
        signs2.evaluate()
        assertEquals(1, hooks2.shown.size)
        assertEquals(lt.unreachableTitle("Anna"), hooks2.shown.last().title)
        source2.list = listOf(status(reported, false, t0 + 31 * min))
        signs2.evaluate()
        assertEquals(1, hooks2.shown.size)
        assertEquals(AliveStatus.ALARM, source2.list.single().phase)
        assertNotNull(source2.list.single().plan.position)

        // The released alarm first, on a phone whose own deadline has not run out by its clock: it rings, with the place.
        val (source3, hooks3, signs3) = setup { t0 + 27 * min }
        source3.list = listOf(status(reported, false, t0 + 27 * min))
        signs3.evaluate()
        val alarm = hooks3.shown.last()
        assertEquals(AlertPriority.Alarm, alarm.priority)
        assertEquals(lt.unreachableTitle("Anna"), alarm.title)
        assertTrue("Standort" in alarm.text || "location" in alarm.text, alarm.text)
    }
}
