package app.anynear

import android.Manifest
import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat
import android.hardware.usb.UsbManager
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import app.anynear.ui.App
import app.anynear.ui.sharedContentOf
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as MainApplication

    /** When the app last asked for Bluetooth; for detecting that the system prompt did not appear at all. */
    private var bluetoothAskedAt = 0L

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            app.hooks.refreshPermissions()
            // If „Nicht mehr fragen“ was chosen (from Android 11 on after declining twice), Android shows no prompt
            // any more and answers immediately. Then the button leads to the app's settings, otherwise nothing happens.
            val denied = result.filterValues { !it }.keys
            val noDialog = SystemClock.elapsedRealtime() - bluetoothAskedAt < NO_DIALOG_MILLIS
            if (denied.isNotEmpty() && noDialog && denied.none(::shouldShowRequestPermissionRationale)) {
                app.hooks.openAppSettings()
            }
        // The phone's language may have changed: update the names of the notification channels.
        app.hooks.refreshNotificationChannels()
        }

    // Without permission messages still arrive, just without a notification. No asking again.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { app.hooks.refreshPermissions() }

    /** When the app last asked for the location; as with Bluetooth. */
    private var locationAskedAt = 0L

    // Approximate location for official warnings. If no prompt comes any more („Nicht mehr fragen“), go to settings.
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            app.hooks.refreshPermissions()
            val noDialog = SystemClock.elapsedRealtime() - locationAskedAt < NO_DIALOG_MILLIS
            if (!granted && noDialog && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                app.hooks.openAppSettings()
            }
        }

    // Dialogs of Google Play for an update (flavor `play`, ADR 0096).
    private val storeUpdateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            app.hooks.store?.onFlowResult(result.resultCode)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Header area light, dark in dark mode: status bar icons follow the system mode.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        app.hooks.permissionRequester = {
            bluetoothAskedAt = SystemClock.elapsedRealtime()
            permissionLauncher.launch(AndroidPlatformHooks.requestedPermissions())
        }
        app.hooks.notificationPermissionRequester = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        app.hooks.locationPermissionRequester = {
            locationAskedAt = SystemClock.elapsedRealtime()
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        app.hooks.store?.launcher = storeUpdateLauncher
        setContent { App(app.controller) }
        handleLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    /**
     * Invitation or contact link https://anynear.net/k#… or /c#… (App Link). The part after # stays on the phone.
     * Or: notification tapped, then open the conversation or „Dokumente finden“. Or: shared from another app, then the
     * app asks whom it is for. Or: radio plugged in via USB and anynear chosen, then open the rescue.
     */
    private fun handleLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            val shared = Intent(intent)
            intent.action = null
            // Via a shortcut in the share menu (ShareShortcuts) the conversation is already determined.
            val target = shared.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)
            lifecycleScope.launch { sharedContentOf(shared)?.let { app.controller.receiveShared(it, target) } }
            return
        }
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            // anynear chosen in the chooser „App für USB-Gerät“: Android has thereby already granted access.
            // First the choice: set up a new radio or rescue one.
            intent.action = null
            app.controller.openUsbPlugged()
            return
        }
        if (intent?.action == AlertNotifier.ACTION_OPEN_ALERTS) {
            intent.getStringExtra(AlertNotifier.EXTRA_CHANNEL)?.let { app.controller.alerts?.requestOpen(it) }
            intent.action = null
            return
        }
        if (intent?.action == DocFinderService.ACTION_OPEN_FINDER) {
            app.controller.openFindDocs()
            intent.action = null
            return
        }
        if (intent?.action == MessageNotifier.ACTION_OPEN_CONVERSATION) {
            intent.getStringExtra(MessageNotifier.EXTRA_CONVERSATION)?.let(app.controller::openConversation)
            intent.action = null
            return
        }
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.dataString?.let(app.controller::openLink)
        // Do not process again on every rotation of the screen.
        intent.action = null
    }

    override fun onStart() {
        super.onStart()
        app.controller.setInForeground(true)
        // Every time the app becomes visible: if the receiving service was not running, start it now.
        app.hooks.ensureService()
    }

    override fun onStop() {
        app.controller.setInForeground(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // From Android 10 on several Activities can run at once; only the topmost counts (onTopResumedActivityChanged).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) app.controller.setFocused(true)
        // The user may have changed permissions in the system settings.
        app.hooks.refreshPermissions()
        // The system dialog „Zulassen“ only pauses the Activity, onStart does not come afterwards.
        app.controller.checkBackgroundRestriction()
        // Back from the setting „Nicht stören“: the notice disappears, alarm channels now bypass it.
        app.controller.alerts?.checkAccess()
        app.hooks.refreshNotificationChannels()
        // Back from „Unbekannte Apps installieren“: continue the update.
        app.hooks.updater.onResume()
        // Google Play: install a downloaded update, continue an immediate one.
        app.hooks.store?.onResume()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        app.controller.setFocused(isTopResumedActivity)
    }

    override fun onPause() {
        app.controller.setFocused(false)
        super.onPause()
    }

    override fun onDestroy() {
        app.hooks.permissionRequester = null
        app.hooks.notificationPermissionRequester = null
        app.hooks.locationPermissionRequester = null
        app.hooks.store?.let { if (it.launcher === storeUpdateLauncher) it.launcher = null }
        super.onDestroy()
    }

    private companion object {
        /** If Android answers faster, it did not show a prompt. */
        const val NO_DIALOG_MILLIS = 400L
    }
}
