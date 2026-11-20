package app.anynear.alerts

import app.anynear.anmp.AnmpAttachment
import app.anynear.app.PlatformHooks
import app.anynear.i18n.texts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Area “Meldungen” (ADR 0062): channels and alerts from the core ([source]), plus what only applies on this device
 * (sound, from when on loud, district for official warnings, read) and the notifications. Lives, like the
 * [app.anynear.app.AppController], as long as the process.
 */
class Alerts(
    private val scope: CoroutineScope,
    private val source: AlertSource,
    private val settingsStore: AlertSettingsStore,
    private val hooks: PlatformHooks,
    /**
     * Invitation link of the curated channel with official warnings (public, from the app's configuration);
     * `null`: not set up, then the app does not show it (implementation plan §5).
     */
    val curatedLink: String? = null,
    private val now: () -> Long,
    /** Area table for the district of the location; replaceable in tests. */
    private val regionTable: suspend () -> RegionTable = RegionTable::load,
    /** Own name for the sentence when sharing a channel. */
    private val ownName: () -> String = { "" },
    /** Acknowledged alerts of the person ([AlertAcks]); go to all own devices via the person's settings. */
    private val acks: StateFlow<Map<String, Long>> = MutableStateFlow(emptyMap()),
    private val saveAcks: (Map<String, Long>) -> Unit = {},
    /** Seen per channel for all own devices ([AlertsSeen]); read here goes there, from there it comes here. */
    private val syncedSeen: StateFlow<Map<String, Long>> = MutableStateFlow(emptyMap()),
    private val saveSyncedSeen: (Map<String, Long>) -> Unit = {},
    private val log: (String) -> Unit = {},
) {
    /** Acknowledged alerts (“Gesehen”), per ID; see [AlertAcks]. */
    val acked: StateFlow<Map<String, Long>> get() = acks

    /** “Gesehen”: the alert disappears for this person from “Gerade aktiv”, on all their devices. */
    fun ack(alert: Alert) = saveAcks(AlertAcks.prune(AlertAcks.ack(acks.value, alert.id, now()), now()))

    /** “Wieder einblenden”. */
    fun unack(alert: Alert) = saveAcks(AlertAcks.prune(AlertAcks.unack(acks.value, alert.id, now()), now()))

    val channels: StateFlow<List<AlertChannel>> = source.channels

    private val _settings = MutableStateFlow(settingsStore.settings())
    private val _region = MutableStateFlow(settingsStore.region())
    private val _seen = MutableStateFlow(settingsStore.seen())

    /** District for official warnings from the approximate location; `null`: still unknown, then there are none. */
    val region: StateFlow<AlertRegion?> = _region.asStateFlow()

    /** May the app read the approximate location? */
    val locationAllowed: StateFlow<Boolean> = hooks.locationAllowed

    /** State of the search for the district, for the hint below “Amtliche Warnungen”. */
    enum class Locating { Idle, Busy, Failed, Abroad }

    private val _locating = MutableStateFlow(Locating.Idle)
    val locating: StateFlow<Locating> = _locating.asStateFlow()

    private val locateLock = Mutex()
    private var table: RegionTable? = null

    /** Alerts that count on this device (official ones only in the own district), newest first. */
    val alerts: StateFlow<List<Alert>> = combine(source.alerts, source.channels, _region) { list, channels, region ->
        visibleAlerts(list, channels, region?.kreis)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Official warnings everywhere in the country, for the map (ADR 0121 §7): the ones with an area, also outside the own district. */
    val areaAlerts: StateFlow<List<Alert>> = source.alerts.map { list -> list.filter { it.area.isNotEmpty() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** District key → name, for the map; loaded with the table of the regions. */
    suspend fun kreisNames(): Map<String, String> = (table ?: regionTable().also { table = it }).kreisNames

    /** Number on the tab: unread alerts from priority warning on. */
    val unreadCount: StateFlow<Int> = combine(alerts, _seen, source.channels) { list, seen, channels -> unreadCount(list, seen, channels) }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    /** Channel list with last alert and unread ones. */
    val rows: StateFlow<List<ChannelRow>> = combine(source.channels, alerts, _seen) { channels, list, seen ->
        channelRows(channels, list, seen)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Curated channel, if subscribed. */
    val curatedChannel: StateFlow<AlertChannel?> = source.channels.map { channels -> channels.firstOrNull { it.curated } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _pending = MutableStateFlow<PendingChannel?>(null)

    /** Opened channel link, not subscribed yet. */
    val pending: StateFlow<PendingChannel?> = _pending.asStateFlow()

    private val _openRequest = MutableStateFlow<String?>(null)

    /** Channel that the UI should open, e.g. after tapping a notification. */
    val openRequest: StateFlow<String?> = _openRequest.asStateFlow()

    fun requestOpen(channelId: String) {
        _openRequest.value = channelId
    }

    fun consumeOpenRequest() {
        _openRequest.value = null
    }

    private val _notice = MutableStateFlow<String?>(null)

    /** Short notice for the UI (snackbar), e.g. “Kanal abonniert”. */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun clearNotice() {
        _notice.value = null
    }

    private val _accessMissing = MutableStateFlow(false)

    /** Android: the app has no access to “Nicht stören” yet (checked again on returning from the settings). */
    val accessMissing: StateFlow<Boolean> = _accessMissing.asStateFlow()
    private val _hintUntil = MutableStateFlow(settingsStore.hintPostponedUntil())

    /**
     * Hint “Nicht stören freigeben” (Android), like the battery hint: only once there is at least one channel,
     * and not as long as “Später” applies.
     */
    val accessHint: StateFlow<Boolean> = combine(source.channels, _accessMissing, _hintUntil) { channels, missing, until ->
        channels.isNotEmpty() && missing && now() >= until
    }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Invitation to a channel via a link. */
    data class PendingChannel(val link: String, val name: String, val curated: Boolean, val seenByOwner: Boolean = false)

    fun start() {
        settingsStore.forgetChosenStates()
        // Official warnings on and location allowed (also just now): determine the district.
        scope.launch {
            combine(curatedChannel, locationAllowed) { curated, allowed -> curated != null && allowed }
                .distinctUntilChanged()
                .collect { if (it) locate() }
        }
        scope.launch { source.arrivals.collect(::notifySafely) }
        scope.launch { syncedSeen.collect(::applySyncedSeen) }
        // New channels count as read up to now: when subscribing, the 30 days before are not “neu”.
        scope.launch {
            source.channels.collect { channels ->
                val seen = _seen.value
                val fresh = channels.filter { it.id !in seen }
                if (fresh.isNotEmpty()) updateSeen(seen + fresh.associate { it.id to now() })
            }
        }
        checkAccess()
    }

    // --- Settings per channel ---

    fun settingsFor(channel: AlertChannel): ChannelSettings = _settings.value[channel.id] ?: defaultSettings(channel)

    /** For the UI: settings of all channels (defaults are missing). */
    val settings: StateFlow<Map<String, ChannelSettings>> = _settings.asStateFlow()

    fun setSettings(channel: AlertChannel, settings: ChannelSettings) {
        val all = _settings.value + (channel.id to settings)
        _settings.value = all
        settingsStore.saveSettings(all)
    }

    // --- District for official warnings ---

    /** App comes to the foreground: determine the district again if official warnings are on. */
    fun refreshRegion() {
        if (curatedChannel.value != null && locationAllowed.value) scope.launch { locate() }
    }

    /** Button “Standort erlauben” or “Nochmal versuchen”. */
    fun allowLocation() {
        if (locationAllowed.value) scope.launch { locate() } else hooks.requestLocation()
    }

    /** Approximate location → district, all on the device; the location itself is not stored. */
    private suspend fun locate() {
        if (!locateLock.tryLock()) return
        try {
            _locating.value = Locating.Busy
            val point = try {
                hooks.approximateLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Standort: ${e::class.simpleName}")
                null
            }
            if (point == null) {
                _locating.value = Locating.Failed
                return
            }
            val found = (table ?: regionTable().also { table = it }).nearest(point)
            _locating.value = if (found == null) Locating.Abroad else Locating.Idle
            // Outside Germany: no official warnings instead of those of a distant district.
            if (found != _region.value) {
                _region.value = found
                settingsStore.saveRegion(found)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Kreis: ${e::class.simpleName}: ${e.message}")
            _locating.value = Locating.Failed
        } finally {
            locateLock.unlock()
        }
    }

    /** Channel viewed: everything in it counts as read, on the other own devices too. */
    fun markSeen(channelId: String) {
        val at = now()
        updateSeen(_seen.value + (channelId to at))
        saveSyncedSeen(AlertsSeen.merge(syncedSeen.value, mapOf(channelId to at)))
        hooks.cancelAlertNotifications(channelId)
    }

    /** Seen on another own device: adopt it here and clear away its notifications. */
    private fun applySyncedSeen(remote: Map<String, Long>) {
        val local = _seen.value
        val later = remote.filter { (id, at) -> id in local && at > (local[id] ?: 0L) }
        if (later.isEmpty()) return
        updateSeen(local + later)
        later.keys.forEach(hooks::cancelAlertNotifications)
    }

    private fun updateSeen(seen: Map<String, Long>) {
        _seen.value = seen
        settingsStore.saveSeen(seen)
    }

    // --- Channels ---

    fun channel(id: String): AlertChannel? = source.channels.value.firstOrNull { it.id == id }

    fun alertsOf(channelId: String): List<Alert> = source.alerts.value.filter { it.channelId == channelId }

    /** @return the new channel or `null` with a notice. */
    suspend fun createChannel(name: String): AlertChannel? = attempt { source.createChannel(name.trim()) }

    fun channelLink(channelId: String): String? = source.channelLink(channelId)

    /** Share menu with a sentence instead of just the link, as with the invitation to a circle. */
    fun shareChannel(channelId: String) {
        val url = source.channelLink(channelId) ?: return
        val name = ownName().trim()
        val channel = channel(channelId)?.name
        hooks.share(if (name.isEmpty() || channel == null) url else texts.alerts.shareChannel(name, channel, url))
    }

    /** Read a channel link without doing anything; `null` if it is broken. */
    fun parseLink(url: String): ChannelInvite? = source.parseLink(url)

    suspend fun leave(channelId: String) {
        val curated = channel(channelId)?.curated == true
        val left = attempt { source.leave(channelId) } != null
        // Without official warnings no district is needed any more either.
        if (curated && left) {
            _region.value = null
            settingsStore.saveRegion(null)
        }
        _settings.value = (_settings.value - channelId).also(settingsStore::saveSettings)
        updateSeen(_seen.value - channelId)
    }

    suspend fun rename(channelId: String, name: String) {
        attempt { source.rename(channelId, name.trim()) }
    }

    /**
     * Alert from the app itself into an own channel (e.g. “Neue Anfrage” at support, ADR 0065), without a notice in
     * the UI. Only in channels whose owner is this person.
     */
    suspend fun sendQuietly(channelId: String, priority: AlertPriority, title: String, text: String): Boolean {
        if (channel(channelId)?.let { it.owner && !it.curated } != true) return false
        return try {
            source.send(channelId, priority, title.trim(), text.trim(), null)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Meldung aus der App: ${e::class.simpleName}")
            false
        }
    }

    suspend fun send(channelId: String, priority: AlertPriority, title: String?, text: String, url: String?): Boolean =
        deliver(texts.alerts.sent) {
            source.send(channelId, priority, title?.trim()?.ifEmpty { null }, text.trim(), url?.trim()?.ifEmpty { null })
        }

    /**
     * All-clear for an active alert of an own channel ([canEnd]): info with the title of the alert (display and
     * notification put “Entwarnung:” in front), short text, replaces [alert] and ends it. The notification
     * of the alert is replaced in the process ([notificationOf]); an all-clear never gets loud ([loudnessOf]).
     */
    suspend fun endAlert(alert: Alert): Boolean {
        if (channel(alert.channelId)?.let { it.owner && !it.curated } != true) return false
        return deliver(texts.alerts.endAlertSent) {
            source.send(
                alert.channelId, AlertPriority.Info, alert.title?.ifBlank { null }, texts.alerts.endAlertDefaultText, null,
                replaces = alert.id, ended = true,
            )
        }
    }

    private suspend fun deliver(done: String, block: suspend () -> Alert): Boolean {
        val sent = try {
            block()
        } catch (e: IllegalArgumentException) {
            // The core checks length and link; plain text only for troubleshooting, not for the UI.
            log("Meldung ungültig: ${e.message}")
            _notice.value = texts.alerts.composeInvalid
            return false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Meldungen: ${e::class.simpleName}: ${e.message}")
            _notice.value = texts.alerts.failed
            return false
        }
        _notice.value = done
        // The core does not report own alerts as newly arrived; as with ntfy the sending device notifies too,
        // by the same rules and only once (in case it does arrive once more after all).
        notifySafely(sent)
        return sent.id.isNotEmpty()
    }

    suspend fun createIngress(channelId: String, label: String): AlertIngress? = attempt { source.createIngress(channelId, label.trim()) }

    suspend fun revokeIngress(channelId: String, ingressId: String) {
        attempt { source.revokeIngress(channelId, ingressId) }
    }

    /** Remove a subscriber (owner only, ADR 0062 §11); the others notice nothing, the old link is no longer valid. */
    suspend fun removeSubscriber(channelId: String, subscriber: AlertSubscriber) {
        if (attempt { source.removeSubscriber(channelId, subscriber.deviceId) } != null) _notice.value = texts.alerts.subscriberRemoved(subscriber.name)
    }

    /** Reset the link (owner only); afterwards share the new link. */
    suspend fun resetLink(channelId: String) {
        if (attempt { source.resetLink(channelId) } != null) _notice.value = texts.alerts.resetLinkDone
    }

    suspend fun fetchAttachment(alert: Alert, attachment: AnmpAttachment): ByteArray? =
        runCatching { source.fetchAttachment(alert, attachment) }.getOrNull()

    /**
     * Turn on official warnings: subscribe to the curated channel from the configuration and ask for the approximate
     * location (only now, not at app start). `true` if subscribing worked; the list
     * of channels ([curatedChannel]) only catches up shortly afterwards.
     */
    suspend fun enableCurated(): Boolean {
        val link = curatedLink ?: return false
        attempt { source.join(link) } ?: return false
        if (!locationAllowed.value) hooks.requestLocation()
        return true
    }

    // --- Invitation links ---

    /** Is [url] a channel link (even a broken one)? Then it belongs here, not to circles or contacts. */
    fun isChannelLink(url: String): Boolean = source.parseLink(url) != null || CHANNEL_LINK.containsMatchIn(url)

    /** Channel link opened: only after “Abonnieren” does something happen. @return error message or `null`. */
    fun receiveLink(url: String): String? {
        val invite = source.parseLink(url) ?: return texts.alerts.linkBroken
        if (source.channels.value.any { it.id == invite.channelId }) return texts.alerts.alreadyJoined(invite.name)
        _pending.value = PendingChannel(url, invite.name, invite.curated, invite.seenByOwner && !invite.curated)
        return null
    }

    fun dismissPending() {
        _pending.value = null
    }

    fun acceptPending() {
        val pending = _pending.value ?: return
        _pending.value = null
        scope.launch {
            val result = runCatching { source.join(pending.link) }.getOrElse { ChannelJoinResult.Invalid }
            _notice.value = when (result) {
                is ChannelJoinResult.Joined -> texts.alerts.joined(result.channel.name)
                is ChannelJoinResult.AlreadyJoined -> texts.alerts.alreadyJoined(result.channel.name)
                ChannelJoinResult.ForeignNetwork -> texts.alerts.linkForeignNetwork
                ChannelJoinResult.Invalid -> texts.alerts.linkBroken
            }
            // Official warnings turned on via the link: ask for the location as with the button.
            if (result is ChannelJoinResult.Joined && result.channel.curated && !locationAllowed.value) hooks.requestLocation()
        }
    }

    // --- Notifications and test alarm ---

    private fun notifySafely(alert: Alert) {
        try {
            notify(alert)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Meldung: ${e.message}")
        }
    }

    /** Every alert notifies once, unless the channel has notifications off (ADR 0062 §5). */
    private fun notify(alert: Alert) {
        val channel = channel(alert.channelId) ?: return
        if (channel.curated && !inRegion(alert, _region.value?.kreis)) return
        val notification = notificationOf(alert, channel, settingsFor(channel)) ?: return
        if (!settingsStore.firstNotification(notifiedKey(alert))) return
        hooks.showAlertNotification(notification)
    }

    /**
     * Test alarm (ADR 0062 §6): the same loud path as a real alarm of this channel, once on this device, then
     * the list of what is missing. Also with notifications turned off or “Laut: Nie”, otherwise it checks nothing.
     */
    suspend fun testAlarm(channel: AlertChannel): List<AlarmProblem> {
        val settings = settingsFor(channel)
        hooks.showAlertNotification(
            AlertNotification(
                key = TEST_KEY,
                channelId = channel.id,
                channelName = channel.name,
                title = texts.alerts.testTitle,
                text = texts.alerts.testText(channel.name),
                priority = AlertPriority.Alarm,
                loudness = AlertLoudness.Loud,
                sound = AlertSound.allowedFor(channel.curated, settings.sound),
                timestampMillis = now(),
                test = true,
            ),
        )
        return problems()
    }

    /** What currently stands in the way of an alarm on this device. */
    suspend fun problems(): List<AlarmProblem> {
        val platform = hooks.alarmProblems()
        val battery = if (hooks.backgroundRestriction() != null) listOf(AlarmProblem.BatteryOptimized) else emptyList()
        return (platform + battery).distinct()
    }

    /** Button next to a problem: open the matching setting. */
    fun fix(problem: AlarmProblem) {
        if (problem == AlarmProblem.BatteryOptimized) hooks.backgroundRestriction()?.let(hooks::openBackgroundSettings)
        else hooks.openAlarmSettings(problem)
    }

    /** Back from the settings: check the access to “Nicht stören” again. */
    fun checkAccess() {
        _accessMissing.value = hooks.doNotDisturbAccessMissing()
    }

    fun openAccessSettings() = hooks.openAlarmSettings(AlarmProblem.DoNotDisturbAccess)

    fun postponeAccessHint() {
        val until = now() + HINT_PAUSE_MILLIS
        _hintUntil.value = until
        settingsStore.postponeHint(until)
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("Meldungen: ${e::class.simpleName}: ${e.message}")
        _notice.value = texts.alerts.failed
        null
    }

    companion object {
        /** Notification of the test alarm; a new one replaces the old one. */
        const val TEST_KEY = "alert-test"

        private const val HINT_PAUSE_MILLIS = 3 * 24 * 60 * 60 * 1000L

        /** Channel links `https://<linkHost>/a#…` (implementation plan §1), also when copied incompletely. */
        private val CHANNEL_LINK = Regex("""anynear\.(?:app|net)/a/?#""")
    }
}

/** Records that an alert has notified: channel and ID, the same for app and iOS extension. */
fun notifiedKey(alert: Alert) = "${alert.channelId}/${alert.id}"

/**
 * Notification for [alert] according to the channel's settings; `null` if the channel does not notify. An
 * all-clear replaces the notification of the alert.
 */
fun notificationOf(alert: Alert, channel: AlertChannel, settings: ChannelSettings): AlertNotification? {
    if (!settings.notify) return null
    val label = priorityLabel(alert.priority, channel.curated, alert.ended)
    return AlertNotification(
        key = alert.replaces?.takeIf { alert.ended } ?: alert.id,
        channelId = channel.id,
        channelName = channel.name,
        title = alert.title?.ifBlank { null }?.let { "$label: $it" } ?: "$label · ${channel.name}",
        text = alert.text.ifBlank { alert.source ?: channel.name },
        priority = alert.priority,
        loudness = loudnessOf(alert, settings),
        // A sound the channel may not have (e.g. „Amtlich“ on an own channel, chosen before) falls back to its default.
        sound = AlertSound.allowedFor(channel.curated, settings.sound),
        timestampMillis = alert.timestampMillis,
        replacesKey = alert.replaces,
    )
}

/** Word for the priority: “Warnung” only for official sources, otherwise “Wichtig” (ADR 0062 §7). */
fun priorityLabel(priority: AlertPriority, curated: Boolean, ended: Boolean = false): String = when {
    ended -> texts.alerts.allClear
    priority == AlertPriority.Alarm -> texts.alerts.prioAlarm
    priority == AlertPriority.Warning -> if (curated) texts.alerts.prioWarning else texts.alerts.prioImportant
    else -> texts.alerts.prioInfo
}
