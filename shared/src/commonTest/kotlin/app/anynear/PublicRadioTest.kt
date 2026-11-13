package app.anynear

import app.anynear.contacts.Contact
import app.anynear.app.CommunityOffer
import app.anynear.app.PublicRadio
import app.anynear.app.PublicSendResult
import app.anynear.data.TextFile
import app.anynear.mesh.ChannelSetupResult
import app.anynear.mesh.DeviceSetupCheck
import app.anynear.mesh.DeviceSetupResult
import app.anynear.mesh.IncomingPublicText
import app.anynear.mesh.IncomingText
import app.anynear.mesh.LinkState
import app.anynear.mesh.MeshLink
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.OwnDevice
import app.anynear.mesh.PairedDevice
import app.anynear.mesh.RadioProfile
import app.anynear.mesh.PublicChannel
import app.anynear.mesh.PublicChannels
import app.anynear.mesh.PublicRadioChannel
import app.anynear.mesh.RadioChannel
import app.anynear.mesh.RadioSend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.ModuleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Public radio of the area (ADR 0055): which channels are public, limits when sending, only on the phone. */
class PublicRadioTest {
    /** Made-up key for tests, not the one of Mesh Hessen. */
    private val testCommunityPsk = ByteArray(32) { (it * 7).toByte() }

    @Test
    fun onlyDefaultPrimaryAndKnownCommunityChannelArePublic() {
        val channels = listOf(
            channel(0, "", Channel.Role.PRIMARY, byteArrayOf(1)),
            channel(1, "anynear", Channel.Role.SECONDARY, ByteArray(32) { 1 }),
            channel(2, "Mesh Hessen", Channel.Role.SECONDARY, testCommunityPsk),
            channel(3, "Familie", Channel.Role.SECONDARY, ByteArray(32) { 2 }), // Channel of another app: stays invisible
        )
        assertEquals(
            listOf(PublicRadioChannel(PublicChannel.NEARBY, 0), PublicRadioChannel(PublicChannel.MESH_HESSEN, 2)),
            PublicChannels.of(channels, testCommunityPsk),
        )
    }

    @Test
    fun privatePrimaryAndWrongCommunityKeyAreNotPublic() {
        val channels = listOf(
            channel(0, "Privat", Channel.Role.PRIMARY, ByteArray(32) { 3 }),
            channel(2, "Mesh Hessen", Channel.Role.SECONDARY, ByteArray(32) { 4 }),
        )
        assertEquals(emptyList(), PublicChannels.of(channels, testCommunityPsk))
        // Without a known key „Mesh Hessen“ never exists.
        assertEquals(emptyList(), PublicChannels.of(listOf(channel(2, "Mesh Hessen", Channel.Role.SECONDARY, testCommunityPsk)), byteArrayOf()))
    }

    @Test
    fun meshHessenMustNotSharePosition() {
        val withPosition = channel(2, "Mesh Hessen", Channel.Role.SECONDARY, testCommunityPsk, positionPrecision = 16)
        val otherApp = channel(3, "Familie", Channel.Role.SECONDARY, ByteArray(32) { 2 }, positionPrecision = 16)
        assertEquals(listOf(withPosition), RadioProfile.channelsSharingPosition(listOf(withPosition, otherApp)))
    }

    @Test
    fun incomingMessagesAreKeptLocallyAndCountAsUnread() = runTest(UnconfinedTestDispatcher()) {
        val link = FakePublicLink()
        val file = MemoryFile()
        val radio = publicRadio(link, file)
        radio.start()
        link.air.emit(IncomingPublicText(packetId = 7, fromNodeNum = 42, channel = PublicChannel.NEARBY, text = "Hallo zusammen", timestampMillis = clock))
        link.air.emit(IncomingPublicText(packetId = 7, fromNodeNum = 42, channel = PublicChannel.NEARBY, text = "Hallo zusammen", timestampMillis = clock))
        assertEquals(1, radio.messages.value.size)
        assertEquals("Testfunk", radio.messages.value.single().fromName)
        assertEquals(1, radio.unread.value[PublicChannel.NEARBY])
        radio.markRead(PublicChannel.NEARBY)
        assertEquals(0, radio.unread.value[PublicChannel.NEARBY])
        // Survives a restart, only from the own file.
        assertEquals(1, publicRadio(link, file).messages.value.size)
    }

    @Test
    fun notificationsAreOffByDefaultAndOnlyForSwitchedOnChats() = runTest(UnconfinedTestDispatcher()) {
        val link = FakePublicLink()
        val store = InMemoryStore()
        val radio = publicRadio(link, MemoryFile(), store = store)
        val notified = mutableListOf<PublicChannel>()
        radio.onNotify = { notified += it }
        radio.start()
        assertEquals(false, radio.notify.value[PublicChannel.NEARBY])
        link.air.emit(IncomingPublicText(1, 42, PublicChannel.NEARBY, "Hallo", clock))
        assertEquals(emptyList(), notified)
        radio.setNotify(PublicChannel.NEARBY, true)
        link.air.emit(IncomingPublicText(2, 42, PublicChannel.NEARBY, "Noch jemand da?", clock))
        link.air.emit(IncomingPublicText(3, 42, PublicChannel.MESH_HESSEN, "Hallo Hessen", clock))
        assertEquals(listOf(PublicChannel.NEARBY), notified)
        assertEquals(listOf("Hallo", "Noch jemand da?"), radio.unreadMessages(PublicChannel.NEARBY).map { it.text })
        // The switch survives a restart.
        assertEquals(true, publicRadio(link, MemoryFile(), store = store).notify.value[PublicChannel.NEARBY])
        assertEquals(PublicChannel.NEARBY, PublicRadio.channelOfNotificationKey(PublicRadio.notificationKey(PublicChannel.NEARBY)))
        assertEquals(null, PublicRadio.channelOfNotificationKey("c:nearby"))
    }

    @Test
    fun reactionsAttachToTheMessageTheyReferTo() = runTest(UnconfinedTestDispatcher()) {
        val link = FakePublicLink()
        val radio = publicRadio(link, MemoryFile())
        radio.start()
        link.air.emit(IncomingPublicText(7, 42, PublicChannel.NEARBY, "Hallo", clock, hops = 2, snr = -3.5f, rssi = -90))
        // Reaction of another radio to packet 7; one to an unknown packet is dropped.
        link.air.emit(IncomingPublicText(8, 43, PublicChannel.NEARBY, "👍", clock, reactionTo = 7))
        link.air.emit(IncomingPublicText(9, 43, PublicChannel.NEARBY, "❤️", clock, reactionTo = 99))
        val message = radio.messages.value.single()
        assertEquals(2, message.hops)
        assertEquals(-90, message.rssi)
        assertEquals(listOf("👍" to listOf("n:43")), message.reactionGroups)
        // The own reaction goes to everyone as a Meshtastic reaction and counts towards the 30 seconds.
        assertIs<PublicSendResult.Sent>(radio.react(message.id, "😂"))
        assertEquals(listOf("😂" to 7), link.reactions)
        assertEquals("😂", radio.messages.value.single().ownReaction)
        assertIs<PublicSendResult.TooSoon>(radio.react(message.id, "👍"))
        assertIs<PublicSendResult.TooSoon>(radio.send(PublicChannel.NEARBY, "Und jetzt?"))
    }

    @Test
    fun sendingIsLimitedInLengthAndPace() = runTest(UnconfinedTestDispatcher()) {
        val link = FakePublicLink()
        val radio = publicRadio(link, MemoryFile())
        assertIs<PublicSendResult.TooLong>(radio.send(PublicChannel.NEARBY, "a".repeat(PublicRadio.MAX_BYTES + 1)))
        assertIs<PublicSendResult.Empty>(radio.send(PublicChannel.NEARBY, "   "))
        assertIs<PublicSendResult.Sent>(radio.send(PublicChannel.NEARBY, "Guten Morgen"))
        assertEquals(listOf("Guten Morgen"), link.sent)
        val tooSoon = radio.send(PublicChannel.NEARBY, "Noch was")
        assertIs<PublicSendResult.TooSoon>(tooSoon)
        assertEquals(30, tooSoon.seconds)
        // The limit applies per channel.
        assertIs<PublicSendResult.Sent>(radio.send(PublicChannel.MESH_HESSEN, "Hallo Hessen"))
        clock += PublicRadio.MIN_GAP_MILLIS
        assertIs<PublicSendResult.Sent>(radio.send(PublicChannel.NEARBY, "Noch was"))
        // Never „angekommen“: the last state is that of the radio.
        assertEquals(MessageStatus.RELAYED, radio.messages.value.first { it.outgoing }.status)
    }

    @Test
    fun switchingOffHidesAndForgetsEverything() = runTest(UnconfinedTestDispatcher()) {
        val link = FakePublicLink()
        val radio = publicRadio(link, MemoryFile())
        radio.start()
        link.air.emit(IncomingPublicText(1, 42, PublicChannel.NEARBY, "Hallo", clock))
        assertEquals(listOf(PublicChannel.NEARBY, PublicChannel.MESH_HESSEN), radio.available.value)
        radio.setShown(false)
        assertEquals(emptyList(), radio.available.value)
        assertEquals(emptyList(), radio.messages.value)
        link.air.emit(IncomingPublicText(2, 42, PublicChannel.NEARBY, "Noch da?", clock))
        assertEquals(emptyList(), radio.messages.value)
    }

    @Test
    fun oldMessagesAreDropped() = runTest(UnconfinedTestDispatcher()) {
        val link = FakePublicLink()
        val radio = publicRadio(link, MemoryFile())
        radio.start()
        link.air.emit(IncomingPublicText(1, 42, PublicChannel.NEARBY, "Alt", clock - PublicRadio.MAX_AGE_MILLIS - 1))
        link.air.emit(IncomingPublicText(2, 42, PublicChannel.NEARBY, "Neu", clock))
        assertEquals(listOf("Neu"), radio.messages.value.map { it.text })
        assertTrue(radio.messages.value.none { it.outgoing })
    }

    @Test
    fun communityChannelOnlyAfterYes() = runTest(UnconfinedTestDispatcher()) {
        val link = FakePublicLink(public = listOf(PublicRadioChannel(PublicChannel.NEARBY, 0)), otherSlots = 1)
        val radio = publicRadio(link, MemoryFile(), communityPsk = testCommunityPsk)
        // Slots 1–7 minus `anynear` and another app.
        assertEquals(CommunityOffer("Mesh Hessen", 5), radio.offer.value)
        radio.ensureMeshHessen()
        assertEquals(emptyList(), link.created, "ohne Ja kein Kanal")
        radio.acceptCommunity()
        assertEquals(null, radio.offer.value)
        radio.ensureMeshHessen()
        assertEquals(listOf("Mesh Hessen"), link.created)
    }

    @Test
    fun noThanksIsRemembered() = runTest(UnconfinedTestDispatcher()) {
        val store = InMemoryStore()
        val link = FakePublicLink(public = emptyList())
        val radio = publicRadio(link, MemoryFile(), communityPsk = testCommunityPsk, store = store)
        radio.declineCommunity()
        assertEquals(null, radio.offer.value)
        assertEquals(null, publicRadio(link, MemoryFile(), communityPsk = testCommunityPsk, store = store).offer.value)
        assertEquals(false, radio.community.value)
    }

    @Test
    fun noFreeSlotOffersNothingAndCanBeDismissed() = runTest(UnconfinedTestDispatcher()) {
        val link = FakePublicLink(public = emptyList(), otherSlots = 6)
        val radio = publicRadio(link, MemoryFile(), communityPsk = testCommunityPsk)
        assertEquals(0, radio.offer.value?.freeSlots)
        radio.dismissNoSlot()
        assertEquals(null, radio.offer.value)
        assertEquals(null, radio.community.value, "weggeklickt ist keine Antwort")
    }

    @Test
    fun noOfferWithoutKeyOrWhenChannelExists() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(null, publicRadio(FakePublicLink(public = emptyList()), MemoryFile()).offer.value)
        assertEquals(null, publicRadio(FakePublicLink(), MemoryFile(), communityPsk = testCommunityPsk).offer.value)
    }

    @Test
    fun switchingCommunityOffRemovesOnlyTheChannelFromTheList() = runTest(UnconfinedTestDispatcher()) {
        val link = FakePublicLink()
        val radio = publicRadio(link, MemoryFile(), communityPsk = testCommunityPsk)
        radio.setCommunity(false) // never consented: an existing channel stays
        assertEquals(emptyList(), link.removed)
        radio.setCommunity(true)
        radio.setCommunity(false)
        assertEquals(listOf("Mesh Hessen"), link.removed)
    }

    private var clock = 1_800_000_000_000L

    private fun TestScope.publicRadio(link: MeshLink, file: TextFile, communityPsk: ByteArray = byteArrayOf(), store: InMemoryStore = InMemoryStore()) =
        PublicRadio(backgroundScope, link, file, cipher = null, store = store, now = { clock }, communityPsk = communityPsk)

    private fun channel(index: Int, name: String, role: Channel.Role, psk: ByteArray, positionPrecision: Int = 0): Channel =
        Channel.Builder().also {
            it.index = index
            it.role = role
            it.settings = ChannelSettings.Builder().also { s ->
                s.name = name
                s.psk = psk.toByteString()
                s.module_settings = ModuleSettings.Builder().also { m -> m.position_precision = positionPrecision }.build()
            }.build()
        }.build()

    private class MemoryFile(var content: String? = null) : TextFile {
        override fun read(): String? = content
        override fun write(content: String) {
            this.content = content
        }
    }

    /** Radio with both public channels, without circles. */
    private class FakePublicLink(
        public: List<PublicRadioChannel> = listOf(PublicRadioChannel(PublicChannel.NEARBY, 0), PublicRadioChannel(PublicChannel.MESH_HESSEN, 2)),
        otherSlots: Int = 0,
    ) : MeshLink {
        val air = MutableSharedFlow<IncomingPublicText>()
        val sent = mutableListOf<String>()
        val created = mutableListOf<String>()
        val removed = mutableListOf<String>()
        override val publicChannels: StateFlow<List<PublicRadioChannel>> = MutableStateFlow(public)
        override val otherChannelSlots: StateFlow<Int> = MutableStateFlow(otherSlots)
        override suspend fun ensurePublicChannel(name: String, psk: ByteArray): ChannelSetupResult {
            created += name
            return ChannelSetupResult.Done
        }
        override val incomingPublic: Flow<IncomingPublicText> = air
        override fun sendPublic(text: String, channel: PublicChannel): RadioSend {
            sent += text
            return RadioSend(sent.size, flowOf(MessageStatus.HANDED_TO_RADIO, MessageStatus.RELAYED))
        }
        val reactions = mutableListOf<Pair<String, Int>>()
        override fun sendPublicReaction(emoji: String, replyTo: Int, channel: PublicChannel): Int {
            reactions += emoji to replyTo
            return 1000 + reactions.size
        }

        override val state: StateFlow<LinkState> = MutableStateFlow(LinkState.Connected)
        override val ownDevice: StateFlow<OwnDevice?> = MutableStateFlow(null)
        override val circleChannels: StateFlow<List<RadioChannel>?> = MutableStateFlow(listOf(RadioChannel(1, "anynear", ByteArray(32).toByteString())))
        override val knownNames: StateFlow<Map<Int, String>> = MutableStateFlow(mapOf(42 to "Testfunk"))
        override val incoming: Flow<IncomingText> = emptyFlow()
        override val setupCheck: StateFlow<DeviceSetupCheck?> = MutableStateFlow(null)
        override fun start(device: PairedDevice) = Unit
        override suspend fun stop() = Unit
        override suspend fun probe(device: PairedDevice) = false
        override fun send(text: String, to: Int?, channelIndex: Int): RadioSend = error("nicht im Test")
        override suspend fun sendData(payload: ByteArray, to: Int?, channelIndex: Int): RadioSend = error("nicht im Test")
        override suspend fun applyRadioProfile(nameSuffix: String, force: Boolean) = DeviceSetupResult.NotConnected
        override suspend fun renameDevice(nameSuffix: String) = DeviceSetupResult.NotConnected
        override suspend fun ensureChannel(name: String, psk: ByteArray) = ChannelSetupResult.NotConnected
        override suspend fun removeChannel(name: String): Boolean {
            removed += name
            return true
        }
        override fun ownContact(): Contact? = null
        override suspend fun registerContact(contact: Contact) = false
    }
}
