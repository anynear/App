package app.anynear.ui

import androidx.compose.runtime.Composable

/** Intercept the system's back button (Android), e.g. from the chat back to the overview. */
@Composable
internal expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)

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
