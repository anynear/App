package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anynear.anmp.core.AnmpLogin
import app.anynear.anmp.core.LoginInfo
import app.anynear.anmp.core.LoginLink
import app.anynear.anmp.core.LoginResult
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.passwords.rememberDeviceCheck
import kotlinx.coroutines.launch

/** Opened or scanned login link (ADR 0122): „Bei … anmelden?“. */
@Composable
internal fun PendingLoginDialog(controller: AppController) {
    val link by controller.pendingLogin.collectAsState()
    link?.let { LoginDialog(controller, it, controller::dismissLogin) }
}

/**
 * Service with its domain, „Dein Name dort“ (profile name as default) and three numbers. Tapping a number asks for the
 * device lock and then confirms; nothing leaves the phone before that. Afterwards a clear answer in the same dialog.
 */
@Composable
internal fun LoginDialog(controller: AppController, link: LoginLink, onDismiss: () -> Unit) {
    var info by remember(link) { mutableStateOf<LoginInfo?>(null) }
    var name by remember(link) { mutableStateOf(controller.loginDefaultName()) }
    var busy by remember(link) { mutableStateOf(false) }
    var error by remember(link) { mutableStateOf<String?>(null) }
    /** The request is used up (wrong number, expired, …): only „Schließen“ is left. */
    var over by remember(link) { mutableStateOf(false) }
    var picked by remember(link) { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(link) {
        val i = controller.loginInfo(link)
        if (i.status == LoginResult.OK) {
            info = i
        } else {
            error = when (i.status) {
                LoginResult.EXPIRED -> texts.login.expired
                LoginResult.TOO_MANY -> texts.login.tooMany
                LoginResult.INVALID -> texts.login.invalid
                else -> texts.login.offline
            }
            over = true
        }
    }

    fun confirm(n: Int) {
        val i = info ?: return
        scope.launch {
            busy = true
            error = null
            val e = controller.confirmLogin(i, n, name)
            busy = false
            if (e == null) {
                controller.showNotice(texts.login.done(i.name))
                onDismiss()
            } else {
                error = e
                // A bad name can be fixed here; everything else needs a new start on the page (or a new try).
                over = e != texts.login.badName && e != texts.login.offline && e != texts.login.tooMany
            }
        }
    }
    val check = rememberDeviceCheck { ok ->
        when (ok) {
            true -> picked?.let(::confirm)
            null -> error = texts.login.needsLock
            false -> Unit
        }
    }

    val i = info
    AppDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (i != null) texts.login.title(i.name) else texts.login.loading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (i == null && error == null) CircularProgressIndicator()
                if (i != null) {
                    Text(i.domain, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (!over) {
                        OutlinedTextField(
                            name,
                            { if (it.length <= AnmpLogin.MAX_NAME * 2) name = it },
                            singleLine = true,
                            enabled = !busy,
                            label = { Text(texts.login.nameLabel) },
                            supportingText = { Text(texts.login.nameHint) },
                            isError = AnmpLogin.cleanName(name) == null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(texts.login.pickNumber)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            i.choices.forEach { n ->
                                AppButton(
                                    n.toString(),
                                    {
                                        if (AnmpLogin.cleanName(name) == null) {
                                            error = texts.login.badName
                                        } else {
                                            picked = n
                                            check(texts.login.reason(i.name))
                                        }
                                    },
                                    kind = ButtonKind.Secondary,
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f).height(MainButtonHeight),
                                )
                            }
                        }
                        Text(texts.login.footnote, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (busy) {
                    Text(texts.login.confirming)
                    CircularProgressIndicator()
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (over) AppButton(texts.common.close, onDismiss, kind = ButtonKind.Primary)
        },
        dismissButton = {
            if (!over) AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet, enabled = !busy)
        },
    )
}
