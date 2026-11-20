package app.anynear.core

import dev.whyoleg.cryptography.CryptographyProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust

internal actual val cryptoProvider: CryptographyProvider = CryptographyProvider.Default

internal actual val localCryptoProvider: CryptographyProvider get() = cryptoProvider

/**
 * Host with the Docker relays. The simulator shares the network with the Mac (127.0.0.1); a real iPhone
 * reaches it only via its Wi-Fi address from `app.devRelayHost` (local.properties).
 */
internal actual val devRelayHost: String =
    if (NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null || CONFIGURED_DEV_RELAY_HOST.isEmpty()) "127.0.0.1"
    else CONFIGURED_DEV_RELAY_HOST

/** Only for the entry point of the local development network: the list carries its own signature. */
@OptIn(ExperimentalForeignApi::class)
internal actual fun devSeedHttpClient(): HttpClient = HttpClient(Darwin) {
    expectSuccess = false
    followRedirects = false
    engine {
        handleChallenge { _, _, challenge, completionHandler ->
            val space = challenge.protectionSpace
            val trust = space.serverTrust
            if (space.authenticationMethod == NSURLAuthenticationMethodServerTrust && trust != null) {
                completionHandler(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(trust))
            } else {
                completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
            }
        }
    }
}
