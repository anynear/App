package app.anynear.passwords

import app.anynear.core.cryptoProvider
import app.anynear.data.secureRandomBytes
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

/**
 * A passkey of a login (ADR 0052, item 3). Binary values as Base64url without `=`; [privateKey] is the private
 * ES256 key as PKCS#8. Fields like `VaultPasskey` in the iOS extension.
 */
@Serializable
data class Passkey(
    val credentialId: String,
    val rpId: String,
    val userHandle: String,
    val userName: String,
    val privateKey: String,
    /** Unix milliseconds. */
    val created: Long,
)

/** What the website gets back on creation. */
class Registration(val passkey: Passkey, val authenticatorData: ByteArray, val attestationObject: ByteArray, val publicKeyDer: ByteArray)

/** What the website gets back on sign-in. */
class Assertion(val authenticatorData: ByteArray, val signature: ByteArray)

/**
 * WebAuthn authenticator of anynear (ADR 0052, item 3): ES256, attestation "none", AAGUID of zeros, counter 0,
 * flags UP, UV, BE and BS. The cryptography is done by the library; here we only assemble what WebAuthn
 * requires. Implemented the same way in Swift (`iosApp/CredentialProvider/Passkeys.swift`).
 */
object Webauthn {
    /** ES256 in COSE. */
    const val ALG_ES256 = -7

    private const val UP = 0x01
    private const val UV = 0x04
    private const val BE = 0x08
    private const val BS = 0x10
    private const val AT = 0x40

    /** New passkey; with attestation "none" the website's clientDataHash does not go into the response. */
    fun create(rpId: String, userHandle: ByteArray, userName: String, now: Long): Registration {
        val ecdsa = cryptoProvider.get(ECDSA)
        val pair = ecdsa.keyPairGenerator(EC.Curve.P256).generateKeyBlocking()
        val credentialId = secureRandomBytes(16)
        val point = pair.publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.RAW)
        val cose = Cbor.map(
            Cbor.int(1) to Cbor.int(2),
            Cbor.int(3) to Cbor.int(ALG_ES256),
            Cbor.int(-1) to Cbor.int(1),
            Cbor.int(-2) to Cbor.bytes(point.copyOfRange(1, 33)),
            Cbor.int(-3) to Cbor.bytes(point.copyOfRange(33, 65)),
        )
        val authData = rpIdHash(rpId) + byteArrayOf((UP or UV or BE or BS or AT).toByte()) + ByteArray(4) +
            ByteArray(16) + byteArrayOf((credentialId.size shr 8).toByte(), credentialId.size.toByte()) + credentialId + cose
        val attestation = Cbor.map(
            Cbor.text("fmt") to Cbor.text("none"),
            Cbor.text("attStmt") to Cbor.map(),
            Cbor.text("authData") to Cbor.bytes(authData),
        )
        val passkey = Passkey(
            credentialId = b64(credentialId),
            rpId = rpId,
            userHandle = b64(userHandle),
            userName = userName,
            privateKey = b64(pair.privateKey.encodeToByteArrayBlocking(EC.PrivateKey.Format.DER)),
            created = now,
        )
        return Registration(passkey, authData, attestation, pair.publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.DER))
    }

    fun assert(passkey: Passkey, clientDataHash: ByteArray): Assertion {
        val authData = rpIdHash(passkey.rpId) + byteArrayOf((UP or UV or BE or BS).toByte()) + ByteArray(4)
        val key = cryptoProvider.get(ECDSA).privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArrayBlocking(EC.PrivateKey.Format.DER, unb64(passkey.privateKey))
        val signature = key.signatureGenerator(SHA256, ECDSA.SignatureFormat.DER).generateSignatureBlocking(authData + clientDataHash)
        return Assertion(authData, signature)
    }

    fun sha256(data: ByteArray): ByteArray = cryptoProvider.get(SHA256).hasher().hashBlocking(data)

    private fun rpIdHash(rpId: String) = sha256(rpId.encodeToByteArray())

    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    fun b64(bytes: ByteArray): String = base64Url.encode(bytes).trimEnd('=')

    fun unb64(text: String): ByteArray = base64Url.decode(text.trimEnd('='))
}

/** The bit of CBOR that attestation and COSE keys need (RFC 8949, canonical in the order of the callers). */
internal object Cbor {
    fun int(v: Int): ByteArray = if (v >= 0) head(0, v.toLong()) else head(1, (-1L - v))

    fun bytes(b: ByteArray): ByteArray = head(2, b.size.toLong()) + b

    fun text(s: String): ByteArray = s.encodeToByteArray().let { head(3, it.size.toLong()) + it }

    fun map(vararg entries: Pair<ByteArray, ByteArray>): ByteArray =
        entries.fold(head(5, entries.size.toLong())) { acc, (k, v) -> acc + k + v }

    private fun head(major: Int, n: Long): ByteArray {
        val m = major shl 5
        return when {
            n < 24 -> byteArrayOf((m or n.toInt()).toByte())
            n < 0x100 -> byteArrayOf((m or 24).toByte(), n.toByte())
            n < 0x10000 -> byteArrayOf((m or 25).toByte(), (n shr 8).toByte(), n.toByte())
            else -> byteArrayOf((m or 26).toByte(), (n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
        }
    }
}
