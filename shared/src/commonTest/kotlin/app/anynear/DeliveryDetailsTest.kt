package app.anynear

import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.Transport
import app.anynear.ui.deliveryDetails
import kotlin.test.Test
import kotlin.test.assertEquals

class DeliveryDetailsTest {
    private val names = mapOf("d:a" to "Anna", "d:b" to "Ben", "d:c" to "Carla")

    @Test
    fun circleListsEveryPersonLikeAGroupChat() {
        val message = ChatMessage(
            localId = "x",
            conversation = Conversation.Circle("k1"),
            fromNodeNum = 0,
            text = "Hallo",
            timestampMillis = 1,
            outgoing = true,
            status = MessageStatus.ONLINE,
            via = setOf(Transport.INTERNET),
            recipients = setOf("d:c", "d:a", "d:b"),
            deliveredTo = setOf("d:a", "d:c"),
            deliveredAt = mapOf("d:a" to 2, "d:c" to 3),
        )

        assertEquals(
            "Gesendet: t1 über Internet\nAngekommen bei 2 von 3:\nAnna: t2\nBen: noch nicht\nCarla: t3\nNoch von niemandem gelesen",
            message.deliveryDetails(isDirect = false, format = { "t$it" }, nameOf = { names.getValue(it) }),
        )
    }

    @Test
    fun relayedRadioMessageSaysSo() {
        val message = ChatMessage(
            localId = "r",
            conversation = Conversation.Direct("d:a"),
            fromNodeNum = 0,
            text = "Bin im Wald",
            timestampMillis = 1,
            outgoing = false,
            via = setOf(Transport.RADIO),
            relayed = true,
        )
        val text = message.deliveryDetails(isDirect = true, format = { "12:00" })
        assertEquals(2, text.lines().size)
        assertEquals(app.anynear.i18n.texts.chat.relayedByOther, text.lines().last())
        assertEquals(1, message.copy(relayed = false).deliveryDetails(isDirect = true, format = { "12:00" }).lines().size)
    }
}
