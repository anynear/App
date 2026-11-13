package app.anynear

import app.anynear.anmp.core.AnmpLinks
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The hard-coded contact link of support is a valid contact in this network (ADR 0065). */
class SupportLinkTest {
    @Test
    fun supportLinkIsAContactInThisNetwork() = runTest {
        val link = assertNotNull(AppConfig.SUPPORT_CONTACT_LINK)
        val card = assertNotNull(AnmpLinks.parseContact(link, "anynear.app"))
        assertEquals("ro9NwRU2zyAA-5xRZy0U48TOfL5kGHDZABeekQ1utVk", card.networkId)
        assertEquals("anynear Support", card.name)
        // ADR 0084: every device of the support identity opens requests, not only the one the link comes from.
        assertNotNull(card.mailboxKey)
    }

    @Test
    fun supportJsonOnlyTakesAContactLinkOnAnynearApp() {
        assertEquals("https://anynear.app/c#abc_-1", app.anynear.app.Support.parseSupportJson("""{"version":1,"contact":"https://anynear.app/c#abc_-1"}"""))
        kotlin.test.assertNull(app.anynear.app.Support.parseSupportJson("""{"version":2,"contact":"https://anynear.app/c#abc"}"""))
        kotlin.test.assertNull(app.anynear.app.Support.parseSupportJson("""{"version":1,"contact":"https://evil.example/c#abc"}"""))
        kotlin.test.assertNull(app.anynear.app.Support.parseSupportJson("""{"version":1,"contact":"https://anynear.app/k#abc"}"""))
        kotlin.test.assertNull(app.anynear.app.Support.parseSupportJson("kein json"))
    }
}
