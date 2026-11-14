package app.anynear

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.content.ContextCompat
import app.anynear.app.AppUpdateState
import app.anynear.app.UpdateKind
import app.anynear.app.classifyUpdate
import app.anynear.i18n.texts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Updates the APK outside Google Play (flavor `direct`) itself from the GitHub releases of the app.
 *
 * Flow: `build.json` of the latest release ([GITHUB_MANIFEST_URL]) names the newest version (versionCode, size,
 * SHA-256, package, signer), without sign-in; the APK is the asset `anynear-v<version>.apk` of the same release.
 * As long as the releases are not public, the version data of the website ([MANIFEST_URL]) stands in.
 * If it is newer than the installed one, the app downloads the APK, also without sign-in,
 * checks size, SHA-256, package name, version and signature
 * and hands it to the [PackageInstaller]. Android shows its own confirmation dialog in the process and
 * only installs if the APK is signed with the same key as the installed app.
 *
 * The check happens on opening and, as long as the process runs (foreground service, ADR 0008), also in the
 * background, each at most every [CHECK_INTERVAL_MILLIS] (battery: an update is not urgent, „Nach neuer Version
 * sehen“ in the settings asks immediately). If the app finds a new version in the
 * background, the notification „Neue Version von anynear“ appears once per version.
 *
 * Off in the Google Play build (Play updates), for debug builds (own key) and when an app store installed the app.
 */
class AppUpdater(private val context: Context) {
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val prefs = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)

    /** Most recently found newer version. */
    @Volatile private var latest: Release? = null

    private val _availableBuild = MutableStateFlow<Long?>(null)

    /**
     * versionCode of the newer version found last, kept while it is downloaded and installed; `null` if none
     * (ADR 0096 §6: the block only comes if this is at least `min`).
     */
    val availableBuild: StateFlow<Long?> = _availableBuild.asStateFlow()

    /** Lines for the log in the app (Einstellungen > App > Erweitert), set by [MainApplication]. */
    @Volatile var log: (String) -> Unit = {}

    /** Waits for the permission „Unbekannte Apps installieren“, after that it continues by itself. */
    @Volatile private var installAfterPermission = false

    private val installed: PackageInfo get() = context.packageManager.getPackageInfo(context.packageName, 0)

    val version: String? get() = installed.versionName

    /** versionCode from the number of commits (2n after the merge, 2n+1 at the tag; see beta-release.yml). */
    val build: Long get() = PackageInfoCompat.getLongVersionCode(installed)

    /** An app store installed the app and updates it. */
    val fromStore: Boolean = installerPackage() in STORE_INSTALLERS

    val supported: Boolean = run {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        BuildConfig.SELF_UPDATE && !debuggable && !fromStore
    }

    init {
        if (!supported) {
            _state.value = AppUpdateState.Unsupported
        } else {
            // Also without opening the app; check() itself takes care of the interval ([CHECK_INTERVAL_MILLIS]).
            worker.scheduleWithFixedDelay({ check(force = false) }, 1, 60, TimeUnit.MINUTES)
        }
    }

    fun check(force: Boolean) {
        if (!supported) return
        when (_state.value) {
            is AppUpdateState.Checking, is AppUpdateState.Downloading, is AppUpdateState.Installing, is AppUpdateState.NeedsPermission -> return
            else -> Unit
        }
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MILLIS) return
        _state.value = AppUpdateState.Checking
        worker.execute {
            try {
                val release = fetchManifest()
                prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                when (classify(release)) {
                    UpdateKind.MOVED -> {
                        // New app ID (ADR 0025): not installable, lead to the move instead.
                        latest = null
                        _availableBuild.value = null
                        note("Neue App ${release.packageName} ${release.version}")
                        _state.value = AppUpdateState.MovedToNewApp(release.version)
                        if (!inForeground()) notifyAvailable(release, UpdateKind.MOVED)
                    }
                    UpdateKind.REINSTALL -> {
                        // New signing key (ADR 0037): Android does not accept the APK, so reinstall.
                        latest = null
                        _availableBuild.value = null
                        note("Neuer Signaturschlüssel ${release.version}")
                        _state.value = AppUpdateState.ReinstallNeeded(release.version)
                        if (!inForeground()) notifyAvailable(release, UpdateKind.REINSTALL)
                    }
                    UpdateKind.NEWER -> {
                        latest = release
                        _availableBuild.value = release.versionCode
                        _state.value = AppUpdateState.Available(release.version, release.size)
                        if (!inForeground()) notifyAvailable(release)
                    }
                    UpdateKind.NONE -> {
                        latest = null
                        _availableBuild.value = null
                        _state.value = AppUpdateState.UpToDate
                    }
                }
            } catch (e: Exception) {
                note("Nachsehen fehlgeschlagen", e)
                // Without internet no error notice; on a manual check there is one.
                _state.value = if (force) {
                    AppUpdateState.Failed(texts.system.updateCheckFailed, null)
                } else {
                    AppUpdateState.Idle
                }
            }
        }
    }

    fun install() {
        val known = latest ?: return check(force = true)
        if (!context.packageManager.canRequestPackageInstalls()) {
            installAfterPermission = true
            _state.value = AppUpdateState.NeedsPermission(known.version)
            openInstallPermission()
            return
        }
        installAfterPermission = false
        cancelNotification()
        _state.value = AppUpdateState.Downloading(known.version, null)
        worker.execute {
            var release = known
            val apk = try {
                var attempt = 0
                var file: File? = null
                while (file == null) {
                    // Only the newest APK is on anynear.app (fallback source). A version found earlier may already be
                    // deleted (404), so re-read the manifest before downloading.
                    release = fetchManifest()
                    when (classify(release)) {
                        UpdateKind.NEWER -> Unit
                        UpdateKind.MOVED -> {
                            latest = null
                            _availableBuild.value = null
                            _state.value = AppUpdateState.MovedToNewApp(release.version)
                            return@execute
                        }
                        UpdateKind.REINSTALL -> {
                            latest = null
                            _availableBuild.value = null
                            _state.value = AppUpdateState.ReinstallNeeded(release.version)
                            return@execute
                        }
                        UpdateKind.NONE -> {
                            latest = null
                            _availableBuild.value = null
                            _state.value = AppUpdateState.UpToDate
                            return@execute
                        }
                    }
                    latest = release
                    _availableBuild.value = release.versionCode
                    _state.value = AppUpdateState.Downloading(release.version, null)
                    note("Lade ${release.version} (${release.size} Bytes)")
                    file = try {
                        download(release).also { verify(it, release) }
                    } catch (e: HttpStatusException) {
                        // 409: there is a newer version by now, reload the version data.
                        if (e.code != HttpURLConnection.HTTP_CONFLICT || ++attempt > 2) throw e
                        note("Version ${release.version} veraltet (409), lade Versionsdaten neu")
                        null
                    }
                }
                file
            } catch (e: HttpStatusException) {
                note("Laden von ${release.version} fehlgeschlagen", e)
                _state.value = AppUpdateState.Failed(texts.system.downloadFailed, release.version)
                return@execute
            } catch (e: UpdateException) {
                note("Laden von ${release.version} fehlgeschlagen: ${e.detail}")
                _state.value = AppUpdateState.Failed(e.message ?: FAILED, release.version)
                return@execute
            } catch (e: Exception) {
                note("Laden von ${release.version} fehlgeschlagen", e)
                _state.value = AppUpdateState.Failed(
                    if (e is IOException) texts.system.downloadFailed else FAILED,
                    release.version,
                )
                return@execute
            }
            try {
                _state.value = AppUpdateState.Installing(release.version)
                note("Übergebe ${release.version} an Android")
                commit(apk)
            } catch (e: Exception) {
                // Error in the package installer, not in the internet connection.
                note("Übergabe an Android fehlgeschlagen", e)
                _state.value = AppUpdateState.Failed(FAILED, release.version)
            }
        }
    }

    /** Back from the system settings: with permission, continue right away. */
    fun onResume() {
        // The app is open and shows the notice itself.
        cancelNotification()
        val waiting = _state.value as? AppUpdateState.NeedsPermission ?: return
        if (!installAfterPermission) return
        if (context.packageManager.canRequestPackageInstalls()) {
            install()
        } else {
            // Not allowed: offer again without jumping to the settings once more.
            installAfterPermission = false
            _state.value = AppUpdateState.Available(waiting.version, latest?.size ?: 0)
        }
    }

    /** Result from the package installer ([UpdateInstallReceiver]). */
    internal fun onInstallResult(status: Int, message: String?) {
        val version = latest?.version
        note("Android meldet Status $status${message?.let { ": $it" } ?: ""}")
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> _state.value = AppUpdateState.UpToDate
            // „Abbrechen“ in Android's dialog: simply offer again.
            PackageInstaller.STATUS_FAILURE_ABORTED -> latest?.let { _state.value = AppUpdateState.Available(it.version, it.size) }
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                _state.value = AppUpdateState.Failed(texts.system.notEnoughSpace, version)
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                _state.value = AppUpdateState.Failed(
                    texts.system.versionMismatch,
                    null,
                )
            else -> {
                _state.value = AppUpdateState.Failed(FAILED, version)
            }
        }
    }

    /** From GitHub; if that fails (no connection, releases not public yet), from the website. */
    private fun fetchManifest(): Release =
        try {
            fetchGitHubManifest()
        } catch (e: Exception) {
            note("GitHub nicht erreichbar, nehme anynear.app", e)
            fetchSiteManifest()
        }

    /** `build.json` of the latest release; the APK lies next to it in the same release. */
    private fun fetchGitHubManifest(): Release {
        val json = JSONObject(connect(GITHUB_MANIFEST_URL).use { it.inputStream.bufferedReader().readText() })
        val version = json.getString("version")
        if (!VERSION_NAME.matches(version)) throw IOException("Ungültige Version in build.json: $version")
        return release(json, "$GITHUB/releases/download/v$version/anynear-v$version.apk")
    }

    private fun fetchSiteManifest(): Release {
        val json = JSONObject(connect(MANIFEST_URL).use { it.inputStream.bufferedReader().readText() })
        return release(json, siteUrl(json.getString("path")))
    }

    private fun release(json: JSONObject, url: String): Release {
        val sha = json.getString("sha256").lowercase()
        if (!SHA256.matches(sha)) throw IOException("Ungültige Prüfsumme im Manifest")
        return Release(
            version = json.getString("version"),
            versionCode = json.getLong("versionCode"),
            url = url,
            size = json.getLong("size"),
            sha256 = sha,
            // Older version data without the field: the same app.
            packageName = json.optString("package").ifBlank { null },
            // Older version data without the field: the same key (ADR 0037).
            signer = json.optString("signer").lowercase().takeIf { SHA256.matches(it) },
        )
    }

    private fun classify(release: Release): UpdateKind =
        classifyUpdate(
            context.packageName,
            installed.longVersionCodeCompat(),
            release.packageName,
            release.versionCode,
            installedSigner = ownSigner,
            manifestSigner = release.signer,
        )

    /** SHA-256 of our signing certificate (lowercase hex), like `signer` in version data. `null` before Android 9. */
    private val ownSigner: String? by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@lazy null
        runCatching {
            val signers = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
            signers?.singleOrNull()?.let { cert ->
                MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
            }
        }.getOrNull()
    }

    private fun download(release: Release): File {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "update.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        var read = 0L
        var lastPercent = -1
        connect(release.url, readTimeoutMillis = 60_000).use { connection ->
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (read > release.size) throw UpdateException(FAILED, "Datei größer als angekündigt")
                        val percent = (read * 100 / release.size.coerceAtLeast(1)).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.value = AppUpdateState.Downloading(release.version, percent / 100f)
                        }
                    }
                }
            }
        }
        if (read != release.size) throw IOException("Unvollständig geladen: $read von ${release.size} Bytes")
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (sha != release.sha256) throw UpdateException(FAILED, "SHA-256 stimmt nicht: $sha")
        return file
    }

    /** Before handing over to Android: right app, newer version, the same key. */
    private fun verify(apk: File, release: Release) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: throw UpdateException(FAILED, "APK nicht lesbar")
        if (archive.packageName != context.packageName) throw UpdateException(FAILED, "Falsches Paket: ${archive.packageName}")
        if (archive.longVersionCodeCompat() <= installed.longVersionCodeCompat()) throw UpdateException(FAILED, "Keine neuere Version")
        if (archive.longVersionCodeCompat() != release.versionCode) throw UpdateException(FAILED, "Version passt nicht zum Manifest")
        // Below Android 9 only the package installer itself checks that (the same key is mandatory there).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val own = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            val new = archive.signingInfo
            val ownCerts = own?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            val newCerts = new?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            if (ownCerts.isEmpty() || ownCerts != newCerts) {
                throw UpdateException(
                    texts.system.versionMismatch,
                    "Anderer Signaturschlüssel",
                )
            }
        }
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // If anynear is already the installation source itself, Android may update without a prompt
            // (UPDATE_PACKAGES_WITHOUT_USER_ACTION in the manifest); otherwise Android asks as usual.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("update.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName),
                flags,
            )
            session.commit(callback.intentSender)
        }
    }

    /** Once per version, only in the background; in the open app the dialog appears instead. */
    private fun notifyAvailable(release: Release, kind: UpdateKind = UpdateKind.NEWER) {
        if (prefs.getLong(KEY_NOTIFIED, 0) >= release.versionCode) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel()
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(AppBrand.notificationIcon)
            .setContentTitle(
                when (kind) {
                    UpdateKind.MOVED -> texts.backup.movedTitle
                    UpdateKind.REINSTALL -> texts.backup.reinstallTitle
                    else -> texts.settings.newVersionTitle(release.version)
                },
            )
            .setContentText(
                when (kind) {
                    UpdateKind.MOVED -> texts.system.movedNotificationText
                    UpdateKind.REINSTALL -> texts.system.reinstallNotificationText
                    else -> texts.system.updateNotificationText
                },
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            prefs.edit().putLong(KEY_NOTIFIED, release.versionCode).apply()
        } catch (e: SecurityException) {
            // Permission revoked in the meantime; the notice then comes when the app is opened.
        }
    }

    /** Creates the channel or renames it in the current language (like [MessageNotifier.ensureChannel]). */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, texts.system.updatesChannel, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = texts.system.updatesChannelDescription
            },
        )
    }

    private fun cancelNotification() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun inForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun openInstallPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Einstellung „Unbekannte Apps“ nicht vorhanden", e)
            installAfterPermission = false
            _state.value = AppUpdateState.Failed(
                texts.system.allowInstall,
                latest?.version,
            )
        }
    }

    /**
     * Follows redirects only to [ALLOWED_HOSTS] over https (GitHub sends release files on to its download host).
     * Whatever comes from there still has to match size, SHA-256, package and signature.
     */
    private fun connect(url: String, readTimeoutMillis: Int = 15_000): AutoCloseableConnection {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            checkHost(current)
            val connection = open(current, readTimeoutMillis)
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_OK) return AutoCloseableConnection(connection)
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (code !in REDIRECTS || location == null) throw HttpStatusException(current, code)
            current = URL(URL(current), location).toString()
        }
        throw IOException("Zu viele Weiterleitungen: $url")
    }

    private fun checkHost(url: String) {
        val parsed = URL(url)
        if (parsed.protocol != "https" || parsed.host !in ALLOWED_HOSTS) throw IOException("Nicht erlaubte Adresse: ${parsed.host}")
    }

    /** Redirects are followed by [connect] itself, only to [ALLOWED_HOSTS]. */
    private fun open(url: String, readTimeoutMillis: Int): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = readTimeoutMillis
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.instanceFollowRedirects = false
        return connection
    }

    /** Only files under /download/ of anynear.app, nothing that the manifest redirects elsewhere. */
    private fun siteUrl(path: String): String {
        if (!DOWNLOAD_PATH.matches(path) || path.contains("..")) throw IOException("Ungültiger Pfad im Manifest: $path")
        return "$SITE$path"
    }

    private fun installerPackage(): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    } catch (e: Exception) {
        null
    }

    private class AutoCloseableConnection(val connection: HttpURLConnection) : AutoCloseable {
        val inputStream get() = connection.inputStream
        override fun close() = connection.disconnect()
    }

    private class UpdateException(userMessage: String, val detail: String) : Exception(userMessage)

    /** Response other than 200; [code] e.g. 404 (releases not public) or 409 (version data outdated). */
    private class HttpStatusException(url: String, val code: Int) : IOException("$url antwortet $code")

    /** To Logcat and to the app's log, with error type and text. */
    private fun note(message: String, cause: Throwable? = null) {
        Log.w(TAG, message, cause)
        log(message + (cause?.let { " (${it::class.simpleName}: ${it.message})" } ?: ""))
    }

    private data class Release(
        val version: String,
        val versionCode: Long,
        val url: String,
        val size: Long,
        val sha256: String,
        val packageName: String?,
        val signer: String?,
    )

    companion object {
        private const val TAG = "App/Update"
        private const val SITE_HOST = "anynear.app"
        private const val SITE = "https://$SITE_HOST"

        /** Releases of the app with `build.json` and `anynear-v<version>.apk` (job android in beta-release.yml). */
        private const val GITHUB = "https://github.com/anynear/App"
        const val GITHUB_MANIFEST_URL = "$GITHUB/releases/latest/download/build.json"

        /** Fallback while the releases are not public: written by the website (repo Website) when deploying. */
        const val MANIFEST_URL = "$SITE/v1/app/android.json"

        /** GitHub forwards release files to its download hosts. */
        private val ALLOWED_HOSTS = setOf(
            SITE_HOST,
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )
        private const val MAX_REDIRECTS = 5
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
        private val VERSION_NAME = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(-[0-9a-f]{7})?$")

        private const val KEY_LAST_CHECK = "last_check"

        /** versionCode for which a notification last appeared. */
        private const val KEY_NOTIFIED = "notified_version_code"
        private const val CHANNEL_ID = "app_update"
        private const val NOTIFICATION_ID = 200
        private const val CHECK_INTERVAL_MILLIS = 12 * 60 * 60 * 1000L
        private val FAILED: String get() = texts.system.updateFailed
        private val SHA256 = Regex("^[0-9a-f]{64}$")
        private val DOWNLOAD_PATH = Regex("^/download/[A-Za-z0-9._-]+\\.apk$")

        /** With these installation sources the store updates, not the app. */
        private val STORE_INSTALLERS = setOf("com.android.vending", "org.fdroid.fdroid", "org.fdroid.basic", "com.aurora.store")

        private fun PackageInfo.longVersionCodeCompat(): Long =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else @Suppress("DEPRECATION") versionCode.toLong()
    }
}
