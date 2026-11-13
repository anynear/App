package app.anynear.data

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.CFBridgingRetain
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService
import kotlin.io.encoding.Base64

/**
 * Keychain, only on this device, not in backups of other devices.
 *
 * The entries end up in the first keychain group of the entitlements
 * (`…app.anynear.shared`), which app and Notification Service Extension share, and are
 * readable after the first unlock since power-on: the extension must be able to decrypt messages even
 * with the device locked (ADR 0009). The service name is brand-neutral (ADR 0061, decision 3).
 */
@OptIn(ExperimentalSettingsApi::class, ExperimentalSettingsImplementation::class, ExperimentalForeignApi::class)
private class KeychainSecretStore : SecretStore {
    private val keychain = sharedKeychain()

    /**
     * `KeychainSettings` gives `null` only for `errSecItemNotFound` and throws for every other status, e.g.
     * `errSecInteractionNotAllowed` before the first unlock after power-on (app or extension started in the
     * background). That stays an exception here, so nobody creates a new key in place of one that is only locked.
     */
    override fun get(key: String): ByteArray? {
        val stored = useKeychain("read $key") { getStringOrNull(key) } ?: return null
        return try {
            Base64.decode(stored)
        } catch (e: IllegalArgumentException) {
            null // Damaged for good: no way to read it again.
        }
    }

    override fun put(key: String, value: ByteArray?) {
        useKeychain("write $key") { if (value == null) remove(key) else putString(key, Base64.encode(value)) }
    }

    private inline fun <T> useKeychain(what: String, block: KeychainSettings.() -> T): T = try {
        keychain.block()
    } catch (e: Exception) {
        throw SecretUnreadableException("keychain: $what failed", e)
    }
}

@OptIn(ExperimentalSettingsApi::class, ExperimentalSettingsImplementation::class, ExperimentalForeignApi::class)
private fun sharedKeychain() = KeychainSettings(
    kSecAttrService to CFBridgingRetain("secrets"),
    kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
)

actual fun platformSecretStore(): SecretStore = KeychainSecretStore()

@OptIn(ExperimentalSettingsApi::class, ExperimentalSettingsImplementation::class)
actual fun platformSecretKeys(): Set<String> = sharedKeychain().keys
