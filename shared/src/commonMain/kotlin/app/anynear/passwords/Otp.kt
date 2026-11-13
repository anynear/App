package app.anynear.passwords

import app.anynear.AppConfig
import app.anynear.core.cryptoProvider
import dev.whyoleg.cryptography.CryptographyAlgorithmId
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA1
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA512

/**
 * Time-based one-time code of a login (ADR 0052, item 2, RFC 6238). What is stored is the `otpauth://totp/…` link,
 * as the website shows it as a QR code; [parse] reads it, [code] computes the code for a point in time.
 * Implemented the same way in the iOS extension (`iosApp/CredentialProvider/PasswordVaultFile.swift`).
 */
class Otp(
    val secret: ByteArray,
    val digits: Int = 6,
    val period: Int = 30,
    val algorithm: String = "SHA1",
    /** Name from the link, e.g. "Bank" from `otpauth://totp/Bank:anna@example.de`; empty if there is none in it. */
    val issuer: String = "",
) {
    /** Code at time [nowMillis], with leading zeros. */
    fun code(nowMillis: Long): String {
        val counter = nowMillis / 1000 / period
        val message = ByteArray(8) { i -> (counter ushr (56 - 8 * i)).toByte() }
        val hash = cryptoProvider.get(HMAC).keyDecoder(digest()).decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, secret)
            .signatureGenerator().generateSignatureBlocking(message)
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or (hash[offset + 3].toInt() and 0xff)
        var mod = 1L
        repeat(digits) { mod *= 10 }
        return (binary % mod).toString().padStart(digits, '0')
    }

    /** Seconds for which the code at time [nowMillis] is still valid (1 to [period]). */
    fun secondsLeft(nowMillis: Long): Int = period - ((nowMillis / 1000) % period).toInt()

    // RFC 6238 requires SHA-1 for the vast majority of websites; here only as HMAC, where it remains secure.
    @OptIn(DelicateCryptographyApi::class)
    private fun digest(): CryptographyAlgorithmId<Digest> = when (algorithm) {
        "SHA256" -> SHA256
        "SHA512" -> SHA512
        else -> SHA1
    }

    companion object {
        /**
         * An `otpauth://totp/…` link or just the key in Base32, as websites show it for typing in
         * (spaces and lower case do not matter). `null` if it is neither of the two; counter-based codes
         * (`otpauth://hotp`) are not supported.
         */
        fun parse(text: String): Otp? {
            val t = text.trim()
            if (!t.startsWith("otpauth:", ignoreCase = true)) return base32(t)?.let { Otp(it) }
            val rest = t.substringAfter("://", "")
            if (!rest.startsWith("totp/", ignoreCase = true)) return null
            val label = decode(rest.substringAfter('/').substringBefore('?'))
            val params = rest.substringAfter('?', "").split('&').filter { '=' in it }
                .associate { it.substringBefore('=').lowercase() to decode(it.substringAfter('=')) }
            val secret = params["secret"]?.let(::base32) ?: return null
            val digits = params["digits"]?.toIntOrNull() ?: 6
            val period = params["period"]?.toIntOrNull() ?: 30
            val algorithm = (params["algorithm"] ?: "SHA1").uppercase()
            if (digits !in 6..8 || period !in 1..300 || algorithm !in setOf("SHA1", "SHA256", "SHA512")) return null
            val issuer = params["issuer"]?.trim().orEmpty().ifEmpty { label.substringBefore(':', "").trim() }
            return Otp(secret, digits, period, algorithm, issuer)
        }

        /** Link as it is stored in the login (ADR 0052, item 5). */
        fun uri(otp: Otp): String = buildString {
            append("otpauth://totp/")
            append(encode(otp.issuer.ifEmpty { AppConfig.APP_NAME }))
            append("?secret=").append(base32Encode(otp.secret))
            if (otp.issuer.isNotEmpty()) append("&issuer=").append(encode(otp.issuer))
            if (otp.algorithm != "SHA1") append("&algorithm=").append(otp.algorithm)
            if (otp.digits != 6) append("&digits=").append(otp.digits)
            if (otp.period != 30) append("&period=").append(otp.period)
        }

        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

        /** Base32 per RFC 4648, without spaces, hyphens and `=`; at least 10 bytes (80 bits). */
        internal fun base32(text: String): ByteArray? {
            val clean = text.uppercase().filterNot { it == ' ' || it == '-' || it == '=' }
            if (clean.isEmpty() || clean.any { it !in ALPHABET }) return null
            val out = ArrayList<Byte>(clean.length * 5 / 8)
            var buffer = 0
            var bits = 0
            for (c in clean) {
                buffer = (buffer shl 5) or ALPHABET.indexOf(c)
                bits += 5
                if (bits >= 8) {
                    bits -= 8
                    out.add((buffer shr bits).toByte())
                    buffer = buffer and ((1 shl bits) - 1)
                }
            }
            return out.toByteArray().takeIf { it.size >= 10 }
        }

        internal fun base32Encode(bytes: ByteArray): String = buildString {
            var buffer = 0
            var bits = 0
            for (b in bytes) {
                buffer = (buffer shl 8) or (b.toInt() and 0xff)
                bits += 8
                while (bits >= 5) {
                    bits -= 5
                    append(ALPHABET[(buffer shr bits) and 31])
                }
                buffer = buffer and ((1 shl bits) - 1)
            }
            if (bits > 0) append(ALPHABET[(buffer shl (5 - bits)) and 31])
        }

        private fun decode(s: String): String {
            val bytes = ArrayList<Byte>()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                val hex = if (c == '%' && i + 2 < s.length) s.substring(i + 1, i + 3).toIntOrNull(16) else null
                if (hex != null) {
                    bytes.add(hex.toByte())
                    i += 3
                } else {
                    (if (c == '+') " " else c.toString()).encodeToByteArray().forEach(bytes::add)
                    i++
                }
            }
            return bytes.toByteArray().decodeToString()
        }

        private fun encode(s: String): String = s.encodeToByteArray().joinToString("") { b ->
            val c = (b.toInt() and 0xff).toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in "-._~") c.toString() else "%" + (b.toInt() and 0xff).toString(16).uppercase().padStart(2, '0')
        }
    }
}
