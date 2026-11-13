package app.anynear.mesh

import android.content.Context
import org.meshtastic.sdk.storage.sqldelight.AndroidContextHolder

/** Needed by the SDK for its SQLDelight database. */
internal fun initMeshStorage(app: Context) {
    AndroidContextHolder.context = app
}
