package app.anynear

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import app.anynear.alerts.GeoPoint
import app.anynear.location.Place
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Approximate location for official warnings (district, only on the phone): only `ACCESS_COARSE_LOCATION`, only in
 * the foreground, without Google services (ADR 0008) via the system's `LocationManager`. With [place] also the place
 * that goes along when nobody confirms a check-in (ADR 0121 §10), as exact as the person allowed.
 */
internal class ApproximateLocation(private val context: Context) {
    fun allowed(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Is location switched on for the device (quick settings „Standort“)? */
    fun servicesOn(): Boolean =
        context.getSystemService(LocationManager::class.java)?.let(LocationManagerCompat::isLocationEnabled) ?: false

    /** First a fresh value from the system, else locate once, else the last known one; `null` if nothing works. */
    @SuppressLint("MissingPermission")
    suspend fun read(): GeoPoint? {
        if (!allowed()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        val known = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        val location = known?.takeIf { System.currentTimeMillis() - it.time < RECENT_MILLIS } ?: current(manager, providers) ?: known
        return location?.let { GeoPoint(it.latitude, it.longitude) }
    }

    /** May the app read the exact location (ADR 0121 §10)? */
    fun preciseAllowed(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Where the phone is, as exact as the system allows (ADR 0121 §10), with its time and accuracy: a value of the last
     * two minutes, else locate once, else the last known one, however old. `null` without permission or when the system
     * gives nothing, as it does in the background. Without Google services, like [read].
     */
    @SuppressLint("MissingPermission")
    /** „Immer“: before Android 10 there is no such permission, the location may be read in the background anyway. */
    fun backgroundAllowed(): Boolean = allowed() && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)

    @Volatile private var latest: Place? = null

    private var moves: android.location.LocationListener? = null

    /**
     * Reports to [onPlace] when the phone has clearly moved (ADR 0121 §24), without locating by itself: from the mobile
     * and Wi-Fi networks, and whatever another app has the phone locate anyway. In the background the system delivers
     * only with „Immer“. [on] = false ends it.
     */
    @SuppressLint("MissingPermission")
    fun watchMoves(on: Boolean, onPlace: (Place) -> Unit) {
        val manager = context.getSystemService(LocationManager::class.java) ?: return
        moves?.let { runCatching { manager.removeUpdates(it) } }
        moves = null
        if (!on || !allowed()) return
        val listener = android.location.LocationListener { location ->
            onPlace(Place(location.latitude, location.longitude, System.currentTimeMillis() - ageMillis(location), if (location.hasAccuracy()) location.accuracy.roundToInt() else null))
        }
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            if (provider !in providers) continue
            runCatching { manager.requestLocationUpdates(provider, MOVE_MIN_MILLIS, MOVE_METERS, listener, android.os.Looper.getMainLooper()) }
                .onFailure { Log.w(TAG, "Watching moves over $provider failed", it) }
        }
        moves = listener
    }

    /** A place the service got while sharing: [place] answers with it as long as it is fresh. */
    fun remember(place: Place) {
        latest = place
    }

    suspend fun place(): Place? {
        val precise = preciseAllowed()
        if (!precise && !allowed()) return null
        latest?.takeIf { System.currentTimeMillis() - it.atMillis < FRESH_MILLIS }?.let { return it }
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        val known = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.minByOrNull(::ageMillis)
        val location = known?.takeIf { ageMillis(it) < FRESH_MILLIS } ?: current(manager, providers, precise) ?: known
        return location?.let {
            Place(it.latitude, it.longitude, System.currentTimeMillis() - ageMillis(it), if (it.hasAccuracy()) it.accuracy.roundToInt() else null)
        }
    }

    /**
     * How old a value is, by the clock that runs since the start of the phone. The time of day in a value comes from
     * its source (for GPS from the satellites) and can be off; the helpers shall read from when the place really is.
     */
    private fun ageMillis(location: Location): Long =
        ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000).coerceAtLeast(0)

    @SuppressLint("MissingPermission")
    private suspend fun current(manager: LocationManager, providers: List<String>, precise: Boolean = false): Location? {
        // Approximate location: from Android 12 on „fused“/GPS is imprecise as well; before that the network remains.
        // Exact location: before Android 12 only GPS is exact, so it comes first there.
        val preferred = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            if (precise && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.GPS_PROVIDER)
        }
        val provider = preferred.firstOrNull { it in providers } ?: return null
        return withTimeoutOrNull(TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Standort: ${e::class.simpleName}")
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private companion object {
        const val TAG = "Location"
        const val RECENT_MILLIS = 10 * 60 * 1000L
        const val FRESH_MILLIS = 2 * 60 * 1000L

        /** A move counts from this far, and is reported at most this often. */
        const val MOVE_METERS = 150f
        const val MOVE_MIN_MILLIS = 5 * 60 * 1000L
        const val TIMEOUT_MILLIS = 20_000L
    }
}
