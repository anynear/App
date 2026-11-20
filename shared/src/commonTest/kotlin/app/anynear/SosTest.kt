package app.anynear

import app.anynear.alerts.AlertLoudness
import app.anynear.alerts.AlertNotification
import app.anynear.alerts.AlertSound
import app.anynear.anmp.core.AliveStatus
import app.anynear.anmp.core.SosCall
import app.anynear.lifesigns.LifeSignLoudness
import app.anynear.app.NoOpPlatformHooks
import app.anynear.data.ObservedKeyValueStore
import app.anynear.location.Place
import app.anynear.sos.Sos
import app.anynear.sos.SosHelpers
import app.anynear.sos.SosSource
import app.anynear.sos.sosWake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Calling the helpers (ADR 0083 §2–§4, ADR 0121 §13): who is called, what goes along, and the alarm at a helper. */
class SosTest {
    private val t0 = 1_800_000_000_000L

    private class Hooks : NoOpPlatformHooks() {
        val shown = mutableListOf<AlertNotification>()
        val cancelled = mutableListOf<String>()
        var dialed: String? = null
        override fun showAlertNotification(notification: AlertNotification) { shown += notification }
        override fun cancelAlertNotifications(channelId: String) { cancelled += channelId }
        override fun batteryPercent(): Int? = 37
        override fun dial(number: String) { dialed = number }
    }

    private class Source : SosSource {
        override val changes = MutableSharedFlow<String>(extraBufferCapacity = 8)
        var all = listOf<SosCall>()
        var raised: Triple<List<String>, List<String>, Int?>? = null
        var cared: String? = null
        override fun calls() = all
        override suspend fun raise(circleIds: List<String>, personIds: List<String>, place: Place?, battery: Int?, trail: List<Place>): String? {
            raised = Triple(circleIds, personIds, battery)
            all = all + SosCall("call-1", "ich", own = true, start = 1, lastAt = 1, circles = circleIds, persons = personIds)
            return "call-1"
        }
        override suspend fun update(id: String, place: Place?, battery: Int?, trail: List<Place>) = true
        override suspend fun end(id: String): Boolean { all = all.map { if (it.id == id) it.copy(ended = true) else it }; return true }
        override suspend fun care(id: String): Boolean { cared = id; all = all.map { if (it.id == id) it.copy(caring = it.caring + "ich") else it }; return true }

        // Contacts for calls (ADR 0121 §25): in these tests this phone said yes to Anna, unless a test says otherwise.
        override val contactChanges = MutableSharedFlow<String>(extraBufferCapacity = 8)
        var agreedTo = listOf("anna")
        var asking = listOf<app.anynear.anmp.core.SosAsk>()
        var asked: Pair<List<String>, List<String>>? = null
        var answered: Pair<String, Boolean>? = null
        override fun agreed() = agreedTo
        override fun asks() = asking
        override suspend fun ask(circleIds: List<String>, personIds: List<String>): Boolean { asked = circleIds to personIds; return true }
        override suspend fun answer(person: String, agree: Boolean): Boolean {
            answered = person to agree
            agreedTo = if (agree) agreedTo + person else agreedTo - person
            asking = asking.filter { it.person != person }
            return true
        }
    }

    private fun sos(source: Source = Source(), hooks: Hooks = Hooks(), store: ObservedKeyValueStore = ObservedKeyValueStore(InMemoryStore())) =
        Sos(CoroutineScope(Dispatchers.Unconfined), source, store, hooks, sharing = null, now = { t0 }, nameOf = { if (it == "anna") "Anna" else "" }).also { it.start() }

    @Test
    fun withoutHelpersNothingGoesOutAndTheHelpersAreASettingOfThePerson() = runTest {
        val source = Source()
        val store = ObservedKeyValueStore(InMemoryStore())
        val s = sos(source, store = store)
        assertEquals(Sos.Raise.NoHelpers, s.raise())
        assertNull(source.raised)
        s.setHelpers(SosHelpers(circles = setOf("familie"), persons = setOf("anna")))
        assertEquals("c:familie,p:anna", store.getString(Sos.KEY_HELPERS))
        // Another own device reads the same helpers.
        assertEquals(SosHelpers(setOf("familie"), setOf("anna")), sos(store = store).helpers.value)

        assertEquals(Sos.Raise.Sent, s.raise())
        assertEquals(Triple(listOf("familie"), listOf("anna"), 37), source.raised)
        assertNotNull(s.own())
        assertTrue(s.end())
        assertNull(s.own())
    }

    @Test
    fun fiveTapsCountDownOnlyWithHelpersAndCanBeCancelled() {
        val s = sos()
        s.countDown()
        // No helpers yet: the page opens for choosing them, nothing counts down.
        assertEquals("", s.screen.value)
        assertTrue(!s.counting.value)
        s.setHelpers(SosHelpers(circles = setOf("familie")))
        s.countDown()
        assertTrue(s.counting.value)
        s.cancelCountDown()
        assertTrue(!s.counting.value)
        s.close()
        assertNull(s.screen.value)
    }

    @Test
    fun aHelperIsWokenLoudlyOnceAndTheAlarmStopsWhenItLooksAfterIt() = runTest {
        val source = Source()
        val hooks = Hooks()
        val s = sos(source, hooks)
        source.all = listOf(SosCall("call-9", "anna", own = false, start = t0, lastAt = t0))
        source.changes.emit("call-9")
        val alarm = hooks.shown.single()
        assertEquals(AlertLoudness.Loud, alarm.loudness)
        // Its own sound, so that one knows without looking that a person calls.
        assertEquals(AlertSound.Sos, alarm.sound)
        assertEquals(Sos.channelOf("call-9"), alarm.channelId)
        assertTrue("Anna" in alarm.title)
        // An update of the same call does not ring again.
        source.changes.emit("call-9")
        assertEquals(1, hooks.shown.size)

        assertTrue(s.care("call-9"))
        assertEquals("call-9", source.cared)
        assertTrue(Sos.channelOf("call-9") in hooks.cancelled)

        // The all-clear: a quiet notice, once.
        source.all = source.all.map { it.copy(ended = true) }
        source.changes.emit("call-9")
        source.changes.emit("call-9")
        assertEquals(2, hooks.shown.size)
        assertEquals(AlertLoudness.Normal, hooks.shown.last().loudness)

        s.dialEmergency()
        assertEquals("112", hooks.dialed)
    }

    /** 19.11.2026: closing the page while the call goes out must not stop it. */
    @Test
    fun aCallGoesOutAlsoWhenThePageIsClosedAtOnce() = runTest {
        val source = Source()
        val s = sos(source)
        s.setHelpers(SosHelpers(circles = setOf("familie")))
        s.open()
        s.raiseNow()
        s.close()
        assertEquals(listOf("familie"), source.raised?.first)
        assertNotNull(s.own())
        assertTrue(!s.raising.value && !s.failed.value)
        // Closing during the countdown of the five taps does stop it: nothing was sent yet.
        source.all = emptyList()
        source.changes.emit("x")
        s.countDown()
        assertTrue(s.counting.value)
        s.close()
        assertTrue(!s.counting.value)
    }

    // --- A check-in nobody confirmed is a call for the helpers (ADR 0083 §5.4) ---

    private fun missed(reason: Int = AliveStatus.REASON_MISSED, id: String = "call-p1") =
        SosCall(id, "anna", own = false, start = t0, lastAt = t0, via = "familie", plan = "p1", reason = reason)

    @Test
    fun aCheckInNobodyConfirmedRingsOnceAsACallAndSaysWhy() = runTest {
        val source = Source()
        val hooks = Hooks()
        sos(source, hooks)
        source.all = listOf(missed())
        source.changes.emit("call-p1")
        val alarm = hooks.shown.single()
        assertEquals(AlertLoudness.Loud, alarm.loudness)
        assertEquals(AlertSound.Sos, alarm.sound)
        assertEquals("Anna hat das Lebenszeichen nicht bestätigt", alarm.title)
        // The packet of the phone, the alarm of the relays and the own deadline are the same call: no second alarm.
        source.all = listOf(missed().copy(battery = 40, lastAt = t0 + 1000))
        source.changes.emit("call-p1")
        assertEquals(1, hooks.shown.size)
        // „Alles gut“ after all: the alarm goes; the check-in itself says that all is well.
        source.all = source.all.map { it.copy(ended = true) }
        source.changes.emit("call-p1")
        assertEquals(1, hooks.shown.size)
        assertTrue(Sos.channelOf("call-p1") in hooks.cancelled)

        // A phone that went silent has its own words.
        source.all = listOf(missed(AliveStatus.REASON_UNREACHABLE, "call-p2"))
        source.changes.emit("call-p2")
        assertEquals("Anna ist nicht erreichbar", hooks.shown.last().title)
    }

    @Test
    fun suchACallIsAsLoudAsCheckInsOfItsCircleAndTheExtensionsAlarmCounts() = runTest {
        val source = Source()
        val hooks = Hooks()
        val store = ObservedKeyValueStore(InMemoryStore())
        val s = sos(source, hooks, store)
        var volume = LifeSignLoudness.Normal
        s.lifeSignLoudness = { circle -> if (circle == "familie") volume else LifeSignLoudness.Loud }
        source.all = listOf(missed())
        source.changes.emit("call-p1")
        assertEquals(AlertLoudness.Normal, hooks.shown.single().loudness)
        volume = LifeSignLoudness.Off
        source.all = listOf(missed(id = "call-p2"))
        source.changes.emit("call-p2")
        assertEquals(1, hooks.shown.size)
        // A call by hand is always loud.
        volume = LifeSignLoudness.Off
        source.all = listOf(SosCall("call-3", "anna", own = false, start = t0, lastAt = t0, via = "familie"))
        source.changes.emit("call-3")
        assertEquals(AlertLoudness.Loud, hooks.shown.last().loudness)

        // A check-in for me alone came directly: what I set for that person counts.
        var asked = ""
        s.lifeSignLoudness = { key -> asked = key; LifeSignLoudness.Normal }
        source.all = listOf(SosCall("call-p7", "anna", own = false, start = t0, lastAt = t0, plan = "p7", reason = AliveStatus.REASON_MISSED))
        source.changes.emit("call-p7")
        assertEquals("p:anna", asked)
        assertEquals(AlertLoudness.Normal, hooks.shown.last().loudness)
        hooks.shown.removeAt(hooks.shown.lastIndex)
        s.lifeSignLoudness = { circle -> if (circle == "familie") volume else LifeSignLoudness.Loud }

        // The iPhone rang for the check-in while the app was closed: the app does not ring again for that call.
        volume = LifeSignLoudness.Loud
        Sos.markShown(store, "call-p4")
        source.all = listOf(missed(id = "call-p4"))
        source.changes.emit("call-p4")
        assertEquals(2, hooks.shown.size)
    }

    /** 20.11.2026: a locked iPhone is treated like a phone whose app runs: it rings for the call, once. */
    @Test
    fun theExtensionRingsForACallOnceAndAsLoudAsTheCircleSaysForCheckIns() {
        val store = InMemoryStore()
        val byHand = assertNotNull(sosWake(store, "call-1", "familie", "Anna", fromCheckIn = false, unreachable = false, atMillis = t0))
        assertEquals(AlertLoudness.Loud, byHand.loudness)
        assertEquals(AlertSound.Sos, byHand.sound)
        assertEquals(Sos.channelOf("call-1"), byHand.channelId)
        assertTrue("Anna" in byHand.title)
        // A second holder wakes for the same call, and later the app opens: no second alarm.
        assertNull(sosWake(store, "call-1", "familie", "Anna", fromCheckIn = false, unreachable = false, atMillis = t0))
        assertTrue("call-1" in store.getString(Sos.KEY_SHOWN).orEmpty())

        val missed = assertNotNull(sosWake(store, "call-2", "familie", "Anna", fromCheckIn = true, unreachable = false, atMillis = t0))
        assertEquals("Anna hat das Lebenszeichen nicht bestätigt", missed.title)
        assertEquals(AlertLoudness.Loud, missed.loudness)
        assertEquals("Anna ist nicht erreichbar", sosWake(store, "call-3", "familie", "Anna", fromCheckIn = true, unreachable = true, atMillis = t0)?.title)

        // Check-ins of the circle set to normal or off: the call that came from one follows; one by hand stays loud.
        store.putString("lifesigns.loudness", """{"familie":"Normal","nachbarn":"Off"}""")
        assertEquals(AlertLoudness.Normal, sosWake(store, "call-4", "familie", "Anna", fromCheckIn = true, unreachable = false, atMillis = t0)?.loudness)
        assertNull(sosWake(store, "call-5", "nachbarn", "Anna", fromCheckIn = true, unreachable = false, atMillis = t0))
        assertEquals(AlertLoudness.Loud, sosWake(store, "call-6", "nachbarn", "Anna", fromCheckIn = false, unreachable = false, atMillis = t0)?.loudness)
    }

    @Test
    fun allGoodOnTheOwnCallOfACheckInConfirmsTheCheckIn() = runTest {
        val source = Source()
        val s = sos(source)
        val confirmed = mutableListOf<String>()
        s.confirmCheckIn = { plan ->
            confirmed += plan
            source.all = source.all.map { it.copy(ended = true) }
        }
        source.all = listOf(SosCall("call-p1", "ich", own = true, start = t0, lastAt = t0, circles = listOf("familie"), plan = "p1", reason = AliveStatus.REASON_MISSED))
        source.changes.emit("call-p1")
        assertEquals("call-p1", s.own()?.id)
        assertTrue(s.end())
        assertEquals(listOf("p1"), confirmed)
        assertNull(s.own())
        // A call by hand is shown before one that came from a check-in.
        source.all = listOf(
            SosCall("call-p2", "ich", own = true, start = t0, lastAt = t0, circles = listOf("familie"), plan = "p2"),
            SosCall("call-5", "ich", own = true, start = t0, lastAt = t0, circles = listOf("familie")),
        )
        source.changes.emit("call-5")
        assertEquals("call-5", s.own()?.id)
    }

    /** ADR 0121 §25: nobody can make someone else's phone ring without a yes. */
    @Test
    fun aCallOnlyRingsForSomeoneThisPersonSaidYesTo() = runTest {
        val source = Source().apply { agreedTo = emptyList() }
        val hooks = Hooks()
        val s = sos(source, hooks)
        source.all = listOf(SosCall("call-7", "anna", own = false, start = t0, lastAt = t0))
        source.changes.emit("call-7")
        // Shown, but quietly, and it does not ring again.
        assertEquals(AlertLoudness.Normal, hooks.shown.single().loudness)
        s.ringAgain()
        assertEquals(1, hooks.shown.size)

        // Anna asks: a quiet notice once, and the question waits in the app.
        source.asking = listOf(app.anynear.anmp.core.SosAsk("anna", t0))
        source.contactChanges.emit("anna")
        source.contactChanges.emit("anna")
        assertEquals(2, hooks.shown.size)
        assertTrue("Anna" in hooks.shown.last().title)
        assertEquals(listOf("anna"), s.asks.value.map { it.person })
        assertTrue(s.answer("anna", true))
        assertEquals("anna" to true, source.answered)
        assertEquals(listOf("anna"), s.agreed.value)
        assertTrue(s.asks.value.isEmpty())
        // Answered: the notice about the question goes.
        assertTrue("sos-ask" in hooks.cancelled)

        // From now on her call rings.
        source.all = listOf(SosCall("call-8", "anna", own = false, start = t0, lastAt = t0))
        source.changes.emit("call-8")
        assertEquals(AlertLoudness.Loud, hooks.shown.last().loudness)

        // Choosing contacts asks them.
        s.setHelpers(SosHelpers(circles = setOf("familie"), persons = setOf("dora")))
        assertEquals(listOf("familie") to listOf("dora"), source.asked)
    }

    @Test
    fun itRingsAgainEveryTwoMinutesFiveTimesUntilSomeoneLooksAfterIt() = runTest {
        val source = Source()
        val hooks = Hooks()
        var t = t0
        val s = Sos(CoroutineScope(Dispatchers.Unconfined), source, ObservedKeyValueStore(InMemoryStore()), hooks, sharing = null, now = { t }, nameOf = { "Anna" }, membersOf = { listOf("ben", "tom") }).also { it.start() }
        source.all = listOf(SosCall("call-9", "anna", own = false, start = t0, lastAt = t0))
        source.changes.emit("call-9")
        assertEquals(1, hooks.shown.size)
        // Too early: nothing.
        t += 60_000L
        s.ringAgain()
        assertEquals(1, hooks.shown.size)
        repeat(8) {
            t += Sos.RING_GAP_MILLIS
            s.ringAgain()
        }
        // The first alarm and five more, all loud; then it stays quiet.
        assertEquals(1 + Sos.RING_TIMES, hooks.shown.size)
        assertTrue(hooks.shown.all { it.loudness == AlertLoudness.Loud })

        // Another call, and someone looks after it: no more ringing.
        source.all = listOf(SosCall("call-10", "anna", own = false, start = t, lastAt = t))
        source.changes.emit("call-10")
        val before = hooks.shown.size
        source.all = source.all.map { it.copy(caring = listOf("ben")) }
        source.changes.emit("call-10")
        t += Sos.RING_GAP_MILLIS
        s.ringAgain()
        assertEquals(before, hooks.shown.size)

        // The own call: with how many helpers it should arrive.
        assertEquals(3, s.helperCount(SosCall("own", "ich", own = true, start = t, lastAt = t, circles = listOf("familie"), persons = listOf("dora"))))
    }

    /** 19.11.2026: the sound of a call and the official one are not for own channels; those get a calmer warning. */
    @Test
    fun soundsOfChannelsKeepApartFromTheCallAndTheOfficialOne() {
        val own = AlertSound.choicesFor(curated = false)
        assertTrue(AlertSound.Warning in own && AlertSound.Alarm in own)
        assertTrue(AlertSound.Official !in own && AlertSound.Sos !in own && AlertSound.Question !in own)
        val curated = AlertSound.choicesFor(curated = true)
        assertTrue(AlertSound.Official in curated && AlertSound.Sos !in curated && AlertSound.Warning !in curated)
        // An own channel that was set to the official sound before rings with its default.
        assertEquals(AlertSound.Alarm, AlertSound.allowedFor(curated = false, chosen = AlertSound.Official))
        assertEquals(AlertSound.Warning, AlertSound.allowedFor(curated = false, chosen = AlertSound.Warning))
        assertEquals(AlertSound.Official, AlertSound.allowedFor(curated = true, chosen = AlertSound.Warning))
    }
}
