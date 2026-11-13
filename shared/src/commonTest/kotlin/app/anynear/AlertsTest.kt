package app.anynear

import app.anynear.anmp.AnmpAttachment
import app.anynear.alerts.AlarmProblem
import app.anynear.alerts.Alert
import app.anynear.alerts.AlertChannel
import app.anynear.alerts.AlertIngress
import app.anynear.alerts.AlertLoudness
import app.anynear.alerts.AlertNotification
import app.anynear.alerts.AlertPriority
import app.anynear.alerts.AlertSettingsStore
import app.anynear.alerts.AlertSound
import app.anynear.alerts.AlertSource
import app.anynear.alerts.AlertSubscriber
import app.anynear.alerts.AlertStatus
import app.anynear.alerts.AlertRegion
import app.anynear.alerts.Alerts
import app.anynear.alerts.ChannelInvite
import app.anynear.alerts.ChannelJoinResult
import app.anynear.alerts.ChannelSettings
import app.anynear.alerts.activeAlerts
import app.anynear.alerts.canEnd
import app.anynear.alerts.channelRows
import app.anynear.alerts.GeoPoint
import app.anynear.alerts.RegionTable
import app.anynear.alerts.defaultSettings
import app.anynear.alerts.inRegion
import app.anynear.alerts.loudnessOf
import app.anynear.alerts.notificationOf
import app.anynear.alerts.replacedIds
import app.anynear.alerts.statusAt
import app.anynear.alerts.unreadCount
import app.anynear.alerts.visibleAlerts
import app.anynear.app.BackgroundRestriction
import app.anynear.app.NoOpPlatformHooks
import app.anynear.i18n.localeTagOverride
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Alerts and channels (ADR 0062): state, location filter, list, count on the tab, volume, notification. */
class AlertsTest {
    private val clock = 1_800_000_000_000L
    private val hour = 3_600_000L

    private val home = AlertChannel("home", "Haus", owner = true)
    private val family = AlertChannel("family", "Oma", owner = false)
    private val nina = AlertChannel("nina", "NINA", owner = false, curated = true)

    private fun alert(
        id: String,
        channel: AlertChannel = home,
        prio: AlertPriority = AlertPriority.Alarm,
        at: Long = clock - hour,
        until: Long = at + 24 * hour,
        replaces: String? = null,
        ended: Boolean = false,
        area: List<String> = emptyList(),
        endedByOther: Boolean = false,
    ) = Alert(channel.id, id, at, prio, "Titel $id", "Text $id", untilMillis = until, replaces = replaces, ended = ended, area = area, endedByOther = endedByOther)

    @Test
    fun statusIsActiveEndedOrExpired() {
        assertEquals(AlertStatus.Active, alert("a").statusAt(clock))
        assertEquals(AlertStatus.Expired, alert("a", until = clock).statusAt(clock))
        assertEquals(AlertStatus.Ended, alert("a", endedByOther = true).statusAt(clock))
        assertEquals(AlertStatus.Ended, alert("b", replaces = "a", ended = true).statusAt(clock))
        // Without „gültig bis“ active for 24 hours.
        val plain = Alert("home", "x", clock, AlertPriority.Info, null, "Text")
        assertEquals(AlertStatus.Active, plain.statusAt(clock + 23 * hour))
        assertEquals(AlertStatus.Expired, plain.statusAt(clock + 24 * hour))
    }

    @Test
    fun regionMatchesPrefixesBothWays() {
        // Frankfurt am Main: warning for all of Hesse and for the district match, neighboring district and Bavaria do not.
        assertTrue(inRegion(alert("a", nina, area = listOf("06")), "06412"))
        assertTrue(inRegion(alert("a", nina, area = listOf("06412")), "06412"))
        assertTrue(inRegion(alert("a", nina, area = listOf("09", "06412")), "06412"))
        assertFalse(inRegion(alert("a", nina, area = listOf("06433")), "06412"))
        assertFalse(inRegion(alert("a", nina, area = listOf("09162")), "06412"))
        // Without an area everywhere.
        assertTrue(inRegion(alert("a", nina), "06412"))
    }

    @Test
    fun withoutRegionNoOfficialAlertCounts() {
        assertFalse(inRegion(alert("a", nina, area = listOf("06")), null))
        assertFalse(inRegion(alert("a", nina), null))
        val list = listOf(alert("own", home, area = listOf("09")), alert("hesse", nina, area = listOf("06")), alert("everywhere", nina))
        assertEquals(listOf("own"), visibleAlerts(list, listOf(home, nina), null).map { it.id })
    }

    @Test
    fun regionOnlyAppliesToCuratedChannels() {
        val list = listOf(alert("own", home, area = listOf("09")), alert("bavaria", nina, area = listOf("09")), alert("hesse", nina, area = listOf("06")))
        val visible = visibleAlerts(list, listOf(home, nina), "06412")
        assertEquals(listOf("own", "hesse"), visible.map { it.id })
    }

    @Test
    fun activeListShowsCurrentAlarmsAndWarningsFirst() {
        val list = listOf(
            alert("info", prio = AlertPriority.Info),
            alert("warning", prio = AlertPriority.Warning, at = clock - 10),
            alert("old-alarm", at = clock - 2 * hour),
            alert("expired", until = clock - 1),
            alert("smoke", at = clock - 3 * hour),
            alert("clear", replaces = "smoke", ended = true, at = clock - 5),
            alert("updated", at = clock - 4 * hour),
            alert("update", replaces = "updated", at = clock - hour),
        )
        assertEquals(listOf("update", "old-alarm", "warning"), activeAlerts(list, clock).map { it.id })
    }

    @Test
    fun channelListNewestFirstWithUnreadImportantOnes() {
        val list = listOf(
            alert("a", home, at = clock - 3 * hour),
            alert("b", home, prio = AlertPriority.Info, at = clock - hour),
            alert("c", family, prio = AlertPriority.Warning, at = clock - 2 * hour),
        )
        val seen = mapOf("home" to clock - 4 * hour, "family" to clock - hour)
        val rows = channelRows(listOf(family, home, nina), list, seen)
        assertEquals(listOf("home", "family", "nina"), rows.map { it.channel.id })
        assertEquals("b", rows[0].last?.id)
        // Info does not count, read ones do not.
        assertEquals(1, rows[0].unread)
        assertEquals(0, rows[1].unread)
        assertNull(rows[2].last)
    }

    @Test
    fun badgeCountsUnreadFromWarningOnWithoutAllClears() {
        val list = listOf(
            alert("alarm"),
            alert("warning", prio = AlertPriority.Warning),
            alert("info", prio = AlertPriority.Info),
            alert("clear", replaces = "alarm", ended = true),
            alert("seen", family, at = clock - 5 * hour),
        )
        assertEquals(2, unreadCount(list, mapOf("family" to clock - 4 * hour), listOf(home, family)))
        assertEquals(0, unreadCount(list, mapOf("home" to clock, "family" to clock), listOf(home, family)))
        // A channel that is no longer in the list does not count: its alerts cannot be read away.
        assertEquals(3, unreadCount(list, emptyMap(), listOf(home, family)))
        assertEquals(2, unreadCount(list, emptyMap(), listOf(home)))
        assertEquals(0, unreadCount(list, emptyMap(), emptyList()))
    }

    @Test
    fun loudnessFollowsTheChannelSettings() {
        val standard = ChannelSettings()
        assertEquals(AlertLoudness.Loud, loudnessOf(alert("a"), standard))
        assertEquals(AlertLoudness.Normal, loudnessOf(alert("a", prio = AlertPriority.Warning), standard))
        // Info comes with the normal sound, not silently (formerly Quiet: went by unnoticed).
        assertEquals(AlertLoudness.Normal, loudnessOf(alert("a", prio = AlertPriority.Info), standard))
        assertEquals(AlertLoudness.Loud, loudnessOf(alert("a", prio = AlertPriority.Info), standard.copy(loudFrom = AlertPriority.Info)))
        assertEquals(AlertLoudness.Normal, loudnessOf(alert("a", prio = AlertPriority.Warning), standard.copy(loudFrom = null)))
        assertEquals(AlertLoudness.Loud, loudnessOf(alert("a", prio = AlertPriority.Warning), standard.copy(loudFrom = AlertPriority.Warning)))
        assertEquals(AlertLoudness.Normal, loudnessOf(alert("a"), standard.copy(loudFrom = null)))
        // An all-clear never rings loudly.
        assertEquals(AlertLoudness.Normal, loudnessOf(alert("b", replaces = "a", ended = true), standard))
    }

    @Test
    fun officialChannelsHaveTheirOwnAlarmSound() {
        assertEquals(AlertSound.Official, defaultSettings(nina).sound)
        assertEquals(AlertSound.Alarm, defaultSettings(home).sound)
        assertEquals(AlertPriority.Alarm, defaultSettings(home).loudFrom)
    }

    @Test
    fun allClearReplacesTheNotificationOfTheAlarm() {
        val clear = notificationOf(alert("b", replaces = "a", ended = true), home, ChannelSettings())
        assertEquals("a", clear?.key)
        assertEquals("b", notificationOf(alert("b", replaces = "a"), home, ChannelSettings())?.key)
    }

    @Test
    fun everyPriorityNotifiesUnlessTheChannelIsOff() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeAlertSource(listOf(home, family))
        val hooks = RecordingHooks()
        val alerts = Alerts(backgroundScope, source, AlertSettingsStore(InMemoryStore()), hooks, now = { clock })
        alerts.start()
        source.arrivals.emit(alert("i", family, prio = AlertPriority.Info))
        source.arrivals.emit(alert("w", family, prio = AlertPriority.Warning))
        source.arrivals.emit(alert("a", family, prio = AlertPriority.Alarm))
        assertEquals(listOf("i", "w", "a"), hooks.shown.map { it.key })
        assertEquals(listOf(AlertLoudness.Normal, AlertLoudness.Normal, AlertLoudness.Loud), hooks.shown.map { it.loudness })
        // Notifications for the channel off: none, no alarm either; the alerts stay in the history.
        alerts.setSettings(home, alerts.settingsFor(home).copy(notify = false))
        source.arrivals.emit(alert("x", home, prio = AlertPriority.Alarm))
        source.arrivals.emit(alert("y", home, prio = AlertPriority.Info))
        assertEquals(3, hooks.shown.size)
        assertNull(notificationOf(alert("x", home), home, ChannelSettings(notify = false)))
    }

    @Test
    fun loudThresholdsPerChannel() {
        fun loud(loudFrom: AlertPriority?) = AlertPriority.entries.map { prio ->
            notificationOf(alert("a", prio = prio), home, ChannelSettings(loudFrom = loudFrom))!!.loudness == AlertLoudness.Loud
        }
        assertEquals(listOf(false, false, true), loud(AlertPriority.Alarm))
        assertEquals(listOf(false, true, true), loud(AlertPriority.Warning))
        assertEquals(listOf(true, true, true), loud(AlertPriority.Info))
        assertEquals(listOf(false, false, false), loud(null))
        // Default: notify, loud only for an alarm.
        assertEquals(ChannelSettings(notify = true, loudFrom = AlertPriority.Alarm), ChannelSettings())
    }

    @Test
    fun ownAlertNotifiesOnThisDeviceOnce() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeAlertSource(listOf(home))
        val hooks = RecordingHooks()
        val alerts = Alerts(backgroundScope, source, AlertSettingsStore(InMemoryStore()), hooks, now = { clock })
        alerts.start()
        // The core does not report own alerts as arrived (ANMP ChannelFlowTest): formerly nothing came here.
        assertTrue(alerts.send("home", AlertPriority.Alarm, "Probe", "Test-Alarm", null))
        assertEquals(listOf("sent"), hooks.shown.map { it.key })
        assertEquals(AlertLoudness.Loud, hooks.shown.single().loudness)
        // If it does arrive once more (e.g. via another relay), not twice.
        source.arrivals.emit(Alert("home", "sent", 0, AlertPriority.Alarm, "Probe", "Test-Alarm"))
        assertEquals(1, hooks.shown.size)
        // Info from the app: also notified, with the normal sound.
        source.nextId = "info"
        alerts.send("home", AlertPriority.Info, null, "Nur zur Info", null)
        assertEquals(AlertLoudness.Normal, hooks.shown.last().loudness)
        assertEquals(2, hooks.shown.size)
    }

    @Test
    fun allClearOnlyForActiveAlertsOfOwnChannels() {
        val none = emptySet<String>()
        // Own channel, active: yes, also info and alerts via an address for devices (different sender).
        assertTrue(canEnd(alert("a"), home, none, clock))
        assertTrue(canEnd(alert("a", prio = AlertPriority.Info), home, none, clock))
        assertTrue(canEnd(alert("a").copy(sender = "geraet"), home, none, clock))
        // Subscribed, official, unknown channel: no.
        assertFalse(canEnd(alert("a", family), family, none, clock))
        assertFalse(canEnd(alert("a", nina), nina, none, clock))
        assertFalse(canEnd(alert("a"), null, none, clock))
        // Expired, already cleared, itself an all-clear, replaced by a newer one: no.
        assertFalse(canEnd(alert("a", until = clock), home, none, clock))
        assertFalse(canEnd(alert("a", endedByOther = true), home, none, clock))
        assertFalse(canEnd(alert("b", prio = AlertPriority.Info, replaces = "a", ended = true), home, none, clock))
        assertFalse(canEnd(alert("a"), home, setOf("a"), clock))
    }

    @Test
    fun allClearReplacesTheAlertQuietly() = runTest(UnconfinedTestDispatcher()) {
        localeTagOverride = "de-DE"
        try {
            val source = FakeAlertSource(listOf(home, family, nina))
            val hooks = RecordingHooks()
            val alerts = Alerts(backgroundScope, source, AlertSettingsStore(InMemoryStore()), hooks, now = { clock })
            alerts.start()
            val original = alert("probe")
            source.nextId = "ende"
            assertTrue(alerts.endAlert(original))
            // Info, title of the alert, short text, replaces and ends it.
            assertEquals(listOf<Any?>("home", AlertPriority.Info, "Titel probe", "Aufgehoben.", "probe", true), source.sent.single())
            assertEquals("Entwarnung gesendet", alerts.notice.value)
            // The notification of the alert is replaced, quietly, with „Entwarnung:“ in front.
            val shown = hooks.shown.single()
            assertEquals("probe", shown.key)
            assertEquals("probe", shown.replacesKey)
            assertEquals(AlertLoudness.Normal, shown.loudness)
            assertEquals("Entwarnung: Titel probe", shown.title)
            // Subscribed and official channels: nothing is sent.
            assertFalse(alerts.endAlert(alert("x", family)))
            assertFalse(alerts.endAlert(alert("y", nina)))
            assertEquals(1, source.sent.size)
        } finally {
            localeTagOverride = null
        }
    }

    @Test
    fun afterTheAllClearTheAlertIsEndedAndNoLongerActive() {
        // This is how the core delivers both after the all-clear: the alert as cleared, the all-clear itself.
        val original = alert("probe", endedByOther = true)
        val allClear = alert("ende", prio = AlertPriority.Info, at = clock, replaces = "probe", ended = true)
        val list = listOf(allClear, original)
        assertEquals(AlertStatus.Ended, original.statusAt(clock))
        assertEquals(AlertStatus.Ended, allClear.statusAt(clock))
        assertEquals(emptyList(), activeAlerts(list, clock))
        // Even before the core updates the state, it drops out at the top and can no longer be cleared.
        val stale = listOf(allClear, alert("probe"))
        assertEquals(emptyList(), activeAlerts(stale, clock))
        assertFalse(canEnd(alert("probe"), home, replacedIds(stale), clock))
        // The all-clear does not count as unread.
        assertEquals(1, unreadCount(list, emptyMap(), listOf(home)))
    }

    @Test
    fun storedSettingsFromBeforeKeepLoudFromAndNotify() {
        val store = InMemoryStore()
        // This is how the app stored it before „Benachrichtigungen“ (defaults missing, Json without encodeDefaults).
        store.putString("alerts.settings", """{"home":{"loudFrom":"Warning","sound":"Hint"},"family":{"loudFrom":null},"nina":{}}""")
        val settings = AlertSettingsStore(store).settings()
        assertEquals(ChannelSettings(notify = true, loudFrom = AlertPriority.Warning, sound = AlertSound.Hint), settings["home"])
        assertEquals(ChannelSettings(notify = true, loudFrom = null), settings["family"])
        assertEquals(ChannelSettings(), settings["nina"])
    }

    @Test
    fun wakeExtensionOnlyMarksWhatItShowed() {
        val settings = AlertSettingsStore(InMemoryStore())
        assertFalse(settings.notified("home/a"))
        settings.markNotified("home/a")
        assertTrue(settings.notified("home/a"))
        assertFalse(settings.notified("home/b"))
        // Afterwards the app only notifies what the extension has not shown.
        assertFalse(settings.firstNotification("home/a"))
        assertTrue(settings.firstNotification("home/b"))
    }

    @Test
    fun arrivalsNotifyByChannelSettingsAndArea() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeAlertSource(listOf(home, nina))
        val hooks = RecordingHooks()
        val store = InMemoryStore()
        AlertSettingsStore(store).saveRegion(AlertRegion("06411", "Darmstadt"))
        val alerts = Alerts(backgroundScope, source, AlertSettingsStore(store), hooks, now = { clock })
        alerts.start()
        alerts.setSettings(home, ChannelSettings(loudFrom = AlertPriority.Warning, sound = AlertSound.Hint))
        source.arrivals.emit(alert("w", home, prio = AlertPriority.Warning))
        source.arrivals.emit(alert("bavaria", nina, area = listOf("09")))
        source.arrivals.emit(alert("hesse", nina, area = listOf("06411")))
        // If the same alert comes once more (wake-up and fetching), it does not ring twice.
        source.arrivals.emit(alert("w", home, prio = AlertPriority.Warning))
        assertEquals(listOf("w", "hesse"), hooks.shown.map { it.key })
        assertEquals(AlertLoudness.Loud, hooks.shown[0].loudness)
        assertEquals(AlertSound.Hint, hooks.shown[0].sound)
        assertEquals(AlertSound.Official, hooks.shown[1].sound)
        // Settings survive a restart.
        assertEquals(AlertSound.Hint, Alerts(backgroundScope, source, AlertSettingsStore(store), hooks, now = { clock }).settingsFor(home).sound)
    }

    @Test
    fun newChannelsStartRead() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeAlertSource(emptyList())
        var time = clock
        val alerts = Alerts(backgroundScope, source, AlertSettingsStore(InMemoryStore()), RecordingHooks(), now = { time })
        alerts.start()
        source.alertList.value = listOf(alert("old", family, at = clock - hour))
        source.channelList.value = listOf(family)
        assertEquals(0, alerts.unreadCount.value)
        source.alertList.value = source.alertList.value + alert("new", family, at = clock + 1)
        assertEquals(1, alerts.unreadCount.value)
        time = clock + hour
        alerts.markSeen("family")
        assertEquals(0, alerts.unreadCount.value)
    }

    @Test
    fun sharingAChannelSaysWhoInvitesToWhat() = runTest(UnconfinedTestDispatcher()) {
        localeTagOverride = "de-DE"
        try {
            val hooks = RecordingHooks()
            var name = "Testperson Anna"
            val alerts = Alerts(backgroundScope, FakeAlertSource(listOf(home)), AlertSettingsStore(InMemoryStore()), hooks, now = { clock }, ownName = { name })
            alerts.shareChannel("home")
            assertEquals("Testperson Anna lädt dich zum Kanal „Haus“ bei ${AppConfig.APP_NAME} ein: https://anynear.net/a#home", hooks.shared.single())
            // Still without a name: rather only the link than a sentence without a sender.
            name = " "
            alerts.shareChannel("home")
            assertEquals("https://anynear.net/a#home", hooks.shared.last())
            assertEquals("Oma", alerts.parseLink("https://anynear.net/a#gut")?.name)
            assertNull(alerts.parseLink("https://anynear.net/a#kaputt"))
        } finally {
            localeTagOverride = null
        }
    }

    @Test
    fun channelLinkWaitsForConfirmation() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeAlertSource(emptyList())
        val alerts = Alerts(backgroundScope, source, AlertSettingsStore(InMemoryStore()), RecordingHooks(), now = { clock })
        val link = "https://anynear.net/a#gut"
        assertTrue(alerts.isChannelLink(link))
        assertTrue(alerts.isChannelLink("https://anynear.net/a#kaputt"))
        assertFalse(alerts.isChannelLink("https://anynear.net/k#kreis"))
        assertNull(alerts.receiveLink(link))
        assertEquals("Oma", alerts.pending.value?.name)
        assertTrue(source.joined.isEmpty())
        alerts.acceptPending()
        assertEquals(listOf(link), source.joined)
        assertNull(alerts.pending.value)
        // A broken link reports itself instead of opening a dialog.
        assertTrue(alerts.receiveLink("https://anynear.net/a#kaputt") != null)
        assertNull(alerts.pending.value)
    }

    @Test
    fun ownerRemovesASubscriberAndJoinSaysTheOwnerSees() = runTest(UnconfinedTestDispatcher()) {
        localeTagOverride = "de-DE"
        try {
            val source = FakeAlertSource(emptyList())
            val alerts = Alerts(backgroundScope, source, AlertSettingsStore(InMemoryStore()), RecordingHooks(), now = { clock })
            // A link with a sign-up route: the dialog says that the owner sees you (ADR 0062 §11).
            assertNull(alerts.receiveLink("https://anynear.net/a#gesehen"))
            assertTrue(alerts.pending.value!!.seenByOwner)
            alerts.removeSubscriber("home", AlertSubscriber("dev", "Ben", 0))
            assertEquals(listOf("home" to "dev"), source.removed)
            assertEquals("Ben ist nicht mehr dabei.", alerts.notice.value)
        } finally {
            localeTagOverride = null
        }
    }

    @Test
    fun testAlarmRunsTheLoudPathAndListsWhatIsMissing() = runTest(UnconfinedTestDispatcher()) {
        val hooks = RecordingHooks(problems = listOf(AlarmProblem.DoNotDisturbAccess), restriction = BackgroundRestriction.BATTERY_OPTIMIZED)
        val alerts = Alerts(backgroundScope, FakeAlertSource(listOf(nina)), AlertSettingsStore(InMemoryStore()), hooks, now = { clock })
        // Even with notifications switched off and „Laut: Nie“ the test alarm checks the loud route.
        alerts.setSettings(nina, alerts.settingsFor(nina).copy(notify = false, loudFrom = null))
        val problems = alerts.testAlarm(nina)
        assertEquals(listOf(AlarmProblem.DoNotDisturbAccess, AlarmProblem.BatteryOptimized), problems)
        val shown = hooks.shown.single()
        assertTrue(shown.test)
        assertEquals(AlertLoudness.Loud, shown.loudness)
        assertEquals(AlertSound.Official, shown.sound)
    }

    @Test
    fun accessHintOnlyWithChannels() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeAlertSource(emptyList())
        val hooks = RecordingHooks(accessMissing = true)
        val alerts = Alerts(backgroundScope, source, AlertSettingsStore(InMemoryStore()), hooks, now = { clock })
        alerts.start()
        assertFalse(alerts.accessHint.value)
        source.channelList.value = listOf(home)
        assertTrue(alerts.accessHint.value)
        alerts.postponeAccessHint()
        assertFalse(alerts.accessHint.value)
    }

    @Test
    fun coreAlertsBecomeAppAlertsInMilliseconds() {
        fun item(prio: Int, status: app.anynear.anmp.core.AlertStatus, ended: Boolean = false) = app.anynear.anmp.core.AlertItem(
            channelId = "home", id = "0102030405060708", ts = 1_800_000_000, prio = prio, title = null, text = "Rauch", url = null,
            until = 1_800_086_400, replaces = null, ended = ended, source = null, area = emptyList(), attachments = emptyList(),
            sender = "ab", status = status, replacedBy = null, received = 0,
        )
        val alarm = app.anynear.alerts.CoreAlertSource.alertOf(item(3, app.anynear.anmp.core.AlertStatus.ACTIVE))
        assertEquals(1_800_000_000_000L, alarm.timestampMillis)
        assertEquals(1_800_086_400_000L, alarm.untilMillis)
        assertEquals(AlertPriority.Alarm, alarm.priority)
        assertEquals(AlertPriority.Info, app.anynear.alerts.CoreAlertSource.alertOf(item(1, app.anynear.anmp.core.AlertStatus.ACTIVE)).priority)
        // Replaced by an all-clear: cleared; the all-clear itself stays „ended“ without „ersetzt“.
        assertTrue(app.anynear.alerts.CoreAlertSource.alertOf(item(3, app.anynear.anmp.core.AlertStatus.ENDED)).endedByOther)
        assertFalse(app.anynear.alerts.CoreAlertSource.alertOf(item(3, app.anynear.anmp.core.AlertStatus.ENDED, ended = true)).endedByOther)
    }

    @Test
    fun withoutRegionOfficialAlertsDoNotNotify() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeAlertSource(listOf(home, nina))
        val hooks = RecordingHooks()
        val alerts = Alerts(backgroundScope, source, AlertSettingsStore(InMemoryStore()), hooks, now = { clock })
        alerts.start()
        source.arrivals.emit(alert("hesse", nina, area = listOf("06")))
        source.arrivals.emit(alert("everywhere", nina))
        source.arrivals.emit(alert("own", home))
        assertEquals(listOf("own"), hooks.shown.map { it.key })
    }

    @Test
    fun regionComesFromTheApproximateLocationOnlyWhenAllowed() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeAlertSource(listOf(nina))
        val hooks = RecordingHooks()
        val store = InMemoryStore()
        // Federal states formerly chosen by hand no longer apply.
        store.putString("alerts.area", """{"areas":["09"]}""")
        val alerts = Alerts(backgroundScope, source, AlertSettingsStore(store), hooks, now = { clock }, regionTable = { table })
        alerts.start()
        assertNull(store.getString("alerts.area"))
        assertNull(alerts.region.value)
        assertEquals(0, hooks.locationReads)
        // Allowed: district from the location, only the district is stored.
        hooks.location = GeoPoint(50.1106, 8.6821)
        hooks.allowed.value = true
        assertEquals(AlertRegion("06412", "Frankfurt am Main"), alerts.region.value)
        assertEquals(AlertRegion("06412", "Frankfurt am Main"), AlertSettingsStore(store).region())
        assertFalse(store.getString("alerts.region").orEmpty().contains("50.1"))
        // Back into the foreground at another place.
        hooks.location = GeoPoint(50.1080, 8.7250)
        alerts.refreshRegion()
        assertEquals("06413", alerts.region.value?.kreis)
        // Abroad no official warnings instead of those of a distant district.
        hooks.location = GeoPoint(40.4168, -3.7038)
        alerts.refreshRegion()
        assertNull(alerts.region.value)
        assertEquals(Alerts.Locating.Abroad, alerts.locating.value)
        // Location currently not available: the known district stays.
        hooks.location = GeoPoint(50.1106, 8.6821)
        alerts.refreshRegion()
        hooks.location = null
        alerts.refreshRegion()
        assertEquals("06412", alerts.region.value?.kreis)
        assertEquals(Alerts.Locating.Failed, alerts.locating.value)
        // Official warnings unsubscribed: the district is forgotten.
        alerts.leave("nina")
        assertNull(alerts.region.value)
        assertNull(AlertSettingsStore(store).region())
    }

    @Test
    fun turningOnOfficialWarningsAsksForTheLocation() = runTest(UnconfinedTestDispatcher()) {
        val hooks = RecordingHooks()
        val alerts = Alerts(backgroundScope, FakeAlertSource(emptyList()), AlertSettingsStore(InMemoryStore()), hooks, curatedLink = "https://anynear.net/a#nina", now = { clock })
        alerts.start()
        assertEquals(0, hooks.locationRequests)
        alerts.enableCurated()
        assertEquals(1, hooks.locationRequests)
    }

    // Excerpt of the real table (files/regions): Frankfurt, Offenbach am Main, a municipality in the Offenbach district.
    private val table = RegionTable.parse(
        "06412;Frankfurt am Main\n06413;Offenbach am Main\n06438;Landkreis Offenbach\n",
        "06412;50110880;8682433\n06413;50105567;8764442\n06438;50054636;8695300\n",
    )

    private class RecordingHooks(
        private val problems: List<AlarmProblem> = emptyList(),
        private val restriction: BackgroundRestriction? = null,
        private val accessMissing: Boolean = false,
    ) : NoOpPlatformHooks() {
        val shown = mutableListOf<AlertNotification>()
        val shared = mutableListOf<String>()
        override fun share(text: String) {
            shared += text
        }
        val allowed = MutableStateFlow(false)
        var location: GeoPoint? = null
        var locationReads = 0
        var locationRequests = 0
        override val locationAllowed = allowed
        override fun requestLocation() {
            locationRequests++
        }
        override suspend fun approximateLocation(): GeoPoint? {
            locationReads++
            return location
        }
        override fun showAlertNotification(notification: AlertNotification) {
            shown += notification
        }
        override suspend fun alarmProblems(): List<AlarmProblem> = problems
        override fun backgroundRestriction(): BackgroundRestriction? = restriction
        override fun doNotDisturbAccessMissing(): Boolean = accessMissing
    }

    private class FakeAlertSource(channels: List<AlertChannel>) : AlertSource {
        val channelList = MutableStateFlow(channels)
        val alertList = MutableStateFlow<List<Alert>>(emptyList())
        val joined = mutableListOf<String>()
        var nextId = "sent"

        /** Sent: channel, priority, title, text, replaces, ended. */
        val sent = mutableListOf<List<Any?>>()
        override val channels = channelList
        override val alerts = alertList
        override val arrivals = MutableSharedFlow<Alert>()
        override suspend fun createChannel(name: String) = AlertChannel(name, name, owner = true).also { channelList.value += it }
        override suspend fun join(link: String): ChannelJoinResult {
            joined += link
            return ChannelJoinResult.Joined(AlertChannel("family", "Oma", owner = false))
        }
        override fun parseLink(link: String) = when {
            link.endsWith("#gut") -> ChannelInvite("family", "Oma", curated = false, networkId = "1")
            link.endsWith("#gesehen") -> ChannelInvite("family", "Oma", curated = false, networkId = "1", seenByOwner = true)
            else -> null
        }
        override fun channelLink(channelId: String): String? = "https://anynear.net/a#$channelId"
        override suspend fun leave(channelId: String) {
            channelList.value = channelList.value.filterNot { it.id == channelId }
        }
        override suspend fun rename(channelId: String, name: String) = Unit
        override suspend fun send(
            channelId: String, priority: AlertPriority, title: String?, text: String, url: String?,
            untilMillis: Long?, replaces: String?, ended: Boolean,
        ): Alert {
            sent += listOf(channelId, priority, title, text, replaces, ended)
            return Alert(channelId, nextId, 0, priority, title, text, replaces = replaces, ended = ended)
        }
        override suspend fun createIngress(channelId: String, label: String) = AlertIngress("i", label, "https://alerts.anynear.app/token", 0)
        override suspend fun revokeIngress(channelId: String, ingressId: String) = Unit
        override suspend fun resetLink(channelId: String) = Unit
        val removed = mutableListOf<Pair<String, String>>()
        override suspend fun removeSubscriber(channelId: String, deviceId: String) {
            removed += channelId to deviceId
        }
        override suspend fun fetchAttachment(alert: Alert, attachment: AnmpAttachment): ByteArray? = null
    }
}
