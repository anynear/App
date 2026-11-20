package app.anynear.location

/**
 * How old a place is, in whole days as people count them (ADR 0121 §24): 0 for today, 1 for yesterday. A level is
 * brought up to date only when something happens, so readers see from when a place is; for an area only the day, never
 * the time, because when someone entered a cell would say more than the cell does.
 */
object PlaceAge {
    private const val DAY = 86_400_000L

    /** After this many days without news a place is not shown any more. */
    const val SHOWN_DAYS = 28

    /** Days between [atMillis] and today; [nextMidnight] is the next local midnight after now. Never negative. */
    fun days(atMillis: Long, nextMidnight: Long): Int = ((nextMidnight - 1 - atMillis) / DAY).coerceAtLeast(0).toInt()

    /** `true` if the place is too old to show. */
    fun gone(atMillis: Long, nextMidnight: Long): Boolean = days(atMillis, nextMidnight) >= SHOWN_DAYS
}
