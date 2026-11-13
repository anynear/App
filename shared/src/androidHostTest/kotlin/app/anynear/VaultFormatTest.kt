package app.anynear

import app.anynear.passwords.Login
import app.anynear.passwords.VaultFile
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * ADR 0039: the iOS password extension reads `vault` with CryptoKit (`AES.GCM.SealedBox(combined:)`), i.e.
 * 12-byte nonce, ciphertext, 16-byte tag. Checked here with an independent implementation (javax.crypto).
 */
class VaultFormatTest {
    @Test
    fun nonceFirstTagLast() {
        val key = ByteArray(32) { it.toByte() }
        val sealed = VaultFile.seal(key, listOf(Login("a", "beispiel.example", "name", "ausgedacht", 1L)))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed, 0, 12))
        val json = cipher.doFinal(sealed, 12, sealed.size - 12).decodeToString()
        assertTrue(json.startsWith("""{"v":1,"logins":[{"id":"a","website":"beispiel.example""""), json)
    }
}
