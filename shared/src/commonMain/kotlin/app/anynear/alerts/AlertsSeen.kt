package app.anynear.alerts

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Up to when a channel has been seen (Unix ms per channel ID), for the person on all own devices: goes via the
 * person's settings (ADR 0050, key [SETTING_KEY]). Per channel the later point in time wins; that way a
 * channel read on the phone is also read on the tablet, and its notifications disappear there.
 */
object AlertsSeen {
    const val SETTING_KEY = "alerts_seen"

    private val serializer = MapSerializer(String.serializer(), Long.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    fun merge(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> =
        (a.keys + b.keys).associateWith { maxOf(a[it] ?: 0L, b[it] ?: 0L) }

    fun encode(seen: Map<String, Long>): String = json.encodeToString(serializer, seen)

    /** Anything unreadable counts as empty so that a broken value from another device blocks nothing. */
    fun decode(text: String?): Map<String, Long> =
        text?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
}
