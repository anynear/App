package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.circles.Circle
import app.anynear.i18n.texts
import app.anynear.location.ShareLevel

/** Circles a check-in can go to: their members are the ones who would see the place. */
private fun AppController.shareCircles(all: List<Circle>): List<Circle> = lifeSigns?.choices(all) ?: all.filter { !it.removed }

/**
 * Asks once who had the app before the update: „Sollen deine Helfer sehen, wo du bist, wenn etwas ist?“ (ADR 0121
 * §10). Both buttons decide; the back gesture postpones. The setup asks the same in its step „Standort“.
 */
@Composable
internal fun LocationShareDialog(controller: AppController) {
    val sharing = controller.locationSharing ?: return
    val circles by controller.circles.collectAsState()
    val lt = texts.location
    val names = controller.shareCircles(circles).map { it.name }.filter { it.isNotBlank() }
    AppDialog(
        onDismissRequest = sharing::postpone,
        title = { Text(lt.questionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(lt.questionText)
                if (names.isNotEmpty()) {
                    Text(lt.appliesTo(names.joinToString(", ")), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(lt.questionNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { AppButton(lt.yes, { sharing.answer(ShareLevel.WhenNeeded) }) },
        dismissButton = { AppButton(lt.no, { sharing.answer(ShareLevel.Off) }, kind = ButtonKind.Quiet) },
    )
}

/**
 * Settings „Standort“ (ADR 0121 §4, the first part of „Wer sieht mich?“): the default for everyone, one line per
 * circle that can be set differently, and the permission of the system on this device.
 */
@Composable
internal fun LocationSection(controller: AppController) {
    val sharing = controller.locationSharing ?: return
    val default by sharing.default.collectAsState()
    val overrides by sharing.circles.collectAsState()
    val allowed by sharing.allowed.collectAsState()
    val circles by controller.circles.collectAsState()
    val lt = texts.location
    val on = default == ShareLevel.WhenNeeded
    fun set(to: Boolean) = sharing.answer(if (to) ShareLevel.WhenNeeded else ShareLevel.Off)
    val shown = controller.shareCircles(circles)
    AppSection(lt.settingsTitle, footer = lt.settingsFooterMap) {
        AppRow(
            title = lt.whenNeeded,
            subtitle = if (on) lt.whenNeededOn else lt.whenNeededOff,
            icon = AppIcons.Info,
            trailing = { Switch(checked = on, onCheckedChange = ::set) },
            onClick = { set(!on) },
        )
        // Someone sees the place, but this device may not read it exactly: say so and offer the way to the system.
        val anyOn = on || shown.any { (overrides[it.id] ?: default) == ShareLevel.WhenNeeded }
        if (anyOn && !allowed) {
            AppRow(title = lt.allowTitle, subtitle = lt.allowText, icon = AppIcons.Achtung, chevron = true, onClick = sharing::allow)
        }
    }
}

/** „10 m“, „250 m“, „3 km“: how accurate a place is, rounded so that it does not look more exact than it is. */
internal fun formatAccuracy(meters: Int): String = when {
    meters < 1000 -> texts.location.metres(maxOf(10, (meters + 5) / 10 * 10))
    else -> texts.location.kilometres(((meters + 500) / 1000).toString())
}
