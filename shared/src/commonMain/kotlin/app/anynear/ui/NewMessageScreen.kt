package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.mesh.Conversation

/**
 * „Neue Nachricht“: whom do you want to write to? Circles and contacts to choose from, below them the ways to
 * new people („Kontakt hinzufügen“) and groups („Kreis gründen“). Tapping opens the conversation,
 * an existing one is continued. The same selection serves for passing things on (file from „Meine Ablage“, something
 * shared from another app) with its own [title], [question] and [preview] of what is passed on.
 */
@Composable
internal fun NewMessageScreen(
    controller: AppController,
    onBack: () -> Unit,
    onPick: (Conversation) -> Unit,
    title: String = texts.chat.newMessage,
    question: String = texts.chat.whoToMessage,
    preview: String? = null,
    /** Own circle whose invitation is passed on: left out, and whoever is already in it comes last and dimmed. */
    invitedCircle: String? = null,
) {
    val circles by controller.circles.collectAsState()
    val people by controller.people.collectAsState()
    // Recompute as soon as someone signs up.
    val circleMembers by controller.circleMembers.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val circleNames = circles.associate { it.id to it.name }
    // You can no longer write into a circle you were removed from.
    val shownCircles = circles.filter { !it.removed && it.id != invitedCircle && matchesSearch(query, it.name) }
    val supportPeer by controller.supportPeer.collectAsState()
    // The support identity is not a contact to write to; questions go through the form (ADR 0065).
    val shownPeople = people.filter { person -> person.peer != supportPeer && matchesSearch(query, person.name, *person.circleIds.map(circleNames::get).toTypedArray()) }
        .sortedBy { invitedCircle in it.circleIds }
    val invitedName = invitedCircle?.let(circleNames::get)

    Column(Modifier.fillMaxSize()) {
        AppHeader(title, leading = { HeaderBackButton(onBack) })
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Text(
                    question,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
                )
            }
            if (preview != null) item {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item { SearchField(query, { query = it }, texts.chat.searchName) }
            if (shownCircles.isEmpty() && shownPeople.isEmpty() && query.isNotBlank()) item { NoSearchResults() }
            if (shownCircles.isNotEmpty()) item {
                AppSection(texts.chat.circles) {
                    shownCircles.forEach { circle ->
                        AppRow(
                            title = circle.name,
                            subtitle = circleSummary(circle, circleMembers[circle.id].orEmpty().size),
                            leading = { CircleAvatar(controller, circle.id, circle.image, 44.dp) },
                            onClickLabel = texts.chat.writeInCircle,
                            onClick = { onPick(Conversation.Circle(circle.id)) },
                        )
                    }
                }
            }
            if (shownPeople.isNotEmpty()) item {
                AppSection(texts.chat.contacts) {
                    shownPeople.forEach { person ->
                        val names = person.circleIds.mapNotNull(circleNames::get)
                        val alreadyIn = invitedName != null && invitedCircle in person.circleIds
                        AppRow(
                            title = person.name,
                            subtitle = when {
                                alreadyIn -> texts.chat.alreadyInCircle(invitedName.orEmpty())
                                names.isEmpty() -> texts.chat.addedByYou
                                else -> names.joinToString(" · ")
                            },
                            muted = alreadyIn,
                            leading = { PersonAvatar(controller, person.peer, person.image, 44.dp) },
                            onClickLabel = texts.chat.writeMessage,
                            onClick = { onPick(Conversation.Direct(person.peer)) },
                        )
                    }
                }
            } else if (query.isBlank()) item {
                Text(
                    texts.chat.noContactsYet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            // One below the other and full width: fully readable and easy to hit even with a large font.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppButton(texts.chat.addContact, { adding = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = AppIcons.Plus)
                    AppButton(texts.chat.startCircle, { creating = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = AppIcons.Plus)
                }
            }
        }
    }

    if (adding) AddContactFlow(controller, onDismiss = { adding = false }, onOpenChat = { onPick(Conversation.Direct(it)) })
    if (creating) CreateCircleDialog(controller, onDismiss = { creating = false }, onCreated = { onPick(Conversation.Circle(it)) })
}
