package app.anynear.data

/** Cryptographically secure random bytes (Android: SecureRandom, iOS: SecRandomCopyBytes). */
expect fun secureRandomBytes(size: Int): ByteArray
