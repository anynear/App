package app.anynear.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A speech bubble on the map before it is placed: where its tip points (pixels on the screen) and how big it is. */
internal data class Bubble(val key: String, val tipX: Float, val tipY: Float, val width: Float, val height: Float)

/** Where a bubble's centre lies, relative to its tip. */
internal data class BubbleSpot(val dx: Float, val dy: Float)

/**
 * Places the speech bubbles of the people on the map (20.11.2026): each one stands above the place its tip points to.
 * Where it would cover a bubble placed before (or the place of someone else), it turns to the next direction around
 * its place (from the top clockwise and counter-clockwise in turns, the bottom last), and only when all are taken it
 * moves further out. So several people at the same place or close together are fanned out around it, and every tip
 * still points to its own place. The bubbles are placed in the order of [Bubble.key], so the result depends neither on
 * the order of the list nor on where the map is, only on the distances. Pure, so that it can be tested.
 *
 * @param tail the room between the tip and the edge of the bubble when it stands above.
 * @param gap the room kept between two bubbles.
 */
internal fun placeBubbles(bubbles: List<Bubble>, tail: Float, gap: Float): List<BubbleSpot> {
    val spots = arrayOfNulls<BubbleSpot>(bubbles.size)
    val placed = mutableListOf<Int>()
    for (i in bubbles.indices.sortedBy { bubbles[it].key }) {
        val b = bubbles[i]
        val candidates = (0 until STEPS).flatMap { step -> DIRECTIONS.map { spotAt(b, it, tail, 1f + step * STRETCH) } }
        fun free(s: BubbleSpot, tips: Boolean) =
            placed.none { covers(b, s, bubbles[it], spots[it]!!, gap) } &&
                (!tips || bubbles.none { o -> !o.samePlace(b) && holds(b, s, o.tipX, o.tipY, gap) })
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

/** How far a bubble moves out each time all directions are taken, and how often. */
private const val STRETCH = 0.3f
private const val STEPS = 16
