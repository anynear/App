package app.anynear.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

/**
 * Every dialog of the app. A tap next to it does not close it: people tap a dimmed screen to wake it, and a code
 * written down halfway or a Wi-Fi password was gone (user test 14.11.2026). It closes only through its buttons or the
 * back gesture ([onDismissRequest]). `AppDialogTest` makes sure no screen uses `AlertDialog` directly.
 */
@Composable
internal fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        properties = AppDialogProperties,
    )
}

/** Properties of the app's dialogs: only buttons and the back gesture close them ([AppDialog]). */
internal val AppDialogProperties = DialogProperties(dismissOnClickOutside = false)

/** The same for full-screen dialogs, which set their own width. */
internal val AppFullScreenDialogProperties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)
