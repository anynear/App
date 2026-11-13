package app.anynear.ui

import app.anynear.i18n.Language
import app.anynear.i18n.formatLocaleTag
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarMatchNextTime
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

/** Formats per language tag, built once. */
private class Formats(tag: String) {
    private val locale = NSLocale(localeIdentifier = tag.replace('-', '_'))
    private val german = Language.of(tag) == Language.GERMAN
    val time = if (german) pattern("HH:mm") else styled(NSDateFormatterNoStyle, NSDateFormatterShortStyle)
    val dateTime = if (german) pattern("dd.MM.yyyy, HH:mm") else styled(NSDateFormatterMediumStyle, NSDateFormatterShortStyle)
    val shortDate = if (german) pattern("dd.MM.yy") else styled(NSDateFormatterShortStyle, NSDateFormatterNoStyle)
    val weekday = pattern("EEEE")
    val dayMonth = pattern(dayMonthPattern(tag))

    private fun pattern(p: String) = NSDateFormatter().apply {
        dateFormat = p
        locale = this@Formats.locale
    }

    private fun styled(date: ULong, time: ULong) = NSDateFormatter().apply {
        locale = this@Formats.locale
        dateStyle = date
        timeStyle = time
    }
}

private var cached: Pair<String, Formats>? = null

private fun formats(): Formats {
    val tag = formatLocaleTag()
    cached?.let { (t, f) -> if (t == tag) return f }
    return Formats(tag).also { cached = tag to it }
}

private fun date(epochMillis: Long) = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)

internal actual fun formatTime(epochMillis: Long): String = formats().time.stringFromDate(date(epochMillis))

internal actual fun formatDateTime(epochMillis: Long): String = formats().dateTime.stringFromDate(date(epochMillis))

internal actual fun formatShortDate(epochMillis: Long): String = formats().shortDate.stringFromDate(date(epochMillis))

internal actual fun formatDayMonth(epochMillis: Long): String = formats().dayMonth.stringFromDate(date(epochMillis))

internal actual fun formatWeekday(epochMillis: Long): String = formats().weekday.stringFromDate(date(epochMillis))

internal actual fun daysBetween(epochMillis: Long, nowMillis: Long): Long {
    val calendar = NSCalendar.currentCalendar
    return calendar.components(
        NSCalendarUnitDay,
        fromDate = calendar.startOfDayForDate(date(epochMillis)),
        toDate = calendar.startOfDayForDate(date(nowMillis)),
        options = 0u,
    ).day
}

internal actual fun nextLocalHour(nowMillis: Long, hour: Int, minLeadMillis: Long): Long {
    val calendar = NSCalendar.currentCalendar
    val from = date(nowMillis + minLeadMillis)
    val next = calendar.nextDateAfterDate(from, matchingHour = hour.toLong(), minute = 0, second = 0, options = NSCalendarMatchNextTime)
        ?: return nowMillis + minLeadMillis + 24 * 3_600_000L
    return (next.timeIntervalSince1970 * 1000).toLong()
}
