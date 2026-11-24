package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.anynear.anmp.core.PlaceSharing
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.location.Between
import app.anynear.location.ShareLevel
import app.anynear.location.SharedPlace
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.ChatSpot
import app.anynear.mesh.Conversation
import app.anynear.people.MemberDirectory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** The sharing with [personId] as the core keeps it (ADR 0123), kept current. */
@Composable
internal fun rememberSharing(controller: AppController, personId: String): PlaceSharing? {
    val all by (controller.sharings?.all ?: remember { MutableStateFlow(emptyMap<String, PlaceSharing>()) }).collectAsState()
    return all[personId]
}

/** Where the person and [personId] stand (ADR 0123 §7): what each shows, the sharing and an open question. */
@Composable
internal fun rememberBetween(controller: AppController, personId: String): Between? {
    val sharing = controller.locationSharing ?: return null
    val s = rememberSharing(controller, personId)
    val persons by sharing.persons.collectAsState()
    val places by (controller.placeSharer?.places ?: remember { MutableStateFlow(emptyMap<String, SharedPlace>()) }).collectAsState()
    // Read so that the state follows a change on another own device.
    sharing.circles.collectAsState().value
    sharing.default.collectAsState().value
    sharing.paused.collectAsState().value
    sharing.approved.collectAsState().value
    persons.size
    val person = controller.people.collectAsState().value.firstOrNull { it.deviceId == personId }
    val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val theirs = places[personId]?.takeIf { it.untilMillis == 0L || it.untilMillis > now }?.level
    val circles = person?.circleIds.orEmpty().filter { it != MemberDirectory.DIRECT_CONTACTS }
    return sharing.between(personId, circles, theirs, s?.takeIf { it.open }?.let { ShareLevel.ofWire(it.asked) }, s?.askedByUs == true)
}

/**
 * „Standort freigeben“ (ADR 0123 §2, §7): first what holds and what is open, with the buttons for it, then the levels
 * one can ask for. Nothing goes out before the other person accepts.
 */
@Composable
internal fun SharingDialog(controller: AppController, personId: String, onDismiss: () -> Unit) {
    val mt = texts.map
    val name = controller.people.collectAsState().value.firstOrNull { it.deviceId == personId }?.name.orEmpty()
    val b = rememberBetween(controller, personId) ?: return
    var level by remember(b.suggested) { mutableStateOf(b.suggested ?: ShareLevel.Area) }
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(mt.mutualTitle(name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SharingState(controller, personId, name, b)
                if (b.askable.isNotEmpty()) {
                    Text(if (b.exists) mt.sharingOther else mt.mutualText, style = MaterialTheme.typography.bodyMedium)
                    listOf(
                        Triple(ShareLevel.Area, mt.mutualArea, mt.mutualAreaText),
                        Triple(ShareLevel.Exact, mt.mutualExact, mt.mutualExactText),
                    ).filter { it.first in b.askable }.forEach { (l, title, text) ->
                        AppRow(title = title, subtitle = text, onClick = { level = l }, leading = { RadioButton(selected = level == l, onClick = { level = l }) })
                    }
                    if (b.shared != null) Text(mt.sharingKeeps, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (b.askable.isEmpty()) AppButton(texts.common.done, onDismiss, kind = ButtonKind.Quiet)
            else AppButton(mt.mutualSend, {
                controller.askSharing(personId, if (level in b.askable) level else b.suggested ?: level)
                onDismiss()
            }, kind = ButtonKind.Quiet)
        },
        dismissButton = if (b.askable.isEmpty()) null else ({ AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) }),
    )
}

/**
 * What holds between the person and [personId], with the buttons for it (ADR 0123 §7): the sharing with „Beenden“, an
 * open question with „Annehmen“ and „Ablehnen“ or „Zurückziehen“, and what each shows the other right now.
 */
@Composable
internal fun SharingState(controller: AppController, personId: String, name: String, b: Between) {
    val mt = texts.map
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        b.shared?.let { shared ->
            Text(mt.mutualBoth(name, shared == ShareLevel.Exact), style = MaterialTheme.typography.titleSmall)
            AppButton(mt.sharingEnd, { controller.endSharing(personId) }, kind = ButtonKind.Quiet)
        }
        b.asked?.let { asked ->
            if (b.askedByUs) {
                Text(mt.sharingAskedOwn(name, asked == ShareLevel.Exact), style = MaterialTheme.typography.titleSmall)
                Text(mt.sharingWaiting, style = MaterialTheme.typography.labelMedium, color = quiet)
                AppButton(mt.sharingWithdraw, { controller.withdrawSharing(personId) }, kind = ButtonKind.Quiet)
            } else {
                Text(mt.mutualAskBy(name, asked == ShareLevel.Exact), style = MaterialTheme.typography.titleSmall)
                SharingAnswerButtons(controller, personId)
            }
        }
        Text(mt.mutualMine(name, levelName(b.mine)), style = MaterialTheme.typography.bodyMedium)
        Text(mt.mutualTheirs(name, levelName(b.theirs)), style = MaterialTheme.typography.bodyMedium)
    }
}

/** „Annehmen“ and „Ablehnen“ for the question of [personId], the same everywhere (ADR 0123 §1). */
@Composable
internal fun SharingAnswerButtons(controller: AppController, personId: String) {
    val mt = texts.map
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppButton(mt.sharingAccept, { controller.answerSharing(personId, true) }, kind = ButtonKind.Secondary, icon = AppIcons.Haken)
        AppButton(mt.sharingDecline, { controller.answerSharing(personId, false) }, kind = ButtonKind.Quiet)
    }
}

/**
 * A card about sharing the place in the chat (ADR 0123 §5): the question, its answer, the withdrawal and the end, for
 * both sides alike. Only the newest open question has buttons.
 */
@Composable
internal fun SharingCard(controller: AppController, message: ChatMessage, spot: ChatSpot, senderName: String?, color: Color, footer: @Composable ColumnScope.() -> Unit) {
    val mt = texts.map
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val who = senderName ?: controller.findPerson(message.fromPeer.orEmpty())?.name ?: controller.senderLabel(message)
    val other = (message.conversation as? Conversation.Direct)?.let { controller.peerName(it.peer) } ?: who
    val personId = controller.sharingPerson(message.conversation)
    val exact = spot.level == ShareLevel.Exact.wire
    val all by controller.messages.collectAsState()
    val sharing = personId?.let { rememberSharing(controller, it) }
    val (title, icon, accent) = when (spot.kind) {
        ChatSpot.SHARE -> Triple(if (message.outgoing) mt.sharingAskedOwn(other, exact) else mt.mutualAskBy(who, exact), AppIcons.Karte, color)
        ChatSpot.SHARED -> {
            // An answer follows a question of the other side; older apps also sent it later to lower or end.
            val answers = remember(all, message.localId) {
                all.lastOrNull {
                    it.conversation == message.conversation && it.timestampMillis <= message.timestampMillis && it.localId != message.localId &&
                        it.spot?.kind in ChatSpot.SHARE..ChatSpot.WITHDRAWN
                }?.let { it.spot?.kind == ChatSpot.SHARE && it.outgoing != message.outgoing } == true
            }
            val yes = (spot.level ?: 0) > 0
            when {
                !answers -> Triple(if (message.outgoing) mt.mutualLoweredOwn(other, yes) else mt.mutualLoweredBy(who, yes), AppIcons.Karte, color)
                yes -> Triple(if (message.outgoing) mt.sharingAcceptedOwn(exact) else mt.sharingAcceptedBy(who, exact), AppIcons.Haken, AppTheme.colors.safe)
                else -> Triple(if (message.outgoing) mt.sharingDeclinedOwn else mt.sharingDeclinedBy(who), AppIcons.Schliessen, color)
            }
        }
        ChatSpot.ENDED -> Triple(if (message.outgoing) mt.sharingEndedOwn else mt.sharingEndedBy(who), AppIcons.Schliessen, color)
        else -> Triple(if (message.outgoing) mt.sharingWithdrawnOwn else mt.sharingWithdrawnBy(who), AppIcons.Schliessen, color)
    }
    SystemCard(icon, title, accent = accent, footer = footer) {
        if (spot.kind != ChatSpot.SHARE || personId == null) return@SystemCard
        // The newest question in this chat that is still open: the buttons; an older one says nothing more.
        val newest = remember(all, message.localId) {
            all.lastOrNull { it.conversation == message.conversation && it.spot?.kind == ChatSpot.SHARE }?.localId == message.localId
        }
        val open = newest && sharing?.open == true && sharing.askedByUs == message.outgoing && sharing.asked == spot.level
        if (!open) return@SystemCard
        if (message.outgoing) {
            Text(mt.sharingWaiting, style = MaterialTheme.typography.labelMedium, color = quiet)
            AppButton(mt.sharingWithdraw, { controller.withdrawSharing(personId) }, kind = ButtonKind.Quiet)
        } else {
            if (sharing.active) Text(mt.sharingKeeps, style = MaterialTheme.typography.labelMedium, color = quiet)
            SharingAnswerButtons(controller, personId)
        }
    }
}

/**
 * A step about SOS contacts with the person of the chat (ADR 0123 §5), in the middle like the other cards. The newest
 * open question of the other person carries „Annehmen“ and „Ablehnen“.
 */
@Composable
internal fun SosEventCard(controller: AppController, message: ChatMessage, card: Int) {
    val sos = controller.sos
    val personId = controller.sharingPerson(message.conversation)
    val asks by (sos?.asks ?: remember { MutableStateFlow(emptyList<app.anynear.anmp.core.SosAsk>()) }).collectAsState()
    val all by controller.messages.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val good = card == app.anynear.sos.CARD_THEY_YES || card == app.anynear.sos.CARD_WE_YES
    val accent = if (good) AppTheme.colors.safe else MaterialTheme.colorScheme.onSurface
    // The current name, also after „Namen ändern“; the stored text keeps the name of the moment it came.
    val people by controller.people.collectAsState()
    val name = personId?.let { id -> people.firstOrNull { it.deviceId == id }?.name }
    SystemCard(
        if (good) AppIcons.Haken else AppIcons.Person,
        name?.let { texts.sos.card(card, it) } ?: message.text,
        accent = accent,
        footer = { SystemCardTime(message.timestampMillis) },
    ) {
        if (card != app.anynear.sos.CARD_ASKED_US || sos == null || personId == null) return@SystemCard
        val newest = remember(all, message.localId) {
            all.lastOrNull { it.conversation == message.conversation && it.system?.sos == app.anynear.sos.CARD_ASKED_US }?.localId == message.localId
        }
        if (!newest || asks.none { it.person == personId }) return@SystemCard
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(texts.map.sharingAccept, { scope.launch { sos.answer(personId, true) } }, kind = ButtonKind.Secondary, icon = AppIcons.Haken)
            AppButton(texts.map.sharingDecline, { scope.launch { sos.answer(personId, false) } }, kind = ButtonKind.Quiet)
        }
    }
}
