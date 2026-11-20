package app.anynear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anynear.anmp.core.HistoryLoading
import app.anynear.app.AppController
import app.anynear.circles.Circle
import app.anynear.app.PublicRadio
import app.anynear.chats.ChatListEntry
import app.anynear.i18n.texts
import app.anynear.mesh.Conversation
import app.anynear.mesh.FoundDevice
import app.anynear.mesh.PublicChannel
import app.anynear.mesh.previewText
import kotlin.time.Clock
import kotlinx.coroutines.launch

/**
 * „Nachrichten“ overview, built as in common messengers: every circle and every person one
 * row, newest at the top, bold with a yellow number where something is new. Large rows, the whole row is tappable.
 * At the bottom right floats the one main action „Neue Nachricht“ ([onNewMessage]).
 */
@Composable
internal fun ChatListScreen(
    controller: AppController,
    onOpen: (Conversation) -> Unit,
    onNewMessage: () -> Unit,
    onOpenPublic: (PublicChannel) -> Unit = {},
    /** Circle page or person page, from the long-press actions. */
    onOpenInfo: (Conversation) -> Unit = {},
) {
    val entries by controller.chatList.collectAsState()
    val unread by controller.unreadCount.collectAsState()
    val circles by controller.circles.collectAsState()
    val people by controller.people.collectAsState()
    val notificationHint by controller.notificationHint.collectAsState()
    val bluetoothHint by controller.bluetoothHint.collectAsState()
    val suggestedDevice by controller.suggestedDevice.collectAsState()
    val loading by controller.historyLoading.collectAsState()
    val requests by controller.contactRequests.collectAsState()
    val publicRadio = controller.publicRadio
    val publicChannels by (publicRadio?.available ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()
    val communityOffer by (publicRadio?.offer ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    var adding by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<ChatListEntry?>(null) }
    var leaving by remember { mutableStateOf<Circle?>(null) }
    var deleting by remember { mutableStateOf<ChatListEntry?>(null) }
    val now = Clock.System.now().toEpochMilliseconds()
    var query by rememberSaveable { mutableStateOf("") }
    var requestsOpen by rememberSaveable { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()
    // The search covers the name and the last message, the way the row shows them.
    // Circles live here since the tab „Kreise“ is the map (ADR 0121 §1): a filter replaces the list of circles.
    var kind by rememberSaveable { mutableStateOf(0) }
    val shown = entries.filter { matchesSearch(query, it.title, it.preview(loading)) }
        .filter { kind == 0 || (kind == 1) == (it.conversation is Conversation.Circle) }
    // While searching, also circles and contacts without a conversation yet (ADR 0100 §2): otherwise one would think
    // the person is gone. Tapping opens the conversation, like „Neue Nachricht“.
    val circleMembers by controller.circleMembers.collectAsState()
    val supportPeer by controller.supportPeer.collectAsState()
    val listed = entries.map { it.conversation }.toSet()
    val circleNames = circles.associate { it.id to it.name }
    val moreCircles = if (query.isBlank()) emptyList() else circles.filter {
        !it.removed && Conversation.Circle(it.id) !in listed && matchesSearch(query, it.name)
    }
    val morePeople = if (query.isBlank()) emptyList() else people.filter { person ->
        person.peer != supportPeer && Conversation.Direct(person.peer) !in listed &&
            matchesSearch(query, person.name, *person.circleIds.map(circleNames::get).toTypedArray())
    }
    // Space below the list, as high as the button (grows with the font size), so that it never covers the last entry.
    var buttonHeight by remember { mutableStateOf(MainButtonHeight) }
    val density = LocalDensity.current

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = texts.chat.messages,
            subtitle = when (unread) {
                0 -> null
                else -> texts.chat.newMessages(unread)
            },
            // Also in the ⋮ menu (ADR 0121 §1); the visible button stays, it is the main task of the page.
            menu = listOf(
                MenuAction(texts.map.newMessage, AppIcons.Nachrichten, onNewMessage),
                MenuAction(texts.map.newCircle, AppIcons.Kreis) { creating = true },
            ),
        )
        // A running call for the helpers, the own one or one of someone we help (ADR 0083 §2).
        // New device or back from the emergency card: the messages are still coming (ADR 0107 §8).
        if (loading.any) HistoryStrip(texts.status.messagesLoading)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize()) {
                if (entries.isNotEmpty() || people.isNotEmpty() || circles.isNotEmpty()) {
                    SearchField(query, { query = it }, texts.chat.search, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp))
                }
                if (entries.any { it.conversation is Conversation.Circle } && entries.any { it.conversation !is Conversation.Circle }) {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(texts.map.filterAll, texts.map.filterCircles, texts.map.filterPeople).forEachIndexed { i, label ->
                            androidx.compose.material3.FilterChip(selected = kind == i, onClick = { kind = i }, label = { Text(label) })
                        }
                    }
                }
                if (shown.isEmpty() && moreCircles.isEmpty() && morePeople.isEmpty() && query.isNotBlank()) NoSearchResults()
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = buttonHeight + FLOATING_MARGIN * 2),
                ) {
                    // Cards only without a search, so that the hits are right below the field.
                    if (query.isBlank()) {
                        // In the list, so that both scroll along with large font and the button covers nothing.
                        // After a crash ask once whether we get a report (ADR 0082).
                        item(key = "crash-report") { CrashCard(controller, onOpen) }
                        // Persons asking to become a contact (ADR 0116): agree or decline; what they wrote waits until then.
                        // Several requests are one card that unfolds, so that they do not push the chats out of sight.
                        if (requests.size > 1) {
                            item(key = "requests") {
                                RequestsCard(requests.size, requestsOpen) { requestsOpen = !requestsOpen }
                            }
                        }
                        if (requests.size == 1 || requestsOpen) {
                            requests.forEach { r ->
                                item(key = "request-${r.person}") {
                                    PermissionCard(
                                        texts.people.requestTitle(r.name),
                                        controller.requestOrigin(r),
                                        onAllow = { uiScope.launch { controller.acceptContactRequestNow(r.person)?.let(onOpen) } },
                                        onLater = { controller.declineContactRequest(r.person) },
                                        allowLabel = texts.people.requestAgree,
                                        laterLabel = texts.people.requestDecline,
                                    )
                                }
                            }
                        }
                        item(key = "emergency-card") { EmergencyCardReminder(controller) }
                        if (bluetoothHint) {
                            item(key = "bluetooth-hint") {
                                PermissionCard(texts.chat.allowBluetoothTitle, texts.chat.allowBluetoothText, controller::allowBluetoothFromHint, controller::postponeBluetoothHint)
                            }
                        }
                        suggestedDevice?.let { device ->
                            item(key = "radio-suggestion") {
                                PermissionCard(
                                    texts.setup.radioFound,
                                    if (device.connection == FoundDevice.Connection.BLUETOOTH) texts.setup.suggestedKnownRadio(device.name)
                                    else texts.setup.suggestedRadio(device.name),
                                    controller::acceptSuggestedDevice,
                                    controller::postponeSuggestedDevice,
                                    allowLabel = texts.setup.connect,
                                )
                            }
                        }
                        // Radio community of the area (ADR 0054 §1a): ask once, calmly, never as a dialog at start.
                        communityOffer?.let { offer ->
                            item(key = "community-offer") {
                                if (offer.freeSlots > 0) {
                                    PermissionCard(
                                        texts.publicRadio.offerTitle(offer.name),
                                        texts.publicRadio.offerText(offer.name, offer.freeSlots),
                                        onAllow = { publicRadio?.acceptCommunity() },
                                        onLater = { publicRadio?.declineCommunity() },
                                        allowLabel = texts.publicRadio.offerYes,
                                        laterLabel = texts.publicRadio.offerNo,
                                    )
                                } else {
                                    PermissionCard(
                                        texts.publicRadio.offerTitle(offer.name),
                                        texts.publicRadio.offerNoSlot(offer.name),
                                        onAllow = null,
                                        onLater = { publicRadio?.dismissNoSlot() },
                                        laterLabel = texts.publicRadio.introOk,
                                    )
                                }
                            }
                        }
                        if (notificationHint) {
                            item(key = "notification-hint") {
                                PermissionCard(texts.chat.allowNotificationsTitle, texts.chat.allowNotificationsText, controller::allowNotifications, controller::postponeNotificationHint)
                            }
                        }
                    }
                    if (entries.isEmpty() && query.isBlank()) {
                        item(key = "empty") {
                            if (people.isEmpty() && circles.isEmpty()) {
                                // Nobody there yet: the two paths to others, without a second orange action.
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(texts.chat.emptyNobodyYet, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    AppButton(texts.chat.addContact, { adding = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = AppIcons.Plus)
                                    AppButton(texts.chat.startCircle, { creating = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = AppIcons.Plus)
                                    Text(texts.chat.emptyInviteHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                Text(
                                    texts.chat.emptyChatList,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                                )
                            }
                        }
                    }
                    items(shown, key = { it.key }) { entry ->
                        ChatListRow(entry, now, loading, onClick = { onOpen(entry.conversation) }, onLongClick = { actionsFor = entry }) {
                            when (val c = entry.conversation) {
                                is Conversation.Circle -> CircleAvatar(controller, c.circleId, circles.firstOrNull { it.id == c.circleId }?.image, AVATAR_SIZE)
                                is Conversation.Direct -> PersonAvatar(controller, c.peer, people.firstOrNull { it.peer == c.peer }?.image, AVATAR_SIZE)
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 84.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (moreCircles.isNotEmpty()) item(key = "more-circles") {
                        SearchMore(texts.chat.circles) {
                            moreCircles.forEach { circle ->
                                AppRow(
                                    title = circle.name,
                                    subtitle = circleSummary(circle, circleMembers[circle.id].orEmpty().size),
                                    leading = { CircleAvatar(controller, circle.id, circle.image, 44.dp) },
                                    onClickLabel = texts.chat.writeInCircle,
                                    onClick = { onOpen(Conversation.Circle(circle.id)) },
                                )
                            }
                        }
                    }
                    if (morePeople.isNotEmpty()) item(key = "more-people") {
                        SearchMore(texts.chat.contacts) {
                            morePeople.forEach { person ->
                                val names = person.circleIds.mapNotNull(circleNames::get)
                                AppRow(
                                    title = person.name,
                                    subtitle = if (names.isEmpty()) texts.chat.addedByYou else names.joinToString(" · "),
                                    leading = { PersonAvatar(controller, person.peer, person.image, 44.dp) },
                                    onClickLabel = texts.chat.writeMessage,
                                    onClick = { onOpen(Conversation.Direct(person.peer)) },
                                )
                            }
                        }
                    }
                    // Public radio (ADR 0055): a section of its own below circles and people, never in between.
                    if (publicRadio != null && publicChannels.isNotEmpty() && query.isBlank()) {
                        item(key = "public-section") {
                            Text(
                                texts.publicRadio.section,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                            )
                        }
                        items(publicChannels, key = { "public-${it.name}" }) { channel ->
                            PublicChatRow(publicRadio, channel, onClick = { onOpenPublic(channel) })
                            HorizontalDivider(Modifier.padding(start = 84.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            AppButton(
                texts.chat.newMessage,
                onNewMessage,
                kind = ButtonKind.Main,
                icon = AppIcons.Stift,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(FLOATING_MARGIN)
                    .onSizeChanged { buttonHeight = with(density) { it.height.toDp() } },
            )
        }
    }
    // Long press on a row (FUT 14.11.2026): the actions of the circle or person page that one looks for here, with
    // the same confirmation dialogs as there.
    actionsFor?.let { entry ->
        ActionsDialog(entry.title, onDismiss = { actionsFor = null }) {
            when (val c = entry.conversation) {
                is Conversation.Circle -> {
                    AppRow(title = texts.chat.viewCircle, icon = AppIcons.Kreis, onClick = { actionsFor = null; onOpenInfo(c) })
                    circles.firstOrNull { it.id == c.circleId }?.let { circle ->
                        AppRow(
                            title = if (circle.removed) texts.people.removeFromList else texts.people.leaveCircle,
                            icon = AppIcons.Verlassen,
                            danger = true,
                            onClick = { actionsFor = null; leaving = circle },
                        )
                    }
                }
                is Conversation.Direct -> {
                    AppRow(title = texts.chat.viewProfile, icon = AppIcons.Person, onClick = { actionsFor = null; onOpenInfo(c) })
                    AppRow(title = texts.people.deleteHistory, icon = AppIcons.Schliessen, danger = true, onClick = { actionsFor = null; deleting = entry })
                }
            }
        }
    }
    leaving?.let { LeaveCircleDialog(controller, it, onDismiss = { leaving = null }) }
    deleting?.let { entry ->
        (entry.conversation as? Conversation.Direct)?.let { c -> DeleteHistoryDialog(controller, c.peer, entry.title, onDismiss = { deleting = null }) }
    }
    if (adding) AddContactFlow(controller, onDismiss = { adding = false }, onOpenChat = { onOpen(Conversation.Direct(it)) })
    if (creating) CreateCircleDialog(controller, onDismiss = { creating = false }, onCreated = { onOpen(Conversation.Circle(it)) })
}

/** Explain first, then ask: allow notifications, without a system prompt on first start. */
/** Request for a permission at the top of messages: short sentence, „Erlauben“ and „Später“ (notifications, Bluetooth). */
@Composable
private fun PermissionCard(
    title: String,
    text: String,
    onAllow: (() -> Unit)?,
    onLater: () -> Unit,
    allowLabel: String = texts.chat.allow,
    laterLabel: String = texts.common.later,
) {
    AppSection(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        SectionContent {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            onAllow?.let { AppButton(allowLabel, it, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary) }
            AppButton(laterLabel, onLater, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet)
        }
    }
}

/** Several contact requests at once: their number, unfolds the single requests below. */
@Composable
private fun RequestsCard(count: Int, open: Boolean, onToggle: () -> Unit) {
    AppSection(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        SectionContent {
            Text(texts.people.requestsTitle(count), style = MaterialTheme.typography.titleMedium)
            AppButton(
                if (open) texts.people.requestsHide else texts.people.requestsShow,
                onToggle,
                Modifier.fillMaxWidth(),
                kind = if (open) ButtonKind.Quiet else ButtonKind.Secondary,
            )
        }
    }
}

/** Hits of the search without a conversation yet, as on „Neue Nachricht“: a heading and a card. */
@Composable
private fun SearchMore(title: String, content: @Composable () -> Unit) {
    AppSection(title, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) { content() }
}

/** Distance of the floating button to the edge and to the list. */
private val FLOATING_MARGIN = 16.dp

@Composable
private fun ChatListRow(entry: ChatListEntry, now: Long, loading: HistoryLoading, onClick: () -> Unit, onLongClick: () -> Unit, avatar: @Composable () -> Unit) {
    val hasNew = entry.unread > 0
    val preview = entry.preview(loading)
    val time = entry.last?.let { formatChatListTime(it.timestampMillis, now) }
    // For screen readers one sentence instead of individual parts.
    val spoken = buildString {
        append(entry.title)
        if (entry.conversation is Conversation.Circle) append(", ").append(texts.chat.circle)
        when (entry.unread) {
            0 -> Unit
            else -> append(", ").append(texts.chat.newMessages(entry.unread))
        }
        append(". ").append(preview)
        time?.let { append(", ").append(it) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .combinedClickable(onLongClickLabel = texts.chat.chatActions, onLongClick = onLongClick, onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        avatar()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (hasNew) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                time?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (hasNew) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (hasNew) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (hasNew) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (hasNew) UnreadBadge(entry.unread)
            }
        }
    }
}

private val AVATAR_SIZE = 54.dp

/** Row of a public chat: radio icon, name, last message or „Öffentlich · …“, number without notification. */
@Composable
private fun PublicChatRow(publicRadio: PublicRadio, channel: PublicChannel, onClick: () -> Unit) {
    val messages by publicRadio.messages.collectAsState()
    val unread by publicRadio.unread.collectAsState()
    val count = unread[channel] ?: 0
    val last = messages.lastOrNull { it.channel == channel }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = "${channel.title}, ${channel.subtitle}" + if (count > 0) ", ${texts.chat.newMessages(count)}" else "" }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PublicChannelAvatar(AVATAR_SIZE)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                channel.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (count > 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    last?.text ?: channel.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (count > 0) UnreadBadge(count)
            }
        }
    }
}

/** Yellow number as in common messengers. Text dark, yellow only as a surface. */
@Composable
private fun UnreadBadge(count: Int) {
    Box(
        Modifier
            .padding(start = 8.dp)
            .heightIn(min = 28.dp)
            .widthIn(min = 28.dp)
            .background(MaterialTheme.colorScheme.secondary, CircleShape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Last message; without one „Wird geladen …“ while its messages are still coming (ADR 0107 §8). */
private fun ChatListEntry.preview(loading: HistoryLoading): String {
    val message = last ?: return when {
        removed -> texts.chat.noLongerInCircle
        loading.has(conversation) -> texts.chat.loadingMessages
        else -> texts.chat.noMessagesYet
    }
    // A place (ADR 0121 §8): what the card says, not the stand-in text with its link for older apps.
    val shown = message.spot?.let { spot ->
        when (spot.kind) {
            app.anynear.mesh.ChatSpot.MEET -> spot.label?.let { texts.map.spotMeetText(it) } ?: texts.map.spotMeet
            app.anynear.mesh.ChatSpot.ASK -> texts.map.spotAsk
            app.anynear.mesh.ChatSpot.ALL -> texts.map.shareAll
            else -> if (message.outgoing) texts.map.spotHereOwn else texts.location.settingsTitle
        }
    } ?: message.previewText
    return lastSender?.let { "$it: $shown" } ?: shown
}

/** Whether the messages of [conversation] are still coming on this device (ADR 0107 §8). */
private fun HistoryLoading.has(conversation: Conversation): Boolean = when (conversation) {
    is Conversation.Circle -> conversation.circleId in circles
    is Conversation.Direct -> mailbox
}
