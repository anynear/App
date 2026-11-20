package app.anynear

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.window.ComposeUIViewController
import app.anynear.alerts.AlarmProblem
import app.anynear.alerts.AlertNotification
import app.anynear.alerts.GeoPoint
import app.anynear.app.MessageNotification
import app.anynear.app.Reminder
import app.anynear.push.AlertNotifications
import app.anynear.push.REPLY_ACTION
import app.anynear.push.REPLY_CATEGORY
import app.anynear.push.WAKE_THREAD
import app.anynear.push.WakeBadge
import app.anynear.app.AppController
import app.anynear.app.AppUpdateState
import app.anynear.app.NoOpPlatformHooks
import app.anynear.app.createAppController
import app.anynear.i18n.texts
import app.anynear.i18n.Language
import app.anynear.i18n.currentLanguage
import app.anynear.core.PushToken
import app.anynear.ui.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.NSURL
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UIKit.UIDevice
import platform.UIKit.UIViewController
import platform.UIKit.registerForRemoteNotifications
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionCriticalAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSettingEnabled
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTextInputNotificationAction
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS: sharing via the system menu and notifications. When the app is closed, the
 * mailbox of anynear wakes the iPhone via Apple's push service, the Notification Service Extension then builds the
 * text (ADR 0009). If the app is still running (e.g. with a radio in the background), it builds it itself.
 */
private class IosPlatformHooks : NoOpPlatformHooks() {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override val pushToken = MutableStateFlow<PushToken?>(null)

    /** Full identifier as on Android (`0.1.0-beta.<Hash>`), otherwise the version number for Apple. */
    override val appVersion: String? = (NSBundle.mainBundle.objectForInfoDictionaryKey("AppVersionName") as? String)?.takeIf { it.isNotBlank() }
        ?: NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String

    /** Debug configuration from Xcode: the Kotlin framework is then a debug build as well. */
    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    override val testBuild: Boolean = kotlin.native.Platform.isDebugBinary

    /** `CURRENT_PROJECT_VERSION` like `versionCode` on Android (from the number of commits, see release.yml). */
    override val appBuild: Long? = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toLongOrNull()

    // New version from the App Store, without the signed list (ADR 0096 §7); TestFlight reports itself.
    private val appStore = AppStoreUpdates(debug = testBuild)

    override val appUpdate: StateFlow<AppUpdateState> get() = appStore.state

    override fun checkAppUpdate(force: Boolean) = appStore.check(force)

    override fun installAppUpdate() {
        if (!appStore.openStore()) openUpdatePage()
    }

    // Required update (ADR 0096 §6): iOS cannot ask for the build number, the button opens its place.
    override val opensUpdatePage: Boolean = true

    override fun openUpdatePage() {
        if (!appStore.testFlight && appStore.openStore()) return
        val target = when {
            appStore.testFlight -> AppConfig.TESTFLIGHT_URL
            currentLanguage() == Language.GERMAN -> AppConfig.APP_PAGE_URL_DE
            else -> AppConfig.APP_PAGE_URL_EN
        }
        val url = NSURL.URLWithString(target) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    override fun phoneBatteryLevel(): Int? {
        val device = UIDevice.currentDevice
        device.batteryMonitoringEnabled = true
        // -1 if unknown (simulator).
        return device.batteryLevel.takeIf { it >= 0f }?.let { (it * 100).toInt() }
    }

    override val notificationPermissionNeeded = MutableStateFlow(false)

    // Approximate location for official warnings; asks only when switching on, not on start.
    private val location = IosApproximateLocation(openSettings = ::openNotificationSettings)

    override val locationAllowed: StateFlow<Boolean> = location.allowed

    override fun requestLocation() = location.request()

    override suspend fun approximateLocation(): GeoPoint? = location.read()

    override val preciseLocationAllowed: StateFlow<Boolean> = location.precise

    override fun requestPreciseLocation() = location.requestPrecise()

    override suspend fun currentPlace(): app.anynear.location.Place? = location.place()

    override val backgroundLocationAllowed: StateFlow<Boolean> = location.always

    override fun requestBackgroundLocation() = location.requestAlways()

    private val locationReasons = mutableSetOf<String>()

    override fun setLocationInUse(reason: String, text: String?) {
        if (text == null) locationReasons.remove(reason) else locationReasons.add(reason)
        location.share(locationReasons.isNotEmpty())
    }

    override val placeUpdates: kotlinx.coroutines.flow.Flow<app.anynear.location.Place> = location.updates

    override fun batteryPercent(): Int? {
        val device = platform.UIKit.UIDevice.currentDevice
        device.batteryMonitoringEnabled = true
        return device.batteryLevel.takeIf { it >= 0f }?.let { (it * 100).toInt() }
    }

    override fun dial(number: String) {
        NSURL.URLWithString("tel:$number")?.let { url -> platform.UIKit.UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null) }
    }

    override fun openMap(latitude: Double, longitude: Double, label: String) {
        // Apple Maps; the name of the person only labels the pin.
        val name = label.encodeToByteArray().joinToString("") { b ->
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() && b >= 0) c.toString() else "%" + (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
        }
        NSURL.URLWithString("https://maps.apple.com/?ll=$latitude,$longitude&q=$name")?.let { url ->
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
        }
    }

    override fun locationServicesOn(): Boolean = location.servicesOn()

    // iOS opens no page of the location services itself; the app's page leads there.
    override fun openLocationSettings() = openNotificationSettings()

    init {
        // Locked only „anynear · Neue Nachricht“ (with the iOS default „Vorschauen: wenn entsperrt“).
        val message = UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY,
            actions = emptyList<Any>(),
            intentIdentifiers = emptyList<Any>(),
            hiddenPreviewsBodyPlaceholder = texts.system.newMessage,
            options = 0u,
        )
        // Circle chats and direct messages: „Antworten“ without opening the app, also on the Apple Watch.
        val reply = UNNotificationCategory.categoryWithIdentifier(
            identifier = REPLY_CATEGORY,
            actions = listOf(
                UNTextInputNotificationAction.actionWithIdentifier(
                    identifier = REPLY_ACTION,
                    title = texts.system.reply,
                    options = 0u,
                    textInputButtonTitle = texts.chat.send,
                    textInputPlaceholder = texts.system.replyHint,
                ),
            ),
            intentIdentifiers = emptyList<Any>(),
            hiddenPreviewsBodyPlaceholder = texts.system.newMessage,
            options = 0u,
        )
        center.setNotificationCategories(setOf(message, reply))
        // Already allowed (earlier start): fetch the token anew, it can change.
        center.getNotificationSettingsWithCompletionHandler { settings ->
            if (settings?.authorizationStatus == UNAuthorizationStatusAuthorized) {
                registerForPush()
                // Allowed before critical alerts existed: ask once more for them alone. iOS asks only once per option.
                if (AppConfig.CRITICAL_ALERTS && settings.criticalAlertSetting != UNNotificationSettingEnabled) {
                    center.requestAuthorizationWithOptions(
                        UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionCriticalAlert,
                    ) { _, _ -> }
                }
            }
            notificationPermissionNeeded.value = settings?.authorizationStatus == UNAuthorizationStatusNotDetermined
        }
    }

    override fun share(text: String) {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        var top: UIViewController = root
        while (true) top = top.presentedViewController ?: break
        top.presentViewController(UIActivityViewController(listOf(text), null), animated = true, completion = null)
    }

    // The app's page in the iPhone settings; „Mitteilungen“ and „Standort“ are there.
    override fun openNotificationSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    override fun requestNotificationPermission() {
        // „Time Sensitive“ needs no question of its own; critical alerts only with the entitlement.
        val critical = if (AppConfig.CRITICAL_ALERTS) UNAuthorizationOptionCriticalAlert else 0uL
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound or critical) { granted, _ ->
            notificationPermissionNeeded.value = false
            if (granted) registerForPush()
        }
    }

    private fun registerForPush() = dispatch_async(dispatch_get_main_queue()) {
        UIApplication.sharedApplication.registerForRemoteNotifications()
    }

    override fun showMessageNotification(notification: MessageNotification) {
        val content = UNMutableNotificationContent()
        content.setTitle(notification.title)
        content.setBody(
            (
                notification.lines.map { if (notification.isCircle) "${it.sender}: ${it.text}" else it.text } +
                    listOfNotNull(notification.replyError?.let(texts.system::replyNotSent))
                ).joinToString("\n"),
        )
        content.setThreadIdentifier(notification.key)
        content.setCategoryIdentifier(if (notification.canReply) REPLY_CATEGORY else CATEGORY)
        // Again after a failed reply: without a second sound.
        if (notification.replyError == null) content.setSound(UNNotificationSound.defaultSound)
        content.setUserInfo(mapOf<Any?, Any?>(CONVERSATION_KEY to notification.key))
        // One notification per conversation: replace those of the extension for the same conversation.
        replaceThread(notification.key) {
            center.addNotificationRequestWithIdentifier(notification.key, content)
        }
    }

    override fun cancelMessageNotification(key: String) = replaceThread(key) {}

    // Alerts (ADR 0062): one notification per alert, an all-clear replaces that of the cancelled one.
    override fun showAlertNotification(notification: AlertNotification) {
        val ids = listOfNotNull(notification.replacesKey, notification.key)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
        center.addNotificationRequestWithIdentifier(notification.key, AlertNotifications.content(notification))
    }

    override suspend fun alarmProblems(): List<AlarmProblem> = AlertNotifications.problems(center)

    override fun cancelAlertNotifications(channelId: String) {
        center.getDeliveredNotificationsWithCompletionHandler { delivered ->
            val ids = delivered.orEmpty().filterIsInstance<UNNotification>().map { it.request.identifier }.filter { it.startsWith("$channelId/") }
            center.removeDeliveredNotificationsWithIdentifiers(ids)
        }
    }

    // Counter on the app icon; the extension keeps counting in the background (WakeBadge).
    override fun setUnreadBadge(count: Int) {
        WakeBadge.set(count)
        center.setBadgeCount(count.toLong(), withCompletionHandler = null)
    }

    // Reminders (ADR 0085): local notification, scheduled with a time trigger; iOS delivers it even when terminated.
    override fun showReminder(reminder: Reminder) {
        val content = UNMutableNotificationContent()
        content.setTitle(reminder.title)
        content.setBody(reminder.text)
        content.setThreadIdentifier("reminders")
        content.setSound(UNNotificationSound.defaultSound)
        val nowMillis = (NSDate().timeIntervalSince1970 * 1000).toLong()
        val seconds = reminder.atMillis?.let { (it - nowMillis) / 1000.0 }?.takeIf { it >= 1.0 }
        val trigger = seconds?.let { UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(it, repeats = false) }
        center.removePendingNotificationRequestsWithIdentifiers(listOf(reminder.id))
        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(reminder.id, content, trigger),
            withCompletionHandler = null,
        )
    }

    override fun cancelReminder(id: String) = center.removePendingNotificationRequestsWithIdentifiers(listOf(id))

    private fun replaceThread(key: String, then: () -> Unit) {
        center.getDeliveredNotificationsWithCompletionHandler { delivered ->
            val ids = delivered.orEmpty().filterIsInstance<UNNotification>()
                // With the conversation also the fixed notices „Neue Nachricht“: the app has fetched itself meanwhile.
                .filter { it.request.content.threadIdentifier == key || it.request.content.threadIdentifier == WAKE_THREAD }
                .map { it.request.identifier }
            center.removeDeliveredNotificationsWithIdentifiers(ids + key)
            then()
        }
    }

    private fun UNUserNotificationCenter.addNotificationRequestWithIdentifier(id: String, content: UNMutableNotificationContent) =
        addNotificationRequest(UNNotificationRequest.requestWithIdentifier(id, content, null), withCompletionHandler = null)

    companion object {
        const val CATEGORY = "message"
        const val CONVERSATION_KEY = "conversation"
    }
}

private val hooks by lazy { IosPlatformHooks() }

// Crash reports (ADR 0082): before the controller so that a crash on start gets in as well.
private val crashes by lazy {
    app.anynear.app.CrashRecorder(
        file = app.anynear.data.platformTextFile(app.anynear.app.CrashRecorder.FILE_NAME),
        platform = app.anynear.app.usagePlatform,
        build = { hooks.appVersion },
        os = "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}",
        model = UIDevice.currentDevice.model,
        dataDirs = listOf(app.anynear.data.platformDataDir()),
        now = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    )
}

/**
 * Record uncaught exceptions in Kotlin code (ADR 0082). From `iOSApp.init()`, before everything else. Afterwards
 * Kotlin/Native terminates the process as before.
 */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
@Suppress("unused")
fun installCrashReporting() {
    val records = crashes
    kotlin.native.setUnhandledExceptionHook { error -> records.record(error) }
}

/**
 * Crash from MetricKit (`MXCrashDiagnostic`), delivered on the next start. [fromMillis] to [toMillis] is the
 * period of the report; [abort]: signal SIGABRT. Every uncaught Kotlin exception that the hook has already
 * recorded ends that way too, therefore not once more then.
 */
@Suppress("unused")
fun recordNativeCrash(error: String, trace: String, fromMillis: Long, toMillis: Long, abort: Boolean) {
    if (abort && crashes.hasCrashBetween(fromMillis, toMillis)) return
    crashes.record(app.anynear.app.CrashReport.Kind.Native, error, trace, at = toMillis)
}

// One controller at a time, as on Android in the Application. A restart (after the emergency card code, the handover
// from another own device, a reset) replaces it within the process: iOS apps must not quit themselves.
// If iOS starts the app in the background (push, sharing), nothing counts as read until `scenePhase` reports active.
private fun newController(restart: Boolean = false): AppController =
    createAppController(hooks, crashes = crashes).also {
        val active = UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive
        it.setFocused(active)
        it.start()
        // On a restart `scenePhase` does not change again, so tell the new controller here that the app is open.
        if (restart && active) it.setInForeground(true)
    }

private val current: MutableStateFlow<AppController> by lazy {
    app.anynear.data.restartHandler = { restart() }
    MutableStateFlow(newController())
}

private val controller: AppController get() = current.value

// Only touched on the main thread.
private var restarting = false

/**
 * Stops the running controller, then builds a new one from the stored state; the UI follows [current]. On the main
 * thread, whichever thread asks for it (a dialog, or the controller's own work, e.g. after „Alle Daten löschen“).
 */
private fun restart() {
    MainScope().launch {
        if (restarting) return@launch
        restarting = true
        val old = current.value
        try {
            old.shutdown()
            current.value = newController(restart = true)
        } finally {
            restarting = false
        }
    }
}

/**
 * Register background work, on start in `didFinishLaunching` (ADR 0081): the silent look for new documents.
 * The controller is created only when iOS really starts the task.
 */
@Suppress("unused")
fun registerBackgroundWork() {
    var job: kotlinx.coroutines.Job? = null
    app.anynear.app.IosFinderSchedule.register(
        check = { done ->
            val finder = controller.docFinder
            if (finder == null || !finder.watchOn.value) {
                if (finder != null) app.anynear.app.IosFinderSchedule.set(false)
                done()
            } else {
                job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    try {
                        finder.checkNew()
                    } finally {
                        done()
                    }
                }
            }
        },
        cancel = { job?.cancel() },
    )
}

/** Notification „neue Dokumente gefunden“ tapped: open the suggestions. */
@Suppress("unused")
fun onFinderOpened() = controller.openFindDocs()

/** Entry point for the Swift shell in iosApp/. */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController {
    // After a restart everything starts anew with the new controller, as after opening the app.
    val c by current.collectAsState()
    key(c) { App(c) }
}

/** Universal link https://anynear.net/k#… or /c#… (from SwiftUI `onOpenURL`). */
@Suppress("unused")
fun handleIncomingUrl(url: String) = controller.openLink(url)

/** App visible or in the background (SwiftUI `scenePhase`). In the background the app builds notices itself. */
@Suppress("unused")
fun setAppVisible(visible: Boolean) {
    controller.setInForeground(visible)
    controller.setFocused(visible)
    if (visible) {
        receiveShared()
    }
}

/**
 * Shared from another app: the Share Extension opens `anynear://share`. It is also picked up on the
 * next opening, in case iOS did not let the app start from the extension.
 */
@Suppress("unused")
fun receiveShared() {
    app.anynear.ui.takeSharedFromExtension()?.let(controller::receiveShared)
}

/**
 * Token from Apple's push service (`didRegisterForRemoteNotificationsWithDeviceToken`), as hex.
 * [sandbox]: development build, Apple delivers via the sandbox address.
 */
@Suppress("unused")
fun onPushToken(token: String, sandbox: Boolean) {
    val topic = NSBundle.mainBundle.bundleIdentifier ?: return
    controller // Start the controller for sure so that the registration with the mailbox runs.
    hooks.pushToken.value = PushToken(token.lowercase(), topic, sandbox)
}

/** Notification tapped: `userInfo["conversation"]` opens exactly this conversation. */
@Suppress("unused")
fun onNotificationOpened(conversation: String?) {
    conversation?.let(controller::openConversation)
}

/**
 * „Antworten“ in a message notification (also from the Apple Watch): iOS starts the app in the background if needed, the
 * reply goes the same way as from the chat. [completion] (the handler of `didReceive`) comes once it left the phone or
 * after at most 20 seconds, so that iOS keeps the app running that long.
 */
@Suppress("unused")
fun onNotificationReply(conversation: String?, text: String, completion: () -> Unit) {
    if (conversation == null) {
        completion()
        return
    }
    // Create the controller here on the main thread, as on every other start.
    val app = controller
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
        try {
            app.replyFromNotification(conversation, text)
        } finally {
            dispatch_async(dispatch_get_main_queue()) { completion() }
        }
    }
}

/** Notification of an alert tapped: `userInfo["alertChannel"]` opens the channel. */
@Suppress("unused")
fun onAlertOpened(channel: String?) {
    channel?.let { controller.alerts?.requestOpen(it) }
}

/** Swift side for passwords (keychain, Face ID, settings), see [app.anynear.passwords.IosPasswordKeychain]. */
@Suppress("unused")
fun setPasswordKeychain(keychain: app.anynear.passwords.IosPasswordKeychain) {
    app.anynear.passwords.iosPasswordKeychain = keychain
}

/**
 * Command from the menu bar (Mac) or via keyboard shortcut (iPad), name as in [app.anynear.app.AppCommand],
 * e.g. `NewMessage`. Unknown names do nothing.
 */
@Suppress("unused")
fun menuCommand(name: String) {
    app.anynear.app.AppCommand.entries.firstOrNull { it.name == name }?.let(controller::command)
}

/** Texts of the menu bar from the app's catalogues (ADR 0026) so that Swift needs none of its own. */
@Suppress("unused")
fun menuText(key: String): String = when (key) {
    "NewMessage" -> texts.chat.newMessage
    "Settings" -> texts.system.menuSettings
    "Help" -> texts.system.menuHelp
    "GoTo" -> texts.system.menuGoTo
    "ShowChats" -> texts.setup.tabChats
    "ShowCircles" -> texts.setup.tabCircles
    "ShowFiles" -> texts.setup.tabFiles
    "ShowAlerts" -> texts.alerts.title
    "Back" -> texts.common.back
    else -> key
}
