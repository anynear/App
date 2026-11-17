package app.anynear.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** From this distance on, letting go answers the message (same value as in the web app). */
private val REPLY_TRIGGER = 56.dp

/** The message does not follow the finger further than this. */
private val REPLY_MAX = 80.dp

/**
 * Pull a message to the right to answer it (ADR 0092), as in other messengers: the message follows the finger, the
 * answer arrow appears on the left; beyond [REPLY_TRIGGER] a short vibration says „let go now“, letting go starts the
 * answer, before that the message springs back. Only sideways, so scrolling up and down stays as it is; the swipe from
 * the screen edge (back on iOS) starts further out. Without [onReply] (cannot be answered) nothing moves.
 */
@Composable
internal fun SwipeToReply(onReply: (() -> Unit)?, content: @Composable () -> Unit) {
    if (onReply == null) {
        content()
        return
    }
    val density = LocalDensity.current
    val trigger = with(density) { REPLY_TRIGGER.toPx() }
    val max = with(density) { REPLY_MAX.toPx() }
    val haptics = LocalHapticFeedback.current
    var pulled by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val state = rememberDraggableState { delta ->
        // Beyond the trigger point it gets heavier, so that the end is felt.
        val next = (pulled + if (pulled >= trigger && delta > 0) delta * 0.4f else delta).coerceIn(0f, max)
        pulled = next
        val nowArmed = next >= trigger
        if (nowArmed && !armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        armed = nowArmed
    }
    Box(
        Modifier.fillMaxWidth().draggable(
            state,
            Orientation.Horizontal,
            onDragStopped = {
                if (armed) onReply()
                armed = false
                animate(pulled, 0f) { value, _ -> pulled = value }
            },
        ),
    ) {
        if (pulled > 0f) {
            val shown = (pulled / trigger).coerceIn(0f, 1f)
            Icon(
                AppIcons.Antworten,
                contentDescription = null,
                tint = if (armed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .size(24.dp)
                    .graphicsLayer {
                        alpha = shown
                        scaleX = 0.6f + 0.4f * shown
                        scaleY = 0.6f + 0.4f * shown
                    },
            )
        }
        Box(Modifier.offset { IntOffset(pulled.roundToInt(), 0) }) { content() }
    }
}
