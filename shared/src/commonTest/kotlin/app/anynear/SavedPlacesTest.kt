package app.anynear

import app.anynear.data.ObservedKeyValueStore
import app.anynear.location.Place
import app.anynear.location.SavedPlace
import app.anynear.location.SavedPlaces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Saved places: where the phone is among them, and what it proposes to save (ADR 0121 §13). */
class SavedPlacesTest {
    private val day = 86_400_000L
    private val hour = 3_600_000L

    /** 2026-11-16, a Monday, 00:00 UTC; the tests take UTC as the local time. */
    private val monday = 20_773 * day

    private val store = ObservedKeyValueStore(InMemoryStore())
    private fun places() = SavedPlaces(CoroutineScope(Dispatchers.Unconfined), store, now = { monday }, nextMidnight = { (it / day + 1) * day })

    private fun at(millis: Long, lat: Double = 50.110649, lon: Double = 8.682127, accuracy: Int = 12) = Place(lat, lon, millis, accuracy)

    @Test
    fun thePhoneKnowsSinceWhenItIsAtASavedPlace() {
        val p = places()
        p.save(SavedPlace("home", "Zu Hause", 50.110649, 8.682127))
        assertNull(p.here())
        p.onPlace(at(monday + 18 * hour))
        // Still there an hour later: since the first time.
        p.onPlace(at(monday + 19 * hour, lat = 50.1110))
        val (place, since) = assertNotNull(p.here())
        assertEquals("Zu Hause", place.name)
        assertEquals(monday + 18 * hour, since)
        // It survives a restart of the app.
        assertEquals(monday + 18 * hour, places().here()?.second)
        // An inaccurate fix far away says nothing.
        p.onPlace(at(monday + 20 * hour, lat = 50.2, accuracy = 900))
        assertNotNull(p.here())
        p.onPlace(at(monday + 20 * hour, lat = 50.2))
        assertNull(p.here())
        // Removing the place removes being there.
        p.onPlace(at(monday + 21 * hour))
        p.remove("home")
        assertNull(p.here())
    }

    @Test
    fun threeNightsProposeHomeAndNoIsRemembered() {
        val p = places()
        p.onPlace(at(monday + 23 * hour))
        // The same night after midnight counts once.
        p.onPlace(at(monday + day + 2 * hour))
        p.onPlace(at(monday + day + 23 * hour))
        assertNull(p.proposal.value)
        p.onPlace(at(monday + 2 * day + 23 * hour))
        val proposal = assertNotNull(p.proposal.value)
        assertEquals(SavedPlaces.Kind.Home, proposal.kind)
        p.dismiss(proposal)
        p.onPlace(at(monday + 3 * day + 23 * hour))
        assertNull(p.proposal.value)
    }

    @Test
    fun threeWorkingDaysProposeWorkButNotTheWeekend() {
        val p = places()
        // Saturday and Sunday before: no working days.
        p.onPlace(at(monday - 2 * day + 11 * hour))
        p.onPlace(at(monday - day + 11 * hour))
        p.onPlace(at(monday + 11 * hour))
        p.onPlace(at(monday + day + 11 * hour))
        assertNull(p.proposal.value)
        p.onPlace(at(monday + 2 * day + 11 * hour))
        assertEquals(SavedPlaces.Kind.Work, p.proposal.value?.kind)
        // Saved: the question is gone and does not come back here.
        p.save(SavedPlace("work", "Arbeit", 50.110649, 8.682127))
        assertNull(p.proposal.value)
        p.onPlace(at(monday + 3 * day + 11 * hour))
        assertNull(p.proposal.value)
    }
}
