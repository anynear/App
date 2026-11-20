package app.anynear.core

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.client.HttpClient
import kotlin.io.encoding.Base64

/** Crypto of the platform for data on the device (Android: JDK with BouncyCastle, iOS: Apple). Protocol crypto is in the ANMP core. */
internal expect val cryptoProvider: CryptographyProvider

/**
 * Crypto for the local key and cipher ([LocalKey], [app.anynear.data.LocalCipher]), which the app needs on the main
 * thread before the first screen: HMAC-SHA256 and AES-GCM of the platform. On Android that is the system's own
 * provider, because setting up BouncyCastle takes seconds on a weak phone; elsewhere it is [cryptoProvider].
 * Both give the same bytes (`LocalCryptoTest`).
 */
internal expect val localCryptoProvider: CryptographyProvider

/** Host of the local development relays as seen from the emulator, simulator or phone on Wi-Fi. */
internal expect val devRelayHost: String

/**
 * Only for the local development network ([AppNet.DEV]): HTTP client that accepts the self-signed certificate of
 * the seed. The app verifies the network list via its signature, not via TLS.
 */
internal expect fun devSeedHttpClient(): HttpClient

/** Base64url without padding, as in the protocol. */
internal val b64: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

internal suspend fun sha256(data: ByteArray): ByteArray = cryptoProvider.get(SHA256).hasher().hash(data)
