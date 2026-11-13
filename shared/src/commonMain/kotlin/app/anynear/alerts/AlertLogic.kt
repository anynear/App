package app.anynear.alerts

// Pure rules of the “Meldungen” view (ADR 0062 §5, §7, §9), without platform; tested in AlertsTest.

/**
 * State at time [nowMillis]: ended (itself an all-clear or replaced by one), expired
 * (“gültig bis” passed) or active. The core computes “expired” only on loading; the app recomputes it for display.
 */
fun Alert.statusAt(nowMillis: Long): AlertStatus = when {
    ended || endedByOther -> AlertStatus.Ended
    untilMillis <= nowMillis -> AlertStatus.Expired
    else -> AlertStatus.Active
}

/**
 * Location filter for curated channels, runs only on the phone (implementation plan §5). [kreis] is the district key
 * from the approximate location ([AlertRegion.kreis]); `null`: still unknown, then no official alert counts. An alert
 * matches if one of its areas (regional key prefixes) contains the district or lies within it (a warning for all of
 * Hesse `06` matches Frankfurt `06412`). Alerts without an area apply everywhere.
 */
fun inRegion(alert: Alert, kreis: String?): Boolean =
    kreis != null && (alert.area.isEmpty() || alert.area.any { a -> a.isNotEmpty() && (kreis.startsWith(a) || a.startsWith(kreis)) })

/** Alerts that count on this device: curated ones only in the district [kreis]. */
fun visibleAlerts(alerts: List<Alert>, channels: List<AlertChannel>, kreis: String?): List<Alert> {
    val curated = channels.filter { it.curated }.mapTo(HashSet()) { it.id }
    return alerts.filter { it.channelId !in curated || inRegion(it, kreis) }
}

/** IDs of the alerts that a later one replaces (update or all-clear). */
fun replacedIds(alerts: List<Alert>): Set<String> = alerts.mapNotNullTo(HashSet()) { it.replaces }

/**
 * At the top of “Meldungen”: active alarms and warnings (from [AlertPriority.Warning] on), without replaced ones and
 * without the all-clears themselves. Alarms first, otherwise the newest first.
 */
fun activeAlerts(alerts: List<Alert>, nowMillis: Long): List<Alert> {
    val replaced = replacedIds(alerts)
    return alerts
        .filter { it.priority >= AlertPriority.Warning && it.id !in replaced && it.statusAt(nowMillis) == AlertStatus.Active }
        .sortedWith(compareByDescending<Alert> { it.priority }.thenByDescending { it.timestampMillis })
}

/**
 * May [alert] show “Entwarnen”? Only in own channels (also for alerts that came in via an address for devices),
 * not in subscribed and curated ones, only as long as the alert is active, is not itself an all-clear and
 * no newer one has replaced it ([replaced] from [replacedIds]).
 */
fun canEnd(alert: Alert, channel: AlertChannel?, replaced: Set<String>, nowMillis: Long): Boolean =
    channel != null && channel.owner && !channel.curated && channel.id == alert.channelId &&
        !alert.ended && alert.id !in replaced && alert.statusAt(nowMillis) == AlertStatus.Active

/** One row of the channel list: the channel with its newest alert and the unread important ones. */
data class ChannelRow(val channel: AlertChannel, val last: Alert?, val unread: Int)

/**
 * Channel list like the conversation list: channels with the newest alert first, channels without an alert by name.
 * [seen]: per channel, up to when it was read (ms).
 */
fun channelRows(channels: List<AlertChannel>, alerts: List<Alert>, seen: Map<String, Long>): List<ChannelRow> {
    val byChannel = alerts.groupBy { it.channelId }
    return channels.map { channel ->
        val list = byChannel[channel.id].orEmpty()
        ChannelRow(channel, list.maxByOrNull { it.timestampMillis }, unreadIn(list, seen[channel.id] ?: 0L))
    }.sortedWith(compareByDescending<ChannelRow> { it.last?.timestampMillis ?: Long.MIN_VALUE }.thenBy { it.channel.name.lowercase() })
}

/**
 * Number on the tab (ADR 0062 §9): unread alerts from priority warning on, across all [channels] in the list. An alert
 * of a channel that is no longer there cannot be read away, so it does not count.
 */
fun unreadCount(alerts: List<Alert>, seen: Map<String, Long>, channels: List<AlertChannel>): Int {
    val listed = channels.mapTo(HashSet()) { it.id }
    return alerts.filter { it.channelId in listed }.groupBy { it.channelId }.entries
        .sumOf { (channel, list) -> unreadIn(list, seen[channel] ?: 0L) }
}

private fun unreadIn(alerts: List<Alert>, seenUntil: Long): Int =
    alerts.count { it.priority >= AlertPriority.Warning && !it.ended && it.timestampMillis > seenUntil }

/**
 * How an alert appears (ADR 0062 §5): from the configured priority on loud (even when muted), otherwise with
 * the normal sound, info too. An all-clear never rings loudly. Whether there is a notification at all is told by
 * [ChannelSettings.notify].
 */
fun loudnessOf(alert: Alert, settings: ChannelSettings): AlertLoudness =
    if (!alert.ended && settings.loudFrom != null && alert.priority >= settings.loudFrom) AlertLoudness.Loud else AlertLoudness.Normal

/** Settings of a channel that nobody has changed yet: sound by kind of channel. */
fun defaultSettings(channel: AlertChannel) = ChannelSettings(sound = AlertSound.defaultFor(channel.curated))
