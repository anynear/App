package app.anynear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.anynear.app.AppController
import app.anynear.chats.findAppLink
import app.anynear.i18n.texts
import kotlinx.coroutines.delay

/**
 * Camera image that reads QR codes. [onCode] gets the content of every recognized code (also repeatedly).
 * Asks for the camera itself; without permission it shows a sentence about it.
 */
@Composable
internal expect fun QrCameraView(onCode: (String) -> Unit, modifier: Modifier = Modifier)

/**
 * Full screen for scanning an anynear QR code (invitation, contact, new own device). An anynear code is taken over
 * immediately like a tapped link, other codes only result in a short hint. [help] is the sentence at the bottom.
 */
@Composable
internal fun QrScanDialog(controller: AppController, help: String = texts.people.scanHelp, onDismiss: () -> Unit) {
    var hint by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = AppFullScreenDialogProperties) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            QrCameraView(
                onCode = { text ->
                    if (done) return@QrCameraView
                    val link = findAppLink(text)
                    if (link != null) {
                        done = true
                        onDismiss()
                        controller.openLink(link)
                    } else {
                        hint = texts.people.notAppCode
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Frame as an aiming aid in the middle.
            Box(Modifier.align(Alignment.Center).size(240.dp).border(3.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(24.dp)))
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(AppIcons.Schliessen, contentDescription = texts.common.close, tint = Color.White)
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    hint ?: help,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                )
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
