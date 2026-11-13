package app.anynear.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

/**
 * Log for troubleshooting, only in memory and only the last [max] lines. Contains no
 * message texts. To be seen and shared under Einstellungen > App > Erweitert so that it works without a
 * USB cable.
 */
class DebugLog(
    private val max: Int = 1_000,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    data class Entry(val atMillis: Long, val tag: String, val message: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun add(tag: String, message: String) {
        val entry = Entry(now(), tag, message)
        _entries.update { (it + entry).takeLast(max) }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
