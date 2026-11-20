package app.anynear

import app.anynear.location.Outlines
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The map data shipped with the app (`scripts/outlines.py`, ADR 0121 §7, §11): municipalities with their borders. */
class OutlinesTest {
    private val outlines = assertNotNull(Outlines.parse(File("src/commonMain/composeResources/files/regions/outlines.bin").readBytes()))

    @Test
    fun allMunicipalitiesAreThereAndEveryRingRefersToAPiece() {
        assertTrue(outlines.towns.size > 10_000, "Gemeinden: ${outlines.towns.size}")
        // 400 districts in the table of the regions (end of 2025), and Hanau, a district of its own since 2026.
        assertEquals(401, outlines.byKreis.size)
        for (t in outlines.towns) for (ring in t.rings) for (ref in ring) {
            assertTrue(ref != 0 && (if (ref > 0) ref - 1 else -ref - 1) < outlines.arcs.size)
        }
        assertTrue(outlines.arcs.indices.any(outlines::isKreisBorder))
    }

    @Test
    fun aPlaceLiesInItsMunicipality() {
        assertEquals("Frankfurt am Main", outlines.townAt(50.1106, 8.6821)?.name) // Römer
        assertEquals("06412", outlines.townAt(50.1106, 8.6821)?.kreis)
        assertEquals("München", outlines.townAt(48.1374, 11.5755)?.name)
        assertEquals("Offenbach am Main", outlines.townAt(50.1055, 8.7610)?.name)
        // Outside Germany: Paris, and the North Sea.
        assertNull(outlines.townAt(48.8566, 2.3522))
        assertNull(outlines.townAt(54.6, 6.5))
    }

    @Test
    fun theProjectionGoesThereAndBack() {
        assertEquals(50.1106, Outlines.latitude(Outlines.y(50.1106)), 1e-9)
        assertEquals(8.6821, Outlines.longitude(Outlines.x(8.6821)), 1e-9)
        assertNull(Outlines.parse(byteArrayOf(1, 2, 3, 4)))
    }
}
