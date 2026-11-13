package app.anynear.data

/** Small persistent key-value store (Android: SharedPreferences, iOS: NSUserDefaults). */
interface KeyValueStore {
    fun getString(key: String): String?

    /** `null` removes the entry. */
    fun putString(key: String, value: String?)
}

expect fun platformKeyValueStore(): KeyValueStore

/** Directory for the SDK database (NodeDB cache). */
expect fun platformDataDir(): String
