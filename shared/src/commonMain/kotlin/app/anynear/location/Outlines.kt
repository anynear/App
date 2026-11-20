package app.anynear.location

import app.anynear.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * The map of the app without a map service (ADR 0121 §7, §11): the borders of all municipalities and districts of
 * Germany, shipped with the app (`scripts/outlines.py`, source BKG VG250, dl-de/by-2-0). A border piece between two
 * neighbours is stored once ([arcs]). Coordinates are in the square of the Web Mercator projection: x and y from 0 to
 * 1, y growing to the south ([x], [y]).
 */
class Outlines(
    /** Border pieces: x, y, x, y, … */
    val arcs: Array<FloatArray>,
    /** Per piece: bit 0 border of a district, bit 1 outer border (coast, country). */
    val flags: ByteArray,
    /** Per piece: minX, minY, maxX, maxY. */
    val boxes: FloatArray,
    val towns: List<Town>,
) {
    /** A municipality: district key (5 digits), name and its rings as piece numbers + 1, negative = backwards. */
    class Town(val kreis: String, val name: String, val rings: Array<IntArray>, val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
        val centerX: Float get() = (minX + maxX) / 2
        val centerY: Float get() = (minY + maxY) / 2
    }

    /** District key → the municipalities in it. */
    val byKreis: Map<String, List<Town>> by lazy { towns.groupBy { it.kreis } }

    /** District key → minX, minY, maxX, maxY. */
    val kreisBoxes: Map<String, FloatArray> by lazy {
        byKreis.mapValues { (_, list) -> floatArrayOf(list.minOf { it.minX }, list.minOf { it.minY }, list.maxOf { it.maxX }, list.maxOf { it.maxY }) }
    }

    fun isKreisBorder(arc: Int): Boolean = flags[arc].toInt() and 1 != 0

    /** The border of the country or the coast. */
    fun isOuter(arc: Int): Boolean = flags[arc].toInt() and 2 != 0

    /** The points of ring [ring] in order, handed to [each] as x, y; every [stride]-th point of a piece and its ends. */
    inline fun ring(ring: IntArray, stride: Int = 1, each: (Float, Float) -> Unit) {
        for (ref in ring) {
            val a = arcs[if (ref > 0) ref - 1 else -ref - 1]
            val n = a.size / 2
            if (ref > 0) {
                var i = 0
                while (i < n - 1) {
                    each(a[2 * i], a[2 * i + 1])
                    i += stride
                }
            } else {
                var i = n - 1
                while (i > 0) {
                    each(a[2 * i], a[2 * i + 1])
                    i -= stride
                }
            }
        }
    }

    /** The municipality the point lies in; `null` outside Germany. */
    fun townAt(latitude: Double, longitude: Double): Town? {
        val px = x(longitude).toFloat()
        val py = y(latitude).toFloat()
        return towns.firstOrNull { t -> px >= t.minX && px <= t.maxX && py >= t.minY && py <= t.maxY && inside(t, px, py) }
    }

    private fun inside(t: Town, px: Float, py: Float): Boolean {
        var odd = false
        for (r in t.rings) {
            var first = true
            var fx = 0f
            var fy = 0f
            var lx = 0f
            var ly = 0f
            ring(r) { x, y ->
                if (first) {
                    fx = x; fy = y; first = false
                } else if ((ly > py) != (y > py) && px < (x - lx) * (py - ly) / (y - ly) + lx) {
                    odd = !odd
                }
                lx = x; ly = y
            }
            if (!first && (ly > py) != (fy > py) && px < (fx - lx) * (py - ly) / (fy - ly) + lx) odd = !odd
        }
        return odd
    }

    companion object {
        /** Germany as a box, for the first view: minX, minY, maxX, maxY. */
        val GERMANY = floatArrayOf(x(5.8).toFloat(), y(55.1).toFloat(), x(15.1).toFloat(), y(47.2).toFloat())

        fun x(longitude: Double): Double = (longitude + 180.0) / 360.0

        fun y(latitude: Double): Double {
            val lat = latitude.coerceIn(-85.0, 85.0) * PI / 180
            return (1 - ln(tan(lat) + 1 / kotlin.math.cos(lat)) / PI) / 2
        }

        fun longitude(x: Double): Double = x * 360.0 - 180.0

        fun latitude(y: Double): Double = (2 * atan(exp(PI * (1 - 2 * y))) - PI / 2) * 180 / PI

        /** Reads the file written by `scripts/outlines.py`; `null` if it is not one. */
        fun parse(bytes: ByteArray): Outlines? {
            if (bytes.size < 4 || bytes[0] != 'A'.code.toByte() || bytes[1] != 'O'.code.toByte() || bytes[2] != 1.toByte()) return null
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
            return runCatching {
                val arcCount = varint().toInt()
                val flags = ByteArray(arcCount)
                val boxes = FloatArray(arcCount * 4)
                val arcs = Array(arcCount) { i ->
                    flags[i] = varint().toByte()
                    val n = varint().toInt()
                    val a = FloatArray(n * 2)
                    var lat = 0L
                    var lon = 0L
                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE
                    var maxY = -Float.MAX_VALUE
                    for (k in 0 until n) {
                        lat += zigzag()
                        lon += zigzag()
                        val px = x(lon / 1e5).toFloat()
                        val py = y(lat / 1e5).toFloat()
                        a[2 * k] = px
                        a[2 * k + 1] = py
                        minX = min(minX, px); maxX = max(maxX, px); minY = min(minY, py); maxY = max(maxY, py)
                    }
                    boxes[4 * i] = minX; boxes[4 * i + 1] = minY; boxes[4 * i + 2] = maxX; boxes[4 * i + 3] = maxY
                    a
                }
                val towns = List(varint().toInt()) {
                    val kreis = bytes.decodeToString(pos, pos + 5)
                    pos += 5
                    val len = varint().toInt()
                    val name = bytes.decodeToString(pos, pos + len)
                    pos += len
                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE
                    var maxY = -Float.MAX_VALUE
                    val rings = Array(varint().toInt()) {
                        IntArray(varint().toInt()) {
                            val ref = zigzag().toInt()
                            val arc = if (ref > 0) ref - 1 else -ref - 1
                            minX = min(minX, boxes[4 * arc]); minY = min(minY, boxes[4 * arc + 1])
                            maxX = max(maxX, boxes[4 * arc + 2]); maxY = max(maxY, boxes[4 * arc + 3])
                            ref
                        }
                    }
                    Town(kreis, name, rings, minX, minY, maxX, maxY)
                }
                Outlines(arcs, flags, boxes, towns)
            }.getOrNull()
        }

        private val cacheLock = kotlinx.coroutines.sync.Mutex()
        private var cached: Outlines? = null

        /** The outlines, read once per process: the map and the small maps in chats share them. */
        suspend fun shared(): Outlines? {
            cached?.let { return it }
            cacheLock.lock()
            try {
                return cached ?: load().also { cached = it }
            } finally {
                cacheLock.unlock()
            }
        }

        /** The outlines from the app's resources; `null` if they cannot be read. */
        @OptIn(ExperimentalResourceApi::class)
        suspend fun load(): Outlines? = withContext(Dispatchers.Default) {
            runCatching { parse(Res.readBytes("files/regions/outlines.bin")) }.getOrNull()
        }
    }
}
