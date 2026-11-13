package app.anynear

import app.anynear.core.b64
import app.anynear.contacts.Contact
import app.anynear.anmp.AnmpContent
import app.anynear.anmp.AnmpKind
import app.anynear.anmp.core.AnmpCore
import app.anynear.anmp.core.AnmpPlatform
import app.anynear.app.AppController
import app.anynear.app.NoOpPlatformHooks
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.AttachmentState
import app.anynear.attachments.DraftAttachment
import app.anynear.chats.key
import app.anynear.core.CoreBlobs
import app.anynear.core.CoreKeyValues
import app.anynear.core.CoreSecrets
import app.anynear.core.LocalKey
import app.anynear.core.WakeTexts
import app.anynear.data.EncryptedKeyValueStore
import app.anynear.i18n.texts
import app.anynear.mesh.ChannelSetupResult
import app.anynear.mesh.Conversation
import app.anynear.mesh.DeviceSetupCheck
import app.anynear.mesh.DeviceSetupResult
import app.anynear.mesh.IncomingData
import app.anynear.mesh.IncomingText
import app.anynear.mesh.LinkState
import app.anynear.mesh.MeshLink
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.NotReadyException
import app.anynear.mesh.OwnDevice
import app.anynear.mesh.PairedDevice
import app.anynear.mesh.RadioChannel
import app.anynear.mesh.RadioSend
import app.anynear.data.PairedDeviceRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Circles with full app controllers against the five Docker relays with ANDP 1 ([LocalNet]): attachments,
 * removing, circle name, earlier messages for a new member, passing on at the shared radio (ADR 0022) and
 * the text of the wake-up on iOS. Runs only with RELAY_E2E=1. All persons are freshly created test devices.
 */
class CircleAppEndToEndTest {
    private class Device(val controller: AppController, val secrets: ConcurrentSecrets, val store: ConcurrentStore, val radio: TestRadio?)

    /** Radio where the test feeds in incoming packets itself. */
    private class TestRadio(nodeNum: Int) : MeshLink {
        override val state: StateFlow<LinkState> = MutableStateFlow(LinkState.Connected)
        override val ownDevice: StateFlow<OwnDevice?> = MutableStateFlow(OwnDevice(nodeNum, "anynear Test $nodeNum", "T$nodeNum", null))
        private val channels = MutableStateFlow<List<RadioChannel>?>(emptyList())
        override val circleChannels: StateFlow<List<RadioChannel>?> = channels
        override val knownNames: StateFlow<Map<Int, String>> = MutableStateFlow(emptyMap())
        override val incoming = emptyFlow<IncomingText>()
        val data = MutableSharedFlow<IncomingData>(extraBufferCapacity = 16)
        override val incomingData = data
        override fun start(device: PairedDevice) = Unit
        override suspend fun stop() = Unit
        override suspend fun probe(device: PairedDevice) = true
        override fun send(text: String, to: Int?, channelIndex: Int): RadioSend = throw NotReadyException("nur Empfang")
        override suspend fun sendData(payload: ByteArray, to: Int?, channelIndex: Int): RadioSend = throw NotReadyException("nur Empfang")
        override val setupCheck: StateFlow<DeviceSetupCheck?> = MutableStateFlow(null)
        override suspend fun applyRadioProfile(nameSuffix: String, force: Boolean) = DeviceSetupResult.Done
        override suspend fun renameDevice(nameSuffix: String) = DeviceSetupResult.Done
        override suspend fun ensureChannel(name: String, psk: ByteArray): ChannelSetupResult {
            val list = channels.value.orEmpty().filter { it.name != name }
            channels.value = list + RadioChannel(list.size + 1, name, okio.ByteString.of(*psk))
            return ChannelSetupResult.Done
        }
        override suspend fun removeChannel(name: String) = true
        override fun ownContact(): Contact? = null
        override suspend fun registerContact(contact: Contact) = true
    }

    private class NoRadio : MeshLink {
        override val state: StateFlow<LinkState> = MutableStateFlow(LinkState.NoDevice)
        override val ownDevice: StateFlow<OwnDevice?> = MutableStateFlow(null)
        override val circleChannels: StateFlow<List<RadioChannel>?> = MutableStateFlow(null)
        override val knownNames: StateFlow<Map<Int, String>> = MutableStateFlow(emptyMap())
        override val incoming = emptyFlow<IncomingText>()
        override fun start(device: PairedDevice) = Unit
        override suspend fun stop() = Unit
        override suspend fun probe(device: PairedDevice) = false
        override fun send(text: String, to: Int?, channelIndex: Int): RadioSend = throw NotReadyException("kein Funkgerät")
        override suspend fun sendData(payload: ByteArray, to: Int?, channelIndex: Int): RadioSend = throw NotReadyException("kein Funkgerät")
        override val setupCheck: StateFlow<DeviceSetupCheck?> = MutableStateFlow(null)
        override suspend fun applyRadioProfile(nameSuffix: String, force: Boolean) = DeviceSetupResult.NotConnected
        override suspend fun renameDevice(nameSuffix: String) = DeviceSetupResult.NotConnected
        override suspend fun ensureChannel(name: String, psk: ByteArray) = ChannelSetupResult.NotConnected
        override suspend fun removeChannel(name: String) = false
        override fun ownContact(): Contact? = null
        override suspend fun registerContact(contact: Contact) = false
    }

    private fun device(scope: CoroutineScope, name: String, radio: TestRadio? = null, online: MutableStateFlow<Boolean> = MutableStateFlow(true)): Device {
        val secrets = ConcurrentSecrets()
        val store = ConcurrentStore()
        if (radio != null) PairedDeviceRepository(store).pair(PairedDevice("tcp:test-$name", "anynear Test"))
        val c = testController(scope, radio ?: NoRadio(), NoOpPlatformHooks(), LocalNet.config(), LocalNet.connector(online), store = store, secrets = secrets)
        c.setOwnName("Testperson $name")
        c.finishSetup()
        return Device(c, secrets, store, radio)
    }

    private suspend fun eventually(what: String, check: () -> Boolean) {
        val t0 = System.currentTimeMillis()
        withTimeout(60.seconds) { while (!check()) delay(200) }
        println("ok: $what (${System.currentTimeMillis() - t0} ms)")
    }

    private fun e2e(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        if (!LocalNet.enabled) return@runBlocking
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> println("Hintergrund: $e") })
        try {
            scope.block()
        } finally {
            scope.cancel()
        }
    }

    /** Anna founds, the others join; waits until all know each other. */
    private suspend fun circleOf(founder: Device, vararg others: Device): String {
        check(founder.controller.createCircle("Testkreis") == null)
        eventually("Kreis angelegt") { founder.controller.circles.value.isNotEmpty() }
        val id = founder.controller.circles.value.single().id
        others.forEach { check(it.controller.joinCircle(founder.controller.inviteLink(id)!!) == null) }
        val all = listOf(founder) + others
        eventually("alle kennen sich") {
            all.all { d -> d.controller.membersOf(id).size == all.size - 1 }
        }
        return id
    }

    private val photo = kotlin.random.Random(7).nextBytes(150_000)

    @Test
    fun attachmentsInCircleAndDirect() = e2e {
        val anna = device(this, "Anna")
        val ben = device(this, "Ben")
        val id = circleOf(anna, ben)
        val circle = Conversation.Circle(id)
        anna.controller.send(circle, "Ein Foto", listOf(DraftAttachment(AttachmentKind.IMAGE, photo, width = 40, height = 30)))
        eventually("Ben hat das Foto") {
            ben.controller.messages.value.any { m -> m.text == "Ein Foto" && m.attachments.singleOrNull()?.state == AttachmentState.READY }
        }
        val got = ben.controller.messages.value.single { it.text == "Ein Foto" }.attachments.single()
        check(ben.controller.attachmentBytes(got.blobId)!!.contentEquals(photo)) { "Foto unverändert" }
        eventually("bei Anna angekommen") { anna.controller.messages.value.single { it.text == "Ein Foto" }.status?.arrived == true }

        val toAnna = Conversation.Direct("d:" + anna.controller.myPersonId.value)
        val pdf = "%PDF-1.4 ausgedacht".encodeToByteArray() + ByteArray(5_000)
        ben.controller.send(toAnna, "Ein PDF", listOf(DraftAttachment(AttachmentKind.PDF, pdf, name = "Plan.pdf")))
        eventually("Anna hat das PDF") {
            anna.controller.messages.value.any { m -> m.text == "Ein PDF" && m.attachments.singleOrNull()?.state == AttachmentState.READY }
        }
        val a = anna.controller.messages.value.single { it.text == "Ein PDF" }.attachments.single()
        check(a.name == "Plan.pdf" && anna.controller.attachmentBytes(a.blobId)!!.contentEquals(pdf))
    }

    @Test
    fun removedMemberGetsNoticeAndNoLongerReads() = e2e {
        val anna = device(this, "Anna")
        val ben = device(this, "Ben")
        val clara = device(this, "Clara")
        val id = circleOf(anna, ben, clara)
        val circle = Conversation.Circle(id)
        val claraId = clara.controller.myDeviceId.value!!
        anna.controller.removeMember(id, claraId)
        eventually("Anna sieht die Zeile") { anna.controller.messages.value.any { it.text == texts.status.lineYouRemoved("Testperson Clara") } }
        eventually("Ben sieht die Zeile") { ben.controller.messages.value.any { it.text == texts.status.lineRemoved("Testperson Anna", "Testperson Clara") } }
        eventually("Clara ist entfernt") {
            clara.controller.circles.value.singleOrNull()?.removed == true &&
                clara.controller.messages.value.any { it.text == texts.status.lineRemovedYou("Testperson Anna") }
        }
        eventually("Ben hat den neuen Schlüssel") { ben.controller.circles.value.single().epoch == 1 }
        eventually("Clara ist bei Ben kein Mitglied mehr") { ben.controller.membersOf(id).none { it.deviceId == claraId } }
        delay(2_000)
        check(ben.controller.membersOf(id).none { it.deviceId == claraId }) { "Clara kommt nicht über den alten space zurück" }
        anna.controller.send(circle, "Nach dem Entfernen")
        eventually("Ben liest weiter") { ben.controller.messages.value.any { it.text == "Nach dem Entfernen" } }
        delay(3_000)
        check(clara.controller.messages.value.none { it.text == "Nach dem Entfernen" }) { "Clara liest nichts Neues" }
    }

    @Test
    fun circleNameReachesEveryone() = e2e {
        val anna = device(this, "Anna")
        val ben = device(this, "Ben")
        val id = circleOf(anna, ben)
        check(anna.controller.renameCircle(id, "Familie Test") == null)
        eventually("Ben hat den neuen Namen") { ben.controller.circles.value.single().name == "Familie Test" }
        eventually("mit Zeile") { ben.controller.messages.value.any { it.text == texts.status.lineRenamed("Testperson Anna", "Familie Test") } }
        // The app shrinks images with the platform's image functions; those do not exist in the host test.
        // Storing and loading images is checked by the ANMP core (CircleFlowTest in the ANMP repo).
    }

    @Test
    fun newMemberGetsEarlierMessages() = e2e {
        val anna = device(this, "Anna")
        val ben = device(this, "Ben")
        val id = circleOf(anna, ben)
        val circle = Conversation.Circle(id)
        anna.controller.send(circle, "Erste")
        ben.controller.send(circle, "Zweite")
        eventually("beide da") { listOf(anna, ben).all { d -> d.controller.messages.value.count { it.text in setOf("Erste", "Zweite") } == 2 } }
        val clara = device(this, "Clara")
        check(clara.controller.joinCircle(anna.controller.inviteLink(id)!!) == null)
        // What is still in the circle's space (30 days) Clara reads there herself; older items via history (ADR 0014).
        eventually("Clara hat beide") {
            clara.controller.messages.value.filter { it.system == null }.map { it.text }.sorted() == listOf("Erste", "Zweite")
        }
        eventually("Verlauf vollständig") { clara.controller.historyHint(id) == null }
    }

    /**
     * ADR 0051: Clara does not know Anna and Ben, but hears Anna's direct packet to Ben over radio and passes it to the
     * radio inbox. Ben has no radio, fetches it there and confirms receipt.
     */
    @Test
    fun strangerUplinksAndRecipientGetsItFromTheRadioInbox() = e2e {
        val anna = device(this, "Anna")
        val ben = device(this, "Ben")
        val clara = device(this, "Clara", TestRadio(303))
        val id = circleOf(anna, ben)
        val benMember = anna.controller.membersOf(id).single { it.deviceId == ben.controller.myDeviceId.value }
        // Anna does not have her radio in the app right now: the packet is created as over radio, with her keys.
        val mid = b64.encode(ByteArray(8) { 9 })
        val packet = withTimeout(10.seconds) {
            var p: ByteArray? = null
            while (p == null) {
                p = anna.controller.core.radioTextPacket(null, benMember.deviceId, mid, System.currentTimeMillis(), "Über eine fremde App")
                if (p == null) delay(50)
            }
            p
        }
        eventually("Clara ist online") { clara.controller.onlineReachable.value }
        clara.radio!!.data.emit(IncomingData(4711, 101, direct = false, channelIndex = 1, payload = packet, timestampMillis = System.currentTimeMillis()))
        // Conversations with persons run via the person ID (ADR 0042).
        val fromAnna = Conversation.Direct("d:" + anna.controller.myPersonId.value)
        eventually("Ben hat sie aus der radio inbox") { ben.controller.messages.value.any { it.text == "Über eine fremde App" && it.conversation == fromAnna } }
        check(clara.controller.messages.value.none { it.text == "Über eine fremde App" }) { "Clara liest nicht mit" }
    }

    /** iOS wake-up: the extension fetches the same stores with its own core and builds the text (ADR 0009). */
    @Test
    fun wakeTextNamesCircleAndSender() = e2e {
        val anna = device(this, "Anna")
        val benOnline = MutableStateFlow(true)
        val ben = device(this, "Ben", online = benOnline)
        val id = circleOf(anna, ben)
        // The app itself fetches nothing right now (as in the background on iOS), only the extension.
        benOnline.value = false
        delay(500)
        anna.controller.send(Conversation.Circle(id), "Weck Ben")
        eventually("bei den Relays") { anna.controller.messages.value.single { it.text == "Weck Ben" }.status == app.anynear.mesh.MessageStatus.ONLINE }
        val cipher = LocalKey.existingCipher(ben.secrets)!!
        val store = EncryptedKeyValueStore(ben.store, cipher)
        val extension = AnmpCore(
            this, LocalNet.config(),
            AnmpPlatform(CoreSecrets(ben.secrets), CoreKeyValues(store), CoreBlobs(TestFiles(), cipher), connector = LocalNet.connector(MutableStateFlow(true))),
        )
        extension.open()
        val wake = WakeTexts(extension, store).contentFor(null)
        checkNotNull(wake) { "Weckruf ohne Text" }
        check(wake.title == "Testkreis" && wake.body == "Testperson Anna: Weck Ben") { "$wake" }
        check(wake.key == Conversation.Circle(id).key())
        // The same wake-up from a second relay: the same text.
        check(WakeTexts(extension, store).contentFor(wake.objectId) == wake)
    }

    /**
     * Anna writes on her second device (ADR 0042), for instance in the web app. Ben's confirmation also arrives on the
     * phone; there the copy must then show „angekommen“, in the circle as in the direct conversation.
     */
    @Test
    fun ownCopyFromOtherDeviceShowsArrived() = e2e {
        val phone = device(this, "Anna")
        val ben = device(this, "Ben")
        val id = circleOf(phone, ben)

        // Pair a second device and, like the app after the handover, restart with the same keys.
        val fresh = device(this, "Tablet")
        val link = fresh.controller.pairingOffer("Tablet")
        check(phone.controller.addDevice(link, null) == null)
        checkNotNull(fresh.controller.core.awaitPairing(30.seconds)) { "Übergabe" }
        fresh.controller.core.stop()
        val tablet = testController(this, NoRadio(), NoOpPlatformHooks(), LocalNet.config(), LocalNet.connector(MutableStateFlow(true)), store = fresh.store, secrets = fresh.secrets)
        tablet.finishSetup()
        eventually("Tablet ist Anna") { tablet.myPersonId.value == phone.controller.myPersonId.value }
        eventually("Tablet kennt den Kreis mit Ben") { tablet.membersOf(id).any { it.personId == ben.controller.myPersonId.value } }

        tablet.send(Conversation.Circle(id), "Vom Tablet in den Kreis")
        eventually("Ben hat sie") { ben.controller.messages.value.any { it.text == "Vom Tablet in den Kreis" } }
        eventually("auf dem Handy angekommen") {
            phone.controller.messages.value.singleOrNull { it.text == "Vom Tablet in den Kreis" }?.status == MessageStatus.CONFIRMED
        }

        val toBen = Conversation.Direct("d:" + ben.controller.myPersonId.value)
        eventually("Tablet kann Ben schreiben") { tablet.send(toBen, "Vom Tablet an Ben") == AppController.SendResult.Sent }
        eventually("Ben hat die Direktnachricht") { ben.controller.messages.value.any { it.text == "Vom Tablet an Ben" } }
        eventually("Direktnachricht auf dem Handy angekommen") {
            phone.controller.messages.value.singleOrNull { it.text == "Vom Tablet an Ben" }?.status == MessageStatus.CONFIRMED
        }
    }

}
