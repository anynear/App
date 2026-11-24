package app.anynear.app

import app.anynear.anmp.core.radio.DeviceName
import app.anynear.anmp.core.radio.ProfileDeviation
import app.anynear.data.FileSort
import app.anynear.anmp.Anmp
import app.anynear.anmp.AnmpArrived
import app.anynear.anmp.AnmpAttachment
import app.anynear.anmp.AnmpContent
import app.anynear.anmp.AnmpHeader
import app.anynear.anmp.AnmpKind
import app.anynear.anmp.AnmpRadio
import app.anynear.anmp.AnmpInfo
import app.anynear.anmp.AnmpVerifier
import app.anynear.anmp.core.HistoryLoading
import app.anynear.anmp.core.Invite
import app.anynear.anmp.core.OwnHistoryItem
import app.anynear.anmp.core.AddContactResult as CoreAddContactResult
import app.anynear.anmp.core.BlobResult
import app.anynear.anmp.core.ContactCard
import app.anynear.anmp.core.ContactRequest
import app.anynear.anmp.core.Intro
import app.anynear.anmp.core.SentRequest
import app.anynear.anmp.core.CoreEvent
import app.anynear.alerts.AlertChannel
import app.anynear.alerts.Alerts
import app.anynear.lifesigns.LifeSigns
import app.anynear.anmp.core.JoinResult
import app.anynear.anmp.core.DeviceInfo
import app.anynear.anmp.core.StoredDevice
import app.anynear.data.ObservedKeyValueStore
import app.anynear.anmp.core.PairResult
import app.anynear.anmp.core.EraseResult
import app.anynear.anmp.core.AnmpCore
import app.anynear.anmp.core.AnmpLogin
import app.anynear.anmp.core.LoginInfo
import app.anynear.anmp.core.LoginLink
import app.anynear.anmp.core.LoginResult
import app.anynear.anmp.core.UpdateDemand
import app.anynear.ui.daysBetween
import app.anynear.anmp.core.RadioHost
import app.anynear.mesh.MeshtasticRadioPort
import app.anynear.anmp.core.radio.RadioTarget
import app.anynear.mesh.toMessageStatus
import app.anynear.mesh.ReplyRef
import app.anynear.mesh.ContactIntro
import app.anynear.mesh.isSystemCard
import app.anynear.anmp.core.RecoverResult
import app.anynear.anmp.core.VaultResult
import app.anynear.anmp.core.AnmpCoreHost
import app.anynear.anmp.core.PreparedAttachment
import app.anynear.anmp.core.PreparedLink
import app.anynear.AppConfig
import app.anynear.i18n.Strings
import app.anynear.alerts.AlertPriority
import app.anynear.attachments.Attachment
import app.anynear.attachments.AttachmentFiles
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.AttachmentLimits
import app.anynear.attachments.AttachmentState
import app.anynear.attachments.AttachmentWants
import app.anynear.attachments.DraftAttachment
import app.anynear.attachments.RadioAnnouncement
import app.anynear.attachments.VoicePreview
import app.anynear.attachments.VoiceTranscript
import app.anynear.attachments.attachmentHash
import app.anynear.attachments.shortenFileName
import app.anynear.attachments.shrinkImage
import app.anynear.attachments.uprightTurn
import app.anynear.chats.ChatList
import app.anynear.chats.ChatListEntry
import app.anynear.chats.ListedContact
import app.anynear.chats.InviteCard
import app.anynear.chats.InviteKind
import app.anynear.chats.InviteLink
import app.anynear.chats.findInviteLinks
import app.anynear.chats.isSettingsLink
import app.anynear.chats.ReactionNotices
import app.anynear.chats.ReadUntil
import app.anynear.chats.conversationOf
import app.anynear.chats.key
import app.anynear.circles.Circle
import app.anynear.circles.CircleRepository
import app.anynear.circles.ProfileRepository
import app.anynear.contacts.Contact
import app.anynear.contacts.ContactRepository
import app.anynear.core.PushToken
import app.anynear.core.b64
import app.anynear.data.DraftStore
import app.anynear.data.KeyValueStore
import app.anynear.data.MessageOutbox
import app.anynear.data.MessageStore
import app.anynear.data.PairedDeviceRepository
import app.anynear.data.secureRandomBytes
import app.anynear.i18n.texts
import app.anynear.images.AvatarImages
import app.anynear.images.ImageLimits
import app.anynear.images.ImageRef
import app.anynear.images.imageHash
import app.anynear.images.localImageRef
import app.anynear.mesh.BluetoothBonding
import app.anynear.mesh.UsbDevices
import app.anynear.mesh.UsbRadio
import app.anynear.mesh.platformUsbRescue
import app.anynear.mesh.ChannelSetupResult
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.ChatSpot
import app.anynear.mesh.LinkPreview
import app.anynear.chats.LinkDraft
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import app.anynear.chats.LinkPreviews
import app.anynear.chats.codePointCount
import app.anynear.chats.isPublicHost
import app.anynear.mesh.ConnectionDrops
import app.anynear.mesh.Conversation
import app.anynear.mesh.DeviceSetupCheck
import app.anynear.mesh.DeviceSetupResult
import app.anynear.mesh.FoundDevice
import app.anynear.mesh.IncomingData
import app.anynear.mesh.IncomingText
import app.anynear.mesh.LinkState
import app.anynear.mesh.MeshLink
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.NetworkTransport
import app.anynear.mesh.RadioFirmware
import app.anynear.mesh.NotReadyException
import app.anynear.mesh.PairedDevice
import app.anynear.mesh.PeerId
import app.anynear.mesh.radioCheckChannel
import app.anynear.mesh.replyChannel
import app.anynear.mesh.PublicChannel
import app.anynear.mesh.RadioChannel
import app.anynear.mesh.RadioSend
import app.anynear.mesh.Reaction
import app.anynear.mesh.SystemNote
import app.anynear.mesh.Transport
import app.anynear.mesh.ownReaction
import app.anynear.mesh.previewText
import app.anynear.mesh.spotLine
import app.anynear.people.CircleMember
import app.anynear.people.ContactNames
import app.anynear.people.Member
import app.anynear.people.MemberDirectory
import app.anynear.people.Person
import app.anynear.people.Reachability
import app.anynear.people.circleMembers
import app.anynear.people.ownDevicesIn
import app.anynear.people.radioDevicesOf
import kotlin.time.Clock
import app.anynear.data.PersonalFiles
import app.anynear.data.SharedStorage
import app.anynear.passwords.PasswordState
import app.anynear.passwords.PasswordStore
import app.anynear.data.platformDeviceName
import app.anynear.data.restartApp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import app.anynear.mesh.radioNumberLabel
import app.anynear.anmp.core.Conversation as CoreConversation

/**
 * Central app logic without UI dependencies. Lives as long as the app process (Android: in the Application, iOS: in
 * the MainViewController, where a restart replaces it within the process, see [shutdown]).
 *
 * One chat, two paths (ADR 0003): every message goes over the internet (ANMP core on ANDP, ADR 0034–0036) and,
 * if a radio is paired, additionally over radio (ANMP format). A radio is not a prerequisite.
 * Circles, contacts, grants, fetching, receipts, history and key rotation are kept by the core ([core]); the
 * app maps its events to messages, notifications, system lines and states of the UI.
 */
class AppController(
    private val scope: CoroutineScope,
    private val link: MeshLink,
    private val pairedDevices: PairedDeviceRepository,
    private val contactRepository: ContactRepository,
    private val messageStore: MessageStore,
    private val hooks: PlatformHooks,
    private val scanBluetooth: () -> Flow<FoundDevice>,
    private val scanNetwork: () -> Flow<FoundDevice>,
    private val circleRepository: CircleRepository,
    private val profile: ProfileRepository,
    private val memberDirectory: MemberDirectory,
    /** ANMP core: circles, contacts, personal mailbox, sending and fetching over ANDP. */
    internal val core: AnmpCore,
    /** ANMP packets over radio with the same device keys as the core. */
    /** Small state of the app (encrypted on the device), e.g. which messages are already with the core. */
    private val store: KeyValueStore,
    private val attachmentFiles: AttachmentFiles,
    private val avatars: AvatarImages,
    private val drafts: DraftStore,
    /** Log for troubleshooting (Android: logcat), without message texts. */
    private val log: (String) -> Unit = {},
    /** The same for viewing and sharing in the app (Einstellungen > App > Erweitert). */
    val debugLog: DebugLog = DebugLog(),
    /** Crash reports on the device (ADR 0082); `null` in tests and where the platform keeps none. */
    val crashes: CrashRecorder? = null,
    /** network ID of the network the app works in (ADR 0035 §6); it rejects links from other networks. */
    private val networkId: String,
    /** Passwords (ADR 0039); `null` in tests without a platform. */
    val passwords: PasswordStore? = null,
    /** „Meine Ablage“ (ADR 0040); `null` in tests without a platform. */
    val personalFiles: PersonalFiles? = null,
    /** Storages of the circles (ADR 0049); `null` in tests without a platform. */
    val circleFiles: CircleFiles? = null,
    /** Online storage space across all storages together (ADR 0079). */
    val storage: SharedStorage? = null,
    /** Read text and recognition of the storages (ADR 0067); `null` in tests without a platform. */
    val docReading: DocReading? = null,
    /** Search the phone for important documents (ADR 0071); `null` in tests without a platform. */
    val docFinder: DocFinder? = null,
    /** Old state from before ANMP 1 was deleted at startup: show the notice once (ADR 0034 §4). */
    freshStart: Boolean = false,
    /** Everything was deleted at startup because another own device removed this one: show the notice once. */
    removedHere: Boolean = false,
    /**
     * Schedules that everything on the device is deleted at the next start (ADR 0046), with `true` because another
     * own device removed this one; `null` in tests.
     */
    private val requestReset: ((removed: Boolean) -> Unit)? = null,
    /** Public radio of the area (ADR 0055); `null` in tests without a platform. */
    val publicRadio: PublicRadio? = null,
    /** List and files for the software of the radio (ADR 0056); `null` in tests without a platform. */
    firmwareSource: FirmwareSource? = null,
    /** Alerts and channels (ADR 0062); `null` in tests without a platform. */
    val alerts: Alerts? = null,
    /** Usage statistics (ADR 0045); `null` in tests. */
    private val usageStats: UsageStats? = null,
    /** Check-ins (ADR 0083); `null` in tests without a platform. */
    val lifeSigns: LifeSigns? = null,
    /** Who sees the own location (ADR 0121); `null` in tests without a platform. */
    val locationSharing: app.anynear.location.LocationSharing? = null,
    /** Sends the own place and keeps what others share (ADR 0121 §3–§5); `null` in tests without a platform. */
    val placeSharer: app.anynear.location.PlaceSharer? = null,
    /** Tiles of the detailed map (ADR 0121 §11); `null` in tests without a platform. */
    val mapTiles: app.anynear.location.MapTiles? = null,
    /** Calling the helpers (ADR 0083 §2); `null` in tests without a platform. */
    val sos: app.anynear.sos.Sos? = null,
    /** „Standort freigeben“ as the core keeps it (ADR 0123); `null` in tests without a platform. */
    val sharings: app.anynear.location.Sharings? = null,
    /** The places the person named (ADR 0121 §13); `null` in tests without a platform. */
    val savedPlaces: app.anynear.location.SavedPlaces? = null,
    val circlePlaces: app.anynear.location.CirclePlaces? = null,
    /** The one check of the location permission for every action (ADR 0125); `null` in tests without a platform. */
    val locationAccess: app.anynear.location.LocationAccess? = null,
) {
    /** The place that rides along with a direct message (ADR 0121 §20), in the form of the core. */
    private fun corePlace(r: app.anynear.location.PlaceSharer.Riding): app.anynear.anmp.core.PlaceShare = app.anynear.anmp.core.PlaceShare(
        r.level.wire, app.anynear.anmp.core.AlivePosition(r.place.latitude, r.place.longitude, r.place.atMillis, r.place.accuracyMeters),
        r.radiusMeters, r.untilMillis, r.label, r.sinceMillis,
    )

    private fun coreSpot(s: ChatSpot): app.anynear.anmp.core.MessageSpot = app.anynear.anmp.core.MessageSpot(
        s.kind,
        if (s.latitude != null && s.longitude != null) app.anynear.anmp.core.AlivePosition(s.latitude, s.longitude, s.atMillis, s.accuracyMeters) else null,
        s.label,
        s.untilMillis,
        s.level,
    )

    /** A place the map shall show and lead to (ADR 0121 §8): set by a card in a chat, read by the tab „Karte“. */
    data class MapFocus(val latitude: Double, val longitude: Double, val label: String, val peer: String? = null)

    private val _mapFocus = MutableStateFlow<MapFocus?>(null)
    val mapFocus: StateFlow<MapFocus?> = _mapFocus.asStateFlow()

    /** The circle the map shall show when it opens, e.g. from the map icon in the header of a chat; `null` for everyone. */
    private val _mapCircle = MutableStateFlow<Pair<String?, Long>>(null to 0L)
    val mapCircle: StateFlow<Pair<String?, Long>> = _mapCircle.asStateFlow()

    /** Opens the map for [conversation]: its circle as the filter (ADR 0121 §13). */
    fun showMapFor(conversation: Conversation) {
        _mapCircle.value = (conversation as? Conversation.Circle)?.circleId to nowMillis()
    }

    /** The map tab without a filter, e.g. for the own running check-in, whose screen opens there. */
    fun showMap() {
        _mapCircle.value = null to nowMillis()
    }

    fun showOnMap(focus: MapFocus?) {
        _mapFocus.value = focus
    }

    /** Why the own place could not be sent, so that the app says the right thing (ADR 0121 §8). */
    enum class PlaceSend { Sent, NotAllowed, LocationOff, Unknown, Failed }

    /** How long [sendOwnPlace] waits for the person's answer to the system's question about the location. */
    private val PLACE_PERMISSION_WAIT_MILLIS = 120_000L

    /**
     * Sends where this phone is into [conversation]: as a meeting point ([ChatSpot.MEET], with [label]) or as the
     * answer to „Wo bist du?“ ([ChatSpot.HERE]). Needs the exact location: without the permission the system is asked
     * and nothing is sent; with the location switched off on the device its settings open.
     */
    suspend fun sendOwnPlace(conversation: Conversation, kind: Int, label: String? = null): PlaceSend {
        val sharing = locationSharing ?: return PlaceSend.Failed
        if (!hooks.locationServicesOn()) {
            hooks.openLocationSettings()
            return PlaceSend.LocationOff
        }
        if (!sharing.allowed.value) {
            // Asked right here (20.11.2026): once the person allows it, the place goes out without a second tap.
            // The sheet comes once (ADR 0125 §4); a later tap on „Standort senden“ is the consent itself and goes
            // straight to the system. Declined or left open, the answer is the hint as before.
            val access = locationAccess
            val allowed = if (access != null) {
                val need = app.anynear.location.LocationNeed.Precise
                val purpose = app.anynear.location.LocationPurpose.SendPlace
                (if (access.asked(purpose)) access.fixAndWait(need, purpose) else access.ensure(need, purpose)) == app.anynear.location.LocationAccessState.Ok
            } else {
                sharing.allow()
                kotlinx.coroutines.withTimeoutOrNull(PLACE_PERMISSION_WAIT_MILLIS) { sharing.allowed.first { it } } == true
            }
            if (!allowed) return PlaceSend.NotAllowed
        }
        val place = sharing.place() ?: return PlaceSend.Unknown
        val mt = app.anynear.i18n.texts.map
        val link = "https://www.openstreetmap.org/?mlat=${place.latitude}&mlon=${place.longitude}#map=17/${place.latitude}/${place.longitude}"
        val text = (if (kind == ChatSpot.MEET) mt.spotMeetText(label?.trim().orEmpty()) else mt.spotHereText) + "\n" + link
        val sent = send(conversation, text, spot = ChatSpot(kind, place.latitude, place.longitude, place.atMillis, place.accuracyMeters, label?.trim()?.takeIf { it.isNotEmpty() }))
        return if (sent is SendResult.Rejected) PlaceSend.Failed else PlaceSend.Sent
    }

    /**
     * „Immer“ taken away while a level of this phone shows the place all the time (ADR 0125 §5): only the device the
     * place comes from needs it, and nothing while everything is paused.
     */
    private suspend fun watchAlwaysLost() {
        val access = locationAccess ?: return
        val sharing = locationSharing ?: return
        kotlinx.coroutines.flow.combine(
            access.changes, sharing.default, sharing.circles, sharing.persons,
            kotlinx.coroutines.flow.combine(sharing.paused, sharing.source) { paused, source -> paused to source },
        ) { _, default, circles, persons, (paused, _) ->
            val always = default?.always == true || circles.values.any { it.always } || persons.values.any { it.always }
            always && !paused && placeSharer?.isSource != false
        }.collect { access.checkAlways(it) }
    }

    private val _sosNoPrecise = MutableStateFlow<Set<String>>(emptySet())

    /**
     * The persons we are SOS contact for whose calls come without their precise place (ADR 0125 §8): their phone does
     * not allow it. Shown with them, so that nobody relies on a place that will not come.
     */
    val sosNoPrecise: StateFlow<Set<String>> = _sosNoPrecise.asStateFlow()

    private fun refreshSosNoPrecise() {
        _sosNoPrecise.value = runCatching { core.sosAgreed().filter { core.sosPreciseOf(it) == false }.toSet() }.getOrDefault(emptySet())
    }

    /**
     * Tells the own SOS contacts whether a call carries the precise place (ADR 0125 §8): from the device the place
     * comes from, whenever its permission changes.
     */
    private suspend fun tellSosPrecise() {
        val access = locationAccess ?: return
        val sharing = locationSharing ?: return
        // Which device is the source needs the own device ID: read before it is known, a phone that shares counted as
        // „not the source“ and told its SOS contacts nothing, also later (tablet, 22.11.2026).
        core.identity.filterNotNull().first()
        kotlinx.coroutines.flow.combine(access.changes, sharing.source) { _, _ -> (placeSharer?.isSource != false) to (access.state(app.anynear.location.LocationNeed.Precise) == app.anynear.location.LocationAccessState.Ok) }
            .distinctUntilChanged()
            .collect { (isSource, precise) -> if (isSource) runCatching { core.setSosPrecise(precise) } }
    }

    /** Sends a meeting point chosen on the map into [conversation] (ADR 0121 §13): no own location needed for it. */
    fun sendMeetingPoint(conversation: Conversation, latitude: Double, longitude: Double, label: String?): Boolean {
        val mt = app.anynear.i18n.texts.map
        val name = label?.trim()?.takeIf { it.isNotEmpty() }
        val link = "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=17/$latitude/$longitude"
        return send(conversation, mt.spotMeetText(name.orEmpty()) + "\n" + link, spot = ChatSpot(ChatSpot.MEET, latitude, longitude, nowMillis(), null, name)) !is SendResult.Rejected
    }

    /**
     * „Alle teilen“ (ADR 0121 §13): invites everyone in [circleId] to share their place for [minutes], and joins
     * oneself. Nobody else shares anything before agreeing.
     */
    fun inviteAllToShare(circleId: String, minutes: Int): Boolean {
        val until = nowMillis() + minutes * 60_000L
        val mt = app.anynear.i18n.texts.map
        val sent = send(Conversation.Circle(circleId), mt.shareAllMessage(app.anynear.ui.formatTimeText(until)), spot = ChatSpot(ChatSpot.ALL, untilMillis = until)) !is SendResult.Rejected
        if (sent) joinSharing(circleId, until)
        return sent
    }

    /** „Mitmachen“: the own place goes to the members of [circleId] until [untilMillis]; `false` without the permission, which is asked for then. */
    fun joinSharing(circleId: String, untilMillis: Long): Boolean {
        val sharing = locationSharing ?: return false
        if (!hooks.locationServicesOn()) {
            hooks.openLocationSettings()
            return false
        }
        sharing.setLevel(circleId, app.anynear.location.ShareLevel.Exact, untilMillis, members = people.value.filter { circleId in it.circleIds }.mapNotNull { it.deviceId })
        scope.launch { placeSharer?.sync() }
        return sharing.allowed.value
    }

    /** „Aussteigen“: the circle is back at what it was before. */
    fun leaveSharing(circleId: String) {
        locationSharing?.setLevel(circleId, null)
        scope.launch { placeSharer?.sync() }
    }

    // --- „Standort freigeben“ (ADR 0123 §2) ----------------------------------------------------------------------
    // Every step is a card in the chat with the person; the core keeps the state from the cards on every own device.

    /** The person ID for the core of [conversation], if it is one person with anynear. */
    fun sharingPerson(conversation: Conversation): String? = (conversation as? Conversation.Direct)?.let { findPerson(it.peer)?.deviceId }

    private fun directOf(personId: String): Conversation? = people.value.firstOrNull { it.deviceId == personId }?.let { Conversation.Direct(it.peer) }

    private fun sendSharingCard(personId: String, text: String, kind: Int, level: Int? = null): Boolean {
        val conversation = directOf(personId) ?: return false
        if (send(conversation, text, spot = ChatSpot(kind, level = level)) is SendResult.Rejected) return false
        sharings?.refresh()
        scope.launch { placeSharer?.sync() }
        return true
    }

    /** Asks [personId] that both see each other at [level]; nothing goes out before the yes. `false` if it did not go out. */
    fun askSharing(personId: String, level: app.anynear.location.ShareLevel): Boolean =
        sendSharingCard(personId, app.anynear.i18n.texts.map.mutualMessage, ChatSpot.SHARE, level.wire).also { if (it) askForLevel(level) }

    /** A level both see each other at needs „Immer“ on this phone (ADR 0125 §3), asked for once; never holds it up. */
    private fun askForLevel(level: app.anynear.location.ShareLevel) {
        val access = locationAccess ?: return
        if (!level.always) return
        val need = app.anynear.location.LocationNeed.Always
        if (access.state(need) == app.anynear.location.LocationAccessState.Ok) return
        scope.launch { access.ensure(need, app.anynear.location.LocationPurpose.Level) }
    }

    /** The answer to the open question of [personId]: [accept] at the level asked for, or no. */
    fun answerSharing(personId: String, accept: Boolean): Boolean {
        val (level, byUs) = sharings?.asked(personId) ?: return false
        if (byUs) return false
        val mt = app.anynear.i18n.texts.map
        return sendSharingCard(personId, if (accept) mt.mutualYesMessage else mt.mutualNoMessage, ChatSpot.SHARED, if (accept) level.wire else 0)
            .also { if (it && accept) askForLevel(level) }
    }

    /**
     * A step about SOS contacts with [personId] as a card in the chat with it (ADR 0123 §5); kept on this device only,
     * every own device makes its own from what the core tells it.
     */
    fun addSosCard(personId: String, card: Int) {
        val person = people.value.firstOrNull { it.deviceId == personId } ?: return
        val text = app.anynear.i18n.texts.sos.card(card, person.name)
        messageStore.addSystem("sos-$card-$personId-${kotlin.time.Clock.System.now().toEpochMilliseconds()}", Conversation.Direct(person.peer), text, SystemNote(sos = card))
    }

    /** Takes back the own open question to [personId]. */
    fun withdrawSharing(personId: String): Boolean {
        if (sharings?.asked(personId)?.second != true) return false
        return sendSharingCard(personId, app.anynear.i18n.texts.map.sharingWithdrawnMessage, ChatSpot.WITHDRAWN)
    }

    /** Ends the sharing with [personId] for both, and an open question with it. */
    fun endSharing(personId: String): Boolean {
        val s = sharings?.of(personId) ?: return false
        if (!s.active && !s.open) return false
        return sendSharingCard(personId, app.anynear.i18n.texts.map.mutualEndedMessage, ChatSpot.ENDED)
    }

    /** „Wo bist du?“ into [conversation] (ADR 0121 §8): the others answer with their place if they want. */
    fun askWhere(conversation: Conversation): Boolean =
        send(conversation, app.anynear.i18n.texts.map.spotAsk, spot = ChatSpot(ChatSpot.ASK)) !is SendResult.Rejected

    /** The persons the own place can go to: everyone with anynear from the own circles and contacts (ADR 0121 §5). */
    fun shareTargets(): List<app.anynear.location.ShareTarget> = people.value.mapNotNull { p ->
        val id = p.deviceId ?: return@mapNotNull null
        app.anynear.location.ShareTarget(id, p.name, p.circleIds.filter { it != MemberDirectory.DIRECT_CONTACTS })
    }

    /** Order of the storages (ADR 0091 §7): chosen on this device, for all storages; not synced to other devices. */
    private val _filesSort = MutableStateFlow(FileSort.of(store.getString(FILES_SORT_KEY)))
    val filesSort: StateFlow<FileSort> = _filesSort.asStateFlow()

    fun setFilesSort(sort: FileSort) {
        _filesSort.value = sort
        store.putString(FILES_SORT_KEY, sort.name.takeIf { sort != FileSort.NEWEST })
    }

    val pairedDevice: StateFlow<PairedDevice?> = pairedDevices.device

    /** „Meine Funkgeräte“: all that were ever assigned here, even if they are not connected right now. */
    val knownDevices: StateFlow<List<PairedDevice>> = pairedDevices.knownDevices

    /** Disconnected by hand: the radio stays assigned, it only reconnects on „Verbinden“. */
    val radioDisconnected: StateFlow<Boolean> = pairedDevices.disconnected

    // --- Settings in the network (ADR 0106) ---

    /** Person settings to the profile space, device settings to the device space; `null` in tests with a plain store. */
    private val settingsSync: SettingsSync? = (store as? ObservedKeyValueStore)?.let { observed ->
        SettingsSync(scope, observed, object : SettingsSync.Target {
            override suspend fun setPersonSettings(values: Map<String, ByteArray>) = core.setSettings(values)

            override suspend fun setDeviceSettings(values: Map<String, ByteArray>) = core.setDeviceSettings(values)

            override suspend fun takeOverDevice(deviceId: String): Map<String, ByteArray>? = core.takeOverDevice(deviceId)
        })
    }

    /** An own device this phone may replace (ADR 0106 §7); [listed]: still in „Meine Geräte“. */
    data class ReplaceCandidate(val deviceId: String, val name: String, val listed: Boolean)

    private val _replaceOffer = MutableStateFlow<List<ReplaceCandidate>?>(null)

    /**
     * After the emergency card or adding this phone: own devices with settings in the network, for „Ersetzt dieses
     * Handy eins deiner Geräte?“; `null` = nothing to ask.
     */
    val replaceOffer: StateFlow<List<ReplaceCandidate>?> = _replaceOffer.asStateFlow()

    private suspend fun offerReplace() {
        if (settingsSync == null || store.getString(ASK_REPLACE_KEY) == null) return
        val stored: List<StoredDevice> = withTimeoutOrNull(REPLACE_WAIT) {
            var found: List<StoredDevice>? = core.storedDevices()
            while (found == null) {
                delay(REPLACE_RETRY)
                found = core.storedDevices()
            }
            found
        } ?: return
        if (stored.isEmpty()) {
            store.putString(ASK_REPLACE_KEY, null)
            return
        }
        // The own list arrives shortly after the start; without it the names are missing.
        withTimeoutOrNull(REPLACE_LIST_WAIT) { core.ownDevices.first { it.isNotEmpty() } }
        val listed = core.ownDevices.value.associateBy { it.deviceId }
        _replaceOffer.value = stored.map { ReplaceCandidate(it.deviceId, listed[it.deviceId]?.name.orEmpty(), it.deviceId in listed) }
    }

    /**
     * Answer to „Ersetzt dieses Handy eins deiner Geräte?“: `null` = no, it is an additional one. Otherwise the settings
     * of [deviceId] become this phone's (they take effect with [finishReplace]) and the replaced device leaves „Meine
     * Geräte“ right away: whoever says this phone replaces it has answered that already (no second question). `false`
     * if the network did not answer.
     */
    suspend fun replaceDevice(deviceId: String?): Boolean {
        if (deviceId == null) {
            store.putString(ASK_REPLACE_KEY, null)
            _replaceOffer.value = null
            return true
        }
        val ok = settingsSync?.takeOver(deviceId) == true
        if (!ok) return false
        if (core.ownDevices.value.any { it.deviceId == deviceId }) runCatching { core.removeOwnDevice(deviceId) }
        store.putString(ASK_REPLACE_KEY, null)
        return true
    }

    /** After taking over the settings of a replaced device: restart, so that every part of the app reads them. */
    fun finishReplace() {
        _replaceOffer.value = null
        restartApp()
    }

    /** If there are logins, adding a device asks for the device lock first (ADR 0039, item 5). */
    val passwordsNeedDeviceLock: Boolean get() = passwords?.hasVault() == true

    private val _oldAppToRemove = MutableStateFlow(false)

    /**
     * The old app ([AppConfig.OLD_APP_ID]) is still installed. Since the hard cut (ADR 0061) it can do
     * nothing any more and nothing migrates from it; it should go so that nobody opens the wrong one.
     */
    val oldAppToRemove: StateFlow<Boolean> = _oldAppToRemove.asStateFlow()

    /** The relays no longer fully serve this version (`clients.min` in `/andp/v1/info`, ADR 0061 decision 13). */
    val updateNeeded: StateFlow<Boolean> get() = core.updateNeeded

    fun refreshOldApp() {
        _oldAppToRemove.value = hooks.oldAppInstalled()
    }

    /**
     * Delete dialog for the old app. It only lies on top of the app without pausing it; so check here for
     * one minute whether it is gone, so that the notice disappears by itself.
     */
    fun uninstallThisApp() = hooks.uninstallThisApp()

    // --- Commands from the menu bar (Mac, iPad with keyboard) ---

    private val _commands = MutableSharedFlow<AppCommand>(extraBufferCapacity = 8)

    /** Commands the UI executes, e.g. ⌘N for „Neue Nachricht“. */
    val commands: SharedFlow<AppCommand> = _commands.asSharedFlow()

    fun command(command: AppCommand) {
        _commands.tryEmit(command)
    }

    // --- Reset and delete everything (ADR 0046) ---

    val resetAvailable: Boolean get() = requestReset != null

    /** Without an emergency card and without another own device, circles and contacts do not come back after a reset. */
    val resetLosesPerson: Boolean get() = !core.hasEmergencyCard && ownDevices.value.size <= 1

    /** Delete everything on this device, like freshly installed; the person stays in the network. Restarts the app. */
    fun resetThisDevice() {
        val request = requestReset ?: return
        messageStore.messages.value.map { it.conversation }.distinct().forEach(::cancelNotification)
        hooks.setMessageReception(false)
        hooks.setBackgroundConnection(false)
        request(false)
        restartApp()
    }

    /**
     * Delete the person from the network (leave circles, contacts, other own devices, storage, passwords,
     * emergency card), then [resetThisDevice]. Returns the reason if it is not possible right now; then all stays.
     */
    suspend fun eraseEverything(): String? {
        when (core.eraseEverything()) {
            EraseResult.Offline -> return texts.settings.eraseOffline
            EraseResult.Done -> resetThisDevice()
            // Not every leave and revocation reached a relay yet: deleting the installation now would lose them, and
            // circles and contacts would never learn of it. Wait, also across restarts, without a way around.
            EraseResult.Partly -> {
                store.putString(ERASE_WAITING_KEY, "1")
                waitForErase()
            }
        }
        return null
    }

    private val _eraseWaiting = MutableStateFlow(store.getString(ERASE_WAITING_KEY) != null)

    /** „Alle Daten löschen“ waits for writes no relay has yet ([EraseResult.Partly]); the app shows only that. */
    val eraseWaiting: StateFlow<Boolean> = _eraseWaiting.asStateFlow()

    private var eraseWaiter: Job? = null

    private fun waitForErase() {
        _eraseWaiting.value = true
        if (eraseWaiter?.isActive == true) return
        eraseWaiter = scope.launch {
            while (core.undeliveredWrites() > 0) delay(ERASE_POLL)
            store.putString(ERASE_WAITING_KEY, null)
            resetThisDevice()
        }
    }

    init {
        // Interrupted while waiting (app closed, phone restarted): the writes go on after the start, so does the wait.
        if (_eraseWaiting.value) waitForErase()
    }

    fun uninstallOldApp() {
        hooks.uninstallOldApp()
        scope.launch {
            repeat(60) {
                delay(1.seconds)
                refreshOldApp()
                if (!_oldAppToRemove.value) return@launch
            }
        }
    }

    val ownDevice = link.ownDevice
    val contacts: StateFlow<List<Contact>> = contactRepository.contacts
    val messages: StateFlow<List<ChatMessage>> = messageStore.messages
    val bluetoothPermissionGranted = hooks.bluetoothPermissionGranted
    val ownName: StateFlow<String> = profile.ownName

    // --- Circles ------------------------------------------------------------------------

    val circles: StateFlow<List<Circle>> = circleRepository.circles

    /** Circle currently shown. */
    val selectedCircle: StateFlow<Circle?> =
        combine(circleRepository.circles, circleRepository.selectedId) { list, id -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }
            .stateIn(scope, SharingStarted.Eagerly, circleRepository.circles.value.firstOrNull())

    /**
     * Should the app receive in the background (Android: foreground service, ADR 0008)? As soon as setup is done, with
     * or without a circle, like `setupDone.collect(hooks::setMessageReception)`. For restart, app update and alarms that
     * start the service again without the app open: `setupDone` is available at once from storage, the circles maybe not
     * yet; earlier "at least one circle" decided here and switched the service off after an update for people with only
     * contacts (or before the circles were loaded).
     */
    val receptionWanted: Boolean get() = setupDone.value

    init {
        // Do not send an existing installation (circle or a message written by oneself) through setup again.
        profile.migrateSetupDone(circleRepository.circles.value.isNotEmpty() || messageStore.messages.value.any { it.outgoing })
    }

    /** Setup completed? Otherwise the app shows the setup. A circle is not needed for that. */
    val setupDone: StateFlow<Boolean> = profile.setupDone

    /**
     * „Erst einmal umsehen“ or own contact added: setup done, without a circle.
     * [open]: conversation that should be open right afterwards.
     */
    fun finishSetup(open: Conversation? = null) {
        profile.markSetupDone()
        open?.let { _openRequest.value = it }
    }

    private val _myPersonId = MutableStateFlow<String?>(null)

    /** Own person (ADR 0042): the same on phone and tablet. */
    val myPersonId: StateFlow<String?> = _myPersonId.asStateFlow()

    /** Person of a device as [PeerId] (ADR 0042); devices without a person key are the person themselves. */
    private fun peerOfDevice(deviceId: String): String = PeerId.device(memberDirectory.personOf(deviceId))

    /** Person ID of [deviceId] (ADR 0042). */
    fun personOfDevice(deviceId: String): String = memberDirectory.personOf(deviceId)

    private fun isOwnDevice(deviceId: String): Boolean = _myPersonId.value != null && memberDirectory.personOf(deviceId) == _myPersonId.value

    private val _myDeviceId = MutableStateFlow<String?>(null)

    /** Own device ID, as soon as the device key is loaded. */
    val myDeviceId: StateFlow<String?> = _myDeviceId.asStateFlow()

    /** Own ANMP identifier (8 bytes, Base64url), as soon as the core runs. */
    private val myTag: String? get() = core.identity.value?.tag

    /** Own picture, `null` = generated picture. */
    val ownImage: StateFlow<ImageRef?> =
        profile.ownImage.map { it.image }.stateIn(scope, SharingStarted.Eagerly, profile.ownImage.value.image)

    /** Changes as soon as a picture is added on the phone. */
    val avatarVersion: StateFlow<Int> = avatars.version

    /** Members of all circles (without ourselves). */
    val members: StateFlow<List<Member>> = memberDirectory.members

    /**
     * Names from confirmed contact links until the core has stored the contact (see [acceptPendingContact]).
     * Stands before [chatList] because the list can call [peerName] already while being created.
     */
    private val linkNames = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Contacts being added via a link right now; the core stores them in the background ([acceptPendingContact]). */
    private val addingContacts = MutableStateFlow<Set<String>>(emptySet())

    /** Own names for other people in effect, person ID → name ([ContactNames]). */
    private val ownContactNames: StateFlow<Map<String, String>> =
        profile.contactNameEntries.map(ContactNames::active).stateIn(scope, SharingStarted.Eagerly, ContactNames.active(profile.contactNameEntries.value))

    /**
     * The others in each circle, one entry per person (ADR 0042): several devices of someone count once, one's own
     * further devices not at all (they belong to the own line „(du)“).
     */
    val circleMembers: StateFlow<Map<String, List<CircleMember>>> =
        combine(memberDirectory.members, _myPersonId, _myDeviceId, ownContactNames) { m, person, device, names -> circleMembersByCircle(m, person, device, names) }
            .stateIn(scope, SharingStarted.Eagerly, circleMembersByCircle(memberDirectory.members.value, null, null, ownContactNames.value))

    private fun circleMembersByCircle(m: List<Member>, person: String?, device: String?, names: Map<String, String>): Map<String, List<CircleMember>> =
        m.map { it.circleId }.filter { it != MemberDirectory.DIRECT_CONTACTS }.distinct().associateWith { circleMembers(m, it, person, device, names) }

    /** How many of one's own devices are in [circleId], this one included. */
    fun ownDevicesIn(circleId: String): Int = ownDevicesIn(memberDirectory.members.value, circleId, _myPersonId.value, _myDeviceId.value)

    /** All persons from all circles and radio contacts, merged; own names ([ContactNames]) cover profile names. */
    val people: StateFlow<List<Person>> =
        combine(memberDirectory.members, contactRepository.contacts, circleRepository.circles, _myPersonId, ownContactNames) { m, c, k, own, names ->
            Person.merge(m, c, k.map { it.id }, own, names)
        }.stateIn(scope, SharingStarted.Eagerly, Person.merge(memberDirectory.members.value, contactRepository.contacts.value, emptyList(), ownNames = ownContactNames.value))

    /**
     * Gives [peer] an own name that only applies to oneself and covers the name from their profile everywhere; blank:
     * back to the profile name („Zurücksetzen“). Goes to all own devices via the person's settings (ADR 0050).
     */
    fun setContactName(peer: String, name: String) {
        val personId = findPerson(peer)?.deviceId ?: PeerId.deviceIdOf(peer) ?: return
        profile.setContactName(personId, name, nowMillis())
    }

    /** Does the phone have internet at all? Without internet a silent mailbox is not a problem of its own. */
    val internetAvailable: StateFlow<Boolean> = hooks.internetAvailable

    /** At least one relay answers. */
    val onlineReachable: StateFlow<Boolean> =
        core.relays.map { it.reachable.isNotEmpty() }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Per relay of the network list, whether the app currently reaches it (Verbindungen → Internet). */
    val relayStatuses: StateFlow<List<RelayStatus>> =
        core.relays.map(::relayStatuses).stateIn(scope, SharingStarted.Eagerly, emptyList())

    // --- Support identity (ADR 0065) --------------------------------------------------

    /** Device from [AppConfig.SUPPORT_CONTACT_LINK]; `null` while unknown or if this is the support identity. */
    private val supportDevice = MutableStateFlow<String?>(null)

    /**
     * The support identity as [PeerId]. It does not appear among the contacts and not as a direct conversation:
     * questions go through the form, one circle of its own per request. `null` on the support identity itself.
     */
    val supportPeer: StateFlow<String?> =
        combine(supportDevice, memberDirectory.members) { device, _ -> device?.let(::peerOfDevice) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** Circle of a request („Support: …“ with the support identity as a member); shows the picture of support. */
    fun isSupportCircle(circleId: String): Boolean {
        val support = supportPeer.value ?: return false
        val circle = circleRepository.find(circleId) ?: return false
        val prefixes = listOf(Strings.german.support.circlePrefix, Strings.english.support.circlePrefix)
        return prefixes.any(circle.name::startsWith) && memberDirectory.of(circleId).any { PeerId.device(it.personId) == support }
    }

    // --- Contact requests and introductions (ADR 0116) -----------------------------------

    private val _contactRequests = MutableStateFlow<List<ContactRequest>>(emptyList())

    /** Persons asking to become a contact; shown as cards in „Nachrichten“ until you agree or decline. */
    val contactRequests: StateFlow<List<ContactRequest>> = _contactRequests.asStateFlow()

    private val _sentRequests = MutableStateFlow<List<SentRequest>>(emptyList())

    /** Our own requests not yet answered: „Anfrage gesendet“. */
    val sentRequests: StateFlow<List<SentRequest>> = _sentRequests.asStateFlow()

    private suspend fun refreshContactRequests() {
        val before = _contactRequests.value.map { it.person }.toSet()
        val now = runCatching { core.contactRequests() }.getOrNull() ?: return
        _contactRequests.value = now
        val sentBefore = _sentRequests.value
        _sentRequests.value = runCatching { core.sentContactRequests() }.getOrDefault(sentBefore)
        // A request of ours that ended before its time was answered: the person agreed. A line in the chat with it, so
        // that the conversation shows in „Nachrichten“ right away (an expired one ended without an answer).
        val at = Clock.System.now().toEpochMilliseconds()
        val sentNow = _sentRequests.value.map { it.person }.toSet()
        sentBefore.filter { it.person !in sentNow && (it.until == 0L || it.until > at) }.forEach { r ->
            // A request over a contact link names the device, not the person: the line goes to the chat with the
            // person, which [people] knows one step after the core.
            scope.launch {
                val peer = PeerId.device(r.deviceId ?: r.person)
                awaitReachable(peer)
                val conversation = Conversation.Direct(findPerson(peer)?.peer ?: PeerId.device(r.person))
                messageStore.addSystem(newId(), conversation, texts.people.requestAnsweredLine(r.name))
            }
        }
        // One notice per new request, not one per message it brings along.
        now.filter { it.person !in before }.forEach { r ->
            if (!inFront()) {
                hooks.showMessageNotification(
                    MessageNotification(
                        key = "request:${r.person}",
                        title = texts.people.requestTitle(r.name),
                        isCircle = false,
                        lines = listOf(MessageNotification.Line(r.name, requestOrigin(r), r.ts)),
                        unreadCount = 1,
                    ),
                )
            }
        }
    }

    /** „Vorgestellt von Anna“ or „Über deinen Kontaktlink“. */
    fun requestOrigin(r: ContactRequest): String =
        r.introducedByName?.let(texts.people::introducedBy) ?: texts.people.viaYourContactLink

    /** Agree to the request of [person]: it becomes a contact, its messages appear in „Nachrichten“. */
    fun acceptContactRequest(person: String) {
        scope.launch { acceptContactRequestNow(person) }
    }

    /**
     * Like [acceptContactRequest], returns the conversation with the person to open, `null` if there is no such request
     * (any more). A line in it makes the conversation show in „Nachrichten“ even without messages.
     */
    suspend fun acceptContactRequestNow(person: String): Conversation? {
        val name = _contactRequests.value.firstOrNull { it.person == person }?.name
        val accepted = core.acceptContactRequest(person)
        refreshContactRequests()
        if (!accepted || name == null) return null
        val conversation = Conversation.Direct(PeerId.device(person))
        messageStore.addSystem(newId(), conversation, texts.people.requestAcceptedLine(name))
        return conversation
    }

    /** Decline silently: the person learns nothing, what it wrote is deleted here. */
    fun declineContactRequest(person: String) {
        scope.launch {
            core.declineContactRequest(person)
            forgetConversationData(Conversation.Direct(PeerId.device(person)))
            refreshContactRequests()
        }
    }

    /**
     * Introduce the contact [peer] to the contact [toPeer] (ADR 0116): a card in your chat with [toPeer]; both have to
     * agree before they can write to each other. `null` on success, otherwise a text for the notice.
     */
    fun introduce(peer: String, toPeer: String) {
        scope.launch { introduceNow(peer, toPeer)?.let { _notice.value = it } }
    }

    private suspend fun introduceNow(peer: String, toPeer: String): String? {
        val person = PeerId.deviceIdOf(peer) ?: return texts.people.introFailed
        val to = PeerId.deviceIdOf(toPeer) ?: return texts.people.introFailed
        // The name the person gave itself, never an own name for it (ADR 0116 §2): the stand-in text goes to the other side.
        val name = findPerson(peer)?.profileName?.takeIf { it.isNotBlank() } ?: peerName(peer)
        val image = personImageBytes(peer)?.let { bytes ->
            runCatching { core.prepareAttachment(bytes, AttachmentKind.IMAGE.wire, null) }.getOrNull()
        }
        val out = core.introduce(person, to, texts.chat.introStandIn(name), image) ?: return texts.people.introFailed
        val now = Clock.System.now().toEpochMilliseconds()
        messageStore.addOutgoing(
            ChatMessage(
                localId = out.mid, conversation = Conversation.Direct(toPeer), fromNodeNum = 0, text = texts.chat.introStandIn(name),
                timestampMillis = now, outgoing = true, status = MessageStatus.ONLINE, recipients = setOf(toPeer), via = setOf(Transport.INTERNET),
                remoteId = out.mid,
                intro = ContactIntro("", person, name, _myPersonId.value.orEmpty(), now + INTRO_LIFETIME_MILLIS, ""),
            ),
        )
        _notice.value = texts.people.introSent(name, peerName(toPeer))
        return null
    }

    /** We want the contact of the card [intro] (ADR 0116): the introducer passes the request on. */
    suspend fun acceptIntro(intro: ContactIntro): String? {
        val r = core.acceptIntro(Intro(intro.id, intro.person, intro.name, intro.byPerson, intro.until, intro.card))
        refreshContactRequests()
        return when (r) {
            is CoreAddContactResult.Requested -> { _notice.value = texts.people.requestSent(intro.name); null }
            is CoreAddContactResult.AlreadyKnown -> null
            CoreAddContactResult.Own -> texts.status.ownContact
            else -> texts.people.introExpired
        }
    }

    // --- Overview „Nachrichten“ and notifications ---------------------------------------

    /** All conversations as in common messengers: newest on top, with the number of unread messages. */
    val chatList: StateFlow<List<ChatListEntry>> =
        combine(circleRepository.circles, messageStore.messages, people, supportPeer, _contactRequests) { circles, messages, persons, support, requests ->
            // The direct conversation with support (only „Neue Anfrage“) is not in the list; nor one of a person that
            // still asks to become a contact (ADR 0116): it shows as a request until you agree.
            val asking = requests.map { Conversation.Direct(PeerId.device(it.person)) }.toSet()
            ChatList.build(circles, messages, ::peerName, ::senderLabel, listedContacts(persons, memberDirectory.members.value))
                .filterNot { support != null && it.conversation == Conversation.Direct(support) }
                .filterNot { it.conversation in asking }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Contacts one added (not only circle members), also without messages, for the overview (ADR 0008): sorted by when
     * they were added, the first device of the person counts.
     */
    private fun listedContacts(persons: List<Person>, members: List<Member>): List<ListedContact> =
        persons.filter { it.isIn(members, MemberDirectory.DIRECT_CONTACTS) }.map { p ->
            val added = members.filter { it.circleId == MemberDirectory.DIRECT_CONTACTS && (it.personId == p.deviceId || it.deviceId in p.devices) }
                .map { it.addedAt }.filter { it > 0 }.minOrNull() ?: 0
            ListedContact(p.peer, p.devices, added)
        }

    /**
     * Unread messages in the conversations of the overview, for the number at „Nachrichten“ and on the icon. Unread ones
     * elsewhere (e.g. from support or a circle the device no longer knows) cannot be opened, so they do not count.
     */
    val unreadCount: StateFlow<Int> =
        chatList.map { list -> list.sumOf { it.unread } }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, 0)

    /**
     * Counter on the app icon: unread messages and alerts together. When something is read, also on another own
     * device, it goes down here too.
     */
    private fun startBadge(alertsUnread: StateFlow<Int>?) {
        scope.launch {
            combine(unreadCount, alertsUnread ?: MutableStateFlow(0)) { messages, alerts -> messages + alerts }
                .distinctUntilChanged()
                .collect(hooks::setUnreadBadge)
        }
    }

    /** Reactions to own messages that stay in the notification until the conversation is read. */
    private val reactionNotices = ReactionNotices(::nowMillis)

    /** Is the app visible? Android reports this from the Activity; iOS only runs visible anyway. */
    private val inForeground = MutableStateFlow(true)

    /** App visible; false in the background and with the screen off (Android `onStop`, iOS `scenePhase`). */
    val visible: StateFlow<Boolean> = inForeground.asStateFlow()

    /**
     * Does the app have focus? Visible alone is not enough for reading: in split screen, under a system dialog
     * or on the Mac behind another window nobody is looking. Android reports this from the Activity, iOS from `scenePhase`.
     */
    private val focused = MutableStateFlow(true)

    /** Open conversation counts as read: app visible and in the foreground (ADR 0019). */
    val readable: StateFlow<Boolean> =
        combine(inForeground, focused) { visible, focus -> visible && focus }.stateIn(scope, SharingStarted.Eagerly, true)

    init {
        // Show open suggestions again and continue a scan that a restart or update ended (ADR 0071 item 10).
        docFinder?.restore(readable)
    }

    private val openConversation = MutableStateFlow<Conversation?>(null)

    /** Whether the password area is open right now (from App.kt, [setInPasswords]). */
    private var inPasswordArea = false

    /**
     * Since when (ms) nobody has been looking at the passwords, because the area was left or the app is in the
     * background; `null` while the area is open and the app in front. Grace period counts from here (ADR 0048 item 6).
     */
    private var passwordsAwaySince: Long? = null
    private var passwordLockJob: Job? = null

    /** Password area opened or left; leaving only locks after the grace period (ADR 0048 item 6). */
    fun setInPasswords(inside: Boolean) {
        inPasswordArea = inside
        updatePasswordGrace()
    }

    /**
     * Grace period of the passwords: whoever leaves the area or sends the app to the background can come back within
     * 180 seconds without unlocking again, e.g. between storage and chat or to paste in another app. After that
     * the area locks. iOS suspends the app in the background, so on return the elapsed time counts.
     */
    private fun updatePasswordGrace() {
        if (inPasswordArea && inForeground.value) {
            passwordLockJob?.cancel()
            passwordsAwaySince = null
            return
        }
        val since = passwordsAwaySince ?: nowMillis().also { passwordsAwaySince = it }
        passwordLockJob?.cancel()
        val left = PASSWORD_GRACE.inWholeMilliseconds - (nowMillis() - since)
        if (left <= 0) {
            passwords?.lock()
            return
        }
        passwordLockJob = scope.launch {
            delay(left)
            withContext(Dispatchers.Main) { passwords?.lock() }
        }
    }

    /** Visible and in focus; otherwise nobody is looking, new things arrive as a notification. */
    private fun inFront() = inForeground.value && focused.value

    fun setFocused(focus: Boolean) {
        focused.value = focus
    }

    fun setInForeground(visible: Boolean) {
        inForeground.value = visible
        // Opened: what the levels show of the own place is brought up to date (ADR 0121 §24).
        if (visible) placeSharer?.touch()
        link.setInForeground(visible)
        // In the background, query each mailbox at only one relay and, without news, only again after a minute (battery).
        core.setBackground(!visible)
        // Passwords only lock after the grace period ([updatePasswordGrace], ADR 0048 item 6).
        if (!visible) {
            // Update of the radio only with the app open (ADR 0056 §2 step 5).
            radioSoftware?.onBackground()
        } else {
            passwords?.refresh()
        }
        updatePasswordGrace()
        if (visible) {
            // Fetch again at once, also the network list (at most every 10 minutes): a new relay is there right away.
            core.wakeUp()
            refreshOldApp()
            checkBackgroundRestriction()
            hooks.checkAppUpdate(force = false)
            // Rule for updates (ADR 0096 §3): ask for a newer radio list, and look at the clock again.
            updateTick.update { it + 1 }
            scope.launch { runCatching { core.refreshRadioList() }.onFailure { if (it is CancellationException) throw it } }
            usageStats?.onForeground()
            core.radioForeground()
            // Official warnings: district from the approximate location, only in the foreground.
            alerts?.refreshRegion()
            refreshRadioArea()
            scope.launch { pullVault() }
            scope.launch { runCatching { syncProfile() } }
        }
    }

    /** Installed version of the app, for display in the settings. */
    val appVersion: String? get() = hooks.appVersion

    /** Update of the app itself (test version from anynear.app), see [AppUpdateState]. */
    val appUpdate: StateFlow<AppUpdateState> = hooks.appUpdate

    fun checkAppUpdate() = hooks.checkAppUpdate(force = true)

    fun installAppUpdate() = hooks.installAppUpdate()

    private val updateNotes = mutableMapOf<String, ReleaseNotes>()

    /**
     * „Was ist neu“ of the offered [version] since the installed one, in the language of the app; Google Play names no
     * version, then the build number counts. `null` as long as the website has nothing for it (no internet, no notes,
     * nothing new).
     */
    suspend fun updateNotes(version: String?): ReleaseNotes? {
        val build = if (version.isNullOrBlank()) hooks.installableBuild.value else null
        val language = texts.language
        val key = "$language/${version.orEmpty()}/${build ?: ""}"
        updateNotes[key]?.let { return it }
        return ReleaseNotesLoader.load(version, build, since = hooks.appVersion, language = language)?.also { updateNotes[key] = it }
    }

    // --- Update requested by the network (ADR 0096) ---

    /** How this installation gets to a new version (ADR 0096 §6). */
    private val updateWay: StateFlow<UpdateWay> =
        if (hooks.opensUpdatePage) {
            MutableStateFlow(UpdateWay.OpenPage)
        } else {
            hooks.installableBuild.map { build -> build?.let { UpdateWay.Install(it) } ?: UpdateWay.None }
                .stateIn(scope, SharingStarted.Eagerly, UpdateWay.None)
        }

    /** Pushed on every return to the foreground, so that a passed `from` counts at once. */
    private val updateTick = MutableStateFlow(0)

    /** „Später“ on the band without a date: until the next start of the app. */
    private val updateLaterThisRun = MutableStateFlow(false)

    /** „Später“ on the band with a date: point in time, the band comes back on the next day (on this device only). */
    private val updateLaterAt = MutableStateFlow(store.getString(UPDATE_LATER_KEY)?.toLongOrNull())

    /** Current time, at least every minute while someone looks, and on every return to the foreground. */
    private val updateClock: Flow<Long> = merge(
        updateTick,
        flow {
            while (true) {
                emit(0)
                delay(1.minutes)
            }
        },
    ).map { Clock.System.now().toEpochMilliseconds() }

    private fun currentUpdateNotice(now: Long, way: UpdateWay, behind: Boolean, thisRun: Boolean, laterAt: Long?): UpdateNotice {
        val demand = core.radioList.value?.app?.demand(hooks.appBuild ?: 0, now) ?: UpdateDemand.None
        val laterToday = laterAt != null && daysBetween(laterAt, now) == 0L
        return updateNotice(demand, way, debug = hooks.testBuild, clientsBehind = behind).afterLater(thisRun, laterToday)
    }

    init {
        // Gateway of the radio bridge (ADR 0054 §5b) from the signed radio list to the radio link.
        scope.launch { core.radioList.map { it?.gateway }.distinctUntilChanged().collect { link.useGateway(it) } }
    }

    /**
     * Band in „Nachrichten“ or block over the app (ADR 0096 §4, §5): rule `app` of the radio list for this build,
     * combined with the way to the new version and with `clients.min` ([updateNeeded]).
     */
    val updateNotice: StateFlow<UpdateNotice> =
        combine(
            combine(core.radioList, updateWay, core.updateNeeded, ::Triple),
            updateClock,
            updateLaterThisRun,
            updateLaterAt,
        ) { (_, way, behind), now, thisRun, laterAt -> currentUpdateNotice(now, way, behind, thisRun, laterAt) }
            .distinctUntilChanged()
            .stateIn(
                scope, SharingStarted.WhileSubscribed(5_000),
                currentUpdateNotice(Clock.System.now().toEpochMilliseconds(), updateWay.value, core.updateNeeded.value, false, updateLaterAt.value),
            )

    /** Is there anything the button „Aktualisieren“ can do here? */
    val canStartUpdate: Boolean get() = hooks.opensUpdatePage || hooks.appUpdate.value !is AppUpdateState.Unsupported

    /** „Aktualisieren“ on the band or „Jetzt aktualisieren“ on the block. */
    fun startUpdate() {
        when {
            hooks.opensUpdatePage -> hooks.openUpdatePage()
            updateNotice.value is UpdateNotice.Block -> hooks.installRequiredUpdate()
            else -> hooks.installAppUpdate()
        }
    }

    /** „Später“ on the band: without a date until the next start, with a date until the next day (ADR 0096 §4). */
    fun postponeUpdateNotice() {
        when (updateNotice.value) {
            is UpdateNotice.Soon -> {
                val now = Clock.System.now().toEpochMilliseconds()
                updateLaterAt.value = now
                store.putString(UPDATE_LATER_KEY, now.toString())
            }
            UpdateNotice.Needed -> updateLaterThisRun.value = true
            else -> Unit
        }
    }

    /** Counter of the reception over the stream from the home relay (ADR 0072), for the log under „Erweitert“. */
    val streamStats: StateFlow<app.anynear.anmp.core.StreamStats> get() = core.streamStats

    /** Android alarm: restart fetches at once, also those still waiting after an abort (phone was asleep). */
    fun wakeUp() = core.wakeUp()

    /** Check again, e.g. back from the settings or after the system dialog „Zulassen“. */
    fun checkBackgroundRestriction() {
        _backgroundRestriction.value = hooks.backgroundRestriction()
    }

    private val _backgroundRestriction = MutableStateFlow<BackgroundRestriction?>(null)

    /**
     * Notice „Im Hintergrund erlauben“ in case Android would put anynear to sleep. Only after
     * setup and not as long as the user has chosen „Später“.
     */
    val backgroundHint: StateFlow<BackgroundRestriction?> =
        combine(_backgroundRestriction, profile.backgroundHintPostponedUntil, setupDone) { restriction, until, done ->
            restriction?.takeIf { done && Clock.System.now().toEpochMilliseconds() >= until }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The answers of the person's other devices have had time to arrive (ADR 0106), so nobody is asked twice. */
    private val settled = MutableStateFlow(false)

    /**
     * Time to ask „Sollen deine Helfer sehen, wo du bist, wenn etwas ist?“ (ADR 0121 §10): set up, not answered here
     * or on another own device, not postponed. The setup asks in its own step; whoever had the app before the update
     * is asked once.
     */
    val locationQuestion: StateFlow<Boolean> = locationSharing?.let { sharing ->
        scope.launch {
            setupDone.first { it }
            delay(LOCATION_QUESTION_DELAY)
            settled.value = true
        }
        combine(settled, sharing.default, sharing.postponedUntil) { ready, level, until ->
            ready && level == null && Clock.System.now().toEpochMilliseconds() >= until
        }.stateIn(scope, SharingStarted.Eagerly, false)
    } ?: MutableStateFlow(false)

    /** Card „Benachrichtigungen erlauben“ in messages: only after setup, with an explanation instead of a system prompt at startup. */
    val notificationHint: StateFlow<Boolean> =
        combine(setupDone, hooks.notificationPermissionNeeded, profile.notificationHintPostponedUntil) { done, needed, until ->
            done && needed && Clock.System.now().toEpochMilliseconds() >= until
        }.stateIn(scope, SharingStarted.Eagerly, false)

    /** „Erlauben“: the system prompt once, afterwards no card any more. */
    fun allowNotifications() {
        profile.postponeNotificationHint(Long.MAX_VALUE)
        hooks.requestNotificationPermission()
    }

    /** „Erlauben“ on the card: the system prompt (or the system setting), afterwards no card any more. */
    fun allowBluetoothFromHint() {
        profile.postponeBluetoothHint(Long.MAX_VALUE)
        hooks.requestBluetoothPermission()
    }

    fun postponeBluetoothHint() {
        profile.postponeBluetoothHint(Clock.System.now().toEpochMilliseconds() + BACKGROUND_HINT_PAUSE.inWholeMilliseconds)
    }

    fun postponeNotificationHint() {
        profile.postponeNotificationHint(Clock.System.now().toEpochMilliseconds() + BACKGROUND_HINT_PAUSE.inWholeMilliseconds)
    }

    /** What makes background reception harder for Android (battery), `null` if nothing; for the settings. */
    val backgroundRestriction: StateFlow<BackgroundRestriction?> = _backgroundRestriction.asStateFlow()

    /** Notifications not yet allowed and the system prompt still open? */
    val notificationPermissionNeeded: StateFlow<Boolean> = hooks.notificationPermissionNeeded

    /** Einstellungen → Benachrichtigungen: first the system prompt while it is open, otherwise the system setting. */
    fun openNotificationSettings() {
        if (hooks.notificationPermissionNeeded.value) {
            profile.postponeNotificationHint(Long.MAX_VALUE)
            hooks.requestNotificationPermission()
        } else {
            hooks.openNotificationSettings()
        }
    }

    /** Button in the notice: open the matching system setting. */
    fun openBackgroundSettings() {
        _backgroundRestriction.value?.let(hooks::openBackgroundSettings)
    }

    /** „Später“: do not ask again for some days. */
    fun postponeBackgroundHint() {
        profile.postponeBackgroundHint(Clock.System.now().toEpochMilliseconds() + BACKGROUND_HINT_PAUSE.inWholeMilliseconds)
    }

    private val _backgroundNotice = MutableStateFlow(false)

    /** The ongoing notification „Empfängt deine Nachrichten“ was tapped (Android): explain it and offer to hide it. */
    val backgroundNotice: StateFlow<Boolean> = _backgroundNotice.asStateFlow()

    fun openBackgroundNotice() {
        _backgroundNotice.value = true
    }

    fun dismissBackgroundNotice() {
        _backgroundNotice.value = false
    }

    /** „Ausblenden“: to the system setting of that notification; the switch there is the user's. */
    fun hideBackgroundNotice() {
        _backgroundNotice.value = false
        hooks.openBackgroundNoticeSettings()
    }

    /** The UI reports which conversation is open right now (`null`: none). */
    fun setOpenConversation(conversation: Conversation?) {
        openConversation.value = conversation
    }

    private val _openRequest = MutableStateFlow<Conversation?>(null)

    /** Conversation the UI should open, e.g. after tapping a notification. */
    val openRequest: StateFlow<Conversation?> = _openRequest.asStateFlow()

    /** Notification tapped. [key] as in [MessageNotification.key]. */
    fun openConversation(key: String) {
        PublicRadio.channelOfNotificationKey(key)?.let {
            _openPublicRequest.value = it
            return
        }
        _openRequest.value = conversationOf(key)
    }

    private val _findDocsRequest = MutableStateFlow(false)

    /** Open „Dokumente finden“ (ADR 0071), e.g. after tapping the notification of the scan. */
    val findDocsRequest: StateFlow<Boolean> = _findDocsRequest.asStateFlow()

    fun openFindDocs() {
        _findDocsRequest.value = true
    }

    fun consumeFindDocsRequest() {
        _findDocsRequest.value = false
    }

    private val _eraseRequest = MutableStateFlow(false)

    /**
     * Open „Alle Daten löschen“ in the settings, from the link https://anynear.app/s#erase (ADR 0089): the page on deleting
     * data (website, privacy notice) leads straight there. Only opens the confirmation, deletes nothing by itself.
     */
    val eraseRequest: StateFlow<Boolean> = _eraseRequest.asStateFlow()

    fun consumeEraseRequest() {
        _eraseRequest.value = false
    }

    private val _openPublicRequest = MutableStateFlow<PublicChannel?>(null)

    /** Notification of a public chat tapped (ADR 0055 §4): open this chat. */
    val openPublicRequest: StateFlow<PublicChannel?> = _openPublicRequest.asStateFlow()

    fun consumeOpenPublicRequest() {
        _openPublicRequest.value = null
    }

    /** Public chat viewed: remove its notification. */
    fun cancelPublicNotification(channel: PublicChannel) = hooks.cancelMessageNotification(PublicRadio.notificationKey(channel))

    /** New message in a public chat with notifications switched on: only report without focus. */
    private fun notifyAboutPublic(channel: PublicChannel) {
        val radio = publicRadio ?: return
        if (inFront()) return
        val unread = radio.unreadMessages(channel)
        if (unread.isEmpty()) return
        hooks.showMessageNotification(
            MessageNotification(
                key = PublicRadio.notificationKey(channel),
                title = when (channel) {
                    PublicChannel.NEARBY -> texts.publicRadio.nearbyTitle
                    PublicChannel.MESH_HESSEN -> texts.publicRadio.meshHessenTitle
                },
                isCircle = true,
                lines = unread.takeLast(NOTIFICATION_LINES).map {
                    val sender = contactNameForRadio(it.fromNodeNum)?.let(texts.publicRadio::contactRadio)
                        ?: it.fromName?.takeIf { name -> name.isNotBlank() }
                        ?: texts.publicRadio.unknownRadio
                    MessageNotification.Line(sender, it.text, it.timestampMillis)
                },
                unreadCount = unread.size,
            ),
        )
    }

    fun consumeOpenRequest() {
        _openRequest.value = null
    }

    /** New message in [conversation]: read if it is open right now, otherwise a notification if the app has no focus. */
    private fun onReceived(conversation: Conversation) {
        if (!inFront()) {
            notifyAbout(conversation)
        } else if (openConversation.value == conversation) {
            markConversationRead(conversation)
        }
        // In the foreground, but another conversation open: the number in the overview is enough, no system notice.
    }

    /** For troubleshooting: whether an incoming read receipt matches an own message. */
    private fun logReadArrival(mid: String, peer: String, via: Transport) {
        val own = messageStore.find(mid)?.takeIf { it.outgoing }
        val expected = own?.let { m -> m.recipients.ifEmpty { setOfNotNull((m.conversation as? Conversation.Direct)?.peer) } }
        log(
            "Lesebestätigung von $peer über $via für mid=$mid: " + when {
                own == null -> "keine eigene Nachricht"
                peer !in expected.orEmpty() -> "nicht erwartet, Empfänger=$expected Gespräch=${own.conversation.key()}"
                else -> "zählt (vorher ${own.status})"
            },
        )
    }


    /** Conversation viewed: counts as read, the senders get a read receipt (ADR 0019). */
    private fun markConversationRead(conversation: Conversation) {
        val read = messageStore.markRead(conversation).filter { !it.outgoing && it.system == null && !it.fromHistory }
        // For the own other devices and a phone after the emergency card: read up to the newest message (ADR 0107 §8).
        ReadUntil.keyOf(conversation)?.let { key -> messageStore.newestSentAt(conversation)?.let { profile.markReadUntil(key, it) } }
        if (read.isEmpty()) return

        // Internet: per message with message ID to the device of the sender, collected there.
        read.forEach { message ->
            val mid = message.remoteId
            val peer = message.fromPeer
            val device = peer?.let(PeerId::deviceIdOf)
            val skip = when {
                mid == null -> "ohne Nachrichten-ID"
                device == null -> "Absender $peer ohne App-Gerät"
                !core.canWriteTo(device) -> "kein grant für den Briefkasten von $device"
                else -> null
            }
            log("Gelesen ${conversation.key()} mid=$mid via=${message.via}: Internet " + (skip?.let { "übersprungen, $it" } ?: "an $device"))
            if (skip == null) {
                core.confirmRead(device!!, mid!!, coreConversationOf(conversation))
            } else if (mid != null) {
                // The sender gets nothing, the own other devices still do: read is read.
                _myDeviceId.value?.let { me -> core.confirmRead(me, mid, coreConversationOf(conversation)) }
            }
        }

        // Radio: one ANMP packet per sender to their installation (ADR 0021), only if the radio is there right now.
        if (link.state.value != LinkState.Connected) {
            log("Gelesen ${conversation.key()}: Funk übersprungen, Funkgerät ${link.state.value}")
            return
        }
        read.filter { it.via.contains(Transport.RADIO) || it.radioPacketId != 0 }
            .groupBy { it.fromDevice ?: it.fromPeer?.let(PeerId::deviceIdOf) }
            .forEach { (device, messages) ->
                val ids = messages.mapNotNull { anmpId(it.remoteId) }
                if (device == null || ids.isEmpty()) return@forEach
                scope.launch { sendRadioRead(device, ids) }
            }
    }

    /**
     * Radio path of a conversation: target (`null` = everyone in the channel) and channel. Direct messages go over
     * a circle channel that we share with the person. `null` if there is no radio path.
     */
    /** Radio of the recipient (direct only, for Meshtastic texts and reactions) and slot of the radio channel. */
    private fun radioRouteOf(conversation: Conversation): Pair<Int?, Int>? {
        val target = (conversation as? Conversation.Direct)?.let { findPerson(it.peer)?.nodeNum ?: PeerId.nodeNumOf(it.peer) }
        val channel = radioChannelFor(conversation) ?: return null
        return target to channel
    }

    /** Someone reacted to an own message: without focus a notification as for new messages. */
    private fun onReactionReceived(target: ChatMessage, reactor: String, emoji: String, ts: Long) {
        if (inFront()) return
        if (reactionNotices.add(target, reactor, emoji, ts)) notifyAbout(target.conversation)
    }

    /** Remove the notification of a conversation, including the reactions collected in it. */
    private fun cancelNotification(conversation: Conversation) {
        reactionNotices.clear(conversation)
        hooks.cancelMessageNotification(conversation.key())
    }

    /**
     * Reply typed or picked in the notification of [key] (also on a watch, ready-made replies), without opening the
     * app. Goes the same way as from the chat ([send]), encrypted over internet and radio. Afterwards the conversation
     * counts as read and its notification disappears; if the reply cannot go, the notification comes back with the
     * reason. Returns `true` once the reply was accepted; waits at most [wait] for it to leave the phone, so that the
     * platform keeps the app alive that long. Public chats (radio) offer no reply in the notification.
     */
    suspend fun replyFromNotification(key: String, text: String, wait: Duration = REPLY_WAIT): Boolean {
        val conversation = conversationOf(key) ?: return false
        if (text.isBlank()) {
            notifyAbout(conversation)
            return false
        }
        // Started by the notification: give the core a moment to load the own keys and contacts. Both waits together
        // stay within [wait].
        val deadline = nowMillis() + wait.inWholeMilliseconds
        withTimeoutOrNull(wait) { _myDeviceId.filterNotNull().first() }
        val since = nowMillis()
        val result = send(conversation, text)
        if (result is SendResult.Rejected) {
            log("Antwort aus der Benachrichtigung ${conversation.key()} abgelehnt: ${result.reason}")
            notifyAbout(conversation, replyError = result.reason)
            return false
        }
        log("Antwort aus der Benachrichtigung ${conversation.key()} angenommen")
        // Whoever replies has read the conversation: read receipts as when opening it (ADR 0019).
        markConversationRead(conversation)
        cancelNotification(conversation)
        withTimeoutOrNull((deadline - nowMillis()).coerceAtLeast(0)) {
            messageStore.messages.first { all ->
                all.any { it.outgoing && it.conversation == conversation && it.timestampMillis >= since && it.status != MessageStatus.QUEUED }
            }
        }
        return true
    }

    /**
     * „Gelesen“ in the notification of [key] (also on a watch or in Android Auto), without opening the app: the
     * conversation counts as read, as when opening it, so the senders get a read receipt (ADR 0019), and the
     * notification disappears. Waits at most [wait] for the core, as [replyFromNotification]. Returns `false` for an
     * unknown conversation.
     */
    suspend fun markReadFromNotification(key: String, wait: Duration = REPLY_WAIT): Boolean {
        val conversation = conversationOf(key) ?: return false
        // Started by the notification: the core needs the own keys before it can send read receipts.
        withTimeoutOrNull(wait) { _myDeviceId.filterNotNull().first() }
        log("Gelesen aus der Benachrichtigung ${conversation.key()}")
        markConversationRead(conversation)
        cancelNotification(conversation)
        return true
    }

    private fun notifyAbout(conversation: Conversation, replyError: String? = null) {
        val unread = messageStore.messages.value.filter { it.unread && it.conversation == conversation }
        val reactions = reactionNotices.of(conversation)
        if (replyError == null && unread.isEmpty() && reactions.isEmpty()) return
        val title = when (conversation) {
            is Conversation.Circle -> circleRepository.find(conversation.circleId)?.name ?: return
            is Conversation.Direct -> peerName(conversation.peer)
        }
        hooks.showMessageNotification(
            MessageNotification(
                key = conversation.key(),
                title = title,
                isCircle = conversation is Conversation.Circle,
                lines = (
                    unread.map { MessageNotification.Line(senderLabel(it), it.spotLine ?: it.previewText, it.timestampMillis, it.fromPeer, personImageBytes(it.fromPeer)) } +
                        reactions.map {
                            MessageNotification.Line(peerName(it.reactor), texts.chat.reactedTo(it.emoji, it.targetText), it.timestampMillis, it.reactor, personImageBytes(it.reactor))
                        }
                    ).sortedBy { it.timestampMillis }.takeLast(NOTIFICATION_LINES),
                unreadCount = unread.size,
                image = conversationImage(conversation)?.let { runCatching { avatars.bytes(it) }.getOrNull() },
                canReply = true,
                replyError = replyError,
            ),
        )
    }

    /**
     * Uploaded picture of a conversation, as in the overview: circle, person; circles of a support request show the
     * picture of support (ADR 0065). For notifications and the share menu of the system.
     */
    private fun conversationImage(conversation: Conversation): ImageRef? = when (conversation) {
        is Conversation.Circle ->
            if (isSupportCircle(conversation.circleId)) supportPeer.value?.let { findPerson(it)?.image }
            else circleRepository.find(conversation.circleId)?.image
        is Conversation.Direct -> findPerson(conversation.peer)?.image
    }

    /** Identifier of the picture of [conversation], changes with the picture; `null` = none. */
    fun conversationImageId(conversation: Conversation): String? = conversationImage(conversation)?.id

    /** Uploaded picture of [conversation], if on the phone. */
    suspend fun conversationImageBytes(conversation: Conversation): ByteArray? =
        conversationImage(conversation)?.let { imageBytes(it) }

    private fun personImageBytes(peer: String?): ByteArray? =
        peer?.let { findPerson(it)?.image }?.let { runCatching { avatars.bytes(it) }.getOrNull() }

    private val _notice = MutableStateFlow<String?>(null)

    /** Short feedback for the UI, e.g. „Du bist jetzt im Kreis Freunde.“ */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Short notice at the bottom of the screen, e.g. „Gespeichert in …“. */
    fun showNotice(text: String) {
        _notice.value = text
    }

    fun clearNotice() {
        _notice.value = null
    }

    init {
        if (freshStart) _notice.value = texts.status.freshStart
        if (removedHere) _notice.value = texts.devices.removedHereNotice
    }

    /**
     * Invitation opened via a link: link, circle ID and name, until it is confirmed. [inviter] is the name of whoever
     * created the link; `""` for links of older apps.
     */
    data class InviteOffer(val url: String, val circleId: String, val circleName: String, val inviter: String = "")

    private val _pendingInvite = MutableStateFlow<InviteOffer?>(null)

    /** Invitation opened via a link that still has to be confirmed (with name on first start). */
    val pendingInvite: StateFlow<InviteOffer?> = _pendingInvite.asStateFlow()

    // --- Radio --------------------------------------------------------------------------

    val linkState: StateFlow<LinkState> =
        combine(link.state, pairedDevices.device, hooks.bluetoothPermissionGranted) { state, device, granted ->
            when {
                device == null -> LinkState.NoDevice
                !granted && !NetworkTransport.isNetworkDevice(device) -> LinkState.BluetoothUnavailable
                else -> state
            }
        }.stateIn(scope, SharingStarted.Eagerly, LinkState.NoDevice)

    /**
     * Card „Bluetooth erlauben“ in messages: a radio via Bluetooth is on record, but the app
     * may not use Bluetooth, e.g. after a reinstall or restore. As with the
     * notifications only after setup and with „Später“.
     */
    val bluetoothHint: StateFlow<Boolean> =
        combine(setupDone, linkState, profile.bluetoothHintPostponedUntil) { done, state, until ->
            done && state == LinkState.BluetoothUnavailable && Clock.System.now().toEpochMilliseconds() >= until
        }.stateIn(scope, SharingStarted.Eagerly, false)

    private val canTransmit: StateFlow<Boolean> =
        linkState.map { it == LinkState.Connected }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _radioProblems = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Problems when setting up circle channels on the radio, per circle, in everyday language. */
    val radioProblems: StateFlow<Map<String, String>> = _radioProblems.asStateFlow()

    private val _radioChannelProblem = MutableStateFlow<String?>(null)

    /** The radio channel `anynear` is missing and could not be set up; visible on the radio page even without circles. */
    val radioChannelProblem: StateFlow<String?> = _radioChannelProblem.asStateFlow()

    /** Channel slots that other apps occupy on the radio; the circles lack them. */
    val otherRadioChannelSlots: StateFlow<Int> = link.otherChannelSlots

    private val _internetOnly = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Circles that only run over the internet. Since the one radio channel (ADR 0051) there is no shortage of slots;
     * stays empty until there is a switch "internet only" on the circle.
     */
    val internetOnlyCircles: StateFlow<Set<String>> =
        combine(_internetOnly, pairedDevices.device) { ids, device -> if (device == null) emptySet() else ids }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    // --- Set up radio (device profile, ADR 0006) ---------------------------------------

    /** Does the connected radio deviate from the device profile? For the notice in the settings. */
    val deviceSetupNeeded: StateFlow<DeviceSetupCheck?> =
        combine(link.setupCheck, linkState) { check, state -> check?.takeIf { it.kind != DeviceSetupCheck.Kind.OK && state == LinkState.Connected } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private val _setupRequested = MutableStateFlow(false)

    /** Change the radio is applying right now ([deviceBusy]). */
    private class DeviceChange(val name: String?)

    private val _deviceChange = MutableStateFlow<DeviceChange?>(null)

    /** Fallback in case the node number is still unknown. Stays the same as long as the app runs. */
    private val randomNameSuffix = DeviceName.random()

    /** Names of other radios that the own device has heard. */
    private val otherDeviceNames: StateFlow<Set<String>> =
        combine(link.knownNames, link.ownDevice) { names, own -> names.filterKeys { it != own?.nodeNum }.values.toSet() }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /**
     * Dialog „Funkgerät einrichten“. Comes by itself when the device deviates, unless the user has
     * already declined or set up exactly this state. From the settings at any time.
     */
    val deviceSetupPrompt: StateFlow<DeviceSetupPrompt?> =
        combine(deviceSetupNeeded, pairedDevices.skippedSetup, _setupRequested, otherDeviceNames, link.ownDevice) { check, skipped, requested, taken, own ->
            // A mere change of radio area is applied without asking (below); from the settings it can still be asked for.
            check?.takeIf { requested || (it.signature != skipped && !it.areaChange) }?.let {
                // An existing name stays (shared device; a name with an older prefix becomes „anynear.app | …“), otherwise derived from the node number.
                val suggestion = DeviceName.suffixOf(it.currentName)
                    ?: own?.let { device -> DeviceName.derived(device.nodeNum, taken) }
                    ?: randomNameSuffix
                DeviceSetupPrompt(it, suggestedNameSuffix = suggestion, takenNames = taken)
            }
        }
            // While the device applies changes (restart), it deviates briefly; do not ask then.
            .combine(_deviceChange) { prompt, change -> prompt.takeIf { change == null } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    fun requestDeviceSetup() {
        _setupRequested.value = true
    }

    fun postponeDeviceSetup() {
        deviceSetupNeeded.value?.let { pairedDevices.skipSetup(it.signature) }
        _setupRequested.value = false
    }

    /**
     * Writes the device profile with the name `anynear [nameSuffix]`. The same state is not asked about
     * again afterwards, so that a device that does not apply a value does not ask in a loop.
     */
    fun applyDeviceSetup(nameSuffix: String) {
        val check = deviceSetupNeeded.value ?: return
        if (holdForRadioArea { applyDeviceSetup(nameSuffix) }) {
            _setupRequested.value = false
            return
        }
        pairedDevices.skipSetup(check.signature)
        _setupRequested.value = false
        val onlyName = check.deviations == setOf(ProfileDeviation.NAME)
        val isUpdate = check.kind == DeviceSetupCheck.Kind.UPDATE
        val started = when {
            onlyName -> texts.status.deviceNaming
            isUpdate -> texts.status.deviceUpdating
            else -> texts.status.deviceSettingUp
        }
        runDeviceTask(started, DeviceName.longName(nameSuffix), { link.applyRadioProfile(nameSuffix) }) { result ->
            when (result) {
                // Done only comes when the freshly read state matches the device profile.
                DeviceSetupResult.Done -> if (isUpdate) texts.status.deviceUpToDate else texts.status.deviceReady
                DeviceSetupResult.NotConnected -> texts.status.deviceNotConnected
                is DeviceSetupResult.Failed ->
                    if (isUpdate) texts.status.updateFailed(result.reason) else texts.status.setupFailed(result.reason)
            }
        }
    }

    // --- Radio, „Erweitert“: change name, apply the settings of the app again ------------------

    /**
     * Is setup, renaming or applying running right now? The device restarts in the process. Until it reports
     * the new state, name, setup and „Erweitert“ are locked and the channel sync waits.
     */
    val deviceBusy: StateFlow<Boolean> = _deviceChange.map { it != null }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Name the radio is applying right now (`anynear.app | …`); `null` if none. */
    val pendingDeviceName: StateFlow<String?> = _deviceChange.map { it?.name }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Names of other radios in range, for the notice „gibt es schon“. */
    val takenDeviceNames: StateFlow<Set<String>> = otherDeviceNames

    /** Name suggestion derived from the node number (part after `anynear `), as soon as the device is known. */
    val deviceNameSuggestion: StateFlow<String?> =
        combine(link.ownDevice, otherDeviceNames) { own, taken -> own?.let { DeviceName.derived(it.nodeNum, taken) } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    fun renameDevice(nameSuffix: String) =
        runDeviceTask(texts.status.deviceNameSaving, DeviceName.longName(nameSuffix), { link.renameDevice(nameSuffix) }) { result ->
            when (result) {
                DeviceSetupResult.Done -> texts.status.deviceNameSaved
                DeviceSetupResult.NotConnected -> texts.status.deviceNotConnected
                is DeviceSetupResult.Failed -> texts.status.deviceNameNotTaken
            }
        }

    /** Writes all settings of the app again, even if the device already matches. The name stays. */
    fun reapplyRadioProfile() {
        if (holdForRadioArea(::reapplyRadioProfile)) return
        val suffix = DeviceName.suffixOf(link.ownDevice.value?.longName) ?: deviceNameSuggestion.value ?: randomNameSuffix
        runDeviceTask(texts.status.profileApplying, name = null, { link.applyRadioProfile(suffix, force = true) }) { result ->
            when (result) {
                DeviceSetupResult.Done -> texts.status.profileApplied
                DeviceSetupResult.NotConnected -> texts.status.deviceNotConnected
                is DeviceSetupResult.Failed -> texts.status.somethingFailed(result.reason)
            }
        }
    }

    /** Only ever one change to the radio at a time; stays locked until [task] has the result. */
    private fun runDeviceTask(started: String, name: String?, task: suspend () -> DeviceSetupResult, finished: (DeviceSetupResult) -> String) {
        if (_deviceChange.value != null) return
        _deviceChange.value = DeviceChange(name)
        _notice.value = started
        scope.launch {
            try {
                _notice.value = finished(task())
            } finally {
                _deviceChange.value = null
            }
        }
    }

    // --- Radio area (ADR 0054 §1b): radio settings of the region the radio is in ---------------

    /** Area of the radio list whose settings the own radio uses; automatic by location or chosen by hand. */
    val radioAreas = RadioAreas(scope, store, core.radioList, hooks.locationAllowed, hooks::approximateLocation, log, hooks::locationServicesOn)

    private val _radioAreaNeeded = MutableStateFlow(false)

    /**
     * Ask „Wo nutzt du dein Funkgerät?“ (ADR 0054 §1b): the area is unknown, and a radio connected for the first time in
     * this run or the app was about to write radio settings. Without an area the app writes nothing to the radio.
     */
    val radioAreaNeeded: StateFlow<Boolean> = _radioAreaNeeded.asStateFlow()

    /** What was held back for want of an area; runs once the area is known. */
    private var afterRadioArea: (() -> Unit)? = null

    /** Asked once per run when a radio connects without an area; „Später“ holds until the next start. */
    private var radioAreaAsked = false

    /** Holds [then] back and asks for the area if none is known; `true` if held back. */
    private fun holdForRadioArea(then: () -> Unit): Boolean {
        if (radioAreas.known.value) return false
        afterRadioArea = then
        radioAreaAsked = true
        _radioAreaNeeded.value = true
        refreshRadioArea()
        return true
    }

    /** An answer in the question for the area: it closes; what was held back runs as soon as the area is known. */
    fun answeredRadioArea() {
        _radioAreaNeeded.value = false
    }

    /** „Später“ in the question for the area: nothing is written to the radio until an area is set. */
    fun postponeRadioArea() {
        afterRadioArea = null
        _radioAreaNeeded.value = false
    }

    /** Location of the device is off: open its setting („Standort einschalten“). */
    fun openLocationSettings() = hooks.openLocationSettings()

    /** Choose an area by name, or `null` for automatic; automatic looks up the location at once. */
    fun chooseRadioArea(area: String?) {
        radioAreas.choose(area)
        refreshRadioArea()
    }

    /** „Standort erlauben“ in the choice of the radio area; already allowed: look up the area at once. */
    fun allowLocationForRadioArea() {
        if (radioAreas.locationAllowed.value) refreshRadioArea() else hooks.requestLocation()
    }

    private fun refreshRadioArea() {
        scope.launch { runCatching { radioAreas.refresh() }.onFailure { if (it is CancellationException) throw it } }
    }

    /** Last area change tried silently (signature and area), so that a radio that does not take it is not written in a loop. */
    private var lastAreaChange: String? = null

    init {
        scope.launch {
            combine(radioAreas.target, radioAreas.areas) { target, areas -> target to areas.mapNotNull(RadioTarget::of) }
                .distinctUntilChanged()
                .collect { (target, known) -> link.useRadioTarget(target, known) }
        }
        // An area became known: run what was held back, the question goes away.
        scope.launch {
            radioAreas.known.collect { known ->
                if (!known) return@collect
                _radioAreaNeeded.value = false
                afterRadioArea?.let { afterRadioArea = null; it() }
            }
        }
        // First radio of this run without an area: ask once (ADR 0054 §1b).
        scope.launch {
            combine(linkState, radioAreas.known, setupDone) { state, known, done -> state == LinkState.Connected && !known && done }
                .distinctUntilChanged()
                .collectLatest { ask ->
                    if (!ask || radioAreaAsked) return@collectLatest
                    // First give the location a moment, so that the question does not flash up for nothing.
                    refreshRadioArea()
                    delay(RADIO_AREA_GRACE)
                    if (!radioAreas.known.value && !radioAreaAsked) {
                        radioAreaAsked = true
                        _radioAreaNeeded.value = true
                    }
                }
        }
        // Look up the area again when the list changes, a radio connects or the permission comes.
        scope.launch {
            combine(core.radioList, linkState, radioAreas.locationAllowed) { list, state, allowed ->
                Triple(list?.issued, state == LinkState.Connected, allowed)
            }.distinctUntilChanged().collect { refreshRadioArea() }
        }
        // A radio of the app on another area's settings switches without asking and says so once (ADR 0054 §1).
        scope.launch {
            combine(deviceSetupNeeded, radioAreas.target, _deviceChange) { check, target, change ->
                check?.takeIf { it.areaChange && change == null }?.let { it to target }
            }.collect { pair ->
                val (check, target) = pair ?: return@collect
                // Without an area nothing is switched (ADR 0054 §1b): the question for the area comes instead.
                if (target.area == null) return@collect
                val key = "${check.signature}|${target.area}"
                if (key == lastAreaChange) return@collect
                lastAreaChange = key
                val area = target.area ?: texts.settings.radioAreaStandard
                val suffix = DeviceName.suffixOf(check.currentName) ?: deviceNameSuggestion.value ?: randomNameSuffix
                runDeviceTask(texts.status.radioAreaChanging(area), name = null, { link.applyRadioProfile(suffix) }) { result ->
                    when (result) {
                        DeviceSetupResult.Done -> texts.status.radioAreaChanged(area)
                        DeviceSetupResult.NotConnected -> texts.status.deviceNotConnected
                        is DeviceSetupResult.Failed -> texts.status.updateFailed(result.reason)
                    }
                }
            }
        }
    }

    /** May the user write? As soon as setup is done, even without a circle. Waiting messages go out automatically. */
    val canSend: StateFlow<Boolean> = setupDone

    private val outbox = MessageOutbox(
        scope = scope,
        store = messageStore,
        canTransmit = canTransmit,
        transmit = ::radioTransmit,
        trackingWindow = DIRECT_CONFIRMATION_WINDOW,
        stillWanted = ::stillWantedOnRadio,
    )

    // --- Software of the radio (ADR 0056) --------------------------------------------------

    /** Software and model of the connected radio, for „Erweitert“. */
    val radioFirmware: StateFlow<RadioFirmware?> = link.firmware

    /** New software for the own radio, only with consent; `null` without a platform. */
    val radioSoftware: RadioSoftware? = firmwareSource?.let { source ->
        RadioSoftware(
            scope = scope,
            link = link,
            source = source,
            overNetwork = { pairedDevices.device.value?.let(NetworkTransport::isNetworkDevice) == true },
            phoneBattery = hooks::phoneBatteryLevel,
            messagesWaiting = { !outbox.isEmpty() },
            appBuild = hooks.appBuild,
            now = ::nowMillis,
        )
    }

    // --- Radio check (ADR 0053) -----------------------------------------------------------

    /** Radio check at the push of a button, with upper limits; the last result per radio stays stored. */
    val radioChecks = RadioChecks(
        scope = scope,
        store = store,
        nowMillis = ::nowMillis,
        // The same channel as for messages, hop limit from the radio profile: the real path is checked.
        send = { node, timeout ->
            link.radioCheck(node, radioCheckChannelFor(node) ?: throw NotReadyException("Funkkanal fehlt"), timeout)
                // The radio answers: own messages over radio waiting for this person may go again (ADR 0090).
                ?.also { scope.launch { core.radioHeard(node.toUInt().toLong()) } }
        },
        log = log,
    )

    init {
        // Network back: send and fetch at once instead of waiting out the delivery plan (ADR 0090).
        scope.launch { hooks.internetAvailable.drop(1).filter { it }.collect { core.networkChanged() } }
    }

    /**
     * Until then (ms) no recommendation for rescue via USB: after the reset the old pairing rightly fails
     * (new key, new name); then re-pairing helps, not yet another rescue (hardware test 03.11.2026).
     */
    private val pairingQuietUntil = MutableStateFlow(0L)

    /**
     * Pairing with the radio has failed at least [PAIRING_HINT_AFTER] times in a row, and it is not
     * connected: the radio page recommends removing it in the Bluetooth settings and pairing it again.
     */
    val pairingTrouble: StateFlow<Boolean> =
        combine(BluetoothBonding.failures, linkState) { failures, state -> failures >= PAIRING_HINT_AFTER && state != LinkState.Connected }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * How serious the pairing problem is: 0 none, 1 from [PAIRING_HINT_AFTER] failures (recommend re-pairing), 2 from
     * [RESCUE_HINT_AFTER] (re-pairing did not help: recommend rescue via USB cable).
     */
    val pairingTroubleLevel: StateFlow<Int> =
        combine(BluetoothBonding.failures, linkState, pairingQuietUntil) { failures, state, quiet ->
            when {
                state == LinkState.Connected -> 0
                // Shortly after the reset the old pairing rightly fails: recommend re-pairing, not rescue.
                failures >= RESCUE_HINT_AFTER && nowMillis() >= quiet -> 2
                failures >= PAIRING_HINT_AFTER -> 1
                else -> 0
            }
        }.stateIn(scope, SharingStarted.Eagerly, 0)

    private val pairingHintDismissed = MutableStateFlow(0)

    /** Level of the pairing notice as long as it is not dismissed for this level (once per app start and level). */
    val pairingHint: StateFlow<Int> =
        combine(pairingTroubleLevel, pairingHintDismissed) { level, dismissed -> if (level > dismissed) level else 0 }
            .stateIn(scope, SharingStarted.Eagerly, 0)

    fun dismissPairingHint() {
        pairingHintDismissed.value = maxOf(pairingHintDismissed.value, pairingTroubleLevel.value)
    }

    // --- Unreachable contacts (ADR 0046): every relay refuses our messages, likely the account was deleted ---------

    /** „Behalten“ per person, with the time of the mark it applies to: a new mark shows the hint again. */
    private val unreachableKept = MutableStateFlow(
        store.getString(UNREACHABLE_KEPT_KEY).orEmpty().split(',').mapNotNull { e ->
            e.substringBeforeLast(':').takeIf { it.isNotEmpty() }?.let { it to (e.substringAfterLast(':').toLongOrNull() ?: 0L) }
        }.toMap(),
    )

    /** Persons (person ID) that can no longer be reached, with since when (ms). */
    val unreachable: StateFlow<Map<String, Long>> get() = core.unreachable

    /** Persons whose hint in the chat is still open: unreachable and not kept for this mark. */
    val unreachableHint: StateFlow<Set<String>> by lazy {
        combine(core.unreachable, unreachableKept) { marks, kept -> marks.filter { (p, since) -> kept[p] != since }.keys }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())
    }

    /** „Behalten“: hides the hint for [person] until it is marked again. */
    fun keepUnreachable(person: String) {
        val since = core.unreachable.value[person] ?: return
        val kept = unreachableKept.value.filterKeys { it in core.unreachable.value } + (person to since)
        unreachableKept.value = kept
        store.putString(UNREACHABLE_KEPT_KEY, kept.entries.joinToString(",") { "${it.key}:${it.value}" }.ifEmpty { null })
    }

    /** Rescue via USB cable (ADR 0056); `null` where the platform cannot do USB (iOS). */
    val usbRescue: UsbRescueFlow? = platformUsbRescue { log("USB-Rettung: $it") }?.let { usb ->
        UsbRescueFlow(
            usb,
            radioSoftware,
            lastHwModel = { store.getString(LAST_RADIO_MODEL_KEY) },
            log = { log("USB-Rettung: $it") },
            connect = ::connectUsbRadio,
            radios = { RecommendedRadios.loadOnce(store) },
            background = scope,
        )
    }

    /**
     * Setting up via USB cable (ADR 0094): connects the radio on the cable on the chosen way. Bluetooth and Wi-Fi look
     * for exactly this radio by the end of its name and pair it (over Bluetooth the phone then asks for the number);
     * over the cable it becomes the assigned radio right away. `null` if it does not show up in time, for example
     * because another phone is connected to it.
     */
    private suspend fun connectUsbRadio(radio: UsbRadio, via: UsbConnection): String? {
        if (via == UsbConnection.USB) {
            val node = radio.nodeNum ?: return null
            val device = UsbDevices.deviceFor(node, radio.name ?: radio.nameAfterReset.orEmpty())
            pairKnown(device)
            return device.name.ifBlank { null } ?: radio.firmware?.hwModel
        }
        // The radio told its address over the cable: connect there, no search in the Wi-Fi needed (FUT 14.11.2026).
        if (via == UsbConnection.WIFI) radio.wifiAddress?.let { ip -> connectAtAddress(radio, ip)?.let { return it } }
        val suffix = radio.bluetoothSuffix?.lowercase() ?: return null
        return findAndPair(suffix, if (via == UsbConnection.WIFI) FoundDevice.Connection.NETWORK else FoundDevice.Connection.BLUETOOTH)
    }

    /** Assigns the radio at [ip] in the Wi-Fi and waits until it is connected; `null` if it does not answer there. */
    private suspend fun connectAtAddress(radio: UsbRadio, ip: String): String? {
        val address = NetworkTransport.parse(ip) ?: return null
        val device = NetworkTransport.deviceFor(address, radio.name)
        pairKnown(device)
        val connected = withTimeoutOrNull(WIFI_CONNECT_WAIT) { linkState.first { it == LinkState.Connected } } != null
        if (!connected) log("Funkgerät unter $ip nicht erreicht, suche im WLAN")
        return if (connected) device.name else null
    }

    /**
     * Looks on [connection] for exactly the radio whose name ends with [suffix] (`_xxxx` from the node number) and
     * pairs it; over Bluetooth the phone then asks for the number. `null` if it does not show up within [wait]
     * (restart included).
     */
    private suspend fun findAndPair(suffix: String, connection: FoundDevice.Connection, wait: Duration = USB_PAIR_WAIT): String? {
        if (connection == FoundDevice.Connection.BLUETOOTH && !hooks.bluetoothPermissionGranted.value) {
            hooks.requestBluetoothPermission()
            withTimeoutOrNull(USB_PAIR_WAIT) { hooks.bluetoothPermissionGranted.first { it } } ?: return null
        }
        val found = withTimeoutOrNull(wait) {
            coroutineScope {
                // A search ends after 30 seconds; the radio may still be restarting, so keep searching.
                val keepSearching = launch {
                    while (true) {
                        if (scanJob?.isActive != true) startScan()
                        delay(2.seconds)
                    }
                }
                _scanResults.first { list -> list.any { it.isRadio(suffix, connection) } }.first { it.isRadio(suffix, connection) }
                    .also { keepSearching.cancel() }
            }
        }
        stopScan()
        if (found == null) {
            log("Funkgerät mit …$suffix nicht gefunden ($connection)")
            return null
        }
        pair(found)
        return found.name
    }

    /** Where switching the way of use without cable stands (ADR 0094 §3); `null` when nothing is running. */
    sealed interface WayChange {
        data class Working(val toWifi: Boolean) : WayChange

        data class Done(val name: String, val toWifi: Boolean) : WayChange

        /** The radio took the change but did not show up on the new way. */
        data class NotSeen(val toWifi: Boolean) : WayChange

        /** The radio did not take the change (not connected, refused); nothing changed. */
        data object Failed : WayChange
    }

    private val _wayChange = MutableStateFlow<WayChange?>(null)
    val wayChange: StateFlow<WayChange?> = _wayChange.asStateFlow()

    /**
     * Can the way of use be switched without cable right now? Only for the assigned radio while it is connected over
     * Bluetooth or Wi-Fi, and only if it can do Wi-Fi.
     */
    val canSwitchWay: StateFlow<Boolean> =
        combine(pairedDevices.device, linkState, link.firmware) { device, state, firmware ->
            device != null && !UsbDevices.isUsbDevice(device) && state == LinkState.Connected && firmware?.hasWifi == true
        }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Is the assigned radio in the Wi-Fi (connected over the network)? */
    val radioInWifi: Boolean get() = pairedDevices.device.value?.let(NetworkTransport::isNetworkDevice) == true

    /**
     * Switches the way of use over the current connection (ADR 0094 §3): [ssid] set = into the Wi-Fi, `null` = back to
     * Bluetooth. Then looks for the radio on the new way and pairs it.
     */
    fun switchWay(ssid: String?, password: String?) {
        val node = link.ownDevice.value?.nodeNum ?: run { _wayChange.value = WayChange.Failed; return }
        val toWifi = ssid != null
        _wayChange.value = WayChange.Working(toWifi)
        scope.launch {
            if (!link.setWayOfUse(ssid?.trim(), password)) {
                _wayChange.value = WayChange.Failed
                return@launch
            }
            val suffix = "_" + (node and 0xFFFF).toString(16).padStart(4, '0')
            // Into the Wi-Fi without cable nothing tells a wrong password (Bluetooth is off by then): only the time limit,
            // kept short so that the message comes soon (FUT 14.11.2026).
            val name = if (toWifi) findAndPair(suffix, FoundDevice.Connection.NETWORK, WAY_WIFI_WAIT) else findAndPair(suffix, FoundDevice.Connection.BLUETOOTH)
            _wayChange.value = if (name != null) WayChange.Done(name, toWifi) else WayChange.NotSeen(toWifi)
        }
    }

    fun dismissWayChange() {
        _wayChange.value = null
    }

    private fun FoundDevice.isRadio(suffix: String, way: FoundDevice.Connection) = connection == way && name.lowercase().endsWith(suffix)

    private val _usbRescueOpen = MutableStateFlow(false)

    /** Is the dialog „Rettung per USB-Kabel“ open? It can be opened from „Erweitert“ and from notices. */
    val usbRescueOpen: StateFlow<Boolean> = _usbRescueOpen.asStateFlow()

    /**
     * Opens the rescue; fresh only if it is closed. If the radio switches to the bootloader, it shows up as a new
     * USB device, and Android opens anynear again: then it must continue at the same point (choose drive).
     */
    fun openUsbRescue() = openUsb(UsbPurpose.RESCUE)

    /** „Per USB-Kabel verbinden“ when setting up a new radio: looks at the radio right away and guides through it. */
    fun openUsbSetup() = openUsb(UsbPurpose.SETUP)

    /**
     * Radio just plugged in (anynear chosen in Android's question): first the choice between setting up and rescuing.
     * If the dialog is already open (e.g. the nRF52 shows up anew in the bootloader), it continues where it was.
     */
    fun openUsbPlugged() = openUsb(null)

    private fun openUsb(purpose: UsbPurpose?) {
        val rescue = usbRescue ?: return
        // A step that goes on without the dialog (joining the Wi-Fi, connecting): show it instead of starting anew.
        val fresh = !_usbRescueOpen.value && !rescue.runningInBackground
        if (fresh) rescue.reset(purpose)
        // Only ever one notice: the pairing notice is settled with the rescue.
        dismissPairingHint()
        _usbRescueOpen.value = true
        // Setting up or just plugged in: look right away instead of waiting for „Suchen“.
        if (fresh && purpose != UsbPurpose.RESCUE) scope.launch { rescue.search() }
    }

    fun closeUsbRescue() {
        _usbRescueOpen.value = false
    }

    /** Are there Bluetooth settings to open (Android)? */
    val canOpenBluetoothSettings: Boolean get() = hooks.canOpenBluetoothSettings

    /** Pairing waits for the number from the radio's display (Android). */
    val bluetoothPairingWaiting: StateFlow<Boolean> = BluetoothBonding.waiting

    fun openBluetoothSettings() = hooks.openBluetoothSettings()

    private val connectionDrops = ConnectionDrops()
    private val dropsLock = Mutex()

    /**
     * Radio on Wi-Fi is used by another phone or app, and the app has stepped back (hardware test 02.11.2026): it no
     * longer reconnects on its own, because every connect would kick the other one out. The radio page offers „Hier
     * verbinden“; the question comes up by itself as long as it is not answered ([radioInUsePrompt]).
     */
    val radioInUseElsewhere: StateFlow<Boolean> =
        linkState.map { it == LinkState.InUseElsewhere }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _radioInUsePrompt = MutableStateFlow(false)

    /** Ask whether to connect here (dialog in the app, notification in the background). */
    val radioInUsePrompt: StateFlow<Boolean> = _radioInUsePrompt.asStateFlow()

    /**
     * Connect here after all: the other phone or app is disconnected. Afterwards this phone stays put for
     * [TAKE_OVER_HOLD], so that a second anynear steps back instead of this one.
     */
    fun takeOverRadio() {
        val device = pairedDevice.value ?: return
        _radioInUsePrompt.value = false
        hooks.cancelReminder(RADIO_IN_USE_ID)
        scope.launch {
            dropsLock.withLock { connectionDrops.takenOver(nowMillis(), TAKE_OVER_HOLD) }
            log("Funkgerät hier übernommen")
            link.start(device)
        }
    }

    /** Leave the radio with the other one; the radio page still offers „Hier verbinden“. */
    fun leaveRadioElsewhere() {
        _radioInUsePrompt.value = false
    }

    /** Count connection drops, only for radios on Wi-Fi; Bluetooth drops for other reasons (distance). */
    private suspend fun watchConnectionDrops() {
        link.state.collect { state ->
            // Connected again, other radio or none: the question is settled.
            if (state != LinkState.InUseElsewhere) _radioInUsePrompt.value = false
            val now = nowMillis()
            val network = pairedDevice.value?.let(NetworkTransport::isNetworkDevice) == true
            val displaced = dropsLock.withLock {
                when (state) {
                    LinkState.Connected -> connectionDrops.connected(now)
                    LinkState.Lost, LinkState.Searching -> connectionDrops.lost(now)
                    else -> Unit
                }
                network && state != LinkState.InUseElsewhere && connectionDrops.unstable(now)
            }
            if (displaced) stepBack()
        }
    }

    /** Make room for the other phone or app and ask: in the app as a dialog, in the background as a notification. */
    private suspend fun stepBack() {
        log("Funkgerät wird woanders genutzt: Platz gemacht")
        link.yieldRadio()
        _radioInUsePrompt.value = true
        if (!inFront()) {
            hooks.showReminder(Reminder(RADIO_IN_USE_ID, texts.settings.radioInUseTitle, texts.settings.radioInUseNotification))
        }
    }

    /** Remind to charge the radio (ADR 0085). */
    private val chargeReminder = ChargeReminder(store, hooks, ::nowMillis)

    private suspend fun watchCharge() {
        combine(profile.chargeReminder, pairedDevice, link.state, link.ownDevice) { on, paired, state, own ->
            Triple(on && paired != null, state == LinkState.Connected, own?.batteryLevel)
        }.distinctUntilChanged().collect { (enabled, connected, battery) ->
            chargeReminder.update(enabled, connected, battery)
        }
    }

    /**
     * Radio connected and a channel to send on (open channel 0 or `anynear`, like the messages): only then is there the
     * button „Über Funk prüfen“. Lazy because [radioPort] is set up further down.
     */
    val radioCheckReady: StateFlow<Boolean> by lazy { radioPort.ready }

    /** Channel for the radio check to [node]: the same path as a message to it ([radioCheckChannel]). */
    private fun radioCheckChannelFor(node: Int): Int? {
        val anynearRadio = people.value.any { it.deviceId != null && node in knownRadios(it.peer) }
        return radioCheckChannel(messageStore.messages.value, node, radioOnly = !anynearRadio, sendChannel = radioPort.channelIndex())
    }

    /** Radios of [peer]: from the radio contact or from earlier radio messages (ADR 0053 §3). */
    fun knownRadios(peer: String): List<Int> {
        val nodeNum = findPerson(peer)?.nodeNum ?: PeerId.nodeNumOf(peer)
        return Reachability.of(messageStore.messages.value, peer, nodeNum).radioNodes.filter { it != link.ownDevice.value?.nodeNum }
    }

    /**
     * Names of the persons who use the radio [nodeNum] (ADR 0022): stored with this radio or written
     * from there. Empty if anynear knows nobody for it.
     */
    fun radioUsers(nodeNum: Int): List<String> {
        val peers = people.value.filter { it.nodeNum == nodeNum }.map { it.peer } +
            messageStore.messages.value.filter { !it.outgoing && it.fromNodeNum == nodeNum }.mapNotNull { it.fromPeer }
        return peers.distinct().filter { PeerId.nodeNumOf(it) == null }.map(::peerName).distinct()
    }

    /** Radio for the core (ADR 0057); remember packet IDs of own transmissions so that echoes do not count as new. */
    private val radioPort = MeshtasticRadioPort(scope, link, radioAreas.channel0, onSent = { messageStore.rememberPacket(profile.ownNodeNum.value, it) }, log = { log(it) })

    /** What the radio path in the core wants to know from the app (ADR 0051, 0057). */
    private val radioHost = object : RadioHost {
        override fun online(): Boolean = onlineReachable.value


        override fun mayServeOthers(): Boolean {
            if (!profile.relayForOthers.value) return false
            // Only with enough battery on the radio (above 100 = power supply); without a value it counts as enough.
            val battery = link.ownDevice.value?.batteryLevel ?: return true
            return battery >= MIN_RELAY_BATTERY
        }

        override fun stillWanted(mid: String): Boolean = stillWantedOnRadio(mid)
    }

    /**
     * Sender with internet (ADR 0051): if no delivery receipt comes after [RADIO_FALLBACK_AFTER], the radio form
     * of the message is additionally put in the radio inbox. So it also reaches someone who only has radio right now.
     */
    private suspend fun radioFallbackLoop() {
        val done = mutableSetOf<String>()
        while (true) {
            delay(RADIO_FALLBACK_CHECK)
            if (!onlineReachable.value || !core.hasRadioInbox) continue
            val now = nowMillis()
            val due = messageStore.messages.value.filter { m ->
                m.outgoing && m.system == null && m.localId !in done && m.status?.arrived != true &&
                    now - m.timestampMillis in RADIO_FALLBACK_AFTER.inWholeMilliseconds..RADIO_FALLBACK_UNTIL.inWholeMilliseconds
            }
            for (m in due) {
                done += m.localId
                val kinds = m.attachments.map { it.kind }
                val duration = m.attachments.firstOrNull { it.kind == AttachmentKind.AUDIO }?.durationMillis ?: 0
                val text = RadioAnnouncement.cut(RadioAnnouncement.radioText(m.text, kinds, duration), radioTextLimit(m.conversation))
                val packet = runCatching { anmpTextPacket(m, text) }.getOrNull() ?: continue
                if (core.uplinkRadioPacket(packet)) log("Funkform von ${m.localId.take(6)} in der radio inbox")
            }
            if (done.size > 2_000) done.clear()
        }
    }

    private val _scanResults = MutableStateFlow<List<FoundDevice>>(emptyList())
    val scanResults: StateFlow<List<FoundDevice>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null
    private var started = false

    private val _suggestedDevice = MutableStateFlow<FoundDevice?>(null)

    /**
     * Radio on Wi-Fi or one assigned earlier via Bluetooth, as long as none is connected: none paired
     * or the paired one not reachable right now. The message list then shows a card with „Verbinden“ and
     * „Später“ (like the card „Bluetooth erlauben“), only after setup.
     */
    val suggestedDevice: StateFlow<FoundDevice?> =
        combine(_suggestedDevice, setupDone, pairedDevices.suggestionPostponedUntil) { found, done, until ->
            found?.takeIf { done && Clock.System.now().toEpochMilliseconds() >= until }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    fun acceptSuggestedDevice() {
        _suggestedDevice.value?.let(::pair)
        _suggestedDevice.value = null
    }

    fun postponeSuggestedDevice() {
        pairedDevices.postponeSuggestion(Clock.System.now().toEpochMilliseconds() + BACKGROUND_HINT_PAUSE.inWholeMilliseconds)
        _suggestedDevice.value = null
    }

    /**
     * Searches for radios on its own as long as none is connected and the app is visible: on Wi-Fi any (a device in
     * the own network is very likely the own one), via Bluetooth only one that was assigned here before
     * (one in radio range may be someone else's). A paired device first gets [SUGGEST_GRACE] to connect;
     * if it is not there afterwards, another one is offered (or the same over Wi-Fi instead of Bluetooth).
     *
     * On Wi-Fi only when a radio was used here before and the system will not step in ([suggestWifi]): otherwise
     * Android 17 shows its dialog „Gerät zum Verbinden auswählen“ on every search, so people without a radio would see
     * it each time the app opens. Without a radio the search on Wi-Fi only runs under „Funkgerät verbinden“ ([startScan]).
     */
    private suspend fun suggestDevices() {
        combine(pairedDevices.device, linkState, inForeground, pairedDevices.disconnected) { device, state, visible, off ->
            // Stepped back for another phone or app: do not offer another radio either.
            Triple(device, state == LinkState.Connected || state == LinkState.InUseElsewhere || off, visible)
        }
            .distinctUntilChanged()
            .collectLatest { (device, connected, visible) ->
                // The suggestion is only seen in the app: do not search on Wi-Fi in the background (battery).
                if (!visible) return@collectLatest
                _suggestedDevice.value = null
                // Connected or disconnected by hand: offer nothing.
                if (connected) return@collectLatest
                if (device != null) delay(SUGGEST_GRACE)
                while (true) {
                    // If the search under „Funkgerät verbinden“ is running, the user chooses there themselves; after
                    // „Später“ there is no search at all.
                    val postponed = Clock.System.now().toEpochMilliseconds() < pairedDevices.suggestionPostponedUntil.value
                    if (scanJob?.isActive != true && !postponed) {
                        val known = pairedDevices.known.value
                        val sources = buildList {
                            if (suggestWifi(known)) add(scanNetwork().catch { })
                            if (hooks.bluetoothPermissionGranted.value && known.isNotEmpty()) {
                                add(scanBluetooth().catch { }.filter { it.identifier in known })
                            }
                        }
                        val found = if (sources.isEmpty()) null else withTimeoutOrNull(SUGGEST_SCAN_DURATION) {
                            merge(*sources.toTypedArray()).first { it.identifier != device?.identifier }
                        }
                        if (found != null) {
                            _suggestedDevice.value = found
                            return@collectLatest
                        }
                    }
                    delay(SUGGEST_SCAN_PAUSE)
                }
            }
    }

    /**
     * Search on Wi-Fi on its own only if a radio was assigned here before (paired now or under „Meine Funkgeräte“)
     * and the local network may be searched without a system dialog (Android 17+: permission granted).
     */
    private fun suggestWifi(known: Set<String>): Boolean = known.isNotEmpty() && hooks.localNetworkAllowed.value

    /**
     * Ends this controller for a restart within the process (iOS, [app.anynear.data.restartApp]): disconnect the radio,
     * stop all work of [scope] and wait briefly for it, so that nothing writes any more when the next controller reads
     * the stored state. Afterwards this controller is unusable.
     */
    suspend fun shutdown() {
        withTimeoutOrNull(SHUTDOWN_WAIT) { link.stop() }
        val job = scope.coroutineContext[Job] ?: return
        job.cancel()
        withTimeoutOrNull(SHUTDOWN_WAIT) { job.join() }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        outbox.start()
        startBadge(alerts?.unreadCount)
        radioSoftware?.start()
        // After the reset via USB: count failures anew and recommend no rescue for a while.
        usbRescue?.let { rescue ->
            scope.launch {
                rescue.state.collect {
                    if (it is UsbRescueState.Done && it.step == UsbRescueStep.RESET) {
                        BluetoothBonding.succeeded()
                        pairingQuietUntil.value = nowMillis() + PAIRING_QUIET_AFTER_RESET.inWholeMilliseconds
                    }
                }
            }
        }
        // Remember the radio's own name while it is connected, so that „Meine Funkgeräte“ shows it later too instead of
        // „Funkgerät“ or the stand-in name of the search.
        scope.launch {
            combine(pairedDevices.device, link.state, link.ownDevice) { device, state, own ->
                if (state == LinkState.Connected) device?.identifier?.let { id -> own?.longName?.takeIf { it.isNotBlank() }?.let { id to it } } else null
            }.filterNotNull().distinctUntilChanged().collect { (id, name) -> pairedDevices.rememberName(id, name) }
        }
        // Remember the model: if the radio later hangs in the bootloader, the USB rescue knows which file fits.
        scope.launch { link.firmware.filterNotNull().collect { store.putString(LAST_RADIO_MODEL_KEY, it.hwModel) } }
        // Subscribe to events of the core before it starts, so that none is lost.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { core.events.collect { e -> runCatching { onCoreEvent(e) }.onFailure { if (it is CancellationException) throw it; log("Ereignis ${e::class.simpleName}: ${it.message}") } } }
        // Removed, also already before this start (e.g. while the app was closed): delete everything here.
        scope.launch { core.revoked.first { it }; removedHere() }
        scope.launch {
            // Note when this device began to load before anything arrives (ADR 0107 §8). Noted only once a message came
            // back while loading, a circle joined later counted everything sent before that moment as read and sent no
            // read receipt for it.
            loadingSince()
            val me = core.start()
            // What the others share was loaded with the core just now; the sharer made before it has not seen it yet.
            placeSharer?.reload()
            _myDeviceId.value = me.deviceId
            _myPersonId.value = me.personId
            launch { refreshContactRequests() }
            // Only a stand-in if this device has no name yet (e.g. after the emergency card); the name typed when adding
            // it by QR stays instead of turning back into the model name at every start.
            ownDeviceNameToSet(core.ownDevices.value.firstOrNull { it.deviceId == me.deviceId }?.name, platformDeviceName())
                ?.let { core.setOwnDeviceName(it) }
            runCatching { reattributeRemovedDevices(me.personId) }.onFailure { if (it is CancellationException) throw it }
            // Own name and radio go along with every registration in the circle; afterwards the contact link.
            combine(profile.ownName, profile.ownNodeNum, link.ownDevice) { name, node, _ -> name to node }
                .distinctUntilChanged()
                .collectLatest { (name, node) ->
                    core.setProfile(name, node.toUInt().toLong())
                    _ownContactLink.value = core.ownContactLink(link.ownContact()?.let(ContactRepository::bytesOf))
                }
        }
        // iOS: register the push target with the core; it registers it at the relays as a watch (ADR 0009, 0035 §5).
        scope.launch { hooks.pushToken.collect { t: PushToken? -> core.setPushTarget(t?.token, t?.topic.orEmpty(), t?.sandbox == true) } }
        // Own picture to the core; it stores it per circle and contact. Also at startup in case it does not have it yet
        // (e.g. chosen before the core): it recognizes the same picture by the hash. Without a picture do nothing at
        // startup, so that a newer picture from another own device is not lost.
        scope.launch {
            profile.ownImage.withIndex().collect { (i, own) ->
                if (i == 0 && own.image == null) return@collect
                core.setOwnImage(own.image?.let { avatars.bytes(it) })
            }
        }
        // After setup the app also receives in the background, with or without a circle (Android: foreground service, ADR 0008).
        scope.launch { setupDone.collect(hooks::setMessageReception) }
        // Open conversation of an app in the foreground counts as read, its notification disappears.
        scope.launch {
            combine(readable, openConversation) { read, open -> open.takeIf { read } }
                .filterNotNull()
                .collect { conversation ->
                    markConversationRead(conversation)
                    cancelNotification(conversation)
                }
        }
        scope.launch {
            combine(pairedDevices.device, hooks.bluetoothPermissionGranted, pairedDevices.disconnected) { device, granted, off ->
                device.takeUnless { off } to granted
            }
                .distinctUntilChanged()
                .collect { (device, granted) ->
                    if (device != null && (granted || NetworkTransport.isNetworkDevice(device))) {
                        hooks.setBackgroundConnection(true)
                        link.start(device)
                    } else {
                        link.stop()
                        hooks.setBackgroundConnection(false)
                    }
                }
        }
        scope.launch {
            link.incoming.collect { incoming ->
                val fromPeer = peerForNode(incoming.fromNodeNum)
                val conversation = if (incoming.direct) {
                    Conversation.Direct(fromPeer)
                } else {
                    // The radio channel is public (ADR 0051): texts to everyone belong to no circle.
                    return@collect
                }
                if (incoming.reactionTo != 0) {
                    onRadioReaction(incoming, conversation, fromPeer)
                    return@collect
                }
                if (messageStore.addIncoming(incoming, localId = newId(), conversation = conversation, fromPeer = fromPeer)) {
                    onReceived(conversation)
                }
            }
        }
        startAttachmentTransfers()
        // Remember the own radio so that messages are assigned correctly even without a connection.
        scope.launch { link.ownDevice.filterNotNull().collect { profile.setOwnNodeNum(it.nodeNum) } }
        // With every new channel list give all contacts (again) to the radio so that it knows their
        // keys, and kick off waiting radio messages. addContact is idempotent.
        scope.launch {
            link.circleChannels.filterNotNull().distinctUntilChanged().collect {
                contactRepository.contacts.value.forEach { link.registerContact(it) }
                outbox.wake()
            }
        }
        scope.launch { syncRadioChannel() }
        publicRadio?.let { pr ->
            pr.onNotify = ::notifyAboutPublic
            pr.start()
            scope.launch { syncPublicChannel(pr) }
        }
        scope.launch { core.startRadio(radioPort, radioHost) }
        scope.launch { watchConnectionDrops() }
        scope.launch { watchCharge() }
        scope.launch { radioFallbackLoop() }
        scope.launch { removeOldRadioChannels() }
        scope.launch { suggestDevices() }
        scope.launch { keepOwnHistory() }
        settingsSync?.start()
        scope.launch { watchAlwaysLost() }
        scope.launch { tellSosPrecise() }
        refreshSosNoPrecise()
        scope.launch { runCatching { offerReplace() } }
        startImageTransfers()
        passwords?.onVaultChanged = { data, changed, key -> scope.launch { core.vaultPut(data, changed, key) } }
        passwords?.onKeyAvailable = ::onPasswordKey
        scope.launch {
            core.identity.filterNotNull().first()
            pullVault()
        }
        // Who is support? On the support identity itself it stays empty.
        scope.launch {
            core.identity.filterNotNull().first()
            refreshSupportLink()
        }
        // Support identity (ADR 0065): every new request additionally as an alert in the own channel „anynear-support“.
        // Look again even if the channels are only loaded after the messages (at startup).
        scope.launch {
            val channels = alerts?.channels ?: return@launch
            combine(messageStore.messages, channels) { messages, _ -> messages }.collect { forwardSupportRequests(it) }
        }
        // Profile and settings of the person (ADR 0050): first sync, then every change here to the core.
        scope.launch {
            core.identity.filterNotNull().first()
            runCatching { syncProfile() }
            // Briefly collected, so reading in an open chat does not write the profile space for every message.
            profile.personalSettingsChanged.distinctUntilChanged().drop(1).debounce(PERSONAL_SETTINGS_DELAY).collect { values ->
                core.setSettings(values.mapValues { it.value.encodeToByteArray() })
            }
        }
        // Read up to a point on an own device, also on a phone back from the emergency card (ADR 0107 §8).
        scope.launch {
            profile.readUntil.collect { marks -> applyReadUntil(marks) }
        }
        // A device became a person (ADR 0042): conversations, receipts and drafts move silently to the person, and a
        // conversation open under the device follows ([peerRenamed]), e.g. right after „Hinzufügen und schreiben“.
        scope.launch {
            val told = mutableSetOf<String>()
            memberDirectory.members.collect { list ->
                list.filter { it.personId != it.deviceId }.map { it.deviceId to it.personId }.distinct().forEach { (device, person) ->
                    val old = PeerId.device(device)
                    val new = PeerId.device(person)
                    if (messageStore.renamePeer(old, new)) log("Gespräch von Gerät auf Person umgestellt")
                    if (told.add(device)) {
                        drafts.move(Conversation.Direct(old), Conversation.Direct(new))
                        _peerRenamed.tryEmit(old to new)
                    }
                }
            }
        }
        scope.launch { core.identity.filterNotNull().collect { _myPersonId.value = it.personId } }
    }

    private val _peerRenamed = MutableSharedFlow<Pair<String, String>>(replay = 1, extraBufferCapacity = 16)

    /**
     * A conversation partner known by a device became a person (old peer → new peer, ADR 0042). The UI moves an open
     * conversation along; its messages and draft are already under the new key.
     */
    val peerRenamed: SharedFlow<Pair<String, String>> = _peerRenamed.asSharedFlow()

    /** What the core needs from the app: history for new members and pictures or attachments for `want` (ADR 0014). */
    val coreHost: AnmpCoreHost = object : AnmpCoreHost {
        override suspend fun circleHistory(circleId: String): List<ByteArray> =
            messageStore.messages.value
                .filter { it.conversation == Conversation.Circle(circleId) && it.signed != null }
                .sortedBy { it.timestampMillis }
                .mapNotNull { m -> m.signed?.let { runCatching { b64.decode(it) }.getOrNull() } }

        override suspend fun sharedContent(conversation: CoreConversation, hash: String, fromDevice: String): ByteArray? {
            localImage(hash)?.let { ref -> avatars.bytes(ref)?.let { return it } }
            val circleId = (conversation as? CoreConversation.Circle)?.circleId
            val attachment = AttachmentWants.shareable(messageStore.messages.value, hash, circleId, fromDevice, attachmentFiles::exists) ?: return null
            return withContext(Dispatchers.Default) { attachmentFiles.read(attachment.blobId) }
        }
    }

    // --- Events of the core --------------------------------------------------------------

    /** History of a newly joined circle until it is complete (ADR 0014). */
    private val historyBuffer = mutableMapOf<String, MutableList<ChatMessage>>()

    private suspend fun onCoreEvent(event: CoreEvent) {
        when (event) {
            is CoreEvent.Message -> {
                // Something came in, the app is awake anyway: a good moment for the own place (ADR 0121 §24).
                placeSharer?.touch()
                onCoreMessage(event)
            }
            is CoreEvent.Reaction -> onCoreReaction(event)
            is CoreEvent.Deleted -> onCoreDeleted(event)
            is CoreEvent.Receipt -> event.mids.forEach { mid ->
                val peer = peerOfDevice(event.fromDevice)
                val via = if (event.viaRadio) Transport.RADIO else Transport.INTERNET
                if (event.read) {
                    logReadArrival(mid, peer, via)
                    messageStore.confirmRead(mid, peer, via)
                } else {
                    // If the receipt says how the message arrived over there, that counts; else how it came itself (ADR 0058).
                    val arrived = buildSet {
                        if (event.arrived and AnmpArrived.INTERNET != 0) add(Transport.INTERNET)
                        if (event.arrived and AnmpArrived.RADIO != 0) add(Transport.RADIO)
                    }.ifEmpty { setOf(via) }
                    arrived.forEach { messageStore.confirmDelivery(mid, peer, it) }
                }
            }
            is CoreEvent.RadioMessage -> onRadioMessage(event)
            is CoreEvent.RadioSend -> {
                if (event.packetId != 0) messageStore.setRadioPacket(event.mid, event.packetId)
                messageStore.updateStatus(event.mid, event.state.toMessageStatus(), Transport.RADIO)
            }
            is CoreEvent.Stored -> messageStore.find(event.ref)?.takeIf { it.outgoing }?.let { m ->
                // Is at at least one relay: "sent"; the attachments were handled before.
                m.attachments.filter { it.state == AttachmentState.UPLOADING }.forEach { messageStore.updateAttachment(m.localId, it.blobId, AttachmentState.READY) }
                if (Transport.INTERNET !in m.via) messageStore.updateStatus(m.localId, MessageStatus.ONLINE, Transport.INTERNET)
            }
            is CoreEvent.SendFailed -> messageStore.find(event.ref)?.takeIf { it.outgoing && it.via.isEmpty() }?.let { messageStore.markFailed(it.localId) }
            is CoreEvent.ContactUnreachable -> findPerson(PeerId.device(event.personId))?.let { person ->
                messageStore.addSystem(newId(), Conversation.Direct(person.peer), texts.status.lineUnreachable(person.name.ifBlank { texts.status.someone }))
            }
            is CoreEvent.ContactReachable -> Unit
            is CoreEvent.ContactRequestsChanged -> scope.launch { refreshContactRequests() }
            is CoreEvent.IntroAccepted -> messageStore.addSystem(
                newId(), Conversation.Direct(PeerId.device(event.toPerson)), texts.people.introAcceptedLine(peerName(PeerId.device(event.toPerson)), event.name),
            )
            is CoreEvent.MemberUpdated -> if (event.member.image != null) imageWake.trySend(Unit)
            is CoreEvent.MemberLeft -> {
                messageStore.dropRecipient(Conversation.Circle(event.circleId), peerOfDevice(event.deviceId))
                if (circleRepository.find(event.circleId)?.removed == false) systemLine(event.circleId, texts.status.lineLeft(event.name.ifBlank { texts.status.someone }))
            }
            is CoreEvent.CircleUpdated -> if (event.circle.image != null) imageWake.trySend(Unit)
            is CoreEvent.CircleGone -> {
                _radioProblems.update { it - event.circleId }
                forgetCircleData(event.circleId)
            }
            is CoreEvent.CircleRenamed -> systemLine(event.circleId, texts.status.lineRenamed(memberName(event.circleId, event.byDevice), event.name))
            is CoreEvent.CircleImageChanged -> {
                val who = memberName(event.circleId, event.byDevice)
                systemLine(event.circleId, if (event.removed) texts.status.lineImageRemoved(who) else texts.status.lineImageChanged(who))
                imageWake.trySend(Unit)
            }
            is CoreEvent.KeyChanged -> onKeyChanged(event)
            is CoreEvent.HistoryLoaded -> {
                val items = historyBuffer.remove(event.circleId).orEmpty()
                messageStore.addHistory(items)
                downloadWake.trySend(Unit)
            }
            is CoreEvent.Profile -> if (event.image != null) imageWake.trySend(Unit)
            // Alerts (ADR 0062) are taken over by the area „Meldungen“ (Alerts) itself.
            is CoreEvent.Alert -> Unit
            // Check-ins (ADR 0083): [LifeSigns] reads them from the core itself.
            is CoreEvent.AliveChanged -> Unit
            // Only the iOS notification extension reports these (wakeFetch); the running core emits AliveChanged.
            is CoreEvent.AliveWake -> Unit
            is CoreEvent.SosWake -> Unit
            // Shared places (ADR 0121): [app.anynear.location.PlaceSharer] reads them from the core itself.
            is CoreEvent.PlaceChanged -> Unit
            // Read by [app.anynear.location.CirclePlaces].
            is CoreEvent.CirclePlacesChanged -> Unit
            is CoreEvent.PlaceAsked -> Unit
            // Calls for the helpers (ADR 0083 §2): [app.anynear.sos.Sos] reads them from the core itself.
            is CoreEvent.SosChanged -> Unit
            // Read by [app.anynear.sos.Sos].
            is CoreEvent.SosAsked -> Unit
            is CoreEvent.SosContactsChanged -> refreshSosNoPrecise()
            is CoreEvent.SharingChanged -> Unit
            is CoreEvent.Have -> onHave(event.hash, event.ref)
            is CoreEvent.ReadElsewhere -> {
                val conversation = conversationOf(event.conversation)
                if (messageStore.markReadElsewhere(conversation, event.mids)) cancelNotification(conversation)
            }
            is CoreEvent.DevicesChanged -> onDevicesChanged(event)
            is CoreEvent.OwnData -> onOwnData(event)
            is CoreEvent.ProfileChanged -> onProfileChanged(event)
            is CoreEvent.Revoked -> removedHere()
            is CoreEvent.PersonConflict -> log("Zwei verschiedene Gerätelisten für ${event.personId}")
        }
    }

    /** Recipients of an own message expected in the circle [circleId]: all other persons. */
    private fun circleRecipients(circleId: String): Set<String> =
        memberDirectory.of(circleId).map { it.personId }.filter { it != _myDeviceId.value && it != _myPersonId.value }.map(PeerId::device).toSet()

    /** Recipients of an own message that another own device sent; as when sending here. */
    private fun ownCopyRecipients(conversation: Conversation): Set<String> = when (conversation) {
        is Conversation.Circle -> circleRecipients(conversation.circleId)
        is Conversation.Direct -> PeerId.deviceIdOf(conversation.peer)
            ?.takeIf { it != _myDeviceId.value && it != _myPersonId.value }?.let { setOf(conversation.peer) }.orEmpty()
    }

    /**
     * Messages kept under a device that the core could not name a person for: until ANMP knew removed devices in the
     * circle history, the old phone after the emergency card was „someone else“ (FUT B8). Own ones become own again,
     * those of another person move to that person.
     */
    private suspend fun reattributeRemovedDevices(myPerson: String) {
        val devices = messageStore.messages.value
            .filter { !it.outgoing && it.system == null && it.fromDevice != null && it.fromPeer == PeerId.device(it.fromDevice) }
            .mapNotNull { it.fromDevice }.toSet()
        if (devices.isEmpty()) return
        val own = mutableSetOf<String>()
        for (device in devices) {
            val person = core.personOfDevice(device)
            when {
                person == myPerson -> own += device
                person != device -> messageStore.renamePeer(PeerId.device(device), PeerId.device(person))
            }
        }
        val fixed = messageStore.claimOwn(own, ::ownCopyRecipients)
        if (fixed > 0) log("$fixed eigene Nachrichten eines entfernten Geräts wieder als eigene")
    }

    private fun conversationOf(c: CoreConversation): Conversation = when (c) {
        is CoreConversation.Circle -> Conversation.Circle(c.circleId)
        is CoreConversation.Direct -> Conversation.Direct(PeerId.device(c.personId))
    }

    private fun coreConversationOf(c: Conversation): CoreConversation? = when (c) {
        is Conversation.Circle -> CoreConversation.Circle(c.circleId)
        is Conversation.Direct -> PeerId.deviceIdOf(c.peer)?.let { CoreConversation.Direct(it) }
    }

    private fun onCoreMessage(e: CoreEvent.Message) {
        val peer = PeerId.device(e.fromPerson)
        // From another own device (ADR 0042): an own message, e.g. written on the phone.
        val own = e.fromPerson == _myPersonId.value
        val conversation = conversationOf(e.conversation)
        val message = ChatMessage(
            localId = e.mid?.takeIf { own } ?: newId(),
            conversation = conversation,
            fromNodeNum = e.fromNode.toInt(),
            text = e.text,
            timestampMillis = e.timestampMillis,
            outgoing = own,
            status = MessageStatus.ONLINE.takeIf { own },
            // The delivery receipts go to all own devices (ADR 0042); without recipients none of them would count.
            recipients = if (own) ownCopyRecipients(conversation) else emptySet(),
            // Copy of an own message: the other device sent it, never send it again here.
            via = if (own) setOf(Transport.INTERNET) else emptySet(),
            fromPeer = peer.takeIf { !own },
            fromDevice = e.fromDevice,
            senderName = e.fromName,
            attachments = e.attachments.mapNotNull(::attachmentOf),
            link = e.link?.let { l -> LinkPreview(l.url, l.title, l.description, l.site, l.image?.let(::attachmentOf)) },
            remoteId = e.mid,
            replyTo = replyRef(e.replyTo, e.replyFrom),
            passedOn = e.passedOn,
            spot = e.spot?.let { sp -> ChatSpot(sp.kind, sp.position?.latitude, sp.position?.longitude, sp.position?.at ?: 0, sp.position?.accuracy, sp.label, sp.until, sp.level) },
            signed = e.opened?.let(b64::encode),
            space = e.space.ifEmpty { null },
            intro = e.intro?.let { ContactIntro(it.id, it.person, it.name, it.byPerson, it.until, it.card) },
        )
        if (e.history && conversation is Conversation.Circle) {
            historyBuffer.getOrPut(conversation.circleId) { mutableListOf() } += message
            return
        }
        // Passed on at the shared radio (ADR 0022): the sender gets the delivery receipt as over the internet.
        if (e.forwarded) e.mid?.let { core.confirmReceipt(e.fromDevice, it, AnmpArrived.INTERNET) }
        val read = !own && readOnArrival(conversation, message.timestampMillis)
        val added = messageStore.addOnline(e.objectId, message, read)
        if (message.attachments.isNotEmpty() || message.link?.image != null) downloadWake.trySend(Unit)
        // A person that still asks to become a contact (ADR 0116): one notice for the request, none per message.
        if (added && !read && !e.fromRequest) onReceived(conversation)
    }

    /** Attachment of a received message; unknown kinds are skipped. */
    private fun attachmentOf(a: AnmpAttachment): Attachment? {
        val kind = AttachmentKind.fromWire(a.type) ?: return null
        return Attachment(
            blobId = b64.encode(a.id),
            key = b64.encode(a.key),
            kind = kind,
            name = a.name.orEmpty(),
            size = a.size,
            width = a.width,
            height = a.height,
            state = AttachmentState.DOWNLOADING,
            hash = a.hash?.let(b64::encode),
            durationMillis = a.duration,
            transcribed = a.transcribed,
        )
    }

    private fun onCoreReaction(event: CoreEvent.Reaction) {
        val conversation = conversationOf(event.conversation)
        // Reference via the author's ANMP identifier (ADR 0021); the same reaction over radio does not count twice,
        // because per person the newest applies. Own messages can also be from another own device (ADR 0042).
        val targetTag = runCatching { b64.decode(event.targetTag) }.getOrNull() ?: return
        val own = event.targetTag == myTag || memberForTag(targetTag)?.let { isOwnDevice(it.deviceId) } == true
        val author = if (own) null else memberForTag(targetTag)?.let { peerOfDevice(it.deviceId) } ?: return
        val target = messageStore.findByRemote(conversation, author, event.targetMid, own) ?: return
        val reactor = if (isOwnDevice(event.fromDevice)) Reaction.SELF else peerOfDevice(event.fromDevice)
        if (messageStore.setReaction(target.localId, reactor, event.emoji, event.timestampMillis)) {
            onReactionReceived(target, reactor, event.emoji, event.timestampMillis)
        }
    }

    /**
     * The author has deleted a message for everyone (ADR 0068); the core has checked that the deletion comes from the
     * same person. Unknown message: placeholder, so that it does not appear at all later.
     */
    private fun onCoreDeleted(event: CoreEvent.Deleted) {
        val conversation = conversationOf(event.conversation)
        val targetTag = runCatching { b64.decode(event.targetTag) }.getOrNull() ?: return
        val own = event.targetTag == myTag || memberForTag(targetTag)?.let { isOwnDevice(it.deviceId) } == true
        val authorDevice = if (own) null else memberForTag(targetTag)?.deviceId ?: return
        val author = authorDevice?.let(::peerOfDevice)
        val target = messageStore.findByRemote(conversation, author, event.targetMid, own)
        when {
            // A system card stays, also if an older app deletes it for everyone (22.11.2026).
            target != null -> if (!target.isSystemCard && messageStore.markDeleted(target.localId)) {
                reactionNotices.clear(conversation)
                if (messageStore.messages.value.none { it.unread && it.conversation == conversation }) hooks.cancelMessageNotification(conversation.key())
                else notifyAbout(conversation)
            }
            author != null -> messageStore.addDeleted(newId(), conversation, author, authorDevice, event.targetMid, event.timestampMillis)
        }
    }

    /** Deleting for everyone works for own messages until [DELETE_FOR_EVERYONE_MILLIS] after sending (ADR 0068). */
    fun canDeleteForEveryone(message: ChatMessage): Boolean =
        message.outgoing && !message.deleted && !message.isSystemCard && nowMillis() - message.timestampMillis <= DELETE_FOR_EVERYONE_MILLIS

    /** Delete an own message for everyone (ADR 0068): here at once, to the others over the internet and, if connected, over radio. */
    fun deleteForEveryone(message: ChatMessage) {
        val current = messageStore.find(message.localId) ?: return
        if (!canDeleteForEveryone(current) || !messageStore.markDeleted(current.localId)) return
        val authorTag = (current.fromDevice ?: _myDeviceId.value)?.let(::tagOf)?.let(b64::encode) ?: myTag ?: return
        val mid = current.localId
        val ts = nowMillis()
        // What is still waiting does not go out any more, on no way.
        scope.launch { core.cancelSend(mid) }
        when (val conversation = current.conversation) {
            is Conversation.Circle -> {
                val circle = circleRepository.find(conversation.circleId)?.takeIf { !it.removed } ?: return
                scope.launch { core.deleteMessage(CoreConversation.Circle(circle.id), authorTag, mid) }
                if (link.state.value == LinkState.Connected) scope.launch { core.radioDelete(circle.id, null, authorTag, mid, ts) }
            }
            is Conversation.Direct -> {
                val person = findPerson(conversation.peer) ?: return
                person.deviceId?.let { device -> scope.launch { core.deleteMessage(CoreConversation.Direct(device), authorTag, mid) } }
                if (link.state.value == LinkState.Connected) {
                    radioDeviceOf(person)?.let { d -> scope.launch { core.radioDelete(null, d.deviceId, authorTag, mid, ts) } }
                }
            }
        }
    }

    /** Delete only on this device (ADR 0068); the others and the own other devices keep the message. */
    fun deleteForMe(message: ChatMessage) {
        // System cards stay (22.11.2026): they record what happened in the chat.
        if (messageStore.find(message.localId)?.isSystemCard ?: message.isSystemCard) return
        messageStore.remove(message.localId)
        // An own message that is still waiting does not go out any more, over the internet or over radio.
        if (message.outgoing) scope.launch { core.cancelSend(message.localId) }
    }

    /** Key rotation in the circle (ADR 0012), checked by the core: lines in the chat, notices, radio channel. */
    private suspend fun onKeyChanged(e: CoreEvent.KeyChanged) {
        val circle = circleRepository.find(e.circleId)
        val who = e.byName.ifBlank { texts.status.someone }
        e.removedDevice?.takeIf { it != _myDeviceId.value && !isOwnDevice(it) }?.let { messageStore.dropRecipient(Conversation.Circle(e.circleId), peerOfDevice(it)) }
        when (e.kind) {
            CoreEvent.KIND_YOU_REMOVED -> {
                val name = e.removedName.orEmpty().ifBlank { texts.status.someone }
                systemLine(e.circleId, texts.status.lineYouRemoved(name), SystemNote(e.removedDevice, null, name))
                _notice.value = texts.status.memberRemovedNotice(name)
            }
            CoreEvent.KIND_YOU_RESET -> {
                systemLine(e.circleId, texts.status.lineYouResetInvites)
                _notice.value = texts.status.invitesResetNotice
            }
            CoreEvent.KIND_READMITTED -> systemLine(e.circleId, texts.status.lineBackInCircle)
            CoreEvent.KIND_REMOVED_YOU -> {
                // Silently delete the history from the device (ADR 0014). The circle stays with this one line until
                // the deadline for a simultaneous counter-rotation has passed (ADR 0012), then it disappears.
                forgetCircleData(e.circleId)
                systemLine(e.circleId, texts.status.lineRemovedYou(who))
                circle?.let { _notice.value = texts.status.removedFromCircleNotice(it.name) }
            }
            CoreEvent.KIND_SWITCHED -> when {
                e.removedDevice != null && e.why != WHY_LEFT -> {
                    val name = e.removedName.orEmpty().ifBlank { texts.status.someone }
                    systemLine(e.circleId, texts.status.lineRemoved(who, name), SystemNote(e.removedDevice, null, name))
                }
                e.why == WHY_RESET -> systemLine(e.circleId, texts.status.lineResetInvites(who))
            }
        }
    }

    /** Clear away channels of earlier versions as soon as the radio is connected (ADR 0061, decision 6). */
    private suspend fun removeOldRadioChannels() {
        combine(linkState, link.oldCircleChannels) { state, old -> old.takeIf { state == LinkState.Connected }.orEmpty() }
            .distinctUntilChanged()
            .collect { old -> old.forEach { name -> if (link.removeChannel(name)) log("Alter Kanal $name entfernt") } }
    }

    private fun hasMessages(circleId: String): Boolean =
        messageStore.messages.value.any { it.conversation == Conversation.Circle(circleId) && it.system == null }

    /** Nobody but us in the circle and no message yet: the UI shows „Lade jemanden ein“. */
    fun isAlone(circleId: String): Boolean = memberDirectory.of(circleId).isEmpty() && !hasMessages(circleId)

    /**
     * Sets up the one radio channel `anynear` on the connected radio (ADR 0051, 0061), with the public key
     * from ANMP. Channels per circle no longer exist; [removeOldRadioChannels] clears them away. Channels of other apps
     * (Hessenmesh and the like) are never touched. Waits as long as the device applies changes.
     */
    private suspend fun syncRadioChannel() {
        val psk = AnmpRadio.channelPsk()
        var attempted = false
        combine(linkState, link.circleChannels, _deviceChange) { state, channels, change ->
            if (state == LinkState.Connected && change == null) channels else null
        }.collect { channels ->
            if (channels == null) {
                attempted = false
                return@collect
            }
            if (channels.any { it.name == AnmpInfo.RADIO_CHANNEL_NAME && it.psk == psk.toByteString() } || attempted) return@collect
            attempted = true
            val result = link.ensureChannel(AnmpInfo.RADIO_CHANNEL_NAME, psk)
            val problem = when (result) {
                is ChannelSetupResult.Failed -> texts.status.radioChannelFailed(result.reason)
                ChannelSetupResult.NoFreeSlot -> texts.status.radioChannelNoSlot
                else -> null
            }
            _radioChannelProblem.value = problem
            _radioProblems.value = if (problem == null) emptyMap() else circleRepository.circles.value.associate { it.id to problem }
            if (result == ChannelSetupResult.NoFreeSlot) log("Funkkanal ${AnmpInfo.RADIO_CHANNEL_NAME}: kein Platz frei")
        }
    }

    /**
     * Creates the public channel „Mesh Hessen“ as soon as the person has agreed to the offer (ADR 0054 §1a, ADR 0055),
     * once per connection and only if the device is not applying anything right now. An existing channel of the same
     * name is not touched by the app.
     */
    private suspend fun syncPublicChannel(publicRadio: PublicRadio) {
        var attempted = false
        combine(linkState, link.publicChannels, _deviceChange, publicRadio.community) { state, channels, change, yes ->
            if (state == LinkState.Connected && change == null && yes == true) channels else null
        }.collect { channels ->
            if (channels == null) {
                attempted = false
                return@collect
            }
            if (attempted || channels.any { it.channel == PublicChannel.MESH_HESSEN }) return@collect
            attempted = true
            publicRadio.ensureMeshHessen()
        }
    }

    /** Name of the contact the radio [nodeNum] belongs to (ADR 0055: „Pauls Funkgerät“); `null` if unknown. */
    fun contactNameForRadio(nodeNum: Int): String? =
        people.value.firstOrNull { nodeNum in knownRadios(it.peer) }?.let { peerName(it.peer) }

    /** Slot of the radio channel `anynear` on the connected radio; `null` as long as it is missing. */
    private fun radioChannelIndex(): Int? =
        link.circleChannels.value.orEmpty().firstOrNull { it.name == AnmpInfo.RADIO_CHANNEL_NAME }?.index

    /**
     * Channel for a Meshtastic text in [conversation]: to a radio without anynear the channel its last message came on
     * (it does not know `anynear`), otherwise the radio channel `anynear`.
     */
    private fun radioChannelFor(conversation: Conversation?): Int? {
        val direct = conversation as? Conversation.Direct
        if (direct != null && isRadioOnly(direct.peer)) replyChannel(messageStore.messages.value, direct)?.let { return it }
        return radioChannelIndex()
    }

    /** Radio without anynear (e.g. the Meshtastic app): reachable only over radio, as plain text without attachments. */
    fun isRadioOnly(peer: String): Boolean {
        val person = findPerson(peer)
        return person?.deviceId == null && (person?.nodeNum ?: PeerId.nodeNumOf(peer)) != null
    }

    fun requestBluetoothPermission() = hooks.requestBluetoothPermission()

    fun requestNotificationPermission() = hooks.requestNotificationPermission()

    // --- Device assignment (optional) -------------------------------------------------

    fun startScan() {
        if (scanJob?.isActive == true) return
        _scanResults.value = emptyList()
        scanJob = scope.launch {
            _isScanning.value = true
            try {
                // Searched on purpose: ask once for the local network (Android 17+), so the system does not show its
                // own device picker on this and every later search. Declined, the search on Wi-Fi still runs and
                // Android shows its picker, which is fine here because the person asked for it.
                if (!hooks.localNetworkAllowed.value) hooks.requestLocalNetworkPermission()
                // Both searches run in parallel. If one fails (Bluetooth off, no Wi-Fi), the other keeps running.
                val sources = buildList {
                    add(scanNetwork().catch { })
                    if (hooks.bluetoothPermissionGranted.value) add(scanBluetooth().catch { })
                }
                withTimeoutOrNull(30.seconds) {
                    merge(*sources.toTypedArray()).collect { found ->
                        _scanResults.update { list -> mergeFound(list, found) }
                    }
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
    }

    /** Pair a newly found radio. If another one is assigned, it is replaced and stays under „Meine Funkgeräte“. */
    fun pair(device: FoundDevice) {
        stopScan()
        if (device.identifier != pairedDevices.device.value?.identifier) resetRadioState()
        pairedDevices.pair(PairedDevice(identifier = device.identifier, name = device.name))
    }

    sealed interface ManualConnectResult {
        data object Connected : ManualConnectResult

        data object InvalidAddress : ManualConnectResult

        data object NotReachable : ManualConnectResult
    }

    /**
     * „Erweitert“: assign a radio via a Wi-Fi address entered by hand.
     * Only accepted if a radio actually answers there.
     */
    suspend fun connectByAddress(input: String): ManualConnectResult {
        val address = NetworkTransport.parse(input) ?: return ManualConnectResult.InvalidAddress
        val device = NetworkTransport.deviceFor(address)
        stopScan()
        if (!link.probe(device)) return ManualConnectResult.NotReachable
        pairedDevices.pair(device)
        return ManualConnectResult.Connected
    }

    /** „Trennen“: end the connection to the radio without forgetting it. */
    fun disconnectRadio() = pairedDevices.setDisconnected(true)

    /** „Verbinden“ after [disconnectRadio]. */
    fun reconnectRadio() = pairedDevices.setDisconnected(false)

    /** Use a device from „Meine Funkgeräte“ again. */
    fun pairKnown(device: PairedDevice) {
        if (device.identifier == pairedDevices.device.value?.identifier) return reconnectRadio()
        stopScan()
        resetRadioState()
        pairedDevices.pair(device)
    }

    /** Remove a device from „Meine Funkgeräte“; if it is the assigned one, it is released in the process. */
    fun forgetDevice(identifier: String) {
        if (identifier == pairedDevices.device.value?.identifier) unpair()
        pairedDevices.forget(identifier)
    }

    fun unpair() {
        pairedDevices.unpair()
        resetRadioState()
    }

    private fun resetRadioState() {
        profile.setOwnNodeNum(0)
        _radioProblems.value = emptyMap()
        _internetOnly.value = emptySet()
    }

    // --- Messages ----------------------------------------------------------------------

    sealed interface SendResult {
        data object Sent : SendResult

        data class Rejected(val reason: String) : SendResult
    }

    /** Write attachments of the drafts one after another, in the order of the changes ([DraftStore]). */
    private val draftIo = Dispatchers.Default.limitedParallelism(1)

    /** Stored draft of a conversation, also after a restart. */
    fun draftText(conversation: Conversation): String = drafts.text(conversation)

    fun saveDraftText(conversation: Conversation, text: String) = drafts.setText(conversation, text)

    /** The draft of [conversation] came from „Weiterleiten“ (ADR 0118). */
    fun draftPassedOn(conversation: Conversation): Boolean = drafts.passedOn(conversation)

    fun saveDraftPassedOn(conversation: Conversation, passedOn: Boolean) = drafts.setPassedOn(conversation, passedOn)

    suspend fun draftAttachments(conversation: Conversation): List<DraftAttachment> =
        withContext(draftIo) { drafts.attachments(conversation) }

    fun saveDraftAttachments(conversation: Conversation, attachments: List<DraftAttachment>) {
        scope.launch(draftIo) { runCatching { drafts.setAttachments(conversation, attachments) } }
    }

    /** Shared from another app (share menu of the system), until one has chosen whom it goes to. */
    class SharedContent(
        val text: String,
        val files: List<File>,
        /** Attachments ready as they are (forwarding: photos are not shrunk a second time). */
        val drafts: List<DraftAttachment> = emptyList(),
        /** Forwarded from a chat of the app („Weiterleiten“) instead of shared from another app. */
        val forward: Boolean = false,
    ) {
        /** [bytes] `null`: not readable or [tooLarge]. */
        class File(val bytes: ByteArray?, val name: String, val image: Boolean, val tooLarge: Boolean = false)
    }

    private val _shared = MutableStateFlow<SharedContent?>(null)

    /** Shared content for which the UI asks „An wen?“. */
    val shared: StateFlow<SharedContent?> = _shared.asStateFlow()

    /**
     * [target]: key of a conversation ([app.anynear.chats.key]) if a conversation was chosen directly in the share
     * menu. Then into it as a draft without „An wen?“ and open the conversation; unknown conversations ask as usual.
     */
    fun receiveShared(content: SharedContent, target: String? = null) {
        if (content.text.isBlank() && content.files.isEmpty() && content.drafts.isEmpty()) return
        val conversation = target?.takeIf { key -> chatList.value.any { it.key == key && !it.removed } }?.let(::conversationOf)
        if (conversation == null) {
            _shared.value = content
            return
        }
        scope.launch {
            putIntoDraft(conversation, content)?.let { _notice.value = it }
            _openRequest.value = conversation
        }
    }

    fun dismissShared() {
        _shared.value = null
    }

    /**
     * „Weiterleiten“: text and attachments of [message] go through the same „An wen?“ as something shared from another
     * app and then sit as a draft in the chosen conversation; only the send button sends. Attachments are taken from
     * the phone and sealed again with a new key for the new conversation (ANDP has no references across spaces, and a
     * new key keeps the same file in two conversations from being recognisable at the relays). Over radio only the
     * text goes, as with every message. @return what cannot be forwarded, `null` if it is on its way to „An wen?“.
     */
    suspend fun forward(message: ChatMessage): String? {
        val drafts = message.attachments.map { a ->
            val bytes = attachmentBytes(a.blobId) ?: return texts.chat.attachmentGone
            DraftAttachment(a.kind, bytes, name = a.name, width = a.width, height = a.height, durationMillis = a.durationMillis)
        }
        // The automatic text preview of a voice message is not the sender's words; the receiver's app makes its own.
        val text = if (message.attachments.any { it.transcribed }) "" else message.text
        receiveShared(SharedContent(text, emptyList(), drafts, forward = true))
        return null
    }

    /**
     * Puts the shared content as a draft into [conversation]: text below the existing draft, files to the attachments.
     * It is only sent with the send button, one sees beforehand what goes out. @return what was not taken over.
     */
    suspend fun putSharedIntoDraft(conversation: Conversation): String? {
        // The selection stays until the draft is in place (shrinking photos takes time); double tapping counts once.
        val content = _shared.value?.takeIf { !puttingShared } ?: return null
        puttingShared = true
        try {
            return putIntoDraft(conversation, content)
        } finally {
            puttingShared = false
            _shared.value = null
        }
    }

    private var puttingShared = false

    /**
     * Shared from another app or forwarded into a conversation (the „Weitergeleitet“ mark with [passedOn]): waits
     * here until the chat of the conversation takes it into its draft ([takeDraftAdditions]).
     */
    class DraftAddition(val text: String, val attachments: List<DraftAttachment>, val passedOn: Boolean = false)

    private val _draftAdditions = MutableStateFlow<Map<Conversation, List<DraftAddition>>>(emptyMap())

    /** Additions per conversation that its chat has not yet taken over. */
    val draftAdditions: StateFlow<Map<Conversation, List<DraftAddition>>> = _draftAdditions.asStateFlow()

    /** Takes the waiting additions of [conversation] (each only once). */
    fun takeDraftAdditions(conversation: Conversation): List<DraftAddition> {
        var taken = emptyList<DraftAddition>()
        _draftAdditions.update { all ->
            taken = all[conversation].orEmpty()
            all - conversation
        }
        return taken
    }

    private suspend fun putIntoDraft(conversation: Conversation, content: SharedContent): String? {
        val problems = mutableListOf<String>()
        val prepared = content.files.mapNotNullTo(mutableListOf()) { file ->
            val bytes = file.bytes
            when {
                file.tooLarge -> null.also { problems += texts.chat.fileTooLarge }
                bytes == null -> null.also { problems += texts.chat.fileUnreadable }
                file.image -> prepareImage(bytes).also { if (it == null) problems += texts.chat.photoUnreadable }
                else -> prepareFile(bytes, file.name).let { (d, problem) -> problem?.let { problems += it }; d }
            }
        }
        prepared += content.drafts
        // The chat takes it over into its draft as soon as it is open (also if it is open already); it then stores the
        // draft itself. Written into the store from here, the open chat did not see it and overwrote it on its next
        // change (tester report, round 2: shared photo missing until the app was restarted, or gone).
        val addition = DraftAddition(content.text.trim(), prepared, passedOn = content.forward)
        _draftAdditions.update { it + (conversation to (it[conversation].orEmpty() + addition)) }
        return problems.distinct().firstOrNull()
    }

    /**
     * Accepts the message at once and shows it. It goes over the internet to all mailboxes
     * and, if a radio is paired, over its queue (see [MessageOutbox]).
     * Attachments only go over the internet; over radio the text announces them (ADR 0011). A long text
     * likewise goes in full only over the internet, shortened over radio ([RadioAnnouncement.cut]). Without
     * internet the message waits on the phone together with its attachments, even across a restart.
     */
    fun send(
        conversation: Conversation,
        text: String,
        drafts: List<DraftAttachment> = emptyList(),
        transcript: VoiceTranscript? = null,
        /** Preview of the first web link (ADR 0069), already loaded; only goes along if the link is in the text. */
        linkDraft: LinkDraft? = null,
        /** Message this one answers (ADR 0092), from [replyRefOf]. */
        reply: ReplyRef? = null,
        /** Passes on a message one got („Weiterleiten“, ADR 0118): marked „Weitergeleitet“. */
        passedOn: Boolean = false,
        /** A meeting point, „Wo bist du?“ or the answer (ADR 0121 §8); [text] is what older apps show. */
        spot: ChatSpot? = null,
    ): SendResult {
        var body = text.trim()
        if (body.isEmpty() && drafts.isEmpty()) return SendResult.Rejected(texts.status.messageEmpty)
        // Sending is a moment the phone is in the hand anyway: the own place is brought up to date for those who see it
        // (ADR 0121 §24).
        placeSharer?.touch()
        if (drafts.size > AttachmentLimits.MAX_PER_MESSAGE) {
            return SendResult.Rejected(texts.status.tooManyAttachments(AttachmentLimits.MAX_PER_MESSAGE))
        }
        if (drafts.any { it.bytes.size > AttachmentLimits.MAX_BYTES }) return SendResult.Rejected(texts.status.fileTooLarge)
        // A voice message has exactly one recording and no other attachments (ADR 0030).
        val voice = drafts.firstOrNull { it.kind == AttachmentKind.AUDIO }
        if (voice != null && drafts.size > 1) return SendResult.Rejected(texts.status.tooManyAttachments(1))
        // Without own text the automatic text preview becomes the text, shortened to the radio budget (ADR 0030).
        val preview = if (voice != null && body.isEmpty()) transcript?.let { voicePreview(conversation, it) } else null
        if (preview != null) body = preview
        val kinds = drafts.map { it.kind }
        if (textLeft(conversation, body, kinds) < 0) return SendResult.Rejected(texts.status.messageTooLong)
        // A long text goes in full over the internet, over radio only the beginning (ADR 0031).
        val radioBody = RadioAnnouncement.cut(RadioAnnouncement.radioText(body, kinds, voice?.durationMillis ?: 0), radioTextLimit(conversation))

        // Conversation still under the device ID of a contact just scanned, the person has their person ID
        // by now: send there and switch the conversation to it, otherwise it would say „nicht erreichbar“.
        if (conversation is Conversation.Direct) {
            val current = findPerson(conversation.peer)?.peer
            if (current != null && current != conversation.peer) {
                val moved = Conversation.Direct(current)
                _openRequest.value = moved
                return send(moved, text, drafts, transcript, linkDraft, reply, passedOn, spot)
            }
        }

        // Contact just added via a link, the core is still storing it: do not reject, but wait briefly
        // and then send. Otherwise the first attempt would say „nicht erreichbar“, the second would work.
        if (conversation is Conversation.Direct && conversation.peer in addingContacts.value && !reachableNow(conversation.peer)) {
            scope.launch {
                if (awaitReachable(conversation.peer, CONTACT_SAVE_WAIT)) {
                    (send(conversation, text, drafts, transcript, linkDraft, reply, passedOn, spot) as? SendResult.Rejected)?.let { _notice.value = it.reason }
                } else {
                    _notice.value = texts.status.personUnreachable
                }
            }
            return SendResult.Sent
        }

        // Over which circle does the message go? For direct messages over a shared circle.
        val person = (conversation as? Conversation.Direct)?.let { findPerson(it.peer) }
        // Circle chat: this circle. Direct message: over the internet into the personal mailbox,
        // over radio in the channel of a shared (otherwise the selected) circle.
        val circle: Circle? = when (conversation) {
            is Conversation.Circle -> circleRepository.find(conversation.circleId)?.takeIf { !it.removed }
                ?: return SendResult.Rejected(texts.status.circleClosedForWriting)
            is Conversation.Direct -> person?.circleIds?.firstNotNullOfOrNull { id -> circleRepository.find(id)?.takeIf { !it.removed } }
                ?: selectedCircle.value?.takeIf { !it.removed }
        }

        val radioTarget = (conversation as? Conversation.Direct)?.let { person?.nodeNum ?: PeerId.nodeNumOf(it.peer) }
        val onlineDevice = person?.deviceId
        val onlineKey = person?.publicKey
        val radioAvailable = pairedDevices.device.value != null && !pairedDevices.disconnected.value
        if (conversation is Conversation.Direct && (onlineDevice == null || onlineKey == null)) {
            if (drafts.isNotEmpty()) return SendResult.Rejected(texts.status.attachmentsNeedApp)
            if (!(radioTarget != null && radioAvailable)) {
                return SendResult.Rejected(texts.status.personUnreachable)
            }
        }

        // A delivery receipt is expected from these devices (internet path).
        val recipients: Set<String> = when (conversation) {
            is Conversation.Circle -> circle?.let { c -> circleRecipients(c.id) }.orEmpty()
            is Conversation.Direct -> listOfNotNull(onlineDevice).filter { it != _myDeviceId.value && it != _myPersonId.value }.map(PeerId::device).toSet()
        }

        val attachments = drafts.map { draft ->
            Attachment(
                blobId = b64.encode(secureRandomBytes(16)),
                key = b64.encode(secureRandomBytes(32)),
                kind = draft.kind,
                name = if (draft.kind == AttachmentKind.PDF || draft.kind == AttachmentKind.FILE) shortenFileName(draft.name) else "",
                size = draft.bytes.size.toLong(),
                width = draft.width,
                height = draft.height,
                state = AttachmentState.UPLOADING,
                durationMillis = draft.durationMillis,
                transcribed = preview != null && draft.kind == AttachmentKind.AUDIO,
            )
        }
        // Preview only for the internet and only if the link really goes along (ADR 0069).
        val linkImageBytes = linkDraft?.image?.bytes
        val linkPreview = linkDraft?.takeIf { body.contains(it.url) && (conversation is Conversation.Circle || onlineDevice != null) }?.let { d ->
            LinkPreview(
                d.url, d.title, d.description, d.site,
                d.image?.let { img ->
                    Attachment(
                        blobId = b64.encode(secureRandomBytes(16)), key = b64.encode(secureRandomBytes(32)), kind = AttachmentKind.IMAGE,
                        name = "", size = img.bytes.size.toLong(), width = img.width, height = img.height, state = AttachmentState.UPLOADING,
                    )
                },
            )
        }
        // Message ID on all paths (ANMP, ADR 0021).
        val localId = newMessageId()
        val now = Clock.System.now().toEpochMilliseconds()
        // Until the attachments are stored encrypted, the preview comes from memory.
        if (attachments.isNotEmpty()) unsaved.update { it + attachments.mapIndexed { i, a -> a.blobId to drafts[i].bytes } }
        val linkImage = linkPreview?.image
        if (linkImage != null && linkImageBytes != null) unsaved.update { it + (linkImage.blobId to linkImageBytes) }
        messageStore.addOutgoing(
            ChatMessage(
                localId = localId,
                conversation = conversation,
                fromNodeNum = profile.ownNodeNum.value,
                text = body,
                timestampMillis = now,
                outgoing = true,
                status = MessageStatus.QUEUED,
                recipients = recipients,
                attachments = attachments,
                link = linkPreview,
                replyTo = reply,
                passedOn = passedOn,
                spot = spot,
            ),
        )
        if (radioAvailable) {
            // ANMP into the queue of the core, durable across restarts (ADR 0057); Meshtastic text to radios
            // without anynear stays in the queue of the app.
            // A message goes to each device of the person that may sit at a radio: one for the wrong device is dropped
            // silently there (hardware test 11.11.2026).
            val anmpDevices = (conversation as? Conversation.Direct)?.let { person?.let { p -> radioDevicesOf(p, memberDirectory.members.value) } }.orEmpty()
            when {
                conversation is Conversation.Circle -> scope.launch { core.queueRadioText(conversation.circleId, null, localId, now, radioBody, reply?.mid, reply?.tag, spot?.let(::coreSpot)) }
                anmpDevices.isNotEmpty() -> {
                    // Over radio the place the person sees anyway rides along: no packet of its own (ADR 0121 §20).
                    val ride = person?.deviceId?.let { placeSharer?.riding(it, radio = true) }?.let(::corePlace)
                    scope.launch { anmpDevices.forEach { core.queueRadioText(null, it.deviceId, localId, now, radioBody, reply?.mid, reply?.tag, spot?.let(::coreSpot), ride) } }
                }
                radioTarget != null -> outbox.enqueue(localId, radioBody, radioTarget, "")
            }
        }
        scope.launch {
            // First save attachments on the phone (encrypted), then store and send.
            attachments.forEachIndexed { i, a ->
                attachmentFiles.write(a.blobId, drafts[i].bytes)
                unsaved.update { it - a.blobId }
            }
            if (linkImage != null && linkImageBytes != null) {
                attachmentFiles.write(linkImage.blobId, linkImageBytes)
                unsaved.update { it - linkImage.blobId }
            }
            deliverOnline(localId)
        }
        return SendResult.Sent
    }

    // --- ANMP over radio (ADR 0021) ------------------------------------------------------------

    /**
     * Gives the message [localId] as Meshtastic text to a radio without anynear. ANMP messages go over the
     * queue of the core ([AnmpCore.queueRadioText]).
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun radioTransmit(localId: String, text: String, to: Int?, circleId: String): RadioSend {
        val channel = radioChannelFor(messageStore.find(localId)?.conversation) ?: throw NotReadyException("Funkkanal fehlt")
        // The channel is public (ADR 0051): there are no texts to everyone.
        if (to == null) throw NotReadyException("Ohne ANMP nur direkt an ein Funkgerät")
        return link.send(text, to, channel)
    }

    /** ANMP packet for an own message; `null` if it has to go as Meshtastic text. */
    private suspend fun anmpTextPacket(message: ChatMessage, text: String): ByteArray? {
        if (anmpId(message.localId) == null) return null
        return when (val conversation = message.conversation) {
            is Conversation.Circle -> core.radioTextPacket(conversation.circleId, null, message.localId, message.timestampMillis, text, message.replyTo?.mid, message.replyTo?.tag)
            is Conversation.Direct -> {
                val device = findPerson(conversation.peer)?.let(::radioDeviceOf) ?: return null
                core.radioTextPacket(null, device.deviceId, message.localId, message.timestampMillis, text, message.replyTo?.mid, message.replyTo?.tag)
            }
        }
    }

    /**
     * Device of a person for a single direct packet over radio (reaction, withdrawal): the most likely one at a radio
     * ([radioDevicesOf]). Radio address (route tag) and encryption apply per device, not per person; with the person ID
     * (ADR 0042) the recipient did not find the packet (hardware test 02.11.2026).
     */
    private fun radioDeviceOf(person: Person): Member? = radioDevicesOf(person, memberDirectory.members.value).firstOrNull()

    /** Installation for the ANMP identifier [tag], as far as known. */
    private fun memberForTag(tag: ByteArray): Member? =
        memberDirectory.members.value.lastOrNull { m -> tagOf(m.deviceId)?.contentEquals(tag) == true }

    /** ANMP identifier (8 bytes) of a device ID. */
    private fun tagOf(deviceId: String): ByteArray? = runCatching { Anmp.tagOf(b64.decode(deviceId)) }.getOrNull()

    /** Message over radio, opened and assigned by the core (ADR 0051, 0057). */
    private fun onRadioMessage(e: CoreEvent.RadioMessage) {
        val fromPeer = peerOfDevice(e.fromDevice)
        val direct = e.conversation is CoreConversation.Direct
        val conversation = if (direct) Conversation.Direct(fromPeer) else conversationOf(e.conversation)
        val incoming = ChatMessage(
            localId = newId(),
            conversation = conversation,
            fromNodeNum = e.fromNode,
            text = e.text,
            timestampMillis = e.timestampMillis,
            outgoing = false,
            fromPeer = fromPeer,
            fromDevice = e.fromDevice,
            senderName = e.fromName,
            remoteId = e.mid,
            relayed = e.relayed,
            replyTo = replyRef(e.replyTo, e.replyFrom),
            spot = e.spot?.let { sp -> ChatSpot(sp.kind, sp.position?.latitude, sp.position?.longitude, sp.position?.at ?: 0, sp.position?.accuracy, sp.label, sp.until, sp.level) },
        )
        if (!messageStore.addRadio(incoming, e.fromNode, e.packetKey)) return
        onReceived(conversation)
        // Over radio the core confirms itself; into the mailbox of the sender as soon as there is internet (ADR 0051).
        if (direct) core.confirmReceipt(e.fromDevice, e.mid, AnmpArrived.RADIO)
    }

    private fun replyRef(mid: String?, tag: String?): ReplyRef? = if (mid != null && tag != null) ReplyRef(mid, tag) else null

    /**
     * Reference for an answer to [message] (ADR 0092): its ANMP message ID and the tag of the installation that wrote
     * it. `null` for messages without an ANMP ID (system lines, Meshtastic texts from radios without anynear, deleted).
     */
    fun replyRefOf(message: ChatMessage): ReplyRef? {
        if (message.system != null || message.deleted) return null
        val mid = (if (message.outgoing) message.localId else message.remoteId)?.takeIf { anmpId(it) != null } ?: return null
        val device = message.fromDevice ?: if (message.outgoing) null else message.fromPeer?.let(PeerId::deviceIdOf)
        val tag = device?.let(::tagOf)?.let(b64::encode) ?: myTag.takeIf { message.outgoing } ?: return null
        return ReplyRef(mid, tag)
    }

    /** Own reaction as ANMP over radio; without a queue, otherwise only over the internet. */
    private suspend fun sendRadioReaction(target: ChatMessage, targetId: ByteArray, emoji: String, ts: Long) {
        val authorTag = if (target.outgoing) myTag else target.fromPeer?.let(PeerId::deviceIdOf)?.let(::tagOf)?.let(b64::encode)
        authorTag ?: return
        val mid = b64.encode(targetId)
        when (val conversation = target.conversation) {
            is Conversation.Circle -> core.radioReact(conversation.circleId, null, authorTag, mid, emoji, ts)
            is Conversation.Direct -> {
                val device = findPerson(conversation.peer)?.let(::radioDeviceOf) ?: return
                core.radioReact(null, device.deviceId, authorTag, mid, emoji, ts)
            }
        }
    }

    /**
     * Read receipt as ANMP to the installation [device] that wrote the messages [ids]; as many IDs
     * as fit into one radio packet (the newest).
     */
    private suspend fun sendRadioRead(device: String, ids: List<ByteArray>) {
        val sent = core.radioRead(device, ids.map(b64::encode))
        log(if (sent != null) "Gelesen an $device: Funk gesendet, Paket ${sent.packetId}" else "Gelesen an $device: Funk übersprungen")
    }

    // --- Reactions (ADR 0018) -----------------------------------------------------------------

    /**
     * Own reaction [emoji] to [message]; another emoji replaces it, empty takes it back
     * (ADR 0021). Is shown at once. Goes over the internet and, if the radio is connected right
     * now, also over radio: among anynear apps as ANMP, to radios without anynear as a Meshtastic reaction.
     */
    fun react(message: ChatMessage, emoji: String) {
        val current = messageStore.find(message.localId) ?: return
        // Empty takes back the own reaction; without an own reaction there is nothing to take back.
        val valid = if (emoji.isEmpty()) current.ownReaction != null else Reaction.isValid(emoji)
        // One does not react to own messages; an earlier own reaction can still be taken back.
        if (current.outgoing && emoji.isNotEmpty()) return
        if (current.system != null || current.ownReaction == emoji || !valid) return
        val ts = nowMillis()
        if (!messageStore.setReaction(current.localId, Reaction.SELF, emoji, ts)) return
        val conversation = current.conversation
        val circle = (conversation as? Conversation.Circle)?.circleId?.let(circleRepository::find)?.takeIf { !it.removed }
        val person = (conversation as? Conversation.Direct)?.let { findPerson(it.peer) }

        // Radio among anynear apps: ANMP with reference to the message ID (ADR 0021).
        val anmpTarget = if (current.outgoing) anmpId(current.localId) else anmpId(current.remoteId)
        if (anmpTarget != null && link.state.value == LinkState.Connected) {
            scope.launch { sendRadioReaction(current, anmpTarget, emoji, ts) }
        } else if (current.radioPacketId != 0 && emoji.isNotEmpty() && current.fromPeer?.let(PeerId::deviceIdOf) == null) {
            // Radio without anynear: Meshtastic reaction with reference to the packet ID of the message.
            radioRouteOf(conversation)?.let { (radioTarget, channel) ->
                val packetId = link.sendReaction(emoji, current.radioPacketId, radioTarget, channel) ?: 0
                if (packetId != 0) messageStore.rememberPacket(profile.ownNodeNum.value, packetId)
            }
        }

        // Internet: the reference is message ID and ANMP identifier of the author.
        val authorDevice = current.fromDevice ?: current.fromPeer?.let(PeerId::deviceIdOf)
        val authorTag = if (current.outgoing) (current.fromDevice ?: _myDeviceId.value)?.let(::tagOf)?.let(b64::encode) ?: myTag
            else authorDevice?.let(::tagOf)?.let(b64::encode)
        val mid = (if (current.outgoing) current.localId else current.remoteId) ?: return
        val target: CoreConversation = when {
            circle != null -> CoreConversation.Circle(circle.id)
            person?.deviceId != null -> CoreConversation.Direct(person.deviceId)
            else -> return
        }
        authorTag ?: return
        scope.launch { core.react(target, authorTag, mid, emoji) }
    }

    /** Meshtastic reaction over radio, from radios without anynear. Discarded without a known message. */
    private fun onRadioReaction(incoming: IncomingText, conversation: Conversation, fromPeer: String) {
        if (!Reaction.isValid(incoming.text)) return
        if (!messageStore.rememberPacket(incoming.fromNodeNum, incoming.packetId)) return
        val target = messageStore.findByRadioPacket(conversation, incoming.reactionTo) ?: return
        if (messageStore.setReaction(target.localId, fromPeer, incoming.text, incoming.timestampMillis)) {
            onReactionReceived(target, fromPeer, incoming.text, incoming.timestampMillis)
        }
    }


    /** Sign circle messages over radio too (ADR 0021); can be switched off under „Erweitert“. */


    /** Share usage statistics (ADR 0045), applies to the person. */
    val usageStatsOn: StateFlow<Boolean> = profile.usageStats

    /** Only from the UI: the switch is reported once (ADR 0045), values taken over from other devices are not. */
    fun setUsageStats(on: Boolean) {
        if (profile.usageStats.value == on) return
        profile.setUsageStats(on)
        usageStats?.onSwitched(on)
    }

    /** Test mode (debug build): no usage report is sent. */
    val usageStatsTestMode: Boolean get() = usageStats?.testMode == true

    /** What was sent last; `null` as long as nothing has been sent. */
    val usageStatsLast: StateFlow<UsageReport?> = usageStats?.last ?: MutableStateFlow(null)

    /** Pass on for others (ADR 0051), default on. */
    val relayForOthers: StateFlow<Boolean> = profile.relayForOthers

    fun setRelayForOthers(on: Boolean) = profile.setRelayForOthers(on)

    /** Remind to charge the radio (ADR 0085), only for this phone. */
    val chargeReminderOn: StateFlow<Boolean> = profile.chargeReminder

    fun setChargeReminder(on: Boolean) = profile.setChargeReminder(on)

    /** A patron has answered the last ask: messages come to this device over radio (ADR 0051). */
    val servedByPatron: StateFlow<Boolean> get() = core.servedByPatron

    /** Light, dark or like the phone. */
    val darkMode: StateFlow<DarkMode> = profile.darkMode

    fun setDarkMode(mode: DarkMode) = profile.setDarkMode(mode)

    /**
     * Text preview of a voice message in [conversation]: the beginning of [transcript], as much as
     * fits over radio next to the announcement of the recording (ADR 0030); `null` if nothing fits. Own
     * text may be longer and goes shortened over radio (ADR 0031).
     */
    fun voicePreview(conversation: Conversation, transcript: VoiceTranscript): String? =
        VoicePreview.cut(transcript) {
            RadioAnnouncement.radioText(it, listOf(AttachmentKind.AUDIO)).encodeToByteArray().size <= radioTextLimit(conversation)
        }

    /**
     * Still free for text in [conversation]. With an internet path [MAX_ONLINE_TEXT_CHARS] characters (Unicode code
     * points, [codePointCount]) apply, over radio only the beginning goes then ([radioTextKept]). Only reachable over
     * radio (radio without anynear): bytes of the radio packet, the announcement of the attachments counts too.
     */
    fun textLeft(conversation: Conversation, text: String, kinds: List<AttachmentKind>): Int =
        if (reachableOnline(conversation)) MAX_ONLINE_TEXT_CHARS - text.trim().codePointCount()
        else radioTextLimit(conversation) - RadioAnnouncement.radioText(text.trim(), kinds).encodeToByteArray().size

    /**
     * How many characters of `text.trim()` go along over radio if [text] is too long for it; `null` if
     * everything fits or nothing goes over radio in [conversation]. Whether a radio is connected is checked
     * by the UI itself ([linkState]).
     */
    fun radioTextKept(conversation: Conversation, text: String, kinds: List<AttachmentKind>): Int? {
        val body = text.trim()
        if (body.isEmpty()) return null
        if (conversation is Conversation.Direct && (findPerson(conversation.peer)?.nodeNum ?: PeerId.nodeNumOf(conversation.peer)) == null) return null
        val full = RadioAnnouncement.radioText(body, kinds)
        val cut = RadioAnnouncement.cut(full, radioTextLimit(conversation))
        if (cut == full) return null
        // Before the text there may be the announcement of the attachments („📎 Bild: “).
        return (cut.removeSuffix(RadioAnnouncement.ELLIPSIS).length - (full.length - body.length)).coerceAtLeast(0)
    }

    /** Circles and contacts with anynear have mailboxes; radios without anynear only the radio path. */
    private fun reachableOnline(conversation: Conversation): Boolean = when (conversation) {
        is Conversation.Circle -> true
        is Conversation.Direct -> findPerson(conversation.peer).let { it?.deviceId != null && it.publicKey != null }
    }

    /**
     * Maximum length of a radio text in bytes, measured by the radio packet (ANMP, ADR 0021): in the circle with
     * or without signature, directly to an anynear installation; to radios without anynear as text.
     */
    private fun radioTextLimit(conversation: Conversation): Int {
        return when (conversation) {
            is Conversation.Circle -> core.radioTextLimit(conversation.circleId)
            is Conversation.Direct ->
                if (findPerson(conversation.peer)?.deviceId == null) MAX_TEXT_BYTES else core.radioTextLimit(null)
        }
    }

    /** Messages that are already with the core; the core forwards them itself, also after a restart. */
    private val handedLock = Mutex()

    private suspend fun handedToCore(localId: String): Boolean = handedLock.withLock { localId in loadHanded() }

    private suspend fun markHanded(localId: String) = handedLock.withLock {
        store.putString(HANDED_KEY, handedJson.encodeToString(handedSerializer, (loadHanded() + localId).takeLast(MAX_HANDED)))
    }

    private fun loadHanded(): List<String> =
        store.getString(HANDED_KEY)?.let { runCatching { handedJson.decodeFromString(handedSerializer, it) }.getOrNull() }.orEmpty()

    /**
     * Gives an own message to the core: first the attachments (encrypted, as contents), then the message.
     * The core stores it and keeps trying across restarts ([CoreEvent.Stored], [CoreEvent.SendFailed]).
     * The message ID is [localId], the same as over radio (ADR 0021).
     */
    private suspend fun deliverOnline(localId: String) {
        if (handedToCore(localId)) return
        val message = messageStore.find(localId) ?: return
        val conversation = message.conversation
        val circleId = (conversation as? Conversation.Circle)?.circleId?.takeIf { id -> circleRepository.find(id)?.removed == false }
        val device = (conversation as? Conversation.Direct)?.let { findPerson(it.peer)?.deviceId }
        if (circleId == null && (device == null || !core.canWriteTo(device))) {
            // No internet path (e.g. radio contact without the app). Out only over radio or not at all.
            if (message.via.isEmpty()) messageStore.markFailed(localId)
            return
        }
        val prepared = mutableListOf<PreparedAttachment>()
        for (attachment in message.attachments.filter { it.state == AttachmentState.UPLOADING }) {
            val bytes = withContext(Dispatchers.Default) { attachmentFiles.read(attachment.blobId) }
            if (bytes == null) {
                messageStore.markFailed(localId)
                return
            }
            // Hash: if the attachment is later missing at the relays, recipients ask for it (ADR 0014).
            if (attachment.hash == null) messageStore.setAttachmentHash(localId, attachment.blobId, attachmentHash(bytes))
            prepared += runCatching {
                core.prepareAttachment(
                    bytes, attachment.kind.wire, attachment.name.ifEmpty { null }, attachment.width, attachment.height,
                    attachment.durationMillis, attachment.transcribed,
                )
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                log("Anhang nicht vorbereitet: ${e.message}")
                messageStore.markFailed(localId)
                return
            }
        }
        val ready = messageStore.find(localId) ?: return
        // Preview (ADR 0069): without a readable picture it goes without one; the message is not lost because of it.
        val preparedLink = ready.link?.let { l ->
            val image = l.image?.let { img ->
                val bytes = withContext(Dispatchers.Default) { attachmentFiles.read(img.blobId) } ?: return@let null
                runCatching { core.prepareAttachment(bytes, AttachmentKind.IMAGE.wire, null, img.width, img.height) }.getOrNull()
            }
            PreparedLink(l.url, l.title, l.description, l.site, image)
        }
        // Direct: what the reader sees of the own place goes along if it is news (ADR 0121 §20).
        val ride = if (circleId == null) device?.let { placeSharer?.riding(it, radio = false) } else null
        val out = when {
            circleId != null -> core.sendCircle(circleId, ready.text, prepared, mid = localId, timestampMillis = ready.timestampMillis, link = preparedLink, replyTo = ready.replyTo?.mid, replyFrom = ready.replyTo?.tag, passedOn = ready.passedOn, spot = ready.spot?.let(::coreSpot))
            else -> core.sendDirect(device!!, ready.text, prepared, mid = localId, timestampMillis = ready.timestampMillis, link = preparedLink, replyTo = ready.replyTo?.mid, replyFrom = ready.replyTo?.tag, passedOn = ready.passedOn, spot = ready.spot?.let(::coreSpot), place = ride?.let(::corePlace))
        }
        if (out != null && ride != null && device != null) placeSharer?.rode(device, ride)
        if (out == null) {
            if (ready.via.isEmpty()) messageStore.markFailed(localId)
            return
        }
        markHanded(localId)
        // For new members: the signed packet into the history (ADR 0014).
        out.opened?.let { messageStore.setSigned(localId, b64.encode(it)) }
    }

    // --- Attachments -------------------------------------------------------------------

    private val downloadWake = Channel<Unit>(Channel.CONFLATED)

    // --- Preview for links (ADR 0069) --------------------------------------------------------

    /** Own client without cookies and without automatic redirects: every redirect is checked first. */
    private val previewClient by lazy {
        HttpClient {
            followRedirects = false
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = PREVIEW_TIMEOUT_MILLIS
                connectTimeoutMillis = PREVIEW_TIMEOUT_MILLIS
            }
        }
    }

    /**
     * Loads the preview for [url], only here at the sender (ADR 0069 §2): only `https`, no addresses in the own network,
     * at most [LinkPreviews.MAX_REDIRECTS] redirects, [LinkPreviews.MAX_HTML_BYTES] of HTML, the picture shrunk
     * and without image metadata. `null` if there is no preview; an error stays silent.
     */
    suspend fun loadLinkPreview(url: String): LinkDraft? = withContext(Dispatchers.Default) {
        runCatching {
            val (finalUrl, html) = fetchLimited(url, LinkPreviews.MAX_HTML_BYTES, html = true) ?: return@runCatching null
            val info = LinkPreviews.parse(html.decodeToString(), finalUrl) ?: return@runCatching null
            val image = info.image?.let { imageUrl ->
                fetchLimited(imageUrl, LinkPreviews.MAX_IMAGE_BYTES, html = false)?.second
                    ?.let { shrinkImage(it, LinkPreviews.IMAGE_MAX_EDGE, AttachmentLimits.IMAGE_QUALITY) }
                    ?.let { DraftAttachment(AttachmentKind.IMAGE, it.bytes, width = it.width, height = it.height) }
            }
            LinkDraft(url, info.title, info.description, info.site, image)
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            null
        }
    }

    /** Fetch with checked redirects; reads at most [max] bytes. Returns the last address and the bytes. */
    private suspend fun fetchLimited(start: String, max: Int, html: Boolean): Pair<String, ByteArray>? {
        var url = start
        repeat(LinkPreviews.MAX_REDIRECTS + 1) {
            if (!LinkPreviews.isAllowed(url)) return null
            // The resolved name must not point into the own network either (router, printer, NAS; ADR 0069).
            if (!isPublicHost(LinkPreviews.lookupHostOf(url) ?: return null)) return null
            val response = previewClient.get(url) {
                header(HttpHeaders.Accept, if (html) "text/html" else "image/*")
                header(HttpHeaders.UserAgent, PREVIEW_USER_AGENT)
            }
            if (response.status.value in 300..399) {
                url = response.headers[HttpHeaders.Location]?.let { loc -> if (loc.startsWith("/")) url.substringBefore("://") + "://" + url.substringAfter("://").substringBefore('/') + loc else loc } ?: return null
                return@repeat
            }
            if (!response.status.isSuccess()) return null
            val type = response.headers[HttpHeaders.ContentType].orEmpty().lowercase()
            if (html && !type.startsWith("text/html")) return null
            if (!html && !type.startsWith("image/")) return null
            val bytes = response.bodyAsChannel().readRemaining(max.toLong() + 1).readByteArray()
            // Do not half-use pictures above the limit; for HTML the beginning is enough (the details are in the head).
            if (!html && bytes.size > max) return null
            return url to bytes.copyOf(minOf(bytes.size, max))
        }
        return null
    }

    /** Shrinks a photo and removes location and other image metadata. `null`: no readable picture. */
    suspend fun prepareImage(
        raw: ByteArray,
        maxEdge: Int = AttachmentLimits.IMAGE_MAX_EDGE,
        quality: Int = AttachmentLimits.IMAGE_QUALITY,
        limit: Long = AttachmentLimits.MAX_BYTES,
    ): DraftAttachment? = withContext(Dispatchers.Default) {
        shrinkImage(raw, maxEdge, quality)
            ?.takeIf { it.bytes.size <= limit }
            ?.let { DraftAttachment(AttachmentKind.IMAGE, it.bytes, width = it.width, height = it.height) }
    }

    /**
     * Photo for a storage (ADR 0067 §7): large, without the place of capture and upright. The orientation from the image
     * metadata always applies; if there is text on it (a document, photographed sideways or upside down), the text
     * recognition on the device additionally rotates it so that the text stands upright.
     */
    suspend fun prepareStoreImage(raw: ByteArray): DraftAttachment? {
        val draft = prepareImage(raw, AttachmentLimits.STORE_IMAGE_MAX_EDGE, AttachmentLimits.STORE_IMAGE_QUALITY, AttachmentLimits.MAX_PERSONAL_BYTES)
            ?: return null
        val turn = uprightTurn(draft.bytes)
        if (turn == 0) return draft
        return rotateStoreImage(draft.bytes, turn) ?: draft
    }

    /**
     * Set an already prepared photo (e.g. from a chat) upright for a storage without shrinking it again;
     * `null` if it is already upright or cannot be read.
     */
    suspend fun uprightImage(bytes: ByteArray): DraftAttachment? {
        val turn = uprightTurn(bytes)
        if (turn == 0) return null
        return rotateStoreImage(bytes, turn)
    }

    /** Rotate a photo from a storage clockwise by [turn] degrees, in the same size and quality. */
    suspend fun rotateStoreImage(bytes: ByteArray, turn: Int): DraftAttachment? = withContext(Dispatchers.Default) {
        shrinkImage(bytes, AttachmentLimits.STORE_IMAGE_MAX_EDGE, AttachmentLimits.STORE_IMAGE_QUALITY, turn)
            ?.takeIf { it.bytes.size <= AttachmentLimits.MAX_PERSONAL_BYTES }
            ?.let { DraftAttachment(AttachmentKind.IMAGE, it.bytes, width = it.width, height = it.height) }
    }

    /**
     * Take over a file unchanged (ADR 0041): a PDF as PDF (with preview), everything else as a file. Second value:
     * why not, if it is larger than [limit] ([tooLarge] is then the notice).
     */
    fun prepareFile(
        raw: ByteArray,
        name: String,
        limit: Long = AttachmentLimits.MAX_BYTES,
        tooLarge: String = texts.status.fileTooLarge,
    ): Pair<DraftAttachment?, String?> = when {
        raw.size > limit -> null to tooLarge
        raw.isPdf() -> DraftAttachment(AttachmentKind.PDF, raw, name = shortenFileName(name)) to null
        else -> DraftAttachment(AttachmentKind.FILE, raw, name = shortenFileName(name)) to null
    }

    /** Attachments just sent, as long as they are not yet stored encrypted on the phone. */
    private val unsaved = MutableStateFlow<Map<String, ByteArray>>(emptyMap())

    /** Content of an attachment as soon as it is on the phone (own ones right after sending). */
    suspend fun attachmentBytes(blobId: String): ByteArray? =
        unsaved.value[blobId] ?: withContext(Dispatchers.Default) { attachmentFiles.read(blobId) }


    private fun startAttachmentTransfers() {
        // What no longer belongs to a message goes away; then continue what was left unfinished.
        scope.launch {
            val needed = messageStore.messages.value.flatMap { m -> m.attachments.map { it.blobId } + listOfNotNull(m.link?.image?.blobId) }.toSet()
            runCatching { attachmentFiles.keepOnly(needed) }
            core.identity.filterNotNull().first()
            messageStore.resumable().forEach { launch { deliverOnline(it.localId) } }
        }
        scope.launch { onlineReachable.collect { if (it) downloadWake.trySend(Unit) } }
        scope.launch {
            var pause = DOWNLOAD_RETRY_MIN
            while (true) {
                val later = downloadPending()
                pause = if (later) minOf(pause * 2, DOWNLOAD_RETRY_MAX) else DOWNLOAD_RETRY_MIN
                withTimeoutOrNull(if (later) pause else Duration.INFINITE) { downloadWake.receive() }
            }
        }
    }

    /** Loads all missing attachments, also of own messages from other own devices. @return `true` if something has to be tried again later. */
    private suspend fun downloadPending(): Boolean {
        var later = false
        val pending = AttachmentWants.pending(messageStore.messages.value)
        for ((message, attachment) in pending) {
            if (attachmentFiles.exists(attachment.blobId)) {
                messageStore.updateAttachment(message.localId, attachment.blobId, AttachmentState.READY)
                continue
            }
            val space = message.space
            val result = if (space == null) BlobResult.Gone else core.fetchAttachment(space, anmpAttachment(attachment))
            if (result is BlobResult.Found && (attachment.hash == null || attachmentHash(result.plaintext) == attachment.hash)) {
                withContext(Dispatchers.Default) { attachmentFiles.write(attachment.blobId, result.plaintext) }
                messageStore.updateAttachment(message.localId, attachment.blobId, AttachmentState.READY)
                continue
            }
            if (result == BlobResult.Later) {
                later = true
                continue
            }
            // Is nowhere (earlier key state, expired, from the history) or does not match the hash:
            // ask in the conversation; whoever has it delivers it (ADR 0014).
            attachment.hash?.let { askForAttachment(message, it) }
            val expired = Clock.System.now().toEpochMilliseconds() - message.timestampMillis > AttachmentLimits.RETENTION_MILLIS
            if (expired) messageStore.updateAttachment(message.localId, attachment.blobId, AttachmentState.MISSING) else later = true
        }
        return later
    }

    /** Reference to an attachment for the core; only ID and key are needed. */
    private fun anmpAttachment(a: Attachment) =
        AnmpAttachment(b64.decode(a.blobId), b64.decode(a.key), a.kind.wire, a.name.ifEmpty { null }, a.size, a.width, a.height)


    /**
     * Radio path only as long as it makes sense: if the message has already gone out over the internet and the
     * radio was unreachable for longer than the assignment window, it would otherwise arrive at the
     * recipient hours later as a duplicate. Circles without a slot on the radio only run over the internet.
     */
    private fun stillWantedOnRadio(localId: String): Boolean {
        val message = messageStore.find(localId) ?: return false
        if (message.deleted) return false
        // Already arrived at everyone (over the internet): radio would only create a duplicate.
        if (message.status?.arrived == true) return false
        val circleId = (message.conversation as? Conversation.Circle)?.circleId
        if (circleId != null && circleId in _radioProblems.value) return false
        val age = Clock.System.now().toEpochMilliseconds() - message.timestampMillis
        return !(message.status == MessageStatus.ONLINE && age > MessageStore.CROSS_TRANSPORT_WINDOW_MILLIS)
    }

    // --- Manage circles ---------------------------------------------------------------

    fun selectCircle(id: String) = circleRepository.select(id)

    /**
     * New circle. [ownName] only on first start. [onCreated] gets the ID to open the circle chat right away
     * as soon as the core has created it. @return error message or `null`.
     */
    fun createCircle(circleName: String, ownName: String? = null, onCreated: (circleId: String) -> Unit = {}): String? {
        if (ownName != null && ownName.isBlank()) return texts.status.enterYourName
        val name = circleName.trim().take(MAX_NAME)
        if (name.isBlank()) return texts.status.nameYourCircleExample
        ownName?.let(profile::setOwnName)
        profile.markSetupDone()
        scope.launch {
            val circle = core.createCircle(name)
            circleRepository.select(circle.id)
            onCreated(circle.id)
        }
        return null
    }

    /** Redeem an invitation link. [ownName] only on first start. @return error message or `null`. */
    fun joinCircle(linkText: String, ownName: String? = null): String? {
        if (ownName != null && ownName.isBlank()) return texts.status.enterYourName
        inviteProblem(linkText)?.let { return it }
        ownName?.let(profile::setOwnName)
        join(linkText)
        profile.markSetupDone()
        return null
    }

    /** Why [text] is not an invitation into this network; `null` if it fits. */
    private fun inviteProblem(text: String): String? {
        val invite = core.parseInvite(text) ?: return if (text.contains("/k")) texts.status.inviteLinkBroken else texts.status.notAnInviteLink
        if (invite.networkId != networkId) return texts.status.linkForeignNetwork
        return null
    }

    /** Join; whoever joins newly (or again) gets the entire history from a member (ADR 0014). */
    private fun join(url: String) {
        val invite = core.parseInvite(url) ?: return
        scope.launch {
            when (val result = core.joinCircle(url)) {
                is JoinResult.Joined -> {
                    circleRepository.select(result.circle.id)
                    _notice.value = texts.status.joinedCircle(result.circle.name)
                }
                is JoinResult.AlreadyMember -> {
                    circleRepository.select(result.circle.id)
                    _notice.value = texts.status.alreadyInCircle(result.circle.name)
                }
                JoinResult.ForeignNetwork -> _notice.value = texts.status.linkForeignNetwork
                JoinResult.Invalid -> _notice.value = texts.status.inviteLinkBroken
            }
            ContactRepository.fromBytes(invite.radio)?.let { inviter ->
                if (contactRepository.add(inviter, link.ownDevice.value?.nodeNum) is ContactRepository.ImportResult.Added) link.registerContact(inviter)
            }
        }
    }

    /** Contact opened via a link that still has to be confirmed. */
    data class PendingContact(val url: String, val name: String, val deviceId: String, val publicKey: String, val radio: ByteArray?)

    private val _pendingContact = MutableStateFlow<PendingContact?>(null)

    /** Contact link opened but not yet confirmed: until then nothing is stored. */
    val pendingContact: StateFlow<PendingContact?> = _pendingContact.asStateFlow()

    /** A contact or invitation link was opened (App Link / Universal Link, QR code). */
    fun openLink(url: String) {
        scope.launch { receiveLink(url)?.let { _notice.value = it } }
    }

    /**
     * Accept the link, but do not add or join anything yet: only after confirmation ([acceptPendingContact],
     * [acceptPendingInvite]) is something stored. Cancelling leaves nothing behind. Known things (contact, circle
     * one is already in) open the conversation directly after setup.
     * @return error message or `null`.
     */
    suspend fun receiveLink(url: String): String? {
        // Settings link (ADR 0089): only „Alle Daten löschen“ so far; before the setup there is nothing to delete.
        if (isSettingsLink(url)) {
            if (setupDone.value && resetAvailable && url.substringAfter('#', "") == "erase") _eraseRequest.value = true
            return null
        }
        // Login with anynear (ADR 0122): nothing is sent before the person picks the number and unlocks.
        if (core.isLoginLink(url)) {
            if (!setupDone.value) return texts.login.notSetUp
            val l = core.parseLoginLink(url) ?: return texts.login.invalid
            _pendingLogin.value = l
            return null
        }
        // Code of a new own device (ADR 0042 §5): only after confirmation with the device lock.
        if (isPairLink(url)) {
            if (!setupDone.value) return texts.devices.addInvalid
            _pendingPairing.value = url
            return null
        }
        // Channel link (ADR 0062): something only happens after „Abonnieren“.
        alerts?.takeIf { it.isChannelLink(url) }?.let { return it.receiveLink(url) }
        val card = core.parseContact(url)
        if (card != null) {
            if (card.networkId != networkId) return texts.status.linkForeignNetwork
            if (card.deviceId == _myDeviceId.value) return texts.status.ownContact
            if (isDirectContact(card.deviceId) && setupDone.value) {
                _openRequest.value = Conversation.Direct(peerOfDevice(card.deviceId))
                return null
            }
            _pendingInvite.value = null
            _pendingContact.value = PendingContact(url, card.name, card.deviceId, card.publicKey, card.radio)
            return null
        }
        if (isContactLinkText(url)) return texts.status.contactLinkBroken
        return offerInvite(url)
    }

    /**
     * Invitation link opened, scanned or pasted: nothing is joined yet, only [pendingInvite] asks „Beitreten“ with circle
     * and inviter. A circle one is already in opens directly after setup. @return error message or `null`.
     */
    fun offerInvite(linkText: String): String? {
        inviteProblem(linkText)?.let { return it }
        val invite = core.parseInvite(linkText) ?: return texts.status.inviteLinkBroken
        val known = memberCircleOf(invite)
        if (known != null && setupDone.value) {
            join(linkText)
            _openRequest.value = Conversation.Circle(known.id)
        } else {
            _pendingContact.value = null
            _pendingInvite.value = InviteOffer(linkText, invite.circleId, invite.name, invite.inviter.trim())
        }
        return null
    }

    /**
     * What the card for an invitation link in a message shows (instead of the link): name from the link and whether one
     * is already in. Changes nothing; broken links and those from another network become [InviteCard.Broken].
     */
    suspend fun inviteCard(link: InviteLink): InviteCard = when (link.kind) {
        InviteKind.Channel -> alerts?.parseLink(link.url)?.takeIf { it.networkId == networkId }?.let { invite ->
            InviteCard.Channel(invite.name, invite.curated, subscribed = alerts.channel(invite.channelId) != null)
        }
        InviteKind.Contact -> core.parseContact(link.url)?.takeIf { it.networkId == networkId }?.let { card ->
            InviteCard.Contact(card.name, known = isDirectContact(card.deviceId), self = card.deviceId == _myDeviceId.value || isOwnDevice(card.deviceId))
        }
        InviteKind.Circle -> core.parseInvite(link.url)?.takeIf { it.networkId == networkId }?.let { invite ->
            InviteCard.Circle(invite.name, member = memberCircleOf(invite) != null)
        }
        InviteKind.Device -> core.parsePair(link.url)?.takeIf { it.networkId == networkId }?.let { offer ->
            InviteCard.Device(offer.name, added = ownDevices.value.any { it.deviceId == offer.deviceId }, self = offer.deviceId == _myDeviceId.value)
        }
    } ?: InviteCard.Broken

    /**
     * The own circle whose invitation is being passed on in [text] (shared into the app from the share menu), so that
     * „An wen?“ can show who is already in it and leave out the circle itself; `null` without exactly one such link.
     */
    fun invitedOwnCircle(text: String): String? =
        findInviteLinks(text).filter { it.kind == InviteKind.Circle }
            .mapNotNull { link -> core.parseInvite(link.url)?.takeIf { it.networkId == networkId }?.let(::memberCircleOf) }
            .distinctBy { it.id }
            .singleOrNull()?.id

    /** Circle from the invitation that one is already in (as in [receiveLink]). */
    private fun memberCircleOf(invite: Invite): Circle? =
        (circleRepository.find(invite.circleId) ?: circleRepository.circles.value.firstOrNull { it.secret == invite.secret })
            ?.takeIf { !it.removed && it.secret == invite.secret }

    /**
     * Card tapped in the chat: known things (subscribed channel, circle one is in) open directly, otherwise the same
     * path as when opening the link (dialog „Abonnieren“, „Hinzufügen“, „Beitreten“), without showing the link.
     */
    fun openInviteCard(link: InviteLink) {
        when (link.kind) {
            InviteKind.Channel -> alerts?.parseLink(link.url)?.takeIf { alerts.channel(it.channelId) != null }?.let {
                alerts.requestOpen(it.channelId)
                return
            }
            InviteKind.Circle -> core.parseInvite(link.url)?.takeIf { it.networkId == networkId }?.let(::memberCircleOf)?.let {
                _openRequest.value = Conversation.Circle(it.id)
                return
            }
            InviteKind.Contact, InviteKind.Device -> Unit
        }
        openLink(link.url)
    }

    private fun isContactLinkText(text: String) = Regex("""anynear\.(?:app|net)/c/?#""").containsMatchIn(text)

    /**
     * Add the contact from the link; with [openChat] („Hinzufügen und schreiben“) open the conversation afterwards,
     * otherwise („Nur hinzufügen“) the view stays. [ownName] only on first start. @return error message or `null`.
     */
    fun acceptPendingContact(ownName: String? = null, openChat: Boolean = true): String? {
        val pending = _pendingContact.value ?: return null
        if (ownName != null && ownName.isBlank()) return texts.status.enterYourName
        if (pending.deviceId == _myDeviceId.value) return texts.status.ownContact
        val pinned = memberDirectory.pinnedKey(pending.deviceId)
        if (pinned != null && pinned != pending.publicKey) return texts.status.contactKeyMismatch(pending.name)
        ownName?.let(profile::setOwnName)
        _pendingContact.value = null
        val known = isDirectContact(pending.deviceId)
        // The core only stores the contact in a moment; until then the conversation carries the name from the link.
        val peer = peerOfDevice(pending.deviceId)
        linkNames.update { it + (peer to pending.name) }
        addingContacts.update { it + peer }
        scope.launch {
            try {
                addContactFromLink(pending.url, pending.radio)
            } finally {
                addingContacts.update { it - peer }
            }
        }
        finishSetup(Conversation.Direct(peerOfDevice(pending.deviceId)).takeIf { openChat })
        // The person decides whether you become contacts (ADR 0116 §7); until then it is a request.
        if (!known) _notice.value = texts.people.requestSent(pending.name)
        return null
    }

    /** After setup: add the contact, show a problem as a notice. */
    fun confirmPendingContact(openChat: Boolean = true) {
        acceptPendingContact(openChat = openChat)?.let { _notice.value = it }
    }

    fun dismissPendingContact() {
        _pendingContact.value = null
    }

    /** Invitation from the link confirmed: join and open the circle chat. */
    fun acceptPendingInvite(ownName: String): String? {
        val invite = _pendingInvite.value ?: return null
        if (ownName.isBlank()) return texts.status.enterYourName
        profile.setOwnName(ownName)
        _pendingInvite.value = null
        join(invite.url)
        finishSetup(Conversation.Circle(invite.circleId))
        return null
    }

    fun dismissPendingInvite() {
        _pendingInvite.value = null
    }

    /** Invitation link for sharing. Contains the circle secret; never store or upload. */
    fun inviteLink(circleId: String): String? =
        core.inviteLink(circleId, link.ownContact()?.let(ContactRepository::bytesOf))

    private val _inviteFor = MutableStateFlow<Circle?>(null)

    /** Circle for which the invite dialog (share link or QR code) is open right now. */
    val inviteFor: StateFlow<Circle?> = _inviteFor.asStateFlow()

    fun showInvite(circleId: String) {
        _inviteFor.value = circleRepository.find(circleId)?.takeIf { !it.removed }
    }

    fun closeInvite() {
        _inviteFor.value = null
    }

    /** Opens the share menu (WhatsApp etc.) with invitation text and link. */
    fun shareInvite(circleId: String) {
        val circle = circleRepository.find(circleId) ?: return
        val url = inviteLink(circleId) ?: return
        hooks.share(texts.status.shareInvite(circle.name, url))
    }

    fun membersOf(circleId: String): List<Member> = memberDirectory.of(circleId)

    /**
     * Remove a member; every member may do this (ADR 0012). The circle gets a new key (new space,
     * new radio key) that all others receive via the previous one. From then on the removed one no longer reads
     * along; old invitation links become invalid. Everyone sees in the circle chat who removed whom.
     */
    fun removeMember(circleId: String, deviceId: String) {
        if (circleRepository.find(circleId)?.removed != false) return
        scope.launch { core.removeMember(circleId, deviceId) }
    }

    /** Removes someone with all their devices from the circle (ADR 0042). */
    fun removePerson(circleId: String, member: CircleMember) {
        if (circleRepository.find(circleId)?.removed != false) return
        scope.launch { member.devices.forEach { core.removeMember(circleId, it) } }
    }

    /** Invalidate all previous invitation links (e.g. if one was forwarded). */
    fun resetInviteLink(circleId: String) {
        if (circleRepository.find(circleId)?.removed != false) return
        scope.launch { core.resetInvites(circleId) }
    }

    private fun memberName(circleId: String, deviceId: String): String =
        memberDirectory.find(circleId, deviceId)?.name?.takeIf { it.isNotBlank() } ?: texts.status.someone

    private fun systemLine(circleId: String, text: String, note: SystemNote = SystemNote()) {
        messageStore.addSystem(newId(), Conversation.Circle(circleId), text, note)
    }

    /** Rename the circle; every member may do this. @return error message or `null`. */
    fun renameCircle(circleId: String, newName: String): String? {
        val name = newName.trim().take(MAX_NAME)
        if (name.isBlank()) return texts.status.nameYourCircle
        val circle = circleRepository.find(circleId)?.takeIf { !it.removed } ?: return texts.status.circleReadOnly
        if (name == circle.name) return null
        if (_myDeviceId.value == null) return texts.status.appStarting
        systemLine(circleId, texts.status.lineYouRenamed(name))
        scope.launch { core.renameCircle(circleId, name) }
        return null
    }

    /** Photo as circle picture; every member may do this. @return error message or `null`. */
    suspend fun setCircleImage(circleId: String, raw: ByteArray): String? {
        if (circleRepository.find(circleId)?.removed != false) return texts.status.circleReadOnly
        val bytes = shrinkForAvatar(raw) ?: return texts.status.photoUnreadable
        if (!core.setCircleImage(circleId, bytes)) return texts.status.circleReadOnly
        core.circle(circleId)?.image?.let { avatars.put(it, bytes) }
        systemLine(circleId, texts.status.lineYouChangedImage)
        return null
    }

    /** Back to the generated circle picture. */
    fun resetCircleImage(circleId: String) {
        if (circleRepository.find(circleId)?.let { it.removed || it.image == null } != false) return
        systemLine(circleId, texts.status.lineYouRemovedImage)
        scope.launch { core.setCircleImage(circleId, null) }
    }

    /** Photo as own picture, for all circles and contacts. @return error message or `null`. */
    suspend fun setOwnImage(raw: ByteArray): String? {
        val bytes = shrinkForAvatar(raw) ?: return texts.status.photoUnreadable
        val ref = localImageRef(imageHash(bytes))
        avatars.put(ref, bytes)
        profile.setOwnImage(ref, nowMillis())
        return null
    }

    /** Back to the generated own picture. */
    fun resetOwnImage() {
        if (profile.ownImage.value.image == null) return
        profile.setOwnImage(null, nowMillis())
    }

    /** Shrinks a photo (without image metadata, ADR 0011). `null`: no readable picture. */
    private suspend fun shrinkForAvatar(raw: ByteArray): ByteArray? =
        withContext(Dispatchers.Default) { shrinkImage(raw, ImageLimits.MAX_EDGE, ImageLimits.QUALITY) }?.bytes

    /** Picture on the phone as soon as it is there (otherwise it is loaded in the background). */
    suspend fun imageBytes(ref: ImageRef): ByteArray? = withContext(Dispatchers.Default) { avatars.bytes(ref) }

    private val imageWake = Channel<Unit>(Channel.CONFLATED)

    /** All pictures that something refers to: own, circles, persons. */
    private fun referencedImages(): Set<ImageRef> =
        (listOfNotNull(profile.ownImage.value.image) + circleRepository.circles.value.mapNotNull { it.image } + memberDirectory.members.value.mapNotNull { it.image })
            .toSet()

    private fun startImageTransfers() {
        scope.launch {
            // What is no longer needed goes away (only once the core knows its circles and members).
            core.identity.filterNotNull().first()
            avatars.keepOnly(referencedImages())
            var pause = DOWNLOAD_RETRY_MIN
            while (true) {
                val later = downloadMissingImages()
                pause = if (later) minOf(pause * 2, DOWNLOAD_RETRY_MAX) else DOWNLOAD_RETRY_MIN
                withTimeoutOrNull(if (later) pause else Duration.INFINITE) { imageWake.receive() }
            }
        }
        scope.launch { onlineReachable.collect { if (it) imageWake.trySend(Unit) } }
    }

    /** @return `true` if something has to be tried again later. */
    private suspend fun downloadMissingImages(): Boolean {
        var later = false
        // The own picture is only on the phone (without a key); it is uploaded by the core.
        for (ref in referencedImages().filter { it.key.isNotEmpty() && !avatars.has(it) }) {
            when (val result = core.fetchImage(ref)) {
                is BlobResult.Found ->
                    if (ref.hash == null || imageHash(result.plaintext) == ref.hash) avatars.put(ref, result.plaintext) else askFor(ref)
                // Is nowhere any more: ask everyone who might have it.
                BlobResult.Gone -> {
                    askFor(ref)
                    later = true
                }
                BlobResult.Later -> later = true
            }
        }
        return later
    }

    // --- Pictures and attachments via their hash: want/have (ADR 0014) -------------------

    /** When a hash was last asked for; not more often than [WANT_PAUSE]. */
    private val wantedAt = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Picture with this hash that is already on the phone. */
    private fun localImage(hash: String): ImageRef? = referencedImages().firstOrNull { it.hash == hash && avatars.has(it) }

    /** Reference is at no relay any more: ask in the circle or the contact. */
    private fun askFor(ref: ImageRef) {
        val hash = ref.hash ?: return
        val circle = circleRepository.circles.value.filterNot { it.removed }.firstOrNull { c ->
            c.image?.hash == hash || memberDirectory.of(c.id).any { it.image?.hash == hash }
        }
        val contact = memberDirectory.members.value.firstOrNull { it.image?.hash == hash }
        when {
            circle != null -> requestContent(hash, CoreConversation.Circle(circle.id))
            contact != null -> requestContent(hash, CoreConversation.Direct(contact.deviceId))
        }
    }

    /** Attachment is missing at the relays: ask in the circle or the sender of the direct message. */
    private fun askForAttachment(message: ChatMessage, hash: String) {
        when (val c = message.conversation) {
            is Conversation.Circle -> if (circleRepository.find(c.circleId)?.removed == false) requestContent(hash, CoreConversation.Circle(c.circleId))
            is Conversation.Direct -> PeerId.deviceIdOf(c.peer)?.let { requestContent(hash, CoreConversation.Direct(it)) }
        }
    }

    private fun requestContent(hash: String, conversation: CoreConversation) {
        val now = nowMillis()
        if (now - (wantedAt.value[hash] ?: 0) < WANT_PAUSE.inWholeMilliseconds) return
        wantedAt.update { it + (hash to now) }
        scope.launch { core.want(conversation, hash) }
    }

    /** Answer to `want`: fetch the picture or attachment, check it and enter it wherever it is missing. Assigned via the hash. */
    private fun onHave(hash: String, ref: ImageRef) {
        if (AttachmentWants.waiting(messageStore.messages.value, hash).isNotEmpty()) {
            scope.launch { receiveAttachment(hash, ref) }
            return
        }
        val missing = referencedImages().filter { it.hash == hash && !avatars.has(it) }
        if (missing.isEmpty()) return
        scope.launch {
            val bytes = (core.fetchImage(ref) as? BlobResult.Found)?.plaintext ?: return@launch
            if (imageHash(bytes) != hash) return@launch
            // The same picture (same hash) for every reference that lacks it.
            (missing + ref).forEach { avatars.put(it, bytes) }
            wantedAt.update { it - hash }
        }
    }

    /** Fetch an attachment from a `have`, check it and enter it in all messages that lack it. */
    private suspend fun receiveAttachment(hash: String, ref: ImageRef) {
        val bytes = (core.fetchImage(ref) as? BlobResult.Found)?.plaintext ?: return
        if (attachmentHash(bytes) != hash) return
        for ((localId, attachment) in AttachmentWants.waiting(messageStore.messages.value, hash)) {
            withContext(Dispatchers.Default) { attachmentFiles.write(attachment.blobId, bytes) }
            messageStore.updateAttachment(localId, attachment.blobId, AttachmentState.READY)
        }
        wantedAt.update { it - hash }
    }

    /**
     * „Wieder einladen“ at the line „… hat Ben entfernt.“: sends Ben the current invitation link as a
     * direct message (internet path only). Only works as long as Ben lets us write (grant for his personal
     * mailbox, ADR 0035 §2). @return error message or `null`.
     */
    fun reinvite(circleId: String, note: SystemNote): String? {
        val circle = circleRepository.find(circleId)?.takeIf { !it.removed } ?: return texts.status.circleReadOnly
        val device = note.removedDevice ?: return null
        val name = note.removedName.orEmpty().ifBlank { texts.status.unknown }
        if (!core.canWriteTo(device)) return texts.status.personUnreachable
        val url = inviteLink(circleId) ?: return null
        val text = texts.status.reinviteMessage(circle.name, url)
        val peer = peerOfDevice(device)
        val localId = newMessageId()
        messageStore.addOutgoing(
            ChatMessage(
                localId = localId,
                conversation = Conversation.Direct(peer),
                fromNodeNum = profile.ownNodeNum.value,
                text = text,
                timestampMillis = nowMillis(),
                outgoing = true,
                status = MessageStatus.QUEUED,
                recipients = setOf(peer),
            ),
        )
        scope.launch { deliverOnline(localId) }
        _notice.value = texts.status.reinviteSent(name)
        return null
    }

    /** Is the device in the circle (again) right now? Then it needs no new invitation. */
    fun isMember(circleId: String, deviceId: String): Boolean = memberDirectory.find(circleId, deviceId) != null

    /** Only own devices left in the circle: leaving also deletes the circle storage on the relays (ADR 0049 §5). */
    fun aloneIn(circleId: String): Boolean = core.aloneIn(circleId)

    /** Leave the circle: others are told. The radio channel `anynear` stays, it belongs to all circles (ADR 0051). */
    fun leaveCircle(circleId: String) {
        circleRepository.find(circleId) ?: return
        scope.launch {
            core.leaveCircle(circleId)
            _radioProblems.update { it - circleId }
            _internetOnly.update { it - circleId }
        }
    }

    /**
     * Silently delete everything that is on the device for a circle (ADR 0014): messages, attachments,
     * pictures no longer needed, drafts, notifications. No notice to the user.
     */
    private fun forgetCircleData(circleId: String) {
        historyBuffer.remove(circleId)
        forgetConversationData(Conversation.Circle(circleId))
    }

    /**
     * Delete the direct conversation with [peer] on this device: messages, attachments, draft, notification. Also works
     * if the person is no longer a contact. The conversation stays with the other person.
     */
    fun deleteDirectHistory(peer: String) = forgetConversationData(Conversation.Direct(peer))

    /** Silently delete everything that is on the device for [conversation]. */
    private fun forgetConversationData(conversation: Conversation) {
        messageStore.removeConversation(conversation)
        drafts.setText(conversation, "")
        drafts.setAttachments(conversation, emptyList())
        cancelNotification(conversation)
        scope.launch {
            val needed = messageStore.messages.value.flatMap { m -> m.attachments.map { it.blobId } }.toSet()
            runCatching { attachmentFiles.keepOnly(needed) }
            avatars.keepOnly(referencedImages())
        }
    }

    /**
     * A received message comes back while its conversation still loads (ADR 0107 §8): read if it was read on an own
     * device before ([ReadUntil]), without a mark if it was sent before this device began to load.
     */
    private fun readOnArrival(conversation: Conversation, sentAt: Long): Boolean {
        val loading = core.loading.value
        val stillLoading = when (conversation) {
            is Conversation.Direct -> loading.mailbox
            is Conversation.Circle -> conversation.circleId in loading.circles
        }
        if (!stillLoading) return false
        val mark = ReadUntil.keyOf(conversation)?.let { profile.readUntil.value[it] }
        return ReadUntil.readOnArrival(sentAt, mark, loadingSince())
    }

    /** When this device began to load the messages after the emergency card or being added; noted at its first start. */
    private fun loadingSince(): Long =
        store.getString(LOADING_SINCE_KEY)?.toLongOrNull() ?: nowMillis().also { store.putString(LOADING_SINCE_KEY, it.toString()) }

    /** Marks from [ReadUntil] that came from another own device: silently read here too. */
    private fun applyReadUntil(marks: Map<String, Long>) {
        for ((key, sentAt) in marks) {
            val conversation = ReadUntil.conversationOf(key) ?: continue
            if (messageStore.markReadUntil(conversation, sentAt)) cancelNotification(conversation)
        }
    }

    /** What this device has not read through once yet (ADR 0107 §8): own mailbox and circles. */
    val historyLoading: StateFlow<HistoryLoading> = core.loading

    /**
     * Notice at the top of a chat as long as its messages are not all there yet (ADR 0014, 0107 §8); `null` otherwise.
     * Direct: until the own mailbox was read once. Circle: until its space was read once, and while a member still owes
     * the history.
     */
    fun historyHint(conversation: Conversation): String? {
        val loading = core.loading.value
        return when (conversation) {
            is Conversation.Direct -> texts.status.historyLoading.takeIf { loading.mailbox }
            is Conversation.Circle -> {
                val circle = circleRepository.find(conversation.circleId)?.takeIf { !it.removed } ?: return null
                when {
                    conversation.circleId in loading.circles -> texts.status.historyLoading
                    !circle.historyPending -> null
                    nowMillis() - circle.joinedAt > HISTORY_HINT_SWITCH.inWholeMilliseconds -> texts.status.historyWaiting
                    else -> texts.status.historyLoading
                }
            }
        }
    }

    fun setOwnName(name: String) {
        if (name.isBlank()) return
        profile.setOwnName(name)
        // Renamed by oneself: goes to the other own devices (ADR 0050).
        scope.launch { core.setProfile(profile.ownName.value, profile.ownNodeNum.value.toUInt().toLong(), renamed = true) }
    }

    // --- Contacts ----------------------------------------------------------------------

    // --- Multiple devices, emergency card (ADR 0042) ---------------------------------------------

    /** Own devices, including this one. */
    val ownDevices: StateFlow<List<DeviceInfo>> = core.ownDevices

    private val _revoked = MutableStateFlow(false)

    /** Another own device has removed this one: anynear sends nothing here any more, everything here is deleted. */
    val revoked: StateFlow<Boolean> = _revoked.asStateFlow()

    /**
     * Removed by another own device (ADR 0042 §6): schedule at once that everything here is deleted, and receive
     * nothing more. Deletion happens at restart, at the latest on the next opening (ADR 0046); until then only the notice.
     */
    private fun removedHere() {
        if (_revoked.value) return
        _revoked.value = true
        log("Von einem anderen eigenen Gerät entfernt: alles wird gelöscht")
        requestReset?.invoke(true)
        messageStore.messages.value.map { it.conversation }.distinct().forEach(::cancelNotification)
        hooks.setMessageReception(false)
        hooks.setBackgroundConnection(false)
    }

    private val _unknownDevice = MutableStateFlow<DeviceInfo?>(null)

    /** A device was added that was not added here: „Warst du das?“ */
    val unknownDevice: StateFlow<DeviceInfo?> = _unknownDevice.asStateFlow()

    private val _lockOutOffer = MutableStateFlow<DeviceInfo?>(null)

    /** After „Nein“ to an unknown device: offer to lock it out for good right away (ADR 0042 §6). */
    val lockOutOffer: StateFlow<DeviceInfo?> = _lockOutOffer.asStateFlow()

    fun dismissLockOutOffer() {
        _lockOutOffer.value = null
    }

    private val addedHere = mutableSetOf<String>()

    /** While this device adds another one ([addDevice]), so that its own addition does not ask „Warst du das?“. */
    @Volatile private var addingDevice = false

    private val _pendingPairing = MutableStateFlow<String?>(null)

    /** Scanned code of a new own device, waits for „Hinzufügen“. */
    val pendingPairing: StateFlow<String?> = _pendingPairing.asStateFlow()

    fun dismissPairing() {
        _pendingPairing.value = null
    }

    private fun onDevicesChanged(e: CoreEvent.DevicesChanged) {
        // Devices of other people show nothing: how many devices someone uses is nobody else's business (ADR 0042 §6).
        if (!e.own) return
        // The core reports a device added here while acceptPairing is still running: no „Warst du das?“ for it.
        if (addingDevice) {
            addedHere += e.added.map { it.deviceId }
            return
        }
        e.added.firstOrNull { it.deviceId !in addedHere && it.deviceId != _myDeviceId.value }?.let { _unknownDevice.value = it }
    }

    /**
     * Answer to „Warst du das?“: on „Nein“ the device is removed at once and the app offers to lock it out for good
     * (new person key, ADR 0042 §6): someone else has the keys of this person.
     */
    fun answerUnknownDevice(mine: Boolean) {
        val d = _unknownDevice.value ?: return
        _unknownDevice.value = null
        if (mine) return
        scope.launch {
            core.removeOwnDevice(d.deviceId)
            if (canLockOut) _lockOutOffer.value = d else _notice.value = texts.devices.removedUnknownNotice
        }
    }

    /** Add a new device via QR; the device lock is already confirmed. [passwordKey]: key of the logins, if unlocked. */
    suspend fun addDevice(link: String, passwordKey: ByteArray?): String? {
        val extras = buildMap {
            put(EXTRA_NAME, profile.ownName.value.encodeToByteArray())
            passwordKey?.let { k -> passwords?.keyForHandover(k)?.let { put(EXTRA_PV, it) } }
            if (passwordKey != null) passwords?.vaultBytes()?.let { put(EXTRA_VAULT, it) }
        }
        addingDevice = true
        val result = try { core.acceptPairing(link, extras) } finally { addingDevice = false }
        return when (val r = result) {
            is PairResult.Added -> {
                addedHere += r.device.deviceId
                _pendingPairing.value = null
                null
            }
            is PairResult.AlreadyAdded -> null
            PairResult.Revoked -> texts.devices.addRevoked
            PairResult.Full -> texts.devices.addFull
            PairResult.ForeignNetwork, PairResult.Invalid -> texts.devices.addInvalid
        }
    }

    private val _pendingLogin = MutableStateFlow<LoginLink?>(null)

    /** Opened or scanned login link (ADR 0122) that waits for „Bei … anmelden?“. */
    val pendingLogin: StateFlow<LoginLink?> = _pendingLogin.asStateFlow()

    fun dismissLogin() {
        _pendingLogin.value = null
    }

    /** Service, domain and the three numbers of a login; reads only. */
    suspend fun loginInfo(link: LoginLink): LoginInfo = core.loginInfo(link)

    /** Default of „Dein Name dort“: the profile name, shortened to what the service takes. */
    fun loginDefaultName(): String = AnmpLogin.defaultName(profile.ownName.value)

    /** Confirms the login after the device lock; `null` when signed in, otherwise the message to show. */
    suspend fun confirmLogin(info: LoginInfo, n: Int, name: String): String? {
        val r = core.confirmLogin(info, n, name)
        log("login: ${r.status}")
        return when (r.status) {
            LoginResult.OK -> null
            LoginResult.WRONG_NUMBER -> texts.login.wrongNumber
            LoginResult.EXPIRED -> texts.login.expired
            LoginResult.REJECTED -> texts.login.rejected
            LoginResult.TOO_MANY -> texts.login.tooMany
            LoginResult.NO_CHAIN -> texts.login.noChain
            LoginResult.BAD_NAME -> texts.login.badName
            LoginResult.INVALID -> texts.login.invalid
            else -> texts.login.offline
        }
    }

    fun isPairLink(text: String): Boolean = Regex("""anynear\.(?:app|net)/p/?#""").containsMatchIn(text)

    fun removeDevice(deviceId: String) {
        scope.launch { core.removeOwnDevice(deviceId) }
    }

    /** Comparison code of a pairing link, shown before the device lock (ADR 0093); `null` if it is no pairing link. */
    suspend fun pairingCode(link: String): String? = core.pairingCode(link.trim())?.let(::spacedCode)

    /** On the new device after [pairingOffer]: the comparison code to show next to the QR (ADR 0093). */
    suspend fun ownPairingCode(): String? = core.ownPairingCode()?.let(::spacedCode)

    private fun spacedCode(code: String) = code.take(3) + " " + code.drop(3)

    /** On the new device: QR for adding; then waits for the handover and restarts the app. */
    suspend fun pairingOffer(name: String): String = core.pairingOffer(name.ifBlank { platformDeviceName() })

    /** Waits up to 10 minutes for the handover; then restart as the same person. `false` on expiry. */
    suspend fun awaitPairing(): Boolean {
        val extras = core.awaitPairing(PAIRING_WAIT) ?: return false
        takeExtras(extras)
        store.putString(ASK_REPLACE_KEY, "1")
        restartApp()
        return true
    }

    private fun takeExtras(extras: Map<String, ByteArray>) {
        extras[EXTRA_NAME]?.decodeToString()?.takeIf { it.isNotBlank() }?.let { profile.setOwnName(it) }
        extras[EXTRA_VAULT]?.let { passwords?.restoreVault(it) }
        extras[EXTRA_PV]?.let { passwords?.restoreKey(it) }
        profile.markSetupDone()
    }

    val hasEmergencyCard: Boolean get() = core.hasEmergencyCard

    /**
     * No emergency card yet, but the root key is on this phone, so one can be made (ADR 0093): a step in the setup and
     * a card in „Nachrichten“ until it is confirmed.
     */
    val emergencyCardMissing: Boolean get() = !core.hasEmergencyCard && core.canRenewWithoutCode

    /**
     * Code of a card made but not yet confirmed: shown again instead of a new card, so the code written down stays
     * valid after the dialog was closed (user test 14.11.2026). `null` if there is none.
     */
    suspend fun pendingEmergencyCard(): String? = core.pendingEmergencyCard()

    /** New emergency card; the code is shown and has to be typed in once. `null`: not possible right now. */
    suspend fun createEmergencyCard(oldCode: String? = null): String? {
        updateRecoveryExtras()
        return core.createEmergencyCard(oldCode)
    }

    /** What the emergency card brings back besides the keys: name and, once unlocked, `pv` (ADR 0042). */
    private val recoveryExtras = mutableMapOf<String, ByteArray>()

    private suspend fun updateRecoveryExtras(pv: ByteArray? = null) {
        recoveryExtras[EXTRA_NAME] = profile.ownName.value.encodeToByteArray()
        pv?.let { recoveryExtras[EXTRA_PV] = it }
        core.setRecoveryExtras(recoveryExtras.toMap())
    }

    // --- Profile and settings on all own devices (ADR 0050) --------------------------------------------------

    /** Name, picture or settings came from another own device or from the relays. */
    private suspend fun onProfileChanged(e: CoreEvent.ProfileChanged) {
        val pushBack = withContext(Dispatchers.Main) {
            e.name?.takeIf { it.isNotBlank() }?.let { profile.setOwnName(it) }
            e.settings.isNotEmpty() && profile.applyPersonalSettings(e.settings.mapValues { it.value.decodeToString() }, nowMillis())
        }
        // Further person settings of the app (ADR 0106): stored now, read at the next start.
        settingsSync?.applyPerson(e.settings)
        // There were receipts here that the other device did not know yet: write back the merged state.
        if (pushBack) core.setSettings(profile.personalSettings().mapValues { it.value.encodeToByteArray() })
        if (e.imageChanged) {
            val bytes = e.image
            if (bytes == null) {
                profile.setOwnImage(null, nowMillis())
            } else {
                val ref = localImageRef(imageHash(bytes))
                avatars.put(ref, bytes)
                profile.setOwnImage(ref, nowMillis())
            }
        }
        log("Profil von einem anderen Gerät übernommen")
    }

    /** Sync the state of name, picture and settings with the relays. */
    private suspend fun syncProfile() {
        core.setSettings(profile.personalSettings().mapValues { it.value.encodeToByteArray() })
        core.profileSync()
    }

    // --- Passwords like „Meine Ablage“ on the relays, history for new own devices (ADR 0042) -------------------

    /**
     * Sync the state of the passwords from the relays (ADR 0048): take over or merge with the key;
     * if there is none there yet, upload the own one. The store of the passwords is only touched on the main thread.
     */
    private suspend fun pullVault() {
        val p = passwords ?: return
        when (val r = core.vaultGet()) {
            is VaultResult.Found -> if (withContext(Dispatchers.Main) { p.takeRemote(r.data, r.changed, r.key) }) log("Passwörter mit den Relays abgeglichen")
            VaultResult.Empty -> {
                val (bytes, key) = withContext(Dispatchers.Main) { p.vaultBytes() to p.unlockedKey() }
                bytes?.let { core.vaultPut(it, p.vaultChanged().takeIf { t -> t > 0 } ?: nowMillis(), key) }
            }
            VaultResult.Later -> Unit
        }
    }

    /** Open passwords: fetch the state of the relays right away so that logins of other devices are there. */
    fun refreshPasswords() {
        scope.launch { pullVault() }
    }

    /**
     * Set up passwords, but only after a look at the relays: if other own devices already have logins,
     * their key comes instead of a new one making them unreadable. `false` if it did not work.
     */
    suspend fun setUpPasswords(): Boolean {
        val p = passwords ?: return false
        withTimeoutOrNull(VAULT_CHECK) { pullVault() }
        return withContext(Dispatchers.Main) {
            if (p.state.value == PasswordState.NotSetUp) p.setUp() else p.state.value == PasswordState.Locked
        }
    }

    /** Devices that have already received `pv` from here (only in this run; twice does no harm). */
    private val pvSentTo = mutableSetOf<String>()

    private fun onPasswordKey(pv: ByteArray) {
        scope.launch {
            pullVault()
            updateRecoveryExtras(pv)
            core.ownDevices.value.map { it.deviceId }.filter { it != _myDeviceId.value && it !in pvSentTo }.forEach { d ->
                if (core.sendOwnData(TOPIC_PV, pv, d)) pvSentTo += d
            }
        }
    }

    /**
     * Hands the local direct history to the core once (ADR 0107 §6): what this person sent from its only device lay
     * only in the recipients' mailboxes; the core writes it as own copies, so other devices and the emergency card get
     * it. A new device reads everything from its mailbox itself; there is no hand-over between devices any more.
     */
    private suspend fun keepOwnHistory() {
        var pause = HISTORY_RETRY_MIN
        while (true) {
            val items = messageStore.messages.value
                .filter { it.conversation is Conversation.Direct && it.system == null && !it.deleted }
                .mapNotNull(::ownHistoryItem)
            if (runCatching { core.keepOwnHistory(items) }.getOrDefault(false)) return
            delay(pause)
            pause = minOf(pause * 2, HISTORY_RETRY_MAX)
        }
    }

    private fun ownHistoryItem(m: ChatMessage): OwnHistoryItem? {
        val peer = (m.conversation as? Conversation.Direct)?.peer?.let(PeerId::deviceIdOf) ?: return null
        val mid = if (m.outgoing) m.localId else m.remoteId ?: return null
        val author = if (m.outgoing) null else m.fromDevice ?: return null
        return OwnHistoryItem(
            peer, mid, m.timestampMillis, m.text, author, m.attachments.map(::anmpAttachment), m.replyTo?.mid, m.replyTo?.tag,
        )
    }

    private fun onOwnData(e: CoreEvent.OwnData) {
        when (e.topic) {
            TOPIC_PV -> passwords?.let { p -> scope.launch(Dispatchers.Main) { if (!p.hasKey()) p.restoreKey(e.data) } }
            TOPIC_PV_ASK -> onPasswordRequest(e)
        }
    }

    /** An own device without a lock of its own (browser, ADR 0059 §6) wants to open the passwords. */
    class PasswordRequest(val deviceId: String, val deviceName: String, internal val nonce: ByteArray)

    private val _passwordRequest = MutableStateFlow<PasswordRequest?>(null)

    /** Open request „Passwörter am Computer öffnen?“; the answer is only given after the device lock. */
    val passwordRequest: StateFlow<PasswordRequest?> = _passwordRequest.asStateFlow()

    private fun onPasswordRequest(e: CoreEvent.OwnData) {
        // Only fresh requests with an identifier as the browser sends it; older ones have long expired when fetched.
        if (e.data.size != PV_ASK_NONCE || nowMillis() - e.ts > PV_ASK_WAIT.inWholeMilliseconds) return
        val name = core.ownDevices.value.firstOrNull { it.deviceId == e.fromDevice }?.name.orEmpty()
        _passwordRequest.value = PasswordRequest(e.fromDevice, name, e.data)
    }

    /** Answer to „Passwörter am Computer öffnen?“; [allow] only after a confirmed device lock. */
    fun answerPasswordRequest(allow: Boolean) {
        val r = _passwordRequest.value ?: return
        _passwordRequest.value = null
        scope.launch { core.sendOwnData(if (allow) TOPIC_PV_OK else TOPIC_PV_NO, r.nonce, r.deviceId) }
    }

    suspend fun confirmEmergencyCard(typed: String): Boolean = core.confirmEmergencyCard(typed)

    /** With the code of the emergency card on a new phone; afterwards restart as the same person. */
    suspend fun recover(code: String): RecoverResult {
        val r = core.recoverWithCode(code)
        if (r is RecoverResult.Recovered) {
            takeExtras(r.extras)
            store.putString(ASK_REPLACE_KEY, "1")
            restartApp()
        }
        return r
    }

    /** Locking out is possible: with the code of the emergency card, or without while the root key is still here. */
    val canLockOut: Boolean get() = core.hasEmergencyCard || core.canRenewWithoutCode

    /** Locking out needs the code of the emergency card (the root key is no longer on this device). */
    val lockOutNeedsCode: Boolean get() = !core.canRenewWithoutCode

    /**
     * Lock out devices forever (ADR 0042 §6): new person key, mailbox, circle keys, store secret and, at the next
     * unlock of the passwords, a new key `pv`. [code] only if [lockOutNeedsCode].
     */
    suspend fun lockOutDevices(deviceIds: List<String>, code: String?): Boolean {
        val ok = core.renewPersonKey(deviceIds, code?.takeIf { it.isNotBlank() })
        if (ok) {
            pvSentTo.clear()
            passwords?.renewKey()
        }
        return ok
    }

    private val _ownContactLink = MutableStateFlow<String?>(null)

    /**
     * Own contact link `https://anynear.net/c#…` (public things only, ADR 0036) with network ID, write grant for the
     * personal mailbox and radio contact if a radio is paired.
     */
    val ownContactLink: StateFlow<String?> = _ownContactLink.asStateFlow()

    sealed interface SupportResult {
        /** Sent; [conversation] is the new circle of the request, the answer arrives there. */
        data class Sent(val conversation: Conversation) : SupportResult

        data class Failed(val reason: String) : SupportResult
    }

    /** Support link: built in, replaced by the one from the website if that fits (ADR 0084 §7). */
    private var supportLink: String? = AppConfig.SUPPORT_CONTACT_LINK
    private var supportLinkLoaded = 0L

    /**
     * Loads the support link from the website (at most once per hour) and remembers the device of the support identity.
     * It is only taken as a contact link of this network named „anynear Support“; otherwise the built-in one stays.
     */
    private suspend fun refreshSupportLink() {
        if (nowMillis() - supportLinkLoaded > SUPPORT_LINK_REFRESH) {
            supportLinkLoaded = nowMillis()
            SupportLinks.load()?.takeIf { it != supportLink }?.let { fetched ->
                val card = runCatching { core.parseContact(fetched) }.getOrNull()
                if (card != null && card.networkId == networkId && card.name == AppConfig.SUPPORT_NAME) supportLink = fetched
            }
        }
        val card = supportLink?.let { runCatching { core.parseContact(it) }.getOrNull() } ?: return
        if (card.deviceId != _myDeviceId.value && !isOwnDevice(card.deviceId)) supportDevice.value = card.deviceId
    }

    /** Is this the support identity (own channel „anynear-support“)? Only it sees the link for publishing. */
    fun isSupportIdentity(channels: List<AlertChannel>): Boolean =
        channels.any { it.owner && !it.curated && it.name.trim().equals(AppConfig.SUPPORT_ALERT_CHANNEL, ignoreCase = true) }

    /** Contact link for publishing, permission for 365 days (ADR 0084 §6). */
    suspend fun publicContactLink(): String? = runCatching { core.publicContactLink() }.getOrNull()

    /** Withdraws the link for publishing; the next one is a new one (ADR 0084 §6). */
    suspend fun revokePublicContactLink() {
        runCatching { core.revokePublicContactLink() }
    }

    /** Is support already set up ([AppConfig.SUPPORT_CONTACT_LINK])? */
    val supportAvailable: Boolean get() = AppConfig.SUPPORT_CONTACT_LINK != null

    /**
     * Support as a chat (ADR 0065): support identity as a contact, new circle „Support: <Betreff>“ with the first
     * message (for an error with [logText] as a file), then the invitation directly to support. It joins via the
     * link and gets the history (ADR 0014); every request is thus a conversation of its own.
     */
    suspend fun sendSupportRequest(kind: SupportKind, subject: String, description: String, logText: String?, crashText: String? = null): SupportResult {
        refreshSupportLink()
        val link = supportLink ?: return SupportResult.Failed(texts.support.notAvailable)
        val peer = when (val r = addContact(link)) {
            is AddContactResult.Added -> r.peer
            is AddContactResult.AlreadyKnown -> r.peer
            else -> return SupportResult.Failed(texts.support.sendFailed)
        }
        if (!awaitReachable(peer)) return SupportResult.Failed(texts.support.sendFailed)
        val kindLabel = if (kind == SupportKind.Problem) texts.support.kindProblem else texts.support.kindQuestion
        val circle = runCatching { core.createCircle(Support.circleName(texts.support.circlePrefix, subject)) }.getOrNull()
            ?: return SupportResult.Failed(texts.support.sendFailed)
        // If something goes wrong afterwards, leave the circle just created again so that no empty „Support: …“ stays.
        val failed = { reason: String ->
            scope.launch { runCatching { core.leaveCircle(circle.id) } }
            SupportResult.Failed(reason)
        }
        // Otherwise [send] rejects the circle just created because the circle list does not know it yet.
        if (!awaitCircle(circle.id)) return failed(texts.support.sendFailed)
        val conversation = Conversation.Circle(circle.id)
        val drafts = listOfNotNull(
            crashText?.let { DraftAttachment(AttachmentKind.FILE, it.encodeToByteArray(), name = texts.support.crashFileName) },
            logText?.let { DraftAttachment(AttachmentKind.FILE, it.encodeToByteArray(), name = texts.support.logFileName) },
        )
        val text = Support.firstMessage(kindLabel, subject, description, appVersion, usagePlatform)
        (send(conversation, text, drafts) as? SendResult.Rejected)?.let { return failed(it.reason) }
        val invite = inviteLink(circle.id) ?: return failed(texts.support.sendFailed)
        (send(Conversation.Direct(peer), texts.support.newRequest(subject.trim(), invite)) as? SendResult.Rejected)
            ?.let { return failed(it.reason) }
        log("Support-Anfrage gesendet")
        return SupportResult.Sent(conversation)
    }

    /**
     * At the support identity (ADR 0065): join every new request right away (invitation link from the direct message,
     * only circles „Support: …“) and send it as an alert into the own channel [AppConfig.SUPPORT_ALERT_CHANNEL]. Only
     * does something if this person owns a channel with that name, so in practice only at the support identity.
     * Every request only once, only from the last 7 days (not the whole history after a restore).
     */
    private suspend fun forwardSupportRequests(messages: List<ChatMessage>) {
        val alerts = alerts ?: return
        val channel = alerts.channels.value.firstOrNull { it.owner && !it.curated && it.name.trim().equals(AppConfig.SUPPORT_ALERT_CHANNEL, ignoreCase = true) } ?: return
        val since = nowMillis() - SUPPORT_FORWARD_WINDOW
        val prefixes = listOf(Strings.german.support.newRequestPrefix, Strings.english.support.newRequestPrefix)
        val circlePrefixes = listOf(Strings.german.support.circlePrefix, Strings.english.support.circlePrefix)
        val done = store.getString(SUPPORT_FORWARDED_KEY)?.let { runCatching { ownDataJson.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull() }.orEmpty()
        val fresh = messages.filter { m ->
            !m.outgoing && m.conversation is Conversation.Direct && m.timestampMillis >= since && m.localId !in done
        }.mapNotNull { m -> Support.requestSubject(m.text, prefixes)?.let { m to it } }
        if (fresh.isEmpty()) return
        val handled = fresh.filter { (m, subject) ->
            val invite = Support.inviteLink(m.text)?.takeIf { url -> core.parseInvite(url)?.name?.let { n -> circlePrefixes.any(n::startsWith) } == true }
            val joined = invite == null || when (runCatching { core.joinCircle(invite) }.getOrNull()) {
                is JoinResult.Joined, is JoinResult.AlreadyMember -> true
                else -> false
            }
            if (!joined) {
                log("Support-Anfrage: Beitritt nicht möglich")
                return@filter false
            }
            val text = texts.support.requestAlertText(subject)
            // If the support identity also runs in the web app, that one may already have reported it.
            Support.alreadyAlerted(alerts.alerts.value, channel.id, text, m.timestampMillis) ||
                alerts.sendQuietly(channel.id, AlertPriority.Warning, texts.support.requestAlertTitle, text)
                    .also { if (!it) log("Support-Anfrage: Meldung nicht gesendet") }
        }.map { it.first.localId }
        if (handled.isEmpty()) return
        store.putString(SUPPORT_FORWARDED_KEY, ownDataJson.encodeToString(ListSerializer(String.serializer()), (done + handled).takeLast(SUPPORT_FORWARDED_MAX)))
        log("Support-Anfragen beigetreten und gemeldet: ${handled.size}")
    }

    // --- Crash report (ADR 0082) --------------------------------------------------------

    /** Crash reports that are not yet sent or discarded; empty where the platform keeps none. */
    val crashReports: StateFlow<List<CrashReport>> = crashes?.reports ?: MutableStateFlow(emptyList())

    /** For the card in „Nachrichten“: only crashes of the running version ([CrashReports.forCard]). */
    val crashReportsOfThisVersion: StateFlow<List<CrashReport>> =
        crashReports.map { CrashReports.forCard(it, appVersion) }.stateIn(scope, SharingStarted.Eagerly, CrashReports.forCard(crashReports.value, appVersion))

    /** The reports as text, the way they would be sent. */
    fun crashReportText(): String = CrashReports.text(crashReports.value)

    /**
     * Send reports via support (ADR 0065): a request „Absturz <Version>“ with `absturz.txt` and the log
     * [logText]. [note] is the optional sentence of the person. Only after sending are the reports deleted.
     */
    suspend fun sendCrashReports(note: String, logText: String?): SupportResult {
        val reports = crashReports.value
        if (reports.isEmpty()) return SupportResult.Failed(texts.support.sendFailed)
        val count = reports.sumOf { it.count }
        val result = sendSupportRequest(
            SupportKind.Problem,
            texts.support.crashSubject(appVersion),
            note.trim().ifEmpty { texts.support.crashDescription(count) },
            logText,
            CrashReports.text(reports),
        )
        if (result is SupportResult.Sent) {
            crashes?.clear()
            log("Absturzberichte gesendet: ${reports.size}")
        }
        return result
    }

    /** Without a support identity: pass on reports via the share menu; they stay in place. */
    fun shareCrashReports() {
        if (crashReports.value.isNotEmpty()) hooks.share(crashReportText())
    }

    /** „Nicht senden“ or „Löschen“: discard all reports. */
    fun dismissCrashReports() {
        crashes?.clear()
    }

    /** Opens the share menu with the log for troubleshooting. */
    fun shareDebugLog(text: String) {
        hooks.share(text)
    }

    /** Opens the share menu with the own contact link. */
    fun shareOwnContact() {
        val url = ownContactLink.value ?: return
        val name = profile.ownName.value.trim()
        hooks.share(if (name.isEmpty()) url else texts.status.shareOwnContact(name, url))
    }

    /**
     * Invite someone from the phone's contacts (ADR 0003): a text message to [number] with the own contact link via
     * [sms], otherwise (no number, no SMS on this device) the share menu. The number is not stored and goes nowhere
     * else. Returns `false` while the own contact link is not ready yet.
     */
    fun inviteFromPhoneContact(number: String?, sms: (number: String, text: String) -> Boolean): Boolean {
        val url = ownContactLink.value ?: return false
        val text = texts.status.phoneInvite(profile.ownName.value.trim(), url)
        if (number == null || !sms(number, text)) hooks.share(text)
        return true
    }

    sealed interface AddContactResult {
        /** [peer]: the person, to open the conversation right away. */
        data class Added(val name: String, val peer: String) : AddContactResult

        data class AlreadyKnown(val name: String, val peer: String) : AddContactResult

        /** Same device ID but a different key: do not take over silently (replacement device or forgery). */
        data class KeyMismatch(val name: String) : AddContactResult

        data object Own : AddContactResult

        data class Invalid(val reason: String) : AddContactResult
    }

    /** Add a contact, across circles, via their contact link (`https://anynear.net/c#…`, internet and possibly radio). */
    suspend fun addContact(text: String): AddContactResult {
        val card: ContactCard = core.parseContact(text) ?: return AddContactResult.Invalid(texts.status.contactLinkBroken)
        return addContactFromLink(text, card.radio)
    }

    private suspend fun addContactFromLink(url: String, radioContact: ByteArray?): AddContactResult {
        val result = when (val r = core.addContact(url)) {
            is CoreAddContactResult.Added -> AddContactResult.Added(r.contact.name, PeerId.device(r.contact.person))
            is CoreAddContactResult.AlreadyKnown -> AddContactResult.AlreadyKnown(r.contact.name, PeerId.device(r.contact.person))
            is CoreAddContactResult.KeyMismatch -> return AddContactResult.KeyMismatch(r.name)
            CoreAddContactResult.Own -> return AddContactResult.Own
            CoreAddContactResult.ForeignNetwork -> return AddContactResult.Invalid(texts.status.linkForeignNetwork)
            CoreAddContactResult.Invalid -> return AddContactResult.Invalid(texts.status.contactLinkBroken)
            is CoreAddContactResult.Requested -> return AddContactResult.Invalid(texts.status.contactLinkBroken)
        }
        scope.launch { refreshContactRequests() }
        ContactRepository.fromBytes(radioContact)?.let { radio ->
            if (contactRepository.add(radio, link.ownDevice.value?.nodeNum) is ContactRepository.ImportResult.Added) link.registerContact(radio)
        }
        return result
    }

    fun removeContact(nodeNum: Int) = contactRepository.remove(nodeNum)

    /** Is [id] (a device ID, or a person ID with any of its devices) stored as a contact (contact link or direct messages)? */
    fun isDirectContact(id: String): Boolean = directEntries(id).isNotEmpty()

    /** Contact entries of [id]: a device ID or a person ID (ADR 0042), whose entries carry its device IDs. */
    private fun directEntries(id: String): List<Member> =
        memberDirectory.members.value.filter { it.circleId == MemberDirectory.DIRECT_CONTACTS && (it.deviceId == id || it.personId == id) }

    /**
     * Remove an app contact stored by oneself, together with the radio contact stored with it. Conversation,
     * history and circles stay (the history is deleted on request by [deleteDirectHistory]); in a shared circle the person stays visible as a member. The core
     * withdraws the personal grant (ADR 0035 §2): without a shared circle they can then no longer write into the
     * personal mailbox until you connect again via the contact link. With a shared circle
     * its grant continues to apply; if they then write directly, they become a contact again.
     */
    fun removeDirectContact(id: String) {
        // [id] is a device or person ID; the page passes the person (ADR 0042), whose entries carry its device IDs.
        val entries = directEntries(id).ifEmpty { return }
        val devices = entries.map { it.deviceId }.toSet()
        val inCircle = memberDirectory.members.value.any { it.deviceId in devices && it.circleId != MemberDirectory.DIRECT_CONTACTS }
        if (!inCircle) entries.map { it.nodeNum }.filter { it != 0 }.distinct().forEach { contactRepository.remove(it) }
        // Removing a contact ends every sharing with it (ADR 0123 §4); the person reads it in the chat, while it still can.
        if (!inCircle) endSharing(id)
        scope.launch {
            if (!inCircle) {
                sos?.contacts?.value?.firstOrNull { it.person == id }?.let { runCatching { sos.endContact(id) } }
                if (sos?.agreed?.value?.contains(id) == true) runCatching { sos.answer(id, false) }
            }
            core.removeContact(id)
        }
    }

    fun findPerson(peer: String): Person? = Person.find(people.value, peer)

    /** Is [peer] already reachable over the internet (device and key known)? */
    private fun reachableNow(peer: String): Boolean = findPerson(peer)?.let { it.deviceId != null && it.publicKey != null } == true

    /** Waits until [people] knows the person with device and key; [people] follows the core one step later. */
    private suspend fun awaitReachable(peer: String, wait: Duration = KNOWN_WAIT): Boolean =
        withTimeoutOrNull(wait) { people.first { list -> Person.find(list, peer)?.let { it.deviceId != null && it.publicKey != null } == true } } != null

    /** Waits until the circle list knows a circle just created; it follows the core one step later. */
    private suspend fun awaitCircle(circleId: String): Boolean =
        withTimeoutOrNull(KNOWN_WAIT) { circleRepository.circles.first { list -> list.any { it.id == circleId } } } != null

    /**
     * Person for a radio, for radio text without ANMP (radios without anynear): the app member with
     * this radio if it is exactly one; otherwise the radio itself. A radio can be used by
     * several people (ADR 0021), then any assignment would be a guess.
     */
    private fun peerForNode(nodeNum: Int): String =
        people.value.filter { it.nodeNum == nodeNum }.singleOrNull()?.peer ?: PeerId.node(nodeNum)

    /** Display name of a person or a radio. */
    fun peerName(peer: String): String {
        findPerson(peer)?.let { return it.name }
        PeerId.nodeNumOf(peer)?.let { return displayName(it) }
        PeerId.deviceIdOf(peer)?.let(ownContactNames.value::get)?.let { return it }
        // Member that is only known from a message.
        return messageStore.messages.value.lastOrNull { it.fromPeer == peer }?.senderName
            ?: linkNames.value[peer]
            ?: texts.status.unknown
    }

    /** Name for the speech bubble of an incoming message. */
    fun senderLabel(message: ChatMessage): String =
        message.fromPeer?.let { peer -> findPerson(peer)?.name ?: PeerId.deviceIdOf(peer)?.let(ownContactNames.value::get) } ?: message.senderName ?: displayName(message.fromNodeNum)

    fun displayName(nodeNum: Int): String =
        contactRepository.find(nodeNum)?.displayName
            ?: link.knownNames.value[nodeNum]
            ?: radioNumberLabel(nodeNum)

    private fun newId(): String = Uuid.random().toString()

    /** Message ID for ANMP (ADR 0021): 8 random bytes, Base64url. */
    private fun newMessageId(): String = b64.encode(secureRandomBytes(Anmp.ID_BYTES))

    /** Message ID as ANMP bytes; `null` for older IDs (UUID) that ANMP cannot carry. */
    private fun anmpId(mid: String?): ByteArray? =
        mid?.takeIf { it.length == 11 }?.let { runCatching { b64.decode(it) }.getOrNull() }?.takeIf { it.size == Anmp.ID_BYTES }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    internal companion object {
        /** Lifetime of an introduction card (ADR 0116), as in the core. */
        const val INTRO_LIFETIME_MILLIS = 30L * 24 * 3600 * 1000

        /** From this many failed pairings in a row the app recommends pairing again. */
        const val PAIRING_HINT_AFTER = 2

        /** From this many failed pairings re-pairing apparently does not help: recommend rescue via USB. */
        const val RESCUE_HINT_AFTER = 4

        /** For this long after the reset via USB no recommendation for rescue: re-pairing has to happen first. */
        private val PAIRING_QUIET_AFTER_RESET = 10.minutes

        /** How long setting up via USB waits for the radio over Bluetooth (restart after „Bluetooth einschalten“ included). */
        private val USB_PAIR_WAIT = 90.seconds

        /** Connecting at the address the radio reported over the cable. */
        private val WIFI_CONNECT_WAIT = 30.seconds

        /** Switching into the Wi-Fi without cable: restart and joining take well under a minute. */
        private val WAY_WIFI_WAIT = 60.seconds

        /** Model of the radio connected last (Meshtastic `hw_model`), for the USB rescue. */
        private const val LAST_RADIO_MODEL_KEY = "radio.lastHwModel"
        private const val FILES_SORT_KEY = "files.sort"

        /** When this device began to load after the emergency card (ms), see [loadingSince]. */
        private const val LOADING_SINCE_KEY = "history.loadingSince"

        /** How long changes to the person's settings are collected before they go to the profile space. */
        private val PERSONAL_SETTINGS_DELAY = 3.seconds

        /** „Behalten“ on the hint for unreachable contacts: `person:since` separated by commas. */
        private const val UNREACHABLE_KEPT_KEY = "contacts.unreachableKept"

        /** Set while „Alle Daten löschen“ waits for writes that no relay has yet (ADR 0046). */
        private const val ERASE_WAITING_KEY = "erase.waiting"
        private val ERASE_POLL = 2.seconds

        /** When „Später“ was tapped on the band with a date (ADR 0096 §4); Unix milliseconds. */
        private const val UPDATE_LATER_KEY = "update.laterAt"

        /** For this long direct messages wait for the receipt of the recipient. */
        private val DIRECT_CONFIRMATION_WINDOW = 2.minutes

        /** For this long after „Später“ the app does not ask again about the battery setting. */
        private val BACKGROUND_HINT_PAUSE = 3.days

        /** After the start the settings of the person's other devices arrive first; only then the location question (ADR 0121 §10). */
        private val LOCATION_QUESTION_DELAY = 20.seconds

        /** This many unread messages are shown by a notification, older ones only as a number. */
        private const val NOTIFICATION_LINES = 6

        /** How long a reply from the notification waits for the core and for leaving the phone (iOS allows about 30 s). */
        private val REPLY_WAIT = 20.seconds

        /** Fetch of a preview (ADR 0069): time limit and neutral name, without device and version. */
        private const val PREVIEW_TIMEOUT_MILLIS = 5_000L
        private const val PREVIEW_USER_AGENT = "Mozilla/5.0 (compatible; LinkPreview)"

        /** „Für alle löschen“ works for 48 hours after sending (ADR 0068). */
        const val DELETE_FOR_EVERYONE_MILLIS = 48L * 60 * 60 * 1000

        /** What a new own device gets besides the keys (ADR 0042 §5): name, password key, logins. */
        private const val EXTRA_NAME = "name"
        private const val EXTRA_PV = "pv"

        /** Set before the restart after the emergency card or adding this phone: ask once which device it replaces. */
        private const val ASK_REPLACE_KEY = "devices.ask_replace"
        private val REPLACE_WAIT = 2.minutes
        private val REPLACE_RETRY = 10.seconds
        private val REPLACE_LIST_WAIT = 30.seconds
        private const val EXTRA_VAULT = "vault"

        /** Topics of the data to own devices (`sendOwnData`). */
        private val HISTORY_RETRY_MIN = 30.seconds
        private val HISTORY_RETRY_MAX = 30.minutes
        private const val TOPIC_PV = "pv"

        /** „Am Handy bestätigen“ (ADR 0059 §6): request of the browser with a 16-byte identifier, answer with the same one. */
        private const val TOPIC_PV_ASK = "pv-ask"
        private const val TOPIC_PV_OK = "pv-ok"
        private const val TOPIC_PV_NO = "pv-no"
        private const val PV_ASK_NONCE = 16

        /** For this long the browser waits; later requests are no longer shown by the app. */
        private val PV_ASK_WAIT = 2.minutes
        private val ownDataJson = Json { ignoreUnknownKeys = true }
        /** For this long the app waits until a circle or contact just created is in its lists. */
        private val KNOWN_WAIT = 10.seconds

        /** For a message to a contact the core is storing right now; it may wait behind network work in the process. */
        private val CONTACT_SAVE_WAIT = 60.seconds

        private const val SUPPORT_FORWARDED_KEY = "support_forwarded"
        private const val SUPPORT_FORWARDED_MAX = 500
        private const val SUPPORT_FORWARD_WINDOW = 7L * 24 * 60 * 60 * 1000

        /** This often the app loads the support link from the website (ADR 0084 §7). */
        private const val SUPPORT_LINK_REFRESH = 60L * 60 * 1000

        /** For this long a new device waits for the handover from the phone. */
        private val PAIRING_WAIT = 10.minutes

        /** At most this long [shutdown] waits for the radio and for the running work to end. */
        private val SHUTDOWN_WAIT = 3.seconds

        /** For this long passwords stay open when one leaves the area or the app goes to the background (ADR 0048). */
        val PASSWORD_GRACE = 180.seconds

        /** For this long „Einrichten“ waits for the relays at most. */
        private val VAULT_CHECK = 8.seconds

        /** After this much time without history the notice says that someone has to be online. */
        private val HISTORY_HINT_SWITCH = 10.minutes

        /** A radio connected without an area: this long the location may still find it before the app asks. */
        private val RADIO_AREA_GRACE = 8.seconds

        /** The same picture is asked for at most this often. */
        private val WANT_PAUSE = 1.hours

        /** Reason for a key rotation (`why`, ANMP). */
        private const val WHY_RESET = AnmpCore.WHY_RESET
        private const val WHY_LEFT = AnmpCore.WHY_LEFT

        /** Longest circle name (as in the invitation link). */
        private const val MAX_NAME = app.anynear.anmp.core.AnmpLinks.MAX_NAME

        /** For this long the app searches for a radio on Wi-Fi, then a pause until the next search. */
        private val SUGGEST_SCAN_DURATION = 20.seconds
        private val SUGGEST_SCAN_PAUSE = 5.minutes

        /** For this long a paired radio may connect before another one on Wi-Fi is offered. */
        private val SUGGEST_GRACE = 1.minutes
        const val RADIO_IN_USE_ID = "radio-in-use"

        /** After „Hier verbinden“ do not step back again for this long (ConnectionDrops.takenOver). */
        private val TAKE_OVER_HOLD = 10.minutes

        /**
         * To radios without anynear (Meshtastic text), some headroom below the payload limit (233 bytes).
         * anynear messages follow the ANMP packet over radio.
         */
        private const val MAX_TEXT_BYTES = 200

        /**
         * Longest text with an internet path, in characters counted as Unicode code points ([codePointCount]), the
         * same way as the web app; at most 16384 bytes in UTF-8. The same in every plan: text length is no plan feature.
         * Over radio only the beginning goes (ADR 0031).
         */
        internal const val MAX_ONLINE_TEXT_CHARS = 4096

        /** The app passes on these kinds for others at the shared radio (ADR 0022). */
        /** Below this battery level on the radio the app passes on nothing for others (ADR 0051). */
        private const val MIN_RELAY_BATTERY = 30


        /** Without a delivery receipt after this time: radio form into the radio inbox (ADR 0051). */
        private val RADIO_FALLBACK_AFTER = 2.minutes
        private val RADIO_FALLBACK_UNTIL = 30.minutes
        private val RADIO_FALLBACK_CHECK = 30.seconds

        /** Loading attachments: pause between attempts when it is not possible right now. */
        private val DOWNLOAD_RETRY_MIN = 30.seconds
        private val DOWNLOAD_RETRY_MAX = 10.minutes

        /** Messages already handed to the core (the last [MAX_HANDED]). */
        private const val HANDED_KEY = "app.handed"
        private const val MAX_HANDED = 2000
        private val handedJson = Json { ignoreUnknownKeys = true }
        private val handedSerializer = ListSerializer(String.serializer())

        private fun ByteArray.isPdf() = size >= 5 && copyOfRange(0, 5).decodeToString() == "%PDF-"

        /**
         * Sort in a new hit. The same device can show up via Bluetooth and on Wi-Fi
         * (same name, see [NetworkTransport.radioName]); it then appears only once, via Bluetooth,
         * because that also works without a home network.
         */
        fun mergeFound(list: List<FoundDevice>, found: FoundDevice): List<FoundDevice> {
            val others = list.filterNot { it.identifier == found.identifier }
            val twin = others.firstOrNull { it.name.equals(found.name, ignoreCase = true) && it.connection != found.connection }
            val merged = when {
                twin == null -> others + found
                found.connection == FoundDevice.Connection.BLUETOOTH -> others - twin + found
                else -> others
            }
            return sortFound(merged)
        }

        /** Bluetooth devices by signal strength (closest first), Wi-Fi devices after that by name. */
        fun sortFound(list: List<FoundDevice>): List<FoundDevice> =
            list.sortedWith(compareBy<FoundDevice>({ it.connection }, { it.rssi?.let { r -> -r } ?: Int.MAX_VALUE }, { it.name }))
    }
}

/** Content of the dialog „Funkgerät einrichten“. */
data class DeviceSetupPrompt(
    val check: DeviceSetupCheck,
    /** Suggestion for the part after `anynear `: existing name (also with an older prefix) or derived from the node number. */
    val suggestedNameSuffix: String,
    /** Names of other radios in range, for the notice „gibt es schon“. */
    val takenNames: Set<String> = emptySet(),
)

/** Name to give this device at the start: the model name, but only if it has none yet; otherwise `null`. */
internal fun ownDeviceNameToSet(current: String?, platform: String): String? = platform.takeIf { current.isNullOrBlank() }
