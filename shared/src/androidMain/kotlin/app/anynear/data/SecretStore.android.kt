package app.anynear.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import app.anynear.AndroidPlatform
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64

/**
 * Values are encrypted with a non-exportable AES key from the Android Keystore.
 * [prefsName] and [alias]: settings and Keystore key under which this is kept.
 */
private class KeystoreSecretStore(context: Context, prefsName: String = SECRETS_NAME, private val alias: String = SECRETS_NAME) : SecretStore {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /** The existing Keystore key, `null` if there is none. Throws if the Keystore cannot be asked right now. */
    private fun existingKey(): SecretKey? = KeyStore.getInstance(KEYSTORE).apply { load(null) }.getKey(alias, null) as? SecretKey

    private fun masterKey(): SecretKey {
        existingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun get(key: String): ByteArray? {
        val stored = prefs.getString(key, null) ?: return null
        val raw = try {
            Base64.decode(stored)
        } catch (e: IllegalArgumentException) {
            return null // Damaged for good: no way to read it again.
        }
        return openSecret(raw, ::existingKey)
    }

    override fun put(key: String, value: ByteArray?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }
        val sealed = withRetries("write $key") { sealSecret(value, masterKey()) }
        prefs.edit().putString(key, Base64.encode(sealed)).apply()
    }

    companion object {
        const val KEYSTORE = "AndroidKeyStore"
    }
}

private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_SIZE = 12
private const val TAG_BITS = 128
private const val ATTEMPTS = 3

/** IV and ciphertext of [value] under [key]. */
internal fun sealSecret(value: ByteArray, key: SecretKey): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return cipher.iv + cipher.doFinal(value)
}

/**
 * Opens what [sealSecret] wrote. `null` only if it can never be read again: [key] gives no key (Keystore key lost,
 * e.g. app data moved to another device), the key does not fit, or [raw] is damaged. Everything else (Keystore busy
 * or failing) throws [SecretUnreadableException] after a few attempts, so that nobody creates a new key in its place.
 */
internal fun openSecret(raw: ByteArray, key: () -> SecretKey?): ByteArray? {
    if (raw.size < IV_SIZE + TAG_BITS / 8) return null
    return withRetries("read") {
        val k = key() ?: return@withRetries null
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(TAG_BITS, raw, 0, IV_SIZE))
            cipher.doFinal(raw, IV_SIZE, raw.size - IV_SIZE)
        } catch (e: AEADBadTagException) {
            null // Another key than the one it was written with.
        } catch (e: KeyPermanentlyInvalidatedException) {
            null
        }
    }
}

/** Runs [block] up to [ATTEMPTS] times while the Keystore fails; then gives up with [SecretUnreadableException]. */
private inline fun <T> withRetries(what: String, block: () -> T): T {
    var last: Exception? = null
    repeat(ATTEMPTS) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            last = e
            if (attempt < ATTEMPTS - 1) Thread.sleep(50L * (attempt + 1))
        }
    }
    throw SecretUnreadableException("secret store: $what failed", last)
}

actual fun platformSecretStore(): SecretStore = KeystoreSecretStore(AndroidPlatform.requireContext())

actual fun platformSecretKeys(): Set<String> =
    AndroidPlatform.requireContext().getSharedPreferences(SECRETS_NAME, Context.MODE_PRIVATE).all.keys

/** Settings and Keystore key for secrets (brand-neutral since ADR 0061, decision 3). */
internal const val SECRETS_NAME = "secrets"
