package app.anynear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anynear.i18n.texts

// Building blocks for all screens: buttons, lists in sections, action tiles, buttons in the header area.
// Screens build buttons and settings rows only from these, so that the same concept applies everywhere
// (Docs/marke/README.md, sections 5, 7 and 8).

/**
 * Ranking of the buttons. [Main] is the one yellow main action of a screen (at most one),
 * [Primary] the normal confirmation, [Secondary] an equivalent alternative, [Quiet] minor things
 * like „Abbrechen“, [Danger] leaving, removing, disconnecting.
 */
internal enum class ButtonKind { Main, Primary, Secondary, Quiet, Danger }

/** Minimum height of all buttons: easy to hit, even with a shaky hand. */
private val ButtonHeight = 48.dp
internal val MainButtonHeight = 56.dp

@Composable
internal fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val padding = PaddingValues(horizontal = if (kind == ButtonKind.Quiet || kind == ButtonKind.Danger) 14.dp else 22.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text, style = if (kind == ButtonKind.Main) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
    when (kind) {
        ButtonKind.Main -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = MainButtonHeight),
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = scheme.secondary, contentColor = scheme.onSecondary),
            content = content,
        )
        ButtonKind.Primary -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = ButtonHeight),
            contentPadding = padding,
            content = content,
        )
        ButtonKind.Secondary -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = ButtonHeight),
            contentPadding = padding,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface),
            content = content,
        )
        ButtonKind.Quiet, ButtonKind.Danger -> TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = ButtonHeight),
            contentPadding = padding,
            colors = ButtonDefaults.textButtonColors(contentColor = if (kind == ButtonKind.Danger) scheme.error else scheme.onSurface),
            content = content,
        )
    }
}

/** Back in the header area. */
@Composable
internal fun HeaderBackButton(onClick: () -> Unit) {
    HeaderAction(AppIcons.Zurueck, texts.common.back, onClick)
}

/** Icon button on the right or left in the night-blue header area. */
@Composable
internal fun HeaderAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = description, tint = AppTheme.colors.onHeader)
    }
}

/**
 * Section of a list or settings page: small heading, below it a card with rows
 * ([AppRow]) or free content. [footer] explains in one sentence what the section does.
 */
@Composable
internal fun AppSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
            )
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 6.dp), content = content)
        }
        footer?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** Free content in an [AppSection], with the same margin as the rows. */
@Composable
internal fun SectionContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * A row in an [AppSection]: icon or picture, title, one line of explanation, on the right a state
 * or an arrow. Tappable rows that open a subpage show the arrow by themselves.
 */
@Composable
internal fun AppRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    danger: Boolean = false,
    chevron: Boolean = false,
    enabled: Boolean = true,
    muted: Boolean = false,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    // [muted]: looks like disabled (e.g. a radio that is not connected), but stays tappable.
    val dim = !enabled || muted
    val textColor = when {
        dim -> scheme.onSurface.copy(alpha = 0.38f)
        danger -> scheme.error
        else -> scheme.onSurface
    }
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            leading != null -> leading()
            icon != null -> RowIcon(icon, danger = danger, enabled = !dim)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = textColor)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = if (dim) textColor else scheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
        if (chevron) Chevron()
    }
}

/** Expandable row („Erweitert“, „Mehr dazu“). The arrow turns downwards when open. */
@Composable
internal fun ExpandRow(title: String, expanded: Boolean, icon: ImageVector? = null, onToggle: () -> Unit) {
    AppRow(
        title = title,
        icon = icon,
        trailing = { Chevron(Modifier.rotate(if (expanded) 90f else 0f)) },
        onClickLabel = if (expanded) texts.chat.collapse else texts.chat.expand,
        onClick = onToggle,
    )
}

/** Arrow to the right: the one sign in the app that a tap leads to another page (rows and the title in the header). */
@Composable
internal fun Chevron(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(AppIcons.Weiter, contentDescription = null, tint = tint, modifier = modifier.size(CHEVRON_SIZE))
}

/** Width of the arrow at the end of the row; rows without an arrow keep this much free when things are stacked. */
internal val CHEVRON_SIZE = 20.dp

/** Icon of a row on a small, calm surface: light on delicate apricot, dark on lighter petrol. */
@Composable
internal fun RowIcon(icon: ImageVector, danger: Boolean = false, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val tile = if (scheme.background.luminance() > 0.5f) scheme.secondaryContainer else scheme.surfaceVariant
    Box(
        Modifier
            .size(40.dp)
            .background(if (danger) scheme.error.copy(alpha = 0.12f) else tile, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (danger) scheme.error else scheme.onSurface
        Icon(icon, contentDescription = null, tint = if (enabled) tint else tint.copy(alpha = 0.38f), modifier = Modifier.size(22.dp))
    }
}

/** Small dot on the right in a row: green = connected, grey = waiting. Never alone, always with text next to it. */
@Composable
internal fun StatusDot(ok: Boolean) {
    val color: Color = if (ok) AppTheme.colors.safe else MaterialTheme.colorScheme.outline
    Box(Modifier.size(12.dp).background(color, CircleShape))
}

/**
 * Row of equally wide tiles with icon and word, for the most important actions of a detail page
 * (e.g. invite, edit).
 */
@Composable
internal fun ActionTiles(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/** One tile in [ActionTiles]. [main] makes it the yellow main action of the screen. */
@Composable
internal fun RowScope.ActionTile(label: String, icon: ImageVector, main: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (main) scheme.secondary else scheme.surfaceContainer,
        contentColor = if (main) scheme.onSecondary else scheme.onSurface,
        modifier = Modifier.weight(1f),
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

/**
 * Search field at the top of a list (messages, contacts, circles). Filters while typing, without a search button;
 * the cross clears it again.
 */
@Composable
internal fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(AppIcons.Suche, contentDescription = null) },
        trailingIcon = if (query.isEmpty()) null else ({
            IconButton(onClick = { onQueryChange("") }) { Icon(AppIcons.Schliessen, contentDescription = texts.chat.clearSearch) }
        }),
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Does [text] match the search? Upper and lower case do not count; an empty search always matches. */
internal fun matchesSearch(query: String, vararg text: String?): Boolean {
    val q = query.trim()
    return q.isEmpty() || text.any { it != null && it.contains(q, ignoreCase = true) }
}

/** Dialog with a selection of actions as rows, e.g. behind the plus in the header. */
@Composable
internal fun ActionsDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(content = content) },
        confirmButton = {},
        dismissButton = { AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/** Empty list of hits: a calm sentence instead of an empty surface. */
@Composable
internal fun NoSearchResults(modifier: Modifier = Modifier) {
    Text(
        texts.chat.nothingFound,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}
