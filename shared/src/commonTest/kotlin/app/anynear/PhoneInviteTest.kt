package app.anynear

import app.anynear.ui.PickedPhoneContact
import app.anynear.ui.cleanPhoneNumber
import app.anynear.ui.smsNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Invite from the phone's contacts: which number the text message goes to. */
class PhoneInviteTest {
    @Test
    fun numbersLoseSpacesDashesAndBrackets() {
        assertEquals("+4917112345", cleanPhoneNumber(" +49 (171) 123-45 "))
        assertEquals("017112345", cleanPhoneNumber("0171 / 123 45"))
    }

    @Test
    fun theFirstUsableNumberWins() {
        assertEquals("017112345", smsNumber(PickedPhoneContact(listOf("", "--", "0171 12345", "0800 999"))))
    }

    @Test
    fun withoutNumberThereIsNoTextMessage() {
        assertNull(smsNumber(PickedPhoneContact(emptyList())))
        assertNull(smsNumber(PickedPhoneContact(listOf("12", "abc"))))
    }
}
