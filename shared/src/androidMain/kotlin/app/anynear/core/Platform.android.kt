package app.anynear.core

import android.os.Build
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.jdk.JDK
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// Own BouncyCastle instance instead of the stripped-down „BC“ from the Android system. Created on first use, not when
// this file loads: loading its classes takes seconds on a weak phone, and the start does not need it.
internal actual val cryptoProvider: CryptographyProvider by lazy { CryptographyProvider.JDK(BouncyCastleProvider()) }

// The system's providers (Conscrypt), already loaded with every app process.
internal actual val localCryptoProvider: CryptographyProvider by lazy { CryptographyProvider.JDK() }

/**
 * Machine with the Docker relays. In the emulator that is always 10.0.2.2; a real phone reaches it only via
 * its Wi-Fi address from `app.devRelayHost` (local.properties).
 */
internal actual val devRelayHost: String =
    if (CONFIGURED_DEV_RELAY_HOST.isEmpty() || isEmulator()) "10.0.2.2" else CONFIGURED_DEV_RELAY_HOST

// Build fields are null in host tests (JVM).
private fun isEmulator(): Boolean =
    Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT?.startsWith("generic") == true || Build.PRODUCT?.contains("sdk") == true

internal actual fun devSeedHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    followRedirects = false
    engine {
        config {
            val trust = AnyCertificate()
            val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), null) }
            sslSocketFactory(tls.socketFactory, trust)
            hostnameVerifier { _, _ -> true }
        }
    }
}

/** Only for the starting point of the local development network: the list carries its own signature. */
private class AnyCertificate : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
