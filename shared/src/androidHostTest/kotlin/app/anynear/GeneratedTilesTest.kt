package app.anynear

import app.anynear.location.MapPack
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Reads a folder of tiles as `scripts/maptiles.py pbf` wrote it (ADR 0121 §11), with the reader of the app. The tiles
 * are not part of the repository: the test runs with `MAP_TILES_DIR=<folder>` and does nothing without it.
 */
class GeneratedTilesTest {
    @Test
    fun everyGeneratedTileReadsAsItsName() {
        val dir = System.getenv("MAP_TILES_DIR")?.takeIf { it.isNotEmpty() }?.let(::File) ?: return
        val files = dir.listFiles { f -> f.isFile && Regex("(10|12)-\\d+-\\d+").matches(f.name) }.orEmpty()
        assertTrue(files.isNotEmpty(), "keine Kacheln in $dir")
        var objects = 0L
        var points = 0L
        val kinds = sortedSetOf<Int>()
        for (file in files) {
            val pack = assertNotNull(MapPack.parse(file.readBytes()), file.name)
            assertEquals(file.name, pack.key)
            assertTrue(pack.level == MapPack.REGION || pack.level == MapPack.NEAR, file.name)
            assertTrue(pack.size > 0, file.name)
            assertTrue(pack.stamp > 1_700_000_000, file.name)
            // What a tile holds lies in the tile: the generator cuts at its border (a hair of rounding allowed).
            val (_, x, y) = file.name.split('-').map { it.toInt() }
            val n = (1 shl pack.level).toFloat()
            val slack = 2e-6f
            assertTrue(pack.minX >= x / n - slack && pack.maxX <= (x + 1) / n + slack, "${file.name}: x ${pack.minX}..${pack.maxX}")
            assertTrue(pack.minY >= y / n - slack && pack.maxY <= (y + 1) / n + slack, "${file.name}: y ${pack.minY}..${pack.maxY}")
            for (i in 0 until pack.size) {
                val kind = pack.kinds[i].toInt()
                kinds += kind
                assertTrue(pack.shapes[i].isNotEmpty(), "${file.name}: Objekt $i ohne Punkte")
                assertTrue(pack.nameOf[i] <= pack.names.size, "${file.name}: Name von Objekt $i")
                if (MapPack.isArea(kind)) assertTrue(pack.shapes[i].size >= 8, "${file.name}: Fläche $i mit ${pack.shapes[i].size / 2} Punkten")
                points += pack.shapes[i].size / 2
            }
            objects += pack.size
        }
        val known = setOf(
            MapPack.MOTORWAY, MapPack.MAIN, MapPack.SIDE, MapPack.STREET, MapPack.PATH, MapPack.RAIL,
            MapPack.WATER, MapPack.STREAM, MapPack.WOOD, MapPack.GREEN, MapPack.PLACE,
        )
        assertTrue(known.containsAll(kinds), "unbekannte Arten: ${kinds - known}")
        println("GeneratedTilesTest: ${files.size} Kacheln, $objects Objekte, $points Punkte, Arten $kinds")
    }
}
