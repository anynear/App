package app.anynear.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

/**
 * Inviting into a circle, two ways:
 * - share a link (WhatsApp, Signal, SMS …) if the person is somewhere else,
 * - show a QR code if they sit opposite. They scan it with the normal camera app; the code
 *   contains the same link and opens anynear directly.
 */
@Composable
internal fun InviteDialog(controller: AppController) {
    val circle by controller.inviteFor.collectAsState()
    val c = circle ?: return
    var showQr by remember(c.id) { mutableStateOf(false) }
    val link = remember(c.id, c.secret) { controller.inviteLink(c.id) } ?: return

    AppDialog(
        onDismissRequest = controller::closeInvite,
        title = { Text(texts.people.inviteTo(c.name)) },
        text = {
            if (showQr) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    QrCode(link)
                    Text(
                        texts.people.qrInviteHelp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(texts.people.inviteWarning)
                    AppButton(
                        texts.people.shareLink,
                        {
                            controller.shareInvite(c.id)
                            controller.closeInvite()
                        },
                        Modifier.fillMaxWidth(),
                        kind = ButtonKind.Main,
                        icon = AppIcons.Teilen,
                    )
                    Text(texts.people.shareLinkHint, style = MaterialTheme.typography.bodySmall)
                    AppButton(texts.people.showQrCode, { showQr = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = AppIcons.QrCode)
                    Text(texts.people.showQrCodeHint, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { AppButton(texts.common.done, controller::closeInvite, kind = ButtonKind.Quiet) },
        dismissButton = if (showQr) ({ AppButton(texts.common.back, { showQr = false }, kind = ButtonKind.Quiet) }) else null,
    )
}

/** QR code always black on white with a margin, so that every camera reads it in dark mode too. */
@Composable
internal fun QrCode(data: String, size: Dp = 240.dp) {
    val painter = rememberQrCodePainter(data)
    Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(14.dp)) {
        Image(painter, contentDescription = texts.people.qrCode, modifier = Modifier.size(size))
    }
}
