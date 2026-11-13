package app.anynear.alerts

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * Acknowledged alerts (“Gesehen”): per alert ID a point in time in ms, positive = acknowledged, negative = shown
 * again. Applies to the person and goes to all own devices via the person's settings (ADR 0050,
 * key [SETTING_KEY]). When merging, the entry with the later point in time (larger absolute value) wins per ID,
 * so no acknowledgement is lost when two devices change something at the same time. An alert that replaces another
 * has a new ID and therefore appears again.
 */
object AlertAcks {
    const val SETTING_KEY = "alerts_acked"

    /** Entries that are older drop out; alerts live at most 30 days (ADR 0062). */
    const val KEEP_MILLIS = 30L * 24 * 60 * 60 * 1000

    private val serializer = MapSerializer(String.serializer(), Long.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    fun isAcked(acks: Map<String, Long>, alertId: String): Boolean = (acks[alertId] ?: 0L) > 0L

    fun ack(acks: Map<String, Long>, alertId: String, now: Long): Map<String, Long> = acks + (alertId to now)

    fun unack(acks: Map<String, Long>, alertId: String, now: Long): Map<String, Long> = acks + (alertId to -now)

    /** Per ID the later point in time wins; on a tie “acknowledged”. */
    fun merge(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> =
        (a.keys + b.keys).associateWith { id ->
            val x = a[id]
            val y = b[id]
            when {
                x == null -> y!!
                y == null -> x
                abs(x) > abs(y) -> x
                abs(y) > abs(x) -> y
                else -> maxOf(x, y)
            }
        }

    fun prune(acks: Map<String, Long>, now: Long): Map<String, Long> = acks.filterValues { now - abs(it) <= KEEP_MILLIS }

    fun encode(acks: Map<String, Long>): String = json.encodeToString(serializer, acks)

    /** Anything unreadable counts as empty so that a broken value from another device blocks nothing. */
    fun decode(text: String?): Map<String, Long> =
        text?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
}
