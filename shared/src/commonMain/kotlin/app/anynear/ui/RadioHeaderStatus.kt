package app.anynear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.mesh.DeviceName
import app.anynear.mesh.LinkState

/**
 * Radio in the header area next to the flame: connected with the name below, otherwise pale with a warning-color dot.
 * Only if a radio is assigned; without a radio there is nothing to note. Tapping opens the connections.
 */
@Composable
internal fun RadioHeaderStatus(controller: AppController, onClick: () -> Unit) {
    val state by controller.linkState.collectAsState()
    val paired by controller.pairedDevice.collectAsState()
    val own by controller.ownDevice.collectAsState()
    if (state == LinkState.NoDevice) return
    val connected = state == LinkState.Connected
    val colors = AppTheme.colors
    // Name without „anynear.app | “, as it was chosen during setup; otherwise the short name of the device.
    val name = own?.let { DeviceName.suffixOf(it.longName) ?: it.shortName.ifBlank { it.longName } }
        ?: paired?.name?.let { DeviceName.suffixOf(it) ?: it }.orEmpty()
    val description = texts.chat.radioStatus(
        if (connected && name.isNotBlank()) texts.chat.linkConnectedTo(name) else state.label(),
    )
    Column(
        Modifier
            .widthIn(max = 72.dp)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(
                AppIcons.Funkgeraet,
                contentDescription = null,
                tint = if (connected) colors.onHeader else colors.onHeader.copy(alpha = 0.5f),
                modifier = Modifier.size(26.dp),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(9.dp)
                    .background(if (connected) colors.safe else MaterialTheme.colorScheme.error, CircleShape),
            )
        }
        if (connected && name.isNotBlank()) {
            Text(name, style = MaterialTheme.typography.labelSmall, color = colors.onHeader, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
