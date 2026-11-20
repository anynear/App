package app.anynear.ui

import app.anynear.i18n.texts

// Date and time in local time and in the language of the app (ADR 0026): in German as before
// („14:05“, „19.10.2026, 14:05“), in English by the country of the phone („2:05 PM“, „Oct 19, 2026, 2:05 PM“).

/** Time, e.g. „14:05“. */
internal expect fun formatTime(epochMillis: Long): String

/** Date and time, e.g. „19.10.2026, 14:05“. */
internal expect fun formatDateTime(epochMillis: Long): String

/** Short date, e.g. „12.10.26“. */
internal expect fun formatShortDate(epochMillis: Long): String

/** Day and month in words, e.g. „20. November“ („November 20“ in US English). */
internal expect fun formatDayMonth(epochMillis: Long): String

/** Weekday, e.g. „Montag“. */
internal expect fun formatWeekday(epochMillis: Long): String

/** Calendar days in local time from [epochMillis] to [nowMillis]: 0 today, 1 yesterday. */
internal expect fun daysBetween(epochMillis: Long, nowMillis: Long): Long

/**
 * Time in the overview „Nachrichten“, as known from messengers: today „14:05“, otherwise
 * „Gestern“, within the last week the weekday („Montag“), before that the date („12.10.26“).
 */
internal fun formatChatListTime(epochMillis: Long, nowMillis: Long): String {
    val days = daysBetween(epochMillis, nowMillis)
    return when {
        days <= 0 -> formatTime(epochMillis)
        days == 1L -> texts.common.yesterday
        days < 7 -> formatWeekday(epochMillis)
        else -> formatShortDate(epochMillis)
    }
}

/** Future point in time with the day in words: „heute, 14:05“, „morgen, 14:05“, „Montag, 14:05“, otherwise date and time. */
internal fun formatFutureDayAndTime(epochMillis: Long, nowMillis: Long): String {
    val days = daysBetween(nowMillis, epochMillis)
    val time = formatTime(epochMillis)
    return when {
        days <= 0 -> texts.common.todayAt(time)
        days == 1L -> texts.common.tomorrowAt(time)
        days < 7 -> texts.common.weekdayAt(formatWeekday(epochMillis), time)
        else -> formatDateTime(epochMillis)
    }
}

/** Point in time with the day in words: „heute, 14:05“, „gestern, 14:05“, „Montag, 14:05“, otherwise date and time. */
internal fun formatDayAndTime(epochMillis: Long, nowMillis: Long): String {
    val days = daysBetween(epochMillis, nowMillis)
    val time = formatTime(epochMillis)
    return when {
        days <= 0 -> texts.common.todayAt(time)
        days == 1L -> texts.common.yesterdayAt(time)
        days < 7 -> texts.common.weekdayAt(formatWeekday(epochMillis), time)
        else -> formatDateTime(epochMillis)
    }
}

/** Next full hour [hour] in local time after [nowMillis] plus [minLeadMillis], e.g. „tomorrow at 7“. */
internal expect fun nextLocalHour(nowMillis: Long, hour: Int, minLeadMillis: Long = 0): Long

/** Pattern for [formatDayMonth]: German „d. MMMM“, English „MMMM d“ in the US and Canada, otherwise „d MMMM“. */
internal fun dayMonthPattern(tag: String): String = when {
    app.anynear.i18n.Language.of(tag) == app.anynear.i18n.Language.GERMAN -> "d. MMMM"
    app.anynear.i18n.regionOf(tag) in setOf("US", "CA", "PH") -> "MMMM d"
    else -> "d MMMM"
}

/** A time of day as text, for texts built outside the interface (a message that names a time). */
internal fun formatTimeText(epochMillis: Long): String = formatTime(epochMillis)
