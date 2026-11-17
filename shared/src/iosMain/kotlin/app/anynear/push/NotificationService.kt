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
import app.anynear.core.WakeOutcome
import app.anynear.AppConfig
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
 * from the shared keychain group and calls [completion] with title and text ([WakeOutcome.found]); without it
 * nothing was found or there was no time left, the fixed text stays ([WakeOutcome.nothingToShow], ADR 0117). [completion] comes exactly once, at the latest after
 * 20 seconds (iOS gives the extension about 30).
 */
@Suppress("unused")
fun handleWake(objectId: String?, completion: (WakeOutcome) -> Unit) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    scope.launch {
        val result = try {
            withTimeoutOrNull(WAKE_TIMEOUT) { fetchContent(scope, objectId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        completion(result ?: WakeOutcome(null, nothingToShow = false))
        scope.cancel()
    }
}

/**
 * The extension may swallow wake-ups with nothing to show (ADR 0117): only with Apple's entitlement
 * `com.apple.developer.usernotifications.filtering` ([AppConfig.FILTER_EMPTY_WAKES]). Without it they stay, but quiet.
 */
@Suppress("unused")
val filterEmptyWakes: Boolean get() = AppConfig.FILTER_EMPTY_WAKES

/**
 * Alert in the wake-up (ADR 0062): set level and sound from [found], as for notifications of the running app. For
 * messages without a level nothing changes.
 */
@Suppress("unused")
fun applyWake(content: UNMutableNotificationContent, found: WakeNotification) {
    val level = found.level ?: return
    AlertNotifications.apply(content, level, found.sound)
}

/**
 * Category for the notification of the conversation [key]: [REPLY_CATEGORY] with „Antworten“ for circle chats and direct
 * messages, `null` (the fixed category `message` of the wake-up stays) for everything else.
 */
@Suppress("unused")
fun replyCategory(key: String): String? = REPLY_CATEGORY.takeIf { app.anynear.chats.conversationOf(key) != null }

/** Category with „Antworten“ (text input with ready-made replies on the Apple Watch); registered by the app. */
const val REPLY_CATEGORY = "reply"

/** Action „Antworten“ in [REPLY_CATEGORY]. */
const val REPLY_ACTION = "reply"

private val WAKE_TIMEOUT = 20.seconds

/** Group of the notifications with fixed text (nothing found); the app clears them away once it fetches itself. */
const val WAKE_THREAD = "wake"

private suspend fun fetchContent(scope: CoroutineScope, objectId: String?): WakeOutcome? {
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
    return WakeTexts(core, store).outcomeFor(objectId)
}
