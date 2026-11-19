package app.anynear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.anynear.alerts.Alerts
import app.anynear.chats.conversationOf
import app.anynear.chats.key
import app.anynear.lifesigns.LifeSigns
import app.anynear.app.AppCommand
import app.anynear.app.AppController
import app.anynear.app.UpdateNotice
import app.anynear.i18n.texts
import app.anynear.app.BackgroundRestriction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import app.anynear.app.DarkMode
import androidx.compose.foundation.isSystemInDarkTheme
import app.anynear.mesh.Conversation
import app.anynear.mesh.PublicChannel
import app.anynear.mesh.LinkState

private enum class Tab(val icon: ImageVector) {
    Chats(AppIcons.Nachrichten),
    /** Circles only (ADR 0100); contacts are under „Neue Nachricht“. */
    Circles(AppIcons.Kreis),
    Files(AppIcons.Dokument),

    /** Alerts and channels (ADR 0062); replaces „Mehr“, whose entries are in the ⋮ menu. */
    Alerts(AppIcons.Glocke),
    ;

    val title: String
        get() = when (this) {
            Chats -> texts.setup.tabChats
            Circles -> texts.setup.tabCircles
            Files -> texts.setup.tabFiles
            Alerts -> texts.alerts.title
        }

    /** Spoken text of the number on the tab. */
    fun badgeDescription(count: Int): String = if (this == Alerts) texts.alerts.newAlerts(count) else texts.setup.newMessages(count)
}

private val NoCount: StateFlow<Int> = MutableStateFlow(0)
private val NoNotice: StateFlow<String?> = MutableStateFlow(null)
private val NoPendingChannel: StateFlow<Alerts.PendingChannel?> = MutableStateFlow(null)
private val NoAccessHint: StateFlow<Boolean> = MutableStateFlow(false)

/** Detail page above list or chat. Back leads to where one came from. No tabs on detail pages. */
private sealed interface Detail {
    data class CircleInfo(val circleId: String) : Detail
    /** [remove]: open the „remove contact“ question right away (from the hint on an unreachable contact). */
    data class PersonInfo(val peer: String, val remove: Boolean = false) : Detail
    /** My profile: from the ⋮ menu. */
    data object OwnProfile : Detail
    data class Connections(val start: ConnectionsPage = ConnectionsPage.Main) : Detail
    data object Settings : Detail
    data object Help : Detail
    data object Troubleshooting : Detail
    data object About : Detail
    data object FilesPersonal : Detail
    data class FilesCircle(val circleId: String) : Detail
    data class ChatFiles(val conversation: Conversation) : Detail

    /** A file from „Meine Ablage“ ([circleId] `null`) or from a circle's; [SendPersonalFile] chooses who it goes to. */
    data class PersonalFile(val fileId: String, val circleId: String? = null) : Detail
    data class SendPersonalFile(val fileId: String, val circleId: String? = null) : Detail

    /** Where the file is stored online (subpage of „Online gespeichert“). */
    data class PersonalFileOnline(val fileId: String, val circleId: String? = null) : Detail
    data object Passwords : Detail
    data object MyDevices : Detail
    data class PasswordLogin(val loginId: String) : Detail

    /** Add a login ([loginId] `null`) or edit one. */
    data class PasswordEdit(val loginId: String?) : Detail

    /** A channel under „Meldungen“ (ADR 0062 §9). */
    data class AlertChannel(val channelId: String) : Detail
}

@Composable
fun App(controller: AppController) {
    val darkMode by controller.darkMode.collectAsState()
    val dark = when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    AppTheme(dark = dark) {
        val setupDone by controller.setupDone.collectAsState()
        val revoked by controller.revoked.collectAsState()
        val eraseWaiting by controller.eraseWaiting.collectAsState()
        val updateNotice by controller.updateNotice.collectAsState()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                // Status bar in the colour of the header area (icons follow the system mode, see MainActivity).
                Box(Modifier.fillMaxWidth().background(AppTheme.colors.header).windowInsetsTopHeight(WindowInsets.statusBars))
                // On the Mac and the iPad the window is much wider than a phone: content as a centred
                // column, otherwise buttons and input fields stretch across the full width.
                // The main view divides up the width itself (sidebar, see MainScreen).
                val main = !revoked && !eraseWaiting && setupDone
                Column(Modifier.weight(1f).fillMaxWidth().then(if (main) Modifier else Modifier.wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = MAX_CONTENT_WIDTH))) {
                    if (eraseWaiting) {
                        Box(Modifier.weight(1f)) { EraseWaitingScreen() }
                    } else if (revoked) {
                        // Removed by another device of one's own (ADR 0042 §6): nothing works any more, only deleting.
                        Box(Modifier.weight(1f)) { RevokedScreen(controller) }
                    } else if (!setupDone) {
                        Box(Modifier.weight(1f).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
                            FamilySetupScreen(controller)
                        }
                    } else if (updateNotice is UpdateNotice.Block) {
                        // Required update (ADR 0096 §5): only the interface is covered, the core keeps running.
                        Box(Modifier.weight(1f)) { UpdateBlockScreen(controller) }
                    } else {
                        Box(Modifier.weight(1f)) { MainScreen(controller) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScreen(controller: AppController) {
    // Rotating, a new window size or a different system mode must not jump back to „Nachrichten“:
    // the tab survives a recreated activity (the manifest keeps it alive on rotation anyway).
    var tab by rememberSaveable { mutableStateOf(Tab.Chats) }
    // Open conversation (circle or person). Back always leads to the „Nachrichten“ overview. Kept like the tab: if
    // Android ends the activity while the camera or the photo picker is in front, the photo arrives in this chat.
    var open by rememberSaveable(stateSaver = OpenConversationSaver) { mutableStateOf<Conversation?>(null) }
    // The conversation opened under a device (contact link) follows when the device turns out to be a person (ADR 0042).
    LaunchedEffect(Unit) {
        controller.peerRenamed.collect { (old, new) ->
            if ((open as? Conversation.Direct)?.peer == old) open = Conversation.Direct(new)
        }
    }
    // Check-in without a circle (ADR 0083): create one, then its chat opens.
    var creatingCircle by remember { mutableStateOf(false) }
    // Open public chat (ADR 0055); separate from circles and people.
    var openPublic by remember { mutableStateOf<PublicChannel?>(null) }
    // Open detail pages, the topmost is shown (e.g. circle → member).
    var details by remember { mutableStateOf<List<Detail>>(emptyList()) }
    // „Neue Nachricht“: choosing who to write to, above the „Nachrichten“ overview.
    var newMessage by remember { mutableStateOf(false) }
    val showDetail: (Detail) -> Unit = { details = details + it }
    val closeDetail: () -> Unit = { details = details.dropLast(1) }
    val openRequest by controller.openRequest.collectAsState()
    val openPublicRequest by controller.openPublicRequest.collectAsState()
    val unread by controller.unreadCount.collectAsState()
    val alerts = controller.alerts
    val alertsUnread by (alerts?.unreadCount ?: NoCount).collectAsState()
    val alertNotice by (alerts?.notice ?: NoNotice).collectAsState()
    val pendingChannel by (alerts?.pending ?: NoPendingChannel).collectAsState()
    val accessHint by (alerts?.accessHint ?: NoAccessHint).collectAsState()
    val openChannelRequest by (alerts?.openRequest ?: NoNotice).collectAsState()
    val badges = mapOf(Tab.Chats to unread, Tab.Alerts to alertsUnread)
    val linkState by controller.linkState.collectAsState()
    val online by controller.onlineReachable.collectAsState()
    val internet by controller.internetAvailable.collectAsState()
    val updateNotice by controller.updateNotice.collectAsState()
    val circle by controller.selectedCircle.collectAsState()
    val notice by controller.notice.collectAsState()
    val pending by controller.pendingInvite.collectAsState()
    val pendingContact by controller.pendingContact.collectAsState()
    val backgroundHint by controller.backgroundHint.collectAsState()
    // Shared from another app: first choose who it goes to, then it sits as a draft in the conversation.
    val shared by controller.shared.collectAsState()
    // Recompute as soon as someone signs in.
    val circleMembers by controller.circleMembers.collectAsState()
    // Update the name in the conversation header as soon as a contact that was just added is stored.
    val people by controller.people.collectAsState()
    val supportPeer by controller.supportPeer.collectAsState()
    val unreachableHint by controller.unreachableHint.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val sendScope = rememberCoroutineScope()
    val openChat: (Conversation) -> Unit = { conversation ->
        if (conversation is Conversation.Circle) controller.selectCircle(conversation.circleId)
        tab = Tab.Chats
        open = conversation
        openPublic = null
        details = emptyList()
        newMessage = false
    }
    // From „Kontakte“ and „Kreise“: open the conversation, the tab stays so that Back leads to the list again.
    // The detail page is reached via the name in the chat header.
    val openChatHere: (Conversation) -> Unit = { conversation ->
        if (conversation is Conversation.Circle) controller.selectCircle(conversation.circleId)
        open = conversation
        openPublic = null
        details = emptyList()
    }
    // Notification tapped: straight into the conversation.
    LaunchedEffect(openRequest) {
        openRequest?.let {
            openChat(it)
            controller.consumeOpenRequest()
        }
    }
    // The same for a public chat (ADR 0055 §4).
    LaunchedEffect(openPublicRequest) {
        openPublicRequest?.let {
            tab = Tab.Chats
            open = null
            details = emptyList()
            newMessage = false
            openPublic = it
            controller.consumeOpenPublicRequest()
        }
    }
    // Notification of an alert tapped: into the channel.
    LaunchedEffect(openChannelRequest) {
        openChannelRequest?.let {
            tab = Tab.Alerts
            open = null
            openPublic = null
            newMessage = false
            // Check-ins (ADR 0083) are at the top of „Meldungen“, not in a channel.
            details = if (it.startsWith(LifeSigns.CHANNEL_PREFIX)) emptyList() else listOf(Detail.AlertChannel(it))
            // Question or ongoing notification tapped: straight to the big button.
            if (it.startsWith(LifeSigns.CHANNEL_PREFIX)) controller.lifeSigns?.openFullScreen()
            alerts?.consumeOpenRequest()
        }
    }
    // Notification of the search run tapped (ADR 0071): „Meine Ablage“, where „Dokumente finden“ opens.
    val findDocsRequest by controller.findDocsRequest.collectAsState()
    LaunchedEffect(findDocsRequest) {
        if (findDocsRequest) {
            tab = Tab.Files
            open = null
            openPublic = null
            newMessage = false
            details = listOf(Detail.FilesPersonal)
        }
    }
    // Link https://anynear.app/s#erase (ADR 0089): to the settings, where „Alle Daten löschen“ asks for confirmation.
    val eraseRequest by controller.eraseRequest.collectAsState()
    LaunchedEffect(eraseRequest) {
        if (eraseRequest) {
            open = null
            openPublic = null
            newMessage = false
            details = listOf(Detail.Settings)
        }
    }
    val selectTab: (Tab) -> Unit = { entry ->
        tab = entry
        open = null
        openPublic = null
        details = emptyList()
        newMessage = false
    }
    val goBack: () -> Unit = {
        when {
            shared != null -> controller.dismissShared()
            details.isNotEmpty() -> closeDetail()
            open != null -> open = null
            openPublic != null -> openPublic = null
            newMessage -> newMessage = false
            else -> tab = Tab.Chats
        }
    }
    // Menu bar of the Mac and keyboard shortcuts on the iPad (⌘N, ⌘,, ⌘1 … ⌘4, ⌘[).
    LaunchedEffect(Unit) {
        controller.commands.collect { command ->
            when (command) {
                AppCommand.NewMessage -> {
                    selectTab(Tab.Chats)
                    newMessage = true
                }
                AppCommand.Settings -> details = listOf(Detail.Settings)
                AppCommand.Help -> details = listOf(Detail.Help)
                AppCommand.ShowChats -> selectTab(Tab.Chats)
                AppCommand.ShowCircles -> selectTab(Tab.Circles)
                AppCommand.ShowFiles -> selectTab(Tab.Files)
                AppCommand.ShowAlerts -> selectTab(Tab.Alerts)
                AppCommand.Back -> goBack()
            }
        }
    }
    // Lock passwords when one leaves the area, after the grace period (ADR 0048 item 6): whoever switches briefly into
    // a chat and back does not have to unlock again.
    val inPasswords = details.any { it is Detail.Passwords || it is Detail.PasswordLogin || it is Detail.PasswordEdit }
    LaunchedEffect(inPasswords) { controller.setInPasswords(inPasswords) }
    SystemBackHandler(enabled = shared != null || details.isNotEmpty() || open != null || openPublic != null || newMessage || tab != Tab.Chats, onBack = goBack)
    LaunchedEffect(notice) {
        // Show first, then delete: deleting changes the key and would otherwise abort the display.
        notice?.let {
            snackbar.showSnackbar(it)
            controller.clearNotice()
        }
    }
    LaunchedEffect(alertNotice) {
        alertNotice?.let {
            snackbar.showSnackbar(it)
            alerts?.clearNotice()
        }
    }
    // ⋮ menu (ADR 0062 §1): opens what used to be under „Mehr“.
    val openMenuTarget: (MenuTarget) -> Unit = { target ->
        details = listOf(
            when (target) {
                MenuTarget.Profile -> Detail.OwnProfile
                MenuTarget.About -> Detail.About
                MenuTarget.Internet -> Detail.Connections(ConnectionsPage.Internet)
                MenuTarget.Radio -> Detail.Connections(ConnectionsPage.Radio)
                MenuTarget.Devices -> Detail.MyDevices
                MenuTarget.Settings -> Detail.Settings
                MenuTarget.Troubleshooting -> Detail.Troubleshooting
                MenuTarget.Help -> Detail.Help
            },
        )
    }

    // Wide window (Mac, iPad landscape): areas on the left as a sidebar, always visible, content as a centred column.
    BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent { e ->
        // Escape leads back like the Back key (keyboard on Mac and iPad).
        (e.type == KeyEventType.KeyDown && e.key == Key.Escape && (shared != null || details.isNotEmpty() || open != null || newMessage))
            .also { if (it) goBack() }
    }) {
        val wide = maxWidth >= SIDEBAR_MIN_WIDTH
        Row(Modifier.fillMaxSize()) {
            if (wide) SideBar(selected = tab, badges = badges, onSelect = selectTab)
            Box(Modifier.weight(1f).fillMaxHeight().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = MAX_CONTENT_WIDTH)) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    // In the open conversation, at „Neue Nachricht“ and on the pages behind it no tabs: more room for
                    // messages, especially with large font. Back leads to the overview one came from.
                    bottomBar = {
                        if (!wide && shared == null && open == null && details.isEmpty() && !newMessage) {
                            TabBar(selected = tab, badges = badges, onSelect = selectTab)
                        }
                    },
                ) { padding ->
                    // Only report when no path at all is open right now. A missing radio is normal.
                    val banner: (@Composable () -> Unit)? = if (!online && linkState != LinkState.Connected) {
                        { StatusBanner(linkState, internet, onRequestPermission = controller::requestBluetoothPermission, onTakeOver = controller::takeOverRadio) }
                    } else if (updateNotice.isBanner) {
                        { UpdateBanner(controller, updateNotice) }
                    } else {
                        null
                    }
                    Column(
                        Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()
                            // Swipe from the left edge (iOS): the screen follows the finger to the right.
                            .graphicsLayer {
                                val pulled = BackSwipe.progress.floatValue
                                translationX = pulled * size.width
                                alpha = 1f - 0.4f * pulled
                            },
                    ) {
                        CompositionLocalProvider(
                            LocalBelowHeader provides banner,
                            LocalHeaderStatus provides { RadioHeaderStatus(controller, onClick = { if (details.lastOrNull() !is Detail.Connections) showDetail(Detail.Connections()) }) },
                            LocalHeaderMenu provides { MainMenu(controller, onOpen = openMenuTarget) },
                            LocalHeaderTasks provides { FinderHeaderTask(controller) },
                        ) {
                            val conversation = open
                            // Circle chat shows the selected circle; if it disappears (left), back to the overview.
                            val openCircle = (conversation as? Conversation.Circle)?.let { c -> circle?.takeIf { it.id == c.circleId } }
                            val sharedNow = shared
                            if (sharedNow != null) {
                                NewMessageScreen(
                                    controller,
                                    onBack = controller::dismissShared,
                                    onPick = { conversation ->
                                        sendScope.launch {
                                            controller.putSharedIntoDraft(conversation)?.let(controller::showNotice)
                                            openChat(conversation)
                                        }
                                    },
                                    title = if (sharedNow.forward) texts.chat.forwardTitle else texts.chat.shareTitle,
                                    question = if (sharedNow.forward) texts.chat.forwardQuestion else texts.chat.shareQuestion,
                                    preview = sharedNow.text.trim().ifEmpty {
                                        sharedNow.files.joinToString(", ") { it.name }.ifEmpty { sharedNow.drafts.joinToString(", ") { draftLabel(it) } }
                                    },
                                    invitedCircle = remember(sharedNow) { controller.invitedOwnCircle(sharedNow.text) },
                                )
                            } else when (val detail = details.lastOrNull()) {
                                is Detail.CircleInfo -> CircleDetailScreen(
                                    controller = controller,
                                    circleId = detail.circleId,
                                    onBack = closeDetail,
                                    onOpenPerson = { showDetail(Detail.PersonInfo(it)) },
                                    onOpenFiles = { showDetail(Detail.FilesCircle(detail.circleId)) },
                                    onOpenChatFiles = { showDetail(Detail.ChatFiles(Conversation.Circle(detail.circleId))) },
                                    // Coming from the circle chat, this leads back into it; from „Kreise“ it opens it.
                                    onWrite = { openChatHere(Conversation.Circle(detail.circleId)) },
                                )
                                Detail.OwnProfile -> ProfileScreen(controller, onBack = closeDetail)
                                is Detail.Connections -> ConnectionsScreen(controller, onBack = closeDetail, start = detail.start)
                                Detail.Settings -> SettingsPageScreen(controller, onBack = closeDetail)
                                Detail.Help -> HelpScreen(controller, onBack = closeDetail, onOpenChat = openChat)
                                Detail.Troubleshooting -> TroubleshootingScreen(controller, onBack = closeDetail, onOpenChat = openChat)
                                Detail.About -> AboutScreen(controller, onBack = closeDetail)
                                Detail.FilesPersonal -> PersonalFilesScreen(controller, onBack = closeDetail, onOpenFile = { showDetail(Detail.PersonalFile(it)) })
                                is Detail.PersonalFile -> PersonalFileScreen(
                                    controller, detail.fileId, onBack = closeDetail,
                                    onSend = { showDetail(Detail.SendPersonalFile(detail.fileId, detail.circleId)) },
                                    onShowOnline = { showDetail(Detail.PersonalFileOnline(detail.fileId, detail.circleId)) },
                                    circleId = detail.circleId,
                                )
                                is Detail.PersonalFileOnline -> PersonalFileOnlineScreen(controller, detail.fileId, onBack = closeDetail, circleId = detail.circleId)
                                // Like „Neue Nachricht“: choose circle or person, the file goes as an attachment, then one is in the conversation.
                                is Detail.SendPersonalFile -> NewMessageScreen(controller, onBack = closeDetail, onPick = { conversation ->
                                    sendScope.launch {
                                        val problem = sendPersonalFile(controller, detail.fileId, conversation, detail.circleId)
                                        if (problem == null) openChat(conversation) else controller.showNotice(problem)
                                    }
                                })
                                is Detail.FilesCircle -> PersonalFilesScreen(
                                    controller, onBack = closeDetail,
                                    onOpenFile = { showDetail(Detail.PersonalFile(it, detail.circleId)) },
                                    circleId = detail.circleId,
                                )
                                is Detail.ChatFiles -> ChatFilesScreen(
                                    controller,
                                    detail.conversation,
                                    title = when (val c = detail.conversation) {
                                        is Conversation.Circle -> controller.circles.value.firstOrNull { it.id == c.circleId }?.name.orEmpty()
                                        is Conversation.Direct -> controller.peerName(c.peer)
                                    },
                                    onBack = closeDetail,
                                )
                                Detail.MyDevices -> MyDevicesScreen(controller, onBack = closeDetail)
                                Detail.Passwords -> PasswordsScreen(
                                    controller,
                                    onBack = closeDetail,
                                    onOpenLogin = { showDetail(Detail.PasswordLogin(it)) },
                                    onAdd = { showDetail(Detail.PasswordEdit(null)) },
                                )
                                is Detail.PasswordLogin -> PasswordLoginScreen(
                                    controller, detail.loginId, onBack = closeDetail,
                                    onEdit = { showDetail(Detail.PasswordEdit(detail.loginId)) },
                                )
                                is Detail.PasswordEdit -> PasswordEditScreen(
                                    controller, detail.loginId, onBack = closeDetail,
                                    // New: show the login right away instead of the form; edited: back to the login.
                                    onSaved = { id ->
                                        details = details.dropLast(1).let { if (detail.loginId == null) it + Detail.PasswordLogin(id) else it }
                                    },
                                )
                                is Detail.AlertChannel -> if (alerts != null) ChannelScreen(alerts, detail.channelId, onBack = closeDetail)
                                is Detail.PersonInfo -> PersonDetailScreen(
                                    controller = controller,
                                    peer = detail.peer,
                                    askRemove = detail.remove,
                                    onBack = closeDetail,
                                    onOpenCircle = { openChatHere(Conversation.Circle(it)) },
                                    // Coming from the conversation, this leads back into the same one; otherwise it opens the conversation.
                                    onWrite = { openChatHere(Conversation.Direct(detail.peer)) },
                                    onOpenChatFiles = { showDetail(Detail.ChatFiles(Conversation.Direct(detail.peer))) },
                                    onRemoved = {
                                        open = null
                                        details = emptyList()
                                    },
                                )
                                null -> when {
                                    openPublic != null && controller.publicRadio != null -> PublicChatScreen(
                                        controller,
                                        controller.publicRadio,
                                        openPublic!!,
                                        onBack = { openPublic = null },
                                    )
                                    conversation is Conversation.Direct -> ChatScreen(
                                        controller = controller,
                                        conversation = conversation,
                                        title = people.firstOrNull { it.peer == conversation.peer }?.name ?: controller.peerName(conversation.peer),
                                        // As in the design: the header leads to the profile; „Zuletzt kontaktiert“ is shown there.
                                        subtitle = texts.chat.viewProfile,
                                        onBack = { open = null },
                                        onTitleClick = { showDetail(Detail.PersonInfo(conversation.peer)) },
                                        avatar = { PersonAvatar(controller, conversation.peer, controller.findPerson(conversation.peer)?.image, 40.dp, enlargeable = true) },
                                        // With support only via the form, a separate circle per question (ADR 0065).
                                        readOnlyReason = if (conversation.peer == supportPeer) texts.support.directBlocked else null,
                                        readOnlyAction = if (conversation.peer == supportPeer) texts.support.openForm to { controller.command(AppCommand.Help) } else null,
                                        topHint = controller.findPerson(conversation.peer)?.deviceId?.takeIf { it in unreachableHint }?.let { person ->
                                            @Composable {
                                                UnreachableNotice(
                                                    name = controller.findPerson(conversation.peer)?.name.orEmpty(),
                                                    onRemove = { showDetail(Detail.PersonInfo(conversation.peer, remove = true)) },
                                                    onKeep = { controller.keepUnreachable(person) },
                                                )
                                            }
                                        },
                                    )
                                    openCircle != null -> ChatScreen(
                                        controller = controller,
                                        conversation = Conversation.Circle(openCircle.id),
                                        title = openCircle.name,
                                        subtitle = circleSummary(openCircle, circleMembers[openCircle.id].orEmpty().size),
                                        onBack = { open = null },
                                        onTitleClick = { showDetail(Detail.CircleInfo(openCircle.id)) },
                                        avatar = { CircleAvatar(controller, openCircle.id, openCircle.image, 40.dp, enlargeable = true) },
                                        onShare = if (openCircle.removed) null else ({ controller.showInvite(openCircle.id) }),
                                        alone = !openCircle.removed && circleMembers[openCircle.id].isNullOrEmpty() && controller.isAlone(openCircle.id),
                                        readOnlyReason = if (openCircle.removed) texts.setup.removedFromCircle else null,
                                    )
                                    tab == Tab.Chats && newMessage -> NewMessageScreen(controller, onBack = { newMessage = false }, onPick = openChat)
                                    tab == Tab.Chats -> ChatListScreen(
                                        controller,
                                        onOpen = openChat,
                                        onNewMessage = { newMessage = true },
                                        onOpenPublic = { openPublic = it },
                                        onOpenInfo = { c ->
                                            when (c) {
                                                is Conversation.Circle -> showDetail(Detail.CircleInfo(c.circleId))
                                                is Conversation.Direct -> showDetail(Detail.PersonInfo(c.peer))
                                            }
                                        },
                                    )
                                    // Circles (ADR 0100): first the circle detail, from there into the chat.
                                    tab == Tab.Circles -> CirclesScreen(controller, onOpenCircle = { showDetail(Detail.CircleInfo(it)) }, onOpenChat = openChatHere)
                                    tab == Tab.Files -> FilesScreen(
                                        controller,
                                        onOpenPersonal = { showDetail(Detail.FilesPersonal) },
                                        onOpenCircle = { showDetail(Detail.FilesCircle(it)) },
                                        onOpenPasswords = { showDetail(Detail.Passwords) },
                                        onOpenFile = { id, circleId -> showDetail(Detail.PersonalFile(id, circleId)) },
                                    )
                                    tab == Tab.Alerts && alerts != null -> AlertsScreen(
                                        alerts, controller.lifeSigns, controller.circles, onOpenChannel = { showDetail(Detail.AlertChannel(it)) },
                                        onCreateCircle = { creatingCircle = true },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (creatingCircle) CreateCircleDialog(controller, onDismiss = { creatingCircle = false }, onCreated = { openChat(Conversation.Circle(it)) })
    InviteDialog(controller)
    PendingPairingDialog(controller)
    UnknownDeviceDialog(controller)
    ReplaceDeviceDialog(controller)
    PasswordRequestDialog(controller)
    pending?.let { PendingInviteDialog(controller, it) }
    pendingContact?.let { PendingContactDialog(controller, it.name) }
    pendingChannel?.let { if (alerts != null) PendingChannelDialog(alerts, it) }
    val backgroundNotice by controller.backgroundNotice.collectAsState()
    if (backgroundNotice) BackgroundNoticeDialog(controller)
    val linkOpen = pending != null || pendingContact != null || pendingChannel != null || backgroundNotice
    // On its own initiative always only one hint (AppHints.kt).
    when (currentHint(controller, linkOpen)) {
        AppHint.RADIO_SOFTWARE_RUN -> RadioSoftwareProgress(controller)
        AppHint.USB_RESCUE -> UsbRescueHost(controller)
        AppHint.RADIO_AREA -> RadioAreaQuestion(controller)
        AppHint.DEVICE_SETUP -> DeviceSetupDialog(controller)
        AppHint.RADIO_IN_USE -> RadioInUseDialog(controller)
        AppHint.PAIRING -> PairingTroubleDialog(controller)
        AppHint.RADIO_SOFTWARE_NOTICE -> RadioSoftwareNotice(controller)
        null -> if (!linkOpen) {
            AppUpdateDialog(controller)
            OldAppDialog(controller)
            backgroundHint?.let { BackgroundHintDialog(controller, it) }
            if (backgroundHint == null && accessHint && alerts != null) AlarmAccessDialog(alerts)
        }
    }
}

/**
 * Tabs at the bottom. If the names no longer fit into four columns with large font, they are placed in two rows
 * of two each: the font stays as large as set, no word is hyphenated or cut off.
 */
@Composable
private fun TabBar(selected: Tab, badges: Map<Tab, Int>, onSelect: (Tab) -> Unit) {
    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    Surface(color = MaterialTheme.colorScheme.background) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        BoxWithConstraints(Modifier.windowInsetsPadding(NavigationBarDefaults.windowInsets)) {
            val widest = Tab.entries.maxOf { measurer.measure(it.title, style.copy(fontWeight = FontWeight.SemiBold)).size.width }
            val fitsInOneRow = with(LocalDensity.current) { widest.toDp() + TAB_LABEL_PADDING * 2 } <= maxWidth / Tab.entries.size
            val rows = if (fitsInOneRow) listOf(Tab.entries) else Tab.entries.chunked(2)
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { entry -> TabItem(entry, entry == selected, badges[entry] ?: 0, style) { onSelect(entry) } }
                    }
                }
            }
        }
    }
}

private val TAB_LABEL_PADDING = 8.dp

/** From this window width on the areas are a sidebar on the left instead of tabs at the bottom. */
private val SIDEBAR_MIN_WIDTH = 860.dp

/** Sidebar in wide windows: the same areas as the tabs, one below the other, with names. */
@Composable
private fun SideBar(selected: Tab, badges: Map<Tab, Int>, onSelect: (Tab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainer, modifier = Modifier.fillMaxHeight().width(SIDEBAR_WIDTH)) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Bottom))
                .padding(horizontal = 12.dp, vertical = 16.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tab.entries.forEach { entry ->
                val on = entry == selected
                val count = badges[entry] ?: 0
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp)
                        .background(if (on) scheme.secondaryContainer else Color.Transparent, CircleShape)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(entry) })
                        .then(if (count > 0) Modifier.semantics { stateDescription = entry.badgeDescription(count) } else Modifier)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(entry.icon, contentDescription = null, tint = if (on) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) scheme.onSurface else scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (count > 0) {
                        Badge(containerColor = scheme.secondary, contentColor = scheme.onSecondary, modifier = Modifier.clearAndSetSemantics {}) {
                            Text(if (count > 99) "99+" else count.toString())
                        }
                    }
                }
            }
        }
    }
}

private val SIDEBAR_WIDTH = 220.dp

/** Maximum width of the content in wide windows (Mac, iPad landscape), roughly a large phone. */
internal val MAX_CONTENT_WIDTH = 640.dp

@Composable
private fun RowScope.TabItem(entry: Tab, selected: Boolean, unread: Int, style: TextStyle, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f)
            .heightIn(min = 80.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .then(
                if (unread > 0) Modifier.semantics { stateDescription = entry.badgeDescription(unread) }
                else Modifier,
            )
            .padding(horizontal = TAB_LABEL_PADDING, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        // Selection as in the design: icon in orange, text dark and bold, below it a short orange line.
        val tint = if (selected) scheme.secondary else scheme.onSurfaceVariant
        Box(
            Modifier.size(width = 64.dp, height = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (unread > 0) {
                BadgedBox(badge = {
                    // The number is already in the tab's spoken text (stateDescription), do not read it out twice.
                    Badge(containerColor = scheme.secondary, contentColor = scheme.onSecondary, modifier = Modifier.clearAndSetSemantics {}) {
                        Text(if (unread > 99) "99+" else unread.toString())
                    }
                }) { Icon(entry.icon, contentDescription = null, tint = tint) }
            } else {
                Icon(entry.icon, contentDescription = null, tint = tint)
            }
        }
        Text(
            entry.title,
            style = style,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.size(width = 28.dp, height = 3.dp).background(if (selected) scheme.secondary else Color.Transparent, CircleShape))
    }
}

/** Android would put anynear to sleep: one sentence, one button straight to the matching setting. */
@Composable
private fun BackgroundHintDialog(controller: AppController, restriction: BackgroundRestriction) {
    AppDialog(
        onDismissRequest = controller::postponeBackgroundHint,
        title = { Text(texts.setup.backgroundTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (restriction == BackgroundRestriction.STOPPED_IN_BACKGROUND) texts.setup.backgroundStopped else texts.setup.backgroundText)
                Text(
                    when (restriction) {
                        BackgroundRestriction.BATTERY_OPTIMIZED -> texts.setup.backgroundTapAllow
                        BackgroundRestriction.BACKGROUND_RESTRICTED -> texts.setup.backgroundTapBattery
                        BackgroundRestriction.STOPPED_IN_BACKGROUND -> texts.setup.backgroundTapStopped
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { AppButton(texts.setup.openSetting, controller::openBackgroundSettings) },
        dismissButton = { AppButton(texts.common.later, controller::postponeBackgroundHint, kind = ButtonKind.Quiet) },
    )
}

/** Ongoing notification „Empfängt deine Nachrichten“ tapped: what it is, and one button to where it can be hidden. */
@Composable
private fun BackgroundNoticeDialog(controller: AppController) {
    AppDialog(
        onDismissRequest = controller::dismissBackgroundNotice,
        title = { Text(texts.setup.noticeTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(texts.setup.noticeText)
                Text(texts.setup.noticeHowToHide, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { AppButton(texts.setup.noticeHide, controller::hideBackgroundNotice) },
        dismissButton = { AppButton(texts.common.close, controller::dismissBackgroundNotice, kind = ButtonKind.Quiet) },
    )
}

/** Opened invitation link: only after „Beitreten“ does one become a member. */
@Composable
private fun PendingInviteDialog(controller: AppController, invite: AppController.InviteOffer) {
    AppDialog(
        onDismissRequest = controller::dismissPendingInvite,
        title = { Text(texts.setup.joinCircleConfirmTitle(invite.circleName)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (invite.inviter.isNotEmpty()) Text(texts.setup.invitedBy(invite.inviter), fontWeight = FontWeight.SemiBold)
                Text(texts.setup.joinCircleConfirmText)
            }
        },
        confirmButton = { AppButton(texts.setup.join, { controller.acceptPendingInvite(controller.ownName.value.ifBlank { texts.common.me }) }) },
        dismissButton = { AppButton(texts.setup.dontJoin, controller::dismissPendingInvite, kind = ButtonKind.Quiet) },
    )
}

/** Opened contact link: only after „Hinzufügen“ is the contact stored. */
@Composable
private fun PendingContactDialog(controller: AppController, name: String) {
    AppDialog(
        onDismissRequest = controller::dismissPendingContact,
        title = { Text(texts.setup.addContactConfirmTitle(name)) },
        text = { Text(texts.setup.addContactConfirmText(name), Modifier.verticalScroll(rememberScrollState())) },
        // Three paths one below the other, so that long labels and large font have room too.
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                AppButton(texts.setup.addAndWrite, { controller.confirmPendingContact(openChat = true) })
                AppButton(texts.setup.onlyAdd, { controller.confirmPendingContact(openChat = false) }, kind = ButtonKind.Quiet)
                AppButton(texts.setup.dontAdd, controller::dismissPendingContact, kind = ButtonKind.Quiet)
            }
        },
    )
}

@Composable
private fun StatusBanner(state: LinkState, internet: Boolean, onRequestPermission: () -> Unit, onTakeOver: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        // Announce the new connection situation without moving the focus.
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(texts.setup.noConnection, style = MaterialTheme.typography.titleMedium)
            // Internet is there but no mailbox answers: do not claim „Kein Internet“.
            val cause = if (internet) texts.setup.appUnreachable else texts.setup.noInternet
            Text(
                if (state == LinkState.NoDevice) texts.setup.messagesWait(cause)
                else texts.setup.messagesWaitWithLink(cause, state.label().replaceFirstChar { it.lowercase() }),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state == LinkState.InUseElsewhere) {
                // Not the hint „Unter Funkgerät …“: the button is right here.
                AppButton(texts.settings.radioInUseConnectHere, onTakeOver, kind = ButtonKind.Quiet)
            } else {
                state.hint(appName)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            if (state == LinkState.BluetoothUnavailable) {
                AppButton(texts.setup.allowAccess, onRequestPermission, kind = ButtonKind.Quiet)
            }
        }
    }
}

/** The open conversation as its key ([key]), as Android keeps saved state. */
private val OpenConversationSaver = Saver<Conversation?, String>(save = { it?.key() }, restore = { conversationOf(it) })
