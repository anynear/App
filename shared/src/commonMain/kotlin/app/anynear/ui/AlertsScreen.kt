package app.anynear.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anynear.anmp.AnmpAttachment
import app.anynear.alerts.AlarmProblem
import app.anynear.alerts.AlertAcks
import app.anynear.alerts.Alert
import app.anynear.alerts.AlertChannel
import app.anynear.alerts.AlertIngress
import app.anynear.alerts.AlertPriority
import app.anynear.alerts.AlertSound
import app.anynear.alerts.AlertStatus
import app.anynear.alerts.AlertSubscriber
import app.anynear.alerts.Alerts
import app.anynear.alerts.ChannelRow
import app.anynear.alerts.activeAlerts
import app.anynear.alerts.canEnd
import app.anynear.alerts.priorityLabel
import app.anynear.alerts.replacedIds
import app.anynear.alerts.statusAt
import app.anynear.attachments.AttachmentKind
import app.anynear.circles.Circle
import app.anynear.lifesigns.LifeSigns
import app.anynear.anmp.core.AliveStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import app.anynear.attachments.mimeTypeOf
import app.anynear.i18n.texts
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

// „Meldungen“ area (ADR 0062 §9): at the top what matters right now, below it the channels; in the channel history
// and settings. There are no replies here.

/** Without check-ins (no core yet): nothing runs. */

private fun nowMillis() = Clock.System.now().toEpochMilliseconds()

/** Clock for „aktiv“/„abgelaufen“: advances every half minute as long as the page is open. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = nowMillis()
        }
    }
    return now
}

/** „Meldungen“ tab. */
@Composable
internal fun AlertsScreen(alerts: Alerts, lifeSigns: LifeSigns?, circles: StateFlow<List<Circle>>, onOpenChannel: (String) -> Unit, onCreateCircle: () -> Unit = {}) {
    val rows by alerts.rows.collectAsState()
    val list by alerts.alerts.collectAsState()
    val channels by alerts.channels.collectAsState()
    val curated by alerts.curatedChannel.collectAsState()
    val now = rememberNow()
    var creating by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf<Alert?>(null) }
    val acked by alerts.acked.collectAsState()
    var showAcked by rememberSaveable { mutableStateOf(false) }
    val allActive = activeAlerts(list, now)
    // Acknowledged ones stay in the history, the top shows only open ones (for this person only, on all their devices).
    val (ackedActive, active) = allActive.partition { AlertAcks.isAcked(acked, it.id) }
    val replaced = replacedIds(list)
    // Rarely needed, so in the ⋮ menu instead of on the page (ADR 0101); a running check-in stays at the top.
    // Check-ins moved to the map (ADR 0121 §1); „Meldungen“ keeps what comes from outside.
    val menu = listOf(MenuAction(texts.alerts.createChannel, AppIcons.Plus) { creating = true })
    Column(Modifier.fillMaxSize()) {
        AppHeader(texts.alerts.title, menu = menu)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (allActive.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle(texts.alerts.activeSection)
                    active.forEach { alert ->
                        val channel = channels.firstOrNull { it.id == alert.channelId }
                        AlertCard(
                            alert, channel, now, big = true, onClick = { onOpenChannel(alert.channelId) },
                            onEnd = if (canEnd(alert, channel, replaced, now)) ({ ending = alert }) else null,
                            onAck = { alerts.ack(alert) },
                        )
                    }
                    if (ackedActive.isNotEmpty()) {
                        AppSection {
                            ExpandRow(texts.alerts.ackedHidden(ackedActive.size), expanded = showAcked, icon = AppIcons.Haken) { showAcked = !showAcked }
                        }
                        if (showAcked) {
                            ackedActive.forEach { alert ->
                                val channel = channels.firstOrNull { it.id == alert.channelId }
                                AlertCard(
                                    alert, channel, now, big = false, onClick = { onOpenChannel(alert.channelId) },
                                    onAck = { alerts.unack(alert) }, ackLabel = texts.alerts.unack,
                                )
                            }
                        }
                    }
                }
            }
            val own = rows.filterNot { it.channel.curated }
            if (own.isEmpty()) {
                AppSection(footer = texts.alerts.emptyText) {
                    AppRow(title = texts.alerts.emptyTitle, icon = AppIcons.Glocke)
                }
            } else {
                AppSection(texts.alerts.channelsSection) {
                    own.forEach { row -> ChannelRowView(row, now) { onOpenChannel(row.channel.id) } }
                }
            }
            // Official warnings only if the curated channel is set up (implementation plan §5).
            if (alerts.curatedLink != null || curated != null) OfficialSection(alerts, curated, rows, now, onOpenChannel)
        }
    }
    if (creating) CreateChannelDialog(alerts, onDismiss = { creating = false }, onCreated = onOpenChannel)
    ending?.let { alert -> EndAlertDialog(alerts, alert, onDismiss = { ending = null }) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
    )
}

/** Official warnings (NINA): turn on, open the channel; the district comes from the approximate location. */
@Composable
private fun OfficialSection(alerts: Alerts, curated: AlertChannel?, rows: List<ChannelRow>, now: Long, onOpenChannel: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val region by alerts.region.collectAsState()
    // The channel is the row; the district only a hint below it, not a second row of equal rank.
    val footer = listOfNotNull(region?.takeIf { curated != null }?.let { texts.alerts.regionHint(it.name) }, texts.alerts.officialFooter).joinToString("\n\n")
    AppSection(texts.alerts.officialSection, footer = footer) {
        if (curated == null) {
            AppRow(
                title = texts.alerts.officialTitle,
                subtitle = texts.alerts.officialSubtitle,
                icon = AppIcons.Achtung,
                trailing = { AppButton(texts.alerts.officialTurnOn, { scope.launch { alerts.enableCurated() } }, kind = ButtonKind.Secondary) },
            )
        } else {
            rows.firstOrNull { it.channel.id == curated.id }?.let { row -> ChannelRowView(row, now) { onOpenChannel(curated.id) } }
            RegionRows(alerts, moreInfoWhenKnown = false)
        }
    }
}

/**
 * District for official warnings: calmly the name, with „Mehr dazu“. Without a district a short sentence saying
 * why, and a button; until then the app shows no official warning and reports none.
 */
@Composable
private fun RegionRows(alerts: Alerts, moreInfoWhenKnown: Boolean = true) {
    val region by alerts.region.collectAsState()
    val allowed by alerts.locationAllowed.collectAsState()
    val locating by alerts.locating.collectAsState()
    var moreInfo by remember { mutableStateOf(false) }
    val current = region
    // Known district: no row of its own, it is shown as a hint with the channel (footer text or header subtitle).
    if (current != null && !moreInfoWhenKnown) return
    when {
        current != null -> Unit
        !allowed -> SectionContent {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(texts.alerts.regionNeedsLocation, style = MaterialTheme.typography.bodyMedium)
                AppButton(texts.alerts.allowLocation, alerts::allowLocation, kind = ButtonKind.Secondary)
            }
        }
        // Right after permission is granted the search is not running yet: then, too, „wird gesucht“.
        locating == Alerts.Locating.Busy || locating == Alerts.Locating.Idle -> AppRow(title = texts.alerts.regionLocating, icon = AppIcons.Info)
        else -> SectionContent {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (locating == Alerts.Locating.Abroad) texts.alerts.regionAbroad else texts.alerts.regionFailed,
                    style = MaterialTheme.typography.bodyMedium,
                )
                AppButton(texts.common.retry, alerts::allowLocation, kind = ButtonKind.Secondary)
            }
        }
    }
    ExpandRow(texts.common.moreInfo, moreInfo) { moreInfo = !moreInfo }
    if (moreInfo) SectionContent { Text(texts.alerts.regionInfo, style = MaterialTheme.typography.bodyMedium) }
}

/** A row as in the conversation list: name, last alert, time, number of unread important ones. */
@Composable
private fun ChannelRowView(row: ChannelRow, now: Long, onClick: () -> Unit) {
    val last = row.last
    AppRow(
        title = row.channel.name,
        subtitle = last?.let { "${priorityLabel(it.priority, row.channel.curated, it.ended)} · ${it.title?.ifBlank { null } ?: it.text}" } ?: texts.alerts.noAlertYet,
        icon = if (row.channel.curated) AppIcons.Achtung else AppIcons.Glocke,
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                last?.let { Text(formatChatListTime(it.timestampMillis, now), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (row.unread > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.clearAndSetSemantics {}) {
                        Text(if (row.unread > 99) "99+" else row.unread.toString())
                    }
                }
            }
        },
        chevron = true,
        onClick = onClick,
    )
}

/**
 * An alert as a card. [big]: at the top under „Gerade aktiv“, large and coloured. In the history calm, with state,
 * picture and context button.
 */
@Composable
private fun AlertCard(
    alert: Alert,
    channel: AlertChannel?,
    now: Long,
    big: Boolean,
    onClick: (() -> Unit)? = null,
    image: (@Composable () -> Unit)? = null,
    onEnd: (() -> Unit)? = null,
    /** Button at the bottom right: „Gesehen“ or „Wieder einblenden“ ([ackLabel]). */
    onAck: (() -> Unit)? = null,
    ackLabel: String = texts.alerts.ack,
) {
    val colors = AppTheme.colors
    val scheme = MaterialTheme.colorScheme
    val status = alert.statusAt(now)
    val loud = big && status == AlertStatus.Active
    val (container, content) = when {
        loud && alert.priority == AlertPriority.Alarm -> colors.alarmContainer to colors.onAlarmContainer
        loud -> colors.warningContainer to colors.onWarningContainer
        else -> scheme.surfaceContainer to scheme.onSurface
    }
    val uriHandler = LocalUriHandler.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PriorityChip(alert, channel?.curated == true)
                Text(
                    listOfNotNull(channel?.name?.takeIf { big }, formatDayAndTime(alert.timestampMillis, now)).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                if (!big) StatusLabel(status)
            }
            alert.title?.ifBlank { null }?.let {
                Text(it, style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (alert.text.isNotBlank()) Text(alert.text, style = MaterialTheme.typography.bodyLarge)
            image?.invoke()
            val details = listOfNotNull(
                alert.source?.let { texts.alerts.fromSource(it) },
                // Arrived via an address for devices: its name, known only to the owner.
                channel?.ingress?.firstOrNull { it.id == alert.sender }?.label,
                texts.alerts.validUntil(formatFutureDayAndTime(alert.untilMillis, now)).takeIf { status == AlertStatus.Active && !alert.ended },
            )
            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            alert.url?.let { url ->
                AppButton(texts.alerts.openContext, { runCatching { uriHandler.openUri(url) } }, kind = ButtonKind.Secondary, icon = AppIcons.Link)
            }
            if (onEnd != null || onAck != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onEnd?.let { AppButton(texts.alerts.endAlert, it, kind = ButtonKind.Secondary) }
                    onAck?.let { AppButton(ackLabel, it, kind = ButtonKind.Secondary, icon = AppIcons.Haken) }
                }
            }
        }
    }
}

/** Prompt before the all-clear; afterwards the alert is in the history as „entwarnt“ and no longer at the top. */
@Composable
private fun EndAlertDialog(alerts: Alerts, alert: Alert, onDismiss: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val name = alert.title?.ifBlank { null } ?: alert.text.lineSequence().first().take(60)
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.alerts.endAlertTitle(name)) },
        text = { Text(texts.alerts.endAlertText) },
        confirmButton = {
            AppButton(texts.alerts.endAlert, {
                busy = true
                scope.launch {
                    val ok = alerts.endAlert(alert)
                    busy = false
                    if (ok) onDismiss()
                }
            }, enabled = !busy)
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/** Small badge with the priority; „Alarm“ in the alarm colour. Text next to it, never colour alone. */
@Composable
internal fun PriorityChip(alert: Alert, curated: Boolean) {
    val colors = AppTheme.colors
    val (bg, fg) = when {
        alert.ended -> colors.safeContainer to colors.onSafeContainer
        alert.priority == AlertPriority.Alarm -> colors.alarm to colors.onAlarm
        alert.priority == AlertPriority.Warning -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
    }
    Text(
        priorityLabel(alert.priority, curated, alert.ended),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = Modifier.background(bg, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun StatusLabel(status: AlertStatus) {
    val (text, dot) = when (status) {
        AlertStatus.Active -> texts.alerts.statusActive to true
        AlertStatus.Ended -> texts.alerts.statusEnded to false
        AlertStatus.Expired -> texts.alerts.statusExpired to false
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusDot(dot)
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * A channel (ADR 0062 §9): history, above it the owner's actions. As with circles and chats the title leads to the
 * details ([ChannelDetailScreen]): settings on this device, subscribers, addresses, leaving.
 */
@Composable
internal fun ChannelScreen(alerts: Alerts, channelId: String, onBack: () -> Unit, focusAlert: String? = null) {
    val channels by alerts.channels.collectAsState()
    val all by alerts.alerts.collectAsState()
    val region by alerts.region.collectAsState()
    val channel = channels.firstOrNull { it.id == channelId }
    // Channel left or removed: back to the overview.
    LaunchedEffect(channel == null) { if (channel == null) onBack() }
    if (channel == null) return
    var details by rememberSaveable(channelId) { mutableStateOf(false) }
    val subtitle = when {
        channel.curated -> listOfNotNull(texts.alerts.kindOfficial, region?.name).joinToString(" · ")
        channel.owner -> texts.alerts.kindOwn
        else -> texts.alerts.kindSubscribed
    }
    if (details) {
        SystemBackHandler(enabled = true, onBack = { details = false })
        ChannelDetailScreen(alerts, channel, subtitle, onBack = { details = false })
        return
    }
    val history = all.filter { it.channelId == channelId }
    // Everything here counts as read, including what arrives while the page is open.
    LaunchedEffect(channelId, history.size) { alerts.markSeen(channelId) }
    val now = rememberNow()
    var composing by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf<Alert?>(null) }
    val replaced = replacedIds(history)
    Column(Modifier.fillMaxSize()) {
        AppHeader(
            channel.name,
            subtitle = subtitle,
            leading = { HeaderBackButton(onBack) },
            onTitleClick = { details = true },
            titleClickLabel = texts.alerts.viewChannel,
            trailing = if (channel.owner) ({ HeaderAction(AppIcons.Teilen, texts.alerts.share) { alerts.shareChannel(channel.id) } }) else null,
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (channel.owner) {
                ActionTiles {
                    ActionTile(texts.alerts.send, AppIcons.Senden, main = true) { composing = true }
                    ActionTile(texts.alerts.share, AppIcons.Teilen) { alerts.shareChannel(channel.id) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(texts.alerts.history)
                if (history.isEmpty()) {
                    Text(texts.alerts.noHistory, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
                history.forEach { alert ->
                    // Opened from a warning on the map (ADR 0121 §7): that alert is brought into view.
                    val focused = alert.id == focusAlert
                    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
                    if (focused) LaunchedEffect(alert.id) { kotlinx.coroutines.delay(150); bring.bringIntoView() }
                    Box(Modifier.bringIntoViewRequester(bring)) {
                        AlertCard(
                            alert, channel, now, big = focused && alert.statusAt(now) == AlertStatus.Active,
                            image = if (alert.attachments.isEmpty()) null else ({ AlertAttachments(alerts, alert) }),
                            onEnd = if (canEnd(alert, channel, replaced, now)) ({ ending = alert }) else null,
                        )
                    }
                }
            }
        }
    }
    if (composing) ComposeDialog(alerts, channel, onDismiss = { composing = false })
    ending?.let { alert -> EndAlertDialog(alerts, alert, onDismiss = { ending = null }) }
}

/** Details of a channel, like the detail page of a circle: settings, region, subscribers, addresses, leaving. */
@Composable
private fun ChannelDetailScreen(alerts: Alerts, channel: AlertChannel, subtitle: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppHeader(
            channel.name,
            subtitle = subtitle,
            leading = { HeaderBackButton(onBack) },
            trailing = if (channel.owner) ({ HeaderAction(AppIcons.Teilen, texts.alerts.share) { alerts.shareChannel(channel.id) } }) else null,
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ChannelSettingsSection(alerts, channel)
            if (channel.curated) AppSection { RegionRows(alerts) }
            if (channel.owner && !channel.curated) SubscribersSection(alerts, channel)
            if (channel.owner) AddressesSection(alerts, channel)
            LeaveSection(alerts, channel)
        }
    }
}

/** Pictures are loaded and shown, other files open when tapped. */
@Composable
private fun AlertAttachments(alerts: Alerts, alert: Alert) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        alert.attachments.forEach { attachment ->
            if (AttachmentKind.fromWire(attachment.type) == AttachmentKind.IMAGE) AlertImage(alerts, alert, attachment)
            else AlertFile(alerts, alert, attachment)
        }
    }
}

@Composable
private fun AlertImage(alerts: Alerts, alert: Alert, attachment: AnmpAttachment) {
    var bitmap by remember(alert.id, attachment.name) { mutableStateOf<ImageBitmap?>(null) }
    var bytes by remember(alert.id, attachment.name) { mutableStateOf<ByteArray?>(null) }
    var failed by remember(alert.id, attachment.name) { mutableStateOf(false) }
    // Tapping opens the picture full screen in the app's viewer, like attachments in the chat and files in „Meine Ablage“.
    var viewing by remember(alert.id, attachment.name) { mutableStateOf<ViewedFile?>(null) }
    LaunchedEffect(alert.id, attachment.name) {
        bytes = alerts.fetchAttachment(alert, attachment)
        bitmap = bytes?.let { withContext(Dispatchers.Default) { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
        failed = bitmap == null
    }
    viewing?.let { FileViewer(it) { viewing = null } }
    when {
        bitmap != null -> Image(
            bitmap!!,
            contentDescription = attachment.name ?: texts.chat.photo,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClickLabel = texts.chat.photo) {
                    bytes?.let { viewing = ViewedFile(it, attachment.name ?: texts.chat.photo, "image/jpeg") }
                },
        )
        failed -> Text(texts.alerts.imageUnavailable, style = MaterialTheme.typography.bodySmall)
        else -> Box(Modifier.fillMaxWidth().heightIn(min = 120.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small))
    }
}

@Composable
private fun AlertFile(alerts: Alerts, alert: Alert, attachment: AnmpAttachment) {
    val scope = rememberCoroutineScope()
    val kind = AttachmentKind.fromWire(attachment.type) ?: AttachmentKind.FILE
    val name = attachment.name ?: kind.name.lowercase()
    var viewing by remember(attachment) { mutableStateOf<ViewedFile?>(null) }
    viewing?.let { FileViewer(it) { viewing = null } }
    AppButton(shortFileName(name), {
        scope.launch { alerts.fetchAttachment(alert, attachment)?.let { viewing = ViewedFile(it, name, mimeTypeOf(kind, name)) } }
    }, kind = ButtonKind.Secondary, icon = AppIcons.Dokument)
}

/** Notifications on or off, from which level loud, which sound, test alarm (ADR 0062 §5, §6). */
@Composable
private fun ChannelSettingsSection(alerts: Alerts, channel: AlertChannel) {
    val all by alerts.settings.collectAsState()
    val settings = all[channel.id] ?: alerts.settingsFor(channel)
    val scope = rememberCoroutineScope()
    var loudChoice by remember { mutableStateOf(false) }
    var soundChoice by remember { mutableStateOf(false) }
    var problems by remember { mutableStateOf<List<AlarmProblem>?>(null) }
    val loudOptions = listOf(
        AlertPriority.Alarm to texts.alerts.loudOnlyAlarm,
        AlertPriority.Warning to if (channel.curated) texts.alerts.loudFromWarning else texts.alerts.loudFromImportant,
        AlertPriority.Info to texts.alerts.loudAlways,
        null to texts.alerts.loudNever,
    )
    val soundNames = mapOf(
        AlertSound.Alarm to texts.alerts.soundAlarm,
        AlertSound.Official to texts.alerts.soundOfficial,
        AlertSound.Warning to texts.alerts.soundWarning,
        AlertSound.Hint to texts.alerts.soundHint,
    )
    // The official sound stays with official warnings, the calmer warning with own channels.
    val soundOptions = AlertSound.choicesFor(channel.curated).map { it to soundNames.getValue(it) }
    val currentSound = AlertSound.allowedFor(channel.curated, settings.sound)
    AppSection(texts.alerts.onThisDevice, footer = texts.alerts.loudFooter.takeIf { settings.notify }) {
        AppRow(
            title = texts.alerts.notifications,
            subtitle = if (settings.notify) texts.alerts.notificationsOn else texts.alerts.notificationsOff,
            icon = AppIcons.Glocke,
            trailing = { Switch(checked = settings.notify, onCheckedChange = { alerts.setSettings(channel, settings.copy(notify = it)) }) },
        )
        if (settings.notify) {
            AppRow(title = texts.alerts.loudFrom, subtitle = loudOptions.first { it.first == settings.loudFrom }.second, icon = AppIcons.Regler, chevron = true, onClick = { loudChoice = true })
            AppRow(title = texts.alerts.sound, subtitle = soundNames.getValue(currentSound), icon = AppIcons.Abspielen, chevron = true, onClick = { soundChoice = true })
        }
        AppRow(
            title = texts.alerts.testAlarm,
            subtitle = texts.alerts.testAlarmSubtitle,
            icon = AppIcons.Achtung,
            chevron = true,
            onClick = { scope.launch { problems = alerts.testAlarm(channel) } },
        )
    }
    if (loudChoice) ChoiceDialog(texts.alerts.loudFrom, loudOptions, settings.loudFrom, onDismiss = { loudChoice = false }) {
        alerts.setSettings(channel, settings.copy(loudFrom = it))
    }
    if (soundChoice) ChoiceDialog(texts.alerts.sound, soundOptions, currentSound, onDismiss = { soundChoice = false }) {
        alerts.setSettings(channel, settings.copy(sound = it))
    }
    problems?.let { TestAlarmDialog(alerts, it, onDismiss = { problems = null }) }
}

/** Test alarm has run: everything ready, or what is missing, each with a button to the matching setting. */
@Composable
private fun TestAlarmDialog(alerts: Alerts, problems: List<AlarmProblem>, onDismiss: () -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.alerts.testTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val fixable = problems.filter { it != AlarmProblem.SilentSwitch }
                Text(if (fixable.isEmpty()) texts.alerts.testAllGood else texts.alerts.testMissing)
                problems.forEach { problem ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(problemText(problem), style = MaterialTheme.typography.bodyMedium)
                        if (problem != AlarmProblem.SilentSwitch) {
                            AppButton(texts.setup.openSetting, { alerts.fix(problem) }, kind = ButtonKind.Quiet)
                        }
                    }
                }
            }
        },
        confirmButton = { AppButton(texts.common.ok, onDismiss) },
    )
}

private fun problemText(problem: AlarmProblem): String = when (problem) {
    AlarmProblem.NotificationsOff -> texts.alerts.problemNotifications
    AlarmProblem.DoNotDisturbAccess -> texts.alerts.problemDoNotDisturb
    AlarmProblem.BatteryOptimized -> texts.alerts.problemBattery
    AlarmProblem.FullScreenNotAllowed -> texts.alerts.problemFullScreen
    AlarmProblem.TimeSensitiveOff -> texts.alerts.problemTimeSensitive
    AlarmProblem.SilentSwitch -> texts.alerts.problemSilentSwitch
}

/** Enter a name, done (ADR 0062 §9); afterwards the new channel opens. */
@Composable
private fun CreateChannelDialog(alerts: Alerts, onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.alerts.createTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(texts.alerts.createText)
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(texts.alerts.channelName) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            AppButton(texts.alerts.create, {
                busy = true
                scope.launch {
                    val channel = alerts.createChannel(name)
                    busy = false
                    if (channel != null) {
                        onDismiss()
                        onCreated(channel.id)
                    }
                }
            }, enabled = name.isNotBlank() && !busy)
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/** Alert from the app: priority, title, text, link. Kept simple, without attachments. */
@Composable
private fun ComposeDialog(alerts: Alerts, channel: AlertChannel, onDismiss: () -> Unit) {
    var priority by remember { mutableStateOf(AlertPriority.Info) }
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.alerts.composeTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(texts.alerts.priority, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlertPriority.entries.forEach { p ->
                        FilterChip(
                            selected = p == priority,
                            onClick = { priority = p },
                            label = { Text(priorityLabel(p, channel.curated)) },
                        )
                    }
                }
                OutlinedTextField(title, { title = it }, singleLine = true, label = { Text(texts.alerts.composeTitleField) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(text, { text = it }, minLines = 2, label = { Text(texts.alerts.composeText) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, singleLine = true, label = { Text(texts.alerts.composeUrl) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            AppButton(texts.chat.send, {
                busy = true
                scope.launch {
                    val ok = alerts.send(channel.id, priority, title, text, url)
                    busy = false
                    if (ok) onDismiss()
                }
            }, enabled = text.isNotBlank() && !busy)
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/** Who has subscribed to one's own channel (ADR 0062 §11): name and since when, tap to remove. */
@Composable
private fun SubscribersSection(alerts: Alerts, channel: AlertChannel) {
    var removing by remember { mutableStateOf<AlertSubscriber?>(null) }
    val scope = rememberCoroutineScope()
    AppSection(texts.alerts.subscribersSection, footer = texts.alerts.subscribersFooter) {
        if (channel.subscribers.isEmpty()) AppRow(title = texts.alerts.noSubscribers)
        channel.subscribers.forEach { s ->
            AppRow(
                title = s.name.ifBlank { texts.alerts.someone },
                subtitle = texts.alerts.subscriberSince(formatShortDate(s.sinceMillis)),
                icon = AppIcons.Person,
                onClick = { removing = s },
            )
        }
    }
    removing?.let { s ->
        val name = s.name.ifBlank { texts.alerts.someone }
        AppDialog(
            onDismissRequest = { removing = null },
            title = { Text(texts.alerts.removeSubscriberTitle(name)) },
            text = { Text(texts.alerts.removeSubscriberText(name)) },
            confirmButton = {
                AppButton(texts.alerts.removeSubscriber, {
                    removing = null
                    scope.launch { alerts.removeSubscriber(channel.id, s) }
                }, kind = ButtonKind.Danger)
            },
            dismissButton = { AppButton(texts.common.cancel, { removing = null }, kind = ButtonKind.Quiet) },
        )
    }
}

/** Addresses for devices under „Erweitert“ (ADR 0062 §4, §9): create, copy, examples, revoke. */
@Composable
private fun AddressesSection(alerts: Alerts, channel: AlertChannel) {
    var expanded by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var moreInfo by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AppSection {
        ExpandRow(texts.common.advanced, expanded, icon = AppIcons.Regler) { expanded = !expanded }
    }
    if (!expanded) return
    AppSection(texts.alerts.addressesSection, footer = texts.alerts.addressesFooter) {
        if (channel.ingress.isEmpty()) AppRow(title = texts.alerts.noAddress)
        channel.ingress.forEach { ingress -> AddressView(alerts, channel, ingress) }
        AppRow(title = texts.alerts.addAddress, icon = AppIcons.Plus, onClick = { adding = true })
        ExpandRow(texts.common.moreInfo, moreInfo) { moreInfo = !moreInfo }
        if (moreInfo) SectionContent { Text(texts.alerts.relayNote, style = MaterialTheme.typography.bodyMedium) }
    }
    AppSection(footer = texts.alerts.resetLinkText) {
        AppRow(title = texts.alerts.resetLink, icon = AppIcons.Schloss, onClick = { resetting = true })
    }
    if (adding) AddAddressDialog(alerts, channel, onDismiss = { adding = false })
    if (resetting) {
        AppDialog(
            onDismissRequest = { resetting = false },
            title = { Text(texts.alerts.resetLink) },
            text = { Text(texts.alerts.resetLinkText) },
            confirmButton = {
                AppButton(texts.alerts.resetLink, {
                    resetting = false
                    scope.launch { alerts.resetLink(channel.id) }
                }, kind = ButtonKind.Danger)
            },
            dismissButton = { AppButton(texts.common.cancel, { resetting = false }, kind = ButtonKind.Quiet) },
        )
    }
}

@Composable
private fun AddressView(alerts: Alerts, channel: AlertChannel, ingress: AlertIngress) {
    var revoking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SectionContent {
        Text(ingress.label, style = MaterialTheme.typography.titleMedium)
        Text(texts.alerts.addressCreated(formatShortDate(ingress.createdMillis)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CodeBlock(ingress.url)
        Text(texts.alerts.exampleHomeAssistant, style = MaterialTheme.typography.titleSmall)
        CodeBlock(texts.alerts.exampleHomeAssistantCode(ingress.url))
        Text(texts.alerts.exampleCommandLine, style = MaterialTheme.typography.titleSmall)
        CodeBlock(texts.alerts.exampleCommandLineCode(ingress.url))
        AppButton(texts.alerts.revoke, { revoking = true }, kind = ButtonKind.Danger)
    }
    if (revoking) {
        AppDialog(
            onDismissRequest = { revoking = false },
            title = { Text(texts.alerts.revokeTitle(ingress.label)) },
            text = { Text(texts.alerts.revokeText) },
            confirmButton = {
                AppButton(texts.alerts.revoke, {
                    revoking = false
                    scope.launch { alerts.revokeIngress(channel.id, ingress.id) }
                }, kind = ButtonKind.Danger)
            },
            dismissButton = { AppButton(texts.common.cancel, { revoking = false }, kind = ButtonKind.Quiet) },
        )
    }
}

/** Text to copy down (address, examples) with a „Kopieren“ button. */
@Composable
private fun CodeBlock(code: String) {
    @Suppress("DEPRECATION") // LocalClipboard needs platform-specific ClipEntry objects.
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 4.dp)) {
            Text(code, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                AppButton(
                    if (copied) texts.people.copied else texts.common.copy,
                    {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    kind = ButtonKind.Quiet,
                    icon = if (copied) AppIcons.Haken else AppIcons.Kopieren,
                )
            }
        }
    }
}

@Composable
private fun AddAddressDialog(alerts: Alerts, channel: AlertChannel, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.alerts.addAddress) },
        text = {
            OutlinedTextField(label, { label = it }, singleLine = true, label = { Text(texts.alerts.addressLabel) }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            AppButton(texts.alerts.create, {
                busy = true
                scope.launch {
                    alerts.createIngress(channel.id, label.ifBlank { texts.alerts.addressLabelDefault })
                    busy = false
                    onDismiss()
                }
            }, enabled = !busy)
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/** Leave the channel (recipient) or remove it from this device (owner), with a confirmation prompt. */
@Composable
private fun LeaveSection(alerts: Alerts, channel: AlertChannel) {
    var asking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val label = if (channel.owner) texts.alerts.remove else texts.alerts.leave
    AppSection {
        AppRow(title = label, icon = AppIcons.Verlassen, danger = true, onClick = { asking = true })
    }
    if (asking) {
        AppDialog(
            onDismissRequest = { asking = false },
            title = { Text(texts.alerts.leaveTitle(channel.name)) },
            text = { Text(if (channel.owner) texts.alerts.removeText else texts.alerts.leaveText) },
            confirmButton = {
                AppButton(label, {
                    asking = false
                    scope.launch { alerts.leave(channel.id) }
                }, kind = ButtonKind.Danger)
            },
            dismissButton = { AppButton(texts.common.cancel, { asking = false }, kind = ButtonKind.Quiet) },
        )
    }
}

/** Opened channel link: only after „Abonnieren“ does anything happen. */
@Composable
internal fun PendingChannelDialog(alerts: Alerts, pending: Alerts.PendingChannel) {
    AppDialog(
        onDismissRequest = alerts::dismissPending,
        title = { Text(texts.alerts.joinTitle(pending.name)) },
        text = {
            val body = when {
                pending.curated -> texts.alerts.joinOfficialText
                pending.seenByOwner -> texts.alerts.joinSeenText
                else -> texts.alerts.joinText
            }
            Text(body, Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = { AppButton(texts.alerts.join, alerts::acceptPending) },
        dismissButton = { AppButton(texts.alerts.dontJoin, alerts::dismissPending, kind = ButtonKind.Quiet) },
    )
}

/** Android: without access to „Nicht stören“ alarms do not get through there; one sentence, one button (like the battery hint). */
@Composable
internal fun AlarmAccessDialog(alerts: Alerts) {
    AppDialog(
        onDismissRequest = alerts::postponeAccessHint,
        title = { Text(texts.alerts.accessTitle) },
        text = { Text(texts.alerts.accessText) },
        confirmButton = { AppButton(texts.setup.openSetting, alerts::openAccessSettings) },
        dismissButton = { AppButton(texts.common.later, alerts::postponeAccessHint, kind = ButtonKind.Quiet) },
    )
}
