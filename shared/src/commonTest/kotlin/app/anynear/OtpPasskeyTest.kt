package app.anynear

import app.anynear.core.cryptoProvider
import app.anynear.passwords.Otp
import app.anynear.passwords.Passkey
import app.anynear.passwords.Webauthn
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ADR 0052: one-time codes per RFC 6238 and passkeys per WebAuthn. */
class OtpPasskeyTest {
    // Test values from RFC 6238, appendix B.
    private val seed20 = "12345678901234567890".encodeToByteArray()

    @Test
    fun rfc6238Vectors() {
        val sha1 = Otp(seed20, digits = 8)
        assertEquals("94287082", sha1.code(59_000))
        assertEquals("07081804", sha1.code(1_111_111_109_000))
        assertEquals("65353130", sha1.code(20_000_000_000_000))
        val sha256 = Otp("12345678901234567890123456789012".encodeToByteArray(), digits = 8, algorithm = "SHA256")
        assertEquals("46119246", sha256.code(59_000))
        val sha512 = Otp("1234567890123456789012345678901234567890123456789012345678901234".encodeToByteArray(), digits = 8, algorithm = "SHA512")
        assertEquals("90693936", sha512.code(59_000))
        assertEquals(1, sha1.secondsLeft(59_000))
        assertEquals(30, sha1.secondsLeft(60_000))
    }

    @Test
    fun readsLinksAndTypedKeys() {
        val otp = assertNotNull(Otp.parse("otpauth://totp/Meine%20Bank:anna@example.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Meine%20Bank&digits=8"))
        assertContentEquals(seed20, otp.secret)
        assertEquals(8, otp.digits)
        assertEquals("Meine Bank", otp.issuer)
        assertEquals("94287082", otp.code(59_000))
        // Name from the label if issuer is missing.
        assertEquals("Shop", Otp.parse("otpauth://totp/Shop:x?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")!!.issuer)
        // Typed in, with spaces and lower case.
        assertContentEquals(seed20, Otp.parse("gezd gnbv gy3t qojq gezd gnbv gy3t qojq")!!.secret)
        // As a link again and back.
        val again = Otp.parse(Otp.uri(otp))!!
        assertContentEquals(otp.secret, again.secret)
        assertEquals(otp.digits, again.digits)
        assertEquals(otp.issuer, again.issuer)

        assertNull(Otp.parse("otpauth://hotp/X?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&counter=1"))
        assertNull(Otp.parse("https://example.com"))
        assertNull(Otp.parse("ABC"))
        assertNull(Otp.parse("otpauth://totp/X?secret=GEZDGNBVGY3TQOJQ&digits=12"))
    }

    @Test
    fun passkeySignsWhatTheWebsiteChecks() {
        val reg = Webauthn.create("meinebank.example", byteArrayOf(9, 8, 7), "anna", 1L)
        val authData = reg.authenticatorData
        assertContentEquals(Webauthn.sha256("meinebank.example".encodeToByteArray()), authData.copyOfRange(0, 32))
        assertEquals(0x5D, authData[32].toInt() and 0xff) // UP, UV, BE, BS, AT
        assertContentEquals(ByteArray(4), authData.copyOfRange(33, 37))
        assertContentEquals(ByteArray(16), authData.copyOfRange(37, 53))
        val credentialId = Webauthn.unb64(reg.passkey.credentialId)
        assertEquals(credentialId.size, ((authData[53].toInt() and 0xff) shl 8) or (authData[54].toInt() and 0xff))
        assertContentEquals(credentialId, authData.copyOfRange(55, 55 + credentialId.size))
        // Attestation object: map with three entries, starts with „fmt“: „none“.
        assertEquals(0xA3, reg.attestationObject[0].toInt() and 0xff)
        assertTrue(reg.attestationObject.decodeToString(0, 20).contains("fmtdnone"))

        val clientDataHash = Webauthn.sha256("{\"type\":\"webauthn.get\"}".encodeToByteArray())
        val assertion = Webauthn.assert(reg.passkey, clientDataHash)
        assertEquals(0x1D, assertion.authenticatorData[32].toInt() and 0xff)
        assertEquals(37, assertion.authenticatorData.size)
        val publicKey = cryptoProvider.get(ECDSA).publicKeyDecoder(EC.Curve.P256).decodeFromByteArrayBlocking(EC.PublicKey.Format.DER, reg.publicKeyDer)
        assertTrue(
            publicKey.signatureVerifier(SHA256, ECDSA.SignatureFormat.DER)
                .tryVerifySignatureBlocking(assertion.authenticatorData + clientDataHash, assertion.signature),
        )
    }

    @Test
    fun passkeyFromIosSignsHereToo() {
        // Throwaway test key, generated with CryptoKit (`iosApp/Passwords/Passkeys.swift`): PKCS#8 as in Kotlin.
        val fromIos = Passkey(
            credentialId = "AQID",
            rpId = "meinebank.example",
            userHandle = "AQID",
            userName = "anna",
            privateKey = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgSK-4XgXcuMwFKwTPJot9plwbWbuBjcD8Lwy__cm9JFShRANCAAQ8nf-LWTwCeT0PQcG7KXM8wY8QGODKzRY29f2Y3phVEiphW7yXfPcnl6_MqAVL2jPmGADfFgUFR6gttREwKa-_",
            created = 1L,
        )
        val hash = Webauthn.sha256("x".encodeToByteArray())
        val assertion = Webauthn.assert(fromIos, hash)
        val raw = Webauthn.unb64(fromIos.privateKey).let { it.copyOfRange(it.size - 65, it.size) }
        val publicKey = cryptoProvider.get(ECDSA).publicKeyDecoder(EC.Curve.P256).decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, raw)
        assertTrue(publicKey.signatureVerifier(SHA256, ECDSA.SignatureFormat.DER).tryVerifySignatureBlocking(assertion.authenticatorData + hash, assertion.signature))
    }
}
