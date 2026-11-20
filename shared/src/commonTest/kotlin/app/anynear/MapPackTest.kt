package app.anynear

import app.anynear.attachments.BinaryFiles
import app.anynear.location.MapOffer
import app.anynear.location.MapPack
import app.anynear.location.MapTileSource
import app.anynear.location.MapTiles
import app.anynear.location.Outlines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The tiles of the detailed map (ADR 0121 §11): the file of `scripts/maptiles.py`, kept on the phone, fetched from the network. */
class MapPackTest {
    private class Files : BinaryFiles {
        val map = mutableMapOf<String, ByteArray>()
        override fun read(name: String): ByteArray? = map[name]
        override fun write(name: String, content: ByteArray) { map[name] = content }
        override fun delete(name: String) { map.remove(name) }
        override fun names(): List<String> = map.keys.toList()
    }

    private fun varint(v: Long, out: MutableList<Byte>) {
        var n = v
        while (n >= 0x80) { out += ((n and 0x7F) or 0x80).toByte(); n = n ushr 7 }
        out += n.toByte()
    }

    private fun zigzag(v: Long, out: MutableList<Byte>) = varint((v shl 1) xor (v shr 63), out)

    private fun text(s: String, out: MutableList<Byte>) { val raw = s.encodeToByteArray(); varint(raw.size.toLong(), out); raw.forEach { out += it } }

    /** A tile as the script writes it: one street „Zeil“ with two points and the place „Innenstadt“. */
    private fun tile(key: String): ByteArray {
        val out = mutableListOf<Byte>()
        "AM".encodeToByteArray().forEach { out += it }
        out += 2
        text(key, out)
        varint(1_800_000_000, out)
        for (v in longArrayOf(5011000, 868000, 5012000, 869000)) zigzag(v, out)
        varint(2, out)
        text("Zeil", out); text("Innenstadt", out)
        varint(2, out)
        varint(MapPack.STREET.toLong(), out); varint(1, out); varint(2, out)
        zigzag(5011400, out); zigzag(868200, out); zigzag(10, out); zigzag(300, out)
        varint(MapPack.PLACE.toLong(), out); varint(2, out); varint(1, out)
        zigzag(5011500, out); zigzag(868300, out)
        return out.toByteArray()
    }

    private val near = MapPack.tileAt(MapPack.NEAR, 50.1106, 8.6821)
    private val region = MapPack.tileAt(MapPack.REGION, 50.1106, 8.6821)

    @Test
    fun aTileReadsAsWritten() {
        val p = assertNotNull(MapPack.parse(tile(near)))
        assertEquals(near, p.key)
        assertEquals(MapPack.NEAR, p.level)
        assertEquals(2, p.size)
        assertEquals("Zeil", p.name(0))
        assertEquals("Innenstadt", p.name(1))
        assertEquals(50.11410, Outlines.latitude(p.shapes[0][3].toDouble()), 0.0005)
        assertEquals(8.68500, Outlines.longitude(p.shapes[0][2].toDouble()), 0.0005)
        assertNull(MapPack.parse(byteArrayOf(1, 2, 3)))
        assertNull(MapPack.parse(tile(near).copyOf(20)))
        assertEquals(0, assertNotNull(MapPack.parse(tile(MapPack.BASE))).level)
        // The maps of the app carry both languages where a name differs.
        assertEquals("München", MapPack.localName("München\tMunich", german = true))
        assertEquals("Munich", MapPack.localName("München\tMunich", german = false))
        assertEquals("Paris", MapPack.localName("Paris", german = false))
    }

    @Test
    fun tilesAreAddressedLikeOnWebMaps() {
        // Frankfurt, Römer.
        assertEquals("12-2146-1387", near)
        assertEquals("10-536-346", region)
        assertEquals(region, MapPack.regionOf(near))
        assertEquals(region, MapPack.regionOf(region))
        assertNull(MapPack.regionOf("base"))
        val x = Outlines.x(8.6821)
        val y = Outlines.y(50.1106)
        assertEquals(listOf(near), MapPack.tilesIn(MapPack.NEAR, x, y, x, y, limit = 4))
        // A view too wide for the level asks for nothing rather than for hundreds of tiles.
        assertTrue(MapPack.tilesIn(MapPack.NEAR, x - 0.01, y - 0.01, x + 0.01, y + 0.01, limit = 24).isEmpty())
        assertEquals(4, MapPack.tilesIn(MapPack.REGION, x - 0.0006, y - 0.0006, x + 0.0006, y + 0.0006, limit = 12).size)
    }

    /** The same bytes the relay writes (`internal/maps/format_test.go`, `relay publish-maps`). */
    @Test
    fun theListsOfTheRelayRead() {
        val hex = "415802020a31302d3533362d333437c08df6d7068102036162630c31322d323134362d313338398094ebdc03a08d06027879"
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val offers = listOf(MapOffer("10-536-347", 1_795_000_000, 257, "abc"), MapOffer("12-2146-1389", 1_000_000_000, 100_000, "xy"))
        assertEquals(offers, MapTiles.parseOffers(bytes))
        assertTrue(bytes.contentEquals(MapTiles.encodeOffers(offers)))
        assertEquals("10-536-347", MapPack.regionOf("12-2146-1389"))
    }

    /** The map is set up with all four values or with none; a half configuration would fetch lists and no tiles. */
    @Test
    fun theMapSpacesAreSetUpTogether() {
        val values = listOf(AppConfig.MAP_SPACE, AppConfig.MAP_GRANT, AppConfig.MAP_DATA_SPACE, AppConfig.MAP_DATA_GRANT)
        assertTrue(values.all { it.isEmpty() } || values.none { it.isEmpty() }, "MAP_SPACE, MAP_GRANT, MAP_DATA_SPACE, MAP_DATA_GRANT")
        if (values.none { it.isEmpty() }) {
            assertTrue(AppConfig.MAP_SPACE != AppConfig.MAP_DATA_SPACE)
            app.anynear.andp.Key.parse(AppConfig.MAP_SPACE)
            app.anynear.andp.Key.parse(AppConfig.MAP_DATA_SPACE)
            app.anynear.andp.Grant.parse(AppConfig.MAP_GRANT)
            app.anynear.andp.Grant.parse(AppConfig.MAP_DATA_GRANT)
        }
    }

    @Test
    fun offersGoThereAndBack() {
        val offers = listOf(MapOffer(near, 1_800_000_000, 64_240, "abcDEF_-123"), MapOffer(region, 5, 7, "x"))
        assertEquals(offers, MapTiles.parseOffers(MapTiles.encodeOffers(offers)))
        assertTrue(MapTiles.parseOffers(byteArrayOf(9, 9, 9, 9)).isEmpty())
    }

    @Test
    fun aTileComesFromThePhoneFirstAndOtherwiseFromTheNetworkAndIsKept() = runBlocking {
        val fetched = mutableListOf<String>()
        val source = object : MapTileSource {
            // The root names the list of the region; that list names the region tile and its near tiles.
            override suspend fun roots() = listOf(MapTiles.encodeOffers(listOf(MapOffer(region, 1, 1, "list-1"))))
            override suspend fun fetch(objectId: String): ByteArray? {
                fetched += objectId
                return when (objectId) {
                    "list-1" -> MapTiles.encodeOffers(listOf(MapOffer(region, 1, 1, "tile-r"), MapOffer(near, 1, 1, "tile-n"), MapOffer("12-2147-1387", 1, 1, "tile-wrong")))
                    "tile-r" -> tile(region)
                    "tile-n" -> tile(near)
                    // An object that is another tile than the list says: not taken.
                    "tile-wrong" -> tile(near)
                    else -> null
                }
            }
        }
        val files = Files()
        val tiles = MapTiles(CoroutineScope(Dispatchers.Default), files, source)
        suspend fun settle(want: Set<String>) { repeat(150) { if (tiles.loaded.value.keys == want) return; delay(20) } }
        tiles.want(listOf(region, near), 0)
        settle(setOf(region, near))
        assertEquals(setOf(region, near), tiles.loaded.value.keys)
        assertEquals(setOf(region, near), tiles.stored())
        // The list of the region was fetched once for both tiles.
        assertEquals(listOf("list-1", "tile-r", "tile-n"), fetched)
        tiles.want(listOf("12-2147-1387", "12-9-9"), 0)
        delay(400)
        assertEquals(setOf(region, near), tiles.loaded.value.keys)

        // A new start without a network: the tiles are on the phone.
        val offline = MapTiles(CoroutineScope(Dispatchers.Default), files, source = null)
        offline.want(listOf(near, "12-2147-1387"), 0)
        repeat(150) { if (offline.loaded.value.isNotEmpty()) return@repeat; delay(20) }
        delay(100)
        assertEquals(setOf(near), offline.loaded.value.keys)
    }
}
