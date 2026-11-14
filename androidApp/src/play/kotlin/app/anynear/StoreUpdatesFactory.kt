package app.anynear

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import app.anynear.app.AppUpdateState
import app.anynear.i18n.texts
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Flavor `play`: Google Play In-App Updates (ADR 0096 §6, §7). */
fun storeUpdates(context: Context): StoreUpdates? = PlayUpdates(context)

/**
 * Asks Google Play for a newer version (on start, at most every 6 hours) and reports it like the own updater does
 * ([AppUpdateState]), so the same notice appears. „Jetzt aktualisieren“ starts Play's flexible flow (download in the
 * background, then restart); under the block the immediate one (Play's full screen). Play names no version, so the
 * states carry an empty version and the versionCode is in [availableBuild].
 */
private class PlayUpdates(context: Context) : StoreUpdates {
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private val prefs = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    override val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val _availableBuild = MutableStateFlow<Long?>(null)
    override val availableBuild: StateFlow<Long?> = _availableBuild.asStateFlow()

    override var launcher: ActivityResultLauncher<IntentSenderRequest>? = null

    @Volatile private var info: AppUpdateInfo? = null

    /** An immediate update was started: resume it on the return to the app. */
    @Volatile private var immediate = false

    private val listener = InstallStateUpdatedListener { s ->
        when (s.installStatus()) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING -> {
                val total = s.totalBytesToDownload()
                _state.value = AppUpdateState.Downloading("", if (total > 0) s.bytesDownloaded().toFloat() / total else null)
            }
            // Downloaded in the background: install right away, the app restarts (the person asked for it).
            InstallStatus.DOWNLOADED -> complete()
            InstallStatus.INSTALLING -> _state.value = AppUpdateState.Installing("")
            InstallStatus.INSTALLED -> _state.value = AppUpdateState.UpToDate
            InstallStatus.FAILED -> _state.value = AppUpdateState.Failed(texts.system.updateFailed, "")
            InstallStatus.CANCELED -> offer()
            else -> Unit
        }
    }

    init {
        manager.registerListener(listener)
    }

    override fun check(force: Boolean) {
        when (_state.value) {
            is AppUpdateState.Checking, is AppUpdateState.Downloading, is AppUpdateState.Installing -> return
            else -> Unit
        }
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MILLIS && info != null) return
        if (force) _state.value = AppUpdateState.Checking
        manager.appUpdateInfo
            .addOnSuccessListener { found ->
                prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                info = found
                when {
                    found.installStatus() == InstallStatus.DOWNLOADED -> {
                        _availableBuild.value = found.availableVersionCode().toLong()
                        _state.value = AppUpdateState.Available("", 0)
                    }
                    found.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                        found.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        _availableBuild.value = found.availableVersionCode().toLong()
                        _state.value = AppUpdateState.Available("", found.totalBytesToDownload())
                    }
                    else -> {
                        _availableBuild.value = null
                        _state.value = AppUpdateState.UpToDate
                    }
                }
            }
            .addOnFailureListener { e ->
                // Not installed from Play (e.g. a debug build) or no internet: no notice, as with the own updater.
                Log.i(TAG, "Play update check failed: ${e.message}")
                _state.value = if (force) AppUpdateState.Failed(texts.system.updateCheckFailed, null) else AppUpdateState.Idle
            }
    }

    override fun install(immediate: Boolean) {
        val known = info
        if (known == null || _availableBuild.value == null) return check(force = true)
        if (known.installStatus() == InstallStatus.DOWNLOADED) return complete()
        val type = if (immediate) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE
        val start = launcher
        if (start == null || !known.isUpdateTypeAllowed(type)) {
            _state.value = AppUpdateState.Failed(texts.system.updateFailed, "")
            return
        }
        this.immediate = immediate
        // An AppUpdateInfo can be used only once to start a flow; ask again for the next try.
        info = null
        val started = runCatching {
            manager.startUpdateFlowForResult(known, start, AppUpdateOptions.newBuilder(type).build())
        }.getOrDefault(false)
        if (!started) {
            _state.value = AppUpdateState.Failed(texts.system.updateFailed, "")
        } else if (!immediate) {
            _state.value = AppUpdateState.Downloading("", null)
        }
        refreshInfo()
    }

    override fun onResume() {
        manager.appUpdateInfo.addOnSuccessListener { found ->
            info = found
            when {
                found.installStatus() == InstallStatus.DOWNLOADED -> complete()
                // Immediate update interrupted (app left during Play's full screen): continue it.
                immediate && found.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> install(immediate = true)
            }
        }
    }

    override fun onFlowResult(resultCode: Int) {
        // „Abbrechen“ in Play's dialog: simply offer again.
        if (resultCode != Activity.RESULT_OK) {
            immediate = false
            offer()
        }
    }

    private fun offer() {
        val build = _availableBuild.value
        _state.value = if (build != null) AppUpdateState.Available("", 0) else AppUpdateState.Idle
    }

    private fun complete() {
        _state.value = AppUpdateState.Installing("")
        manager.completeUpdate()
    }

    private fun refreshInfo() {
        manager.appUpdateInfo.addOnSuccessListener { info = it }
    }

    private companion object {
        const val TAG = "PlayUpdates"
        const val KEY_LAST_CHECK = "play.lastCheck"
        const val CHECK_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L
    }
}
