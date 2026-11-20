package app.anynear

import app.anynear.core.LocalKey
import app.anynear.core.cryptoProvider
import app.anynear.core.localCryptoProvider
import app.anynear.data.LocalCipher
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The local key and cipher run on the platform's own crypto ([localCryptoProvider]) so that the start does not wait
 * for BouncyCastle ([cryptoProvider], the way up to 0.9.0). Everything stored on the device hangs on that key: both
 * ways must give the same bytes, and those bytes are pinned here. The pinned values were computed independently
 * (Python `hmac` and `cryptography`), not with either provider.
 */
class LocalCryptoTest {
    private val deviceKey = ByteArray(32) { it.toByte() }

    /** HKDF-SHA256(salt `anmp`, info `anmp/v1/local-storage`) of the bytes 00..1f. */
    private val localKey = hex("27ab7f4089cc2a2e94933ef803650b039880f017cc3d448a82057dbe815bd54a")

    /** „Grüße aus dem Kreis“ under [localKey]: nonce 64..6f, ciphertext, tag. */
    private val sealed = hex(
        "6465666768696a6b6c6d6e6fe766c7a8ae1bced90f8004349900ca79b7ea2c07d2062b70e7128d9caa9349eba5b289aa5b",
    )

    @Test
    fun localKeyIsTheSameOnBothWaysAndPinned() {
        assertContentEquals(localKey, LocalKey.rawKey(deviceKey, cryptoProvider))
        assertContentEquals(localKey, LocalKey.rawKey(deviceKey, localCryptoProvider))
        assertContentEquals(localKey, LocalKey.rawKey(deviceKey))
    }

    @Test
    fun localKeyMatchesForManyDeviceKeys() {
        repeat(64) { n ->
            val key = ByteArray(32) { (it * 31 + n * 7 + 1).toByte() }
            assertContentEquals(LocalKey.rawKey(key, cryptoProvider), LocalKey.rawKey(key, localCryptoProvider))
        }
    }

    /** RFC 5869, test case 1, cut to the 32 bytes the app derives. */
    @Test
    fun hkdfMatchesTheRfcVectorOnBothWays() {
        val expected = hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf")
        for (provider in listOf(cryptoProvider, localCryptoProvider)) {
            val derived = provider.get(HKDF)
                .secretDerivation(SHA256, 256.bits, ByteArray(13) { it.toByte() }, ByteArray(10) { (0xf0 + it).toByte() })
                .deriveSecretToByteArrayBlocking(ByteArray(22) { 0x0b })
            assertContentEquals(expected, derived)
        }
    }

    @Test
    fun storedDataOpensOnBothWays() {
        assertEquals("Grüße aus dem Kreis", LocalCipher(localKey).openBytes(sealed)?.decodeToString())
        assertEquals("Grüße aus dem Kreis", aesGcm(cryptoProvider).decryptBlocking(sealed).decodeToString())
    }

    @Test
    fun whatOneWaySealsTheOtherOpens() {
        val text = "Einstellungen, Verlauf, Anhänge"
        val old = aesGcm(cryptoProvider)
        val new = LocalCipher(localKey)
        assertEquals(text, new.openBytes(old.encryptBlocking(text.encodeToByteArray()))?.decodeToString())
        assertEquals(text, old.decryptBlocking(new.sealBytes(text.encodeToByteArray())).decodeToString())
    }

    /** The cipher as [LocalCipher] built it up to 0.9.0. */
    private fun aesGcm(provider: CryptographyProvider) =
        provider.get(AES.GCM).keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, localKey).cipher()

    private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
