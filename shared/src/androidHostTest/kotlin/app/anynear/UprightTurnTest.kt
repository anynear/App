package app.anynear

import app.anynear.attachments.TextOcr
import app.anynear.attachments.quarterTurns
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

/** Set upright: from the reading direction of the lines the rotation that sets the text upright. */
class UprightTurnTest {
    @Test
    fun readingDirectionGivesTurn() {
        fun turnFor(angle: Double) = TextOcr.uprightFromReading(TextOcr.readingQuarter(angle.toFloat()))
        // To the right: already upright.
        assertEquals(0, turnFor(0.0))
        assertEquals(0, turnFor(0.1))
        // Downwards (y grows downwards): the image lies 90° clockwise, so 270° further.
        assertEquals(270, turnFor(PI / 2))
        // Upside down.
        assertEquals(180, turnFor(PI))
        assertEquals(180, turnFor(-PI + 0.05))
        // Upwards.
        assertEquals(90, turnFor(-PI / 2))
        assertEquals(90, turnFor(3 * PI / 2))
    }

    @Test
    fun quarterTurnsNormalizes() {
        assertEquals(0, quarterTurns(0))
        assertEquals(90, quarterTurns(90))
        assertEquals(270, quarterTurns(-90))
        assertEquals(0, quarterTurns(360))
        assertEquals(180, quarterTurns(540))
    }
}
