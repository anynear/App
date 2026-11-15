package app.anynear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.anynear.app.AboLimit
import app.anynear.app.AboPreview
import app.anynear.app.AboPreviews
import app.anynear.i18n.texts

/**
 * Hint when a limit of a message is reached (ADR 0076 §1a): which plan allows more, with „Pakete
 * ansehen“. Values from the subscription preview of the website; without it (offline, older preview) nothing appears.
 */
@Composable
internal fun AboLimitHint(limit: AboLimit?, modifier: Modifier = Modifier) {
    limit ?: return
    val preview by produceState<AboPreview?>(null) { value = AboPreviews.loadOnce() }
    val (plan, value) = preview?.upgradeFor(limit) ?: return
    var showing by remember { mutableStateOf(false) }
    val name = plan.name(german())
    val text = when (limit) {
        AboLimit.TEXT -> texts.chat.aboLongerText(name, value)
        AboLimit.ATTACHMENT_SIZE -> texts.chat.aboBiggerAttachments(name, value)
        AboLimit.ATTACHMENT_COUNT -> texts.chat.aboMoreAttachments(name, value)
        AboLimit.VOICE_LENGTH -> texts.chat.aboLongerVoice(name, value)
    }
    Row(modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        AppButton(texts.chat.aboSeePlans, { showing = true }, kind = ButtonKind.Quiet)
    }
    if (showing) {
        Dialog(onDismissRequest = { showing = false }, properties = AppFullScreenDialogProperties) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                AboPreviewScreen(texts.chat.aboPlansTitle) { showing = false }
            }
        }
    }
}
