package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anynear.alerts.NotificationAction
import app.anynear.i18n.texts
import app.anynear.lifesigns.LifeSigns

/**
 * Alarm as a full screen on the lock screen (Android, ADR 0062 §6): large, in the alarm colour, with „Ton aus“ and
 * „Öffnen“. Only the content; the activity takes care of lock screen and sound. Has the alarm a button of the app
 * ([action], e.g. „Alles gut“ or „Ich schaue nach“ for a check-in, ADR 0083), it is the big round button here, so it
 * works with one tap and without unlocking.
 */
@Composable
fun AlarmScreen(
    title: String,
    text: String,
    channel: String,
    onSilence: () -> Unit,
    onOpen: () -> Unit,
    action: NotificationAction? = null,
    onAction: () -> Unit = {},
) {
    AppTheme {
        val colors = AppTheme.colors
        Surface(color = colors.alarmContainer, contentColor = colors.onAlarmContainer, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                if (action == null) Icon(AppIcons.Glocke, contentDescription = null, tint = colors.alarm, modifier = Modifier.size(72.dp))
                Text(channel, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                if (action != null) {
                    val icon = if (action.id.startsWith(LifeSigns.ACTION_OK + ":")) AppIcons.Haken else AppIcons.Person
                    BigRoundButton(action.label, icon, onAction)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        AppButton(texts.alerts.silence, onSilence, Modifier.weight(1f), kind = ButtonKind.Secondary)
                        AppButton(texts.alerts.open, onOpen, Modifier.weight(1f), kind = ButtonKind.Quiet)
                    }
                } else {
                    AppButton(texts.alerts.silence, onSilence, Modifier.fillMaxWidth(), kind = ButtonKind.Main)
                    AppButton(texts.alerts.open, onOpen, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                }
            }
        }
    }
}
