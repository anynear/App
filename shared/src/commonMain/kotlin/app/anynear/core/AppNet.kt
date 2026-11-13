package app.anynear.core

import app.anynear.andp.Key
import app.anynear.andp.AndpClient
import app.anynear.andp.Relay
import app.anynear.andp.networkId
import app.anynear.anmp.core.AnmpCoreConfig
import app.anynear.AppConfig
import app.anynear.anmp.core.RelayConnector
import app.anynear.anmp.core.platformConnector

/**
 * The network in which the app works (ADR 0035 §6): network key and ID of the anynear network from the ANMP core
 * ([AnmpCoreConfig.default]), plus the network list embedded at build time (`net.json`, ADR 0015). Without an
 * embedded list the core fetches it on first start from the seed `https://anynear.net/andp/v1/net`.
 *
 * [DEV] (only with `app.devNet=true` in local.properties): the local Docker network from Relay
 * `compose.net.yaml` with the open test key. Real builds do not know it.
 */
object AppNet {
    /** Local development network instead of the anynear network. */
    val DEV: Boolean = CONFIGURED_DEV_NET

    /**
     * Open test key of the local Docker relays (Relay `compose.net.yaml`, `-network-key`): everyone knows
     * the private part, so only development builds trust it.
     */
    const val DEV_NETWORK_KEY = "atG0seQHRDwR_M2oXoaq8J-caAT7aDXGoI1mRkAnff0"

    fun config(): AnmpCoreConfig =
        if (DEV) {
            val key = Key.parse(DEV_NETWORK_KEY)
            AnmpCoreConfig(networkId(key), key, seedUrl = "https://$devRelayHost:9081")
        } else {
            // Curated channels (ADR 0062 §7): only what is listed here counts as official.
            val base = AnmpCoreConfig.default(EMBEDDED_NET_LIST)
            AnmpCoreConfig(base.networkId, base.networkKey, base.embeddedList, curatedChannels = listOfNotNull(AppConfig.OFFICIAL_ALERTS_LINK))
        }

    /** Connection to the relays: by IP with a pinned key; in the development network via the ports on the computer. */
    fun connector(): RelayConnector = if (DEV) DevConnector else platformConnector()

    /**
     * In the Docker network the list contains the internal addresses (172.29.0.11–15:8080) so that the relays
     * reach each other. From the emulator, simulator or phone they go via [devRelayHost]:9081–9085.
     */
    internal fun devAddress(addr: String): String =
        DEV_ADDR.matchEntire(addr)?.let { "$devRelayHost:908${it.groupValues[1]}" } ?: addr

    private val DEV_ADDR = Regex("""172\.29\.0\.1([1-5]):8080""")

    private object DevConnector : RelayConnector {
        private val pinned = platformConnector()

        override fun connect(relay: Relay): AndpClient? = pinned.connect(relay.copy(addr = devAddress(relay.addr)))

        override fun connectUrl(baseUrl: String): AndpClient = AndpClient.forUrl(baseUrl, devSeedHttpClient())
    }
}
