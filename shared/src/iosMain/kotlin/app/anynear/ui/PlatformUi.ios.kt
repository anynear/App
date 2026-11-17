package app.anynear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import platform.Foundation.NSProcessInfo
import platform.Foundation.isiOSAppOnMac
import platform.UIKit.UIApplication

/**
 * iOS has no back button: besides the arrow at the top left, the usual swipe from the left edge leads back.
 * ComposeUIViewController recognises the edge swipe itself (only from the edge, so swiping on messages to reply or
 * scrolling sideways stays untouched) and hands it to the innermost enabled handler; while the finger moves, its
 * progress goes to [BackSwipe] so the content follows.
 */
@Composable
internal actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val state = rememberNavigationEventState(NavigationEventInfo.None)
    val transition = state.transitionState
    val progress = (transition as? NavigationEventTransitionState.InProgress)?.latestEvent?.progress ?: 0f
    if (enabled) {
        LaunchedEffect(progress) { BackSwipe.progress.floatValue = progress }
        DisposableEffect(Unit) { onDispose { BackSwipe.progress.floatValue = 0f } }
    }
    NavigationBackHandler(
        state = state,
        isBackEnabled = enabled,
        onBackCancelled = { BackSwipe.progress.floatValue = 0f },
        onBackCompleted = {
            BackSwipe.progress.floatValue = 0f
            onBack()
        },
    )
}

/** The iPad app on the Mac (Apple Silicon): Enter sends as in other Mac programs. */
internal actual val enterSends: Boolean = NSProcessInfo.processInfo.isiOSAppOnMac()

@Composable
internal actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

@Composable
internal actual fun rememberUf2DrivePicker(onPicked: (app.anynear.mesh.Uf2Drive?) -> Unit): () -> Unit = { onPicked(null) }
