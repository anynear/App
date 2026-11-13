package app.anynear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.passwords.Login
import app.anynear.passwords.LoginMatch
import app.anynear.passwords.Otp
import app.anynear.passwords.PasswordState
import app.anynear.passwords.PasswordStore
import app.anynear.passwords.checkAutofillEnabled
import app.anynear.passwords.copySecret
import app.anynear.passwords.openAutofillSettings
import app.anynear.passwords.openDeviceLockSettings
import app.anynear.passwords.passkeysSupported
import app.anynear.passwords.rememberPasswordUnlock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Passwords (ADR 0039): own area, reachable from „Ablage“ and „Mehr“. It is opened only with the
// device lock; if you leave the area or the app goes into the background, AppController locks it after a
// grace period (ADR 0048 item 6).

/** Start page of the area: depending on the state set up, unlock or the list of logins. */
@Composable
internal fun PasswordsScreen(controller: AppController, onBack: () -> Unit, onOpenLogin: (id: String) -> Unit, onAdd: () -> Unit) {
    val store = controller.passwords ?: return
    val state by store.state.collectAsState()
    val unlock = rememberPasswordUnlock(store::onUnlock)
    var setUpFailed by remember { mutableStateOf(false) }
    // Show the device lock right on opening, once; if cancelled, the button „Entsperren“ remains.
    var asked by rememberSaveable { mutableStateOf(false) }
    var settingUp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        store.refresh()
        controller.refreshPasswords()
    }
    LaunchedEffect(state) {
        if (state == PasswordState.Locked && !asked) {
            asked = true
            unlock(texts.passwords.unlockReason)
        }
    }
    Column(Modifier.fillMaxSize()) {
        AppHeader(texts.passwords.title, leading = { HeaderBackButton(onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (val s = state) {
                PasswordState.NoDeviceLock -> {
                    // Back from the system setting: continue automatically as soon as the lock exists.
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(1500)
                            store.refresh()
                        }
                    }
                    Text(texts.passwords.noDeviceLockTitle, style = MaterialTheme.typography.titleMedium)
                    Text(texts.passwords.noDeviceLockText, style = MaterialTheme.typography.bodyLarge)
                    AppButton(texts.passwords.openDeviceLockSettings, ::openDeviceLockSettings, Modifier.fillMaxWidth())
                }
                PasswordState.NotSetUp -> {
                    Text(texts.passwords.intro, style = MaterialTheme.typography.bodyLarge)
                    AppSection(texts.passwords.setUpTitle) {
                        SectionContent {
                            Text(texts.passwords.setUpText, style = MaterialTheme.typography.bodyMedium)
                            Text(texts.passwords.setUpBackup, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (setUpFailed) Text(texts.passwords.setUpFailed, color = MaterialTheme.colorScheme.error)
                    // Look at the relays first: if other own devices already have logins, their key comes (ADR 0048).
                    AppButton(texts.passwords.setUp, {
                        if (settingUp) return@AppButton
                        settingUp = true
                        scope.launch {
                            val ok = controller.setUpPasswords()
                            settingUp = false
                            setUpFailed = !ok && store.state.value == PasswordState.NotSetUp
                            if (ok) {
                                asked = true
                                unlock(texts.passwords.unlockReason)
                            }
                        }
                    }, Modifier.fillMaxWidth(), icon = AppIcons.Schluessel)
                }
                PasswordState.Locked -> {
                    Text(texts.passwords.lockedText, style = MaterialTheme.typography.bodyLarge)
                    AppButton(texts.passwords.unlock, { unlock(texts.passwords.unlockReason) }, Modifier.fillMaxWidth(), icon = AppIcons.Schloss)
                }
                PasswordState.WaitingForKey -> UnreadablePasswords(store, texts.passwords.waitingTitle, texts.passwords.waitingText)
                PasswordState.Unreadable -> UnreadablePasswords(store, texts.passwords.unreadableTitle, texts.passwords.unreadableText)
                is PasswordState.Unlocked -> UnlockedPasswords(s.logins, onOpenLogin, onAdd)
            }
            HowPasswordsWork()
        }
    }
}

@Composable
private fun UnreadablePasswords(store: PasswordStore, title: String, text: String) {
    var confirm by remember { mutableStateOf(false) }
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(text, style = MaterialTheme.typography.bodyLarge)
    AppButton(texts.passwords.startOver, { confirm = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
    if (confirm) {
        ConfirmDialog(
            title = texts.passwords.startOverQuestion,
            text = texts.passwords.startOverText,
            confirm = texts.passwords.startOver,
            onConfirm = {
                confirm = false
                store.reset()
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun UnlockedPasswords(logins: List<Login>, onOpenLogin: (String) -> Unit, onAdd: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    if (logins.isNotEmpty()) SearchField(query, { query = it }, texts.passwords.search)
    val found = LoginMatch.sorted(logins.filter { matchesSearch(query, it.website, it.username) }, null)
    AppSection(texts.passwords.logins) {
        found.forEach { login ->
            AppRow(
                title = LoginMatch.displayName(login.website),
                subtitle = login.username.ifEmpty { null },
                icon = AppIcons.Schluessel,
                chevron = true,
                onClick = { onOpenLogin(login.id) },
            )
        }
        when {
            logins.isEmpty() -> SectionContent { Text(texts.passwords.noLogins, style = MaterialTheme.typography.bodyMedium) }
            found.isEmpty() -> NoSearchResults()
        }
    }
    AppButton(texts.passwords.addLogin, onAdd, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = AppIcons.Plus)
    AutofillSection()
}

/** Honest about whether anynear is set up as the password provider; checks regularly while the page is open. */
@Composable
private fun AutofillSection() {
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            checkAutofillEnabled { enabled = it }
            delay(2000)
        }
    }
    AppSection(texts.passwords.autofill, footer = texts.passwords.autofillText) {
        AppRow(
            title = if (enabled == true) texts.passwords.autofillOn else texts.passwords.autofillOff,
            icon = AppIcons.Haken.takeIf { enabled == true } ?: AppIcons.Info,
        )
        SectionContent {
            AppButton(
                if (enabled == true) texts.passwords.changeAutofill else texts.passwords.setUpAutofill,
                ::openAutofillSettings,
                Modifier.fillMaxWidth(),
                kind = if (enabled == true) ButtonKind.Quiet else ButtonKind.Primary,
            )
        }
    }
}

@Composable
private fun HowPasswordsWork() {
    var open by rememberSaveable { mutableStateOf(false) }
    AppSection {
        ExpandRow(texts.passwords.howItWorks, expanded = open, icon = AppIcons.Info) { open = !open }
        if (open) {
            SectionContent {
                listOf(texts.passwords.moreUnlock, texts.passwords.morePasskeys, texts.passwords.moreOffline, texts.passwords.moreBackup, texts.passwords.moreOnlyYou)
                    .forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/** Locked or login gone (background, deleted): back to the start page of the area. */
@Composable
private fun unlockedLogins(controller: AppController, onGone: () -> Unit): List<Login>? {
    val state by (controller.passwords ?: return null).state.collectAsState()
    val logins = (state as? PasswordState.Unlocked)?.logins
    LaunchedEffect(logins == null) { if (logins == null) onGone() }
    return logins
}

/**
 * A login: website, user name, password hidden with „Anzeigen“, copy, edit, delete; plus one-time code
 * and passkeys (ADR 0052).
 */
@Composable
internal fun PasswordLoginScreen(controller: AppController, loginId: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val logins = unlockedLogins(controller, onBack) ?: return
    val login = logins.firstOrNull { it.id == loginId } ?: return LaunchedEffect(Unit) { onBack() }
    var shown by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addCode by remember { mutableStateOf(false) }
    var confirmRemoveCode by remember { mutableStateOf(false) }
    var deletePasskey by remember { mutableStateOf<String?>(null) }
    val name = LoginMatch.displayName(login.website)
    Column(Modifier.fillMaxSize()) {
        AppHeader(name, leading = { HeaderBackButton(onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AppSection {
                AppRow(title = login.website, subtitle = texts.passwords.website, icon = AppIcons.Internet)
                if (login.username.isNotEmpty()) AppRow(title = login.username, subtitle = texts.passwords.username, icon = AppIcons.Person)
                if (login.password.isNotEmpty()) PasswordRow(login.password, shown) { shown = !shown }
                login.otp?.let { Otp.parse(it) }?.let { OtpRow(it) { code -> copySecret(code); controller.showNotice(texts.passwords.otpCopied) } }
                login.passkeys.forEach { passkey ->
                    AppRow(
                        title = texts.passwords.passkey,
                        subtitle = texts.passwords.passkeyCreated(formatShortDate(passkey.created)),
                        icon = AppIcons.Schluessel,
                        onClick = { deletePasskey = passkey.credentialId },
                    )
                }
            }
            if (login.passkeys.isNotEmpty() && !passkeysSupported()) {
                Text(texts.passwords.passkeysNotHere, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (login.password.isNotEmpty()) {
                AppButton(texts.passwords.copy, {
                    copySecret(login.password)
                    controller.showNotice(texts.passwords.copied)
                }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = AppIcons.Kopieren)
            }
            AppSection {
                if (login.otp == null) {
                    AppRow(title = texts.passwords.otpAdd, icon = AppIcons.QrCode, onClick = { addCode = true })
                } else {
                    AppRow(title = texts.passwords.otpRemove, icon = AppIcons.Uhr, onClick = { confirmRemoveCode = true })
                }
                AppRow(title = texts.passwords.edit, icon = AppIcons.Stift, onClick = onEdit)
                AppRow(title = texts.passwords.delete, icon = AppIcons.Schliessen, danger = true, onClick = { confirmDelete = true })
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = texts.passwords.deleteQuestion(name),
            text = texts.passwords.deleteText,
            confirm = texts.passwords.delete,
            onConfirm = {
                confirmDelete = false
                controller.passwords?.delete(login.id)
                onBack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (addCode) {
        OtpAddDialog(
            onAdded = { otp ->
                addCode = false
                controller.passwords?.setOtp(login.id, Otp.uri(otp))
                controller.showNotice(texts.passwords.otpAdded)
            },
            onDismiss = { addCode = false },
        )
    }
    if (confirmRemoveCode) {
        ConfirmDialog(
            title = texts.passwords.otpRemoveQuestion,
            text = texts.passwords.otpRemoveText,
            confirm = texts.passwords.otpRemove,
            onConfirm = {
                confirmRemoveCode = false
                controller.passwords?.setOtp(login.id, null)
            },
            onDismiss = { confirmRemoveCode = false },
        )
    }
    deletePasskey?.let { credentialId ->
        ConfirmDialog(
            title = texts.passwords.passkeyDeleteQuestion,
            text = texts.passwords.passkeyDeleteText,
            confirm = texts.passwords.delete,
            onConfirm = {
                deletePasskey = null
                val lastThing = login.password.isEmpty() && login.passkeys.size == 1
                controller.passwords?.removePasskey(login.id, credentialId)
                if (lastThing) onBack()
            },
            onDismiss = { deletePasskey = null },
        )
    }
}

/**
 * Password row: hidden as dots, „Anzeigen“ or „Verbergen“ with at least 48 dp. With a large font the
 * button is below the password, so that the password does not wrap in the middle of the word.
 */
@Composable
private fun PasswordRow(password: String, shown: Boolean, onToggle: () -> Unit) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    val toggle = @Composable {
        Text(
            if (shown) texts.passwords.hide else texts.passwords.show,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RowIcon(AppIcons.Schloss)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (shown) password else "••••••••",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (shown) Int.MAX_VALUE else 1,
                modifier = if (shown) Modifier else Modifier.semantics { contentDescription = texts.passwords.hiddenPassword },
            )
            Text(texts.passwords.password, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (stacked) Box(Modifier.offset(x = (-16).dp)) { toggle() }
        }
        if (!stacked) toggle()
    }
}

/** Add a login ([loginId] `null`) or edit one: exactly three fields. */
@Composable
internal fun PasswordEditScreen(controller: AppController, loginId: String?, onBack: () -> Unit, onSaved: (id: String) -> Unit) {
    val logins = unlockedLogins(controller, onBack) ?: return
    val existing = loginId?.let { id -> logins.firstOrNull { it.id == id } }
    var website by rememberSaveable { mutableStateOf(existing?.website.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf(existing?.password.orEmpty()) }
    var shown by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        AppHeader(if (existing == null) texts.passwords.addLogin else texts.passwords.editLogin, leading = { HeaderBackButton(onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (existing == null) Text(texts.passwords.addIntro, style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = website,
                onValueChange = { website = it; missing = false },
                singleLine = true,
                label = { Text(texts.passwords.website) },
                supportingText = { Text(texts.passwords.websiteHint) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                singleLine = true,
                label = { Text(texts.passwords.username) },
                supportingText = { Text(texts.passwords.usernameHint) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; missing = false },
                singleLine = true,
                label = { Text(texts.passwords.password) },
                visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                trailingIcon = {
                    Text(
                        if (shown) texts.passwords.hide else texts.passwords.show,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(role = Role.Button) { shown = !shown }.padding(12.dp),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            // A login with a passkey needs no password (ADR 0052).
            val needsPassword = existing?.passkeys.isNullOrEmpty()
            if (missing) Text(if (needsPassword) texts.passwords.needWebsiteAndPassword else texts.passwords.needWebsite, color = MaterialTheme.colorScheme.error)
            AppButton(texts.passwords.save, {
                if (website.isBlank() || (needsPassword && password.isEmpty())) {
                    missing = true
                } else {
                    controller.passwords?.save(existing?.id, website, username, password)?.let { onSaved(it.id) }
                }
            }, Modifier.fillMaxWidth())
        }
    }
}

/** Confirmation before something that cannot be undone (delete, start over). */
@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { AppButton(confirm, onConfirm, kind = ButtonKind.Danger) },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}
