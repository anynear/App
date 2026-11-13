package app.anynear.passwords

import androidx.compose.runtime.Composable
import app.anynear.attachments.BinaryFiles

/**
 * Storage of the password key `pv` in the operating system (ADR 0039, item 2). Reading only works through
 * [rememberPasswordUnlock], i.e. only after the device lock; storing needs no confirmation.
 */
interface PasswordKeyStore {
    /** Is a device lock (PIN, pattern, password, device passcode) set up? */
    fun deviceSecure(): Boolean

    fun state(): PasswordKeyState

    /** Stores [key] protected and replaces an existing one. `false` if that is not possible without a device lock. */
    fun store(key: ByteArray): Boolean

    fun delete()
}

enum class PasswordKeyState {
    /** No key yet. */
    NONE,
    PRESENT,

    /** The system discarded the key because the device lock was turned off. */
    INVALIDATED,
}

sealed interface UnlockResult {
    class Unlocked(val key: ByteArray) : UnlockResult
    data object Cancelled : UnlockResult
    data object NoDeviceLock : UnlockResult

    /** Key gone or invalid: the logins on this phone can no longer be read. */
    data object KeyLost : UnlockResult
}

expect fun platformPasswordKeyStore(): PasswordKeyStore

/** Folder with the file [VaultFile.NAME]; on iOS in the app group's container so that the extension reads it. */
expect fun platformPasswordFiles(): BinaryFiles

/**
 * Ask for the device lock (shown by the system, never an input of our own) and then read the key. Calling the
 * result with a short reason starts the prompt; [onResult] arrives on the main thread.
 */
@Composable
internal expect fun rememberPasswordUnlock(onResult: (UnlockResult) -> Unit): (reason: String) -> Unit

/**
 * Only have the device lock confirmed, without a key (ADR 0042: add a device). [onResult]: `true` confirmed,
 * `false` cancelled, `null` without a device lock; arrives on the main thread.
 */
@Composable
internal expect fun rememberDeviceCheck(onResult: (Boolean?) -> Unit): (reason: String) -> Unit

/** Is anynear set up as the provider for autofill? [done] arrives on the main thread. */
internal expect fun checkAutofillEnabled(done: (Boolean) -> Unit)

/** Open the system setting for the password provider. */
internal expect fun openAutofillSettings()

/** Open the system setting for the device lock. */
internal expect fun openDeviceLockSettings()

/** Password to the clipboard, marked as confidential (Android) or local only and for 60 seconds (iOS). */
expect fun copySecret(text: String)

/** Can this device use passkeys from anynear (Android 14, iOS 17; ADR 0052)? */
internal expect fun passkeysSupported(): Boolean

/**
 * Identifiers of the logins for the system's suggestions (ADR 0052, item 4): website, user name, for passkeys their
 * identifiers; never passwords, codes or keys. Android stores them for the passkey provider, iOS in the
 * `ASCredentialIdentityStore`.
 */
expect fun publishCredentialIdentities(logins: List<Login>)

/** What the system may know about a passkey without unlocking (ADR 0052, item 4). */
@kotlinx.serialization.Serializable
class PasskeyIdentity(val rpId: String, val userName: String, val credentialId: String)
