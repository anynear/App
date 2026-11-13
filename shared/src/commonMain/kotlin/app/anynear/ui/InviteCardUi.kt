package app.anynear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.chats.InviteCard
import app.anynear.chats.InviteKind
import app.anynear.chats.InviteLink
import app.anynear.chats.inviteCardView
import kotlinx.coroutines.flow.MutableStateFlow

private val NoChannels = MutableStateFlow(emptyList<Any>())

/**
 * Invitation in a bubble as a card instead of a link: one sentence with the name from the bubble, below it the
 * state and the one action. For screen readers one element (sentence, state, action); the whole card is
 * tappable. [sender] is the name of the sender, [own] an own message.
 */
@Composable
internal fun InviteCardBox(controller: AppController, link: InviteLink, sender: String, own: Boolean) {
    // Check again as soon as you subscribe, join or add someone.
    val channels by (controller.alerts?.channels ?: NoChannels).collectAsState()
    val circles by controller.circles.collectAsState()
    val members by controller.members.collectAsState()
    val card by produceState<InviteCard?>(null, link.url, channels, circles, members) { value = controller.inviteCard(link) }
    val resolved = card ?: return
    val view = inviteCardView(resolved, sender, own)
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val broken = resolved == InviteCard.Broken
    Column(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .background(scheme.surface, shape)
            .border(1.dp, scheme.outlineVariant, shape)
            .then(
                if (view.action != null) {
                    Modifier.clickable(onClickLabel = view.action, role = Role.Button) { controller.openInviteCard(link) }
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {}
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).background(if (broken) scheme.surfaceVariant else scheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        broken -> AppIcons.Info
                        link.kind == InviteKind.Channel -> AppIcons.Glocke
                        link.kind == InviteKind.Contact -> AppIcons.Person
                        link.kind == InviteKind.Device -> AppIcons.QrCode
                        else -> AppIcons.Kreis
                    },
                    contentDescription = null,
                    tint = if (broken) scheme.onSurfaceVariant else scheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    view.sentence,
                    style = if (broken) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    color = if (broken) scheme.onSurfaceVariant else scheme.onSurface,
                )
                view.status?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        // Looks like a button; the action lies on the whole card (one tap target, one element).
        view.action?.let { action ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .background(if (resolved.isOpen()) scheme.surfaceVariant else scheme.primary, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    action,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (resolved.isOpen()) scheme.onSurfaceVariant else scheme.onPrimary,
                )
            }
        }
    }
}

/** Already in: „Öffnen“ quietly instead of as the main button. */
private fun InviteCard.isOpen(): Boolean = when (this) {
    is InviteCard.Channel -> subscribed
    is InviteCard.Contact -> known
    is InviteCard.Circle -> member
    is InviteCard.Device -> added || self
    InviteCard.Broken -> false
}
