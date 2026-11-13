package app.anynear.app

import app.anynear.anmp.core.firmware.RadioCatalog
import app.anynear.anmp.core.firmware.RadioMatch
import app.anynear.anmp.core.firmware.RecommendedRadio
import app.anynear.data.KeyValueStore
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The recommended radios from the website ([RadioCatalog.URL], ADR 0095 §2): which models to offer first, with picture
 * and „good for“ line, and how an empty radio is recognised on the cable. The webapp reads the same list through ANMP.
 * Fallback (ADR 0095 §2): the last list loaded successfully (kept in the app's store), otherwise the copy built in here
 * ([BUNDLED]).
 */
object RecommendedRadios {
    private var cached: RadioCatalog? = null

    private val client by lazy {
        HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
            }
        }
    }

    /**
     * Loaded successfully only once per app start. Without internet or on an unusable response the last good list,
     * otherwise [BUNDLED]; never `null`, so the choice always shows the recommended radios. [store] keeps the last good list.
     */
    suspend fun loadOnce(store: KeyValueStore? = null): RadioCatalog {
        cached?.let { return it }
        val text = runCatching {
            val response = client.get(RadioCatalog.URL)
            if (response.status.isSuccess()) response.bodyAsText() else null
        }.getOrNull()
        val loaded = text?.let(RadioCatalog::parse)?.takeIf { it.radios.isNotEmpty() }
        if (loaded != null) {
            runCatching { store?.putString(STORE_KEY, text) }
            cached = loaded
            return loaded
        }
        return runCatching { store?.getString(STORE_KEY) }.getOrNull()?.let(RadioCatalog::parse)?.takeIf { it.radios.isNotEmpty() }
            ?: BUNDLED
    }

    private val images = mutableMapOf<String, ByteArray?>()
    private val imageLock = Mutex()

    /**
     * The picture of a radio ([RecommendedRadio.image]) as bytes, once per app start; `null` if it cannot be loaded
     * (then the dialog shows the radio icon instead). Only links on the website are loaded.
     */
    suspend fun image(url: String): ByteArray? {
        if (!url.startsWith(IMAGE_PREFIX)) return null
        imageLock.withLock { if (url in images) return images[url] }
        val bytes = try {
            val response = client.get(url)
            if (response.status.isSuccess()) response.bodyAsBytes().takeIf { it.isNotEmpty() && it.size <= MAX_IMAGE_BYTES } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
        // Failures are not kept: the next opening of the dialog tries again.
        if (bytes != null) imageLock.withLock { images[url] = bytes }
        return bytes
    }

    private const val STORE_KEY = "radios.catalog"
    private const val IMAGE_PREFIX = "https://anynear.app/"
    private const val MAX_IMAGE_BYTES = 2_000_000

    /**
     * Copy of `radios.json` (Fassung 1, website PR 65) for when the list cannot be loaded and none was loaded before.
     * Kept in step with `Website/src/lib/radios.mjs`.
     */
    val BUNDLED = RadioCatalog(
        version = 1,
        radios = listOf(
            RecommendedRadio(
                id = "meshnology-n35",
                name = "Meshnology N35",
                hwModel = "HELTEC_V3",
                mcu = "esp32s3",
                image = "https://anynear.app/assets/devices/meshnology-n35.webp",
                use = mapOf("de" to "Unterwegs oder zu Hause", "en" to "On the go or at home"),
                match = RadioMatch(usb = listOf("10c4:ea60"), embeddedFlashMb = 8, psramMb = 0),
            ),
            RecommendedRadio(
                id = "sensecap-t1000-e",
                name = "SenseCAP T1000-E",
                hwModel = "TRACKER_T1000_E",
                mcu = "nrf52840",
                image = "https://anynear.app/assets/devices/sensecap-t1000-e.webp",
                use = mapOf("de" to "Mobiler Begleiter", "en" to "Mobile companion"),
                match = RadioMatch(usb = listOf("239a:8029", "239a:0029", "239a:002a", "239a:802a", "2886:0057")),
            ),
            RecommendedRadio(
                id = "sensecap-solar-node",
                name = "SenseCAP Solar Node",
                hwModel = "SEEED_SOLAR_NODE",
                mcu = "nrf52840",
                image = "https://anynear.app/assets/devices/sensecap-solar-node.webp",
                use = mapOf("de" to "Mehr Reichweite draußen", "en" to "More range outdoors"),
                match = RadioMatch(usb = listOf("2886:0059")),
            ),
        ),
    )
}
