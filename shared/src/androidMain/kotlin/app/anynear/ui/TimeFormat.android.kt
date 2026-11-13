package app.anynear.ui

import app.anynear.i18n.Language
import app.anynear.i18n.currentLanguage
import app.anynear.i18n.formatLocaleTag
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Formats per language tag, built once. */
private class Formats(tag: String) {
    private val locale = Locale.forLanguageTag(tag)
    private val german = Language.of(tag) == Language.GERMAN
    val time: DateTimeFormatter = if (german) pattern("HH:mm") else DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val dateTime: DateTimeFormatter =
        if (german) pattern("dd.MM.yyyy, HH:mm") else DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    val shortDate: DateTimeFormatter = if (german) pattern("dd.MM.yy") else DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    val weekday: DateTimeFormatter = pattern("EEEE")
    val dayMonth: DateTimeFormatter = pattern(dayMonthPattern(tag))

    private fun pattern(p: String) = DateTimeFormatter.ofPattern(p, locale)
}

@Volatile private var cached: Pair<String, Formats>? = null

private fun formats(): Formats {
    val tag = formatLocaleTag()
    cached?.let { (t, f) -> if (t == tag) return f }
    return Formats(tag).also { cached = tag to it }
}

private fun local(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())

internal actual fun formatTime(epochMillis: Long): String = formats().time.format(local(epochMillis))

internal actual fun formatDateTime(epochMillis: Long): String = formats().dateTime.format(local(epochMillis))

internal actual fun formatShortDate(epochMillis: Long): String = formats().shortDate.format(local(epochMillis))

internal actual fun formatDayMonth(epochMillis: Long): String = formats().dayMonth.format(local(epochMillis))

internal actual fun formatWeekday(epochMillis: Long): String = formats().weekday.format(local(epochMillis))

internal actual fun daysBetween(epochMillis: Long, nowMillis: Long): Long =
    ChronoUnit.DAYS.between(local(epochMillis).toLocalDate(), local(nowMillis).toLocalDate())

internal actual fun nextLocalHour(nowMillis: Long, hour: Int, minLeadMillis: Long): Long {
    val start = local(nowMillis).toLocalDate().atTime(hour, 0).atZone(ZoneId.systemDefault())
    val today = start.toInstant().toEpochMilli()
    return if (today > nowMillis + minLeadMillis) today else start.plusDays(1).toInstant().toEpochMilli()
}
