package app.anynear

import app.anynear.app.NoOpPlatformHooks
import app.anynear.data.ObservedKeyValueStore
import app.anynear.location.LocationSharing
import app.anynear.location.Place
import app.anynear.location.PlaceSharer
import app.anynear.location.PlaceSource
import app.anynear.location.SavedPlace
import app.anynear.location.SavedPlaces
import app.anynear.location.ShareLevel
import app.anynear.location.ShareTarget
import app.anynear.location.SharedPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The four levels per circle and person, and what leaves the phone at each of them (ADR 0121 §3–§5). */
class PlaceSharerTest {
    private var t = 1_800_000_000_000L

    private class Hooks : NoOpPlatformHooks() {
        var place: Place? = null
        override val preciseLocationAllowed: StateFlow<Boolean> = MutableStateFlow(true)
        override suspend fun currentPlace(): Place? = place
        val inUse = mutableMapOf<String, String>()
        var watching = false
        override fun watchMoves(on: Boolean) { watching = on }
        override fun setLocationInUse(reason: String, text: String?) { if (text == null) inUse.remove(reason) else inUse[reason] = text }
    }

    private class Source : PlaceSource {
        data class Sent(val person: String, val level: ShareLevel, val place: Place?, val radius: Int?, val until: Long, val label: String? = null, val since: Long = 0)

        val sent = mutableListOf<Sent>()
        override val changes = MutableSharedFlow<String>()
        override val asks = MutableSharedFlow<String>()
        var shared = listOf<SharedPlace>()
        override fun places(): List<SharedPlace> = shared
        override suspend fun share(personId: String, level: ShareLevel, place: Place?, radiusMeters: Int?, untilMillis: Long, label: String?, sinceMillis: Long): Boolean {
            sent += Sent(personId, level, place, radiusMeters, untilMillis, label, sinceMillis)
            return true
        }
        override suspend fun ask(personId: String) = true
    }

    private val anna = ShareTarget("anna", "Anna", listOf("familie"))
    private val tom = ShareTarget("tom", "Tom", listOf("familie"))
    private val nachbar = ShareTarget("nachbar", "Nachbar", listOf("nachbarn"))

    private class Setup(val sharing: LocationSharing, val sharer: PlaceSharer, val source: Source, val hooks: Hooks, val saved: SavedPlaces)

    private fun setup(targets: List<ShareTarget>): Setup {
        val store = ObservedKeyValueStore(InMemoryStore())
        val hooks = Hooks().apply { place = Place(50.110649, 8.682127, t, 12) }
        val sharing = LocationSharing(CoroutineScope(Dispatchers.Unconfined), store, hooks) { t }
        val source = Source()
        val saved = SavedPlaces(CoroutineScope(Dispatchers.Unconfined), store, now = { t }, nextMidnight = { (it / 86_400_000 + 1) * 86_400_000 })
        val sharer = PlaceSharer(CoroutineScope(Dispatchers.Unconfined), source, sharing, store, ownDeviceId = { "handy" }, now = { t }, saved = saved)
        sharer.targets = { targets }
        return Setup(sharing, sharer, source, hooks, saved)
    }

    @Test
    fun withTheDefaultOnlyTheLevelLeavesThePhone() = runTest {
        val s = setup(listOf(anna, nachbar))
        s.sharer.sync()
        // Nobody answered the question yet: nothing at all.
        assertTrue(s.source.sent.isEmpty())
        s.sharing.answer(ShareLevel.WhenNeeded)
        s.sharer.sync()
        assertEquals(setOf("anna", "nachbar"), s.source.sent.map { it.person }.toSet())
        assertTrue(s.source.sent.all { it.level == ShareLevel.WhenNeeded && it.place == null })
        // Nothing new: nothing is sent again.
        val before = s.source.sent.size
        s.sharer.sync()
        assertEquals(before, s.source.sent.size)
    }

    @Test
    fun approximateIsAFixedAreaAndNeverThePoint() = runTest {
        val s = setup(listOf(anna, nachbar))
        s.sharing.answer(ShareLevel.WhenNeeded)
        s.sharing.setLevel("familie", ShareLevel.Area, members = listOf("anna"))
        s.sharer.sync()
        val toAnna = s.source.sent.last { it.person == "anna" }
        assertEquals(ShareLevel.Area, toAnna.level)
        assertEquals(PlaceSharer.AREA_RADIUS, toAnna.radius)
        val centre = assertNotNull(toAnna.place)
        assertEquals(PlaceSharer.areaOf(50.110649, 8.682127), centre.latitude to centre.longitude)
        assertTrue(centre.latitude != 50.110649)
        // No accuracy that would hint at the point.
        assertNull(centre.accuracyMeters)
        // The neighbour is in another circle and stays at „wenn etwas ist“.
        assertNull(s.source.sent.last { it.person == "nachbar" }.place)
        // This device became the one the location comes from.
        assertEquals("handy", s.sharing.source.value)

        // A few streets further, same cell: no news.
        val count = s.source.sent.size
        s.hooks.place = Place(50.1120, 8.6850, t, 10)
        s.sharer.sync()
        assertEquals(count, s.source.sent.size)
        // Another cell: the new area.
        s.hooks.place = Place(50.1500, 8.6850, t, 10)
        s.sharer.sync()
        assertEquals(count + 1, s.source.sent.size)
    }

    @Test
    fun withoutALocatedPlaceTheLastOneIsNotTakenAway() = runTest {
        val s = setup(listOf(anna))
        s.sharing.answer(ShareLevel.WhenNeeded)
        s.sharing.setPersonLevel("anna", ShareLevel.Exact)
        s.sharer.sync()
        assertNotNull(s.source.sent.last().place)
        val before = s.source.sent.size
        // At night the phone cannot locate itself: nothing goes out, Anna keeps the last place.
        s.hooks.place = null
        t += 25L * 24 * 3_600_000L
        s.sharer.sync()
        assertEquals(before, s.source.sent.size)
        // A lower level still goes out at once, without a point.
        s.sharing.setPersonLevel("anna", ShareLevel.WhenNeeded)
        s.sharer.sync()
        assertEquals(ShareLevel.WhenNeeded, s.source.sent.last().level)
        assertNull(s.source.sent.last().place)
    }

    @Test
    fun someoneNewInACircleSeesThePlaceOnlyAfterTheOk() = runTest {
        val s = setup(listOf(anna, tom))
        s.sharing.answer(ShareLevel.WhenNeeded)
        s.sharing.setLevel("familie", ShareLevel.Exact, members = listOf("anna"))
        s.sharer.sync()
        assertNotNull(s.source.sent.last { it.person == "anna" }.place)
        assertNull(s.source.sent.last { it.person == "tom" }.place)
        assertEquals(listOf(tom), s.sharer.newcomers())
        s.sharing.approve(listOf("tom"))
        s.sharer.sync()
        assertEquals(50.110649, s.source.sent.last { it.person == "tom" }.place?.latitude)
        assertTrue(s.sharer.newcomers().isEmpty())
    }

    @Test
    fun pauseAndTimeLimitsTakeThePlaceAway() = runTest {
        val s = setup(listOf(anna))
        s.sharing.answer(ShareLevel.WhenNeeded)
        s.sharing.setPersonLevel("anna", ShareLevel.Exact, untilMillis = t + 3_600_000L)
        s.sharing.approve(listOf("anna"))
        s.sharer.sync()
        assertEquals(t + 3_600_000L, s.source.sent.last().until)
        assertNotNull(s.source.sent.last().place)

        // Paused: nothing goes out, not even that it is paused; Anna keeps what she saw last.
        val quiet = s.source.sent.size
        s.sharing.setPaused(true)
        s.hooks.place = Place(50.2000, 8.7000, t, 10)
        s.sharer.sync()
        assertEquals(quiet, s.source.sent.size)
        assertNull(s.sharer.riding("anna", radio = true))
        // Going on: she gets the place as it is now.
        s.sharing.setPaused(false)
        s.sharer.sync()
        assertEquals(ShareLevel.Exact, s.source.sent.last().level)
        assertEquals(50.2000, s.source.sent.last().place?.latitude)

        // The hour is over: back to the default by itself.
        t += 3_600_001L
        s.sharer.sync()
        assertEquals(ShareLevel.WhenNeeded, s.source.sent.last().level)
        assertNull(s.source.sent.last().place)
    }

    @Test
    fun onlyTheChosenDeviceSends() = runTest {
        val s = setup(listOf(anna))
        s.sharing.answer(ShareLevel.WhenNeeded)
        s.sharing.setSource("tablet")
        s.sharer.sync()
        assertTrue(s.source.sent.isEmpty())
        s.sharing.setSource("handy")
        s.sharer.sync()
        assertEquals(1, s.source.sent.size)
    }

    @Test
    fun anAnswerToARequestShowsThePlaceForAWhile() = runTest {
        val s = setup(listOf(anna))
        s.sharing.answer(ShareLevel.WhenNeeded)
        s.sharer.answer("anna", null)
        assertEquals(ShareLevel.WhenNeeded, s.sharing.levelFor("anna", listOf("familie")))
        s.sharer.answer("anna", 0)
        assertEquals(ShareLevel.Exact, s.sharing.levelFor("anna", listOf("familie")))
        assertEquals(t + PlaceSharer.ONCE_MILLIS, s.sharing.untilFor("anna", listOf("familie")))
        t += PlaceSharer.ONCE_MILLIS + 1
        assertEquals(ShareLevel.WhenNeeded, s.sharing.levelFor("anna", listOf("familie")))
    }

    /** ADR 0121 §13: „ungefähr“ is a fixed honeycomb cell, and a border at the front door does not show. */
    @Test
    fun theAreaIsAHoneycombCellThatSticksAtItsBorder() {
        val centre = PlaceSharer.areaOf(50.110649, 8.682127)
        // The centre of a cell lies in that cell, and the place is at most a corner's distance away.
        assertEquals(centre, PlaceSharer.areaOf(centre.first, centre.second))
        assertTrue(PlaceSharer.distanceMeters(50.110649, 8.682127, centre.first, centre.second) <= PlaceSharer.AREA_RADIUS + 30)
        val corners = PlaceSharer.areaCorners(centre.first, centre.second, PlaceSharer.AREA_RADIUS)
        assertEquals(6, corners.size)
        for ((lat, lon) in corners) assertEquals(PlaceSharer.AREA_RADIUS.toDouble(), PlaceSharer.distanceMeters(centre.first, centre.second, lat, lon), 30.0)
        // Just across the border (about 1.1 km east of the centre): fresh it is the neighbour, coming from here it stays.
        val across = centre.first to centre.second + 1100.0 / (111_200.0 * 0.6293)
        assertTrue(PlaceSharer.areaOf(across.first, across.second) != centre)
        assertEquals(centre, PlaceSharer.areaOf(across.first, across.second, last = centre))
        // Well inside the neighbour the cell changes.
        val far = centre.first to centre.second + 1500.0 / (111_200.0 * 0.6293)
        assertTrue(PlaceSharer.areaOf(far.first, far.second, last = centre) != centre)
    }

    /** ADR 0121 §13: while a place leaves the phone all the time the system is told, and the person reads who sees it. */
    @Test
    fun thePhoneSaysForAsLongAsAPlaceIsSharedAllTheTime() = runTest {
        val s = setup(listOf(anna, nachbar))
        s.sharing.answer(ShareLevel.WhenNeeded)
        s.sharer.sync()
        // „Wenn etwas ist“ shares nothing all the time: nothing runs in the background.
        assertTrue(s.hooks.inUse.isEmpty())
        s.sharing.setLevel("familie", ShareLevel.Exact, untilMillis = t + 3_600_000L, members = listOf("anna"))
        s.sharer.sync()
        assertTrue("Anna" in s.hooks.inUse.values.single())
        s.sharing.setPaused(true)
        s.sharer.sync()
        assertTrue(s.hooks.inUse.isEmpty())
        s.sharing.setPaused(false)
        s.sharer.sync()
        assertEquals(1, s.hooks.inUse.size)
        // The hour is over: it ends by itself.
        t += 3_600_001L
        s.sharer.sync()
        assertTrue(s.hooks.inUse.isEmpty())
    }

    /** ADR 0121 §13: sharing with a destination ends by itself near it, and the app is told so. */
    @Test
    fun sharingWithADestinationEndsOnArrival() = runTest {
        val s = setup(listOf(anna))
        s.sharing.answer(ShareLevel.WhenNeeded)
        var arrived: PlaceSharer.Escort? = null
        s.sharer.onArrived = { arrived = it }
        // On the way: two kilometres from the destination.
        s.sharer.setEscort(PlaceSharer.Escort(circleId = "familie", planId = "plan", untilMillis = t + 3_600_000L, destLatitude = 50.1286, destLongitude = 8.682127, destLabel = "Zu Hause"), members = listOf("anna"))
        s.sharer.sync()
        assertNull(arrived)
        assertNotNull(s.source.sent.last().place)
        assertEquals(ShareLevel.Exact, s.sharing.level("familie"))

        // Eighty metres from it: arrived. The circle is back at what it was, and no place goes out any more.
        s.hooks.place = Place(50.1279, 8.682127, t, 10)
        s.sharer.sync()
        assertEquals("plan", arrived?.planId)
        assertNull(s.sharer.escort.value)
        assertEquals(ShareLevel.WhenNeeded, s.sharing.level("familie"))
        assertNull(s.source.sent.last().place)
        assertTrue(s.hooks.inUse.isEmpty())

        // To a single person, and stopped by hand: no arrival is reported.
        arrived = null
        s.sharer.setEscort(PlaceSharer.Escort(personId = "anna", untilMillis = t + 600_000L))
        s.sharer.sync()
        assertEquals(ShareLevel.Exact, s.sharing.levelFor("anna", listOf("familie")))
        s.sharer.setEscort(null)
        assertEquals(ShareLevel.WhenNeeded, s.sharing.levelFor("anna", listOf("familie")))
        assertNull(arrived)
    }

    @Test
    fun theNameOfASavedPlaceGoesToWhoSeesThePointOnly() = runTest {
        val s = setup(listOf(anna, nachbar))
        s.saved.save(SavedPlace("home", "Zu Hause", 50.110649, 8.682127))
        s.sharing.answer(ShareLevel.WhenNeeded)
        s.sharing.setPersonLevel("anna", ShareLevel.Exact)
        s.sharing.setPersonLevel("nachbar", ShareLevel.Area)
        s.sharer.sync()
        val toAnna = s.source.sent.last { it.person == "anna" }
        assertEquals("Zu Hause", toAnna.label)
        assertEquals(t, toAnna.since)
        // Who sees the area only does not learn that this is home.
        val toNeighbour = s.source.sent.last { it.person == "nachbar" }
        assertNotNull(toNeighbour.place)
        assertNull(toNeighbour.label)

        // The person allows the name for the area too.
        s.saved.save(SavedPlace("home", "Zu Hause", 50.110649, 8.682127, withArea = true))
        s.sharer.sync()
        assertEquals("Zu Hause", s.source.sent.last { it.person == "nachbar" }.label)

        // Away from home: the name is gone again.
        s.hooks.place = Place(50.1500, 8.6850, t, 10)
        s.sharer.sync()
        assertNull(s.source.sent.last { it.person == "anna" }.label)
        assertNull(s.source.sent.last { it.person == "nachbar" }.label)
    }

    @Test
    fun thePlaceRidesAlongWithADirectMessageOnlyForWhoSeesIt() = runTest {
        val s = setup(listOf(anna, nachbar))
        s.sharing.answer(ShareLevel.WhenNeeded)
        s.sharing.setPersonLevel("anna", ShareLevel.Exact)
        // The phone does not know where it is yet: a message never waits for it.
        assertNull(s.sharer.riding("anna", radio = true))
        s.sharer.sync()
        // Over the internet only news; over radio always, it costs no packet of its own there.
        assertNull(s.sharer.riding("anna", radio = false))
        val radio = assertNotNull(s.sharer.riding("anna", radio = true))
        assertEquals(ShareLevel.Exact, radio.level)
        assertNull(radio.radiusMeters)
        // Who sees the place only when something is wrong gets none with a message.
        assertNull(s.sharer.riding("nachbar", radio = true))

        // Moved since: the next message carries it, and the sharing does not send it again.
        s.hooks.place = Place(50.1500, 8.6850, t, 10)
        s.sharer.locate()
        val news = assertNotNull(s.sharer.riding("anna", radio = false))
        assertEquals(50.1500, news.place.latitude)
        s.sharer.rode("anna", news)
        val count = s.source.sent.size
        s.sharer.sync()
        assertEquals(count, s.source.sent.size)

        // An old place does not ride along.
        t += 30 * 60_000L
        assertNull(s.sharer.riding("anna", radio = true))
    }

    /** 20.11.2026: who pauses everything does not watch the others either; they stand where they were last known. */
    @Test
    fun whilePausedNothingNewOfTheOthersIsShown() = runTest {
        val s = setup(listOf(anna))
        s.sharer.start()
        s.source.shared = listOf(SharedPlace("anna", ShareLevel.Exact, Place(50.10, 8.60, t, 10), atMillis = t))
        s.source.changes.emit("anna")
        assertEquals(50.10, s.sharer.places.value["anna"]?.place?.latitude)

        s.sharing.setPaused(true)
        t += 60_000L
        s.source.shared = listOf(SharedPlace("anna", ShareLevel.Exact, Place(50.30, 8.60, t, 10), atMillis = t), SharedPlace("tom", ShareLevel.Exact, Place(50.40, 8.60, t, 10), atMillis = t))
        s.source.changes.emit("anna")
        // Anna stays where she was, and Tom, who came after pausing, does not show at all.
        assertEquals(50.10, s.sharer.places.value["anna"]?.place?.latitude)
        assertNull(s.sharer.places.value["tom"])

        // Going on shows what came in since.
        s.sharing.setPaused(false)
        assertEquals(50.30, s.sharer.places.value["anna"]?.place?.latitude)
        assertNotNull(s.sharer.places.value["tom"])
    }

    /** 20.11.2026: what was known when pausing is still there after the app was closed and opened again. */
    @Test
    fun whatWasKnownWhenPausingSurvivesARestart() = runTest {
        val store = ObservedKeyValueStore(InMemoryStore())
        var sharing: LocationSharing? = null
        fun start(source: Source): PlaceSharer {
            val own = LocationSharing(CoroutineScope(Dispatchers.Unconfined), store, Hooks()) { t }
            sharing = own
            return PlaceSharer(CoroutineScope(Dispatchers.Unconfined), source, own, store, ownDeviceId = { "handy" }, now = { t }).also {
                it.targets = { listOf(anna) }
                it.start()
            }
        }
        val source = Source().apply { shared = listOf(SharedPlace("anna", ShareLevel.Exact, Place(50.10, 8.60, t, 10), atMillis = t, label = "Zuhause")) }
        start(source)
        sharing!!.setPaused(true)

        // Closed and opened again while paused; Anna moved in the meantime.
        t += 600_000L
        val later = Source().apply { shared = listOf(SharedPlace("anna", ShareLevel.Exact, Place(50.30, 8.60, t, 10), atMillis = t)) }
        val again = start(later)
        assertEquals(50.10, again.places.value["anna"]?.place?.latitude)
        assertEquals("Zuhause", again.places.value["anna"]?.label)
    }

    /** 20.11.2026: at rest the hourly check stretches to six hours; a move brings it back to one. */
    @Test
    fun atRestTheHourlyCheckStretches() {
        val hour = PlaceSharer.IDLE_MILLIS
        var gap = hour
        gap = PlaceSharer.nextIdleGap(gap, moved = false, quiet = true)
        assertEquals(2 * hour, gap)
        gap = PlaceSharer.nextIdleGap(gap, moved = false, quiet = true)
        gap = PlaceSharer.nextIdleGap(gap, moved = false, quiet = true)
        assertEquals(PlaceSharer.IDLE_MAX_MILLIS, gap)
        // Opening the app at the same place changes nothing about it.
        assertEquals(gap, PlaceSharer.nextIdleGap(gap, moved = false, quiet = false))
        // Moved: hourly again.
        assertEquals(hour, PlaceSharer.nextIdleGap(gap, moved = true, quiet = true))
    }

    /** ADR 0121 §24: the system reports moves only while someone sees the place for good, and not while paused. */
    @Test
    fun movesAreWatchedOnlyWhileSomeoneSeesThePlaceForGood() = runTest {
        val s = setup(listOf(anna))
        s.sharing.answer(ShareLevel.WhenNeeded)
        s.sharer.sync()
        assertTrue(!s.hooks.watching)
        s.sharing.setPersonLevel("anna", ShareLevel.Area)
        s.sharer.sync()
        assertTrue(s.hooks.watching)
        s.sharing.setPaused(true)
        s.sharer.sync()
        assertTrue(!s.hooks.watching)
        s.sharing.setPaused(false)
        s.sharer.sync()
        assertTrue(s.hooks.watching)
        s.sharing.setPersonLevel("anna", ShareLevel.WhenNeeded)
        s.sharer.sync()
        assertTrue(!s.hooks.watching)
    }

    /** ADR 0121 §24: a level alone never keeps the phone locating; only an action with an end does. */
    @Test
    fun onlyAnActionKeepsLocatingALevelIsBroughtUpToDateWhenSomethingHappens() = runTest {
        val s = setup(listOf(anna))
        s.sharing.answer(ShareLevel.WhenNeeded)
        s.sharing.setPersonLevel("anna", ShareLevel.Exact)
        s.sharer.sync()
        // Anna sees the place, but nothing runs in the background and the system shows no notification.
        assertNotNull(s.source.sent.last { it.person == "anna" }.place)
        assertTrue(!s.sharer.live())
        assertTrue(s.hooks.inUse.isEmpty())

        // Moved, and nothing happens: Anna keeps the old place.
        val count = s.source.sent.size
        s.hooks.place = Place(50.1500, 8.6850, t, 10)
        assertEquals(count, s.source.sent.size)
        // The app is opened or a message goes out: now she gets the new one, once.
        s.sharer.touch()
        assertEquals(count + 1, s.source.sent.size)
        s.hooks.place = Place(50.2000, 8.6850, t, 10)
        s.sharer.touch()
        assertEquals(count + 1, s.source.sent.size)
        t += PlaceSharer.TOUCH_GAP_MILLIS
        s.sharer.touch()
        assertEquals(count + 2, s.source.sent.size)

        // The phone had no place when the app was opened: that try does not count, the next one goes out at once.
        t += PlaceSharer.TOUCH_GAP_MILLIS
        s.hooks.place = null
        s.sharer.touch()
        val without = s.source.sent.size
        s.hooks.place = Place(50.3000, 8.6850, t, 10)
        s.sharer.touch()
        assertEquals(without + 1, s.source.sent.size)
        assertNotNull(s.source.sent.last { it.person == "anna" }.place)

        // Sharing for an hour is an action: the phone keeps locating and says so.
        s.sharing.setPersonLevel("anna", ShareLevel.Exact, untilMillis = t + 3_600_000L)
        s.sharer.sync()
        assertTrue(s.sharer.live())
        assertTrue(s.hooks.inUse.isNotEmpty())
    }
}
