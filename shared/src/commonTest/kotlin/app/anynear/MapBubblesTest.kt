package app.anynear

import app.anynear.ui.Bubble
import app.anynear.ui.BubbleSpot
import app.anynear.ui.bubbleEdge
import app.anynear.ui.covers
import app.anynear.ui.edgeAt
import app.anynear.ui.placeBubbles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapBubblesTest {
    private val tail = 10f
    private val gap = 4f

    private fun bubble(key: String, x: Float, y: Float, width: Float = 120f) = Bubble(key, x, y, width, 40f)

    private fun assertFree(bubbles: List<Bubble>, spots: List<BubbleSpot>) {
        for (i in bubbles.indices) for (j in i + 1 until bubbles.size) {
            assertFalse(covers(bubbles[i], spots[i], bubbles[j], spots[j], gap), "${bubbles[i].key} covers ${bubbles[j].key}")
        }
    }

    @Test
    fun aloneTheBubbleStandsAboveItsPlace() {
        val spots = placeBubbles(listOf(bubble("a", 100f, 200f)), tail, gap)
        assertEquals(listOf(BubbleSpot(0f, -30f)), spots)
    }

    @Test
    fun farApartNothingIsFanned() {
        val bubbles = listOf(bubble("a", 0f, 0f), bubble("b", 400f, 0f), bubble("c", 0f, 300f))
        assertEquals(List(3) { BubbleSpot(0f, -30f) }, placeBubbles(bubbles, tail, gap))
    }

    @Test
    fun twoAtTheSamePlaceGoDifferentWays() {
        val bubbles = listOf(bubble("b", 50f, 50f), bubble("a", 50f, 50f))
        val spots = placeBubbles(bubbles, tail, gap)
        assertFree(bubbles, spots)
        // The first by key on top, the other turned to the lower right; both still point to the place.
        assertEquals(BubbleSpot(0f, -30f), spots[1])
        assertTrue(spots[0].dx > 0 && spots[0].dy > 0)
    }

    @Test
    fun aBubbleDoesNotCoverTheTipOfAnother() {
        // b stands just above a: a's bubble turns away instead of covering b's place.
        val bubbles = listOf(bubble("a", 100f, 100f), bubble("b", 100f, 75f))
        val spots = placeBubbles(bubbles, tail, gap)
        assertFree(bubbles, spots)
        val a = spots[0]
        assertFalse(kotlin.math.abs(100f + a.dx - 100f) < 64f && kotlin.math.abs(100f + a.dy - 75f) < 24f)
    }

    @Test
    fun theTipStaysShortWhenThereIsRoom() {
        // Three at one place: above, lower right, lower left, none moved out much further than needed.
        val bubbles = listOf(bubble("a", 0f, 0f), bubble("b", 0f, 0f), bubble("c", 0f, 0f))
        val spots = placeBubbles(bubbles, tail, gap)
        assertFree(bubbles, spots)
        assertEquals(BubbleSpot(0f, -30f), spots[0])
        assertTrue(spots[1].dx > 0 && spots[1].dy > 0 && spots[2].dx < 0 && spots[2].dy > 0, "$spots")
        spots.forEach { assertTrue(kotlin.math.abs(it.dx) <= (60f + tail) * 1.3f + 1f && kotlin.math.abs(it.dy) <= (20f + tail) * 1.3f + 1f, "$it") }
    }

    @Test
    fun manyAtTheSamePlaceAreFannedAllAround() {
        val bubbles = (1..7).map { bubble("p$it", 300f, 300f, width = 80f + it * 10) }
        val spots = placeBubbles(bubbles, tail, gap)
        assertFree(bubbles, spots)
        assertEquals(7, spots.distinct().size)
        // Some to the left, some to the right, some above, some below.
        assertTrue(spots.any { it.dx < 0 } && spots.any { it.dx > 0 } && spots.any { it.dy < 0 } && spots.any { it.dy > 0 })
    }

    @Test
    fun closePlacesAreFannedToo() {
        val bubbles = listOf(bubble("a", 100f, 100f), bubble("b", 130f, 105f), bubble("c", 400f, 400f))
        val spots = placeBubbles(bubbles, tail, gap)
        assertFree(bubbles, spots)
        // The one far away is left alone.
        assertEquals(BubbleSpot(0f, -30f), spots[2])
    }

    @Test
    fun aChainOfGroupsEndsFree() {
        // Each one close to the next: fanning one pair pushes it into the next.
        val bubbles = (0 until 6).map { bubble("p$it", 100f + it * 60f, 200f + (it % 2) * 20f) }
        assertFree(bubbles, placeBubbles(bubbles, tail, gap))
    }

    @Test
    fun movingTheMapDoesNotChangeTheLayout() {
        val bubbles = listOf(bubble("a", 100f, 100f), bubble("b", 100f, 100f), bubble("c", 120f, 90f))
        val moved = bubbles.map { it.copy(tipX = it.tipX - 333.3f, tipY = it.tipY + 71.7f) }
        assertEquals(placeBubbles(bubbles, tail, gap), placeBubbles(moved, tail, gap))
    }

    @Test
    fun theOrderOfTheListDoesNotMatterAtOnePlace() {
        val bubbles = listOf(bubble("a", 0f, 0f), bubble("b", 0f, 0f), bubble("c", 0f, 0f))
        val spots = placeBubbles(bubbles, tail, gap)
        val reversed = placeBubbles(bubbles.reversed(), tail, gap).reversed()
        assertEquals(spots, reversed)
    }

    private fun area(key: String, x: Float, y: Float, ring: Float = 200f) = Bubble(key, x, y, 120f, 40f, ring)

    @Test
    fun theEdgeOfACellRunsThroughItsCornersAndSides() {
        // Pointy-top: a corner at the top, the middle of a side to the right.
        assertEquals(BubbleSpot(0f, -100f), edgeAt(100f, -90.0))
        val side = edgeAt(100f, 0.0)
        assertEquals(86.6f, side.dx, 0.01f)
        assertEquals(0f, side.dy)
    }

    @Test
    fun anAreaAloneSitsOnTheTopCornerOfItsCell() {
        assertEquals(listOf(BubbleSpot(0f, -200f)), placeBubbles(listOf(area("a", 0f, 0f)), tail, gap))
    }

    @Test
    fun severalInOneCellAreSpreadAlongItsEdge() {
        val bubbles = listOf(area("c", 0f, 0f), area("a", 0f, 0f), area("b", 0f, 0f))
        val spots = placeBubbles(bubbles, tail, gap)
        assertFree(bubbles, spots)
        // a at the top, b and c a third of the way round each, all on the edge, none in the middle.
        assertEquals(BubbleSpot(0f, -200f), spots[1])
        assertEquals(edgeAt(200f, 30.0), spots[2])
        assertEquals(edgeAt(200f, 150.0), spots[0])
    }

    @Test
    fun anAreaBubbleKeepsOffAnExactPlaceInItsCell() {
        // Someone exact just below the top corner: the area bubble moves along the edge instead of covering the place.
        val bubbles = listOf(area("a", 0f, 0f), bubble("b", 0f, -190f))
        val spots = placeBubbles(bubbles, tail, gap)
        assertFree(bubbles, spots)
        assertTrue(spots[0] != BubbleSpot(0f, -200f))
        assertFalse(kotlin.math.abs(spots[0].dx) < 64f && kotlin.math.abs(spots[0].dy + 190f) < 24f)
    }

    @Test
    fun theMiddleOfACellIsNoPlaceToKeepFree() {
        // An exact bubble may stand above the centre of a cell: nobody is there.
        val bubbles = listOf(area("a", 0f, 0f), bubble("b", 0f, 40f))
        assertEquals(BubbleSpot(0f, -30f), placeBubbles(bubbles, tail, gap)[1])
    }

    @Test
    fun theTailStartsAtTheEdgeOfTheBubble() {
        // A pill 120 × 40: the edge is 20 above, 60 beside the centre, and on the round end in between.
        assertEquals(20f, bubbleEdge(120f, 40f, 0f, -1f), 0.01f)
        assertEquals(60f, bubbleEdge(120f, 40f, 1f, 0f), 0.01f)
        // At 45° still through the straight top, at (20, 20).
        assertEquals(20f * kotlin.math.sqrt(2f), bubbleEdge(120f, 40f, 1f, 1f), 0.01f)
        // Flatter, through the round end around (40, 0) with radius 20.
        val d = bubbleEdge(120f, 40f, 3f, 1f)
        val n = kotlin.math.sqrt(10f)
        assertEquals(20f, kotlin.math.hypot(d * 3 / n - 40f, d / n), 0.01f)
    }
}
