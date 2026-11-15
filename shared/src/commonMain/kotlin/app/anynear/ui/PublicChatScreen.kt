package app.anynear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.app.PublicMessage
import app.anynear.app.PublicRadio
import app.anynear.app.PublicSendResult
import app.anynear.i18n.texts
import app.anynear.mesh.MessageStatus
import app.anynear.mesh.PublicChannel

/** Name of the public chat (ADR 0055). */
internal val PublicChannel.title: String
    get() = when (this) {
        PublicChannel.NEARBY -> texts.publicRadio.nearbyTitle
        PublicChannel.MESH_HESSEN -> texts.publicRadio.meshHessenTitle
    }

/** Subline that always says: public. */
internal val PublicChannel.subtitle: String
    get() = when (this) {
        PublicChannel.NEARBY -> texts.publicRadio.nearbySubtitle
        PublicChannel.MESH_HESSEN -> texts.publicRadio.meshHessenSubtitle
    }

private val PublicChannel.intro: String
    get() = when (this) {
        PublicChannel.NEARBY -> texts.publicRadio.intro
        PublicChannel.MESH_HESSEN -> texts.publicRadio.intro + "\n\n" + texts.publicRadio.meshHessenIntro
    }

/**
 * Picture of a public chat: the radio sign on a neutral, angular surface, so that it never looks like a circle (rings)
 * or a person (round).
 */
@Composable
internal fun PublicChannelAvatar(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(size / 5)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Funk, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size / 2))
    }
}

/**
 * Public chat (ADR 0055): own design, explanation on first opening, confirmation before the first message,
 * only short text and honest states („gesendet“, „weitergegeben“).
 */
@Composable
internal fun PublicChatScreen(controller: AppController, publicRadio: PublicRadio, channel: PublicChannel, onBack: () -> Unit) {
    var info by rememberSaveable(channel) { mutableStateOf(false) }
    if (info) {
        SystemBackHandler(enabled = true, onBack = { info = false })
        PublicChannelInfoScreen(publicRadio, channel, onBack = { info = false })
        return
    }
    val all by publicRadio.messages.collectAsState()
    val messages = all.filter { it.channel == channel }
    var showIntro by remember(channel) { mutableStateOf(!publicRadio.introSeen(channel)) }
    var draft by rememberSaveable(channel) { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val readable by controller.readable.collectAsState()
    LaunchedEffect(messages.size, readable) {
        if (readable) {
            publicRadio.markRead(channel)
            controller.cancelPublicNotification(channel)
        }
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1 + if (showIntro) 1 else 0)
    }

    fun send() {
        notice = when (val result = publicRadio.send(channel, draft)) {
            PublicSendResult.Sent -> {
                draft = ""
                null
            }
            PublicSendResult.Empty -> null
            PublicSendResult.TooLong -> texts.publicRadio.tooLong
            is PublicSendResult.TooSoon -> texts.publicRadio.tooSoon(result.seconds)
            PublicSendResult.NotReady -> texts.publicRadio.notReady
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = channel.title,
            subtitle = channel.subtitle,
            leading = { HeaderBackButton(onBack) },
            avatar = { PublicChannelAvatar(40.dp) },
            // As with circle and person: tapping the header opens the page of the chat.
            onTitleClick = { info = true },
            titleClickLabel = texts.publicRadio.aboutChat,
            trailing = { HeaderAction(AppIcons.Info, texts.publicRadio.aboutChat) { info = true } },
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showIntro) {
                item(key = "intro") {
                    AppSection {
                        SectionContent {
                            Text(channel.intro, style = MaterialTheme.typography.bodyLarge)
                            AppButton(texts.publicRadio.introOk, {
                                publicRadio.setIntroSeen(channel)
                                showIntro = false
                            }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                        }
                    }
                }
            }
            if (messages.isEmpty()) {
                item(key = "empty") {
                    Text(
                        texts.publicRadio.noMessagesYet,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                PublicBubble(controller, message) { emoji ->
                    notice = when (val result = publicRadio.react(message.id, emoji)) {
                        is PublicSendResult.TooSoon -> texts.publicRadio.tooSoon(result.seconds)
                        PublicSendResult.NotReady -> texts.publicRadio.notReady
                        else -> null
                    }
                }
            }
        }
        notice?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        val left = PublicRadio.MAX_BYTES - draft.trim().encodeToByteArray().size
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    notice = null
                },
                placeholder = { Text(texts.publicRadio.inputHint) },
                supportingText = if (left < 40) ({ Text(texts.publicRadio.bytesLeft(left)) }) else null,
                isError = left < 0,
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = { if (publicRadio.firstSendConfirmed) send() else confirm = true },
                enabled = draft.isNotBlank() && left >= 0,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp).size(52.dp),
            ) {
                Icon(AppIcons.Senden, contentDescription = texts.chat.send)
            }
        }
    }

    if (confirm) {
        AppDialog(
            onDismissRequest = { confirm = false },
            title = { Text(texts.publicRadio.confirmTitle) },
            text = { Text(texts.publicRadio.confirmText, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                AppButton(texts.publicRadio.confirmSend, {
                    publicRadio.confirmFirstSend()
                    confirm = false
                    send()
                })
            },
            dismissButton = { AppButton(texts.common.cancel, { confirm = false }, kind = ButtonKind.Quiet) },
        )
    }
}

/** Page of a public chat: switch notifications on and off (default off, ADR 0055 §4) and the explanation. */
@Composable
private fun PublicChannelInfoScreen(publicRadio: PublicRadio, channel: PublicChannel, onBack: () -> Unit) {
    val notify by publicRadio.notify.collectAsState()
    val on = notify[channel] == true
    Column(Modifier.fillMaxSize()) {
        AppHeader(title = channel.title, leading = { HeaderBackButton(onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PublicChannelAvatar(112.dp)
                Text(channel.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                Text(channel.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            AppSection {
                AppRow(
                    title = texts.publicRadio.notifications,
                    subtitle = if (on) texts.publicRadio.notificationsOn else texts.publicRadio.notificationsOff,
                    icon = AppIcons.Nachrichten,
                    trailing = { Switch(checked = on, onCheckedChange = { publicRadio.setNotify(channel, it) }) },
                )
            }
            AppSection(texts.publicRadio.aboutChat) {
                SectionContent { Text(channel.intro, style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

@Composable
private fun PublicBubble(controller: AppController, message: PublicMessage, onReact: (String) -> Unit) {
    val colors = AppTheme.colors
    val outgoing = message.outgoing
    // As in the chat: details with reactions via the „i“ or a long press, a second time closes.
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val react = { emoji: String ->
        onReact(emoji)
        expanded = false
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (outgoing) DetailsButton(expanded) { expanded = !expanded }
            Column(
                Modifier
                    .widthIn(max = 296.dp)
                    .background(if (outgoing) colors.outgoingBubble else colors.incomingBubble, RoundedCornerShape(16.dp))
                    .combinedClickable(onLongClickLabel = if (expanded) texts.chat.hideDetails else texts.chat.showDetailsAndReactions, onLongClick = { expanded = !expanded }) {}
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!outgoing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(AppIcons.Funkgeraet, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Text(publicSender(controller, message), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text(message.text, style = MaterialTheme.typography.bodyLarge, color = if (outgoing) colors.onOutgoingBubble else MaterialTheme.colorScheme.onSurface)
                val meta = buildString {
                    append(formatTime(message.timestampMillis))
                    if (outgoing) message.status?.let { append(" · ").append(it.publicLabel()) }
                }
                Text(meta, style = MaterialTheme.typography.labelMedium, color = if (outgoing) colors.onOutgoingBubble else MaterialTheme.colorScheme.onSurface)
            }
            if (!outgoing) DetailsButton(expanded) { expanded = !expanded }
        }
        // Whoever reacted is a radio, not a verified person.
        Reactions(message.reactionGroups, { key -> PublicMessage.nodeOfReactor(key)?.let { radioLabel(controller, it) } ?: key }, if (outgoing) null else react)
        if (expanded) {
            message.detailLines().forEach { line ->
                Text(line, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
        // Meshtastic does not know taking back: tapping your own reaction again does nothing.
        if (expanded && !outgoing) ReactionPicker(message.ownReaction) { if (it.isNotEmpty()) react(it) }
    }
}

/** Radio instead of person: for your own contact its name, otherwise the name of the device. Nothing is verified. */
private fun publicSender(controller: AppController, message: PublicMessage): String =
    controller.contactNameForRadio(message.fromNodeNum)?.let(texts.publicRadio::contactRadio)
        ?: message.fromName?.takeIf { it.isNotBlank() }
        ?: texts.publicRadio.unknownRadio

private fun radioLabel(controller: AppController, nodeNum: Int): String =
    controller.contactNameForRadio(nodeNum)?.let(texts.publicRadio::contactRadio) ?: controller.displayName(nodeNum)

/**
 * Details behind the „i“: when, from which radio, by which route and how good the reception was. Only what the
 * radio packet really says; if a value is missing, the line is missing.
 */
private fun PublicMessage.detailLines(): List<String> {
    if (outgoing) return listOf(texts.chat.sentAt(formatDateTime(timestampMillis)))
    val lines = mutableListOf(texts.chat.receivedAt(formatDateTime(timestampMillis)))
    if (fromNodeNum != 0) lines += texts.publicRadio.radioId("!" + fromNodeNum.toUInt().toString(16).padStart(8, '0'))
    when {
        viaMqtt -> lines += texts.publicRadio.viaBridge
        hops == 0 -> lines += texts.publicRadio.heardDirectly
        hops != null -> lines += texts.publicRadio.relayedBy(hops)
    }
    val values = listOfNotNull(snr?.let { "SNR ${formatDecimal(it)} dB" }, rssi?.let { "RSSI $it dBm" })
    if (values.isNotEmpty() && !viaMqtt) {
        // Roughly following Meshtastic: from -7 dB good, from -15 dB medium, below that weak. Without SNR no rating.
        val quality = snr?.let {
            when {
                it >= -7f -> texts.publicRadio.signalGood
                it >= -15f -> texts.publicRadio.signalFair
                else -> texts.publicRadio.signalWeak
            }
        }
        lines += if (quality != null) texts.publicRadio.signal(quality, values.joinToString(" · ")) else values.joinToString(" · ")
    }
    return lines
}

/** One decimal place with the decimal separator of the language, without platform formatting. */
private fun formatDecimal(value: Float): String {
    val tenths = kotlin.math.round(value * 10).toInt()
    val sign = if (tenths < 0) "-" else ""
    val abs = kotlin.math.abs(tenths)
    return "$sign${abs / 10}${texts.publicRadio.decimalSeparator}${abs % 10}"
}

/** Honest: there is no single recipient, so never „angekommen“ or „gelesen“. */
private fun MessageStatus.publicLabel(): String = when (this) {
    MessageStatus.QUEUED -> texts.publicRadio.statusSending
    MessageStatus.FAILED -> texts.publicRadio.statusFailed
    MessageStatus.RELAYED -> texts.publicRadio.statusRelayed
    else -> texts.publicRadio.statusSent
}
