package app.anynear

import app.anynear.mesh.MessageStatus
import app.anynear.mesh.PublicChannels
import app.anynear.mesh.RadioChannel
import app.anynear.mesh.anynearIndex
import app.anynear.mesh.sendChannel
import app.anynear.mesh.radioCheckChannel
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.SdkMeshLink
import app.anynear.mesh.SdkMeshLink.Companion.isAckFrom
import app.anynear.mesh.SdkMeshLink.Companion.isCheckReplyFrom
import app.anynear.mesh.SdkMeshLink.Companion.toCheckReply
import app.anynear.mesh.RadioCheckReply
import app.anynear.mesh.toMessageStatus
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.SendFailure
import org.meshtastic.sdk.SendState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SdkMeshLinkRulesTest {
    @Test
    fun sdkAckIsNeverShownAsRecipientConfirmation() {
        assertEquals(MessageStatus.RELAYED, SendState.Acked.toMessageStatus())
        assertEquals(MessageStatus.RELAYED, SendState.Delivered.toMessageStatus())
        assertEquals(MessageStatus.NO_CONFIRMATION, SendState.Failed(SendFailure.AckTimeout).toMessageStatus())
        assertEquals(MessageStatus.FAILED, SendState.Failed(SendFailure.NoRoute).toMessageStatus())
    }

    @Test
    fun onlyTheOneRadioChannelCounts() {
        val channels = listOf(
            channel(0, "anynear", Channel.Role.PRIMARY), // The primary channel never belongs to it
            channel(1, "PINA", Channel.Role.SECONDARY), // before ANMP 1 (ADR 0034)
            channel(2, "PYRSA-K3V9Q", Channel.Role.SECONDARY), // channel per circle, before ADR 0051
            channel(3, "Mesh Hessen", Channel.Role.SECONDARY),
            channel(4, "anynear", Channel.Role.SECONDARY),
            channel(5, "PYRSA", Channel.Role.SECONDARY), // radio channel before anynear (ADR 0061)
            channel(6, "anynear", Channel.Role.DISABLED),
        )
        assertEquals(listOf(4), SdkMeshLink.circleChannelsOf(channels).map { it.index })
        assertEquals(1, SdkMeshLink.otherChannelsOf(channels), "nur Mesh Hessen gehört einer anderen App")
        assertTrue(SdkMeshLink.isOldChannelName("PYRSA-K3V9Q"))
        assertTrue(SdkMeshLink.isOldChannelName("PINA"))
        assertTrue(SdkMeshLink.isOldChannelName("PYRSA"))
        assertFalse(SdkMeshLink.isOldChannelName("anynear"))
        assertFalse(SdkMeshLink.isOldChannelName("Mesh Hessen"))
    }

    @Test
    fun replyToARadioWithoutAppMayUseItsChannel() {
        val channels = listOf(
            channel(0, "", Channel.Role.PRIMARY),
            channel(2, "anynear", Channel.Role.SECONDARY),
            channel(3, "Aus", Channel.Role.DISABLED),
        )
        val active = SdkMeshLink.activeSlotsOf(channels)
        val circle = SdkMeshLink.circleChannelsOf(channels)
        assertEquals(setOf(0, 2), active)
        assertTrue(SdkMeshLink.isUsableChannel(0, to = 42, circle, active), "Antwort direkt an ein Funkgerät ohne anynear im Kanal 0")
        assertFalse(SdkMeshLink.isUsableChannel(0, to = null, circle, active), "an alle nie im Kanal 0")
        assertFalse(SdkMeshLink.isUsableChannel(3, to = 42, circle, active), "ausgeschalteter Kanal")
        assertTrue(SdkMeshLink.isUsableChannel(2, to = null, circle, active))
    }

    @Test
    fun ownRadioChannelTakesTheSlotOfAnOldOneWhenNoneIsFree() {
        val full = listOf(channel(0, "", Channel.Role.PRIMARY)) +
            (1..7).map { channel(it, if (it == 5) "PYRSA" else "Fremd $it", Channel.Role.SECONDARY) }
        assertEquals(null, SdkMeshLink.freeSecondarySlot(full))
        assertEquals(5, SdkMeshLink.oldChannelSlot(full))
        assertEquals(null, SdkMeshLink.oldChannelSlot(full.filterNot { it.index == 5 }))
    }

    @Test
    fun channel0BroadcastsCountOnlyWhenChannel0IsOpen() {
        val broadcast = NodeId.BROADCAST.raw
        assertTrue(SdkMeshLink.isChannel0Broadcast(broadcast, 0, openChannel0 = true))
        assertFalse(SdkMeshLink.isChannel0Broadcast(broadcast, 0, openChannel0 = false))
        assertFalse(SdkMeshLink.isChannel0Broadcast(broadcast, 3, openChannel0 = true))
        assertFalse(SdkMeshLink.isChannel0Broadcast(1234, 0, openChannel0 = true))
    }

    @Test
    fun anmpMayGoOnAnynearOrOnAnOpenChannel0() {
        // Hardware test 11.11.2026: with channel 0 chosen for sending, the link refused every packet ("Funkkanal fehlt").
        val anynear = listOf(RadioChannel(2, "anynear", okio.ByteString.EMPTY))
        assertTrue(SdkMeshLink.isAnmpChannel(2, anynear, openChannel0 = false))
        assertTrue(SdkMeshLink.isAnmpChannel(0, anynear, openChannel0 = true))
        assertTrue(SdkMeshLink.isAnmpChannel(0, emptyList(), openChannel0 = true))
        assertFalse(SdkMeshLink.isAnmpChannel(0, anynear, openChannel0 = false))
        assertFalse(SdkMeshLink.isAnmpChannel(1, anynear, openChannel0 = true))
    }

    @Test
    fun nodeNamesLikeMeshtastic() {
        assertEquals("!939d1998", SdkMeshLink.nodeName(0x939d1998.toInt()))
        assertEquals("!0000002a", SdkMeshLink.nodeName(42))
    }

    @Test
    fun openChannel0NeedsDefaultKeyAndNoName() {
        val default = byteArrayOf(1).toByteString()
        fun primary(name: String, psk: okio.ByteString) = Channel.Builder().also { c ->
            c.index = 0
            c.role = Channel.Role.PRIMARY
            c.settings = ChannelSettings.Builder().also { it.name = name; it.psk = psk }.build()
        }.build()
        assertTrue(PublicChannels.isOpenChannel0(listOf(primary("", default))))
        assertFalse(PublicChannels.isOpenChannel0(listOf(primary("Hessenmesh", default))), "eigener Name")
        assertFalse(PublicChannels.isOpenChannel0(listOf(primary("", ByteArray(32) { 7 }.toByteString()))), "eigener Schlüssel")
        assertFalse(PublicChannels.isOpenChannel0(listOf(primary("", okio.ByteString.EMPTY))), "unverschlüsselt")
        assertFalse(PublicChannels.isOpenChannel0(emptyList()))
    }

    @Test
    fun anmpGoesOnChannel0OnlyWhenOpenAndAllowed() {
        assertEquals(0, sendChannel(anynearIndex = 2, openChannel0 = true, channel0Allowed = true))
        assertEquals(2, sendChannel(anynearIndex = 2, openChannel0 = true, channel0Allowed = false))
        assertEquals(2, sendChannel(anynearIndex = 2, openChannel0 = false, channel0Allowed = true))
        assertEquals(0, sendChannel(anynearIndex = null, openChannel0 = true, channel0Allowed = true))
        assertEquals(null, sendChannel(anynearIndex = null, openChannel0 = false, channel0Allowed = true))
        assertEquals(3, anynearIndex(listOf(RadioChannel(1, "PYRSA", okio.ByteString.EMPTY), RadioChannel(3, "anynear", okio.ByteString.EMPTY))))
    }

    @Test
    fun radioCheckTakesThePathOfAMessage() {
        fun heard(node: Int, channel: Int?, outgoing: Boolean = false) =
            ChatMessage("m$node$channel", Conversation.Direct("n:$node"), node, "Hallo", 0, outgoing = outgoing, radioChannel = channel)
        val messages = listOf(heard(7, 0), heard(7, 3), heard(7, 5, outgoing = true), heard(8, 0))
        assertEquals(3, radioCheckChannel(messages, node = 7, radioOnly = true, sendChannel = 2), "Funkgerät ohne anynear: Kanal seiner letzten Nachricht")
        assertEquals(0, radioCheckChannel(messages, node = 9, radioOnly = true, sendChannel = 0), "nie etwas gehört: wie die Nachrichten")
        assertEquals(0, radioCheckChannel(messages, node = 7, radioOnly = false, sendChannel = 0), "anynear-Funkgerät: offener Kanal 0")
        assertEquals(2, radioCheckChannel(messages, node = 7, radioOnly = false, sendChannel = 2), "anynear-Funkgerät ohne Kanal 0: anynear")
        assertEquals(null, radioCheckChannel(emptyList(), node = 7, radioOnly = false, sendChannel = null))
    }

    @Test
    fun onlyAckFromRecipientCounts() {
        assertTrue(routingPacket(from = 5, requestId = 77).isAckFrom(destination = 5, requestId = 77))
        // Implicit acknowledgement of the own device or of a relay
        assertFalse(routingPacket(from = 1, requestId = 77).isAckFrom(destination = 5, requestId = 77))
        // Other message
        assertFalse(routingPacket(from = 5, requestId = 78).isAckFrom(destination = 5, requestId = 77))
        // NAK
        assertFalse(routingPacket(from = 5, requestId = 77, error = Routing.Error.NO_CHANNEL).isAckFrom(5, 77))
        // Not a routing packet
        val text = MeshPacket.Builder().also { p ->
            p.from = 5
            p.decoded = Data.Builder().also {
                it.portnum = PortNum.TEXT_MESSAGE_APP
                it.request_id = 77
                it.payload = "hi".encodeUtf8()
            }.build()
        }.build()
        assertFalse(text.isAckFrom(5, 77))
    }

    @Test
    fun radioCheckCountsOnlyRepliesFromTheTargetButAlsoItsErrors() {
        assertTrue(routingPacket(from = 5, requestId = 77).isCheckReplyFrom(destination = 5, requestId = 77))
        // An error from the target device itself proves the connection as well (ADR 0053)
        assertTrue(routingPacket(from = 5, requestId = 77, error = Routing.Error.PKI_UNKNOWN_PUBKEY).isCheckReplyFrom(5, 77))
        // Implicit acknowledgement of a neighbour or error of the own device
        assertFalse(routingPacket(from = 1, requestId = 77, error = Routing.Error.MAX_RETRANSMIT).isCheckReplyFrom(5, 77))
        assertFalse(routingPacket(from = 5, requestId = 78).isCheckReplyFrom(5, 77))
    }

    @Test
    fun radioCheckReadsTheWayBackFromTheReply() {
        val reply = MeshPacket.Builder().also {
            it.from = 5
            it.hop_start = 5
            it.hop_limit = 3
            it.rx_snr = -4.5f
            it.rx_rssi = -101
        }.build()
        assertEquals(RadioCheckReply(hops = 2, snr = -4.5f, rssi = -101), reply.toCheckReply())
        // Old firmware without hop_start: number unknown, not 0
        assertEquals(null, MeshPacket.Builder().also { it.hop_limit = 3 }.build().toCheckReply().hops)
    }

    @Test
    fun groupMessagesStopAtFirstFinalStateDirectOnesWaitForRecipient() {
        assertTrue(SdkMeshLink.isFinalFor(MessageStatus.RELAYED, isDirect = false))
        assertFalse(SdkMeshLink.isFinalFor(MessageStatus.RELAYED, isDirect = true))
        assertTrue(SdkMeshLink.isFinalFor(MessageStatus.CONFIRMED, isDirect = true))
        assertFalse(SdkMeshLink.isFinalFor(MessageStatus.HANDED_TO_RADIO, isDirect = false))
    }

    @Test
    fun retryBackoffIsBounded() {
        assertEquals(listOf(2L, 2L, 4L, 8L, 16L, 30L, 30L), (0..6).map(SdkMeshLink::retryDelaySeconds))
    }

    private fun channel(index: Int, name: String, role: Channel.Role) = Channel.Builder().also { c ->
        c.index = index
        c.role = role
        c.settings = ChannelSettings.Builder().also { it.name = name }.build()
    }.build()

    private fun routingPacket(from: Int, requestId: Int, error: Routing.Error = Routing.Error.NONE) =
        MeshPacket.Builder().also { p ->
            p.from = from
            p.decoded = Data.Builder().also {
                it.portnum = PortNum.ROUTING_APP
                it.request_id = requestId
                it.payload = Routing.Builder().also { r -> r.error_reason = error }.build().encodeByteString()
            }.build()
        }.build()
}
