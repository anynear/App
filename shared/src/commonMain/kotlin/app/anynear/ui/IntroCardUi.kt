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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.mesh.ContactIntro
import app.anynear.people.MemberDirectory
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * An introduced contact in a bubble (ADR 0116), built like [InviteCardBox]: one sentence, the state, one action.
 * Incoming: „Hinzufügen“ asks first, then the introducer passes the request on; the introduced person still decides.
 * [sender]: name of whoever introduced, [own]: we introduced.
 */
@Composable
internal fun IntroCardBox(controller: AppController, intro: ContactIntro, sender: String, own: Boolean) {
    val members by controller.members.collectAsState()
    val sent by controller.sentRequests.collectAsState()
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    val known = members.any { it.circleId == MemberDirectory.DIRECT_CONTACTS && controller.personOfDevice(it.deviceId) == intro.person }
    val pending = sent.any { it.person == intro.person }
    val expired = intro.until < Clock.System.now().toEpochMilliseconds()
    val status = when {
        own -> null
        known -> texts.people.alreadyContact
        pending -> texts.people.requestPending
        expired -> texts.people.introExpired
        else -> null
    }
    val action = if (!own && !known && !pending && !expired) texts.people.introAdd else null
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .background(scheme.surface, shape)
            .border(1.dp, scheme.outlineVariant, shape)
            .then(if (action != null) Modifier.clickable(onClickLabel = action, role = Role.Button) { confirming = true } else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(scheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Person, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (own) texts.people.introCardOwn(intro.name) else texts.people.introCardIncoming(sender, intro.name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                )
                status?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)) }
            }
        }
        action?.let {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).background(scheme.primary, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(it, style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
            }
        }
    }
    if (confirming) {
        AppDialog(
            onDismissRequest = { confirming = false },
            title = { Text(texts.setup.addContactConfirmTitle(intro.name)) },
            text = { Text(texts.people.introConfirmText(intro.name), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                AppButton(texts.people.introAdd, {
                    confirming = false
                    scope.launch { controller.acceptIntro(intro)?.let(controller::showNotice) }
                })
            },
            dismissButton = { AppButton(texts.setup.dontAdd, { confirming = false }, kind = ButtonKind.Quiet) },
        )
    }
}
