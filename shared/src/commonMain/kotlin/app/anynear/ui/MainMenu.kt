package app.anynear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import app.anynear.app.AppController
import app.anynear.i18n.texts

/** Destinations in the ⋮ menu; the UI opens the matching detail page for them. */
internal enum class MenuTarget { Profile, About, Internet, Radio, Devices, Settings, Troubleshooting, Help }

/**
 * ⋮ menu in the header of every tab (ADR 0062 §1): what used to be under „Mehr“, in the same order, plus „Über“ next to
 * „Hilfe“. Every entry opens a detail page; backup and emergency card are in the settings,
 * the log under „Fehlerbehebung“.
 */
@Composable
internal fun MainMenu(controller: AppController, onOpen: (MenuTarget) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val update by controller.appUpdate.collectAsState()
    val paired by controller.pairedDevice.collectAsState()
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
