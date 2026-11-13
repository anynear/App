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
import app.anynear.anmp.core.AliveStatus
import app.anynear.lifesigns.LifeSignLoudness
import app.anynear.lifesigns.LifeSignSource
import app.anynear.lifesigns.LifeSignTemplate
import app.anynear.lifesigns.LifeSigns
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        override suspend fun start(circleId: String, label: String, until: Long, quiet: Long, firstDue: Long?): AlivePlan {
            calls += "start $circleId $label ${until - firstDue.let { 0 }} $quiet ${firstDue != null}"
            return AlivePlan("p1", circleId, "me", label, 0, until, firstDue ?: quiet, 5 * 60_000L, 10 * 60_000L, quiet, state = AnmpAliveState.START, lastAt = 0)
        }
        override suspend fun confirm(planId: String): Boolean { calls += "confirm $planId"; return true }
        override suspend fun end(planId: String): Boolean { calls += "end $planId"; return true }
        override suspend fun care(planId: String, state: Int): Boolean { calls += "care $planId $state"; return true }
        override suspend fun tick(): Long? = next
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
}
