package app.anynear.data

/**
 * Key-value store of the platform without encryption on top: all entries as they are on the
 * device, and replace all of them at once (fresh start, migrations).
 */
interface RawKeyValueAccess {
    fun entries(): Map<String, String>

    /** Removes all previous entries and stores [values]. */
    fun replaceAll(values: Map<String, String>)
}

expect fun platformRawKeyValueAccess(): RawKeyValueAccess

/** Names of all entries in the protected store of the platform ([platformSecretStore]). */
expect fun platformSecretKeys(): Set<String>

/**
 * Restarts the app, e.g. after taking over the keys of another own device. Android: new process. iOS: the app must not
 * quit itself (to the person it looks like a crash), so the controller is replaced within the process.
 */
expect fun restartApp()

/** Delete the cache of the radio SDK (devices in range, databases `meshtastic…`); only before the SDK starts. */
expect fun deleteRadioCache()

/** Name of the device for „Meine Geräte“ (ADR 0042), e.g. the model "Pixel 9" or "iPad". */
expect fun platformDeviceName(): String
