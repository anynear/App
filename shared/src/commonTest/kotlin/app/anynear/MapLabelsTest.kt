package app.anynear

import app.anynear.location.MapPack
import app.anynear.ui.LabelCandidate
import app.anynear.ui.MapLabels
import app.anynear.ui.PlaceRank
import app.anynear.ui.PlaceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which names the map shows at which zoom and who wins the room (20.11.2026, „wie bei Google Maps“). */
class MapLabelsTest {
    private fun city(inhabitants: Long) = PlaceRank(PlaceType.CITY, MapLabels.sizeOf(inhabitants)!!)
    private fun town(inhabitants: Long) = PlaceRank(PlaceType.TOWN, MapLabels.sizeOf(inhabitants)!!)
    private fun village(inhabitants: Long) = PlaceRank(PlaceType.VILLAGE, MapLabels.sizeOf(inhabitants)!!)
    private val suburb = PlaceRank(PlaceType.SUBURB, MapLabels.defaultSize(PlaceType.SUBURB))
    private val quarter = PlaceRank(PlaceType.QUARTER, MapLabels.defaultSize(PlaceType.QUARTER))
    private val hamlet = PlaceRank(PlaceType.HAMLET, MapLabels.defaultSize(PlaceType.HAMLET))

    // Inhabitants as in OpenStreetMap (rounded).
    private val berlin = city(3_700_000)
    private val frankfurt = city(773_000)
    private val wiesbaden = city(283_000)
    private val badHomburg = town(54_000)
    private val eltville = town(17_000)
    private val walluf = village(5_800)
    private val small = village(1_200)

    private fun shown(rank: PlaceRank, metresPerDp: Double) = MapLabels.placeShown(rank, metresPerDp)

    @Test
    fun sizeIsTwiceTheDecimalLogarithmOfInhabitants() {
        assertEquals(13, MapLabels.sizeOf(3_700_000))
        assertEquals(11, MapLabels.sizeOf(773_000))
        assertEquals(10, MapLabels.sizeOf(283_000))
        assertEquals(10, MapLabels.sizeOf(100_000))
        assertEquals(9, MapLabels.sizeOf(99_999))
        assertEquals(8, MapLabels.sizeOf(10_000))
        assertEquals(0, MapLabels.sizeOf(1))
        assertEquals(15, MapLabels.sizeOf(40_000_000))
        assertNull(MapLabels.sizeOf(0))
    }

    @Test
    fun theKindCarriesTypeAndSizeAndOldKindsStillRead() {
        for (type in PlaceType.entries.drop(1)) for (size in 0..15) {
            val kind = MapLabels.kindOf(PlaceRank(type, size))
            assertTrue(kind in MapLabels.RANKED..127, "$type $size → $kind")
            assertEquals(PlaceRank(type, size), MapLabels.placeOf(kind, MapPack.REGION, "10-1-1"))
        }
        // As scripts/maptiles.py writes them: 32 + 16 · type + size.
        assertEquals(32 + 10, MapLabels.kindOf(wiesbaden))
        assertEquals(32 + 16 * 4 + 8, MapLabels.kindOf(suburb))
        // Kinds of before: a city, a place in a region tile, a quarter in a near tile, a country.
        assertEquals(PlaceType.CITY, MapLabels.placeOf(MapPack.CITY, 0, MapPack.BASE)?.type)
        assertEquals(PlaceType.TOWN, MapLabels.placeOf(MapPack.PLACE, 0, MapPack.BASE)?.type)
        assertEquals(PlaceType.VILLAGE, MapLabels.placeOf(MapPack.PLACE, MapPack.REGION, "10-1-1")?.type)
        assertEquals(PlaceType.QUARTER, MapLabels.placeOf(MapPack.PLACE, MapPack.NEAR, "12-1-1")?.type)
        assertEquals(PlaceType.COUNTRY, MapLabels.placeOf(MapPack.COUNTRY, 0, MapPack.WORLD)?.type)
        // Roads, waters and the land are no places.
        for (kind in listOf(MapPack.MOTORWAY, MapPack.STREET, MapPack.WATER, MapPack.STREAM, MapPack.LAND)) assertNull(MapLabels.placeOf(kind, MapPack.NEAR, "12-1-1"))
    }

    @Test
    fun zoomingInBringsFirstTheLargeCitiesThenTownsThenQuartersThenStreets() {
        // All of Germany on a phone, about 2,000 m per dp: only the large cities.
        assertTrue(shown(berlin, 2_000.0) && shown(frankfurt, 2_000.0))
        assertFalse(shown(wiesbaden, 2_000.0))
        // The Rhine-Main area, about 500 m per dp: the cities of 100,000 and more.
        assertTrue(shown(wiesbaden, 500.0))
        assertFalse(shown(badHomburg, 500.0))
        // About 40 km across the screen: the towns.
        assertTrue(shown(badHomburg, 100.0) && shown(eltville, 100.0))
        assertFalse(shown(walluf, 100.0) || shown(suburb, 100.0))
        // A city fills the screen: villages and the quarters of the city.
        assertTrue(shown(walluf, 30.0))
        assertTrue(shown(suburb, 20.0) && shown(small, 20.0))
        assertFalse(shown(quarter, 20.0) || shown(hamlet, 20.0))
        assertFalse(MapLabels.lineNameShown(MapPack.MAIN, 20.0))
        // Close: smaller quarters and the main roads, then every street.
        assertTrue(shown(quarter, 10.0) && shown(hamlet, 10.0))
        assertTrue(MapLabels.lineNameShown(MapPack.MAIN, 10.0))
        assertFalse(MapLabels.lineNameShown(MapPack.STREET, 10.0))
        assertTrue(MapLabels.lineNameShown(MapPack.STREET, 3.0))
        assertFalse(MapLabels.lineNameShown(MapPack.PATH, 1.0))
        // Countries only from far out.
        val germany = PlaceRank(PlaceType.COUNTRY, 15)
        assertTrue(shown(germany, 5_000.0))
        assertFalse(shown(germany, 1_100.0))
    }

    @Test
    fun whatShowsFarOutStillShowsCloser() {
        val ranks = PlaceType.entries.drop(1).flatMap { type -> (0..15).map { PlaceRank(type, it) } }
        val zooms = generateSequence(20_000.0) { it * 0.8 }.takeWhile { it > 0.5 }.toList()
        for (rank in ranks) for ((far, near) in zooms.zipWithNext()) {
            if (shown(rank, far)) assertTrue(shown(rank, near), "$rank at $far but not at $near")
        }
    }

    @Test
    fun largerPlacesRankHigherAndEveryPlaceBeforeEveryStreet() {
        val order = listOf(berlin, frankfurt, wiesbaden, badHomburg, eltville, walluf, small).map(MapLabels::placePriority)
        assertEquals(order.sortedDescending(), order)
        assertTrue(MapLabels.placePriority(wiesbaden) > MapLabels.placePriority(suburb))
        assertTrue(MapLabels.placePriority(suburb) > MapLabels.placePriority(quarter))
        // Of the same size the city before the town before the village before the quarter.
        val sameSize = listOf(PlaceType.CITY, PlaceType.TOWN, PlaceType.VILLAGE, PlaceType.SUBURB, PlaceType.QUARTER).map { MapLabels.placePriority(PlaceRank(it, 8)) }
        assertEquals(sameSize.sortedDescending(), sameSize)
        val lowestPlace = MapLabels.placePriority(PlaceRank(PlaceType.QUARTER, 0))
        for (kind in listOf(MapPack.MOTORWAY, MapPack.MAIN, MapPack.SIDE, MapPack.STREET, MapPack.STREAM)) assertTrue(MapLabels.linePriority(kind) < lowestPlace)
        assertTrue(MapLabels.linePriority(MapPack.MAIN) > MapLabels.linePriority(MapPack.SIDE))
        assertTrue(MapLabels.linePriority(MapPack.SIDE) > MapLabels.linePriority(MapPack.STREET))
    }

    private fun label(text: String, rank: PlaceRank, x: Float, y: Float, tie: Int = 0) =
        LabelCandidate(text, MapLabels.placePriority(rank), x - 40, y - 10, x + 40, y + 10, room = 8f, tie = tie)

    @Test
    fun aCityIsNamedByItselfNotByTheQuarterAtItsCentre() {
        // Wiesbaden and its quarter „Mitte“ lie a few hundred metres apart; around them the other quarters.
        val labels = listOf(
            label("Mitte", suburb, 500f, 500f),
            label("Dotzheim", suburb, 300f, 520f),
            label("Wiesbaden", wiesbaden, 520f, 505f),
            label("Biebrich", suburb, 520f, 700f),
            label("Schierstein", suburb, 330f, 650f),
        )
        for (input in listOf(labels, labels.reversed(), labels.sortedBy { it.text })) {
            val chosen = MapLabels.choose(input, 1000f, 1000f).map { it.text }
            assertTrue("Wiesbaden" in chosen, chosen.toString())
            assertFalse("Mitte" in chosen, chosen.toString())
            assertEquals(listOf("Wiesbaden", "Biebrich", "Dotzheim", "Schierstein"), chosen)
        }
    }

    @Test
    fun theLargerCityWinsAndTiesGoByTheOrderOfTheFileNotByChance() {
        val offenbach = city(130_000)
        val a = MapLabels.choose(listOf(label("Offenbach", offenbach, 510f, 500f), label("Frankfurt", frankfurt, 500f, 500f)), 1000f, 1000f)
        assertEquals(listOf("Frankfurt"), a.map { it.text })
        // Mainz and Wiesbaden have the same size: the one later in the file (more inhabitants) wins, in any order.
        val mainz = label("Mainz", city(220_000), 500f, 505f, tie = 3)
        val wiesbaden = label("Wiesbaden", wiesbaden, 500f, 495f, tie = 4)
        assertEquals(listOf("Wiesbaden"), MapLabels.choose(listOf(mainz, wiesbaden), 1000f, 1000f).map { it.text })
        assertEquals(listOf("Wiesbaden"), MapLabels.choose(listOf(wiesbaden, mainz), 1000f, 1000f).map { it.text })
    }

    @Test
    fun aStreetIsNamedOnceAndNamesStayOnThePicture() {
        fun street(x: Float, tie: Int) = LabelCandidate("Rheinstraße", MapLabels.linePriority(MapPack.MAIN), x, 100f, x + 80, 116f, once = true, tie = tie)
        val chosen = MapLabels.choose(listOf(street(100f, 10), street(400f, 50), street(700f, 20)), 1000f, 1000f)
        assertEquals(1, chosen.size)
        // The longest piece carries the name.
        assertEquals(400f, chosen.single().left)
        assertTrue(MapLabels.choose(listOf(label("Rand", eltville, 10f, 500f)), 1000f, 1000f).isEmpty())
    }

    @Test
    fun aTownAndItsQuarterOfTheSameNameAreNamedOnce() {
        // Idstein and its quarter „Idstein“ a little below: one name, not the same twice.
        val town = label("Idstein", eltville, 500f, 500f)
        // As on the tablet: about one and a half names apart, below and to the side.
        val quarter = label("Idstein", suburb, 590f, 545f)
        assertEquals(listOf("Idstein"), MapLabels.choose(listOf(town, quarter), 1000f, 1000f).map { it.text })
        // Far apart, two places of the same name both keep theirs.
        val other = label("Idstein", suburb, 500f, 900f)
        assertEquals(2, MapLabels.choose(listOf(town, other), 1000f, 1000f).size)
    }

    @Test
    fun namesKeepTheirRoom() {
        val a = label("Eltville", eltville, 500f, 500f)
        // 8 px of room: a box 6 px away is too close, one 20 px away is fine.
        val near = label("Walluf", walluf, 500f, 526f)
        val far = label("Walluf", walluf, 500f, 540f)
        assertEquals(listOf("Eltville"), MapLabels.choose(listOf(a, near), 1000f, 1000f).map { it.text })
        assertEquals(listOf("Eltville", "Walluf"), MapLabels.choose(listOf(a, far), 1000f, 1000f).map { it.text })
    }
}
