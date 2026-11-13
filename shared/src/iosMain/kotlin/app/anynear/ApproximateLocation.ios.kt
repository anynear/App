package app.anynear

import app.anynear.alerts.GeoPoint
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyReduced
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * Approximate location for official warnings (district, only on the iPhone): „Beim Verwenden der App“, reduced
 * accuracy suffices. Asks only when official warnings are switched on. Lives on the main thread.
 */
internal class IosApproximateLocation(private val openSettings: () -> Unit) : NSObject(), CLLocationManagerDelegateProtocol {
    private val manager = CLLocationManager()
    private var waiting: CancellableContinuation<GeoPoint?>? = null

    val allowed = MutableStateFlow(false)

    init {
        manager.delegate = this
        manager.desiredAccuracy = kCLLocationAccuracyReduced
        allowed.value = isAllowed()
    }

    private fun isAllowed(): Boolean = manager.authorizationStatus.let {
        it == kCLAuthorizationStatusAuthorizedWhenInUse || it == kCLAuthorizationStatusAuthorizedAlways
    }

    /** Not asked yet: iOS asks. Denied: iOS never asks again, then the app's settings. */
    fun request() {
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> allowed.value = true
            else -> openSettings()
        }
    }

    suspend fun read(): GeoPoint? = withContext(Dispatchers.Main) {
        if (!isAllowed()) return@withContext null
        withTimeoutOrNull(TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { cont ->
                waiting?.let { if (it.isActive) it.resume(null) }
                waiting = cont
                manager.requestLocation()
            }
        }
    }

    private fun finish(point: GeoPoint?) {
        val cont = waiting ?: return
        waiting = null
        if (cont.isActive) cont.resume(point)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation
        finish(location?.coordinate?.useContents { GeoPoint(latitude, longitude) })
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        finish(null)
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        allowed.value = isAllowed()
    }
}

private const val TIMEOUT_MILLIS = 20_000L
