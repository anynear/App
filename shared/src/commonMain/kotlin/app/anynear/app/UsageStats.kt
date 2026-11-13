package app.anynear.app

import app.anynear.AppConfig
import app.anynear.anmp.AnmpStats
import app.anynear.anmp.core.AnmpCore
import app.anynear.circles.ProfileRepository
import app.anynear.data.KeyValueStore
import app.anynear.data.PairedDeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** `android` or `ios` for the usage report. */
expect val usagePlatform: String

/** What was sent last, for „Was zuletzt geschickt wurde“ in the settings. */
@Serializable
data class UsageReport(
    val day: String,
    val devices: Int,
    val radios: Int,
    val circles: Int,
    val channels: Int,
    val nina: Boolean,
    val platform: String,
    val version: String,
)

/**
 * Usage statistics (ADR 0045): once a day one report of this device, as long as the person has not switched it off.
 * Counting, signing and sending is done by the core; the app supplies what only it knows (radios, platform, version).
 * Sending happens after a random waiting time of at most one minute, as long as the app is running; a missed day
 * is not made up for. In test mode ([testMode], debug builds) nothing goes out at all, not even a toggle report.
 *
 * The daily report starts only on the second UTC day the stats run on this device (ADR 0045 §1b): review and test
 * installs (Google's pre-launch tests, Apple's beta review, emulators) start fresh with every release and would
 * each count as a new person.
 */
class UsageStats(
    private val scope: CoroutineScope,
    private val core: AnmpCore,
    private val store: KeyValueStore,
    private val profile: ProfileRepository,
    private val pairedDevices: PairedDeviceRepository,
    private val version: () -> String?,
    private val now: () -> Long,
    private val randomDelayMillis: () -> Long = { Random.nextLong(MAX_DELAY_MILLIS) },
    /** Test mode: send nothing so that tests do not distort the numbers. */
    val testMode: Boolean = false,
    private val log: (String) -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var pending: Job? = null

    private val _last = MutableStateFlow(store.getString(LAST_KEY)?.let { runCatching { json.decodeFromString(UsageReport.serializer(), it) }.getOrNull() })

    /** Report sent last; `null` as long as nothing has been sent. */
    val last: StateFlow<UsageReport?> = _last.asStateFlow()

    /** Checks at start and hourly after that whether a report is still due today. */
    fun start() {
        if (testMode) log("Testmodus: keine Nutzungsmeldung")
        scope.launch {
            while (true) {
                checkIfDue()
                delay(CHECK_MILLIS)
            }
        }
    }

    /** On returning to the app. */
    fun onForeground() {
        scope.launch { checkIfDue() }
    }

    private fun today(): String = AnmpStats.dayOf(now() / 1000)

    /**
     * The person has toggled on this device: report once that it was switched off or switched on again
     * (ADR 0045). If that fails, it stays noted and goes with the next check. Do not call if the
     * setting comes from another own device.
     */
    fun onSwitched(on: Boolean) {
        store.putString(SWITCH_KEY, on.toString())
        scope.launch {
            sendSwitch()
            if (on) checkIfDue()
        }
    }

    private suspend fun sendSwitch() {
        if (testMode) return
        val on = store.getString(SWITCH_KEY)?.toBooleanStrictOrNull() ?: return
        if (core.sendStatsSwitch(usagePlatform, on)) {
            // Only delete if it has not been toggled once more in the meantime.
            if (store.getString(SWITCH_KEY) == on.toString()) store.putString(SWITCH_KEY, null)
            log(if (on) "Statistik wieder eingeschaltet gemeldet" else "Statistik abgeschaltet gemeldet")
        }
    }

    internal suspend fun checkIfDue() {
        if (testMode) return
        sendSwitch()
        mutex.withLock {
            if (!profile.usageStats.value || _last.value?.day == today() || pending?.isActive == true) return
            if (isFirstDay(store, _last.value, today())) return
            pending = scope.launch {
                delay(randomDelayMillis())
                sendNow()
            }
        }
    }

    internal suspend fun sendNow() {
        if (testMode) return
        val day = today()
        if (!profile.usageStats.value || _last.value?.day == day) return
        if (isFirstDay(store, _last.value, day)) return
        val radios = pairedDevices.known.value.size
        val curated = AppConfig.OFFICIAL_ALERTS_LINK?.let { mapOf(it to NINA) }.orEmpty()
        val v = version()
        if (!core.sendStats(usagePlatform, v.orEmpty(), radios, curated)) {
            log("Nutzungsmeldung nicht angekommen, später noch einmal")
            return
        }
        val channels = core.channels.value
        val report = UsageReport(
            day = day,
            devices = core.ownDevices.value.size.coerceIn(1, AnmpStats.MAX_DEVICES),
            radios = radios.coerceIn(0, AnmpStats.MAX_RADIOS),
            circles = core.circles.value.count { !it.removed }.coerceIn(0, AnmpStats.MAX_CIRCLES),
            channels = channels.count { !it.curated }.coerceIn(0, AnmpStats.MAX_CHANNELS),
            nina = channels.any { it.curated },
            platform = usagePlatform,
            version = AnmpStats.versionOf(v),
        )
        store.putString(LAST_KEY, json.encodeToString(UsageReport.serializer(), report))
        _last.value = report
        log("Nutzungsmeldung geschickt")
    }

    companion object {
        /** Identifier of the curated channel NINA in the report. */
        const val NINA = "nina"
        private const val LAST_KEY = "usage_stats_last"
        private const val SWITCH_KEY = "usage_stats_switch_pending"
        private const val FIRST_DAY_KEY = "usage_stats_first_day"

        /**
         * `true` on the first UTC day the stats run on this device (ADR 0045 §1b): then no daily report goes out. The
         * first day is noted on the first call; a device that already reported before this rule counts from the day
         * of its [last] report, so it misses no day.
         */
        internal fun isFirstDay(store: KeyValueStore, last: UsageReport?, today: String): Boolean {
            val first = store.getString(FIRST_DAY_KEY) ?: (last?.day ?: today).also { store.putString(FIRST_DAY_KEY, it) }
            return today <= first
        }
        private const val CHECK_MILLIS = 60 * 60 * 1000L
        /** Short so that the report also arrives if the app is open only briefly (iOS suspends it in the background). */
        private const val MAX_DELAY_MILLIS = 60 * 1000L
    }
}
