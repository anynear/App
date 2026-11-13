package app.anynear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.anynear.attachments.toByteArray
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

/** UIKit holds the delegate only weakly. */
private var backupDelegate: BackupDocumentDelegate? = null

@Composable
internal actual fun rememberBackupFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    return remember {
        {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
            val delegate = BackupDocumentDelegate { callback.value(it) }
            backupDelegate = delegate
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class BackupDocumentDelegate(private val done: (PickedFile) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.filterIsInstance<NSURL>().firstOrNull() ?: return
        val name = url.lastPathComponent ?: ""
        val data = NSData.dataWithContentsOfURL(url)
        done(
            when {
                data == null -> PickedFile(null, name)
                data.length.toLong() > MAX_BACKUP_BYTES -> PickedFile(null, name, tooLarge = true)
                else -> PickedFile(data.toByteArray(), name)
            },
        )
    }
}
