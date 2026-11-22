package app.anynear.location

import app.anynear.attachments.BinaryFiles
import app.anynear.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A piece of the detailed map (ADR 0121 §11), written by `scripts/maptiles.py` from OpenStreetMap: the light map of
 * the whole country that ships with the app ([key] `base`), or one tile to fetch ([key] `<z>-<x>-<y>`, tiles as on
 * web maps). Coordinates are Mercator units like in [Outlines]. The objects are in the order they are drawn in.
 */
class MapPack(
    val key: String,
    /** State of the data, seconds since 1970. */
    val stamp: Long,
    val minX: Float, val minY: Float, val maxX: Float, val maxY: Float,
    val names: List<String>,
    val kinds: ByteArray,
    /** Per object: index into [names] + 1, 0 = none. */
    val nameOf: IntArray,
    /** Per object: x, y, x, y, … */
    val shapes: Array<FloatArray>,
    /** Per object: minX, minY, maxX, maxY. */
    val boxes: FloatArray,
) {
    val size: Int get() = kinds.size

    /** The level of a tile (10 region, 12 near); 0 for the map of the whole country. */
    val level: Int = key.substringBefore('-').toIntOrNull() ?: 0

    fun name(i: Int): String? = nameOf[i].takeIf { it > 0 }?.let { names.getOrNull(it - 1) }

    companion object {
        const val MOTORWAY = 1
        const val MAIN = 2
        const val SIDE = 3
        const val STREET = 4
        const val PATH = 5
        const val RAIL = 6
        const val WATER = 10
        const val STREAM = 11
        const val WOOD = 12
        const val GREEN = 13
        const val PLACE = 20

        /** A city, only in the maps that ship with the app. */
        const val CITY = 21

        /** A country: one point that carries its name, only in the map of the world. */
        const val COUNTRY = 22

        /** The land of a country, only in the map of the world. */
        const val LAND = 30

        /** Tiles of about 25 km: main roads, rail, rivers, large water and woods, places. */
        const val REGION = 10

        /** Tiles of about 6 km: every road and path, streams, small areas, quarters. */
        const val NEAR = 12

        const val BASE = "base"

        fun isArea(kind: Int): Boolean = kind == WATER || kind == WOOD || kind == GREEN || kind == LAND

        fun tileKey(level: Int, x: Int, y: Int): String = "$level-$x-$y"

        /** The tile of [level] the place lies in. */
        fun tileAt(level: Int, latitude: Double, longitude: Double): String {
            val n = 1 shl level
            return tileKey(level, floor(Outlines.x(longitude) * n).toInt(), floor(Outlines.y(latitude) * n).toInt())
        }

        /** The tiles of [level] that cover the box (Mercator units), at most [limit]; none if it would be more. */
        fun tilesIn(level: Int, minX: Double, minY: Double, maxX: Double, maxY: Double, limit: Int): List<String> {
            val n = 1 shl level
            val x0 = floor(minX * n).toInt().coerceIn(0, n - 1)
            val x1 = floor(maxX * n).toInt().coerceIn(0, n - 1)
            val y0 = floor(minY * n).toInt().coerceIn(0, n - 1)
            val y1 = floor(maxY * n).toInt().coerceIn(0, n - 1)
            if ((x1 - x0 + 1).toLong() * (y1 - y0 + 1) > limit) return emptyList()
            return buildList { for (x in x0..x1) for (y in y0..y1) add(tileKey(level, x, y)) }
        }

        /** The region tile a near tile lies in: its list names the near tiles. */
        fun regionOf(key: String): String? {
            val parts = key.split('-').mapNotNull { it.toIntOrNull() }
            if (parts.size != 3) return null
            val shift = parts[0] - REGION
            return if (shift < 0) null else tileKey(REGION, parts[1] shr shift, parts[2] shr shift)
        }

        /** Reads a file of `scripts/maptiles.py`; `null` if the bytes are not one. */
        fun parse(bytes: ByteArray): MapPack? {
            if (bytes.size < 6 || bytes[0] != 'A'.code.toByte() || bytes[1] != 'M'.code.toByte() || bytes[2] != 2.toByte()) return null
            var pos = 3
            fun varint(): Long {
                var shift = 0
                var out = 0L
                while (true) {
                    val b = bytes[pos++].toInt() and 0xFF
                    out = out or ((b and 0x7F).toLong() shl shift)
                    if (b < 0x80) return out
                    shift += 7
                }
            }
            fun zigzag(): Long = varint().let { (it ushr 1) xor -(it and 1) }
            fun text(): String {
                val len = varint().toInt()
                return bytes.decodeToString(pos, pos + len).also { pos += len }
            }
            return runCatching {
                val key = text()
                val stamp = varint()
                val south = zigzag() / 1e5
                val west = zigzag() / 1e5
                val north = zigzag() / 1e5
                val east = zigzag() / 1e5
                val names = List(varint().toInt()) { text() }
                val n = varint().toInt()
                val kinds = ByteArray(n)
                val nameOf = IntArray(n)
                val boxes = FloatArray(n * 4)
                val shapes = Array(n) { i ->
                    kinds[i] = varint().toByte()
                    nameOf[i] = varint().toInt()
                    val count = varint().toInt()
                    val a = FloatArray(count * 2)
                    var lat = 0L
                    var lon = 0L
                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE
                    var maxY = -Float.MAX_VALUE
                    for (k in 0 until count) {
                        lat += zigzag()
                        lon += zigzag()
                        val px = Outlines.x(lon / 1e5).toFloat()
                        val py = Outlines.y(lat / 1e5).toFloat()
                        a[2 * k] = px
                        a[2 * k + 1] = py
                        minX = min(minX, px); maxX = max(maxX, px); minY = min(minY, py); maxY = max(maxY, py)
                    }
                    boxes[4 * i] = minX; boxes[4 * i + 1] = minY; boxes[4 * i + 2] = maxX; boxes[4 * i + 3] = maxY
                    a
                }
                MapPack(
                    key, stamp, Outlines.x(west).toFloat(), Outlines.y(north).toFloat(), Outlines.x(east).toFloat(), Outlines.y(south).toFloat(),
                    names, kinds, nameOf, shapes, boxes,
                )
            }.getOrNull()
        }

        const val WORLD = "world"

        /**
         * A name as the person reads it: the maps that ship with the app carry „Deutsch<Tab>English“ where the two differ.
         */
        fun localName(name: String, german: Boolean): String =
            if ('\t' !in name) name else if (german) name.substringBefore('\t') else name.substringAfter('\t')

        private val bundledLock = Mutex()
        private var bundled: List<MapPack>? = null

        /**
         * The maps that ship with the app, read once: the coarse world (countries, big cities), Europe (trunk roads,
         * rivers, lakes, cities) and Germany (motorways, long rivers, cities and towns). Missing ones are left out.
         */
        @OptIn(ExperimentalResourceApi::class)
        suspend fun bundled(): List<MapPack> {
            bundled?.let { return it }
            return bundledLock.withLock {
                bundled ?: withContext(Dispatchers.Default) {
                    listOf("world", "europe", "base").mapNotNull { name -> runCatching { parse(Res.readBytes("files/regions/$name.amp")) }.getOrNull() }
                }.also { bundled = it }
            }
        }
    }
}

/** Something the anynear network offers under a name: a tile, or the list of a region. */
data class MapOffer(val key: String, val stamp: Long, val bytes: Long, val objectId: String)

/** Where tiles come from; replaceable in tests. */
interface MapTileSource {
    /** The root lists as published, unparsed: region key → object of the region's list. `null` without an answer. */
    suspend fun roots(): List<ByteArray>?

    /** A region's list or a tile by its object ID. */
    suspend fun fetch(objectId: String): ByteArray?
}

/**
 * The tiles of the detailed map on this phone (ADR 0121 §11): offline first, otherwise from the anynear network. A
 * tile is fetched when the map shows its area closely, and kept, so that the area works without internet from then
 * on. Every tile is an object of its own; a small list per region names its tiles, and one root list names the
 * regions. No map service of others learns which area someone looks at; a relay sees that some device fetches a tile.
 */
class MapTiles(
    private val scope: CoroutineScope,
    private val files: BinaryFiles,
    private val source: MapTileSource?,
) {
    private val _loaded = MutableStateFlow<Map<String, MapPack>>(emptyMap())

    /** Tiles read into memory, by key; the ones asked for last. */
    val loaded: StateFlow<Map<String, MapPack>> = _loaded.asStateFlow()

    private val _loading = MutableStateFlow(false)

    /** `true` while tiles the map asked for are being read or fetched: the map shows that it is still filling. */
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val lock = Mutex()
    private val state = Mutex()
    private val busy = mutableSetOf<String>()
    private val missing = mutableMapOf<String, Long>()
    private val used = ArrayList<String>()
    /** Tiles from the phone already compared with the network since the start. */
    private val checked = mutableSetOf<String>()
    private var roots: Map<String, MapOffer>? = null
    private var rootsAt = 0L
    private val regions = mutableMapOf<String, Map<String, MapOffer>>()

    /** Tile keys kept on this phone. */
    fun stored(): Set<String> = files.names().filter { it.startsWith(TILE) }.map { it.removePrefix(TILE) }.toSet()

    /**
     * Makes the tiles [keys] available: from the phone, otherwise from the network. Called with what the map shows;
     * does nothing for tiles that are there, being fetched, or were not to be had a moment ago.
     */
    fun want(keys: List<String>, now: Long) {
        if (keys.isEmpty()) return
        scope.launch {
            // The bookkeeping is touched from several coroutines, so only under [state].
            val todo = state.withLock {
                used.removeAll(keys.toSet())
                used.addAll(keys)
                keys.filter { key -> key !in _loaded.value && key !in busy && missing[key].let { it == null || it < now - RETRY_MILLIS } }.also {
                    busy += it
                    _loading.value = busy.isNotEmpty()
                }
            }
            for (key in todo) {
                val kept = runCatching { withContext(Dispatchers.Default) { files.read(TILE + key)?.let(MapPack::parse) } }.getOrNull()
                val pack = kept ?: runCatching { download(key, now) }.getOrNull()
                state.withLock {
                    busy -= key
                    _loading.value = busy.isNotEmpty()
                    if (pack != null) put(key, pack) else missing[key] = now
                }
            }
            // Tiles from the phone are shown at once and then compared with what the network offers: a tile of an
            // earlier map (22.11.2026: place names without their rank) is replaced by the new one.
            for (key in todo) {
                if (state.withLock { key !in _loaded.value || !checked.add(key) }) continue
                val newer = runCatching { download(key, now, kept = true) }.getOrNull() ?: continue
                state.withLock { if (key in _loaded.value) put(key, newer) }
            }
        }
    }

    private fun put(key: String, pack: MapPack) {
        var all = _loaded.value + (key to pack)
        // The tiles asked for last stay in memory; the others stay on the phone.
        while (all.size > MAX_IN_MEMORY) {
            val oldest = used.firstOrNull { it in all && it != key } ?: break
            used.remove(oldest)
            all = all - oldest
        }
        _loaded.value = all
    }

    /**
     * Fetches the tile [key] as the network offers it now. With [kept] only when that is another object than the
     * one on the phone, and not again and again without a network.
     */
    private suspend fun download(key: String, now: Long, kept: Boolean = false): MapPack? = lock.withLock {
        val from = source ?: return@withLock null
        val region = MapPack.regionOf(key) ?: return@withLock null
        if (kept && roots == null && rootsAt != 0L && now - rootsAt < RETRY_MILLIS) return@withLock null
        if (roots == null || now - rootsAt > ROOTS_FRESH_MILLIS) {
            roots = from.roots()?.flatMap { parseOffers(it) }?.groupBy { it.key }?.mapValues { (_, list) -> list.maxBy { it.stamp } } ?: roots
            rootsAt = now
            if (roots != null) regions.clear()
        }
        val root = roots?.get(region) ?: return@withLock null
        val list = regions[region] ?: run {
            val bytes = files.read(LIST + region)?.takeIf { parseOffers(it).isNotEmpty() && files.read(LIST_ID + region)?.decodeToString() == root.objectId }
                ?: from.fetch(root.objectId)?.also {
                    files.write(LIST + region, it)
                    files.write(LIST_ID + region, root.objectId.encodeToByteArray())
                } ?: return@withLock null
            parseOffers(bytes).associateBy { it.key }.also { regions[region] = it }
        }
        val offer = list[key] ?: return@withLock null
        if (kept && files.read(TILE_ID + key)?.decodeToString() == offer.objectId) return@withLock null
        val bytes = from.fetch(offer.objectId) ?: return@withLock null
        val pack = withContext(Dispatchers.Default) { MapPack.parse(bytes) }?.takeIf { it.key == key } ?: return@withLock null
        files.write(TILE + key, bytes)
        files.write(TILE_ID + key, offer.objectId.encodeToByteArray())
        pack
    }

    companion object {
        const val DIR_NAME = "maps"
        private const val TILE = "t-"
        /** The object a stored tile came from; tiles of before 22.11.2026 have none and are fetched once more. */
        private const val TILE_ID = "o-"
        private const val LIST = "l-"
        private const val LIST_ID = "i-"
        private const val MAX_IN_MEMORY = 48
        private const val ROOTS_FRESH_MILLIS = 6 * 3_600_000L
        private const val RETRY_MILLIS = 10 * 60_000L

        /**
         * A list of offers: `"AX" 2`, the number of offers, and per offer the key (length, text) and as varints the
         * state of the data, the size, the length of the object ID and the ID as text. Anything else is an empty list.
         */
        fun parseOffers(bytes: ByteArray): List<MapOffer> {
            if (bytes.size < 4 || bytes[0] != 'A'.code.toByte() || bytes[1] != 'X'.code.toByte() || bytes[2] != 2.toByte()) return emptyList()
            var pos = 3
            fun varint(): Long {
                var shift = 0
                var out = 0L
                while (true) {
                    val b = bytes[pos++].toInt() and 0xFF
                    out = out or ((b and 0x7F).toLong() shl shift)
                    if (b < 0x80) return out
                    shift += 7
                }
            }
            fun text(): String {
                val len = varint().toInt()
                return bytes.decodeToString(pos, pos + len).also { pos += len }
            }
            return runCatching {
                List(varint().toInt()) {
                    val key = text()
                    val stamp = varint()
                    val size = varint()
                    MapOffer(key, stamp, size, text())
                }
            }.getOrDefault(emptyList())
        }

        fun encodeOffers(offers: List<MapOffer>): ByteArray {
            val out = ArrayList<Byte>()
            fun varint(v: Long) {
                var n = v
                while (n >= 0x80) {
                    out += ((n and 0x7F) or 0x80).toByte()
                    n = n ushr 7
                }
                out += n.toByte()
            }
            fun text(s: String) {
                val raw = s.encodeToByteArray()
                varint(raw.size.toLong())
                raw.forEach { out += it }
            }
            "AX".encodeToByteArray().forEach { out += it }
            out += 2
            varint(offers.size.toLong())
            for (o in offers) {
                text(o.key)
                varint(o.stamp)
                varint(o.bytes)
                text(o.objectId)
            }
            return out.toByteArray()
        }
    }
}
