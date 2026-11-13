package app.anynear.core

import app.anynear.anmp.AnmpInfo
import app.anynear.data.LocalCipher
import app.anynear.data.SecretStore
import app.anynear.data.secureRandomBytes
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * Key for data that is only on this device (settings, history, attachments): ANMP derivation
 * HKDF(X25519 device key, `anmp/v1/local-storage`). The device key is the same one the ANMP core uses as
 * `core.device.x`; if it is missing, the app creates it here before the core starts (the core takes it over).
 * Blocks briefly because settings are read synchronously at start.
 */
object LocalKey {
    /**
     * Seed of the local key after restoring a backup (ADR 0093): the restored phone gets new device keys, the data of
     * the backup stays readable with the device key it came with. Without it the seed is the device key itself.
     */
    const val SEED = "app.local.seed"

    fun cipher(secrets: SecretStore): LocalCipher = LocalCipher(rawKey(seed(secrets)))

    /** Only load, never create (iOS extension: without a set-up app there is nothing to read). */
    fun existingCipher(secrets: SecretStore): LocalCipher? =
        (secrets.get(SEED)?.takeIf { it.size == KEY_SIZE } ?: secrets.get(CoreSecretKeys.DEVICE_X)?.takeIf { it.size == KEY_SIZE })
            ?.let { LocalCipher(rawKey(it)) }

    private fun seed(secrets: SecretStore): ByteArray = secrets.get(SEED)?.takeIf { it.size == KEY_SIZE } ?: deviceX(secrets)

    private fun deviceX(secrets: SecretStore): ByteArray =
        secrets.get(CoreSecretKeys.DEVICE_X)?.takeIf { it.size == KEY_SIZE }
            ?: secureRandomBytes(KEY_SIZE).also { secrets.put(CoreSecretKeys.DEVICE_X, it) }

    internal fun rawKey(deviceX: ByteArray): ByteArray =
        cryptoProvider.get(HKDF)
            .secretDerivation(SHA256, 256.bits, AnmpInfo.SALT.encodeToByteArray(), AnmpInfo.LOCAL_STORAGE.encodeToByteArray())
            .deriveSecretToByteArrayBlocking(deviceX)

    private const val KEY_SIZE = 32
}
