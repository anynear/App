package app.anynear.people

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Own names for other people („Namen ändern“ on the person page): only for oneself, they cover the name from the
 * person's profile everywhere (chat list, chat, circle members, notifications, search). They go to all own devices via
 * the person's settings (ADR 0050, key [SETTING_KEY]), like [app.anynear.alerts.AlertsSeen].
 *
 * Value: JSON `{ "<person ID>": { "n": "<own name>", "t": <change time ms> } }`. Per person the later change wins;
 * „Zurücksetzen“ is a change with an empty name, so it also wins on the other devices. The same in the web app.
 */
object ContactNames {
    const val SETTING_KEY = "contact_names"

    /** Own names are as long as own display names. */
    const val MAX_NAME = 40

    /** Own name [n] for one person, changed at [t]; empty: reset to the profile name. */
    @Serializable
    data class Entry(val n: String = "", val t: Long = 0)

    private val serializer = MapSerializer(String.serializer(), Entry.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    /** Per person the later change wins; on a tie the greater name, so that all devices end up the same. */
    fun merge(a: Map<String, Entry>, b: Map<String, Entry>): Map<String, Entry> =
        (a.keys + b.keys).associateWith { key ->
            val x = a[key]
            val y = b[key]
            when {
                x == null -> y!!
                y == null -> x
                x.t != y.t -> if (x.t > y.t) x else y
                else -> if (x.n >= y.n) x else y
            }
        }

    fun encode(names: Map<String, Entry>): String = json.encodeToString(serializer, names)

    /** Anything unreadable counts as empty so that a broken value from another device blocks nothing. */
    fun decode(text: String?): Map<String, Entry> =
        text?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    /** The own names in effect, person ID → name (resets left out). */
    fun active(names: Map<String, Entry>): Map<String, String> =
        names.mapNotNull { (id, e) -> e.n.trim().takeIf { it.isNotEmpty() }?.let { id to it } }.toMap()

    /** The own name as it is stored: trimmed and at most [MAX_NAME] characters. */
    fun clean(name: String): String = name.trim().take(MAX_NAME)
}
