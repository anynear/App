package app.anynear.push

import app.anynear.AppConfig
import app.anynear.alerts.AlarmProblem
import app.anynear.alerts.AlertLoudness
import app.anynear.alerts.AlertNotification
import app.anynear.core.WakeLevel
import app.anynear.core.wakeLevelOf
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationInterruptionLevel
import platform.UserNotifications.UNNotificationSettingEnabled
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * Alerts as notifications on iOS (ADR 0062 §5, §6). Every alert comes with the normal sound (`active`, mute switch
 * applies); loud ones come as „Time Sensitive“ with their own sound from the
 * app bundle (`alarm.caf`, `amtlich.caf`, `hinweis.caf`, `frage.caf` for the check-in); as „Critical Alert“ only once Apple approves it
 * ([AppConfig.CRITICAL_ALERTS]). Otherwise the mute switch stays effective, the test alarm says so.
 */
internal object AlertNotifications {
    /** Keys in `userInfo`: channel to open (alerts also appear with the app open) and whether it is loud. */
    const val CHANNEL_KEY = "alertChannel"
    const val LOUD_KEY = "alertLoud"

    fun content(notification: AlertNotification): UNMutableNotificationContent {
        val content = UNMutableNotificationContent()
        content.setTitle(notification.title)
        content.setSubtitle(notification.channelName)
        content.setBody(notification.text)
        content.setThreadIdentifier("alerts:${notification.channelId}")
        apply(content, wakeLevelOf(notification.loudness), notification.sound.file.takeIf { notification.loudness == AlertLoudness.Loud })
        content.setUserInfo(mapOf<Any?, Any?>(CHANNEL_KEY to notification.channelId, LOUD_KEY to (notification.loudness == AlertLoudness.Loud)))
        return content
    }

    /** Set level and sound; the extension uses the same rule for wake-ups ([applyWake]). */
    fun apply(content: UNMutableNotificationContent, level: String, sound: String?) {
        content.setInterruptionLevel(
            when (level) {
                WakeLevel.PASSIVE -> UNNotificationInterruptionLevel.UNNotificationInterruptionLevelPassive
                WakeLevel.TIME_SENSITIVE -> UNNotificationInterruptionLevel.UNNotificationInterruptionLevelTimeSensitive
                WakeLevel.CRITICAL -> UNNotificationInterruptionLevel.UNNotificationInterruptionLevelCritical
                else -> UNNotificationInterruptionLevel.UNNotificationInterruptionLevelActive
            },
        )
        content.setSound(
            when {
                level == WakeLevel.PASSIVE -> null
                sound == null -> UNNotificationSound.defaultSound
                level == WakeLevel.CRITICAL -> UNNotificationSound.criticalSoundNamed("$sound.caf", withAudioVolume = 1.0f)
                else -> UNNotificationSound.soundNamed("$sound.caf")
            },
        )
    }

    /** What stands in the way of an alarm on this iPhone. */
    suspend fun problems(center: UNUserNotificationCenter): List<AlarmProblem> = suspendCancellableCoroutine { cont ->
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val list = buildList {
                if (settings?.authorizationStatus != UNAuthorizationStatusAuthorized) {
                    add(AlarmProblem.NotificationsOff)
                } else if (settings.timeSensitiveSetting != UNNotificationSettingEnabled) {
                    add(AlarmProblem.TimeSensitiveOff)
                }
                // Without Apple's approval an alarm stays quiet with the mute switch on; say so honestly.
                if (!AppConfig.CRITICAL_ALERTS || settings?.criticalAlertSetting != UNNotificationSettingEnabled) add(AlarmProblem.SilentSwitch)
            }
            cont.resume(list)
        }
    }
}
