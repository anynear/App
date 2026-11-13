package app.anynear.ui

import androidx.compose.runtime.Composable

/** Maximum size of a backup file when reading it in; it is decrypted as a whole. */
internal const val MAX_BACKUP_BYTES = 512L * 1024 * 1024

/**
 * Picker for a backup file from Files, Downloads, cloud storage etc. (ADR 0027). Calling the
 * result opens the picker; [onPicked] receives the file, nothing if it was cancelled.
 */
@Composable
internal expect fun rememberBackupFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit
