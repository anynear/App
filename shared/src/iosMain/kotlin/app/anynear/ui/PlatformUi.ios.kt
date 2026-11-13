package app.anynear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.Foundation.NSProcessInfo
import platform.Foundation.isiOSAppOnMac
import platform.UIKit.UIApplication

/** iOS has no back button; going back works via the arrow at the top left. */
@Composable
internal actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

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
