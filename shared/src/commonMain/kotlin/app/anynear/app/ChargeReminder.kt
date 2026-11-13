package app.anynear.app

import app.anynear.data.KeyValueStore
import app.anynear.i18n.texts
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Reminds to charge the radio (ADR 0085):
 * - **Battery low:** if the connected radio reports [LOW] % or less, once; again only after [RECOVERED] % or
 *   “on mains power” (values above 100).
 * - **Not seen for a long time:** [AWAY] after the last connection, once per absence, delivered by the system. It is
 *   scheduled on connecting and afterwards at most hourly anew, so that it is also right if the app ends without
 *   disconnecting.
 *
 * The state (last seen, last battery level, already reminded) lives only on this phone.
 */
class ChargeReminder(
    private val store: KeyValueStore,
    private val hooks: PlatformHooks,
    private val now: () -> Long,
) {
    /**
     * New state of the radio. [enabled]: switch on and a radio assigned. [connected]: connected;
     * [battery] its battery level, `null` if it reports none.
     */
    fun update(enabled: Boolean, connected: Boolean, battery: Int?) {
        if (!enabled) {
            hooks.cancelReminder(AWAY_ID)
            return
        }
        val time = now()
        if (connected) {
            if (battery != null) {
                store.putString(LEVEL_KEY, battery.toString())
                checkLow(battery)
            }
            store.putString(SEEN_KEY, time.toString())
            if (time - (long(PLANNED_KEY) ?: 0L) >= REPLAN.inWholeMilliseconds) {
                planAway(time, time)
                store.putString(PLANNED_KEY, time.toString())
            }
        } else {
            // After a restart of the phone a scheduled alarm is gone (Android): reschedule, show what is due once.
            val seen = long(SEEN_KEY) ?: return
            if (long(AWAY_SHOWN_KEY) == seen) return
            planAway(seen, time)
        }
    }

    private fun checkLow(battery: Int) {
        val warned = store.getString(LOW_KEY) == "true"
        when {
            battery > 100 || battery >= RECOVERED -> if (warned) store.putString(LOW_KEY, null)
            battery in 1..LOW && !warned -> {
                store.putString(LOW_KEY, "true")
                hooks.showReminder(Reminder(LOW_ID, texts.settings.chargeLowTitle, texts.settings.chargeLowText(battery)))
            }
        }
    }

    /** Schedule the reminder “not seen for a long time” for [seen] + [AWAY]; already due: show now and remember. */
    private fun planAway(seen: Long, time: Long) {
        val at = seen + AWAY.inWholeMilliseconds
        val level = long(LEVEL_KEY)?.toInt()?.takeIf { it in 1..100 }
        val reminder = Reminder(
            AWAY_ID,
            texts.settings.chargeAwayTitle,
            texts.settings.chargeAwayText(AWAY.inWholeDays.toInt(), level),
            atMillis = at.takeIf { it > time },
        )
        if (reminder.atMillis == null) store.putString(AWAY_SHOWN_KEY, seen.toString())
        hooks.showReminder(reminder)
    }

    private fun long(key: String): Long? = store.getString(key)?.toLongOrNull()

    companion object {
        const val LOW = 20
        const val RECOVERED = 50
        val AWAY = 14.days
        private val REPLAN = 1.hours
        const val LOW_ID = "charge-low"
        const val AWAY_ID = "charge-away"
        private const val SEEN_KEY = "charge.seen"
        private const val LEVEL_KEY = "charge.level"
        private const val LOW_KEY = "charge.lowShown"
        private const val PLANNED_KEY = "charge.planned"
        private const val AWAY_SHOWN_KEY = "charge.awayShownFor"
    }
}
