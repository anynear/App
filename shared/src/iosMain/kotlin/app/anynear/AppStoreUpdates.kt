package app.anynear

import app.anynear.app.AppUpdateState
import app.anynear.app.isNewerVersion
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.NSURL
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication

/**
 * Notice about a new version for an iPhone that installed anynear from the App Store (ADR 0096 §7), without the
 * signed list: on start and at most every 6 hours the app asks Apple's public lookup for the version in the store and
 * compares it with its own `X.Y.Z`. The request names only the app ID. „Jetzt aktualisieren“ opens the App Store page.
 *
 * Not for TestFlight (TestFlight reports new builds itself, the app cannot ask it) and not in debug builds: then
 * [state] stays [AppUpdateState.Unsupported].
 */
internal class AppStoreUpdates(private val debug: Boolean) {
    /** Installed through TestFlight: Apple's receipt there is the sandbox one. */
    val testFlight: Boolean = NSBundle.mainBundle.appStoreReceiptURL?.lastPathComponent == "sandboxReceipt"

    private val enabled = !debug && !testFlight
    private val installed: String? = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String

    private val _state = MutableStateFlow<AppUpdateState>(if (enabled) AppUpdateState.Idle else AppUpdateState.Unsupported)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    /** Page of anynear in the App Store, once the lookup found it. */
    var storeUrl: String? = null
        private set

    private var lastCheck = 0.0
    private val scope = CoroutineScope(Dispatchers.Default)
    private val http by lazy { HttpClient(Darwin) }

    fun check(force: Boolean) {
        if (!enabled || _state.value is AppUpdateState.Checking) return
        val now = NSDate().timeIntervalSince1970
        if (!force && now - lastCheck < CHECK_INTERVAL_SECONDS) return
        lastCheck = now
        if (force) _state.value = AppUpdateState.Checking
        scope.launch {
            val found = try {
                lookup()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.value = when {
                found == null -> if (force) AppUpdateState.Failed(app.anynear.i18n.texts.system.updateFailed, null) else AppUpdateState.Idle
                installed != null && isNewerVersion(found.first, installed) -> AppUpdateState.Available(found.first, 0)
                else -> AppUpdateState.UpToDate
            }
        }
    }

    /** Opens the App Store page; `false` if the lookup has not found one (yet). */
    fun openStore(): Boolean {
        val url = storeUrl?.let { NSURL.URLWithString(it.replaceFirst("https://", "itms-apps://")) } ?: return false
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
        return true
    }

    /** Version and page in the App Store of the phone's country, `null` if anynear is not there. */
    private suspend fun lookup(): Pair<String, String>? {
        val country = (NSLocale.currentLocale.countryCode ?: "DE").lowercase()
        val text = http.get("$LOOKUP_URL?bundleId=${NSBundle.mainBundle.bundleIdentifier ?: "app.anynear"}&country=$country").bodyAsText()
        val result = ((Json.parseToJsonElement(text) as? JsonObject)?.get("results") as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val version = (result["version"] as? JsonPrimitive)?.content ?: return null
        val page = (result["trackViewUrl"] as? JsonPrimitive)?.content ?: return null
        storeUrl = page
        return version to page
    }

    private companion object {
        const val LOOKUP_URL = "https://itunes.apple.com/lookup"
        const val CHECK_INTERVAL_SECONDS = 6 * 60 * 60.0
    }
}
