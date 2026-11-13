package app.anynear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberBackupFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onPicked)
    // Any file: depending on where it is stored, Android does not know the type of a .backup file.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { callback.value(withContext(Dispatchers.IO) { readPickedFile(uri, MAX_BACKUP_BYTES) }) }
    }
    return remember(launcher) { { launcher.launch(arrayOf("*/*")) } }
}
