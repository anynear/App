package app.anynear

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import app.anynear.app.AppUpdateState
import kotlinx.coroutines.flow.StateFlow

/**
 * Updates through the app store from within the app (Docs ADR 0096 §6, §7): flavor `play` asks Google Play
 * (In-App Updates); flavor `direct` has none ([storeUpdates] is `null` there, the own [AppUpdater] takes over).
 * The state is the same [AppUpdateState] as with the own updater, so the app shows the same notice.
 */
interface StoreUpdates {
    val state: StateFlow<AppUpdateState>

    /** versionCode the store offers, `null` if none. */
    val availableBuild: StateFlow<Long?>

    /** Starts Play's dialogs; set by the visible Activity, `null` without one. */
    var launcher: ActivityResultLauncher<IntentSenderRequest>?

    /** Ask the store; without [force] at most every 6 hours. */
    fun check(force: Boolean)

    /** Flexible (in the background, then restart) or, under the block, immediate (full screen of the store). */
    fun install(immediate: Boolean)

    /** Back in the app: finish a downloaded update, resume an immediate one. */
    fun onResume()

    /** Result of the store's dialog. */
    fun onFlowResult(resultCode: Int)
}
