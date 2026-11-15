package app.anynear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.anynear.i18n.texts
import app.anynear.passwords.Otp
import kotlinx.coroutines.delay
import kotlin.time.Clock

// One-time codes in the login (ADR 0052, item 2): code large with remaining time, tapping copies; adding via camera,
// alternatively typing in the key.

/** Current code, new every second, with a bar for the remaining time. Tapping copies it ([onCopy]). */
@Composable
internal fun OtpRow(otp: Otp, onCopy: (String) -> Unit) {
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(otp) {
        while (true) {
            now = Clock.System.now().toEpochMilliseconds()
            delay(1000 - now % 1000)
        }
    }
    val code = otp.code(now)
    val left = otp.secondsLeft(now)
    val description = texts.passwords.otpDescription(code, left)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onCopy(code) }
            .clearAndSetSemantics { contentDescription = description; role = Role.Button }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RowIcon(AppIcons.Uhr)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // In two halves like „123 456“, so that it is easy to read off.
            Text(
                code.chunked((code.length + 1) / 2).joinToString(" "),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
            LinearProgressIndicator(progress = { left.toFloat() / otp.period }, modifier = Modifier.fillMaxWidth())
            Text(
                "${texts.passwords.otp} · ${texts.passwords.otpSecondsLeft(left)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(AppIcons.Kopieren, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Add a one-time code: first the camera for the QR code of the website (`otpauth://totp/…`), below it „Schlüssel
 * eintippen“ for websites without a QR code or without a camera.
 */
@Composable
internal fun OtpAddDialog(onAdded: (Otp) -> Unit, onDismiss: () -> Unit) {
    var typing by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = AppFullScreenDialogProperties) {
        if (typing) {
            OtpKeyEntry(onAdded, onBack = { typing = false }, onDismiss)
            return@Dialog
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            QrCameraView(
                onCode = { text ->
                    if (done) return@QrCameraView
                    val otp = Otp.parse(text).takeIf { text.trim().startsWith("otpauth:", ignoreCase = true) }
                    if (otp != null) {
                        done = true
                        onAdded(otp)
                    } else {
                        hint = texts.passwords.otpNotCode
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.align(Alignment.Center).size(240.dp).border(3.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(24.dp)))
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(AppIcons.Schliessen, contentDescription = texts.common.close, tint = Color.White)
            }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    hint ?: texts.passwords.otpScanHelp,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                )
                AppButton(texts.passwords.otpTypeKey, { typing = true }, kind = ButtonKind.Secondary, icon = AppIcons.Stift)
            }
            hint?.let {
                LaunchedEffect(it) {
                    delay(2500)
                    hint = null
                }
            }
        }
    }
}

@Composable
private fun OtpKeyEntry(onAdded: (Otp) -> Unit, onBack: () -> Unit, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppHeader(texts.passwords.otpAdd, leading = { HeaderBackButton(onBack) }, trailing = {
            IconButton(onClick = onDismiss) { Icon(AppIcons.Schliessen, contentDescription = texts.common.close) }
        })
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; wrong = false },
                label = { Text(texts.passwords.otpKey) },
                supportingText = { Text(texts.passwords.otpKeyHint) },
                isError = wrong,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            if (wrong) Text(texts.passwords.otpInvalidKey, color = MaterialTheme.colorScheme.error)
            AppButton(texts.passwords.save, {
                val otp = Otp.parse(key)
                if (otp == null) wrong = true else onAdded(otp)
            }, Modifier.fillMaxWidth())
        }
    }
}
