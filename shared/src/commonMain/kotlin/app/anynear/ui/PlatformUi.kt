package app.anynear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf

/**
 * Intercept going back, e.g. from the chat to the overview: the system's back button on Android, the swipe from the
 * left edge on iOS.
 */
@Composable
internal expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * How far the finger has pulled the screen while swiping back from the left edge (iOS), 0 to 1; 0 when no swipe is
 * running. The app lets the content follow the finger.
 */
internal object BackSwipe {
    val progress = mutableFloatStateOf(0f)
}

/** Does Enter send in the chat (Mac) instead of starting a new line? Never on phone and tablet. */
internal expect val enterSends: Boolean

/** The screen stays on as long as this is in the composition (updating the radio, ADR 0056). */
@Composable
internal expect fun KeepScreenOn()

/**
 * Opens the picker in which the person chooses the USB drive of the radio (rescue via USB, ADR 0056). Returns the
 * function for opening; [onPicked] gets the drive or `null`.
 */
@Composable
internal expect fun rememberUf2DrivePicker(onPicked: (app.anynear.mesh.Uf2Drive?) -> Unit): () -> Unit
