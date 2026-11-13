package app.anynear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts

/** Destinations in the ⋮ menu; the UI opens the matching detail page for them. */
internal enum class MenuTarget { Profile, About, Internet, Radio, Devices, Settings, Troubleshooting, Help }

/**
 * ⋮ menu in the header of every tab (ADR 0062 §1): what used to be under „Mehr“, in the same order, plus „Über“ next to
 * „Hilfe“. Every entry opens a detail page; backup and emergency card are in the settings,
 * the log under „Fehlerbehebung“.
 *
 * Above the fixed entries the open page can put its own, rarely needed actions (ADR 0101, [AppHeader] `menu`): under a
 * small heading with the page title, set apart by a thick line, so that the fixed part stays the same everywhere.
 */
@Composable
internal fun MainMenu(controller: AppController, onOpen: (MenuTarget) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val update by controller.appUpdate.collectAsState()
    val paired by controller.pairedDevice.collectAsState()
    val context = LocalHeaderContext.current
    Box {
        HeaderAction(AppIcons.Menue, texts.setup.tabMore) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun item(text: String, icon: ImageVector, action: () -> Unit) = DropdownMenuItem(
                text = { Text(text) },
                leadingIcon = { Icon(icon, contentDescription = null) },
                onClick = {
                    open = false
                    action()
                },
            )
            context?.let { c ->
                Text(
                    c.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp).semantics { heading() },
                )
                c.actions.forEach { item(it.label, it.icon, it.onClick) }
                HorizontalDivider(Modifier.padding(vertical = 6.dp), thickness = 3.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
            item(texts.settings.myProfile, AppIcons.Person) { onOpen(MenuTarget.Profile) }
            // Only if there is something to do under „Über“ (new version, move).
            updateHint(update)?.let { item(it, AppIcons.Aktualisieren) { onOpen(MenuTarget.About) } }
            HorizontalDivider()
            item(texts.common.internet, AppIcons.Internet) { onOpen(MenuTarget.Internet) }
            item(if (paired == null) texts.settings.connectRadio else texts.settings.radio, AppIcons.Funkgeraet) { onOpen(MenuTarget.Radio) }
            HorizontalDivider()
            item(texts.devices.title, AppIcons.Person) { onOpen(MenuTarget.Devices) }
            HorizontalDivider()
            item(texts.settings.title, AppIcons.Einstellungen) { onOpen(MenuTarget.Settings) }
            item(texts.support.troubleshooting, AppIcons.Werkzeug) { onOpen(MenuTarget.Troubleshooting) }
            item(texts.settings.help, AppIcons.Info) { onOpen(MenuTarget.Help) }
            item(texts.settings.aboutApp, AppIcons.Kreis) { onOpen(MenuTarget.About) }
        }
    }
}
