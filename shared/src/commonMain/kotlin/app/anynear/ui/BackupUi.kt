package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.data.StateBackup
import app.anynear.i18n.texts
import app.anynear.passwords.UnlockResult
import app.anynear.passwords.rememberPasswordUnlock
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** „Sicherung erstellen“ in the settings (ADR 0027). */
@Composable
internal fun BackupCreateRow(controller: AppController) {
    if (!controller.backupAvailable) return
    var creating by remember { mutableStateOf(false) }
    AppRow(title = texts.backup.create, subtitle = texts.backup.createSubtitle, icon = AppIcons.Speichern, chevron = true, onClick = { creating = true })
    if (creating) CreateBackupDialog(controller) { creating = false }
}

/** „Sicherung“ in the settings: create and restore (ADR 0027), below it the emergency card. */
@Composable
internal fun BackupSection(controller: AppController) {
    var restoring by remember { mutableStateOf(false) }
    if (controller.backupAvailable) {
        AppSection(texts.backup.title, footer = texts.backup.footer) {
            BackupCreateRow(controller)
            AppRow(title = texts.backup.restore, subtitle = texts.backup.restoreSubtitle, icon = AppIcons.Aktualisieren, chevron = true, onClick = { restoring = true })
        }
    }
    // Emergency card: a row of its own with a key, not the same icon as the backup.
    AppSection { EmergencyCardRow(controller) }
    if (restoring) RestoreBackupDialog(controller) { restoring = false }
}

/**
 * The app makes a backup code (ADR 0093), the person writes it down and types it once; then create and save the file
 * (Android: Downloads, iOS: share menu).
 */
@Composable
internal fun CreateBackupDialog(controller: AppController, onDismiss: () -> Unit) {
    val code = remember { controller.newBackupCode() }
    var typing by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun write(passwordKey: ByteArray?) {
        scope.launch {
            busy = true
            val file = runCatching { controller.createBackup(code, passwordKey) }.getOrNull()
            if (file == null) {
                error = texts.backup.createFailed
            } else {
                val name = texts.backup.fileName(Clock.System.now().toString().take(10)) + "." + StateBackup.FILE_EXTENSION
                when (val result = saveToDevice(file, name, StateBackup.MIME_TYPE)) {
                    is SaveResult.Saved -> done = texts.backup.savedIn(result.where)
                    SaveResult.Handed -> done = ""
                    SaveResult.Failed -> error = texts.backup.saveFailed
                }
            }
            busy = false
        }
    }

    val unlockPasswords = rememberPasswordUnlock { result ->
        when (result) {
            is UnlockResult.Unlocked -> write(result.key)
            // No longer readable: the backup is created without them, they could not be taken along anyway.
            UnlockResult.KeyLost -> write(null)
            UnlockResult.Cancelled, UnlockResult.NoDeviceLock -> error = texts.backup.passwordsNeedLock
        }
    }

    fun create() {
        if (!controller.backupCodeMatches(code, typed)) {
            error = texts.backup.codeMismatch
            return
        }
        error = null
        // If there are passwords, they only come along after the device lock (ADR 0039, item 5).
        if (controller.backupNeedsDeviceLock) unlockPasswords(texts.backup.passwordsReason) else write(null)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(texts.backup.create) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val saved = done
                if (saved != null) {
                    if (saved.isNotEmpty()) Text(saved, style = MaterialTheme.typography.bodyLarge)
                    Text(texts.backup.keepSafe, style = MaterialTheme.typography.bodyMedium)
                } else if (!typing) {
                    Text(texts.backup.codeText, style = MaterialTheme.typography.bodyMedium)
                    Text(code, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
                    if (controller.backupNeedsDeviceLock) Text(texts.backup.passwordsIncluded, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(texts.backup.codeTypeText, style = MaterialTheme.typography.bodyMedium)
                    PasswordField(typed, { typed = it; error = null }, texts.backup.code, enabled = !busy, onDone = ::create, hidden = false)
                    if (busy) Text(texts.backup.creating, style = MaterialTheme.typography.bodyMedium)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                }
            }
        },
        confirmButton = {
            when {
                done != null -> AppButton(texts.common.done, onDismiss)
                !typing -> AppButton(texts.backup.codeWritten, { typing = true })
                else -> AppButton(texts.backup.create, ::create, enabled = !busy && typed.isNotBlank())
            }
        },
        dismissButton = {
            if (done == null) {
                if (typing && !busy) AppButton(texts.backup.codeShowAgain, { typing = false; error = null }, kind = ButtonKind.Quiet)
                else AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet, enabled = !busy)
            }
        },
    )
}

/**
 * Choose a file, password, confirm, restart. Also from the setup, on a new phone or
 * a new installation.
 */
@Composable
internal fun RestoreBackupDialog(controller: AppController, onDismiss: () -> Unit) {
    var file by remember { mutableStateOf<PickedFile?>(null) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stagedAt by remember { mutableStateOf<Long?>(null) }
    var restarting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pick = rememberBackupFilePicker { picked ->
        file = picked
        error = when {
            picked.tooLarge -> texts.backup.fileTooLarge
            picked.bytes == null -> texts.backup.fileUnreadable
            else -> null
        }
    }

    fun check() {
        val bytes = file?.bytes ?: return
        scope.launch {
            busy = true
            error = null
            when (val result = controller.stageRestore(bytes, password)) {
                is StateBackup.RestoreCheck.Staged -> stagedAt = result.createdAtMillis
                StateBackup.RestoreCheck.WrongPassword -> error = texts.backup.wrongPassword
                StateBackup.RestoreCheck.NotABackup -> error = texts.backup.notABackup
                StateBackup.RestoreCheck.TooNew -> error = texts.backup.tooNew
                StateBackup.RestoreCheck.TooOld -> error = texts.backup.tooOld
            }
            busy = false
        }
    }

    val staged = stagedAt
    if (staged != null) {
        // Noted; only „Einspielen und neu starten“ replaces anything. Cancelling leaves the state as it is.
        AlertDialog(
            onDismissRequest = {},
            title = { Text(texts.backup.confirmTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (restarting) {
                        Text(texts.backup.restarting, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text(texts.backup.confirmText(formatDateTime(staged)), style = MaterialTheme.typography.bodyLarge)
                        Text(texts.backup.confirmOnlyHere, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                AppButton(texts.backup.restoreAndRestart, enabled = !restarting, kind = ButtonKind.Danger, onClick = {
                    restarting = true
                    controller.restartForRestore()
                })
            },
            dismissButton = {
                if (!restarting) {
                    AppButton(texts.common.cancel, kind = ButtonKind.Quiet, onClick = {
                        controller.discardPendingRestore()
                        onDismiss()
                    })
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(texts.backup.restore) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(texts.backup.restoreText, style = MaterialTheme.typography.bodyMedium)
                val chosen = file
                if (chosen?.bytes != null && chosen.name.isNotBlank()) Text(chosen.name, style = MaterialTheme.typography.bodyMedium)
                AppButton(
                    if (chosen == null) texts.backup.chooseFile else texts.backup.chooseOtherFile,
                    pick,
                    Modifier.fillMaxWidth(),
                    kind = if (chosen == null) ButtonKind.Main else ButtonKind.Secondary,
                    enabled = !busy,
                )
                if (chosen?.bytes != null) PasswordField(password, { password = it; error = null }, texts.backup.codeOrPassword, enabled = !busy, onDone = ::check, hidden = false)
                if (busy) Text(texts.backup.checking, style = MaterialTheme.typography.bodyMedium)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
        },
        confirmButton = {
            AppButton(texts.common.next, ::check, enabled = !busy && file?.bytes != null && password.isNotEmpty())
        },
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet, enabled = !busy) },
    )
}

/** Without suggestions from the keyboard; hidden unless [hidden] is `false` (a code to type off paper). */
@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean, onDone: (() -> Unit)? = null, hidden: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        enabled = enabled,
        label = { Text(label) },
        visualTransformation = if (hidden) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (hidden) KeyboardType.Password else KeyboardType.Ascii,
            autoCorrectEnabled = false,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = Modifier.fillMaxWidth(),
    )
}
