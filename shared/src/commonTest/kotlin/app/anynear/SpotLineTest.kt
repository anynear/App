package app.anynear

import app.anynear.i18n.texts
import app.anynear.mesh.ChatSpot
import app.anynear.mesh.spotLineOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ADR 0121 §26: overview and notification say what the card of a place says, never the stand-in text with its link. */
class SpotLineTest {
    @Test
    fun aPlaceReadsLikeItsCard() {
        val mt = texts.map
        assertEquals(mt.spotAsk, spotLineOf(ChatSpot.ASK, null, outgoing = false))
        assertEquals(mt.noticeHere, spotLineOf(ChatSpot.HERE, null, outgoing = false))
        assertEquals(mt.spotHereOwn, spotLineOf(ChatSpot.HERE, null, outgoing = true))
        assertEquals(mt.spotMeetText("Zelt"), spotLineOf(ChatSpot.MEET, "Zelt", outgoing = false))
        assertEquals(mt.shareAll, spotLineOf(ChatSpot.ALL, null, outgoing = false))
        // „Angekommen“ names the place if it has a name.
        assertTrue("Zuhause" in spotLineOf(ChatSpot.ARRIVED, "Zuhause", outgoing = false))
        assertEquals(mt.noticeArrived(null), spotLineOf(ChatSpot.ARRIVED, null, outgoing = false))
        assertEquals(mt.arrivedOwn, spotLineOf(ChatSpot.ARRIVED, "Zuhause", outgoing = true))
    }
}
