package app.anynear.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager

@Composable
internal actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

internal actual val enterSends: Boolean = false

/**
 * Sets “keep screen on” on the window this is placed in (a dialog has its own window too), and on the
 * activity: if a full-screen dialog lies on top, the covered window of the activity no longer counts.
 */
@Composable
internal actual fun KeepScreenOn() {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(view, activity) {
        view.keepScreenOn = true
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            view.keepScreenOn = false
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal actual fun rememberUf2DrivePicker(onPicked: (app.anynear.mesh.Uf2Drive?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> onPicked(uri?.let { app.anynear.mesh.AndroidUf2Drive(context.applicationContext, it) }) }
    return { launcher.launch(null) }
}
