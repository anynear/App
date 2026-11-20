package app.anynear.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextOverflow
import app.anynear.mesh.ReplyRef
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import app.anynear.attachments.AttachmentLimits
import app.anynear.attachments.AttachmentState
import app.anynear.attachments.AttachmentKind
import app.anynear.attachments.DraftAttachment
import app.anynear.attachments.VoiceTranscript
import app.anynear.chats.findInviteLinks
import app.anynear.chats.findLinks
import app.anynear.chats.withoutInviteLinks
import app.anynear.i18n.texts
import app.anynear.mesh.Reaction
import app.anynear.mesh.awaitsAttachments
import app.anynear.mesh.ownReaction
import app.anynear.mesh.reactionGroups
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.anynear.app.AboLimit
import app.anynear.app.AboPreview
import app.anynear.app.AboPreviews
import app.anynear.app.AppController
import app.anynear.chats.LinkDraft
import app.anynear.chats.LinkPreviews
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import app.anynear.mesh.LinkState
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.PeerId
import app.anynear.mesh.Transport

@Composable
internal fun ChatScreen(
    controller: AppController,
    conversation: Conversation,
    title: String,
    onBack: (() -> Unit)? = null,
    /** Tapping the title: detail page of the circle or the person. */
    onTitleClick: (() -> Unit)? = null,
    /** Line below the title, e.g. who is in the circle. */
    subtitle: String? = null,
    /** Circle chat: share the invitation link. */
    onShare: (() -> Unit)? = null,
    /** Set when nothing can be written here any more (e.g. removed from the circle). */
    readOnlyReason: String? = null,
    /** Button below [readOnlyReason], e.g. „Zum Formular“ in the direct conversation with support. */
    readOnlyAction: Pair<String, () -> Unit>? = null,
    /** Circle in which there is nobody but us yet: prompt to invite instead of showing an empty chat. */
    alone: Boolean = false,
    /** Picture of the circle or the person in the header. */
    avatar: (@Composable () -> Unit)? = null,
    /** Hint above the input, e.g. that the person can no longer be reached; writing stays possible. */
    topHint: (@Composable () -> Unit)? = null,
) {
    val all by controller.messages.collectAsState()
    val canSend by controller.canSend.collectAsState()
    // For the pictures next to the senders in the circle chat.
    val people by controller.people.collectAsState()
    val circleMembers by controller.members.collectAsState()
    val messages = remember(all, conversation) { all.filter { it.conversation == conversation } }
    val isDirect = conversation is Conversation.Direct
    // Circle of a request to support: below the first own message it says that we will get in touch soon.
    val supportPeer by controller.supportPeer.collectAsState()
    val replySoonAfter = remember(messages, conversation, supportPeer, circleMembers) {
        (conversation as? Conversation.Circle)?.circleId?.takeIf(controller::isSupportCircle)
            ?.let { messages.firstOrNull { it.outgoing && it.system == null }?.localId }
    }
    // As long as the chat is open, new items here count as read and create no notification.
    DisposableEffect(conversation) {
        controller.setOpenConversation(conversation)
        onDispose { controller.setOpenConversation(null) }
    }

    // The draft survives page changes and restarts, encrypted on the device (DraftStore).
    var draft by remember(conversation) { mutableStateOf(controller.draftText(conversation)) }
    var drafts by remember(conversation) { mutableStateOf<List<DraftAttachment>>(emptyList()) }
    // Preview for the first web link in the draft (ADR 0069): loaded shortly after typing, "×" remembers the address.
    var linkDraft by remember(conversation) { mutableStateOf<LinkDraft?>(null) }
    var linkDismissed by remember(conversation) { mutableStateOf<String?>(null) }
    LaunchedEffect(draft) {
        delay(LINK_PREVIEW_DELAY_MILLIS)
        val url = LinkPreviews.firstPreviewable(draft)
        when {
            url == null || url == linkDismissed -> linkDraft = null
            url != linkDraft?.url -> linkDraft = controller.loadLinkPreview(url)
        }
    }
    var draftsLoaded by remember(conversation) { mutableStateOf(false) }
    // The draft came from „Weiterleiten“ (ADR 0118); gone once it is sent or emptied.
    var passedOn by remember(conversation) { mutableStateOf(controller.draftPassedOn(conversation)) }
    LaunchedEffect(conversation, draft, drafts, draftsLoaded) {
        if (draftsLoaded && passedOn && draft.isBlank() && drafts.isEmpty()) passedOn = false
    }
    LaunchedEffect(conversation, passedOn) { controller.saveDraftPassedOn(conversation, passedOn) }
    LaunchedEffect(conversation) {
        // Do not displace attachments already selected if loading takes longer.
        drafts = (controller.draftAttachments(conversation) + drafts).take(AttachmentLimits.MAX_PER_MESSAGE)
        draftsLoaded = true
    }
    LaunchedEffect(conversation, draft) { controller.saveDraftText(conversation, draft) }
    LaunchedEffect(conversation, drafts, draftsLoaded) {
        if (draftsLoaded) controller.saveDraftAttachments(conversation, drafts)
    }
    var preparing by remember(conversation) { mutableStateOf(false) }
    var error by remember(conversation) { mutableStateOf<String?>(null) }
    // Attachment too large or too many: hint about the plan that allows more (ADR 0076 §1a).
    var attachmentLimit by remember(conversation) { mutableStateOf<AboLimit?>(null) }
    val placeRunning = rememberPlaceRunning(controller, conversation)
    var attachMenu by remember { mutableStateOf(false) }
    var meeting by remember { mutableStateOf(false) }
    var sharingPlace by remember { mutableStateOf(false) }
    var sharingAll by remember { mutableStateOf(false) }
    // A check-in for this circle or this person, from the „+“ (ADR 0121 §13, §23).
    val lifeSignTarget = lifeSignTargetOf(controller, conversation)
    var startingLifeSign by remember { mutableStateOf(false) }
    if (startingLifeSign && lifeSignTarget != null) controller.lifeSigns?.let { signs ->
        val circleList by controller.circles.collectAsState()
        val person = lifeSignTarget.takeIf { it.startsWith(app.anynear.lifesigns.LifeSigns.PERSON_PREFIX) }?.removePrefix(app.anynear.lifesigns.LifeSigns.PERSON_PREFIX)
        LifeSignStartDialog(
            signs, circleList, onDismiss = { startingLifeSign = false },
            fixedCircle = lifeSignTarget.takeIf { person == null }, fixedPerson = person, fixedName = signs.circle(lifeSignTarget),
        )
    }
    if (sharingAll && conversation is Conversation.Circle) ShareAllDialog(controller, conversation.circleId, onDismiss = { sharingAll = false })
    if (sharingPlace) ShareMyPlaceDialog(controller, conversation, onDismiss = { sharingPlace = false })
    if (meeting) MeetingPointDialog(controller, conversation, onDismiss = { meeting = false })
    // Shared from another app or forwarded into this chat (also while it is open): into the draft, which then is
    // stored as usual. Only after the stored draft is loaded, so that loading does not displace it.
    val additions by controller.draftAdditions.collectAsState()
    LaunchedEffect(conversation, additions, draftsLoaded) {
        if (!draftsLoaded || additions[conversation].isNullOrEmpty()) return@LaunchedEffect
        controller.takeDraftAdditions(conversation).forEach { addition ->
            if (addition.passedOn) passedOn = true
            if (addition.text.isNotEmpty()) draft = draft.trimEnd().let { if (it.isEmpty()) addition.text else "$it\n${addition.text}" }
            val added = addition.attachments.toMutableList()
            // A voice message goes alone (ADR 0030): not into a draft that already has attachments.
            if (added.any { it.kind == AttachmentKind.AUDIO } && (drafts.isNotEmpty() || added.size > 1)) {
                added.removeAll { it.kind == AttachmentKind.AUDIO }
                error = texts.status.tooManyAttachments(1)
            }
            val room = AttachmentLimits.MAX_PER_MESSAGE - drafts.size
            if (added.size > room) {
                error = texts.chat.maxAttachments(AttachmentLimits.MAX_PER_MESSAGE)
                attachmentLimit = AboLimit.ATTACHMENT_COUNT
            }
            drafts = drafts + added.take(room.coerceAtLeast(0))
        }
    }
    val scope = rememberCoroutineScope()
    // Selected, pasted via „Bild einfügen“ or pasted into the input field: everything is prepared the same way.
    fun addPicked(images: Boolean, files: List<PickedFile>) {
        if (files.isEmpty()) return
        scope.launch {
            preparing = true
            val problems = mutableListOf<String>()
            attachmentLimit = if (files.any { it.tooLarge }) AboLimit.ATTACHMENT_SIZE else null
            val prepared = files.mapNotNull { file ->
                val bytes = file.bytes
                when {
                    file.tooLarge -> null.also { problems += texts.chat.fileTooLarge }
                    bytes == null -> null.also { problems += texts.chat.fileUnreadable }
                    images -> controller.prepareImage(bytes).also { if (it == null) problems += texts.chat.photoUnreadable }
                    else -> controller.prepareFile(bytes, file.name).let { (d, problem) -> problem?.let { problems += it }; d }
                }
            }
            val room = AttachmentLimits.MAX_PER_MESSAGE - drafts.size
            if (prepared.size > room) {
                problems += texts.chat.maxAttachments(AttachmentLimits.MAX_PER_MESSAGE)
                attachmentLimit = attachmentLimit ?: AboLimit.ATTACHMENT_COUNT
            }
            drafts = drafts + prepared.take(room.coerceAtLeast(0))
            error = problems.distinct().firstOrNull()
            preparing = false
        }
    }
    val picker = rememberAttachmentPicker(onPicked = ::addPicked)
    // Voice message (ADR 0030): record, store as a draft, send with the send button.
    val recorder = rememberVoiceRecorder()
    val micPermission = rememberMicPermission()
    var recording by remember(conversation) { mutableStateOf(false) }
    var recordedMillis by remember(conversation) { mutableLongStateOf(0L) }
    var notice by remember(conversation) { mutableStateOf<String?>(null) }
    // Longest recording from the own plan (ADR 0099): until the entitlement service everyone has „Kostenlos“; without
    // the preview the previous 5 minutes. Fixed when the recording starts, the recorder's safety stop depends on it.
    val aboPreview by produceState<AboPreview?>(null) { value = AboPreviews.loadOnce() }
    var recordingMaxMillis by remember(conversation) { mutableLongStateOf(AttachmentLimits.AUDIO_MAX_MILLIS) }
    // Recording ended at the limit: hint about the plan that allows longer voice messages, like the other limits.
    var voiceAtLimit by remember(conversation) { mutableStateOf(false) }
    // After „Senden“ in the recording bar: send once the recording is stored as a draft and recognition is running.
    var sendWhenReady by remember(conversation) { mutableStateOf(false) }
    fun finishRecording(limitReached: Boolean = false, send: Boolean = true) {
        if (!recording) return
        recording = false
        val voice = recorder.stop()
        if (voice == null) {
            error = texts.chat.recordingFailed
            return
        }
        drafts = listOf(DraftAttachment(AttachmentKind.AUDIO, voice.bytes, durationMillis = voice.durationMillis))
        // As in common messengers: record, „Senden“, done. The text preview is created on the side (ADR 0030).
        sendWhenReady = send
        if (limitReached) {
            notice = texts.chat.voiceLimitReached((recordingMaxMillis / 60_000).toInt())
            voiceAtLimit = true
        }
    }
    // With the screen off Android hands the app silence instead of the microphone; keep it on while recording.
    if (recording) KeepScreenOn()
    // App closed or screen switched off: end the recording instead of recording silence; it waits as a draft.
    val visible by controller.visible.collectAsState()
    LaunchedEffect(visible) {
        if (!visible && recording) {
            finishRecording(send = false)
            if (drafts.any { it.kind == AttachmentKind.AUDIO }) notice = texts.chat.voiceStoppedInBackground
        }
    }
    LaunchedEffect(recording) {
        while (recording) {
            recordedMillis = recorder.elapsedMillis()
            if (recordedMillis >= recordingMaxMillis) finishRecording(limitReached = true)
            delay(200)
        }
    }
    // Whoever leaves the conversation during the recording finds it again as a draft.
    DisposableEffect(conversation) {
        onDispose {
            if (recording) {
                recording = false
                recorder.stop()?.let { v ->
                    controller.saveDraftAttachments(conversation, listOf(DraftAttachment(AttachmentKind.AUDIO, v.bytes, durationMillis = v.durationMillis)))
                }
            }
        }
    }
    val playback = rememberVoicePlayback()
    val voiceDraft = drafts.firstOrNull { it.kind == AttachmentKind.AUDIO }
    val hasVoice = voiceDraft != null
    // Local recognition as soon as a recording is stored as a draft (also after a restart); sending waits
    // for it at most. Without recognition the recording goes without a text preview (ADR 0030).
    var recognition by remember(conversation) { mutableStateOf<Deferred<VoiceTranscript?>?>(null) }
    var transcript by remember(conversation) { mutableStateOf<VoiceTranscript?>(null) }
    var recognizing by remember(conversation) { mutableStateOf(false) }
    LaunchedEffect(voiceDraft) {
        transcript = null
        recognition = null
        recognizing = false
        val voice = voiceDraft ?: return@LaunchedEffect
        recognizing = true
        val job = async { runCatching { transcribeVoice(voice.bytes, voice.durationMillis) { controller.debugLog.add("Sprache", it) } }.getOrNull() }
        recognition = job
        transcript = job.await()
        recognizing = false
        // Only whether and how much was recognised, never the text (log without message texts).
        transcript?.let { controller.debugLog.add("Sprache", "erkannt: ${it.text.length} Zeichen, vollständig=${it.complete}") }
    }

    val listState = rememberLazyListState()
    StickToEnd(listState, conversation, messages.lastOrNull { it.outgoing }?.localId)
    // Search in the conversation: list of hits instead of history; a hit jumps to its place and lights up briefly.
    var searching by remember(conversation) { mutableStateOf(false) }
    var query by remember(conversation) { mutableStateOf("") }
    var highlighted by remember(conversation) { mutableStateOf<String?>(null) }
    // Only one message shows details or selection; opening another one closes the previous one.
    var openMessage by remember(conversation) { mutableStateOf<String?>(null) }
    // Message the next one answers (ADR 0092); the bar above the input shows it until sending or ✕.
    var replyTo by remember(conversation) { mutableStateOf<ChatMessage?>(null) }
    LaunchedEffect(highlighted) {
        val index = messages.indexOfFirst { it.localId == highlighted }
        if (index < 0) return@LaunchedEffect
        listState.scrollToItem(index)
        delay(HIGHLIGHT_MILLIS)
        highlighted = null
    }
    SystemBackHandler(enabled = searching) { searching = false }

    CompositionLocalProvider(LocalVoicePlayback provides playback) {
        Column(Modifier.fillMaxSize()) {
            AppHeader(
                title = title,
                subtitle = subtitle ?: if (isDirect) texts.chat.directMessage else null,
                avatar = avatar,
                leading = onBack?.let { back ->
                    {
                        HeaderBackButton(back)
                    }
                },
                onTitleClick = onTitleClick,
                titleClickLabel = if (isDirect) texts.chat.viewContact else texts.chat.viewCircle,
                // Side by side (without Back), too, no logo: the name of the conversation needs the space.
                main = false,
                trailing = {
                    Row {
                        if (!searching) HeaderAction(AppIcons.Suche, texts.chat.searchInChat) { searching = true }
                        if (placeRunning) HeaderAction(AppIcons.Karte, texts.map.runningHere) { controller.showMapFor(conversation) }
                        onShare?.let { HeaderAction(AppIcons.Teilen, texts.chat.inviteToThisCircle, it) }
                    }
                },
            )

            if (searching) {
                ChatSearch(
                    controller,
                    messages,
                    query,
                    onQueryChange = { query = it },
                    onClose = { searching = false },
                    onOpen = { message ->
                        searching = false
                        highlighted = message.localId
                    },
                    modifier = Modifier.weight(1f),
                )
                return@Column
            }

            // As long as messages of this chat are still coming (new in the circle, new device, back from the emergency
            // card), a calm sentence at the top (ADR 0014, 0107 §8).
            val circleList by controller.circles.collectAsState()
            val loading by controller.historyLoading.collectAsState()
            var historyHint by remember(conversation) { mutableStateOf<String?>(null) }
            LaunchedEffect(conversation, circleList, loading) {
                while (true) {
                    historyHint = controller.historyHint(conversation)
                    if (historyHint == null) break
                    delay(30_000)
                }
            }
            historyHint?.let { HistoryStrip(it) }

            // Radio without anynear (e.g. the Meshtastic app): looks like a direct message, but goes only over radio.
            val radioOnly = remember(conversation, people) { (conversation as? Conversation.Direct)?.peer?.let(controller::isRadioOnly) == true }
            if (radioOnly) HistoryStrip(texts.chat.radioOnlyConversation)

            // If there is already a draft (e.g. shared from another app), the input field stays visible.
            val hasDraft = draft.isNotEmpty() || drafts.isNotEmpty()
            if (alone && !hasDraft && historyHint == null && messages.all { it.system != null } && onShare != null) {
                InviteFirst(onShare, Modifier.weight(1f))
                return@Column
            }

            // Check-ins of this circle as cards between the messages, by the time they started (ADR 0121 §22).
            val lifeSignCards = rememberLifeSignCards(controller, conversation, messages)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // No message yet: a calm sentence instead of an empty surface.
                if (messages.none { it.system == null } && lifeSignCards.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            if (isDirect) texts.chat.firstMessageTo(title) else texts.chat.circleReadsAlong(title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                        )
                    }
                }
                items(messages, key = { it.localId }) { message ->
                    // Inside the item of the message that follows, so that the positions of the messages stay as they are.
                    lifeSignCards[message.localId]?.forEach { LifeSignCard(controller, it, Modifier.padding(bottom = 6.dp)) }
                    val note = message.system
                    if (note != null) {
                        // Invite again, as long as the removed person is not already back in the circle.
                        val circleId = (conversation as? Conversation.Circle)?.circleId
                        val device = note.removedDevice
                        val canReinvite = circleId != null && device != null && readOnlyReason == null &&
                            circleMembers.none { it.circleId == circleId && it.deviceId == device }
                        SystemLine(message.text, onReinvite = if (canReinvite && circleId != null) ({ error = controller.reinvite(circleId, note) }) else null)
                        return@items
                    }
                    val sender = if (message.outgoing || isDirect) null else message.fromPeer ?: PeerId.node(message.fromNodeNum)
                    val answered = message.replyTo?.let { ref -> answeredMessage(messages, ref) }
                    val reply = controller.replyRefOf(message)?.takeIf { !message.deleted }?.let { { replyTo = message } }
                    // Pull to the right to answer, like the „Antworten“ in the details.
                    SwipeToReply(reply) {
                        MessageBubble(
                            controller = controller,
                            message = message,
                            quote = message.replyTo?.let { quoteOf(controller, answered) },
                            onQuoteClick = {
                                // Jump to the answered message and let it light up briefly; gone: say so (ADR 0092).
                                if (answered != null) highlighted = answered.localId
                                else controller.showNotice(texts.chat.answeredMessageGone)
                            },
                            onReply = reply,
                            senderName = if (message.outgoing) null else controller.senderLabel(message),
                            isDirect = isDirect,
                            highlighted = message.localId == highlighted,
                            open = message.localId == openMessage,
                            onOpen = { opened -> openMessage = if (opened) message.localId else openMessage.takeIf { it != message.localId } },
                            onError = { error = it },
                            avatar = sender?.let { peer ->
                                { PersonAvatar(controller, peer, people.firstOrNull { it.peer == peer }?.image, 32.dp, enlargeable = true) }
                            },
                        )
                    }
                    if (message.localId == replySoonAfter) SystemLine(texts.support.replySoon, onReinvite = null)
                }
                // Check-ins started after the last message.
                lifeSignCards[""]?.let { tail -> items(tail, key = { "alive:" + it.plan.id }) { LifeSignCard(controller, it) } }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            topHint?.invoke()
            if (readOnlyReason != null) {
                Text(readOnlyReason, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                readOnlyAction?.let { (label, action) ->
                    AppButton(label, action, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), kind = ButtonKind.Secondary)
                }
                return@Column
            }
            if (voiceDraft != null) {
                // Only left over if sending did not work or the conversation was left during the recording:
                // play, discard or send. The text preview runs invisibly alongside.
                DraftVoice(
                    voiceDraft,
                    recognizing = false,
                    preview = null,
                    hint = null,
                    onRemove = { drafts = emptyList() },
                    onError = { error = it },
                )
            }
            else if (drafts.isNotEmpty()) DraftStrip(drafts) { removed -> drafts = drafts - removed }
            linkDraft?.takeIf { voiceDraft == null }?.let { l ->
                DraftLinkPreview(l) {
                    linkDismissed = l.url
                    linkDraft = null
                }
            }
            if (passedOn) PassedOnLabel(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
            replyTo?.let { answered ->
                ReplyBar(
                    title = if (answered.outgoing) texts.chat.replyToYou else texts.chat.replyTo(controller.senderLabel(answered)),
                    snippet = replySnippet(answered),
                    onCancel = { replyTo = null },
                )
            }
            val kinds = drafts.map { d -> d.kind }
            val textLimit = TextLimit({ controller.textLeft(conversation, it, kinds) }, textHintBelow(controller.textLeft(conversation, "", kinds)))
            val link by controller.linkState.collectAsState()
            // Too long for radio: the rest is marked in the field and only goes over the internet (like attachments).
            val radioKept = if (link == LinkState.Connected) controller.radioTextKept(conversation, draft, drafts.map { it.kind }) else null
            // Hint only shortly before the limit. Too much is only possible if it becomes tighter after writing
            // (e.g. an attachment to a radio without anynear), it cannot be typed in.
            if (radioKept != null && !preparing) Hint(texts.chat.radioShortened)
            notice?.let { Hint(it) }
            if (preparing) Hint(texts.chat.preparing)
            // Right-aligned with the input field, i.e. to the left of the send button (12 + 8 + 52).
            else textLimit.hint(draft)?.let { TextLimitHint(it, Modifier.padding(start = 16.dp, end = 72.dp, top = 2.dp, bottom = 2.dp)) }
            AboLimitHint(
                when {
                    voiceAtLimit && notice != null -> AboLimit.VOICE_LENGTH
                    else -> attachmentLimit.takeIf { error != null }
                },
            )
            if (recording) {
                VoiceRecordingBar(
                    recordedMillis,
                    onDiscard = {
                        recording = false
                        recorder.cancel()
                    },
                    onDone = { finishRecording() },
                )
                return@Column
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
                Box {
                    IconButton(
                        onClick = { attachMenu = true },
                        enabled = canSend && !hasVoice && drafts.size < AttachmentLimits.MAX_PER_MESSAGE && !preparing,
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) { Icon(AppIcons.Anhang, contentDescription = texts.chat.attachPhotoOrFile) }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(texts.chat.photo) },
                            leadingIcon = { Icon(AppIcons.Bild, contentDescription = null) },
                            onClick = {
                                attachMenu = false
                                picker.pickImages(AttachmentLimits.MAX_PER_MESSAGE - drafts.size)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(texts.chat.file) },
                            leadingIcon = { Icon(AppIcons.Dokument, contentDescription = null) },
                            onClick = {
                                attachMenu = false
                                picker.pickFiles()
                            },
                        )
                        // A place for everyone in the conversation, and the question where the others are (ADR 0121 §8).
                        DropdownMenuItem(
                            text = { Text(texts.map.shareMine) },
                            leadingIcon = { Icon(AppIcons.Karte, contentDescription = null) },
                            onClick = {
                                attachMenu = false
                                sharingPlace = true
                            },
                        )
                        if (conversation is Conversation.Circle) DropdownMenuItem(
                            text = { Text(texts.map.shareAll) },
                            leadingIcon = { Icon(AppIcons.Kreis, contentDescription = null) },
                            onClick = {
                                attachMenu = false
                                sharingAll = true
                            },
                        )
                        // Not in a support circle and not with a radio without anynear.
                        if (lifeSignTarget != null && controller.lifeSigns != null) DropdownMenuItem(
                            text = { Text(texts.lifeSigns.startInChat) },
                            leadingIcon = { Icon(AppIcons.Haken, contentDescription = null) },
                            onClick = {
                                attachMenu = false
                                startingLifeSign = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(texts.map.spotMeetHere) },
                            leadingIcon = { Icon(AppIcons.Karte, contentDescription = null) },
                            onClick = {
                                attachMenu = false
                                meeting = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(texts.map.spotAsk) },
                            leadingIcon = { Icon(AppIcons.Person, contentDescription = null) },
                            onClick = {
                                attachMenu = false
                                controller.askWhere(conversation)
                            },
                        )
                        // Only if there is a picture in the clipboard right now; it is only read on tapping.
                        if (remember(attachMenu) { attachMenu && clipboardHasImage() }) {
                            DropdownMenuItem(
                                text = { Text(texts.chat.pasteImage) },
                                leadingIcon = { Icon(AppIcons.Kopieren, contentDescription = null) },
                                onClick = {
                                    attachMenu = false
                                    scope.launch { addPicked(true, clipboardImages()) }
                                },
                            )
                        }
                    }
                }
                // Camera right next to the attachment (as in common messengers): take a photo, it lands as a draft
                // in the chat.
                IconButton(
                    onClick = { picker.takePhoto() },
                    enabled = canSend && !hasVoice && drafts.size < AttachmentLimits.MAX_PER_MESSAGE && !preparing,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) { Icon(AppIcons.Kamera, contentDescription = texts.chat.takePhoto) }
                val voiceButton = draft.isBlank() && drafts.isEmpty()
                val sendDraft: () -> Unit = {
                    // If recognition is still running, wait for it briefly; afterwards everything goes out in one go.
                    val pending = recognition?.takeIf { voiceDraft != null }
                    scope.launch {
                        preparing = pending?.isActive == true
                        val heard = pending?.let { runCatching { it.await() }.getOrNull() }
                        preparing = false
                        // A voice message has no text of its own, only the automatic text preview.
                        val link = linkDraft?.takeIf { voiceDraft == null && draft.contains(it.url) }
                        val reply = replyTo?.let(controller::replyRefOf)
                        when (val result = controller.send(conversation, if (voiceDraft != null) "" else draft, drafts, heard, link, reply, passedOn)) {
                            AppController.SendResult.Sent -> {
                                replyTo = null
                                passedOn = false
                                linkDraft = null
                                linkDismissed = null
                                draft = ""
                                drafts = emptyList()
                                notice = null
                                playback.stop()
                            }
                            is AppController.SendResult.Rejected -> error = result.reason
                        }
                    }
                }
                LaunchedEffect(sendWhenReady, recognition) {
                    if (sendWhenReady && voiceDraft != null && recognition != null) {
                        sendWhenReady = false
                        sendDraft()
                    }
                }
                // With a voice message no input field: it goes without its own text (ADR 0030, text preview automatic).
                if (hasVoice) Spacer(Modifier.weight(1f))
                // Multi-line: the enter key makes a line break, sending is done with the button. On the Mac
                // Enter sends as in other Mac programs, Shift+Enter makes the line break.
                if (!hasVoice) OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = textLimit.accept(draft, it)
                        error = null
                    },
                    modifier = Modifier.weight(1f).receiveImages { addPicked(true, it) }.onPreviewKeyEvent { e ->
                        val send = enterSends && e.key == Key.Enter && !e.isShiftPressed && !voiceButton && canSend && !preparing
                        if (send && e.type == KeyEventType.KeyDown) sendDraft()
                        send
                    },
                    // Round as in the design; with several lines the corners stay soft.
                    shape = RoundedCornerShape(26.dp),
                    placeholder = { Text(if (drafts.isEmpty()) texts.chat.writeMessage else texts.chat.captionOptional) },
                    enabled = canSend,
                    minLines = 1,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    visualTransformation = radioKept?.let {
                        RadioOverflow(draft.length - draft.trimStart().length + it, SpanStyle(color = MaterialTheme.colorScheme.onSecondaryContainer, background = MaterialTheme.colorScheme.secondaryContainer))
                    } ?: VisualTransformation.None,
                )
                // Sending is the one main action of this screen, hence signal yellow. Without text and
                // attachments the microphone is there: tap, speak, „Fertig“ (ADR 0030).
                FilledIconButton(
                    onClick = {
                        if (voiceButton) {
                            error = null
                            notice = null
                            voiceAtLimit = false
                            playback.stop()
                            recordingMaxMillis = AttachmentLimits.audioMaxMillis(aboPreview?.free?.limits?.voiceMinutes)
                            micPermission.request { granted ->
                                when {
                                    !granted -> error = texts.chat.micDenied
                                    !recorder.start(recordingMaxMillis + RECORDING_SAFETY_MILLIS) -> error = texts.chat.recordingFailed
                                    else -> {
                                        recordedMillis = 0
                                        recording = true
                                    }
                                }
                            }
                            return@FilledIconButton
                        }
                        sendDraft()
                    },
                    enabled = canSend && !preparing,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp).size(52.dp),
                ) {
                    if (voiceButton) Icon(AppIcons.Mikrofon, contentDescription = texts.chat.recordVoice)
                    else Icon(AppIcons.Senden, contentDescription = texts.chat.send)
                }
            }
        }
    }
}

/** Strip „Frühere Nachrichten werden geladen …“ as long as the history is missing (ADR 0014, 0107 §8). */
@Composable
internal fun HistoryStrip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Hint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
}

/** Line about a change in the circle (ADR 0012), centred and quiet; possibly with „Wieder einladen“. */
@Composable
private fun SystemLine(text: String, onReinvite: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onReinvite != null) AppButton(texts.chat.reinvite, onReinvite, kind = ButtonKind.Quiet, icon = AppIcons.Teilen)
    }
}

/** Marks everything from [from] on in the input field: that no longer goes over radio. */
private data class RadioOverflow(val from: Int, val style: SpanStyle) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val marked = AnnotatedString.Builder(text).apply { if (from < text.length) addStyle(style, from, text.length) }.toAnnotatedString()
        return TransformedText(marked, OffsetMapping.Identity)
    }
}

/** This long after the last keystroke the preview of a link is loaded. */
private const val LINK_PREVIEW_DELAY_MILLIS = 700L

/** The system only stops this much after the maximum duration; before that the interface ends the recording. */
private const val RECORDING_SAFETY_MILLIS = 5_000L

/**
 * From this few free characters on the input field shows how much is still possible: with the long limit over the
 * internet a little earlier than with the short one over radio, where 100 would be most of the message.
 */
internal fun textHintBelow(capacity: Int): Int = if (capacity > LONG_TEXT) 100 else 30

/** Above this many characters a limit counts as long ([textHintBelow]). */
private const val LONG_TEXT = 1000

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageBubble(
    controller: AppController,
    message: ChatMessage,
    senderName: String?,
    isDirect: Boolean,
    onError: (String) -> Unit,
    /** Picture of the sender in the circle chat, to the left of the speech bubble. */
    avatar: (@Composable () -> Unit)? = null,
    /** Jumped to from the search: briefly highlighted so that one finds the message. */
    highlighted: Boolean = false,
    /** This message is the one open message in the chat (details or selection). */
    open: Boolean = false,
    /** Reports opening (true) and closing (false), so that the chat closes other messages. */
    onOpen: (Boolean) -> Unit = {},
    /** Quote of the answered message (ADR 0092), `null` if this is no answer. */
    quote: Quote? = null,
    onQuoteClick: () -> Unit = {},
    /** Answer this message; `null` where answering is not possible (no ANMP ID). */
    onReply: (() -> Unit)? = null,
) {
    val outgoing = message.outgoing
    if (message.deleted) {
        DeletedLine(if (outgoing) texts.chat.deletedByYou else texts.chat.deletedBy(senderName ?: controller.senderLabel(message)), message.timestampMillis, outgoing)
        return
    }
    val colors = AppTheme.colors
    // Details with reactions and deleting: via the small "i" next to the speech bubble or a long press.
    // Both toggle, a second time closes again.
    var expandedState by rememberSaveable(message.localId) { mutableStateOf(false) }
    // Open only as long as the chat holds this message as the open one.
    val expanded = expandedState && open
    fun show(details: Boolean) {
        expandedState = details
        onOpen(details)
    }
    var deleting by remember(message.localId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var fileAction by remember(message.localId) { mutableStateOf<FileAction?>(null) }
    FileActionDialog(controller, fileAction, onDismiss = { fileAction = null }) { text, ok ->
        show(false)
        if (ok) controller.showNotice(text) else onError(text)
    }
    @Suppress("DEPRECATION") // LocalClipboard needs platform-specific ClipEntry objects.
    val clipboard = LocalClipboardManager.current
    // Invitations as a card instead of as a link; the stored text keeps the link.
    val invites = remember(message.text) { findInviteLinks(message.text) }
    // With an introduction (ADR 0116) the card says it all; the text is only the stand-in for older apps.
    // The same for a place (ADR 0121 §8): the card shows it.
    val visibleText = remember(message.text, invites, message.intro, message.spot) { if (message.intro != null || message.spot != null) "" else withoutInviteLinks(message.text, invites) }
    // Speech bubble with a "tip" towards the sender's side (design: 18 dp, tip 6 dp).
    val shape = if (outgoing) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    // Not sent and out by no path: light background with a red border so that it stands out.
    val failed = outgoing && message.status == MessageStatus.FAILED && message.via.isEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (highlighted) Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)).padding(4.dp) else Modifier),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        avatar?.invoke()
        Column(horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (outgoing) DetailsButton(expanded) { show(!expanded) }
                Column(
                    Modifier
                        .widthIn(max = 296.dp)
                        .background(if (outgoing && !failed) colors.outgoingBubble else colors.incomingBubble, shape)
                        .then(
                            when {
                                failed -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.error, shape)
                                outgoing -> Modifier
                                else -> Modifier.border(1.dp, colors.bubbleOutline, shape)
                            },
                        )
                        // Long press like the "i" opens the details. While they are open, a long press marks
                        // text instead; the "i" closes them again.
                        .then(
                            if (expanded) Modifier
                            else Modifier.combinedClickable(
                                onLongClickLabel = texts.chat.showDetailsAndReactions,
                                onLongClick = { show(true) },
                            ) {},
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (senderName != null) {
                        Text(senderName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (message.passedOn) {
                        PassedOnLabel(color = if (outgoing && !failed) colors.onOutgoingBubble.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    quote?.let {
                        // In the own dark bubble the signal colour carries the accent, in others the primary colour.
                        val own = outgoing && !failed
                        QuoteBox(
                            it,
                            if (own) colors.onOutgoingBubble else MaterialTheme.colorScheme.onSurface,
                            if (own) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            onQuoteClick,
                        )
                    }
                    if (message.attachments.isNotEmpty()) MessageAttachments(controller, message.attachments, onError)
                    message.spot?.let { SpotCard(controller, message, it, senderName, if (outgoing && !failed) colors.onOutgoingBubble else MaterialTheme.colorScheme.onSurface, onError) }
                    val textColor = if (outgoing && !failed) colors.onOutgoingBubble else MaterialTheme.colorScheme.onSurface
                    if (visibleText.isNotEmpty()) {
                        if (message.attachments.any { it.transcribed }) TextPreviewLabel(visibleText, textColor.copy(alpha = 0.75f))
                        val shown = @Composable { Text(linkified(visibleText, textColor), style = MaterialTheme.typography.bodyLarge, color = textColor) }
                        // With the details open the text can be marked in part (press and hold, drag the handles);
                        // closed, a long press opens the details as before.
                        if (expanded) {
                            // In the own dark bubble the signal colour marks, as for the quote accent.
                            val mark = if (outgoing && !failed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(mark, mark.copy(alpha = 0.4f))) {
                                SelectionContainer { shown() }
                            }
                        } else {
                            shown()
                        }
                    }
                    if (invites.isNotEmpty()) {
                        val sender = senderName ?: controller.senderLabel(message)
                        invites.forEach { InviteCardBox(controller, it, sender, own = outgoing) }
                    }
                    // Introduced contact (ADR 0116): a card with „Hinzufügen“ instead of the stand-in text alone.
                    message.intro?.let { IntroCardBox(controller, it, senderName ?: controller.senderLabel(message), own = outgoing) }
                    // Preview from the sender (ADR 0069), only for a link that is in the text.
                    message.link?.takeIf { message.text.contains(it.url) }?.let { LinkPreviewCard(controller, it, textColor) }
                    if (message.awaitsAttachments) {
                        Text(
                            texts.chat.arrivesWhenOnline,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    MessageFooter(message, isDirect, expanded, controller::peerName, if (outgoing && !failed) colors.onOutgoingBubble.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!outgoing) DetailsButton(expanded) { show(!expanded) }
            }
            val react = { emoji: String ->
                controller.react(message, emoji)
                show(false)
            }
            // One does not react to own messages: no selection, other people's reactions cannot be adopted.
            Reactions(message.reactionGroups, controller::peerName, if (outgoing) null else react)
            if (expanded) {
                // Who reacted how, only in the details (ADR 0018).
                message.reactionDetails(controller::peerName).forEach { line ->
                    Text(line, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
            }
            // Reacting and deleting (ADR 0068) in the details.
            if (expanded && !outgoing) ReactionPicker(message.ownReaction, react)
            if (expanded) {
                // Files of the message: download or into a storage, as in the full view (with a confirmation prompt).
                val files = message.attachments.filter { it.local }
                // Actions as plain icons without labels: no frame, flush with the edge of the speech bubble.
                FlowRow(
                    modifier = Modifier.widthIn(max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, if (outgoing) Alignment.End else Alignment.Start),
                ) {
                    onReply?.let { reply ->
                        MessageAction(texts.chat.reply, AppIcons.Antworten) {
                            show(false)
                            reply()
                        }
                    }
                    // The whole text at once; parts can be marked in the open bubble.
                    if (visibleText.isNotEmpty()) {
                        MessageAction(texts.common.copy, AppIcons.Kopieren) {
                            clipboard.setText(AnnotatedString(visibleText))
                            show(false)
                            controller.showNotice(texts.chat.textCopied)
                        }
                    }
                    // Forward and share take the whole message: only once every attachment is on the phone.
                    if (files.size == message.attachments.size) {
                        MessageAction(texts.chat.forward, AppIcons.Weiterleiten) {
                            scope.launch {
                                val problem = controller.forward(message)
                                if (problem == null) show(false) else onError(problem)
                            }
                        }
                        // Into the system's share menu only via this explicit button (files are otherwise viewed in the app).
                        MessageAction(texts.chat.shareMessage, AppIcons.Teilen) {
                            scope.launch { shareMessage(controller, message)?.let(onError) }
                        }
                    }
                    if (files.isNotEmpty()) {
                        MessageAction(texts.chat.download, AppIcons.Speichern) { fileAction = FileAction.Download(files) }
                        MessageAction(texts.chat.toFiles, AppIcons.Schloss) { fileAction = FileAction.ToFiles(files) }
                    }
                    MessageAction(texts.chat.delete, AppIcons.Papierkorb, MaterialTheme.colorScheme.error) {
                        show(false)
                        deleting = true
                    }
                }
            }
        }
    }
    if (deleting) {
        val forEveryone = controller.canDeleteForEveryone(message)
        AppDialog(
            onDismissRequest = { deleting = false },
            title = { Text(texts.chat.delete) },
            text = { Text(texts.chat.deleteHint) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (forEveryone) {
                        AppButton(texts.chat.deleteForEveryone, {
                            deleting = false
                            controller.deleteForEveryone(message)
                        }, kind = ButtonKind.Danger)
                    }
                    AppButton(texts.chat.deleteForMe, {
                        deleting = false
                        controller.deleteForMe(message)
                    }, kind = if (forEveryone) ButtonKind.Quiet else ButtonKind.Danger)
                    AppButton(texts.common.cancel, { deleting = false }, kind = ButtonKind.Quiet)
                }
            },
        )
    }
}

/** „Weitergeleitet“ with an arrow (ADR 0118): above the content of a passed on message and above the input of such a draft. */
@Composable
private fun PassedOnLabel(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.Weiterleiten, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(texts.chat.passedOn, style = MaterialTheme.typography.labelMedium, color = color, fontStyle = FontStyle.Italic)
    }
}

/** Action below an opened message: an icon without a frame or label, 48 dp to hit. */
@Composable
private fun MessageAction(text: String, icon: ImageVector, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    // Icon only; the text is what screen readers say. 48 dp: minimum size for touch targets.
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = text, tint = color, modifier = Modifier.size(22.dp))
    }
}

/** Quote above an answer (ADR 0092): who wrote the answered message and its beginning. */
internal data class Quote(val name: String?, val snippet: String)

/** The answered message among [messages] of the conversation, by its ANMP ID (own: local ID, others: remote ID). */
internal fun answeredMessage(messages: List<ChatMessage>, ref: ReplyRef): ChatMessage? =
    messages.lastOrNull { m -> m.system == null && (if (m.outgoing) m.localId else m.remoteId) == ref.mid }

private fun quoteOf(controller: AppController, answered: ChatMessage?): Quote =
    if (answered == null) Quote(null, texts.chat.replyUnavailable)
    else Quote(if (answered.outgoing) texts.chat.you else controller.senderLabel(answered), replySnippet(answered))

/** One line for a quote or the answer bar: the text, otherwise what the message carries. */
internal fun replySnippet(m: ChatMessage): String = when {
    m.deleted -> texts.chat.deletedMessage
    m.text.isNotBlank() -> m.text.trim().lineSequence().first().take(200)
    m.attachments.any { it.kind == AttachmentKind.AUDIO } -> texts.chat.voiceMessage
    m.attachments.any { it.kind == AttachmentKind.IMAGE } -> texts.chat.photo
    else -> texts.chat.file
}

/** Name of a draft attachment for a preview line: the file name, otherwise „Foto“, „Sprachnachricht“ or „Datei“. */
internal fun draftLabel(d: DraftAttachment): String = d.name.ifEmpty {
    when (d.kind) {
        AttachmentKind.AUDIO -> texts.chat.voiceMessage
        AttachmentKind.IMAGE -> texts.chat.photo
        else -> texts.chat.file
    }
}

@Composable
private fun QuoteBox(quote: Quote, color: Color, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(bottom = 6.dp)
            .widthIn(min = 120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .clickable(onClickLabel = texts.chat.showAnsweredMessage, onClick = onClick)
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            quote.name?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(
                quote.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = color.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontStyle = if (quote.name == null) androidx.compose.ui.text.font.FontStyle.Italic else null,
            )
        }
    }
}

/** Above the input while answering (ADR 0092): to whom, the beginning of the message, ✕ to cancel. */
@Composable
private fun ReplyBar(title: String, snippet: String, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp).height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(snippet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCancel) { Icon(AppIcons.Schliessen, contentDescription = texts.chat.cancelReply) }
    }
}

/** Instead of a deleted message (ADR 0068): a calm line with the time, without menu and reactions. */
@Composable
private fun DeletedLine(text: String, timestampMillis: Long, outgoing: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Text(
            "$text · ${formatTime(timestampMillis)}",
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .border(1.dp, AppTheme.colors.bubbleOutline, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** Small "i" next to the speech bubble: shows or hides the details of the message. */
@Composable
internal fun DetailsButton(expanded: Boolean, onClick: () -> Unit) {
    // 48 dp: minimum size for touch targets; expanded with a calm surface behind it.
    Box(
        Modifier
            .size(48.dp)
            .background(if (expanded) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, CircleShape)
            .clickable(onClickLabel = if (expanded) texts.chat.hideDetails else texts.chat.showDetailsAndReactions, onClick = onClick)
            .semantics { contentDescription = texts.chat.details },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AppIcons.Info,
            contentDescription = null,
            tint = if (expanded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Reactions below the speech bubble (ADR 0018): only the emoji, without a frame, with a count for several
 * people; tapping someone else's adopts it.
 */
@Composable
internal fun Reactions(groups: List<Pair<String, List<String>>>, nameOf: (String) -> String, onReact: ((String) -> Unit)?) {
    if (groups.isEmpty()) return
    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        groups.forEach { (emoji, peers) ->
            val mine = Reaction.SELF in peers
            val names = peers.map { if (it == Reaction.SELF) texts.chat.reactionYou else nameOf(it) }
            Text(
                if (peers.size > 1) "$emoji ${peers.size}" else emoji,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .then(if (mine || onReact == null) Modifier else Modifier.clickable(onClickLabel = texts.chat.reactTheSame) { onReact(emoji) })
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .semantics { contentDescription = texts.chat.reactedBy(emoji, names) },
            )
        }
    }
}

/** Selection of the reactions below a tapped message; one's own is highlighted. */
@Composable
internal fun ReactionPicker(own: String?, onReact: (String) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.padding(top = 4.dp).background(MaterialTheme.colorScheme.surfaceVariant, shape).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Reaction.CHOICES.forEach { emoji ->
            val selected = emoji == own
            Box(
                Modifier
                    .size(48.dp)
                    .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape) else Modifier)
                    // Tapping one's own reaction once more takes it back (ADR 0021).
                    .clickable(onClickLabel = if (selected) texts.chat.removeReaction else texts.chat.reactWith(emoji)) { onReact(if (selected) "" else emoji) }
                    .semantics { contentDescription = if (selected) texts.chat.yourReaction(emoji) else emoji },
                contentAlignment = Alignment.Center,
            ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
        }
    }
}

/**
 * Footer of a message: time and state. Outgoing one check mark (sent) or two check marks (arrived on the
 * device; yellow: read, ADR 0019). The path (internet, radio) is, as in the design, only shown in the
 * details behind the "i": sent, arrived, read, each with date, time and path.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ColumnScope.MessageFooter(message: ChatMessage, isDirect: Boolean, expanded: Boolean, nameOf: (String) -> String, tint: Color) {
    val status = message.status
    val lines = message.deliveryLines(isDirect, ::formatDateTime, nameOf)
    val side = if (message.outgoing) Alignment.End else Alignment.Start
    // If there is not enough room, the state slides as a whole below the time; "…" never wraps alone.
    FlowRow(
        Modifier.align(side).semantics { contentDescription = lines.joinToString("\n") { it.spoken } },
        horizontalArrangement = Arrangement.spacedBy(3.dp, side),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatTime(message.timestampMillis), style = MaterialTheme.typography.labelSmall, color = tint, modifier = Modifier.padding(end = 2.dp))
        if (message.outgoing && (status == MessageStatus.QUEUED || status == MessageStatus.FAILED) && message.via.isEmpty()) {
            val failed = status == MessageStatus.FAILED
            val color = if (failed) MaterialTheme.colorScheme.error else tint
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(if (failed) AppIcons.Achtung else AppIcons.Uhr, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(status.label(isDirect).replace(" …", "\u00A0…"), style = MaterialTheme.typography.labelMedium, color = color)
            }
            return@FlowRow
        }
        if (message.outgoing) {
            Icon(
                if (status?.arrived == true) AppIcons.DoppelHaken else AppIcons.Haken,
                contentDescription = null,
                tint = if (status == MessageStatus.READ) AppTheme.colors.readTick else tint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    if (expanded && lines.isNotEmpty()) {
        Column(Modifier.align(side).padding(top = 4.dp).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            lines.forEach { DeliveryRow(it, tint) }
        }
    }
}

/** One line of the details: check marks as in the footer (sent, arrived, read yellow), text, path. */
@Composable
private fun DeliveryRow(line: DeliveryLine, tint: Color) {
    Row(
        Modifier.padding(start = if (line.indent) 20.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (line.mark) {
            DeliveryMark.SENT -> Icon(AppIcons.Haken, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            DeliveryMark.ARRIVED -> Icon(AppIcons.DoppelHaken, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            DeliveryMark.READ -> Icon(AppIcons.DoppelHaken, contentDescription = null, tint = AppTheme.colors.readTick, modifier = Modifier.size(14.dp))
            DeliveryMark.NONE -> Unit
        }
        // Text may wrap but never push the path icons out of the line (large font).
        Text(line.text, style = MaterialTheme.typography.labelMedium, color = tint, modifier = Modifier.weight(1f, fill = false))
        TransportIcons(line.via, tint)
    }
}

@Composable
private fun TransportIcons(via: Set<Transport>, tint: Color) {
    Transport.entries.filter { it in via }.forEach { transport ->
        Icon(
            when (transport) {
                Transport.INTERNET -> AppIcons.Internet
                Transport.RADIO -> AppIcons.Funk
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Web addresses underlined and tappable; they open in the phone's browser. Other schemes
 * are deliberately not linked ([findLinks]).
 */
private fun linkified(text: String, color: Color): AnnotatedString {
    val links = findLinks(text)
    if (links.isEmpty()) return AnnotatedString(text)
    val style = TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        var last = 0
        links.forEach { link ->
            append(text.substring(last, link.range.first))
            withLink(LinkAnnotation.Url(link.url, style)) { append(text.substring(link.range)) }
            last = link.range.last + 1
        }
        append(text.substring(last))
    }
}

/** Empty circle: a circle only comes alive with others. One task, one button. */
@Composable
private fun InviteFirst(onShare: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(size = 72.dp)
        Text(texts.chat.inviteSomeoneTitle, style = MaterialTheme.typography.headlineSmall)
        Text(
            texts.chat.inviteSomeoneText,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        AppButton(texts.chat.invite, onShare, kind = ButtonKind.Main, icon = AppIcons.Teilen)
    }
}

/** Hint in a direct conversation: every relay refuses our messages to [name], likely the account was deleted (ADR 0046). */
@Composable
internal fun UnreachableNotice(name: String, onRemove: () -> Unit, onKeep: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(texts.people.unreachableText(name), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(texts.people.unreachableRemove, onRemove, kind = ButtonKind.Danger)
            AppButton(texts.people.unreachableKeep, onKeep, kind = ButtonKind.Quiet)
        }
    }
}


/** „Treffpunkt hier“ (ADR 0121 §8): the place the person stands at, with a name if it wants, for everyone in the conversation. */
@Composable
private fun MeetingPointDialog(controller: AppController, conversation: Conversation, onDismiss: () -> Unit) {
    val mt = texts.map
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        PlacePickerDialog(controller, mt.spotMeet, onDismiss = { picking = false }) { lat, lon ->
            controller.sendMeetingPoint(conversation, lat, lon, name)
            picking = false
            onDismiss()
        }
        return
    }
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(mt.spotMeetHere) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(mt.spotMeetExplain)
                androidx.compose.material3.OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) }, singleLine = true, label = { Text(mt.spotMeetName) }, modifier = Modifier.fillMaxWidth(),
                )
                note?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                // Somewhere else than where the person stands: chosen on the map.
                AppButton(mt.spotMeetOnMap, { picking = true }, kind = ButtonKind.Secondary, icon = AppIcons.Karte)
            }
        },
        confirmButton = {
            AppButton(mt.spotMeetSend, {
                busy = true
                scope.launch {
                    when (val r = controller.sendOwnPlace(conversation, app.anynear.mesh.ChatSpot.MEET, name)) {
                        AppController.PlaceSend.Sent -> onDismiss()
                        else -> note = placeSendText(r)
                    }
                    busy = false
                }
            }, enabled = !busy)
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/**
 * A place in a message (ADR 0121 §8) as a card: a meeting point or someone's place opens the map, which leads there;
 * „Wo bist du?“ offers to send the own place once.
 */
@Composable
private fun SpotCard(controller: AppController, message: ChatMessage, spot: app.anynear.mesh.ChatSpot, senderName: String?, color: Color, onError: (String) -> Unit) {
    val mt = texts.map
    val scope = rememberCoroutineScope()
    val ask = spot.kind == app.anynear.mesh.ChatSpot.ASK
    if (spot.kind == app.anynear.mesh.ChatSpot.ALL) {
        ShareAllCard(controller, message, spot, senderName, color)
        return
    }
    val title = when (spot.kind) {
        app.anynear.mesh.ChatSpot.MEET -> spot.label?.let { mt.spotMeetText(it) } ?: mt.spotMeet
        app.anynear.mesh.ChatSpot.ASK -> mt.spotAsk
        else -> if (message.outgoing) mt.spotHereOwn else mt.spotHere(senderName ?: controller.findPerson(message.fromPeer.orEmpty())?.name ?: "?")
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Karte, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
        }
        val lat = spot.latitude
        val lon = spot.longitude
        if (lat != null && lon != null) {
            val focus = AppController.MapFocus(lat, lon, if (spot.kind == app.anynear.mesh.ChatSpot.MEET) spot.label ?: mt.spotMeet else title, message.fromPeer.takeIf { spot.kind == app.anynear.mesh.ChatSpot.HERE })
            SpotMap(controller, lat, lon, focus.label) { controller.showOnMap(focus) }
            if (spot.atMillis > 0) Text(mt.spotFrom(formatTime(spot.atMillis)), style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.75f))
        } else if (ask && !message.outgoing) {
            Text(mt.spotAnswerHint, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.75f))
            AppButton(mt.spotAnswer, {
                scope.launch {
                    val r = controller.sendOwnPlace(message.conversation, app.anynear.mesh.ChatSpot.HERE)
                    if (r != AppController.PlaceSend.Sent) onError(placeSendText(r))
                }
            }, kind = ButtonKind.Secondary, icon = AppIcons.Karte)
        }
        // In a circle the question goes to everyone: who has answered since, and who is still open (ADR 0121 §13).
        val circle = (message.conversation as? Conversation.Circle)?.circleId
        if (ask && circle != null) {
            val all by controller.messages.collectAsState()
            val people by controller.people.collectAsState()
            val asked = people.filter { circle in it.circleIds && it.deviceId != null && it.peer != message.fromPeer }
            val since = remember(all, message.localId) {
                all.filter { it.conversation == message.conversation && it.timestampMillis > message.timestampMillis }
            }
            val answered = asked.filter { p -> since.any { !it.outgoing && it.fromPeer?.let { peer -> app.anynear.people.Person.find(people, peer)?.peer } == p.peer } }
            // Asked by someone else, I count too.
            val me = if (message.outgoing) 0 else 1
            val mine = if (!message.outgoing && since.any { it.outgoing }) 1 else 0
            Text(mt.answered(answered.size + mine, asked.size + me), style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.85f))
            val open = (asked - answered.toSet()).map { it.name }
            if (open.isNotEmpty()) Text(mt.answeredOpen(open.joinToString(", ")), style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.75f))
        }
    }
}


/** What to tell the person when its place could not be sent. */
private fun placeSendText(result: AppController.PlaceSend): String = when (result) {
    AppController.PlaceSend.LocationOff -> texts.map.spotLocationOff
    AppController.PlaceSend.Unknown -> texts.map.spotNoFix
    AppController.PlaceSend.Failed -> texts.map.askFailed
    else -> texts.map.spotNeedsPlace
}


/** The cut-out of the map in a message with a place (ADR 0121 §13): it does not move; a tap opens the map there. */
@Composable
internal fun SpotMap(controller: AppController, latitude: Double, longitude: Double, label: String, onOpen: () -> Unit) {
    val outlines by androidx.compose.runtime.produceState<app.anynear.location.Outlines?>(null) { value = app.anynear.location.Outlines.shared() }
    val packs by (controller.mapTiles?.loaded ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, app.anynear.location.MapPack>()) }).collectAsState()
    val ground by androidx.compose.runtime.produceState(emptyList<app.anynear.location.MapPack>()) { value = app.anynear.location.MapPack.bundled() }
    val camera = remember(latitude, longitude) { MapCamera().apply { start = latitude to longitude } }
    val o = outlines
    // The tiles around the place, if the phone has them or the network offers them.
    LaunchedEffect(latitude, longitude) {
        controller.mapTiles?.want(
            listOf(app.anynear.location.MapPack.tileAt(app.anynear.location.MapPack.REGION, latitude, longitude), app.anynear.location.MapPack.tileAt(app.anynear.location.MapPack.NEAR, latitude, longitude)),
            kotlin.time.Clock.System.now().toEpochMilliseconds(),
        )
    }
    val here = remember(packs, latitude, longitude) {
        val keys = setOf(app.anynear.location.MapPack.tileAt(app.anynear.location.MapPack.REGION, latitude, longitude), app.anynear.location.MapPack.tileAt(app.anynear.location.MapPack.NEAR, latitude, longitude))
        packs.filterKeys { it in keys }.values.toList()
    }
    Box(
        Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable(onClickLabel = texts.map.spotShowOnMap, onClick = onOpen),
    ) {
        if (o != null) MapView(o, camera, listOf(MapMarker("spot", label, latitude, longitude, place = true)), emptyList(), emptyMap(), packs = here, ground = ground, interactive = false)
    }
}


/**
 * Is something about locations running in this conversation right now (ADR 0121 §13)? I share my place with it for a
 * time, or someone in it shows me a place right now. The header then carries the map icon, which leads to the map.
 */
@Composable
private fun rememberPlaceRunning(controller: AppController, conversation: Conversation): Boolean {
    val sharer = controller.placeSharer ?: return false
    val sharing = controller.locationSharing ?: return false
    val places by sharer.places.collectAsState()
    val until by sharing.until.collectAsState()
    val people by controller.people.collectAsState()
    val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val members = when (conversation) {
        is Conversation.Circle -> people.filter { conversation.circleId in it.circleIds }
        is Conversation.Direct -> listOfNotNull(app.anynear.people.Person.find(people, conversation.peer))
        else -> emptyList()
    }
    val mine = when (conversation) {
        is Conversation.Circle -> (until["c:${conversation.circleId}"] ?: 0) > now
        is Conversation.Direct -> members.any { m -> (until["p:${m.deviceId}"] ?: 0) > now }
        else -> false
    }
    val theirs = members.any { m -> places[m.deviceId]?.let { it.place != null && it.untilMillis !in 1..now && it.level == app.anynear.location.ShareLevel.Exact && it.untilMillis > 0 } == true }
    // A running sign of life in this circle is such an action, too (19.11.2026).
    val lifeSign = (conversation as? Conversation.Circle)?.let { c ->
        val statuses by (controller.lifeSigns?.statuses ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()
        statuses.any { it.plan.circleId == c.circleId && it.plan.peer == null && it.phase != app.anynear.anmp.core.AliveStatus.ENDED }
    } ?: (conversation as? Conversation.Direct)?.let {
        val statuses by (controller.lifeSigns?.statuses ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()
        val peer = members.firstOrNull()?.deviceId
        peer != null && statuses.any { it.plan.peer == peer && it.phase != app.anynear.anmp.core.AliveStatus.ENDED }
    } ?: false
    return mine || theirs || lifeSign
}


/**
 * The check-ins of the circle of [conversation] (ADR 0083 §5), sorted into the chat by the time they started: under the
 * ID of the message that follows, under `""` those after the last message. They come from the check-in itself, not from
 * a message, so the card is there over radio and for a check-in started with an older version, too. The core keeps a
 * check-in for a week after its end; then the card goes.
 */
@Composable
private fun rememberLifeSignCards(controller: AppController, conversation: Conversation, messages: List<ChatMessage>): Map<String, List<app.anynear.anmp.core.AliveStatus>> {
    val target = lifeSignTargetOf(controller, conversation) ?: return emptyMap()
    val statuses by (controller.lifeSigns?.statuses ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()
    return remember(statuses, messages, target) { lifeSignCardsOf(statuses.filter { app.anynear.lifesigns.LifeSigns.targetOf(it.plan) == target }, messages.map { it.localId to it.timestampMillis }) }
}

/**
 * What a check-in started in [conversation] goes to ([app.anynear.lifesigns.LifeSigns.targetOf]): the circle, or the
 * person of a direct conversation. `null` where there is none: a support circle, a radio without anynear.
 */
@Composable
private fun lifeSignTargetOf(controller: AppController, conversation: Conversation): String? {
    val people by controller.people.collectAsState()
    return when (conversation) {
        is Conversation.Circle -> conversation.circleId.takeIf { !controller.isSupportCircle(it) }
        is Conversation.Direct -> app.anynear.people.Person.find(people, conversation.peer)?.deviceId?.takeIf { !controller.isRadioOnly(conversation.peer) }
            ?.let { app.anynear.lifesigns.LifeSigns.PERSON_PREFIX + it }
        else -> null
    }
}

/** Sorts [statuses] before the first of [messages] (ID and time, oldest first) that is newer than their start; `""` after all. */
internal fun lifeSignCardsOf(statuses: List<app.anynear.anmp.core.AliveStatus>, messages: List<Pair<String, Long>>): Map<String, List<app.anynear.anmp.core.AliveStatus>> =
    statuses.sortedBy { it.plan.start }.groupBy { s -> messages.firstOrNull { it.second > s.plan.start }?.first ?: "" }

/**
 * A check-in in the chat of its circle (ADR 0121 §22): whose it is and until when, and how it stands right now. A tap
 * leads to what runs: the own question „Alles gut?“, the call for the helpers if nobody confirmed, otherwise the map
 * of the circle.
 */
@Composable
private fun LifeSignCard(controller: AppController, status: app.anynear.anmp.core.AliveStatus, modifier: Modifier = Modifier) {
    val lifeSigns = controller.lifeSigns ?: return
    val lt = texts.lifeSigns
    val plan = status.plan
    val calls by (controller.sos?.calls ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()
    val call = calls.firstOrNull { it.plan == plan.id && !it.ended }
    val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val phase = status.phase
    val ended = phase == app.anynear.anmp.core.AliveStatus.ENDED
    val alarm = !ended && (phase == app.anynear.anmp.core.AliveStatus.ALARM || call != null)
    val state = when {
        ended -> if (plan.state == app.anynear.anmp.AnmpAliveState.TIMEOUT) lt.cardTimedOut else lt.cardEnded
        alarm -> if (call?.reason == app.anynear.anmp.core.AliveStatus.REASON_UNREACHABLE || (call == null && status.reason == app.anynear.anmp.core.AliveStatus.REASON_UNREACHABLE)) lt.cardUnreachable else lt.cardMissed
        phase == app.anynear.anmp.core.AliveStatus.ASK -> lt.cardAsk
        phase == app.anynear.anmp.core.AliveStatus.HINT -> lt.cardHint
        !status.own && lifeSigns.meAway(status) -> lt.cardAway
        status.own -> lt.cardNext(formatFutureDayAndTime(plan.due, now))
        else -> lt.cardRunning(formatTime(plan.confirmedAt.takeIf { it > 0 } ?: plan.lastAt))
    }
    val accent = when {
        alarm -> AppTheme.colors.alarm
        ended -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> AppTheme.colors.safe
    }
    val open: () -> Unit = {
        when {
            // With one person there is no map of a circle to lead to.
            plan.peer != null && (ended || (call == null && !status.own)) -> Unit
            ended -> controller.showMapFor(Conversation.Circle(plan.circleId))
            call != null && !call.own -> controller.sos?.open(call.id)
            call != null -> controller.sos?.open()
            status.own -> lifeSigns.openFullScreen()
            else -> controller.showMapFor(Conversation.Circle(plan.circleId))
        }
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 320.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.5.dp, accent.copy(alpha = if (ended) 0.25f else 0.7f), RoundedCornerShape(16.dp))
                .clickable(onClickLabel = lt.cardOpen, role = Role.Button, onClick = open).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.Karte, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Text(
                    if (status.own) lt.cardOwn(formatFutureDayAndTime(plan.until, plan.start)) else lt.cardOf(lifeSigns.name(plan.owner), formatFutureDayAndTime(plan.until, plan.start)),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (plan.label.isNotBlank()) Text(lt.cardLabel(plan.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state, style = MaterialTheme.typography.bodyMedium, fontWeight = if (alarm) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal, color = if (alarm) accent else MaterialTheme.colorScheme.onSurface)
            // Where the phone is, if nobody confirmed and the person shares it for that case.
            val place = (call?.position ?: plan.position)?.takeIf { alarm && !status.own }
            if (place != null) SpotMap(controller, place.latitude, place.longitude, lifeSigns.name(plan.owner), open)
            if (!ended) Text(lt.cardOpen, style = MaterialTheme.typography.labelLarge, color = accent, modifier = Modifier.align(Alignment.End))
        }
    }
}


/** „Alle teilen“ (ADR 0121 §13): how long everyone in the circle is invited to share; the starter joins by itself. */
@Composable
private fun ShareAllDialog(controller: AppController, circleId: String, onDismiss: () -> Unit) {
    val mt = texts.map
    var minutes by remember { mutableStateOf(120) }
    ActionsDialog(mt.shareAll, onDismiss = onDismiss) {
        Text(mt.shareAllText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Text(mt.forHowLong, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(60, 120, 240, 480, 720).forEach { m ->
                androidx.compose.material3.FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text(if (m < 120) mt.minutes(m) else mt.hours(m / 60)) })
            }
        }
        Text(mt.openOnly, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        AppButton(
            mt.shareAllStart,
            {
                controller.inviteAllToShare(circleId, minutes)
                onDismiss()
            },
            Modifier.fillMaxWidth().padding(16.dp), kind = ButtonKind.Main,
        )
    }
}

/**
 * The invitation „Alle teilen“ in the chat (ADR 0121 §13): nobody shares before agreeing. „Mitmachen“ shares the own
 * place with the circle until the end, „Aussteigen“ stops it any time; the card says who takes part.
 */
@Composable
private fun ShareAllCard(controller: AppController, message: ChatMessage, spot: app.anynear.mesh.ChatSpot, senderName: String?, color: Color) {
    val mt = texts.map
    val sharing = controller.locationSharing ?: return
    val circleId = (message.conversation as? Conversation.Circle)?.circleId ?: return
    val until = spot.untilMillis ?: return
    val levels by sharing.circles.collectAsState()
    val untils by sharing.until.collectAsState()
    val places by (controller.placeSharer?.places ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, app.anynear.location.SharedPlace>()) }).collectAsState()
    val people by controller.people.collectAsState()
    val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val over = until <= now
    // I take part while this circle is set to the exact place until this very end.
    val joined = !over && levels[circleId] == app.anynear.location.ShareLevel.Exact && untils["c:$circleId"] == until
    val members = people.filter { circleId in it.circleIds && it.deviceId != null }
    // Who agreed counts, also while its phone has no place yet (e.g. indoors without a fix).
    val taking = members.filter { m -> places[m.deviceId]?.let { it.level == app.anynear.location.ShareLevel.Exact && kotlin.math.abs(it.untilMillis - until) < 2000 } == true }
    var note by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Kreis, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Text(if (over) mt.shareAllOver else mt.shareAllUntil(formatTime(until)), style = MaterialTheme.typography.titleSmall, color = color)
        }
        if (!over) {
            Text(mt.shareAllTaking(taking.size + if (joined) 1 else 0, members.size + 1), style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.85f))
            if (taking.isNotEmpty()) Text(taking.joinToString(", ") { it.name }, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.75f))
            if (joined) {
                AppButton(mt.shareAllLeave, { controller.leaveSharing(circleId) }, kind = ButtonKind.Secondary)
            } else {
                Text(mt.shareAllHint, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.75f))
                AppButton(mt.shareAllJoin, { if (!controller.joinSharing(circleId, until)) note = mt.spotNeedsPlace }, kind = ButtonKind.Secondary, icon = AppIcons.Karte)
            }
            AppButton(mt.spotShowOnMap, { controller.showMapFor(message.conversation) }, kind = ButtonKind.Quiet, icon = AppIcons.Karte)
            note?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
        }
    }
}
