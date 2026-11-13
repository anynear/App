package app.anynear

import app.anynear.contacts.Contact
import app.anynear.app.AppController
import app.anynear.app.PlatformHooks
import app.anynear.attachments.AttachmentFiles
import app.anynear.attachments.BinaryFiles
import app.anynear.circles.CircleRepository
import app.anynear.circles.ProfileRepository
import app.anynear.contacts.ContactRepository
import app.anynear.data.DraftStore
import app.anynear.data.MessageStore
import app.anynear.data.PairedDeviceRepository
import app.anynear.images.AvatarImages
import app.anynear.mesh.ChannelSetupResult
import app.anynear.mesh.Conversation
import app.anynear.mesh.DeviceSetupCheck
import app.anynear.mesh.DeviceSetupResult
import app.anynear.mesh.IncomingData
import app.anynear.mesh.IncomingText
import app.anynear.mesh.LinkState
import app.anynear.mesh.MeshLink
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.awaitsAttachments
import app.anynear.mesh.NotReadyException
import app.anynear.mesh.OwnDevice
import app.anynear.mesh.PairedDevice
import app.anynear.mesh.RadioChannel
import app.anynear.mesh.RadioSend
import app.anynear.core.b64
import app.anynear.people.MemberDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Read receipt, direct messages and voice messages with two full app controllers against the five
 * Docker relays with ANDP 1 from Relay `compose.net.yaml` ([LocalNet]). Runs only with RELAY_E2E=1.
 */
class ReadReceiptAppTest {
    private val enabled = LocalNet.enabled

    /** Simulated radio network: what one radio sends arrives immediately at the others on the same channel. */
    private class Air {
        val radios = java.util.concurrent.CopyOnWriteArrayList<FakeRadio>()
    }

    private class FakeRadio(private val air: Air, val nodeNum: Int) : MeshLink {
        init { air.radios += this }
        override val state: StateFlow<LinkState> = MutableStateFlow(LinkState.Connected)
        override val ownDevice: StateFlow<OwnDevice?> = MutableStateFlow(OwnDevice(nodeNum, "anynear Test $nodeNum", "T$nodeNum", null))
        private val channels = MutableStateFlow<List<RadioChannel>?>(emptyList())
        override val circleChannels: StateFlow<List<RadioChannel>?> = channels
        override val knownNames: StateFlow<Map<Int, String>> = MutableStateFlow(emptyMap())
        override val incoming = emptyFlow<IncomingText>()
        val data = kotlinx.coroutines.flow.MutableSharedFlow<IncomingData>(extraBufferCapacity = 64)
        override val incomingData = data
        val sent = java.util.concurrent.atomic.AtomicInteger()
        override fun start(device: PairedDevice) = Unit
        override suspend fun stop() = Unit
        override suspend fun probe(device: PairedDevice) = true
        override fun send(text: String, to: Int?, channelIndex: Int): RadioSend = throw NotReadyException("nur ANMP")
        override suspend fun sendData(payload: ByteArray, to: Int?, channelIndex: Int): RadioSend {
            val name = channels.value.orEmpty().first { it.index == channelIndex }.name
            val id = kotlin.random.Random.nextInt()
            sent.incrementAndGet()
            air.radios.filter { it !== this && (to == null || it.nodeNum == to) }.forEach { other ->
                val index = other.channels.value.orEmpty().firstOrNull { it.name == name }?.index ?: return@forEach
                other.data.emit(IncomingData(id, nodeNum, to != null, index, payload, Clock.System.now().toEpochMilliseconds()))
            }
            return RadioSend(id, kotlinx.coroutines.flow.flowOf(MessageStatus.RELAYED))
        }
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

    private class Hooks : PlatformHooks {
        override val bluetoothPermissionGranted = MutableStateFlow(false)
        override fun requestBluetoothPermission() = Unit
        override val internetAvailable = MutableStateFlow(true)
        override fun setBackgroundConnection(active: Boolean) = Unit
        override fun share(text: String) = Unit
    }

    /** [online]: this installation reaches the relays; `false` means „kein Internet“. */
    private fun app(
        scope: CoroutineScope,
        radio: MeshLink = NoRadio(),
        online: MutableStateFlow<Boolean> = MutableStateFlow(true),
    ): AppController {
        val store = ConcurrentStore()
        if (radio is FakeRadio) PairedDeviceRepository(store).pair(PairedDevice("tcp:test-${radio.nodeNum}", "anynear Test"))
        return testController(scope, radio, Hooks(), LocalNet.config(), LocalNet.connector(online), store = store)
    }

    /**
     * Direct conversation of [from] with [to], the way [from] knows it: via the device from the contact link or, once
     * the key chain is there, via the person (ADR 0042).
     */
    private fun directTo(from: AppController, to: AppController): Conversation =
        from.findPerson("d:" + to.myDeviceId.value)?.peer?.let { Conversation.Direct(it) } ?: Conversation.Direct("d:" + to.myPersonId.value)

    /** Direct conversation with [other], whether via their device or person. */
    private fun Conversation.isWith(other: AppController): Boolean =
        this is Conversation.Direct && (peer == "d:" + other.myDeviceId.value || peer == "d:" + other.myPersonId.value)

    private suspend fun eventually(what: String, check: () -> Boolean) {
        val t0 = System.currentTimeMillis()
        withTimeout(60.seconds) { while (!check()) delay(200) }
        println("ok: $what (${System.currentTimeMillis() - t0} ms)")
    }

    private fun run(benOpensFirst: Boolean, withRadio: Boolean = false, direct: Boolean = false) = runBlocking {
        if (!enabled) return@runBlocking
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val air = Air()
            val anna = if (withRadio) app(scope, FakeRadio(air, 101)) else app(scope)
            val ben = if (withRadio) app(scope, FakeRadio(air, 202)) else app(scope)
            check(anna.createCircle("Testkreis", "Anna") == null)
            eventually("Kreis angelegt") { anna.circles.value.isNotEmpty() }
            val circleId = anna.circles.value.single().id
            check(ben.joinCircle(anna.inviteLink(circleId)!!, "Ben") == null)
            eventually("beide kennen sich") {
                anna.members.value.any { it.circleId == circleId } && ben.members.value.any { it.circleId == circleId }
            }
            // Direct conversations are tied to the person, not to the device (ADR 0042).
            val circle: Conversation = if (direct) Conversation.Direct("d:" + ben.myPersonId.value!!) else Conversation.Circle(circleId)
            val benSide: Conversation = if (direct) Conversation.Direct("d:" + anna.myPersonId.value!!) else circle
            if (benOpensFirst) ben.setOpenConversation(benSide)
            if (withRadio) eventually("Kreis-Kanal auf beiden Funkgeräten") { air.radios.all { it.circleChannels.value.orEmpty().isNotEmpty() } }
            anna.send(circle, "Hallo Ben")
            eventually("Ben hat sie") { ben.messages.value.any { it.text == "Hallo Ben" } }
            if (withRadio) println("Ben: Wege " + ben.messages.value.single { it.text == "Hallo Ben" }.via)
            if (!benOpensFirst) ben.setOpenConversation(benSide)
            try {
                eventually("bei Anna gelesen") {
                    anna.messages.value.single { it.text == "Hallo Ben" }.status == MessageStatus.READ
                }
            } finally {
                val a = anna.messages.value.single { it.text == "Hallo Ben" }
                val b = ben.messages.value.single { it.text == "Hallo Ben" }
                println("Anna: status=${a.status} via=${a.via} recipients=${a.recipients} delivered=${a.deliveredTo} readBy=${a.readBy} readVia=${a.readVia}")
                println("Ben: via=${b.via} unread=${b.unread} remoteId=${b.remoteId} fromPeer=${b.fromPeer}")
            }
        } finally {
            scope.cancel()
        }
    }

    /**
     * Two installations without a circle (setup via „Erst einmal umsehen“): Anna adds Ben via his
     * contact link (only after confirmation), writes to him, Ben receives without a contact and replies,
     * Anna sees the delivery. Only via the mailboxes, without a radio.
     */
    @Test
    fun directMessageWithoutAnyCircle() = runBlocking {
        if (!enabled) return@runBlocking
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val anna = app(scope)
            val ben = app(scope)
            anna.setOwnName("Testperson Anna")
            ben.setOwnName("Testperson Ben")
            anna.finishSetup()
            ben.finishSetup()
            eventually("Kontaktlink von Ben") { ben.ownContactLink.value != null }
            check(anna.receiveLink(ben.ownContactLink.value!!) == null)
            check(anna.people.value.none { it.peer == "d:" + ben.myDeviceId.value }) { "vor der Bestätigung nichts gespeichert" }
            check(anna.acceptPendingContact() == null)
            eventually("Ben ist Kontakt") { anna.findPerson("d:" + ben.myDeviceId.value) != null }
            fun toBen() = directTo(anna, ben)
            fun toAnna() = directTo(ben, anna)
            anna.send(toBen(), "Hallo Ben, ohne Kreis")
            eventually("Ben hat sie") { ben.messages.value.any { it.text == "Hallo Ben, ohne Kreis" && it.conversation.isWith(anna) } }
            eventually("bei Anna zugestellt") { anna.messages.value.single { it.text == "Hallo Ben, ohne Kreis" }.status?.arrived == true }
            ben.send(toAnna(), "Hallo zurück")
            eventually("Anna hat die Antwort") { anna.messages.value.any { it.text == "Hallo zurück" && it.conversation.isWith(ben) } }
            eventually("bei Ben zugestellt") { ben.messages.value.single { it.text == "Hallo zurück" }.status?.arrived == true }
            check(anna.circles.value.isEmpty() && ben.circles.value.isEmpty()) { "beide ohne Kreis" }
            val a = anna.messages.value.single { it.text == "Hallo Ben, ohne Kreis" }
            val b = ben.messages.value.single { it.text == "Hallo zurück" }
            println("Anna → Ben: status=${a.status} via=${a.via} · Ben → Anna: status=${b.status} via=${b.via}")
        } finally {
            scope.cancel()
        }
    }

    /**
     * Contact scanned, „Hinzufügen und schreiben“: the conversation opens with Ben's device ID, shortly after Anna's
     * core lists Ben under his person ID (ADR 0042). Sending in the still open conversation works anyway and switches
     * there (formerly: „Diese Person ist gerade auf keinem Weg erreichbar.“, it only worked after Back and reopening).
     */
    @Test
    fun scannedContactCanBeWrittenInTheConversationOpenedRightAway() = runBlocking {
        if (!enabled) return@runBlocking
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val anna = app(scope)
            val ben = app(scope)
            anna.setOwnName("Testperson Anna")
            ben.setOwnName("Testperson Ben")
            anna.finishSetup()
            ben.finishSetup()
            eventually("Kontaktlink von Ben") { ben.ownContactLink.value != null && ben.myPersonId.value != null }
            check(anna.receiveLink(ben.ownContactLink.value!!) == null)
            check(anna.acceptPendingContact(openChat = true) == null)
            val opened = Conversation.Direct("d:" + ben.myDeviceId.value)
            val byPerson = Conversation.Direct("d:" + ben.myPersonId.value)
            check(opened != byPerson) { "Ben hat eine eigene Personen-ID" }
            eventually("Anna führt Ben unter seiner Personen-ID") { anna.findPerson(byPerson.peer) != null }
            check(anna.send(opened, "Hallo Ben, eben gescannt") is AppController.SendResult.Sent) { "nicht abgelehnt" }
            check(anna.openRequest.value == byPerson) { "Gespräch wechselt zur Person" }
            eventually("Ben hat sie") { ben.messages.value.any { it.text == "Hallo Ben, eben gescannt" && it.conversation.isWith(anna) } }
            check(anna.messages.value.single { it.text == "Hallo Ben, eben gescannt" }.conversation == byPerson) { "im Gespräch der Person" }
        } finally {
            scope.cancel()
        }
    }

    /**
     * Contact removed (ADR 0035 §2): Anna revokes Ben's personal grant, after that Ben can no longer write to her
     * directly, the conversation stays. Via the contact link the two connect again.
     */
    @Test
    fun removedContactCannotWriteUntilAddedAgain() = runBlocking {
        if (!enabled) return@runBlocking
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val anna = app(scope)
            val ben = app(scope)
            anna.setOwnName("Testperson Anna")
            ben.setOwnName("Testperson Ben")
            anna.finishSetup()
            ben.finishSetup()
            eventually("Kontaktlinks") { anna.ownContactLink.value != null && ben.ownContactLink.value != null }
            anna.receiveLink(ben.ownContactLink.value!!)
            anna.acceptPendingContact(openChat = false)
            ben.receiveLink(anna.ownContactLink.value!!)
            ben.acceptPendingContact(openChat = false)
            eventually("beide sind Kontakte") {
                anna.findPerson("d:" + ben.myDeviceId.value) != null && ben.findPerson("d:" + anna.myDeviceId.value) != null
            }
            val benId = ben.myDeviceId.value!!
            fun toAnna() = directTo(ben, anna)
            fun toBen() = directTo(anna, ben)
            ben.send(toAnna(), "Erste Nachricht")
            eventually("Anna hat die erste") { anna.messages.value.any { it.text == "Erste Nachricht" } }
            anna.removeDirectContact(benId)
            eventually("entfernt") { !anna.isDirectContact(benId) }
            check(anna.messages.value.any { it.text == "Erste Nachricht" && it.conversation.isWith(ben) }) { "Verlauf bleibt" }
            delay(2_000) // Revocation is at the relays
            ben.send(toAnna(), "Zweite Nachricht")
            delay(5_000)
            check(anna.messages.value.none { it.text == "Zweite Nachricht" }) { "nach dem Entfernen kommt nichts mehr an" }
            // Add again: Anna issues a new grant, Ben can write again.
            anna.receiveLink(ben.ownContactLink.value!!)
            anna.acceptPendingContact(openChat = false)
            eventually("wieder Kontakt") { anna.isDirectContact(benId) }
            delay(3_000)
            ben.send(toAnna(), "Dritte Nachricht")
            eventually("Anna hat die dritte") { anna.messages.value.any { it.text == "Dritte Nachricht" && it.conversation.isWith(ben) } }
        } finally {
            scope.cancel()
        }
    }

    // --- Voice messages (ADR 0030) -------------------------------------------------------------

    private val longTranscript =
        "Hallo Ben, ich bin gut angekommen, der Zug hatte nur zehn Minuten Verspätung und ich melde mich heute Abend noch einmal ausführlich bei dir"

    /** Made-up recording: random bytes, nothing is played back in the test. */
    private val recording = kotlin.random.Random(30).nextBytes(24_000)

    private fun voice(millis: Long = 42_000) =
        app.anynear.attachments.DraftAttachment(app.anynear.attachments.AttachmentKind.AUDIO, recording, durationMillis = millis)

    private suspend fun circleOf(anna: AppController, ben: AppController, air: Air?): Conversation {
        check(anna.createCircle("Testkreis", "Anna") == null)
        eventually("Kreis angelegt") { anna.circles.value.isNotEmpty() }
        val circleId = anna.circles.value.single().id
        check(ben.joinCircle(anna.inviteLink(circleId)!!, "Ben") == null)
        eventually("beide kennen sich") {
            anna.members.value.any { it.circleId == circleId } && ben.members.value.any { it.circleId == circleId }
        }
        if (air != null) eventually("Kreis-Kanal auf beiden Funkgeräten") { air.radios.all { it.circleChannels.value.orEmpty().isNotEmpty() } }
        return Conversation.Circle(circleId)
    }

    /**
     * Anna speaks a long message, the recognition delivers the text. The shortened beginning goes over radio,
     * the recording over the internet; Ben has one message with recording, marked preview and length.
     */
    @Test
    fun voiceWithPreviewOverRadioAndInternet() = runBlocking {
        if (!enabled) return@runBlocking
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val air = Air()
            val annaRadio = FakeRadio(air, 101)
            val anna = app(scope, annaRadio)
            val ben = app(scope, FakeRadio(air, 202))
            val circle = circleOf(anna, ben, air)
            val transcript = app.anynear.attachments.VoiceTranscript(longTranscript, complete = true)
            val expected = anna.voicePreview(circle, transcript)!!
            check(expected.endsWith("…") && longTranscript.startsWith(expected.removeSuffix("…"))) { expected }
            check(anna.send(circle, "", listOf(voice()), transcript) == AppController.SendResult.Sent)
            val sent = anna.messages.value.single { it.attachments.isNotEmpty() }
            check(sent.text == expected) { "Text ist die Vorschau: ${sent.text}" }
            check(sent.attachments.single().transcribed) { "als Vorschau gekennzeichnet" }
            eventually("Funkpaket raus") { annaRadio.sent.get() > 0 }
            eventually("Ben hat Text und Aufnahme") {
                ben.messages.value.any { m -> m.text == expected && m.attachments.any { it.state == app.anynear.attachments.AttachmentState.READY } }
            }
            val got = ben.messages.value.filter { it.attachments.isNotEmpty() || it.text.contains("…") }
            check(got.size == 1) { "eine Nachricht, nicht zwei: ${got.map { it.text to it.via }}" }
            val a = got.single().attachments.single()
            check(a.kind == app.anynear.attachments.AttachmentKind.AUDIO && a.transcribed && a.durationMillis == 42_000L) { "$a" }
            check(ben.attachmentBytes(a.blobId)!!.contentEquals(recording)) { "Aufnahme unverändert" }
            println("Ben: via=${got.single().via} text=${got.single().text}")
        } finally {
            scope.cancel()
        }
    }

    /**
     * Ben has no internet right now, only radio: he reads the preview immediately („🎤 …“, arrives complete).
     * As soon as he has internet again, the recording completes the same message.
     */
    @Test
    fun voicePreviewReachesRadioOnlyReceiverFirst() = runBlocking {
        if (!enabled) return@runBlocking
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val air = Air()
            val anna = app(scope, FakeRadio(air, 101))
            val benNet = MutableStateFlow(true)
            val ben = app(scope, FakeRadio(air, 202), benNet)
            val circle = circleOf(anna, ben, air)
            benNet.value = false
            val transcript = app.anynear.attachments.VoiceTranscript("Bin gut angekommen", complete = true)
            check(anna.send(circle, "", listOf(voice(12_000)), transcript) == AppController.SendResult.Sent)
            eventually("Ben liest die Vorschau über Funk") { ben.messages.value.any { it.text == "🎤 Bin gut angekommen" } }
            val radioOnly = ben.messages.value.single { it.text.startsWith("🎤") }
            check(radioOnly.awaitsAttachments) { "kündigt die Aufnahme an" }
            benNet.value = true
            eventually("Aufnahme ergänzt dieselbe Nachricht") {
                ben.messages.value.any { m -> m.attachments.any { it.state == app.anynear.attachments.AttachmentState.READY } }
            }
            val all = ben.messages.value.filter { it.system == null }
            check(all.size == 1) { "eine Nachricht: ${all.map { it.text to it.via }}" }
            check(all.single().text == "Bin gut angekommen" && all.single().attachments.single().transcribed) { all.single().text }
        } finally {
            scope.cancel()
        }
    }

    /** Without recognition and text: over radio goes the announcement with length, the recording without a marker. */
    @Test
    fun voiceWithoutPreviewAnnouncesItself() = runBlocking {
        if (!enabled) return@runBlocking
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val air = Air()
            val anna = app(scope, FakeRadio(air, 101))
            val benNet = MutableStateFlow(true)
            val ben = app(scope, FakeRadio(air, 202), benNet)
            val circle = circleOf(anna, ben, air)
            benNet.value = false
            check(anna.send(circle, "", listOf(voice(65_000))) == AppController.SendResult.Sent)
            eventually("Ben sieht die Ankündigung") { ben.messages.value.any { it.text == "🎤 Sprachnachricht 1:05" } }
            benNet.value = true
            eventually("Aufnahme da") { ben.messages.value.any { m -> m.attachments.any { it.state == app.anynear.attachments.AttachmentState.READY } } }
            val m = ben.messages.value.single { it.system == null }
            check(m.text.isEmpty() && !m.attachments.single().transcribed && m.attachments.single().durationMillis == 65_000L) { "${m.text} ${m.attachments}" }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun readWhenChatIsAlreadyOpen() = run(benOpensFirst = true)

    @Test
    fun readWhenChatIsOpenedLater() = run(benOpensFirst = false)

    @Test
    fun readOverRadioAndInternetWhenChatIsAlreadyOpen() = run(benOpensFirst = true, withRadio = true)

    @Test
    fun directReadOverRadioAndInternetWhenChatIsAlreadyOpen() = run(benOpensFirst = true, withRadio = true, direct = true)

    @Test
    fun directReadWhenChatIsAlreadyOpen() = run(benOpensFirst = true, direct = true)

    @Test
    fun readOverRadioAndInternetWhenChatIsOpenedLater() = run(benOpensFirst = false, withRadio = true)
}
