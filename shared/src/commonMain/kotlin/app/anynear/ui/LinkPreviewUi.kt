package app.anynear.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.chats.LinkDraft
import app.anynear.chats.LinkPreviews
import app.anynear.i18n.texts
import app.anynear.mesh.LinkPreview
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Preview of a web link in the bubble (ADR 0069): image, title, description, below it always the name of the site
 * from the address. Tapping opens the link like the link in the text.
 */
@Composable
internal fun LinkPreviewCard(controller: AppController, link: LinkPreview, textColor: Color) {
    val uri = LocalUriHandler.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClickLabel = link.title ?: link.url) { runCatching { uri.openUri(link.url) } },
    ) {
        link.image?.let { img ->
            val ratio = if (img.width > 0 && img.height > 0) (img.width.toFloat() / img.height).coerceIn(1f, 2f) else 1.9f
            ImageTile(controller, img, Modifier.fillMaxWidth().aspectRatio(ratio)) { runCatching { uri.openUri(link.url) } }
        }
        LinkTexts(link.title, link.description, link.url, textColor, Modifier.padding(10.dp))
    }
}

/** Preview above the input field as long as it is to go along; „×“ takes it out for this message. */
@Composable
internal fun DraftLinkPreview(link: LinkDraft, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val image = remember(link) { link.image?.bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    Row(
        Modifier
            .padding(start = 12.dp, end = 4.dp, top = 8.dp)
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
        }
        LinkTexts(link.title, link.description, link.url, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f).padding(horizontal = 10.dp))
        IconButton(onClick = onRemove) { Icon(AppIcons.Schliessen, contentDescription = texts.chat.removePreview) }
    }
}

@Composable
private fun LinkTexts(title: String?, description: String?, url: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        title?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        description?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        LinkPreviews.hostOf(url)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f), maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
