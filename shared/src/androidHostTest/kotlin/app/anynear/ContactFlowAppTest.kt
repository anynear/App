package app.anynear

import app.anynear.contacts.Contact
import app.anynear.app.AppController
import app.anynear.app.NoOpPlatformHooks
import app.anynear.app.PlatformHooks
import app.anynear.attachments.AttachmentFiles
import app.anynear.attachments.BinaryFiles
import app.anynear.chats.InviteCard
import app.anynear.chats.findInviteLinks
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
import app.anynear.mesh.IncomingText
import app.anynear.mesh.LinkState
import app.anynear.mesh.MeshLink
import app.anynear.mesh.NotReadyException
import app.anynear.mesh.OwnDevice
import app.anynear.mesh.PairedDevice
import app.anynear.mesh.PeerId
import app.anynear.mesh.RadioChannel
import app.anynear.mesh.RadioSend
import app.anynear.core.b64
import app.anynear.people.Member
import app.anynear.people.MemberDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CoroutineScope
import app.anynear.anmp.core.MemoryBlobStore
import app.anynear.anmp.core.MemoryKeyValueStore
import app.anynear.anmp.core.MemorySecretStore
import app.anynear.anmp.core.AnmpCore
import app.anynear.anmp.core.AnmpCoreConfig
import app.anynear.anmp.core.AnmpPlatform
import app.anynear.i18n.texts
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Contact flows with a full app controller, without relays and without a radio:
 * confirmation when pasting a contact link, „Nur hinzufügen“, removing a self-saved
 * contact and what applies afterwards when exchanging messages. All persons are freshly created test devices.
 */
class ContactFlowAppTest {
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

    private class App(val scope: CoroutineScope, val controller: AppController)

    private fun app(scope: CoroutineScope, hooks: PlatformHooks): App = runBlocking {
        // No relays: this is only about what is stored on the device.
        val controller = testController(
            scope, NoRadio(), hooks, LocalNet.offlineConfig(), LocalNet.offlineConnector,
            // Never finds anything (instead of ending empty, which triggers an exception in the device scan).
            scanNetwork = { flow { awaitCancellation() } },
        )
        withTimeout(10.seconds) { while (controller.myDeviceId.value == null) delay(20) }
        controller.setOwnName("Testperson Anna")
        controller.finishSetup()
        App(scope, controller)
    }

    private fun eventually(check: () -> Boolean) = runBlocking { withTimeout(10.seconds) { while (!check()) delay(50) } }

    /** Another test device (own ANMP core without network): device ID and contact link. */
    private fun App.otherDevice(name: String): Pair<String, String> = runBlocking {
        val core = AnmpCore(
            scope, LocalNet.offlineConfig(),
            AnmpPlatform(MemorySecretStore(), MemoryKeyValueStore(), MemoryBlobStore(), connector = LocalNet.offlineConnector),
        )
        val info = core.open()
        core.setProfile(name)
        info.deviceId to core.ownContactLink()
    }

    /** Another test device founds [circle]: its invitation link, created by [inviter]. */
    private fun App.otherInvite(inviter: String, circle: String): String = runBlocking {
        val core = AnmpCore(
            scope, LocalNet.offlineConfig(),
            AnmpPlatform(MemorySecretStore(), MemoryKeyValueStore(), MemoryBlobStore(), connector = LocalNet.offlineConnector),
        )
        core.open()
        core.setProfile(inviter)
        core.inviteLink(core.createCircle(circle).id)!!
    }

    private fun withApp(hooks: PlatformHooks = NoOpPlatformHooks(), block: (App) -> Unit) {
        // Errors in the background of this app stay here and do not reach other tests.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
        try {
            block(app(scope, hooks))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun receptionRunsWithoutCircleOnceSetupIsDone() = withApp { a ->
        // Only contacts, no circle: restart, app update and alarm turn receiving on anyway (formerly „Kreis nötig“).
        assertTrue(a.controller.circles.value.isEmpty())
        assertTrue(a.controller.receptionWanted)
    }

    @Test
    fun pastedContactLinkWaitsForConfirmationAndCancelLeavesNothing() = withApp { a ->
        val (ben, link) = a.otherDevice("Testperson Ben")
        assertTrue(link.startsWith("https://anynear.app/c#"))
        assertNull(runBlocking { a.controller.receiveLink(link) })
        assertEquals("Testperson Ben", a.controller.pendingContact.value?.name)
        assertFalse(a.controller.isDirectContact(ben), "vor der Bestätigung nichts gespeichert")
        a.controller.dismissPendingContact()
        assertNull(a.controller.pendingContact.value)
        assertFalse(a.controller.isDirectContact(ben))
        assertNull(a.controller.openRequest.value, "kein Gespräch, das sich später öffnet")
    }

    @Test
    fun pastedInviteLinkAsksWithCircleAndInviterAndCancelLeavesNothing() = withApp { a ->
        val link = a.otherInvite("Testperson Ben", "Testkreis")
        // Pasted under „Kreise“ like opened or scanned: nothing joined before „Beitreten“.
        assertNull(a.controller.offerInvite("Tritt bei: $link"))
        val offer = assertNotNull(a.controller.pendingInvite.value)
        assertEquals("Testkreis", offer.circleName)
        assertEquals("Testperson Ben", offer.inviter)
        assertTrue(a.controller.circles.value.none { it.name == "Testkreis" }, "vor der Bestätigung nicht beigetreten")
        a.controller.dismissPendingInvite()
        assertNull(a.controller.pendingInvite.value)
        Thread.sleep(300)
        assertTrue(a.controller.circles.value.none { it.name == "Testkreis" })
        assertNull(a.controller.openRequest.value)
    }

    @Test
    fun openedInviteLinkJoinsOnlyAfterJoin() = withApp { a ->
        val link = a.otherInvite("Testperson Ben", "Testkreis")
        assertNull(runBlocking { a.controller.receiveLink(link) })
        assertEquals("Testperson Ben", a.controller.pendingInvite.value?.inviter)
        assertTrue(a.controller.circles.value.none { it.name == "Testkreis" })
        assertNull(a.controller.acceptPendingInvite("Testperson Anna"))
        assertNull(a.controller.pendingInvite.value)
        eventually { a.controller.circles.value.any { it.name == "Testkreis" } }
    }

    @Test
    fun brokenOrForeignInviteIsRefusedWithoutAsking() = withApp { a ->
        assertEquals(texts.status.inviteLinkBroken, a.controller.offerInvite("https://anynear.app/k#kaputt"))
        assertEquals(texts.status.notAnInviteLink, a.controller.offerInvite("Hallo"))
        assertNull(a.controller.pendingInvite.value)
    }

    @Test
    fun onlyAddSavesContactWithoutOpeningChat() = withApp { a ->
        val (ben, link) = a.otherDevice("Testperson Ben")
        runBlocking { a.controller.receiveLink(link) }
        assertNull(a.controller.acceptPendingContact(openChat = false))
        eventually { a.controller.isDirectContact(ben) }
        assertNull(a.controller.openRequest.value)
    }

    @Test
    fun addingThroughALinkSaysTheRequestWasSent() = withApp { a ->
        // ADR 0116 §7: the other person decides; the notice says so instead of „ist jetzt in deinen Kontakten“.
        val (_, link) = a.otherDevice("Testperson Ben")
        runBlocking { a.controller.receiveLink(link) }
        assertNull(a.controller.acceptPendingContact(openChat = false))
        assertEquals(texts.people.requestSent("Testperson Ben"), a.controller.notice.value)
    }

    @Test
    fun addAndWriteOpensTheConversation() = withApp { a ->
        val (ben, link) = a.otherDevice("Testperson Ben")
        runBlocking { a.controller.receiveLink(link) }
        assertNull(a.controller.acceptPendingContact(openChat = true))
        // The conversation is open immediately, the core is still saving: still not „Unbekannt“.
        assertEquals("Testperson Ben", a.controller.peerName(PeerId.device(ben)))
        eventually { a.controller.isDirectContact(ben) }
        assertEquals(Conversation.Direct(PeerId.device(ben)), a.controller.openRequest.value)
        assertEquals("Testperson Ben", a.controller.peerName(PeerId.device(ben)))
    }

    @Test
    fun loginLinkWaitsForTheDialogAndForeignIssuersAreRefused() = withApp { a ->
        val req = ByteArray(32) { it.toByte() }
        val link = app.anynear.anmp.core.AnmpLogin.link("anynear.app", app.anynear.anmp.core.AnmpLogin.ISSUER, req)
        val result = runBlocking { a.controller.receiveLink("Melde dich an: $link") }
        if (a.controller.setupDone.value) {
            assertNull(result)
            assertEquals(app.anynear.anmp.core.AnmpLogin.ISSUER, a.controller.pendingLogin.value?.issuer)
            a.controller.dismissLogin()
            val foreign = app.anynear.anmp.core.AnmpLogin.link("anynear.net", "https://id.example.org", req)
            assertEquals(texts.login.invalid, runBlocking { a.controller.receiveLink(foreign) })
        } else {
            assertEquals(texts.login.notSetUp, result)
        }
        assertNull(a.controller.pendingLogin.value)
    }

    @Test
    fun ownLinkAndLinksOfOtherNetworksAreRefused() = withApp { a ->
        val own = runBlocking { withTimeout(10.seconds) { while (a.controller.ownContactLink.value == null) delay(20) }; a.controller.ownContactLink.value!! }
        assertEquals(texts.status.ownContact, runBlocking { a.controller.receiveLink(own) })
        assertEquals(texts.status.contactLinkBroken, runBlocking { a.controller.receiveLink("https://anynear.net/c#kaputt") })
        assertEquals(texts.status.notAnInviteLink, runBlocking { a.controller.receiveLink("https://example.org/") })
        // A link from the anynear network does not belong to this app's test network (ADR 0035 §6).
        val foreign = runBlocking {
            val core = AnmpCore(a.scope, AnmpCoreConfig.default(), AnmpPlatform(MemorySecretStore(), MemoryKeyValueStore(), MemoryBlobStore(), connector = LocalNet.offlineConnector))
            core.open()
            core.ownContactLink()
        }
        assertEquals(texts.status.linkForeignNetwork, runBlocking { a.controller.receiveLink(foreign) })
    }

    @Test
    fun removingDirectContactKeepsConversation() = withApp { a ->
        val (ben, link) = a.otherDevice("Testperson Ben")
        runBlocking { a.controller.receiveLink(link) }
        a.controller.acceptPendingContact(openChat = false)
        eventually { a.controller.isDirectContact(ben) }
        val chat = Conversation.Direct(PeerId.device(ben))
        a.controller.send(chat, "Hallo Ben")
        eventually { a.controller.messages.value.any { it.conversation == chat && it.text == "Hallo Ben" } }
        a.controller.removeDirectContact(ben)
        eventually { !a.controller.isDirectContact(ben) }
        assertTrue(a.controller.messages.value.any { it.conversation == chat && it.text == "Hallo Ben" }, "Gespräch und Verlauf bleiben")
        // The person list catches up with a delay.
        eventually { a.controller.findPerson(chat.peer) == null }
    }

    @Test
    fun conversationOfRemovedContactCanBeDeleted() = withApp { a ->
        val (ben, link) = a.otherDevice("Testperson Ben")
        runBlocking { a.controller.receiveLink(link) }
        a.controller.acceptPendingContact(openChat = false)
        eventually { a.controller.isDirectContact(ben) }
        val chat = Conversation.Direct(PeerId.device(ben))
        a.controller.send(chat, "Hallo Ben")
        eventually { a.controller.chatList.value.any { it.conversation == chat } }
        a.controller.removeDirectContact(ben)
        eventually { a.controller.findPerson(chat.peer) == null }
        // No longer a contact, the conversation can still be deleted.
        a.controller.deleteDirectHistory(chat.peer)
        assertTrue(a.controller.messages.value.none { it.conversation == chat }, "Verlauf ist weg")
        eventually { a.controller.chatList.value.none { it.conversation == chat } }
    }

    @Test
    fun contactCardInChatKnowsWhetherTheContactIsNewKnownOrOwn() = withApp { a ->
        val (ben, link) = a.otherDevice("Testperson Ben")
        val card = { url: String -> runBlocking { a.controller.inviteCard(findInviteLinks("Schau: $url").single()) } }
        assertEquals(InviteCard.Contact("Testperson Ben", known = false, self = false), card(link))
        assertNull(a.controller.pendingContact.value, "die Karte allein ändert nichts")
        // Tapping: the same dialog as when opening the link.
        a.controller.openInviteCard(findInviteLinks(link).single())
        eventually { a.controller.pendingContact.value?.name == "Testperson Ben" }
        a.controller.acceptPendingContact(openChat = false)
        eventually { a.controller.isDirectContact(ben) }
        assertEquals(InviteCard.Contact("Testperson Ben", known = true, self = false), card(link))
        val own = runBlocking { withTimeout(10.seconds) { while (a.controller.ownContactLink.value == null) delay(20) }; a.controller.ownContactLink.value!! }
        assertEquals(InviteCard.Contact("Testperson Anna", known = false, self = true), card(own))
        assertEquals(InviteCard.Broken, card("https://anynear.net/c#kaputt"))
        // A contact from another network cannot be opened here.
        val foreign = runBlocking {
            val core = AnmpCore(a.scope, AnmpCoreConfig.default(), AnmpPlatform(MemorySecretStore(), MemoryKeyValueStore(), MemoryBlobStore(), connector = LocalNet.offlineConnector))
            core.open()
            core.ownContactLink()
        }
        assertEquals(InviteCard.Broken, card(foreign))
    }

    @Test
    fun circleCardOpensTheCircleForMembers() = withApp { a ->
        var circleId: String? = null
        a.controller.createCircle("Testkreis", onCreated = { circleId = it })
        eventually { circleId != null && a.controller.inviteLink(circleId!!) != null }
        val link = findInviteLinks(a.controller.inviteLink(circleId!!)!!).single()
        assertEquals(InviteCard.Circle("Testkreis", member = true), runBlocking { a.controller.inviteCard(link) })
        a.controller.consumeOpenRequest()
        a.controller.openInviteCard(link)
        assertEquals(Conversation.Circle(circleId!!), a.controller.openRequest.value)
        assertEquals(InviteCard.Broken, runBlocking { a.controller.inviteCard(findInviteLinks("https://anynear.net/k#kaputt").single()) })
        // Without alerts in this test app a channel link cannot be opened either.
        assertEquals(InviteCard.Broken, runBlocking { a.controller.inviteCard(findInviteLinks("https://anynear.net/a#abc").single()) })
    }

    @Test
    fun sharedInvitationNamesTheOwnCircle() = withApp { a ->
        var circleId: String? = null
        a.controller.createCircle("Testkreis", onCreated = { circleId = it })
        eventually { circleId != null && a.controller.inviteLink(circleId!!) != null }
        val link = a.controller.inviteLink(circleId!!)!!
        // As the share menu passes it on: a sentence with the link.
        assertEquals(circleId, a.controller.invitedOwnCircle("Komm in meinen Kreis „Testkreis“ bei anynear: $link"))
        assertNull(a.controller.invitedOwnCircle("Nur Text ohne Link"))
        assertNull(a.controller.invitedOwnCircle("https://anynear.net/k#kaputt"))
    }

    @Test
    fun sharingOwnContactIsASentenceWithTheName() {
        val hooks = SharingHooks()
        withApp(hooks) { a ->
            val own = runBlocking { withTimeout(10.seconds) { while (a.controller.ownContactLink.value == null) delay(20) }; a.controller.ownContactLink.value!! }
            a.controller.shareOwnContact()
            assertEquals(texts.status.shareOwnContact("Testperson Anna", own), hooks.shared.single())
            assertTrue(hooks.shared.single().startsWith("Testperson Anna "))
        }
    }

    @Test
    fun invitingFromPhoneContactsTextsOrShares() {
        val hooks = SharingHooks()
        withApp(hooks) { a ->
            runBlocking { withTimeout(10.seconds) { while (a.controller.ownContactLink.value == null) delay(20) } }
            val expected = texts.status.phoneInvite("Testperson Anna", "https://anynear.app/c#").substringBefore("https://")
            fun check(text: String) = assertTrue(text.startsWith(expected) && "anynear.app/c#" in text, text)
            // With a number and SMS: only the text message, the share menu stays closed.
            val texted = mutableListOf<Pair<String, String>>()
            assertTrue(a.controller.inviteFromPhoneContact("+4917112345") { n, t -> texted += n to t; true })
            assertEquals("+4917112345", texted.single().first)
            check(texted.single().second)
            assertTrue(hooks.shared.isEmpty())
            // No SMS on this device (tablet) or no number: the same text in the share menu.
            assertTrue(a.controller.inviteFromPhoneContact("+4917112345") { _, _ -> false })
            assertTrue(a.controller.inviteFromPhoneContact(null) { _, _ -> error("no number, no text message") })
            assertEquals(2, hooks.shared.size)
            hooks.shared.forEach(::check)
        }
    }

    private class SharingHooks : NoOpPlatformHooks() {
        val shared = mutableListOf<String>()
        override fun share(text: String) {
            shared += text
        }
    }

    private class NotificationHooks : NoOpPlatformHooks() {
        val shown = mutableListOf<app.anynear.app.MessageNotification>()
        val cancelled = mutableListOf<String>()
        override fun showMessageNotification(notification: app.anynear.app.MessageNotification) {
            shown += notification
        }
        override fun cancelMessageNotification(key: String) {
            cancelled += key
        }
    }

    @Test
    fun replyFromNotificationSendsLikeTheChat() {
        val hooks = NotificationHooks()
        withApp(hooks) { a ->
            val (ben, link) = a.otherDevice("Testperson Ben")
            runBlocking { a.controller.receiveLink(link) }
            a.controller.acceptPendingContact(openChat = false)
            eventually { a.controller.isDirectContact(ben) }
            val chat = Conversation.Direct(PeerId.device(ben))
            val key = "p:" + chat.peer
            assertTrue(runBlocking { a.controller.replyFromNotification(key, "Okay", wait = 1.seconds) })
            assertTrue(a.controller.messages.value.any { it.conversation == chat && it.outgoing && it.text == "Okay" })
            assertTrue(key in hooks.cancelled, "Benachrichtigung ist weg")
        }
    }

    @Test
    fun replyFromNotificationThatCannotGoShowsTheReason() {
        val hooks = NotificationHooks()
        withApp(hooks) { a ->
            // Nobody known under this ID: no path, the notification comes back with the reason.
            val key = "p:" + PeerId.device(b64.encode(ByteArray(32) { 7 }))
            assertFalse(runBlocking { a.controller.replyFromNotification(key, "Ja", wait = 1.seconds) })
            val shown = hooks.shown.single()
            assertEquals(texts.status.personUnreachable, shown.replyError)
            assertTrue(shown.canReply)
        }
    }

    @Test
    fun noReplyFromNotificationOfPublicChats() = withApp { a ->
        assertFalse(runBlocking { a.controller.replyFromNotification("o:nearby", "Ja", wait = 1.seconds) })
        assertTrue(a.controller.messages.value.none { it.outgoing })
    }

    @Test
    fun createdCircleReportsItsId() = withApp { a ->
        var circleId: String? = null
        assertNull(a.controller.createCircle("Testkreis", onCreated = { circleId = it }))
        eventually { circleId != null && a.controller.circles.value.any { it.id == circleId } }
        val circle = a.controller.circles.value.single()
        assertEquals("Testkreis", circle.name)
        assertTrue(a.controller.inviteLink(circle.id)!!.startsWith("https://anynear.app/k#"))
    }
}
