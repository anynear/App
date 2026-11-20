package app.anynear

import app.anynear.location.KnownPlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A reader's phone names where someone is from the places it knows itself (ADR 0121 §21). */
class KnownPlaceTest {
    private val home = KnownPlace("h", "Zu Hause", 50.1106, 8.6821, 150)
    private val garden = KnownPlace("g", "Garten", 50.1110, 8.6825, 60, circleId = "familie")

    @Test
    fun thePlaceAroundAPointIsFoundAndTheNearestWinsWhereTheyOverlap() {
        assertEquals("Zu Hause", KnownPlace.at(listOf(home), 50.1107, 8.6822)?.name)
        // Both reach here; the garden is nearer.
        assertEquals("Garten", KnownPlace.at(listOf(home, garden), 50.11098, 8.68248)?.name)
        // Outside of both, and with no places at all.
        assertNull(KnownPlace.at(listOf(home, garden), 50.2, 8.7))
        assertNull(KnownPlace.at(emptyList(), 50.1106, 8.6821))
    }
}
