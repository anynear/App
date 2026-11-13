package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.attachments.Attachment
import app.anynear.attachments.AttachmentKind
import app.anynear.i18n.texts
import app.anynear.mesh.ChatMessage
import app.anynear.mesh.Conversation
import kotlin.time.Clock

// Photos and files of a conversation: everything that was sent as an attachment in the chat, on the detail pages of
// circle and person. Unlike the storage (preview) this is not a store of its own, only a different view of
// the attachments in the history; ones not loaded show the same placeholder as in the chat.

/** An attachment together with the message it came with. */
internal class ChatFile(val message: ChatMessage, val attachment: Attachment)

/** All attachments of a conversation, newest first. */
internal fun chatFiles(messages: List<ChatMessage>, conversation: Conversation): List<ChatFile> =
    messages.filter { it.conversation == conversation && it.system == null }
        .sortedByDescending { it.timestampMillis }
        .flatMap { m -> m.attachments.map { ChatFile(m, it) } }

@Composable
private fun rememberChatFiles(controller: AppController, conversation: Conversation): List<ChatFile> {
    val messages by controller.messages.collectAsState()
    return remember(messages, conversation) { chatFiles(messages, conversation) }
}

/**
 * „Fotos und Dateien“ section on a detail page: the newest photos as a row, below it the path to
 * everything. Without attachments only a calm sentence.
 */
@Composable
internal fun ChatFilesSection(controller: AppController, conversation: Conversation, onOpenAll: () -> Unit) {
    val files = rememberChatFiles(controller, conversation)
    val images = files.map { it.attachment }.filter { it.kind == AttachmentKind.IMAGE }
    var viewerStart by remember { mutableStateOf<Int?>(null) }
    AppSection(texts.chat.photosAndFiles) {
        if (files.isEmpty()) {
            AppRow(title = texts.chat.noFilesYet, icon = AppIcons.Bild)
            return@AppSection
        }
        if (images.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val shown = images.take(PREVIEW_COUNT)
                shown.forEachIndexed { i, a ->
                    val more = if (i == shown.lastIndex) images.size - shown.size else 0
                    ImageTile(controller, a, Modifier.weight(1f).aspectRatio(1f), more) { viewerStart = i }
                }
                // Tiles of equal size, even if there are fewer photos.
                repeat(PREVIEW_COUNT - shown.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        AppRow(
            title = texts.chat.showAllFiles(files.size),
            icon = AppIcons.Dokument,
            chevron = true,
            onClick = onOpenAll,
        )
    }
    viewerStart?.let { start -> ImageViewer(controller, images, start) { viewerStart = null } }
}

/**
 * Page with all photos, documents and voice messages of a conversation, each newest first. Photos as
 * a grid (tap: full screen), documents and voice messages with sender and time.
 */
@Composable
internal fun ChatFilesScreen(controller: AppController, conversation: Conversation, title: String, onBack: () -> Unit) {
    val files = rememberChatFiles(controller, conversation)
    val images = files.filter { it.attachment.kind == AttachmentKind.IMAGE }.map { it.attachment }
    val pdfs = files.filter { it.attachment.kind == AttachmentKind.PDF || it.attachment.kind == AttachmentKind.FILE }
    val voices = files.filter { it.attachment.kind == AttachmentKind.AUDIO }
    var viewerStart by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val now = remember(files) { Clock.System.now().toEpochMilliseconds() }
    val origin: (ChatFile) -> String = { f ->
        val who = if (f.message.outgoing) texts.status.youSender else controller.senderLabel(f.message)
        "$who · ${formatChatListTime(f.message.timestampMillis, now)}"
    }

    CompositionLocalProvider(LocalVoicePlayback provides rememberVoicePlayback()) {
        AppHeader(title = texts.chat.photosAndFiles, subtitle = title, leading = { HeaderBackButton(onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (files.isEmpty()) {
                Text(texts.chat.noFilesYet, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (images.isNotEmpty()) {
                AppSection(texts.chat.photos) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        images.chunked(GRID_COLUMNS).forEachIndexed { row, chunk ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                chunk.forEachIndexed { col, a ->
                                    ImageTile(controller, a, Modifier.weight(1f).aspectRatio(1f)) { viewerStart = row * GRID_COLUMNS + col }
                                }
                                repeat(GRID_COLUMNS - chunk.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            if (pdfs.isNotEmpty()) {
                AppSection(texts.chat.documents) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        pdfs.forEach { f ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FileOrigin(origin(f))
                                PdfRow(controller, f.attachment, onError = { error = it }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
            if (voices.isNotEmpty()) {
                AppSection(texts.chat.voiceMessages) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        voices.forEach { f ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FileOrigin(origin(f))
                                MessageVoice(controller, f.attachment, onError = { error = it })
                            }
                        }
                    }
                }
            }
        }
    }
    viewerStart?.let { start -> ImageViewer(controller, images, start) { viewerStart = null } }
}

@Composable
private fun FileOrigin(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Photos in the row on the detail page. */
private const val PREVIEW_COUNT = 4

/** Columns in the photo grid of the „Fotos und Dateien“ page. */
private const val GRID_COLUMNS = 3
