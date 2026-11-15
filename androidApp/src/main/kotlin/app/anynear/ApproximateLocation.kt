package app.anynear

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import app.anynear.alerts.GeoPoint
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Approximate location for official warnings (district, only on the phone): only `ACCESS_COARSE_LOCATION`, only in
 * the foreground, without Google services (ADR 0008) via the system's `LocationManager`.
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

    @SuppressLint("MissingPermission")
    private suspend fun current(manager: LocationManager, providers: List<String>): Location? {
        // Approximate location: from Android 12 on „fused“/GPS is imprecise as well; before that the network remains.
        val preferred = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
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
        const val TIMEOUT_MILLIS = 20_000L
    }
}
