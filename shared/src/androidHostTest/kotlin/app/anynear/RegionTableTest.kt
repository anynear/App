package app.anynear

import app.anynear.alerts.GeoPoint
import app.anynear.alerts.RegionTable
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** District for the approximate location with the real table from `composeResources/files/regions` (scripts/regions.py). */
class RegionTableTest {
    private val table = File("src/commonMain/composeResources/files/regions").let { dir ->
        RegionTable.parse(File(dir, "kreise.csv").readText(), File(dir, "gemeinden.csv").readText())
    }

    private fun kreis(latitude: Double, longitude: Double) = table.nearest(GeoPoint(latitude, longitude))?.kreis

    @Test
    fun tableHasAllGermanDistricts() {
        assertTrue(table.size > 10_000, "Gemeinden: ${table.size}")
        assertEquals("Frankfurt am Main", table.nearest(GeoPoint(50.1106, 8.6821))?.name)
        // Municipality centre from gemeinden.csv, just beyond the city limits of Frankfurt.
        assertEquals("Landkreis Offenbach", table.nearest(GeoPoint(50.0546, 8.6953))?.name)
    }

    @Test
    fun knownPlacesGiveTheirDistrict() {
        assertEquals("06412", kreis(50.1106, 8.6821)) // Frankfurt, Römer
        assertEquals("09162", kreis(48.1374, 11.5755)) // Munich, Marienplatz
        assertEquals("11000", kreis(52.5163, 13.3777)) // Berlin, Brandenburg Gate
        assertEquals("02000", kreis(53.5503, 9.9920)) // Hamburg, city hall
        assertEquals("01056", kreis(54.1817, 7.8857)) // Heligoland (Pinneberg district), 40 km off the coast
    }

    @Test
    fun nearTheBorderTheNearestCentreDecides() {
        // Between Frankfurt (centre 50.11088, 8.682433) and Offenbach am Main (50.105567, 8.764442), 360 m
        // apart: left 2.7 km to Frankfurt and 3.2 km to Offenbach, right 3.05 km to Frankfurt and 2.83 km to
        // Offenbach; no other municipality is closer (recalculated from gemeinden.csv).
        assertEquals("06412", kreis(50.1080, 8.7200))
        assertEquals("06413", kreis(50.1080, 8.7250))
    }

    @Test
    fun outsideGermanyThereIsNoDistrict() {
        assertNull(kreis(40.4168, -3.7038)) // Madrid
        assertNull(kreis(48.8566, 2.3522)) // Paris
        // Basel is less than 5 km from the centre of a municipality in the Lörrach district: its warnings count there.
        assertEquals("08336", kreis(47.5596, 7.5886))
    }
}
