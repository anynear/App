package app.anynear

import app.anynear.chats.findAppLink
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.Transport
import app.anynear.ui.DeliveryMark
import app.anynear.ui.deliveryLines
import app.anynear.ui.footerVia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageFooterTest {
    private fun outgoing(status: MessageStatus, deliveredVia: Set<Transport> = emptySet()) = ChatMessage(
        localId = "x",
        conversation = Conversation.Direct("d:a"),
        fromNodeNum = 0,
        text = "Hallo",
        timestampMillis = 1,
        outgoing = true,
        status = status,
        via = setOf(Transport.INTERNET),
        deliveredVia = deliveredVia,
        arrivedAtMillis = 2,
    )

    @Test
    fun footerShowsOnlyTheSendingPathUntilArrived() {
        assertEquals(setOf(Transport.INTERNET), outgoing(MessageStatus.ONLINE).footerVia)
    }

    @Test
    fun footerShowsOnlyTheArrivalPathOnceArrived() {
        assertEquals(setOf(Transport.RADIO), outgoing(MessageStatus.CONFIRMED, deliveredVia = setOf(Transport.RADIO)).footerVia)
    }

    @Test
    fun detailsUseTicksLikeWhatsApp() {
        val lines = outgoing(MessageStatus.CONFIRMED, deliveredVia = setOf(Transport.RADIO)).deliveryLines(isDirect = true, format = { "t$it" })
        assertEquals(listOf(DeliveryMark.SENT, DeliveryMark.ARRIVED, DeliveryMark.READ), lines.map { it.mark })
        assertEquals(listOf(setOf(Transport.INTERNET), setOf(Transport.RADIO), emptySet()), lines.map { it.via })
        assertEquals(listOf("Gesendet: t1", "Angekommen: t2", "Noch nicht gelesen"), lines.map { it.text })
    }

    @Test
    fun detailsShowArrivalEvenWhenOnlyAReadReceiptCame() {
        val read = outgoing(MessageStatus.READ).copy(readAt = mapOf("d:a" to 3), readVia = setOf(Transport.RADIO))
        val lines = read.deliveryLines(isDirect = true, format = { "t$it" })
        assertEquals(listOf("Gesendet: t1", "Angekommen: t2", "Gelesen: t3"), lines.map { it.text })
        assertEquals(setOf(Transport.RADIO), lines[1].via)
    }

    @Test
    fun findsInvitationAndContactLinksInText() {
        assertEquals("https://anynear.net/k#abc", findAppLink("Komm in unseren Kreis: https://anynear.net/k#abc"))
        assertEquals("https://anynear.net/c#xyz", findAppLink("https://anynear.net/c#xyz"))
        assertNull(findAppLink("https://anynear.app/beta"))
        // Settings link (ADR 0089) is not an invitation, but recognized as a settings link.
        assertNull(findAppLink("https://anynear.app/s#erase"))
        assertTrue(app.anynear.chats.isSettingsLink("https://anynear.app/s#erase"))
        assertTrue(app.anynear.chats.isSettingsLink("https://anynear.net/s/#erase"))
        assertFalse(app.anynear.chats.isSettingsLink("https://anynear.app/settings#erase"))
        // Created on anynear.app (ADR 0061, decision 4); links of earlier hosts no longer exist.
        assertEquals("https://anynear.app/k#abc", findAppLink("https://anynear.app/k#abc"))
        assertNull(findAppLink("https://old-host.example/k#abc"))
        assertNull(findAppLink("https://example.com/k#abc"))
    }
}
