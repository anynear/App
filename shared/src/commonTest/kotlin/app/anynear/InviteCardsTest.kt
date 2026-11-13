package app.anynear

import app.anynear.chats.InviteCard
import app.anynear.chats.InviteKind
import app.anynear.chats.findInviteLinks
import app.anynear.chats.inviteCardView
import app.anynear.chats.withoutInviteLinks
import app.anynear.i18n.localeTagOverride
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Invitations in chat as a card instead of a link: detection, removal from the text, the card sentence and button. */
class InviteCardsTest {
    private val channel = "https://anynear.app/a#QUJD-_x"
    private val contact = "https://anynear.app/c#Q09O"
    private val circle = "https://anynear.app/k/#S1JF"

    @BeforeTest
    fun german() {
        localeTagOverride = "de-DE"
    }

    @AfterTest
    fun reset() {
        localeTagOverride = null
    }

    @Test
    fun findsOurLinksOfAllKindsButNotOthers() {
        val text = "Kanal $channel, Kontakt $contact und Kreis $circle. Sonst https://example.org/a#x und https://anynear.app/p#pair"
        val links = findInviteLinks(text)
        assertEquals(listOf(InviteKind.Channel, InviteKind.Contact, InviteKind.Circle, InviteKind.Device), links.map { it.kind })
        assertEquals(listOf(channel, contact, circle, "https://anynear.app/p#pair"), links.map { it.url })
        assertEquals(channel, text.substring(links[0].range))
        // Punctuation at the end is not part of the link.
        assertEquals(circle, text.substring(links[2].range))
        assertTrue(findInviteLinks("Schau auf https://anynear.app oder www.anynear.app").isEmpty())
        // On anynear.net links stay valid (ADR 0061, decision 4), old hosts do not.
        assertEquals(listOf(InviteKind.Contact), findInviteLinks("https://anynear.net/c#Q09O").map { it.kind })
        assertTrue(findInviteLinks("https://pyrsa.net/c#Q09O").isEmpty())
    }

    @Test
    fun messageWithOnlyTheLinkShowsNothingButTheCard() {
        assertEquals("", withoutInviteLinks(channel))
        assertEquals("", withoutInviteLinks("  $contact\n"))
        assertEquals("", withoutInviteLinks("$channel $circle"))
    }

    @Test
    fun linkDisappearsFromTheTextTheRestStays() {
        assertEquals("Für die Familie", withoutInviteLinks("Für die Familie: $circle"))
        assertEquals("Hier ist der Kanal fürs Dorf", withoutInviteLinks("Hier ist $channel der Kanal fürs Dorf"))
        assertEquals("Hallo\n\nbis später", withoutInviteLinks("Hallo\n$contact\n\n\nbis später"))
        // Without an invitation link the text stays exactly as it is, even with other links.
        val plain = "Schau: https://example.org/ "
        assertEquals(plain, withoutInviteLinks(plain))
    }

    @Test
    fun forwardedShareSentenceDisappearsNextToItsCard() {
        // Whoever puts the text from „Teilen“ into a chat sees only the card, not the same sentence again.
        assertEquals("", withoutInviteLinks("Testperson Kim lädt dich zum Kanal „Dorf“ bei anynear ein: $channel"))
        assertEquals("", withoutInviteLinks("Testperson Kim invites you to the channel “Dorf” on anynear: $channel"))
        assertEquals("", withoutInviteLinks("Komm in meinen Kreis „Familie“ bei anynear: $circle"))
        assertEquals("", withoutInviteLinks("Komm wieder in den Kreis „Familie“ bei anynear: $circle"))
        assertEquals("", withoutInviteLinks("Testperson Kim bei anynear. Tippe auf den Link, um mich als Kontakt hinzuzufügen: $contact"))
        assertEquals("", withoutInviteLinks("Testperson Kim on anynear. Tap the link to add me as a contact: $contact"))
        // Own words next to it stay.
        assertEquals("Das ist der Kanal fürs Dorf", withoutInviteLinks("Das ist der Kanal fürs Dorf $channel"))
    }

    @Test
    fun sharingTheOwnContactIsNamedOnce() {
        val view = inviteCardView(InviteCard.Contact("Testperson Kim", known = false, self = false), "Testperson Kim", own = false)
        assertEquals("Testperson Kim teilt den eigenen Kontakt.", view.sentence)
        assertEquals("Hinzufügen", view.action)
        val own = inviteCardView(InviteCard.Contact("Testperson Kim", known = false, self = true), "Testperson Kim", own = true)
        assertEquals("Du hast deinen Kontakt geteilt.", own.sentence)
    }

    @Test
    fun channelCardNamesSenderAndChannel() {
        val view = inviteCardView(InviteCard.Channel("Dorf", curated = false, subscribed = false), "Testperson Kim", own = false)
        assertEquals("Testperson Kim lädt dich zum Kanal „Dorf“ ein.", view.sentence)
        assertEquals("Abonnieren", view.action)
        assertNull(view.status)
        val joined = inviteCardView(InviteCard.Channel("Dorf", curated = false, subscribed = true), "Testperson Kim", own = false)
        assertEquals("Abonniert", joined.status)
        assertEquals("Öffnen", joined.action)
        val own = inviteCardView(InviteCard.Channel("Dorf", curated = false, subscribed = true), "Testperson Kim", own = true)
        assertEquals("Du hast zum Kanal „Dorf“ eingeladen.", own.sentence)
    }

    @Test
    fun officialChannelSaysOfficialWarnings() {
        val view = inviteCardView(InviteCard.Channel("NINA", curated = true, subscribed = false), "Testperson Kim", own = false)
        assertEquals("Testperson Kim lädt dich zu den amtlichen Warnungen ein.", view.sentence)
        assertEquals("Du hast zu den amtlichen Warnungen eingeladen.", inviteCardView(InviteCard.Channel("NINA", true, false), "x", own = true).sentence)
    }

    @Test
    fun contactCardWithoutGenderedWords() {
        val view = inviteCardView(InviteCard.Contact("Testperson Ben", known = false, self = false), "Testperson Kim", own = false)
        assertEquals("Testperson Kim teilt einen Kontakt: Testperson Ben", view.sentence)
        assertEquals("Hinzufügen", view.action)
        val known = inviteCardView(InviteCard.Contact("Testperson Ben", known = true, self = false), "Testperson Kim", own = false)
        assertEquals("Schon in deinen Kontakten", known.status)
        assertEquals("Öffnen", known.action)
        // The own contact: nothing to do.
        val self = inviteCardView(InviteCard.Contact("Testperson Anna", known = false, self = true), "Testperson Kim", own = false)
        assertEquals("Das bist du.", self.status)
        assertNull(self.action)
        assertEquals("Du hast einen Kontakt geteilt: Testperson Ben", inviteCardView(InviteCard.Contact("Testperson Ben", false, false), "x", own = true).sentence)
        for (lang in listOf("de-DE", "en-US")) {
            localeTagOverride = lang
            val sentences = listOf(true, false).map { inviteCardView(InviteCard.Contact("Testperson Ben", false, false), "Testperson Kim", own = it).sentence }
            val gendered = Regex("""\b(sein|seine|seinen|ihr|ihre|ihren|his|her|him)\b""", RegexOption.IGNORE_CASE)
            assertFalse(sentences.any { gendered.containsMatchIn(it) }, "$lang: $sentences")
        }
    }

    @Test
    fun circleCardJoinOrOpen() {
        val view = inviteCardView(InviteCard.Circle("Familie", member = false), "Testperson Kim", own = false)
        assertEquals("Testperson Kim lädt dich in den Kreis „Familie“ ein.", view.sentence)
        assertEquals("Beitreten", view.action)
        val member = inviteCardView(InviteCard.Circle("Familie", member = true), "Testperson Kim", own = false)
        assertEquals("Öffnen", member.action)
        assertEquals("Du hast in den Kreis „Familie“ eingeladen.", inviteCardView(InviteCard.Circle("Familie", true), "x", own = true).sentence)
    }

    @Test
    fun deviceCardAddsOnlyNewDevices() {
        val view = inviteCardView(InviteCard.Device("Tablet", added = false, self = false), "Testperson Kim", own = false)
        assertEquals("Testperson Kim teilt den Code des neuen Geräts „Tablet“.", view.sentence)
        assertEquals("Gerät hinzufügen", view.action)
        assertNull(inviteCardView(InviteCard.Device("Tablet", added = true, self = false), "x", own = false).action)
        val self = inviteCardView(InviteCard.Device("", added = false, self = true), "x", own = true)
        assertEquals("Du hast den Code eines neuen Geräts geteilt.", self.sentence)
        assertEquals("Das ist dieses Gerät.", self.status)
        assertNull(self.action)
    }

    @Test
    fun brokenLinkIsAQuietCardWithoutAction() {
        val view = inviteCardView(InviteCard.Broken, "Testperson Kim", own = false)
        assertEquals("Diese Einladung lässt sich nicht öffnen.", view.sentence)
        assertNull(view.action)
        assertFalse(view.sentence.contains("http"))
    }

    @Test
    fun englishCards() {
        localeTagOverride = "en-US"
        assertEquals("Testperson Kim invites you to the channel “Dorf”.", inviteCardView(InviteCard.Channel("Dorf", false, false), "Testperson Kim", own = false).sentence)
        assertEquals("Testperson Kim shares a contact: Testperson Ben", inviteCardView(InviteCard.Contact("Testperson Ben", false, false), "Testperson Kim", own = false).sentence)
        assertEquals("Join", inviteCardView(InviteCard.Circle("Family", false), "Testperson Kim", own = false).action)
    }
}
