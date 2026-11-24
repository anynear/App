package app.anynear.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.mesh.ContactIntro
import app.anynear.people.MemberDirectory
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * An introduced contact (ADR 0116) as a system card: one sentence, the state, one action. Incoming: „Hinzufügen“ asks
 * first, then the introducer passes the request on; the introduced person still decides. [sender]: name of whoever
 * introduced, [own]: we introduced.
 */
@Composable
internal fun IntroCard(controller: AppController, intro: ContactIntro, sender: String, own: Boolean, footer: @Composable ColumnScope.() -> Unit) {
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
    SystemCard(
        AppIcons.Person,
        if (own) texts.people.introCardOwn(intro.name) else texts.people.introCardIncoming(sender, intro.name),
        footer = footer,
    ) {
        status?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        action?.let { AppButton(it, { confirming = true }, kind = ButtonKind.Secondary, icon = AppIcons.Person) }
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
