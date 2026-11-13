package app.anynear.data

/**
 * Protected store for secrets such as the family key
 * (Android: encrypted with a key from the Android Keystore, iOS: keychain).
 *
 * “Not there” and “not readable” are different (same contract as the ANMP core's `SecretStore`): whoever finds
 * nothing may create a new key, but a key that only could not be read right now (iOS before the first unlock after
 * power-on, a busy Android Keystore) must never be replaced, or the device would get a new identity for good.
 */
interface SecretStore {
    /**
     * The stored value, or `null` only if there is none (or it can never be read again, e.g. its Keystore key is
     * gone). Throws [SecretUnreadableException] if the store cannot read it right now.
     */
    fun get(key: String): ByteArray?

    /** `null` removes the entry. Throws [SecretUnreadableException] if the store cannot write right now. */
    fun put(key: String, value: ByteArray?)
}

/** The secret store cannot be used right now; try again later and create nothing in its place. */
class SecretUnreadableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

expect fun platformSecretStore(): SecretStore
