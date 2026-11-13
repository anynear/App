package app.anynear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anynear.i18n.texts

/**
 * „Menschen“: contacts and circles as two visibly separate areas, switchable at the top. Both remain
 * separate lists with their own actions; there is no mixed overall list.
 */
@Composable
internal fun PeopleScreen(
    showCircles: Boolean,
    onSwitch: (Boolean) -> Unit,
    contacts: @Composable () -> Unit,
    circles: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        AppHeader(texts.setup.tabPeople)
        PeopleSwitch(showCircles, onSwitch)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (showCircles) circles() else contacts()
        }
    }
}

/** Two fields of equal size, each at least 48 dp, with tab semantics for screen readers. */
@Composable
private fun PeopleSwitch(showCircles: Boolean, onSwitch: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .background(scheme.surfaceVariant, RoundedCornerShape(28.dp))
            .padding(4.dp)
            .selectableGroup(),
    ) {
        listOf(false to texts.people.contacts, true to texts.people.circles).forEach { (value, label) ->
            val selected = value == showCircles
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(if (selected) scheme.secondaryContainer else Color.Transparent, RoundedCornerShape(24.dp))
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSwitch(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
        }
    }
}
