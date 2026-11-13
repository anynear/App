package app.anynear.passwords

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.anynear.attachments.BinaryFiles
import app.anynear.attachments.IosBinaryFiles
import app.anynear.attachments.toByteArray
import app.anynear.attachments.toNSData
import app.anynear.data.APP_GROUP
import app.anynear.data.platformDataDir
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.UIKit.UIDevice
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardOptionExpirationDate
import platform.UIKit.UIPasteboardOptionLocalOnly
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Keychain and Face ID for passwords (ADR 0039, item 2), implemented in Swift
 * (`iosApp/Passwords/PasswordKeychain.swift`) because the password extension needs the same code. The app sets
 * it on start via `MainViewControllerKt.setPasswordKeychain`.
 */
interface IosPasswordKeychain {
    fun deviceSecure(): Boolean

    fun hasKey(): Boolean

    fun store(key: NSData): Boolean

    fun remove()

    /** Face ID, Touch ID or device passcode, then read the key. [done]: key or `null` with reason. */
    fun unlock(reason: String, done: (NSData?, String) -> Unit)

    fun checkAutofill(done: (Boolean) -> Unit)

    fun openAutofillSettings()

    fun openDeviceLockSettings()

    /**
     * Identities for the `ASCredentialIdentityStore` (ADR 0052, item 4) as a JSON list of [IosIdentity]; replaces the
     * previous ones.
     */
    fun publishIdentities(json: String)

    companion object {
        /** Reasons from [unlock] when no key comes. */
        const val CANCELLED = "cancelled"
        const val NO_DEVICE_LOCK = "no-device-lock"
        const val KEY_LOST = "key-lost"
    }
}

internal var iosPasswordKeychain: IosPasswordKeychain? = null

/** Without the Swift side (tests, preview): no device lock, hence no passwords. */
private class KeychainAccess : PasswordKeyStore {
    private val keychain get() = iosPasswordKeychain

    override fun deviceSecure(): Boolean = keychain?.deviceSecure() == true

    override fun state(): PasswordKeyState = if (keychain?.hasKey() == true) PasswordKeyState.PRESENT else PasswordKeyState.NONE

    override fun store(key: ByteArray): Boolean = keychain?.store(key.toNSData()) == true

    override fun delete() {
        keychain?.remove()
    }
}

actual fun platformPasswordKeyStore(): PasswordKeyStore = KeychainAccess()

/** In the app group container that the password extension reads; without one (build without team ID) at the app. */
@OptIn(ExperimentalForeignApi::class)
actual fun platformPasswordFiles(): BinaryFiles {
    val group = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP)?.path
    return IosBinaryFiles("${group ?: platformDataDir()}/${PasswordStore.DIR_NAME}")
}

@Composable
internal actual fun rememberPasswordUnlock(onResult: (UnlockResult) -> Unit): (reason: String) -> Unit {
    val callback = rememberUpdatedState(onResult)
    return remember {
        { reason ->
            val keychain = iosPasswordKeychain
            if (keychain == null) {
                callback.value(UnlockResult.NoDeviceLock)
            } else {
                keychain.unlock(reason) { data, why ->
                    val result = when {
                        data != null -> UnlockResult.Unlocked(data.toByteArray())
                        why == IosPasswordKeychain.NO_DEVICE_LOCK -> UnlockResult.NoDeviceLock
                        why == IosPasswordKeychain.KEY_LOST -> UnlockResult.KeyLost
                        else -> UnlockResult.Cancelled
                    }
                    dispatch_async(dispatch_get_main_queue()) { callback.value(result) }
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun rememberDeviceCheck(onResult: (Boolean?) -> Unit): (reason: String) -> Unit {
    val callback = rememberUpdatedState(onResult)
    return remember {
        { reason ->
            val context = LAContext()
            if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, null)) {
                callback.value(null)
            } else {
                context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, reason) { ok, _ ->
                    dispatch_async(dispatch_get_main_queue()) { callback.value(ok) }
                }
            }
        }
    }
}

internal actual fun checkAutofillEnabled(done: (Boolean) -> Unit) {
    val keychain = iosPasswordKeychain ?: return done(false)
    keychain.checkAutofill { enabled -> dispatch_async(dispatch_get_main_queue()) { done(enabled) } }
}

internal actual fun openAutofillSettings() {
    iosPasswordKeychain?.openAutofillSettings()
}

internal actual fun openDeviceLockSettings() {
    iosPasswordKeychain?.openDeviceLockSettings()
}

@OptIn(BetaInteropApi::class)
actual fun copySecret(text: String) {
    UIPasteboard.generalPasteboard.setItems(
        listOf(mapOf<Any?, Any?>("public.utf8-plain-text" to text)),
        options = mapOf<Any?, Any?>(
            UIPasteboardOptionLocalOnly to true,
            UIPasteboardOptionExpirationDate to NSDate.dateWithTimeIntervalSinceNow(60.0),
        ),
    )
}

internal actual fun passkeysSupported(): Boolean =
    (UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull() ?: 0) >= 17

/** A login as the Swift side enters it into the `ASCredentialIdentityStore`: without password, code or key. */
@Serializable
private class IosIdentity(
    val id: String,
    val website: String,
    val username: String,
    val password: Boolean,
    val otp: Boolean,
    val passkeys: List<IosPasskeyIdentity>,
)

@Serializable
private class IosPasskeyIdentity(val rpId: String, val userName: String, val credentialId: String, val userHandle: String)

actual fun publishCredentialIdentities(logins: List<Login>) {
    val keychain = iosPasswordKeychain ?: return
    val identities = logins.map { l ->
        IosIdentity(
            id = l.id,
            website = LoginMatch.host(l.website) ?: l.website.trim(),
            username = l.username,
            password = l.password.isNotEmpty(),
            otp = l.otp != null,
            passkeys = l.passkeys.map { IosPasskeyIdentity(it.rpId, it.userName, it.credentialId, it.userHandle) },
        )
    }
    keychain.publishIdentities(Json.encodeToString(ListSerializer(IosIdentity.serializer()), identities))
}
