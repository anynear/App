package app.anynear

import app.anynear.anmp.core.SosContact
import app.anynear.sos.CARD_ASKED_US
import app.anynear.sos.CARD_OVER_FOR_THEM
import app.anynear.sos.CARD_OVER_FOR_US
import app.anynear.sos.CARD_THEY_NO
import app.anynear.sos.CARD_THEY_YES
import app.anynear.sos.CARD_WE_ASKED
import app.anynear.sos.CARD_WE_NO
import app.anynear.sos.CARD_WE_YES
import app.anynear.sos.sosCards
import kotlin.test.Test
import kotlin.test.assertEquals

/** ADR 0123 §5: every step about SOS contacts is a card in the chat, from what changed. */
class SosCardsTest {
    private fun cards(
        askedBefore: Boolean = false, asked: Boolean = false, agreedBefore: Boolean = false, agreed: Boolean = false,
        contactBefore: Int? = null, contact: Int? = null, chosen: Boolean = true,
    ) = sosCards(askedBefore, asked, agreedBefore, agreed, contactBefore, contact, chosen)

    @Test
    fun theQuestionAndTheOwnAnswer() {
        assertEquals(listOf(CARD_ASKED_US), cards(asked = true))
        assertEquals(listOf(CARD_WE_YES), cards(askedBefore = true, agreed = true))
        assertEquals(listOf(CARD_WE_NO), cards(askedBefore = true))
        // Taken back later, or the other ended it.
        assertEquals(listOf(CARD_OVER_FOR_US), cards(agreedBefore = true))
        assertEquals(emptyList(), cards(askedBefore = true, asked = true))
    }

    @Test
    fun theOwnQuestionAndTheOthersAnswer() {
        assertEquals(listOf(CARD_WE_ASKED), cards(contact = SosContact.ASKED))
        // Asked through a circle: no card in every chat.
        assertEquals(emptyList(), cards(contact = SosContact.ASKED, chosen = false))
        // Asked again after a day: nothing new.
        assertEquals(emptyList(), cards(contactBefore = SosContact.ASKED, contact = SosContact.ASKED))
        assertEquals(listOf(CARD_THEY_YES), cards(contactBefore = SosContact.ASKED, contact = SosContact.AGREED))
        assertEquals(listOf(CARD_THEY_NO), cards(contactBefore = SosContact.ASKED, contact = SosContact.REFUSED))
        // A yes taken back, or ended by the own person.
        assertEquals(listOf(CARD_OVER_FOR_THEM), cards(contactBefore = SosContact.AGREED, contact = SosContact.REFUSED))
        assertEquals(listOf(CARD_OVER_FOR_THEM), cards(contactBefore = SosContact.AGREED))
    }
}
