package app.anynear.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow

/**
 * Keeps a chat list at its end while the reader is there: when the chat opens, when the keyboard comes up (the list
 * gets shorter, but a LazyColumn keeps its top in place, so the newest messages would slide under the input) and
 * when new messages arrive. Whoever has scrolled up stays where they are. When [newestOutgoingKey] changes (one's own
 * message was just added), the list goes to the end in any case.
 */
@Composable
internal fun StickToEnd(listState: LazyListState, key: Any?, newestOutgoingKey: Any?) {
    LaunchedEffect(listState, key) {
        // At the start (chat just opened, messages possibly still loading) the end counts as the place to be.
        var atEnd = true
        var lastHeight = -1
        var lastCount = 0
        snapshotFlow {
            val info = listState.layoutInfo
            Triple(info.viewportSize.height, info.totalItemsCount, listState.canScrollForward)
        }.collect { (height, count, canScrollForward) ->
            val heightChanged = height != lastHeight
            val countChanged = count != lastCount
            val firstFill = lastCount == 0
            lastHeight = height
            lastCount = count
            if (heightChanged || countChanged) {
                // Keyboard (height, follows every frame of its animation) and opening: at once; a new message: gently.
                if (atEnd && count > 0) listState.scrollToEnd(animate = !heightChanged && !firstFill)
            } else {
                atEnd = !canScrollForward
            }
        }
    }
    // Only a change counts: the newest own message that is already there when the chat opens is no reason to move.
    val initialOutgoing = remember(listState, key) { newestOutgoingKey }
    LaunchedEffect(listState, newestOutgoingKey) {
        if (newestOutgoingKey != null && newestOutgoingKey != initialOutgoing) listState.scrollToEnd(animate = true)
    }
}

/** Scrolls to the very end, also when the last item is taller than the list. */
internal suspend fun LazyListState.scrollToEnd(animate: Boolean = false) {
    val last = layoutInfo.totalItemsCount - 1
    if (last < 0) return
    if (animate) animateScrollToItem(last) else scrollToItem(last)
    // scrollToItem puts the top of the item at the top; a long message would stay cut off at the bottom.
    val step = layoutInfo.viewportSize.height.coerceAtLeast(1).toFloat()
    repeat(20) { if (canScrollForward) scrollBy(step) else return }
}
