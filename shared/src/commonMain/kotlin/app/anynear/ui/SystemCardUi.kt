package app.anynear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Widest a system card gets; on a narrow phone it takes the width of the chat. */
internal val SystemCardMaxWidth = 320.dp

/**
 * The one look of everything the app itself puts into a chat (system cards): changes in the circle, check-ins, steps
 * about SOS contacts, places and sharing them, introduced contacts. In the middle, the same frame for both sides, an
 * icon with one title line, below it what the card has to say and at the end the time. A system card has no details,
 * no reactions and cannot be deleted: it records what happened, not something someone typed.
 *
 * [accent] colours icon and title (green for something good, red for an alarm); [onClick] makes the whole card a
 * button, e.g. to the check-in that runs.
 */
@Composable
internal fun SystemCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = SystemCardMaxWidth)
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = accent)
            }
            content()
            footer?.invoke(this)
        }
    }
}

/** The time at the end of a system card, right-aligned like in a speech bubble. */
@Composable
internal fun ColumnScope.SystemCardTime(timestampMillis: Long) {
    Text(
        formatTime(timestampMillis),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.End),
    )
}
