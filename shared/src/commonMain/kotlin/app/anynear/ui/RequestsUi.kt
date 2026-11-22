package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anynear.anmp.core.PlaceSharing
import app.anynear.anmp.core.SosAsk
import app.anynear.anmp.core.SosContact
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.location.ShareLevel
import app.anynear.mesh.Conversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** One open request (ADR 0123 §6): who, what about, and whether the own person asked. */
internal sealed interface OpenRequest {
    val person: String

    data class Contact(override val person: String, val name: String, val origin: String) : OpenRequest
    data class SentContact(override val person: String, val name: String) : OpenRequest
    data class Location(override val person: String, val level: ShareLevel, val byUs: Boolean) : OpenRequest
    data class Sos(override val person: String, val byUs: Boolean) : OpenRequest
}

/** Everything open, to the person and by it (ADR 0123 §6), kept current. */
@Composable
internal fun rememberOpenRequests(controller: AppController): List<OpenRequest> {
    val contacts by controller.contactRequests.collectAsState()
    val sent by controller.sentRequests.collectAsState()
    val sharings by (controller.sharings?.all ?: remember { MutableStateFlow(emptyMap<String, PlaceSharing>()) }).collectAsState()
    val sosAsks by (controller.sos?.asks ?: remember { MutableStateFlow(emptyList<SosAsk>()) }).collectAsState()
    val sosContacts by (controller.sos?.contacts ?: remember { MutableStateFlow(emptyList<SosContact>()) }).collectAsState()
    val helpers = controller.sos?.helpers?.collectAsState()?.value
    return buildList {
        contacts.forEach { add(OpenRequest.Contact(it.person, it.name, controller.requestOrigin(it))) }
        sharings.values.filter { it.open && !it.askedByUs }.forEach { add(OpenRequest.Location(it.person, ShareLevel.ofWire(it.asked), false)) }
        sosAsks.forEach { add(OpenRequest.Sos(it.person, false)) }
        sent.forEach { add(OpenRequest.SentContact(it.person, it.name)) }
        sharings.values.filter { it.open && it.askedByUs }.forEach { add(OpenRequest.Location(it.person, ShareLevel.ofWire(it.asked), true)) }
        // Only those chosen alone: a whole circle asked stays on the SOS page, so that it does not fill this list.
        sosContacts.filter { it.state == SosContact.ASKED && helpers?.persons?.contains(it.person) == true }.forEach { add(OpenRequest.Sos(it.person, true)) }
    }
}

/** How many requests wait for the own person's answer, for the line in „Nachrichten“. */
@Composable
internal fun openRequestsToMe(controller: AppController): Int = rememberOpenRequests(controller).count {
    when (it) {
        is OpenRequest.Contact -> true
        is OpenRequest.SentContact -> false
        is OpenRequest.Location -> !it.byUs
        is OpenRequest.Sos -> !it.byUs
    }
}

/**
 * „Anfragen“ (ADR 0123 §6): everything open in one list, contacts, location and SOS alike, with the same buttons.
 * „An dich“ above with „Annehmen“ and „Ablehnen“, „Von dir“ below with „Zurückziehen“.
 */
@Composable
internal fun RequestsScreen(controller: AppController, onBack: () -> Unit, onOpen: (Conversation) -> Unit, onOpenPerson: (String) -> Unit) {
    val pt = texts.people
    val all = rememberOpenRequests(controller)
    val mine = all.filter { it is OpenRequest.SentContact || (it is OpenRequest.Location && it.byUs) || (it is OpenRequest.Sos && it.byUs) }
    val toMe = all - mine.toSet()
    Column(Modifier.fillMaxSize()) {
        AppHeader(pt.requestsPage, leading = { HeaderBackButton(onBack) })
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (all.isEmpty()) Text(pt.requestsNone, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (toMe.isNotEmpty()) AppSection(pt.requestsToYou) { toMe.forEach { RequestRow(controller, it, onOpen, onOpenPerson) } }
            if (mine.isNotEmpty()) AppSection(pt.requestsFromYou) { mine.forEach { RequestRow(controller, it, onOpen, onOpenPerson) } }
        }
    }
}

/** One request: picture, name, what it is about, and its buttons; the same in the list and at the contact page. */
@Composable
internal fun RequestRow(controller: AppController, r: OpenRequest, onOpen: (Conversation) -> Unit = {}, onOpenPerson: ((String) -> Unit)? = null) {
    val pt = texts.people
    val mt = texts.map
    val scope = rememberCoroutineScope()
    val people by controller.people.collectAsState()
    val person = people.firstOrNull { it.deviceId == r.person }
    val name = when (r) {
        is OpenRequest.Contact -> r.name
        is OpenRequest.SentContact -> r.name
        else -> person?.name ?: controller.sos?.name(r.person).orEmpty()
    }
    val what = when (r) {
        is OpenRequest.Contact -> pt.requestAboutContact
        is OpenRequest.SentContact -> pt.requestAboutContact
        is OpenRequest.Location -> pt.requestAboutLocation(r.level == ShareLevel.Exact)
        is OpenRequest.Sos -> if (r.byUs) pt.requestAboutSosOwn else pt.requestAboutSos
    }
    val byUs = r is OpenRequest.SentContact || (r is OpenRequest.Location && r.byUs) || (r is OpenRequest.Sos && r.byUs)
    var busy by remember(r) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppRow(
            title = name, subtitle = if (byUs) "$what · ${mt.sharingWaiting}" else what,
            leading = { person?.let { PersonAvatar(controller, it.peer, it.image, 40.dp) } },
            chevron = onOpenPerson != null && person != null,
            onClick = if (onOpenPerson != null && person != null) ({ onOpenPerson(person.peer) }) else null,
        )
        Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (r) {
                is OpenRequest.Contact -> {
                    AppButton(mt.sharingAccept, {
                        busy = true
                        scope.launch { controller.acceptContactRequestNow(r.person)?.let(onOpen); busy = false }
                    }, kind = ButtonKind.Secondary, icon = AppIcons.Haken, enabled = !busy)
                    AppButton(mt.sharingDecline, { controller.declineContactRequest(r.person) }, kind = ButtonKind.Quiet, enabled = !busy)
                }
                is OpenRequest.SentContact -> Unit
                is OpenRequest.Location -> if (r.byUs) {
                    AppButton(mt.sharingWithdraw, { controller.withdrawSharing(r.person) }, kind = ButtonKind.Quiet)
                } else {
                    SharingAnswerButtons(controller, r.person)
                }
                is OpenRequest.Sos -> controller.sos?.let { sos ->
                    if (r.byUs) {
                        AppButton(mt.sharingWithdraw, { scope.launch { sos.endContact(r.person) } }, kind = ButtonKind.Quiet)
                    } else {
                        AppButton(mt.sharingAccept, { scope.launch { sos.answer(r.person, true) } }, kind = ButtonKind.Secondary, icon = AppIcons.Haken)
                        AppButton(mt.sharingDecline, { scope.launch { sos.answer(r.person, false) } }, kind = ButtonKind.Quiet)
                    }
                }
            }
        }
    }
}

/**
 * „Freigaben“ at the page of a person (ADR 0123 §7): the sharing of the place with „Beenden“ or „Standort freigeben“,
 * SOS in both directions with „Beenden“ or „Als SOS-Kontakt fragen“, and what is open with this person.
 */
@Composable
internal fun PersonSharingsSection(controller: AppController, personId: String, name: String) {
    val pt = texts.people
    val mt = texts.map
    val scope = rememberCoroutineScope()
    val sos = controller.sos
    val b = rememberBetween(controller, personId)
    val contacts by (sos?.contacts ?: remember { MutableStateFlow(emptyList<SosContact>()) }).collectAsState()
    val agreed by (sos?.agreed ?: remember { MutableStateFlow(emptyList<String>()) }).collectAsState()
    val asks by (sos?.asks ?: remember { MutableStateFlow(emptyList<SosAsk>()) }).collectAsState()
    var asking by remember { mutableStateOf(false) }
    val ours = contacts.firstOrNull { it.person == personId }
    AppSection(pt.sharingsTitle, footer = pt.sharingsFooter) {
        SectionContent {
            // The place: what holds, what is open, and the way to ask.
            if (b != null) {
                if (b.exists) SharingState(controller, personId, name, b)
                if (b.askable.isNotEmpty()) AppButton(mt.mutualStart, { asking = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet, icon = AppIcons.Karte)
            }
            // SOS, in both directions.
            when (ours?.state) {
                SosContact.AGREED -> {
                    Text(texts.sos.card(app.anynear.sos.CARD_THEY_YES, name), style = MaterialTheme.typography.titleSmall)
                    AppButton(mt.sharingEnd, { scope.launch { sos?.endContact(personId) } }, kind = ButtonKind.Quiet)
                }
                SosContact.ASKED -> {
                    Text(texts.sos.card(app.anynear.sos.CARD_WE_ASKED, name), style = MaterialTheme.typography.titleSmall)
                    AppButton(mt.sharingWithdraw, { scope.launch { sos?.endContact(personId) } }, kind = ButtonKind.Quiet)
                }
                else -> if (sos != null) AppButton(pt.askAsSos, { scope.launch { sos.askPerson(personId) } }, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet, icon = AppIcons.Person)
            }
            if (personId in agreed) {
                Text(texts.sos.card(app.anynear.sos.CARD_WE_YES, name), style = MaterialTheme.typography.titleSmall)
                AppButton(mt.sharingEnd, { scope.launch { sos?.answer(personId, false) } }, kind = ButtonKind.Quiet)
            }
            if (asks.any { it.person == personId }) RequestRow(controller, OpenRequest.Sos(personId, false))
        }
    }
    if (asking) SharingDialog(controller, personId, onDismiss = { asking = false })
}
