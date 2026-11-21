package app.anynear.ui

import app.anynear.alerts.Alert

/**
 * Tapping a coloured district on the map (ADR 0121 §7): which official warnings apply there, and where the speech
 * bubble with them stands. Pure, so that it can be tested; the map and its bubble only call these.
 */

/**
 * Whether an alert with the regional key prefixes [area] covers the district [kreis] (5 digits). The same rule the
 * map colours by: a warning for a whole state (`06`) covers every district in it, one for a district covers that one.
 */
internal fun coversKreis(area: List<String>, kreis: String): Boolean =
    area.any { a -> val prefix = a.take(5); prefix.isNotEmpty() && (kreis.startsWith(prefix) || prefix.startsWith(kreis)) }

/**
 * The warnings of [active] that apply in the district [kreis], each once, the highest level first and among equal
 * levels the newest first. `null` (outside the districts of the map): none.
 */
internal fun warningsAt(active: List<Alert>, kreis: String?): List<Alert> {
    if (kreis.isNullOrEmpty()) return emptyList()
    return active.filter { it.area.isNotEmpty() && coversKreis(it.area, kreis) }
        .distinctBy { it.channelId to it.id }
        .sortedWith(compareByDescending<Alert> { it.priority }.thenByDescending { it.timestampMillis })
}

/** What a tap on the map means. */
internal sealed interface MapTap {
    /** A person was hit: their dialog opens, even if they stand in a warning area. */
    data class Person(val id: String) : MapTap

    /** A coloured district: its warnings, the highest level first. */
    data class Warnings(val kreis: String, val alerts: List<Alert>) : MapTap

    /** Nothing there: an open bubble closes. */
    data object Nothing : MapTap
}

/**
 * A tap on the map: a person ([person], already hit by the map) goes before the district below, then the warnings of
 * the district [kreis] the tap lies in.
 */
internal fun mapTap(person: String?, kreis: String?, active: List<Alert>): MapTap {
    if (person != null) return MapTap.Person(person)
    val here = warningsAt(active, kreis)
    return if (kreis != null && here.isNotEmpty()) MapTap.Warnings(kreis, here) else MapTap.Nothing
}

/**
 * What kind of warning it is. The relay names the source after the system of the warning (Relay
 * `internal/nina/bbk.go`, `Providers`): DWD for the weather, the flood centres, the police, and the systems of civil
 * protection (MoWaS of the BBK, KATWARN, BIWAPP). Unknown sources: `null`, then only the level is shown.
 */
internal enum class WarningKind { Weather, Flood, Police, CivilProtection }

internal fun warningKind(source: String?): WarningKind? {
    val s = source ?: return null
    return when {
        s.contains("DWD", ignoreCase = true) -> WarningKind.Weather
        s.contains("Hochwasser", ignoreCase = true) || s.contains("LHP", ignoreCase = true) -> WarningKind.Flood
        s.contains("Polizei", ignoreCase = true) -> WarningKind.Police
        listOf("BBK", "MoWaS", "KATWARN", "BIWAPP").any { s.contains(it, ignoreCase = true) } -> WarningKind.CivilProtection
        else -> null
    }
}

/**
 * Names of the area of a warning as people know them: districts by [kreisNames], whole states by [stateNames]; keys
 * without a known name are left out, each name once, in the order of the warning.
 */
internal fun areaNames(area: List<String>, kreisNames: Map<String, String>, stateNames: Map<String, String>): List<String> =
    area.mapNotNull { a -> if (a.length <= 2) stateNames[a] else kreisNames[a.take(5)] }.distinct()

/** Where the bubble with the warnings stands: its upper left corner, whether it hangs below the tap, and where its tail meets it. */
internal data class PopupSpot(val left: Float, val top: Float, val below: Boolean, val tailX: Float)

/**
 * Places the bubble of [width] × [height] for a tap at ([tipX], [tipY]) in a view of [viewWidth] × [viewHeight] like
 * the bubbles of the people: above the tap with its tail pointing down to it; where there is no room above, below it.
 * Horizontally centred on the tap, but never over the edge ([margin]); the tail stays on the straight part of the edge
 * ([corner]) and so may lean towards the tap.
 */
internal fun placeWarningPopup(
    tipX: Float, tipY: Float, width: Float, height: Float, viewWidth: Float, viewHeight: Float, tail: Float, margin: Float, corner: Float,
): PopupSpot {
    val left = if (width >= viewWidth - 2 * margin) margin else (tipX - width / 2).coerceIn(margin, viewWidth - width - margin)
    val above = tipY - tail - margin
    val belowRoom = viewHeight - tipY - tail - margin
    val below = height > above && belowRoom > above
    val top = if (below) tipY + tail else tipY - tail - height
    val tailX = if (width <= 2 * corner) left + width / 2 else tipX.coerceIn(left + corner, left + width - corner)
    return PopupSpot(left, top, below, tailX)
}

/** The most the bubble may be high so that it fits above or below the tap, whichever has more room. */
internal fun warningPopupMaxHeight(tipY: Float, viewHeight: Float, tail: Float, margin: Float): Float =
    maxOf(tipY - tail - margin, viewHeight - tipY - tail - margin).coerceAtLeast(0f)
