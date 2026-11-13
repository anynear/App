package app.anynear

import app.anynear.alerts.Alert
import app.anynear.alerts.AlertPriority
import app.anynear.app.Support
import kotlin.test.Test
import app.anynear.i18n.Strings
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Support as a chat (ADR 0065): name of the circle and first message. */
class SupportTest {
    @Test
    fun shortSubjectStaysWhole() {
        assertEquals("Support: Bilder fehlen", Support.circleName("Support:", "  Bilder   fehlen "))
    }

    @Test
    fun longSubjectIsCutAtAWord() {
        val name = Support.circleName("Support:", "Nachrichten im Kreis kommen seit gestern nicht mehr an")
        assertTrue(name.length <= Support.MAX_CIRCLE_NAME, name)
        assertTrue(name.endsWith("…"), name)
        assertTrue(name.startsWith("Support: Nachrichten im Kreis"), name)
    }

    @Test
    fun firstMessageHasKindTextAndVersion() {
        val text = Support.firstMessage("Fehler", "Bilder fehlen", "Seit heute.", "0.4.0", "Android")
        assertEquals("Fehler: Bilder fehlen\n\nSeit heute.\n\n${AppConfig.APP_NAME} 0.4.0 · Android", text)
    }

    private val prefixes = listOf("Neue Anfrage:", "New request:")

    @Test
    fun recognisesTheRequestToTheSupport() {
        val de = Strings.german.support.newRequest("Bilder fehlen", "https://anynear.app/k#abc")
        assertEquals("Bilder fehlen", Support.requestSubject(de, prefixes))
        val en = Strings.english.support.newRequest("Pictures missing", "https://anynear.app/k#abc")
        assertEquals("Pictures missing", Support.requestSubject(en, prefixes))
    }

    @Test
    fun ordinaryMessagesAreNoRequests() {
        assertNull(Support.requestSubject("Neue Anfrage: ohne Link", prefixes))
        assertNull(Support.requestSubject("Hallo\nhttps://anynear.app/k#abc", prefixes))
    }

    @Test
    fun findsTheInviteLinkInTheRequest() {
        val text = Strings.german.support.newRequest("Bilder fehlen", "https://anynear.app/k#abc")
        assertEquals("https://anynear.app/k#abc", Support.inviteLink(text))
        assertNull(Support.inviteLink("Hallo"))
    }

    @Test
    fun alertFromAnotherDeviceIsNotSentAgain() {
        val alerts = listOf(Alert("ch", "01", 10_000, AlertPriority.Warning, "Neue Support-Anfrage", "Text"))
        assertTrue(Support.alreadyAlerted(alerts, "ch", " Text ", 9_000))
        assertFalse(Support.alreadyAlerted(alerts, "ch", "Text", 200_000))
        assertFalse(Support.alreadyAlerted(alerts, "other", "Text", 9_000))
        assertFalse(Support.alreadyAlerted(alerts, "ch", "Anderer Text", 9_000))
    }
}
