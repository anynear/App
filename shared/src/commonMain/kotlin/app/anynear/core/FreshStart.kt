package app.anynear.core

import app.anynear.attachments.BinaryFiles
import app.anynear.data.KeyValueStore
import app.anynear.data.RawKeyValueAccess
import app.anynear.data.SecretStore
import app.anynear.data.TextFile

/**
 * Fresh start for „Gerät zurücksetzen“ and „Alle Daten löschen“ (ADR 0046): [request] notes it, the app restarts
 * and deletes everything here before anything is opened. Only the access to the test version (Beta Key) is kept,
 * because it belongs to the device, not to the circle. There is no state from the time before anynear: the app ID
 * `app.anynear` starts empty (ADR 0061, decision 3). After that the app is as if freshly installed, without a notice.
 * If another own device has removed this one (ADR 0042 §6), the app notes the fresh start immediately, with a notice
 * ([REMOVED]): at the latest on the next opening everything is gone, even if the app does not restart before that.
 */
object FreshStart {
    /** Marker in the key-value store (unencrypted, without content): still show the notice. */
    const val NOTICE_KEY = "app.fresh_start_notice"

    /** Marker in the key-value store (unencrypted, without content): delete everything on the next start. */
    const val REQUEST_KEY = "app.reset_pending"

    /** Value for [REQUEST_KEY] and [NOTICE_KEY]: removed by another own device. */
    const val REMOVED = "removed"


    /**
     * Before everything else at start: deletes everything if the fresh start is noted. `true` if a fresh start was made.
     * [binaryDirs] and [textFiles] are all folders and files of the app on the device.
     */
    fun runIfNeeded(
        keyValues: RawKeyValueAccess,
        secrets: SecretStore,
        secretKeys: Set<String>,
        binaryDirs: List<BinaryFiles>,
        textFiles: List<TextFile>,
    ): Boolean {
        if (!requested(keyValues)) return false
        val removed = keyValues.entries()[REQUEST_KEY] == REMOVED
        wipe(keyValues, secrets, secretKeys, binaryDirs, textFiles, notice = if (removed) REMOVED else null)
        return true
    }

    fun requested(keyValues: RawKeyValueAccess): Boolean = keyValues.entries()[REQUEST_KEY].let { it == "1" || it == REMOVED }

    /**
     * Note it: on the next start everything on this device is gone, only the Beta Key stays. Restart after that.
     * [removed]: because another own device has removed this one; show the notice once after deleting.
     */
    fun request(keyValues: RawKeyValueAccess, removed: Boolean = false) =
        keyValues.replaceAll(keyValues.entries() + (REQUEST_KEY to if (removed) REMOVED else "1"))

    private fun wipe(
        keyValues: RawKeyValueAccess,
        secrets: SecretStore,
        secretKeys: Set<String>,
        binaryDirs: List<BinaryFiles>,
        textFiles: List<TextFile>,
        notice: String?,
    ) {
        secretKeys.forEach { secrets.put(it, null) }
        keyValues.replaceAll(notice?.let { mapOf(NOTICE_KEY to it) } ?: emptyMap())
        binaryDirs.forEach { dir -> dir.names().forEach(dir::delete) }
        textFiles.forEach { it.write("") }
    }

    /** [plain]: key-value store of the platform without encryption. */
    fun noticePending(plain: KeyValueStore): Boolean = plain.getString(NOTICE_KEY) == "1"

    /** Deleted because another own device has removed this one: point it out once. */
    fun removedNoticePending(plain: KeyValueStore): Boolean = plain.getString(NOTICE_KEY) == REMOVED

    fun noticeShown(plain: KeyValueStore) = plain.putString(NOTICE_KEY, null)
}
