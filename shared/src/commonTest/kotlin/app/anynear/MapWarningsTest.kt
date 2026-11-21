package app.anynear

import app.anynear.alerts.Alert
import app.anynear.alerts.AlertPriority
import app.anynear.ui.MapCamera
import app.anynear.ui.MapTap
import app.anynear.ui.WarningKind
import app.anynear.ui.areaNames
import app.anynear.ui.coversKreis
import app.anynear.ui.mapTap
import app.anynear.ui.placeWarningPopup
import app.anynear.ui.warningKind
import app.anynear.ui.warningPopupMaxHeight
import app.anynear.ui.warningsAt
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapWarningsTest {
    private fun alert(id: String, priority: AlertPriority, ts: Long, vararg area: String) =
        Alert(channelId = "nina", id = id, timestampMillis = ts, priority = priority, title = id, text = "", area = area.toList())

    @Test
    fun aDistrictIsCoveredByItsOwnKeyItsStateAndLongerKeysInIt() {
        assertTrue(coversKreis(listOf("06412"), "06412"))
        assertTrue(coversKreis(listOf("06"), "06412"))
        assertTrue(coversKreis(listOf("064120000000"), "06412"))
        assertFalse(coversKreis(listOf("06411"), "06412"))
        assertFalse(coversKreis(listOf("07"), "06412"))
        assertFalse(coversKreis(listOf(""), "06412"))
    }

    @Test
    fun allWarningsOfTheDistrictTheHighestLevelFirstThenTheNewest() {
        val storm = alert("storm", AlertPriority.Warning, 100, "06412")
        val fire = alert("fire", AlertPriority.Alarm, 50, "06412", "06413")
        val flood = alert("flood", AlertPriority.Warning, 200, "06")
        val elsewhere = alert("elsewhere", AlertPriority.Alarm, 300, "09162")
        assertEquals(listOf("fire", "flood", "storm"), warningsAt(listOf(storm, fire, flood, elsewhere), "06412").map { it.id })
        assertEquals(listOf("elsewhere"), warningsAt(listOf(storm, fire, flood, elsewhere), "09162").map { it.id })
    }

    @Test
    fun noDistrictOrNoAreaMeansNoWarning() {
        val everywhere = alert("everywhere", AlertPriority.Alarm, 1)
        assertEquals(emptyList(), warningsAt(listOf(everywhere, alert("a", AlertPriority.Warning, 1, "06412")), null))
        assertEquals(emptyList(), warningsAt(listOf(everywhere), "06412"))
    }

    @Test
    fun theSameWarningOnlyOnce() {
        val a = alert("a", AlertPriority.Warning, 1, "06412")
        assertEquals(1, warningsAt(listOf(a, a), "06412").size)
    }

    @Test
    fun aPersonGoesBeforeTheWarningAreaBelow() {
        val active = listOf(alert("a", AlertPriority.Alarm, 1, "06412"))
        assertEquals(MapTap.Person("peer"), mapTap("peer", "06412", active))
        val tap = mapTap(null, "06412", active)
        assertTrue(tap is MapTap.Warnings)
        assertEquals("06412", tap.kreis)
        assertEquals(listOf("a"), tap.alerts.map { it.id })
        assertEquals(MapTap.Nothing, mapTap(null, "06411", active))
        assertEquals(MapTap.Nothing, mapTap(null, null, active))
    }

    @Test
    fun theKindComesFromTheSystemOfTheSource() {
        assertEquals(WarningKind.Weather, warningKind("NINA (DWD)"))
        assertEquals(WarningKind.Flood, warningKind("NINA (Hochwasser)"))
        assertEquals(WarningKind.Police, warningKind("NINA (Polizei)"))
        assertEquals(WarningKind.CivilProtection, warningKind("NINA (BBK)"))
        assertEquals(WarningKind.CivilProtection, warningKind("NINA (KATWARN)"))
        assertEquals(WarningKind.CivilProtection, warningKind("NINA (BIWAPP)"))
        assertNull(warningKind("Rauchmelder"))
        assertNull(warningKind(null))
    }

    @Test
    fun areaNamesOfDistrictsAndStatesEachOnce() {
        val kreise = mapOf("06412" to "Frankfurt am Main", "06413" to "Offenbach am Main")
        val states = mapOf("07" to "Rheinland-Pfalz")
        assertEquals(
            listOf("Frankfurt am Main", "Rheinland-Pfalz", "Offenbach am Main"),
            areaNames(listOf("06412", "07", "064120000000", "06413", "99999"), kreise, states),
        )
    }

    @Test
    fun theBubbleStandsAboveTheTapCentredWithItsTailPointingDown() {
        val spot = placeWarningPopup(tipX = 200f, tipY = 500f, width = 300f, height = 200f, viewWidth = 400f, viewHeight = 800f, tail = 10f, margin = 8f, corner = 16f)
        assertFalse(spot.below)
        assertEquals(50f, spot.left)
        assertEquals(290f, spot.top)
        assertEquals(200f, spot.tailX)
    }

    @Test
    fun withoutRoomAboveItHangsBelow() {
        val spot = placeWarningPopup(tipX = 200f, tipY = 100f, width = 300f, height = 200f, viewWidth = 400f, viewHeight = 800f, tail = 10f, margin = 8f, corner = 16f)
        assertTrue(spot.below)
        assertEquals(110f, spot.top)
    }

    @Test
    fun atTheEdgeTheBubbleStaysInsideAndTheTailLeansToTheTap() {
        val spot = placeWarningPopup(tipX = 5f, tipY = 500f, width = 300f, height = 200f, viewWidth = 400f, viewHeight = 800f, tail = 10f, margin = 8f, corner = 16f)
        assertEquals(8f, spot.left)
        assertEquals(24f, spot.tailX)
        val right = placeWarningPopup(tipX = 398f, tipY = 500f, width = 300f, height = 200f, viewWidth = 400f, viewHeight = 800f, tail = 10f, margin = 8f, corner = 16f)
        assertEquals(92f, right.left)
        assertEquals(376f, right.tailX)
    }

    @Test
    fun theBubbleIsAtMostAsHighAsTheLargerRoom() {
        assertEquals(682f, warningPopupMaxHeight(tipY = 100f, viewHeight = 800f, tail = 10f, margin = 8f))
        assertEquals(582f, warningPopupMaxHeight(tipY = 600f, viewHeight = 800f, tail = 10f, margin = 8f))
    }

    @Test
    fun theTappedPointGivesBackThePlaceThatWasProjectedThere() {
        val camera = MapCamera().apply { width = 400f; height = 800f; fitPlaces(listOf(50.11 to 8.68)) }
        val at = camera.project(50.2, 8.9)
        val (lat, lon) = camera.placeAt(at)
        assertTrue(abs(lat - 50.2) < 1e-6, "latitude $lat")
        assertTrue(abs(lon - 8.9) < 1e-6, "longitude $lon")
        val centre = camera.placeAt(Offset(200f, 400f))
        assertTrue(abs(centre.first - 50.11) < 1e-6 && abs(centre.second - 8.68) < 1e-6)
    }
}
