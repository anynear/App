package app.anynear.data

import java.security.KeyStoreException
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Android secret store: lost for good counts as not there, a failing Keystore never does. */
class KeystoreSecretTest {
    private fun aesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val value = ByteArray(32) { it.toByte() }

    @Test
    fun roundTrip() {
        val key = aesKey()
        assertContentEquals(value, openSecret(sealSecret(value, key)) { key })
    }

    @Test
    fun lostKeyOrForeignKeyOrDamagedValueCountsAsNotThere() {
        val sealed = sealSecret(value, aesKey())
        assertNull(openSecret(sealed) { null })
        assertNull(openSecret(sealed) { aesKey() })
        assertNull(openSecret(ByteArray(5)) { error("not asked") })
    }

    @Test
    fun failingKeystoreThrowsInsteadOfNull() {
        val sealed = sealSecret(value, aesKey())
        var asked = 0
        val e = assertFailsWith<SecretUnreadableException> { openSecret(sealed) { asked++; throw KeyStoreException("busy") } }
        assertEquals(3, asked)
        assertEquals("busy", e.cause?.message)
        assertFailsWith<SecretUnreadableException> { openSecret(sealed) { throw ProviderException("Keystore operation failed") } }
    }

    @Test
    fun keystoreRecoversOnSecondAttempt() {
        val key = aesKey()
        val sealed = sealSecret(value, key)
        var asked = 0
        assertContentEquals(value, openSecret(sealed) { if (asked++ == 0) throw ProviderException("busy") else key })
    }
}
