package app.anynear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.attachments.Attachment
import app.anynear.attachments.AttachmentState
import app.anynear.attachments.DraftAttachment
import app.anynear.attachments.VoicePreview
import app.anynear.attachments.formatDuration
import app.anynear.i18n.texts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One playback for the whole chat: if another voice message starts, it pauses the previous one.
 * [current] stays in place after pausing, so that it continues there.
 */
@Stable
internal class VoicePlayback(private val player: VoicePlayer) {
    var current by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var positionMillis by mutableLongStateOf(0L)
        private set

    /** Play or pause; `false` if [bytes] cannot be played. */
    fun toggle(key: String, bytes: ByteArray): Boolean {
        if (current == key && playing) {
            player.pause()
            positionMillis = player.positionMillis()
            playing = false
            return true
        }
        val from = if (current == key) positionMillis else 0
        if (!player.play(bytes, from)) {
            current = null
            playing = false
            return false
        }
        current = key
        positionMillis = from
        playing = true
        return true
    }

    /** Keeps the position up to date and notes the end; runs as long as the chat is open. */
    suspend fun follow() {
        while (true) {
            if (playing) {
                if (player.isPlaying()) {
                    positionMillis = player.positionMillis()
                } else {
                    // At the end: on the next tap from the start.
                    playing = false
                    positionMillis = 0
                }
            }
            delay(TICK_MILLIS)
        }
    }

    fun stop() {
        player.pause()
        playing = false
    }

    fun release() = player.release()
}

internal val LocalVoicePlayback = compositionLocalOf<VoicePlayback?> { null }

@Composable
internal fun rememberVoicePlayback(): VoicePlayback {
    val playback = remember { VoicePlayback(createVoicePlayer()) }
    LaunchedEffect(playback) { playback.follow() }
    DisposableEffect(playback) { onDispose { playback.release() } }
    return playback
}

/**
 * Play/pause, progress and duration. [load] supplies the recording; as long as it is not on the
 * phone ([available]), [status] is shown there.
 */
@Composable
private fun VoiceRow(
    key: String,
    durationMillis: Long,
    available: Boolean,
    status: String?,
    load: suspend () -> ByteArray?,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = LocalVoicePlayback.current ?: rememberVoicePlayback()
    val scope = rememberCoroutineScope()
    val active = playback.current == key
    val playing = active && playback.playing
    val position = if (active) playback.positionMillis else 0
    Row(
        modifier
            .width(VOICE_WIDTH)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                scope.launch {
                    val bytes = load()
                    if (bytes == null || !playback.toggle(key, bytes)) onError(texts.chat.voiceCantPlay)
                }
            },
            enabled = available,
        ) {
            Icon(
                if (playing) AppIcons.Anhalten else AppIcons.Abspielen,
                contentDescription = if (playing) texts.chat.pause else texts.chat.play,
            )
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            LinearProgressIndicator(
                progress = { if (durationMillis > 0) (position.toFloat() / durationMillis).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                status ?: if (active && position > 0) "${formatDuration(position)} / ${formatDuration(durationMillis)}" else formatDuration(durationMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Voice message in the speech bubble. If the recording is still missing, it says that it will follow, without a cause. */
@Composable
internal fun MessageVoice(controller: AppController, attachment: Attachment, onError: (String) -> Unit) {
    val local = attachment.state == AttachmentState.READY || attachment.state == AttachmentState.UPLOADING
    // The same bytes on every tap, so that playback continues there.
    var bytes by remember(attachment.blobId) { mutableStateOf<ByteArray?>(null) }
    VoiceRow(
        key = attachment.blobId,
        durationMillis = attachment.durationMillis,
        available = local,
        status = when (attachment.state) {
            AttachmentState.DOWNLOADING -> texts.chat.loadsWhenOnline
            AttachmentState.MISSING -> texts.chat.noLongerAvailable
            else -> null
        },
        load = { bytes ?: controller.attachmentBytes(attachment.blobId).also { bytes = it } },
        onError = onError,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/**
 * Recorded voice message that has not been sent yet, above the input field, with play and remove.
 * Below it what goes along over radio: the text preview ([preview], still in progress with [recognizing]) or [hint].
 */
@Composable
internal fun DraftVoice(
    draft: DraftAttachment,
    recognizing: Boolean,
    preview: String?,
    hint: String?,
    onRemove: () -> Unit,
    onError: (String) -> Unit,
) {
    val playback = LocalVoicePlayback.current
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoiceRow(
                key = "draft:${draft.hashCode()}",
                durationMillis = draft.durationMillis,
                available = true,
                status = null,
                load = { draft.bytes },
                onError = onError,
            )
            IconButton(onClick = {
                playback?.stop()
                onRemove()
            }) { Icon(AppIcons.Schliessen, contentDescription = texts.chat.removeAttachment) }
        }
        when {
            recognizing -> PreviewNote(texts.chat.textPreviewInProgress, null)
            preview != null -> PreviewNote(previewLabel(preview), preview)
            hint != null -> PreviewNote(hint, null)
        }
    }
}

@Composable
private fun PreviewNote(label: String, text: String?) {
    Column(Modifier.width(VOICE_WIDTH).padding(top = 4.dp, end = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** „Textvorschau“ or „Textvorschau · gekürzt“, never „Zusammenfassung“ (ADR 0030). */
internal fun previewLabel(text: String): String =
    if (VoicePreview.isShortened(text)) texts.chat.textPreviewShortened else texts.chat.textPreview

/** Above the text of a voice message if it is its automatic text preview. */
@Composable
internal fun TextPreviewLabel(text: String, color: Color) {
    Text(previewLabel(text), style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(bottom = 2.dp))
}

/** Replaces the input field as long as recording is in progress: red dot, time, discard, send. */
@Composable
internal fun VoiceRecordingBar(elapsedMillis: Long, onDiscard: () -> Unit, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppButton(texts.chat.discardRecording, onDiscard, kind = ButtonKind.Quiet)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.error, CircleShape))
        Text(formatDuration(elapsedMillis), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        // „Senden“ ends the recording and sends it right away, as in common messengers.
        FilledIconButton(
            onClick = onDone,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
            modifier = Modifier.size(52.dp),
        ) { Icon(AppIcons.Senden, contentDescription = texts.chat.send) }
    }
}

private val VOICE_WIDTH = 240.dp
private const val TICK_MILLIS = 100L
