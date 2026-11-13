package app.anynear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.mesh.ChatMessage
import kotlin.time.Clock

/** This long a message jumped to from the search lights up in the history. */
internal const val HIGHLIGHT_MILLIS = 2_500L

/**
 * Search in a conversation: search field at the top, below it the matching messages, newest first, with sender,
 * time and the place of the match. The search covers the text and the names of the files, only on this phone.
 */
@Composable
internal fun ChatSearch(
    controller: AppController,
    messages: List<ChatMessage>,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onOpen: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val found = remember(messages, query) { if (query.isBlank()) emptyList() else chatSearchMatches(messages, query) }
    val now = remember(found) { Clock.System.now().toEpochMilliseconds() }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(query, onQueryChange, texts.chat.searchInChat, Modifier.weight(1f).focusRequester(focus))
            IconButton(onClick = onClose) { Icon(AppIcons.Schliessen, contentDescription = texts.chat.closeSearch) }
        }
        when {
            query.isBlank() -> Text(
                texts.chat.searchInChatHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            found.isEmpty() -> NoSearchResults()
            else -> {
                Text(
                    texts.chat.searchMatches(found.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(found, key = { it.localId }) { message ->
                        SearchMatch(
                            sender = if (message.outgoing) texts.status.youSender else controller.senderLabel(message),
                            time = formatChatListTime(message.timestampMillis, now),
                            snippet = searchSnippet(searchableText(message), query, MaterialTheme.colorScheme.secondaryContainer),
                            onClick = { onOpen(message) },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMatch(sender: String, time: String, snippet: AnnotatedString, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sender, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(snippet, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Text of a message including file names, the way the search sees it. */
private fun searchableText(message: ChatMessage): String =
    (listOf(message.text) + message.attachments.mapNotNull { it.name.ifEmpty { null } }).filter { it.isNotEmpty() }.joinToString(" · ")

/** Messages (no lines about changes in the circle) whose text or file names contain [query], newest first. */
internal fun chatSearchMatches(messages: List<ChatMessage>, query: String): List<ChatMessage> =
    messages.filter { it.system == null && matchesSearch(query, searchableText(it)) }.sortedByDescending { it.timestampMillis }

/**
 * Excerpt around the first match, the match highlighted. Long text before it is shortened with "…",
 * so that the match is in the first lines.
 */
internal fun searchSnippet(text: String, query: String, mark: Color): AnnotatedString {
    val q = query.trim()
    val at = text.indexOf(q, ignoreCase = true)
    if (q.isEmpty() || at < 0) return AnnotatedString(text)
    var start = (at - SNIPPET_BEFORE).coerceAtLeast(0)
    if (start > 0) start = text.indexOf(' ', start).takeIf { it in start until at }?.plus(1) ?: start
    return buildAnnotatedString {
        if (start > 0) append("…")
        append(text.substring(start, at))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = mark)) { append(text.substring(at, at + q.length)) }
        append(text.substring(at + q.length))
    }
}

private const val SNIPPET_BEFORE = 40
