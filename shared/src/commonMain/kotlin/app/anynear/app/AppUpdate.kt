package app.anynear.app

/**
 * Update of the app itself, without an app store. As long as there is only the test version from anynear.app,
 * this is the only way to new versions: the app asks anynear.app, downloads the new APK,
 * verifies it and hands it to Android's package installer. Android only installs if the APK is signed with
 * the same key as the installed app.
 *
 * On iOS, in debug builds and when installed via an app store: [Unsupported]. The Google Play build (flavor `play`)
 * reports Play's In-App Updates with the same states (Docs ADR 0096 §7); Play names no version, so `version` is empty.
 */
sealed interface AppUpdateState {
    /** There is no update via the app here. */
    data object Unsupported : AppUpdateState

    /** Not checked yet, or checking did not work (e.g. no internet). No notice needed. */
    data object Idle : AppUpdateState

    data object Checking : AppUpdateState

    /** Installed version is the newest. */
    data object UpToDate : AppUpdateState

    data class Available(val version: String, val sizeBytes: Long) : AppUpdateState

    /** [progress] from 0 to 1, `null` as long as the size is unknown. */
    data class Downloading(val version: String, val progress: Float?) : AppUpdateState

    /** Android needs the permission “Unbekannte Apps installieren” for anynear once. */
    data class NeedsPermission(val version: String) : AppUpdateState

    /** Is with Android's package installer; the app restarts in a moment. */
    data class Installing(val version: String) : AppUpdateState

    /** Short sentence for people without technical knowledge; [version] set if a retry is possible. */
    data class Failed(val message: String, val version: String?) : AppUpdateState

    /**
     * The newest version only exists as a new app with a different app ID (ADR 0025, app ID change). Android
     * cannot install it via this one; the way is the move by backup (ADR 0027).
     */
    data class MovedToNewApp(val version: String) : AppUpdateState

    /**
     * The newest version is signed with a different key (new signing key, ADR 0037). Android
     * does not install it via this app; the way is to delete and reinstall.
     */
    data class ReinstallNeeded(val version: String) : AppUpdateState
}

/** What a found version means for this installation. */
enum class UpdateKind { NONE, NEWER, MOVED, REINSTALL }

/**
 * [manifestPackage] is the package name from the version data; if it is missing (older releases), it is the same
 * app. A different app ID beats any version number: it can never be installed here.
 *
 * [manifestSigner] and [installedSigner] are the SHA-256 of the signing certificate (hex, lowercase). If one is missing
 * (older releases, Android before 9), only the version counts. A different key likewise beats any
 * version number.
 */
fun classifyUpdate(
    installedPackage: String,
    installedVersionCode: Long,
    manifestPackage: String?,
    manifestVersionCode: Long,
    installedSigner: String? = null,
    manifestSigner: String? = null,
): UpdateKind = when {
    manifestPackage != null && manifestPackage != installedPackage -> UpdateKind.MOVED
    manifestSigner != null && installedSigner != null && !manifestSigner.equals(installedSigner, ignoreCase = true) -> UpdateKind.REINSTALL
    manifestVersionCode > installedVersionCode -> UpdateKind.NEWER
    else -> UpdateKind.NONE
}

/**
 * Whether the store's version [candidate] is newer than [installed], both `X.Y.Z` (App Store, Docs ADR 0096 §7).
 * Missing parts count as 0; anything after a `-` or `+` is ignored; unreadable versions are never newer.
 */
fun isNewerVersion(candidate: String, installed: String): Boolean {
    fun parts(v: String): List<Int>? = v.substringBefore('-').substringBefore('+').split('.').map { it.toIntOrNull() ?: return null }
    val a = parts(candidate) ?: return false
    val b = parts(installed) ?: return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
