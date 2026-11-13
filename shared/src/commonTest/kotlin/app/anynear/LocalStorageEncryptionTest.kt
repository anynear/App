package app.anynear

import app.anynear.data.EncryptedKeyValueStore
import app.anynear.data.LocalCipher
import app.anynear.data.SecretStore
import app.anynear.data.SecretUnreadableException
import app.anynear.anmp.AnmpKeys
import app.anynear.core.CoreSecretKeys
import app.anynear.core.LocalKey
import kotlinx.coroutines.test.runTest
import kotlin.test.assertContentEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalStorageEncryptionTest {
    @Test
    fun lockedSecretStoreNeverGetsANewDeviceKey() {
        val writes = mutableListOf<String>()
        val locked = object : SecretStore {
            override fun get(key: String): ByteArray? = throw SecretUnreadableException("locked")

            override fun put(key: String, value: ByteArray?) {
                writes += key
            }
        }
        assertFailsWith<SecretUnreadableException> { LocalKey.cipher(locked) }
        assertEquals(emptyList(), writes)
    }

    private val raw = InMemoryStore()
    private val cipher = LocalCipher(ByteArray(32) { 7 })
    private val store = EncryptedKeyValueStore(raw, cipher)

    @Test
    fun valuesAreStoredEncrypted() {
        store.putString("own_name", "Kevin")

        assertEquals("Kevin", store.getString("own_name"))
        assertFalse("Kevin" in raw.getString("own_name").orEmpty())
    }

    @Test
    fun plainValuesFromOlderVersionsAreKeptAndEncrypted() {
        raw.putString("own_name", "Kevin")

        assertEquals("Kevin", store.getString("own_name"))
        assertTrue(LocalCipher.isSealed(raw.getString("own_name").orEmpty()))
        assertEquals("Kevin", store.getString("own_name"))
    }

    @Test
    fun removingWorks() {
        store.putString("own_name", "Kevin")
        store.putString("own_name", null)

        assertNull(raw.getString("own_name"))
    }

    @Test
    fun deviceKeyGivesSameStorageKeyAfterRestart() {
        val secrets = InMemorySecretStore()
        val sealed = LocalKey.cipher(secrets).seal("Hallo")

        assertTrue(sealed.startsWith("local-v1:"))
        assertEquals("Hallo", LocalKey.cipher(secrets).open(sealed))
        assertEquals("Hallo", LocalKey.existingCipher(secrets)!!.open(sealed))
        assertNull(LocalKey.cipher(InMemorySecretStore()).open(sealed))
        assertNull(LocalKey.existingCipher(InMemorySecretStore()))
    }

    /** The app derives the key like the ANMP core (`anmp/v1/local-storage`), from the same `core.device.x`. */
    @Test
    fun storageKeyIsTheAnmpDerivation() = runTest {
        val x = ByteArray(32) { (it * 3).toByte() }
        assertContentEquals(AnmpKeys.localStorageKey(x), LocalKey.rawKey(x))
        val secrets = InMemorySecretStore()
        LocalKey.cipher(secrets)
        assertEquals(32, secrets.get(CoreSecretKeys.DEVICE_X)?.size)
    }
}
