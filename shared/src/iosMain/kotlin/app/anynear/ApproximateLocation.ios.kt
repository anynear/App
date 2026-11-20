package app.anynear

import app.anynear.alerts.GeoPoint
import app.anynear.location.Place
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLAccuracyAuthorization
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreLocation.kCLLocationAccuracyReduced
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

/**
 * Approximate location for official warnings (district, only on the iPhone): „Beim Verwenden der App“, reduced
 * accuracy suffices. Asks only when official warnings are switched on. With [place] also the place that goes along when
 * nobody confirms a check-in (ADR 0121 §10), as exact as iOS allows. Lives on the main thread.
 */
internal class IosApproximateLocation(private val openSettings: () -> Unit) : NSObject(), CLLocationManagerDelegateProtocol {
    private val manager = CLLocationManager()
    private var waiting: CancellableContinuation<CLLocation?>? = null

    val allowed = MutableStateFlow(false)

    /** Allowed with „Genauer Standort“ (ADR 0121 §10). Without it iOS gives a place a few kilometres wide. */
    val precise = MutableStateFlow(false)

    init {
        manager.delegate = this
        manager.desiredAccuracy = kCLLocationAccuracyReduced
        refresh()
    }

    private fun refresh() {
        allowed.value = isAllowed()
        precise.value = isAllowed() && manager.accuracyAuthorization == CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy
        always.value = manager.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways
    }

    private fun isAllowed(): Boolean = manager.authorizationStatus.let {
        it == kCLAuthorizationStatusAuthorizedWhenInUse || it == kCLAuthorizationStatusAuthorizedAlways
    }

    /** Location services of the whole iPhone (Settings → Privacy → Location Services). */
    fun servicesOn(): Boolean = CLLocationManager.locationServicesEnabled()

    /** Not asked yet: iOS asks. Denied: iOS never asks again, then the app's settings. */
    fun request() {
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> refresh()
            else -> openSettings()
        }
    }

    /**
     * For the exact location (ADR 0121 §3): not asked yet, iOS asks, with „Genau“ switched on. Allowed only
     * approximately: iOS asks whether the app may have the exact location this time (the purpose `Share` from
     * Info.plist); for good it is the switch „Genauer Standort“ in the app's settings. Denied: the settings.
     */
    fun requestPrecise() {
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways ->
                if (precise.value) refresh() else manager.requestTemporaryFullAccuracyAuthorizationWithPurposeKey("Share") { refresh() }
            else -> openSettings()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    suspend fun read(): GeoPoint? = locate(kCLLocationAccuracyReduced)?.coordinate?.useContents { GeoPoint(latitude, longitude) }

    /** Where the iPhone is, as exact as iOS allows, with time and accuracy; `null` if not allowed or not obtainable. */
    @OptIn(ExperimentalForeignApi::class)
    suspend fun place(): Place? = locate(kCLLocationAccuracyBest)?.let(::placeOf)

    @OptIn(ExperimentalForeignApi::class)
    private fun placeOf(location: CLLocation): Place {
        val accuracy = location.horizontalAccuracy.takeIf { it >= 0 }?.toInt()
        val at = (location.timestamp.timeIntervalSince1970 * 1000).toLong()
        return location.coordinate.useContents { Place(latitude, longitude, at, accuracy) }
    }

    private suspend fun locate(accuracy: Double): CLLocation? = withContext(Dispatchers.Main) {
        if (!isAllowed()) return@withContext null
        withTimeoutOrNull(TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { cont ->
                waiting?.let { if (it.isActive) it.resume(null) }
                waiting = cont
                manager.desiredAccuracy = accuracy
                manager.requestLocation()
            }
        }
    }

    private fun finish(location: CLLocation?) {
        val cont = waiting ?: return
        waiting = null
        if (cont.isActive) cont.resume(location)
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation
        finish(location)
        if (sharing && location != null) placeOf(location).let { updates.tryEmit(it) }
    }

    /** Places while a place is shared ([share]), also with the app in the background. */
    val updates = kotlinx.coroutines.flow.MutableSharedFlow<Place>(extraBufferCapacity = 4, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)

    private var sharing = false

    /** „Immer“ (ADR 0121 §13): the levels that share all the time continue after a restart of the iPhone with it. */
    val always = MutableStateFlow(false)

    fun requestAlways() {
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedAlways -> refresh()
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusNotDetermined -> manager.requestAlwaysAuthorization()
            else -> openSettings()
        }
    }

    /**
     * While a place leaves the iPhone (sharing for a time, a level that shares all the time, a call for the helpers):
     * the iPhone keeps locating with the app in the background and shows its own sign for it (the blue pill). Started
     * while the app is open, „Beim Verwenden der App“ is enough.
     */
    fun share(on: Boolean) {
        if (on == sharing) return
        sharing = on
        if (on && isAllowed()) {
            manager.allowsBackgroundLocationUpdates = true
            manager.pausesLocationUpdatesAutomatically = false
            manager.showsBackgroundLocationIndicator = true
            manager.desiredAccuracy = kCLLocationAccuracyBest
            manager.distanceFilter = 30.0
            manager.startUpdatingLocation()
        } else {
            manager.stopUpdatingLocation()
            manager.allowsBackgroundLocationUpdates = false
        }
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        finish(null)
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        refresh()
    }
}

private const val TIMEOUT_MILLIS = 20_000L
