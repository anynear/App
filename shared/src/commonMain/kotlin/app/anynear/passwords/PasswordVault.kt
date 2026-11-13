package app.anynear.passwords

import app.anynear.attachments.BinaryFiles
import app.anynear.core.cryptoProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A stored login (ADR 0039): website, user name, password, plus identifier and change time; since ADR 0052
 * optionally a one-time code ([otp], the `otpauth://` link) and passkeys. Empty password only with a passkey.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Login(
    val id: String,
    val website: String,
    val username: String,
    val password: String,
    /** Unix milliseconds of the last change. */
    val changed: Long,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val otp: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val passkeys: List<Passkey> = emptyList(),
) {
    /** Needs version 2 of the file (ADR 0052, item 5). */
    internal val extended: Boolean get() = otp != null || passkeys.isNotEmpty()
}

/** Deleted login, so that another device does not bring it back when merging (ADR 0048). */
@Serializable
data class Removed(val id: String, val changed: Long)

@Serializable
private class VaultContent(val v: Int = VaultFile.VERSION, val logins: List<Login> = emptyList(), val removed: List<Removed> = emptyList())

/** Content of the file: logins and deleted logins. */
data class Vault(val logins: List<Login> = emptyList(), val removed: List<Removed> = emptyList()) {
    /**
     * Merge two states of one's own devices (ADR 0048): per login the newest change applies, a deletion applies
     * if it is not older than the last change. At equal time the content decides, so that all devices
     * get the same result. Order: own logins first.
     */
    fun merge(other: Vault): Vault {
        val removed = (removed + other.removed).groupBy { it.id }.map { (_, r) -> r.maxBy { it.changed } }
        val gone = removed.associate { it.id to it.changed }
        val logins = (logins + other.logins).groupBy { it.id }
            .map { (_, l) -> l.maxWith(compareBy<Login>({ it.changed }, { it.password }, { it.username }, { it.website }, { it.otp.orEmpty() }, { it.passkeys.joinToString { p -> p.credentialId } })) }
            .filter { l -> (gone[l.id] ?: Long.MIN_VALUE) < l.changed }
        return Vault(logins, removed.filter { r -> logins.none { it.id == r.id } })
    }
}

/**
 * File `vault` with all logins: UTF-8 JSON, encrypted with AES-256-GCM under the password key `pv`,
 * 12 bytes of nonce in front, 16 bytes of tag at the end (ADR 0039). The iOS password extension reads the same
 * format in Swift (`iosApp/CredentialProvider/PasswordVaultFile.swift`); changes only with [VERSION] and there too.
 * Version 2 (ADR 0052) only if a login has a one-time code or passkey: older apps then do not read it,
 * instead of discarding the new fields when merging.
 */
object VaultFile {
    const val NAME = "vault"

    /**
     * New passkeys from the iOS extension or the Android provider, in the same format and under the same key.
     * The app merges them into [NAME] on the next unlock and deletes the file (ADR 0052).
     */
    const val INCOMING = "incoming"
    const val VERSION = 2
    const val KEY_SIZE = 32

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun seal(key: ByteArray, logins: List<Login>): ByteArray = seal(key, Vault(logins))

    fun seal(key: ByteArray, vault: Vault): ByteArray =
        cipher(key).encryptBlocking(
            json.encodeToString(VaultContent.serializer(), VaultContent(if (vault.logins.any { it.extended }) 2 else 1, vault.logins, vault.removed))
                .encodeToByteArray(),
        )

    /** `null` if [sealed] is not encrypted with [key], is damaged or comes from a newer version. */
    fun open(key: ByteArray, sealed: ByteArray): List<Login>? = openVault(key, sealed)?.logins

    /** Like [open], with the deleted logins. */
    fun openVault(key: ByteArray, sealed: ByteArray): Vault? {
        val plain = runCatching { cipher(key).decryptBlocking(sealed) }.getOrNull() ?: return null
        val content = runCatching { json.decodeFromString(VaultContent.serializer(), plain.decodeToString()) }.getOrNull() ?: return null
        return Vault(content.logins, content.removed).takeIf { content.v <= VERSION }
    }

    /** Logins including [INCOMING], for filling outside the app; `null` if [NAME] and [INCOMING] are missing or do not fit. */
    fun readAll(files: BinaryFiles, key: ByteArray): Vault? {
        val own = files.read(NAME)?.let { openVault(key, it) }
        val incoming = files.read(INCOMING)?.let { openVault(key, it) }
        return if (own == null && incoming == null) null else (own ?: Vault()).merge(incoming ?: Vault())
    }

    /**
     * Store a new or changed login in [INCOMING] (Android provider); the app takes it over on the next
     * unlock. `false` if an existing file does not fit [key].
     */
    fun addIncoming(files: BinaryFiles, key: ByteArray, login: Login): Boolean {
        val before = files.read(INCOMING)?.let { openVault(key, it) ?: return false } ?: Vault()
        files.write(INCOMING, seal(key, before.merge(Vault(listOf(login)))))
        return true
    }

    /** Short fingerprint of a key: does a state from the relays belong to the key here, without unlocking? */
    fun keyId(key: ByteArray): String =
        cryptoProvider.get(SHA256).hasher().hashBlocking(key).copyOf(8).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun cipher(key: ByteArray) = cryptoProvider.get(AES.GCM).keyDecoder()
        .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
        .cipher()
}

/**
 * Matching a login to the website that currently wants credentials (ADR 0039, item 6): same domain
 * or a subdomain of the stored one. A similar name is never enough. Implemented the same way in the iOS extension.
 */
object LoginMatch {
    /** Host of an input like "https://www.Bank.de/login" → "bank.de"; `null` if none can be recognised. */
    fun host(website: String): String? {
        var s = website.trim().lowercase()
        s = s.substringAfter("://", s)
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfterLast('@').substringBefore(':').trimEnd('.')
        s = s.removePrefix("www.")
        if (s.isEmpty() || s.any { !(it.isLetterOrDigit() || it == '.' || it == '-') } || s.startsWith('.') || ".." in s) return null
        return s
    }

    /** Does [login] match the requesting domain [domain]? */
    fun matches(login: Login, domain: String): Boolean {
        val stored = host(login.website) ?: return false
        val asked = host(domain) ?: return false
        return asked == stored || asked.endsWith(".$stored")
    }

    /** Matching ones first, then the rest; each sorted by website and user name. */
    fun sorted(logins: List<Login>, domain: String?): List<Login> {
        val order = compareBy<Login>({ displayName(it.website).lowercase() }, { it.username.lowercase() })
        if (domain == null) return logins.sortedWith(order)
        val (hit, rest) = logins.partition { matches(it, domain) }
        return hit.sortedWith(order) + rest.sortedWith(order)
    }

    /** Passkeys of a login for the website with identifier [rpId]: exactly that one, without subdomains (ADR 0052). */
    fun passkeys(logins: List<Login>, rpId: String): List<Pair<Login, Passkey>> =
        logins.flatMap { l -> l.passkeys.filter { it.rpId.equals(rpId, ignoreCase = true) }.map { l to it } }

    /**
     * New passkey: goes to the login with the same website and the same user name, otherwise a new login without a
     * password. [newId] for a new login, [now] as the change time.
     */
    fun withPasskey(logins: List<Login>, passkey: Passkey, newId: String, now: Long): Login {
        val same = logins.firstOrNull { host(it.website) == host(passkey.rpId) && it.username.equals(passkey.userName, ignoreCase = true) }
        return same?.let { it.copy(passkeys = it.passkeys + passkey, changed = maxOf(now, it.changed + 1)) }
            ?: Login(newId, passkey.rpId, passkey.userName, "", now, passkeys = listOf(passkey))
    }

    /** Display name: the host without "www.", otherwise the input as it is. */
    fun displayName(website: String): String = host(website) ?: website.trim()
}
