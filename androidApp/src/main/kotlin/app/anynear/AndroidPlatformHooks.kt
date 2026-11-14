package app.anynear

import android.Manifest
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import app.anynear.alerts.AlarmProblem
import app.anynear.alerts.AlertNotification
import app.anynear.alerts.GeoPoint
import app.anynear.app.AppUpdateState
import app.anynear.app.BackgroundRestriction
import app.anynear.app.MessageNotification
import app.anynear.app.CheckInNotification
import app.anynear.app.Reminder
import app.anynear.app.PlatformHooks
import app.anynear.i18n.texts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidPlatformHooks(private val context: Context) : PlatformHooks {
    private val _granted = MutableStateFlow(hasBluetoothPermissions())
    override val bluetoothPermissionGranted: StateFlow<Boolean> = _granted.asStateFlow()

    /** Set by the visible Activity that can show the permission dialog. */
    var permissionRequester: (() -> Unit)? = null

    /** Like [permissionRequester], only for notifications (Android 13+). */
    var notificationPermissionRequester: (() -> Unit)? = null

    /** Like [permissionRequester], only for the approximate location (official warnings). */
    var locationPermissionRequester: (() -> Unit)? = null

    private val location = ApproximateLocation(context)
    private val _locationAllowed = MutableStateFlow(location.allowed())
    override val locationAllowed: StateFlow<Boolean> = _locationAllowed.asStateFlow()

    private val notifier = MessageNotifier(context)

    private val alertNotifier = AlertNotifier(context)
    private val reminderNotifier = ReminderNotifier(context)

    private val stops = BackgroundStops(context)

    /** Updating the APK outside Google Play from the GitHub releases (off in the Play build). */
    val updater = AppUpdater(context)

    /** Entries in the log for troubleshooting (Einstellungen > Erweitert), area „Empfang“; set by [MainApplication]. */
    @Volatile var log: (String) -> Unit = {}

    override val appVersion: String? get() = updater.version

    // Also in Firebase Test Lab (Google Play pre-launch reports): a fresh install per release (ADR 0045 §1b).
    override val testBuild: Boolean get() = BuildConfig.DEBUG ||
        runCatching { Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true" }.getOrDefault(false)

    override val appBuild: Long? get() = updater.build

    override fun phoneBatteryLevel(): Int? =
        (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }

    /** Google Play In-App Updates in the flavor `play`, `null` in `direct` (ADR 0096 §6, §7). */
    val store: StoreUpdates? = storeUpdates(context)

    override val appUpdate: StateFlow<AppUpdateState> get() = store?.state ?: updater.state

    override fun checkAppUpdate(force: Boolean) = store?.check(force) ?: updater.check(force)

    override fun installAppUpdate() = store?.install(immediate = false) ?: updater.install()

    override val installableBuild: StateFlow<Long?> get() = store?.availableBuild ?: updater.availableBuild

    override fun installRequiredUpdate() = store?.install(immediate = true) ?: updater.install()

    // Former app ID (ADR 0025); visible thanks to <queries> in the manifest.
    override fun oldAppInstalled(): Boolean =
        context.packageName != AppConfig.OLD_APP_ID &&
            runCatching { context.packageManager.getPackageInfo(AppConfig.OLD_APP_ID, 0) }.isSuccess

    override fun uninstallOldApp() {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${AppConfig.OLD_APP_ID}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    override fun uninstallThisApp() {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    // The foreground service runs as long as one of the two reasons exists.
    @Volatile private var radioActive = false
    @Volatile private var receptionActive = false

    override fun requestBluetoothPermission() {
        permissionRequester?.invoke()
    }

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val _internet = MutableStateFlow(hasInternet(connectivity.getNetworkCapabilities(connectivity.activeNetwork)))
    override val internetAvailable: StateFlow<Boolean> = _internet.asStateFlow()

    init {
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _internet.value = hasInternet(caps)
                }

                override fun onLost(network: Network) {
                    _internet.value = false
                }
            },
        )
    }

    private val _notificationNeeded = MutableStateFlow(notificationPermissionMissing())
    override val notificationPermissionNeeded: StateFlow<Boolean> = _notificationNeeded.asStateFlow()

    fun refreshPermissions() {
        _granted.value = hasBluetoothPermissions()
        _notificationNeeded.value = notificationPermissionMissing()
        _locationAllowed.value = location.allowed()
    }

    override fun requestLocation() {
        if (location.allowed()) refreshPermissions() else locationPermissionRequester?.invoke()
    }

    override suspend fun approximateLocation(): GeoPoint? = location.read()

    private fun notificationPermissionMissing(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    override fun setBackgroundConnection(active: Boolean) {
        radioActive = active
        updateService()
    }

    override fun setMessageReception(active: Boolean) {
        receptionActive = active
        if (active) WakeAlarm.schedule(context) else WakeAlarm.cancel(context)
        updateService()
    }

    override fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notificationPermissionRequester?.invoke()
    }

    override fun showMessageNotification(notification: MessageNotification) = notifier.show(notification)

    override fun cancelMessageNotification(key: String) = notifier.cancel(key)

    override fun showAlertNotification(notification: AlertNotification) = alertNotifier.show(notification)

    // The launcher builds the counter on the icon from the notifications; they disappear on reading.
    override fun cancelAlertNotifications(channelId: String) = alertNotifier.cancelChannel(channelId)

    override fun showReminder(reminder: Reminder) = reminderNotifier.show(reminder)

    override fun cancelReminder(id: String) = reminderNotifier.cancel(id)

    override fun scheduleExactWake(atMillis: Long?) = LifeSignWake.schedule(context, atMillis)

    private val checkInNotifier by lazy { CheckInNotifier(context) }

    override fun showCheckIn(checkIn: CheckInNotification?) = checkInNotifier.show(checkIn)

    override fun exactAlarmsAllowed(): Boolean = LifeSignWake.exactAllowed(context)

    override fun openExactAlarmSettings() {
        if (!LifeSignWake.openSettings(context)) openAppSettings()
    }

    override suspend fun alarmProblems(): List<AlarmProblem> = alertNotifier.problems()

    override fun doNotDisturbAccessMissing(): Boolean = alertNotifier.doNotDisturbAccessMissing()

    override fun openAlarmSettings(problem: AlarmProblem) {
        if (!alertNotifier.openSettings(problem)) openNotificationSettings()
    }

    override fun backgroundRestriction(): BackgroundRestriction? {
        val activity = context.getSystemService(ActivityManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && activity.isBackgroundRestricted) {
            return BackgroundRestriction.BACKGROUND_RESTRICTED
        }
        val power = context.getSystemService(PowerManager::class.java)
        if (!power.isIgnoringBatteryOptimizations(context.packageName)) return BackgroundRestriction.BATTERY_OPTIMIZED
        return if (stops.stoppedSinceHandled()) BackgroundRestriction.STOPPED_IN_BACKGROUND else null
    }

    override fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Einstellung nicht vorhanden: ${intent.action}")
        }
    }

    override val canOpenBluetoothSettings: Boolean = true

    override fun openBluetoothSettings() {
        try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Einstellung nicht vorhanden: ${Settings.ACTION_BLUETOOTH_SETTINGS}")
        }
    }

    override fun openNotificationSettings() {
        val candidates = listOf(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Einstellung nicht vorhanden: ${intent.action}")
            }
        }
    }

    override fun openBackgroundSettings(restriction: BackgroundRestriction) {
        val app = Uri.fromParts("package", context.packageName, null)
        val candidates = when (restriction) {
            // System dialog „anynear im Hintergrund laufen lassen?“ with „Zulassen“, as with other messengers.
            BackgroundRestriction.BATTERY_OPTIMIZED -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, app),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app),
            )
            // „Eingeschränkt“ can only be changed in the app info under „Akku“.
            BackgroundRestriction.BACKGROUND_RESTRICTED -> listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app))
            // Manufacturer blocks (battery, autostart) can be reached everywhere via the app info; the manufacturers'
            // own pages change with every version. Previous stops count as dealt with afterwards.
            BackgroundRestriction.STOPPED_IN_BACKGROUND -> {
                stops.markHandled()
                listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app))
            }
        }
        // Some manufacturers do not know individual pages; then take the next one.
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Einstellung nicht vorhanden: ${intent.action}")
            }
        }
    }

    private val main = Handler(Looper.getMainLooper())

    /**
     * The app becomes visible: if the service is not running although it should, start it now. In the foreground
     * Android always allows that. Catches the case that a start in the background failed (e.g. after an update) or the
     * system ended it; otherwise receiving stayed off until the next restart of the process.
     */
    fun ensureService() = main.post {
        if (MeshConnectionService.running != null || !(radioActive || receptionActive)) return@post
        log("Dienst lief nicht, Start beim Öffnen")
        updateService()
    }

    private fun updateService() = main.post {
        val radio = radioActive
        val wanted = radio || receptionActive
        // If it is already running, only adjust it: restarting is not allowed from the background.
        MeshConnectionService.running?.let { service ->
            if (wanted) {
                service.refresh(radio)
            } else {
                log("Dienst wird beendet (nicht mehr nötig)")
                service.stopSelf()
            }
            return@post
        }
        if (!wanted) {
            context.stopService(Intent(context, MeshConnectionService::class.java))
            return@post
        }
        val intent = Intent(context, MeshConnectionService::class.java)
            .putExtra(MeshConnectionService.EXTRA_RADIO, radio)
        try {
            ContextCompat.startForegroundService(context, intent)
            log("Dienst wird gestartet" + if (radio) " (mit Funkgerät)" else "")
        } catch (e: RuntimeException) {
            // e.g. ForegroundServiceStartNotAllowedException, when the app is just starting in the background.
            // Receiving and connection then only run as long as the app is visible.
            Log.w(TAG, "Vordergrunddienst konnte nicht gestartet werden", e)
            log("Dienst nicht gestartet: ${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        }
    }

    /** Create the notification channels or rename them in the current language; on every start. */
    fun refreshNotificationChannels() {
        notifier.ensureChannel()
        alertNotifier.ensureChannels()
        updater.ensureChannel()
        MeshConnectionService.ensureChannel(context)
    }

    override fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, texts.system.shareVia).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun hasInternet(caps: NetworkCapabilities?): Boolean =
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

    private fun hasBluetoothPermissions(): Boolean =
        requiredPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    companion object {
        private const val TAG = "App"

        /** Needed for the connection. The app asks for notifications separately, with its own explanation. */
        fun requiredPermissions(): List<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        fun requestedPermissions(): Array<String> = requiredPermissions().toTypedArray()
    }
}
