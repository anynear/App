package app.anynear.push

import app.anynear.anmp.core.AnmpCore
import app.anynear.anmp.core.AnmpPlatform
import app.anynear.attachments.platformBinaryFiles
import app.anynear.core.CoreBlobs
import app.anynear.core.CoreKeyValues
import app.anynear.core.CoreSecretKeys
import app.anynear.core.CoreSecrets
import app.anynear.core.LocalKey
import app.anynear.core.AppNet
import app.anynear.core.WakeNotification
import app.anynear.core.WakeTexts
import app.anynear.data.EncryptedKeyValueStore
import app.anynear.data.platformKeyValueStore
import app.anynear.data.platformSecretStore
import kotlinx.coroutines.CancellationException
import platform.UserNotifications.UNMutableNotificationContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Entry for the Notification Service Extension (`iosApp/NotificationService`), ADR 0009.
 *
 * The wake-up of a relay carries only the fixed text and the ANDP object of the new message (`o`, ANDP §10). The
 * extension fetches the messages itself via the ANMP core (`wakeFetch`), decrypts them with the keys
 * from the shared keychain group and calls [completion] with title and text; `null` means: nothing
 * found or no time left, the fixed text stays. [completion] comes exactly once, at the latest after
 * 20 seconds (iOS gives the extension about 30).
 */
@Suppress("unused")
fun handleWake(objectId: String?, completion: (WakeNotification?) -> Unit) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    scope.launch {
        val result = try {
            withTimeoutOrNull(WAKE_TIMEOUT) { fetchContent(scope, objectId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        completion(result)
        scope.cancel()
    }
}

/**
 * Alert in the wake-up (ADR 0062): set level and sound from [found], as for notifications of the running app. For
 * messages without a level nothing changes.
 */
@Suppress("unused")
fun applyWake(content: UNMutableNotificationContent, found: WakeNotification) {
    val level = found.level ?: return
    AlertNotifications.apply(content, level, found.sound)
}

private val WAKE_TIMEOUT = 20.seconds

/** Group of the notifications with fixed text (nothing found); the app clears them away once it fetches itself. */
const val WAKE_THREAD = "wake"

private suspend fun fetchContent(scope: CoroutineScope, objectId: String?): WakeNotification? {
    val secrets = platformSecretStore()
    // Without a set-up app: nothing to read, create nothing. Before the first unlock after power-on the keychain
    // throws (SecretUnreadableException); handleWake catches it and the fixed text stays.
    if (secrets.get(CoreSecretKeys.DEVICE_SIGN) == null || secrets.get(CoreSecretKeys.INBOX_OWNER) == null) return null
    val cipher = LocalKey.existingCipher(secrets) ?: return null
    val store = EncryptedKeyValueStore(platformKeyValueStore(), cipher)
    val core = AnmpCore(
        scope,
        AppNet.config(),
        AnmpPlatform(CoreSecrets(secrets), CoreKeyValues(store), CoreBlobs(platformBinaryFiles(CoreBlobs.DIR_NAME), cipher), connector = AppNet.connector()),
    )
    core.open()
    return WakeTexts(core, store).contentFor(objectId)
}
