package app.anynear.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** [inner] that reports every key whose value actually changed, e.g. to put settings into the network (ADR 0106). */
class ObservedKeyValueStore(private val inner: KeyValueStore) : KeyValueStore {
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 256)

    /** Keys written with a new value (or removed), in order. */
    val changes: SharedFlow<String> = _changes.asSharedFlow()

    override fun getString(key: String): String? = inner.getString(key)

    override fun putString(key: String, value: String?) {
        if (inner.getString(key) == value) return
        inner.putString(key, value)
        _changes.tryEmit(key)
    }
}
