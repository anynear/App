package app.anynear.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A speech bubble on the map before it is placed: where its tip points (pixels on the screen) and how big it is. With
 * [ring] above 0 it belongs to an area (a honeycomb cell, [ring] pixels from its centre at ([tipX], [tipY]) to a corner):
 * such a bubble has no tip, because there is no point to show, and sits on the edge of the cell (21.11.2026).
 */
internal data class Bubble(val key: String, val tipX: Float, val tipY: Float, val width: Float, val height: Float, val ring: Float = 0f) {
    val area: Boolean get() = ring > 0f
}

/** Where a bubble's centre lies, relative to its tip (for an area: to the centre of the cell). */
internal data class BubbleSpot(val dx: Float, val dy: Float)

/**
 * Places the speech bubbles of the people on the map (20.11.2026): each one stands above the place its tip points to.
 * Where it would cover a bubble placed before (or the place of someone else), it turns to the next direction around
 * its place (from the top clockwise and counter-clockwise in turns, the bottom last), and only when all are taken it
 * moves further out. So several people at the same place or close together are fanned out around it, and every tip
 * still points to its own place. The bubbles are placed in the order of [Bubble.key], so the result depends neither on
 * the order of the list nor on where the map is, only on the distances. Pure, so that it can be tested.
 *
 * A bubble of an area ([Bubble.area]) sits on the edge of its cell, never in its middle, so that nothing looks like a
 * point. Several people in the same cell are spread along its edge, the first by key at the top corner, the others at
 * even steps clockwise; where that is taken, the next free place along the edge, and only then further out.
 *
 * @param tail the room between the tip and the edge of the bubble when it stands above.
 * @param gap the room kept between two bubbles.
 */
internal fun placeBubbles(bubbles: List<Bubble>, tail: Float, gap: Float): List<BubbleSpot> {
    val spots = arrayOfNulls<BubbleSpot>(bubbles.size)
    val placed = mutableListOf<Int>()
    for (i in bubbles.indices.sortedBy { bubbles[it].key }) {
        val b = bubbles[i]
        val candidates = if (b.area) edgeSpots(b, bubbles) else (0 until STEPS).flatMap { step -> DIRECTIONS.map { spotAt(b, it, tail, 1f + step * STRETCH) } }
        // Only real places are kept free; the middle of a cell is no place, a bubble may lie over it.
        fun free(s: BubbleSpot, tips: Boolean) =
            placed.none { covers(b, s, bubbles[it], spots[it]!!, gap) } &&
                (!tips || bubbles.none { o -> !o.area && !o.samePlace(b) && holds(b, s, o.tipX, o.tipY, gap) })
        spots[i] = candidates.firstOrNull { free(it, tips = true) } ?: candidates.firstOrNull { free(it, tips = false) } ?: candidates.first()
        placed += i
    }
    return spots.map { it!! }
}

/** The centre of [b] in direction [degrees] (0 = right, -90 = up) on an ellipse the shape of the bubble, [stretch] times out. */
private fun spotAt(b: Bubble, degrees: Int, tail: Float, stretch: Float): BubbleSpot {
    val a = degrees * PI / 180
    return BubbleSpot(
        (cos(a) * (b.width / 2 + tail) * stretch).toFloat().clean(),
        (sin(a) * (b.height / 2 + tail) * stretch).toFloat().clean(),
    )
}

/**
 * The places on the edge of the cell of [b] to try: first the own share of the edge among all bubbles of the same cell
 * (sorted by key, the first at the top corner, then clockwise), then the edge in steps of [EDGE_STEP] degrees away from
 * it, both ways in turns; each round a little further out.
 */
private fun edgeSpots(b: Bubble, all: List<Bubble>): List<BubbleSpot> {
    val same = all.filter { it.area && it.samePlace(b) }.map { it.key }.sorted()
    val start = -90.0 + 360.0 * same.indexOf(b.key).coerceAtLeast(0) / same.size.coerceAtLeast(1)
    val turns = listOf(0.0) + (1..180 / EDGE_STEP).flatMap { listOf(it * EDGE_STEP.toDouble(), -it * EDGE_STEP.toDouble()) }.dropLast(1)
    return (0 until STEPS).flatMap { step -> turns.map { edgeAt(b.ring * (1f + step * STRETCH / 2), start + it) } }.distinct()
}

/**
 * The point on the edge of a cell with [ring] pixels to a corner in direction [degrees] (0 = right, -90 = up). The
 * cells have a corner at the top (pointy-top): corners at -90 ± 60·k, the middle of the sides 30° from them.
 */
internal fun edgeAt(ring: Float, degrees: Double): BubbleSpot {
    val a = degrees * PI / 180
    // Angle from the middle of the nearest side, whose directions are 0, ±60, ±120 and 180.
    val off = ((degrees % 60 + 60) % 60).let { if (it > 30) 60 - it else it }
    val r = ring * cos(PI / 6) / cos(off * PI / 180)
    return BubbleSpot((cos(a) * r).toFloat().clean(), (sin(a) * r).toFloat().clean())
}

/**
 * How far from the centre of a bubble ([width] × [height], fully rounded ends) its edge lies in direction ([dx], [dy]).
 * The tail starts there, so that it keeps its full width whichever side the bubble stands on (21.11.2026); measured from
 * the centre it would shrink to a hairline beside a wide bubble.
 */
internal fun bubbleEdge(width: Float, height: Float, dx: Float, dy: Float): Float {
    val length = kotlin.math.hypot(dx, dy).takeIf { it > 0f } ?: return 0f
    val ux = abs(dx) / length
    val uy = abs(dy) / length
    val r = height / 2
    val straight = (width / 2 - r).coerceAtLeast(0f)
    // Through the straight top or bottom, or through one of the round ends.
    if (uy > 0f && r * ux / uy <= straight) return r / uy
    return straight * ux + kotlin.math.sqrt(straight * straight * ux * ux - straight * straight + r * r)
}

/** Whether two placed bubbles overlap, with [gap] between them. */
internal fun covers(a: Bubble, sa: BubbleSpot, b: Bubble, sb: BubbleSpot, gap: Float): Boolean {
    val dx = abs((a.tipX + sa.dx) - (b.tipX + sb.dx))
    val dy = abs((a.tipY + sa.dy) - (b.tipY + sb.dy))
    return dx < (a.width + b.width) / 2 + gap && dy < (a.height + b.height) / 2 + gap
}

/** Whether the placed bubble lies over the point ([x], [y]), with [gap] around it. */
private fun holds(b: Bubble, s: BubbleSpot, x: Float, y: Float, gap: Float): Boolean =
    abs(b.tipX + s.dx - x) < b.width / 2 + gap && abs(b.tipY + s.dy - y) < b.height / 2 + gap

/** At the same place as [other]: its tip is the own one, a bubble may lie over it. */
private fun Bubble.samePlace(other: Bubble) = abs(tipX - other.tipX) < SAME && abs(tipY - other.tipY) < SAME

/** Places closer than this many pixels count as the same. */
private const val SAME = 0.5f

/** No `-0.0` and no tiny remainders of the sine, so that equal layouts compare equal. */
private fun Float.clean(): Float = if (abs(this) < 0.01f) 0f else this

/** The directions to try in degrees: the top first, then in turns to the right and to the left, the bottom last. */
private val DIRECTIONS = listOf(-90, -60, -120, -30, -150, 0, 180, 30, 150, 60, 120, 90)

/** Steps along the edge of a cell, in degrees. */
private const val EDGE_STEP = 15

/** How far a bubble moves out each time all directions are taken, and how often. */
private const val STRETCH = 0.3f
private const val STEPS = 16
