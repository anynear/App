package app.anynear.data

import app.anynear.core.cryptoProvider
import dev.whyoleg.cryptography.algorithms.AES
import kotlin.io.encoding.Base64

/**
 * Encrypts data that is only on this device (message history, settings) with
 * AES-256-GCM. The key is derived from the X25519 device key (ANMP `anmp/v1/local-storage`,
 * [app.anynear.core.LocalKey]) and is never stored itself.
 *
 * Runs blocking because settings are read synchronously at start.
 */
class LocalCipher(rawKey: ByteArray) {
    private val cipher = cryptoProvider.get(AES.GCM).keyDecoder()
        .decodeFromByteArrayBlocking(AES.Key.Format.RAW, rawKey)
        .cipher()

    fun seal(plaintext: String): String = PREFIX + Base64.encode(cipher.encryptBlocking(plaintext.encodeToByteArray()))

    /** `null` if [sealed] was not encrypted with this key or is damaged. */
    fun open(sealed: String): String? {
        if (!isSealed(sealed)) return null
        return runCatching { cipher.decryptBlocking(Base64.decode(sealed.removePrefix(PREFIX))).decodeToString() }.getOrNull()
    }

    /** For attachments: nonce and ciphertext without Base64 and without prefix. */
    fun sealBytes(plaintext: ByteArray): ByteArray = cipher.encryptBlocking(plaintext)

    /** `null` if [sealed] was not encrypted with this key or is damaged. */
    fun openBytes(sealed: ByteArray): ByteArray? = runCatching { cipher.decryptBlocking(sealed) }.getOrNull()

    companion object {
        /** ANMP `local-v1:` (ADR 0034, 0061). */
        private const val PREFIX = app.anynear.anmp.AnmpInfo.LOCAL_PREFIX

        fun isSealed(value: String) = value.startsWith(PREFIX)
    }
}

/**
 * Stores values encrypted in [inner]. Unencrypted values of older versions are taken over on
 * first read and rewritten encrypted.
 */
class EncryptedKeyValueStore(private val inner: KeyValueStore, private val cipher: LocalCipher) : KeyValueStore {
    override fun getString(key: String): String? {
        val stored = inner.getString(key) ?: return null
        if (LocalCipher.isSealed(stored)) return cipher.open(stored)
        inner.putString(key, cipher.seal(stored))
        return stored
    }

    override fun putString(key: String, value: String?) {
        inner.putString(key, value?.let(cipher::seal))
    }
}
