package app.anynear

import app.anynear.andp.Hash
import app.anynear.andp.NetworkList
import app.anynear.andp.AndpClient
import app.anynear.andp.Relay
import app.anynear.andp.RelayState
import app.anynear.andp.SignedList
import app.anynear.andp.SigningKey
import app.anynear.andp.networkId
import app.anynear.andp.spkiHash
import app.anynear.anmp.core.CoreTimings
import app.anynear.andp.Grant
import app.anynear.andp.GrantBody
import app.anynear.andp.Rights
import app.anynear.anmp.core.AnmpCoreConfig
import app.anynear.anmp.core.RelayConnector
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Call
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Collections
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration.Companion.seconds

/**
 * Local ANDP network for the E2E suites: five local relays (ANDP 1, operators a, a, b, c, d). Runs only with
 * `RELAY_E2E=1` and the relays running:
 *
 *   RELAY_E2E=1 ./gradlew :shared:testAndroidHostTest
 *
 * The test signs the network list itself with the network's open test key and offers it to all relays.
 * It contains the addresses in the Docker network (172.29.0.11–15:8080) so that the relays reach each
 * other; the apps in the test go via the ports on the machine (127.0.0.1:9081–9085).
 */
internal object LocalNet {
    val enabled: Boolean = System.getenv("RELAY_E2E") == "1"

    private val hostPorts = (1..5).map { "127.0.0.1:908$it" }
    private val internal = (1..5).map { "172.29.0.1$it:8080" }
    private val sites = listOf("relay-a", "relay-b", "relay-c", "relay-d", "relay-e")
    private val ops = listOf("a", "a", "b", "c", "d")

    /** Open test key from compose.net.yaml: SHA-256 of the sentence that the Go test also uses. */
    private val networkKey: SigningKey by lazy {
        runBlocking { SigningKey.fromSeed(MessageDigest.getInstance("SHA-256").digest("PINA-Relay E2E: nur für die lokalen Docker-Briefkästen".encodeToByteArray())) }
    }

    /** Pinned TLS keys of the five relays, as they present themselves from the machine. */
    private val fingerprints: List<Hash> by lazy { hostPorts.map(::fingerprint) }

    /** Signed network list, offered to all relays (once per test run). */
    val signed: SignedList by lazy {
        runBlocking {
            val relays = hostPorts.indices.map { i ->
                val node = AndpClient.forUrl("https://${hostPorts[i]}", http(trustAll = true)).use { it.info().node }
                Relay(site = sites[i], addr = internal[i], key = fingerprints[i], node = node, op = ops[i], state = RelayState.ACTIVE)
            }
            val list = NetworkList(1, networkId(networkKey.key), networkKey.key, System.currentTimeMillis(), relays = relays).sign(networkKey)
            hostPorts.forEach { hp -> runCatching { AndpClient.forUrl("https://$hp", http(trustAll = true)).use { it.offerNet(list) } } }
            list
        }
    }

    /** Shorter times so that the flows run through in seconds. */
    val timings = CoreTimings(
        backoffMax = 5.seconds,
        dutyFirst = 1.seconds,
        dutyStep = 5.seconds,
        reannounceDelay = 1.seconds,
        leaveRotateDelay = 2.seconds,
        historyRetryMin = 2.seconds,
        historyRetryMax = 10.seconds,
    )

    /** radio inbox of the test network (ADR 0051): space with a test key, grant for all, valid for one day. */
    private val radioInbox: Pair<String, String> by lazy {
        runBlocking {
            val owner = SigningKey.fromSeed(ByteArray(32) { 51 })
            val now = System.currentTimeMillis() / 1000
            val grant = Grant.issue(owner, GrantBody(owner.key, Rights(1) + Rights(2) + Rights(4), null, null, now + 86_400, now - 60))
            owner.key.toString() to grant.toString()
        }
    }

    fun config(): AnmpCoreConfig = AnmpCoreConfig(
        networkId(networkKey.key), networkKey.key,
        embeddedList = Json.encodeToString(SignedList.serializer(), signed),
        seedUrl = "https://${hostPorts.first()}",
        timings = timings,
        radioInboxSpace = radioInbox.first,
        radioInboxGrant = radioInbox.second,
    )

    /**
     * Connection of an app in the test: pinned to the network's relays, via the ports on the machine. If
     * [online] is `false`, every request fails, running ones are cancelled („kein Internet“).
     */
    fun connector(online: StateFlow<Boolean>): RelayConnector {
        val calls = Collections.synchronizedSet(mutableSetOf<Call>())
        val http = http(trustAll = false, online = online, calls = calls)
        return object : RelayConnector {
            override fun connect(relay: Relay): AndpClient? {
                val i = internal.indexOf(relay.addr).takeIf { it >= 0 } ?: return null
                return AndpClient.webPki(relay.copy(host = hostPorts[i]), http)
            }

            override fun connectUrl(baseUrl: String): AndpClient = AndpClient.forUrl(baseUrl, http)
        }.also {
            Thread {
                var last = online.value
                while (true) {
                    Thread.sleep(50)
                    val now = online.value
                    if (last && !now) synchronized(calls) { calls.toList() }.forEach(Call::cancel)
                    last = now
                }
            }.apply { isDaemon = true }.start()
        }
    }

    /** Without network: configuration without a list and a connection that never comes about (tests without relays). */
    fun offlineConfig(): AnmpCoreConfig = AnmpCoreConfig(networkId(networkKey.key), networkKey.key, seedUrl = "https://127.0.0.1:1", timings = timings)

    val offlineConnector: RelayConnector = object : RelayConnector {
        override fun connect(relay: Relay): AndpClient? = null

        override fun connectUrl(baseUrl: String): AndpClient = AndpClient.forUrl("https://127.0.0.1:1", http(trustAll = true))
    }

    private fun http(trustAll: Boolean, online: StateFlow<Boolean>? = null, calls: MutableSet<Call>? = null): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 60_000
        }
        engine {
            config {
                val trust = PinTrust(if (trustAll) null else fingerprints.toSet())
                val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), null) }
                sslSocketFactory(tls.socketFactory, trust)
                hostnameVerifier { _, _ -> true }
                addInterceptor { chain ->
                    if (online != null && !online.value) throw IOException("kein Internet (Test)")
                    calls?.add(chain.call())
                    try {
                        chain.proceed(chain.request())
                    } finally {
                        calls?.remove(chain.call())
                    }
                }
            }
        }
    }

    /** Accepts only certificates with one of the pinned keys ([pins] `null`: any, only for setup). */
    private class PinTrust(private val pins: Set<Hash>?) : X509TrustManager {
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("ohne Zertifikat")
            if (pins != null && spkiHash(leaf.encoded) !in pins) throw CertificateException("anderer Schlüssel als angepinnt")
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?): Unit = throw CertificateException()

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private fun fingerprint(hostPort: String): Hash {
        val (host, port) = hostPort.split(":")
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(PinTrust(null)), null) }
        (tls.socketFactory.createSocket(host, port.toInt()) as SSLSocket).use { s ->
            s.startHandshake()
            return spkiHash(s.session.peerCertificates.first().encoded)
        }
    }
}
