package app.anynear

import app.anynear.i18n.MapTextsDe
import app.anynear.location.PlaceAge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A place of a level says from which day it is (ADR 0121 §24). */
class PlaceAgeTest {
    private val day = 86_400_000L
    private val midnight = 20_800 * day

    @Test
    fun daysAreCountedAsPeopleCountThem() {
        // A minute before midnight and just after the one before: both today.
        assertEquals(0, PlaceAge.days(midnight - 60_000L, midnight))
        assertEquals(0, PlaceAge.days(midnight - day, midnight))
        assertEquals(1, PlaceAge.days(midnight - day - 1, midnight))
        assertEquals(3, PlaceAge.days(midnight - 3 * day - 5, midnight))
        // A clock that runs ahead does not give a day in the future.
        assertEquals(0, PlaceAge.days(midnight + 5 * day, midnight))
        assertTrue(!PlaceAge.gone(midnight - 27 * day, midnight))
        assertTrue(PlaceAge.gone(midnight - 28 * day - 1, midnight))
    }

    @Test
    fun theDayIsSaidInWords() {
        assertEquals("heute", MapTextsDe.dayAgo(0))
        assertEquals("gestern", MapTextsDe.dayAgo(1))
        assertEquals("vor 3 Tagen", MapTextsDe.dayAgo(3))
        assertEquals("vor 1 Woche", MapTextsDe.dayAgo(7))
        assertEquals("vor 1 Woche", MapTextsDe.dayAgo(13))
        assertEquals("vor 2 Wochen", MapTextsDe.dayAgo(14))
        assertEquals("vor 3 Wochen", MapTextsDe.dayAgo(27))
    }
}
