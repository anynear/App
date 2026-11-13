package app.anynear.app

import app.anynear.alerts.AlarmProblem
import app.anynear.alerts.AlertNotification
import app.anynear.alerts.GeoPoint
import app.anynear.core.PushToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Platform-specific things that the shared logic has to trigger. */
interface PlatformHooks {
    /** Bluetooth runtime permissions (Android). On iOS CoreBluetooth asks by itself on first access. */
    val bluetoothPermissionGranted: StateFlow<Boolean>

    fun requestBluetoothPermission()

    /**
     * Does the phone have internet right now (Wi-Fi or mobile data)? Distinguishes “kein Internet” from
     * “anynear-Dienst nicht erreichbar”. Platforms without detection report `true`.
     */
    val internetAvailable: StateFlow<Boolean>

    /** Android: start/stop the foreground service so that the BLE connection persists in the background. */
    fun setBackgroundConnection(active: Boolean)

    /** Opens the system's share menu (WhatsApp, Signal, SMS …) with [text]. */
    fun share(text: String)

    /**
     * Android: receive messages in the background too. For this the app itself stays connected to the
     * mailboxes (foreground service); no push service from Google or others (ADR 0008).
     */
    fun setMessageReception(active: Boolean) = Unit

    /** Ask for the notification permission (Android 13+). Without it the app still receives. */
    fun requestNotificationPermission() = Unit

    /** Open the system's notification setting for this app (on/off, sounds). */
    fun openNotificationSettings() = Unit

    /** Open the system setting of this app (permissions), e.g. when the system prompt no longer appears. */
    fun openAppSettings() = Unit

    /** Can the app open the system's Bluetooth settings (to remove a broken pairing)? */
    val canOpenBluetoothSettings: Boolean get() = false

    /** Open the system's Bluetooth settings. */
    fun openBluetoothSettings() = Unit

    /** Can the app still ask for notifications (not allowed and not decided yet)? */
    val notificationPermissionNeeded: StateFlow<Boolean> get() = NoPermissionNeeded

    /** Show or replace the notification for a conversation; one per conversation ([MessageNotification.key]). */
    fun showMessageNotification(notification: MessageNotification) = Unit

    /** Remove the notification of a conversation, e.g. because it is being viewed right now. */
    fun cancelMessageNotification(key: String) = Unit

    /**
     * Notification for an alert (ADR 0062 §5, §6): silent, with normal sound or as an alarm (Android: alarm clock
     * sound, through “Nicht stören”, full screen; iOS: “Time Sensitive” with its own sound).
     */
    fun showAlertNotification(notification: AlertNotification) = Unit

    /** Clear away notifications of a channel: the channel has been seen, here or on another own device. */
    fun cancelAlertNotifications(channelId: String) = Unit

    /** Counter on the app icon: unread messages and alerts together, 0 removes it. */
    fun setUnreadBadge(count: Int) = Unit

    /**
     * Show a reminder (ADR 0085): immediately if [Reminder.atMillis] is `null` or already past, otherwise by the system
     * at that point in time, even if the app is not running then. A new reminder with the same [Reminder.id] replaces
     * the old one.
     */
    fun showReminder(reminder: Reminder) = Unit

    /** Delete the scheduled reminder [id]; one already shown stays. */
    fun cancelReminder(id: String) = Unit

    /**
     * Check-ins (ADR 0083 §5.3): wake the app at [atMillis], also in deep sleep, so that question, hint and alarm come
     * on time; `null` clears the alarm. Android: exact alarm if allowed, otherwise inexact.
     */
    fun scheduleExactWake(atMillis: Long?) = Unit

    /** May the app set exact alarms? Without permission deadlines can come a few minutes late. */
    fun exactAlarmsAllowed(): Boolean = true

    /** Open the setting „Alarms & reminders“ (Android 12+). */
    fun openExactAlarmSettings() = Unit

    /**
     * Ongoing notification while the own check-in runs (ADR 0083 §5.2), also on the lock screen, with „Alles gut“ and
     * „Bin fertig“, which work without unlocking; `null` removes it.
     */
    fun showCheckIn(checkIn: CheckInNotification?) = Unit

    /**
     * May the app read the approximate location (official warnings for the own district)? Only in the foreground,
     * Android `ACCESS_COARSE_LOCATION`, iOS “Beim Verwenden der App”.
     */
    val locationAllowed: StateFlow<Boolean> get() = NoLocation

    /** Ask for the approximate location; if the system no longer asks, open the app's settings. */
    fun requestLocation() = Unit

    /** Approximate location, once and only in the foreground; `null` if not allowed or not obtainable right now. */
    suspend fun approximateLocation(): GeoPoint? = null

    /** What stands in the way of an alarm on this device (without battery, [backgroundRestriction] checks that). */
    suspend fun alarmProblems(): List<AlarmProblem> = emptyList()

    /** Android: does the app have no access to “Nicht stören”? Without it alarms do not get through there. */
    fun doNotDisturbAccessMissing(): Boolean = false

    /** Opens the system setting in which [problem] can be fixed. */
    fun openAlarmSettings(problem: AlarmProblem) = openNotificationSettings()

    /**
     * iOS: token for Apple's push service as soon as iOS delivers it (after notifications are allowed). The app
     * registers it with the mailboxes of anynear, which then wake the iPhone (ADR 0009). Android: always `null`.
     */
    val pushToken: StateFlow<PushToken?> get() = NoPushToken

    /** What hinders reception in the background (Android: battery optimisation, manufacturer locks), `null` if nothing. */
    fun backgroundRestriction(): BackgroundRestriction? = null

    /** Opens the system setting in which [restriction] can be lifted. */
    fun openBackgroundSettings(restriction: BackgroundRestriction) = Unit

    /** Installed app version for display, e.g. `0.1.0-beta.cdfbd83`. */
    val appVersion: String? get() = null

    /**
     * Test mode: debug build (emulator, development) or, on Android, Firebase Test Lab. Then no usage report goes out so that tests do not
     * distort the numbers (ADR 0045).
     */
    val testBuild: Boolean get() = false

    /** Build number of the app (Android `versionCode`, iOS `CURRENT_PROJECT_VERSION`); `null` if unknown. */
    val appBuild: Long? get() = null

    /** Battery level of the phone in percent; `null` if unknown. Before updating the radio (ADR 0056). */
    fun phoneBatteryLevel(): Int? = null

    /** State of the update of the app itself ([AppUpdateState]). */
    val appUpdate: StateFlow<AppUpdateState> get() = NoAppUpdate

    /** Check anynear.app for a new version; without [force] at most every 12 hours. */
    fun checkAppUpdate(force: Boolean) = Unit

    /** Download, verify and install the new version (Android asks by itself in the process). */
    fun installAppUpdate() = Unit

    /**
     * Build number of the newest version this installation can install itself (Docs ADR 0096 §6): Android `direct`
     * from the own updater, `play` from Google Play; `null` if none is known.
     */
    val installableBuild: StateFlow<Long?> get() = NoBuild

    /** Update under the block (ADR 0096 §5): Google Play's immediate flow; otherwise like [installAppUpdate]. */
    fun installRequiredUpdate() = installAppUpdate()

    /**
     * The app cannot ask for a new version but can open its place ([openUpdatePage]): iOS (TestFlight or the page
     * „App laden“, ADR 0096 §6).
     */
    val opensUpdatePage: Boolean get() = false

    /** Opens the place of the new version: iOS TestFlight if installed from there, otherwise the website. */
    fun openUpdatePage() = Unit

    /** Is the app under the former app ID ([app.anynear.AppConfig.OLD_APP_ID]) still installed? Android only. */
    fun oldAppInstalled(): Boolean = false

    /** Opens the system's uninstall dialog exactly for the former app. */
    fun uninstallOldApp() = Unit

    /** Opens the system's uninstall dialog for this app (before reinstalling, ADR 0037). Android only. */
    fun uninstallThisApp() = Unit

}

private val NoAppUpdate: StateFlow<AppUpdateState> = MutableStateFlow(AppUpdateState.Unsupported)

private val NoBuild: StateFlow<Long?> = MutableStateFlow(null)

private val NoPushToken:StateFlow<PushToken?> = MutableStateFlow(null)

private val NoPermissionNeeded: StateFlow<Boolean> = MutableStateFlow(false)

private val NoLocation: StateFlow<Boolean> = MutableStateFlow(false)

/** Ongoing notification of the own check-in; [okAction], [endAction] go to `LifeSigns.onAction`. */
data class CheckInNotification(
    val planId: String,
    val title: String,
    val text: String,
    val okAction: String,
    val okLabel: String,
    val endAction: String,
    val endLabel: String,
)

/** Reminder as a notification (ADR 0085), scheduled or immediate. Tapping opens the app. */
data class Reminder(val id: String, val title: String, val text: String, val atMillis: Long? = null)

/** Reasons why Android would stop the app in the background. */
enum class BackgroundRestriction {
    /** Battery optimisation is on for anynear: Android puts the app to sleep. */
    BATTERY_OPTIMIZED,

    /** Background usage is explicitly restricted for anynear (battery “Eingeschränkt”). */
    BACKGROUND_RESTRICTED,

    /**
     * Battery optimisation is off, yet anynear was terminated in the background: a power-saving feature of the
     * manufacturer's own (e.g. Samsung “Schlafende Apps”, Xiaomi “Autostart”) that Android does not report.
     */
    STOPPED_IN_BACKGROUND,
}

/**
 * Content of a notification, built on the phone from the decrypted messages. Does not leave
 * the device.
 */
data class MessageNotification(
    /** Conversation, see [app.anynear.chats.key]. */
    val key: String,
    /** Circle name or name of the person. */
    val title: String,
    /** Circle: show sender per line. */
    val isCircle: Boolean,
    /** Unread messages, oldest first, at most a few. */
    val lines: List<Line>,
    val unreadCount: Int,
    /** Uploaded image of the circle or the person, `null` = none on the phone. */
    val image: ByteArray? = null,
) {
    /** [senderKey] tells apart senders with the same name; [senderImage] is their uploaded image. */
    data class Line(
        val sender: String,
        val text: String,
        val timestampMillis: Long,
        val senderKey: String? = null,
        val senderImage: ByteArray? = null,
    )
}

/** For tests: no permission prompt, no foreground service, no sharing. */
open class NoOpPlatformHooks : PlatformHooks {
    override val bluetoothPermissionGranted: StateFlow<Boolean> = MutableStateFlow(true)

    override fun requestBluetoothPermission() = Unit

    override val internetAvailable: StateFlow<Boolean> = MutableStateFlow(true)

    override fun setBackgroundConnection(active: Boolean) = Unit

    override fun share(text: String) = Unit
}
