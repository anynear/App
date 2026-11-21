package app.anynear.ui

import app.anynear.location.MapPack
import kotlin.math.floor
import kotlin.math.log10

/** What kind of place a name on the map stands for, from the largest to the smallest. */
internal enum class PlaceType { COUNTRY, CITY, TOWN, VILLAGE, HAMLET, SUBURB, QUARTER }

/**
 * A place name on the map and its rank: the type and the size, `floor(2 · log10(inhabitants))`, so 10 is a city from
 * 100,000 people, 8 a town from 10,000, 6 a village from 1,000 (`scripts/maptiles.py` writes it the same way).
 */
internal data class PlaceRank(val type: PlaceType, val size: Int)

/** A name that wants onto the map: where its box would be (pixels), how much room it keeps free, and how it ranks. */
internal data class LabelCandidate(
    val text: String,
    val priority: Int,
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    /** Room kept free around the box, in pixels: no other name comes closer. */
    val room: Float = 0f,
    /** Shown only once on the map (street names: each street once, not once per piece). */
    val once: Boolean = false,
    /** Among the same [priority]: the higher wins. Set from the order in the file, which is by inhabitants. */
    val tie: Int = 0,
    val payload: Any? = null,
)

/**
 * Which names the map shows at which zoom, and which one wins when two would overlap (20.11.2026, „wie bei Google
 * Maps“): far out only the large cities, closer the smaller cities and towns, then villages and the quarters of a city,
 * last the names of the streets. A larger place always pushes a smaller one aside, so a city is named by itself and
 * not by one of its quarters („Wiesbaden“, not „Mitte“). Zoom is measured in metres per dp, so a phone and a tablet
 * show the same names for the same piece of land.
 */
internal object MapLabels {
    /** Kinds from here on are places with their rank: `RANKED + 16 · type + size` (types from [PlaceType.CITY]). */
    const val RANKED = 32
    private const val SIZES = 16

    /** The size of a place with [inhabitants]; `null` if unknown. */
    fun sizeOf(inhabitants: Long): Int? = if (inhabitants <= 0) null else floor(2 * log10(inhabitants.toDouble())).toInt().coerceIn(0, SIZES - 1)

    /** The size of a place whose inhabitants are not known. */
    fun defaultSize(type: PlaceType): Int = when (type) {
        PlaceType.COUNTRY -> 15
        PlaceType.CITY -> 10
        PlaceType.TOWN -> 8
        PlaceType.VILLAGE -> 6
        PlaceType.HAMLET -> 4
        PlaceType.SUBURB -> 8
        PlaceType.QUARTER -> 6
    }

    /** The kind a place is written with (for tests and the generator's twin in `scripts/maptiles.py`). */
    fun kindOf(rank: PlaceRank): Int = RANKED + SIZES * (rank.type.ordinal - 1) + rank.size.coerceIn(0, SIZES - 1)

    /**
     * The place an object of a map stands for; `null` if it is none. Besides the ranked kinds this reads the kinds
     * of earlier tiles and maps (20 place, 21 city, 22 country), which carry no size: tiles already on a phone stay.
     */
    fun placeOf(kind: Int, level: Int, key: String): PlaceRank? {
        if (kind >= RANKED && kind < RANKED + SIZES * (PlaceType.entries.size - 1)) {
            val type = PlaceType.entries[(kind - RANKED) / SIZES + 1]
            return PlaceRank(type, (kind - RANKED) % SIZES)
        }
        return when (kind) {
            MapPack.COUNTRY -> PlaceRank(PlaceType.COUNTRY, 15)
            // The world shows cities of two million and more, Europe and Germany cities of half a million or capitals.
            MapPack.CITY -> PlaceRank(PlaceType.CITY, if (key == MapPack.WORLD) 12 else 11)
            MapPack.PLACE -> when (level) {
                0 -> PlaceRank(PlaceType.TOWN, 9)
                MapPack.REGION -> PlaceRank(PlaceType.VILLAGE, 6)
                else -> PlaceRank(PlaceType.QUARTER, 5)
            }
            else -> null
        }
    }

    /** Countries are named only from far out: closer, their cities take over. In metres per dp. */
    const val COUNTRIES_ABOVE = 3_500.0

    /** A place is named while a dp is fewer metres than this. */
    fun placeBelow(rank: PlaceRank): Double = when (rank.type) {
        PlaceType.COUNTRY -> Double.MAX_VALUE
        PlaceType.SUBURB -> when {
            rank.size >= 10 -> 50.0
            rank.size >= 9 -> 38.0
            else -> 26.0
        }
        PlaceType.QUARTER -> if (rank.size >= 6) 11.0 else 6.0
        else -> {
            val bySize = when {
                // All of Germany on a phone is about 2,000 m per dp: the cities of half a million and more.
                rank.size >= 14 -> 14_000.0
                rank.size == 13 -> 8_000.0
                rank.size == 12 -> 4_500.0
                rank.size == 11 -> 2_600.0
                rank.size == 10 -> 900.0
                rank.size == 9 -> 380.0
                rank.size == 8 -> 180.0
                rank.size == 7 -> 85.0
                rank.size == 6 -> 45.0
                else -> 25.0
            }
            if (rank.type == PlaceType.HAMLET) minOf(bySize, 14.0) else bySize
        }
    }

    /** Whether the place is named at [metresPerDp]. */
    fun placeShown(rank: PlaceRank, metresPerDp: Double): Boolean =
        if (rank.type == PlaceType.COUNTRY) metresPerDp > COUNTRIES_ABOVE else metresPerDp < placeBelow(rank)

    /**
     * Who wins the room: countries, then places by size, among the same size the city before the town, the town
     * before the village and the quarter last. Every place before every street.
     */
    fun placePriority(rank: PlaceRank): Int = when (rank.type) {
        PlaceType.COUNTRY -> 10_000
        else -> 1_000 + rank.size * 10 + when (rank.type) {
            PlaceType.CITY -> 6
            PlaceType.TOWN -> 5
            PlaceType.VILLAGE -> 4
            PlaceType.HAMLET -> 3
            PlaceType.SUBURB -> 2
            else -> 0
        }
    }

    /** The name of a road or water of [kind] shows while a dp is fewer metres than this; 0 for never. */
    fun lineNameBelow(kind: Int): Double = when (kind) {
        MapPack.MOTORWAY, MapPack.MAIN -> 12.0
        MapPack.SIDE -> 7.0
        MapPack.STREET -> 3.5
        MapPack.STREAM, MapPack.WATER -> 15.0
        else -> 0.0
    }

    fun lineNameShown(kind: Int, metresPerDp: Double): Boolean = metresPerDp < lineNameBelow(kind)

    /** Larger roads before smaller ones; rivers between main and side roads. */
    fun linePriority(kind: Int): Int = when (kind) {
        MapPack.MOTORWAY -> 500
        MapPack.MAIN -> 400
        MapPack.STREAM, MapPack.WATER -> 350
        MapPack.SIDE -> 300
        else -> 200
    }

    /**
     * The names that get onto a picture of [width] × [height]: from the highest rank down, each where it lies fully
     * on the picture and keeps the room of all names placed before it. Same input, same result: ties go by [LabelCandidate.tie],
     * then by name and place, never by the order the tiles came in.
     */
    fun choose(candidates: List<LabelCandidate>, width: Float, height: Float): List<LabelCandidate> {
        val sorted = candidates.sortedWith(
            compareByDescending<LabelCandidate> { it.priority }.thenByDescending { it.tie }.thenBy { it.text }.thenBy { it.left }.thenBy { it.top },
        )
        val placed = ArrayList<LabelCandidate>()
        val named = HashSet<String>()
        for (c in sorted) {
            if (c.left < 0 || c.top < 0 || c.right > width || c.bottom > height) continue
            if (c.once && c.text in named) continue
            val clash = placed.any { p ->
                val room = maxOf(c.room, p.room)
                c.left - room < p.right && c.right + room > p.left && c.top - room < p.bottom && c.bottom + room > p.top
            }
            if (clash) continue
            placed += c
            named += c.text
        }
        return placed
    }
}
