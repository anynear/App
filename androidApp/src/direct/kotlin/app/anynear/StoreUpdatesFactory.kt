package app.anynear

import android.content.Context

/** Flavor `direct`: no app store; the own [AppUpdater] updates (ADR 0017). No dependency on Google Play. */
@Suppress("UNUSED_PARAMETER")
fun storeUpdates(context: Context): StoreUpdates? = null
