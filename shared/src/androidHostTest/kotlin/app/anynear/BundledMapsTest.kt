package app.anynear

import app.anynear.location.MapPack
import app.anynear.location.Outlines
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The maps shipped with the app (`scripts/worldmap.py`, `scripts/maptiles.py`; ADR 0121 §11): world, Europe, Germany. */
class BundledMapsTest {
    private fun pack(name: String) = assertNotNull(MapPack.parse(File("src/commonMain/composeResources/files/regions/$name.amp").readBytes()), name)

    private fun MapPack.names(kind: Int) = (0 until size).filter { kinds[it].toInt() == kind }.mapNotNull { name(it) }

    /** Cities with their rank (ui/MapLabels.kt), in the order of the file. */
    private fun MapPack.cities() = (0 until size).filter { app.anynear.ui.MapLabels.placeOf(kinds[it].toInt(), level, key)?.type == app.anynear.ui.PlaceType.CITY }.mapNotNull { name(it) }

    @Test
    fun theWorldHasItsCountriesInBothLanguages() {
        val world = pack("world")
        assertEquals(MapPack.WORLD, world.key)
        val countries = world.names(MapPack.COUNTRY)
        assertTrue(countries.size > 200, "Länder: ${countries.size}")
        assertTrue("Deutschland\tGermany" in countries)
        assertTrue("Frankreich\tFrance" in countries)
        assertTrue((0 until world.size).count { world.kinds[it].toInt() == MapPack.LAND } > 300)
        assertTrue(world.cities().any { MapPack.localName(it, german = false) == "Tokyo" })
        // The whole world: from the far west to the far east.
        assertTrue(world.minX < 0.02f && world.maxX > 0.98f)
    }

    @Test
    fun europeHasRoadsRiversAndCitiesButLeavesGermanyToItsOwnMap() {
        val europe = pack("europe")
        val cities = europe.cities().map { MapPack.localName(it, german = true) }
        assertTrue("Paris" in cities && "Wien" in cities && "Rom" in cities, cities.take(20).toString())
        assertTrue("Berlin" !in cities)
        assertTrue((0 until europe.size).count { europe.kinds[it].toInt() == MapPack.MOTORWAY } > 100)
        assertTrue(europe.names(MapPack.STREAM).isNotEmpty())
        val germany = pack("base")
        val german = germany.cities()
        assertTrue("Berlin" in german && "München" in german)
        // The largest city is the last one: among places of the same rank the later one wins.
        assertEquals("Berlin", german.last())
        assertTrue(germany.minX > Outlines.x(5.0).toFloat() && germany.maxX < Outlines.x(16.0).toFloat())
    }

    @Test
    fun theMapsStaySmall() {
        val total = listOf("world", "europe", "base").sumOf { File("src/commonMain/composeResources/files/regions/$it.amp").length() }
        assertTrue(total < 900_000, "Bytes: $total")
    }
}
