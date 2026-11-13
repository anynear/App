package app.anynear.push

import app.anynear.data.platformKeyValueStore

/**
 * Counter on the app icon between app and notification extension (shared App Group). The app sets it exactly as soon as
 * it runs; the extension adds one for every notification shown. Only a number, nothing confidential.
 */
object WakeBadge {
    private const val KEY = "app.badge"

    fun set(count: Int) = platformKeyValueStore().putString(KEY, count.coerceAtLeast(0).toString())

    /** One more than last known; so the new notification is already in the counter. */
    fun next(): Int {
        val store = platformKeyValueStore()
        val n = (store.getString(KEY)?.toIntOrNull() ?: 0) + 1
        store.putString(KEY, n.toString())
        return n
    }
}

/** For the extension (Swift): next value of the counter on the app icon. */
@Suppress("unused")
fun nextWakeBadge(): Int = WakeBadge.next()
