package app.anynear.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * App group shared by app and Notification Service Extension (ADR 0009): read states, circle names,
 * members, mailbox list. Without the entitlement (build without team ID) it stays with the
 * app's own settings; the extension then sees nothing and shows only the fixed text.
 */
internal const val APP_GROUP = "group.app.anynear"

private class UserDefaultsStore(private val defaults: NSUserDefaults) : KeyValueStore {
    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }
}

/** Name of the settings in which [sharedDefaults] lives: the app group or, without it, the app itself. */
private var sharedDomain: String? = null

private val sharedDefaults: NSUserDefaults by lazy {
    val groupAvailable = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP) != null
    val group = if (groupAvailable) NSUserDefaults(suiteName = APP_GROUP) else null
    if (group == null) {
        sharedDomain = NSBundle.mainBundle.bundleIdentifier
        return@lazy NSUserDefaults.standardUserDefaults
    }
    sharedDomain = APP_GROUP
    group
}

/** Does this code run in an app extension (e.g. the Notification Service Extension)? */
internal fun isExtension(): Boolean = NSBundle.mainBundle.bundlePath.endsWith(".appex")

actual fun platformKeyValueStore(): KeyValueStore = UserDefaultsStore(sharedDefaults)

/** Only the own entries, not the global ones of iOS that [NSUserDefaults.dictionaryRepresentation] includes. */
private object UserDefaultsRawAccess : RawKeyValueAccess {
    override fun entries(): Map<String, String> {
        val defaults = sharedDefaults
        val domain = sharedDomain ?: return emptyMap()
        return defaults.persistentDomainForName(domain).orEmpty()
            .mapNotNull { (key, value) -> if (key is String && value is String) key to value else null }.toMap()
    }

    override fun replaceAll(values: Map<String, String>) {
        val defaults = sharedDefaults
        for (key in entries().keys) defaults.removeObjectForKey(key)
        for ((key, value) in values) defaults.setObject(value, forKey = key)
        defaults.synchronize()
    }
}

actual fun platformRawKeyValueAccess(): RawKeyValueAccess = UserDefaultsRawAccess

/** Set by the MainViewController: replaces the controller within the process. */
internal var restartHandler: (() -> Unit)? = null

/**
 * iOS cannot start itself anew, and quitting looks like a crash to the person (tester report: after the emergency card
 * code the app closed although the phone was added). So the MainViewController builds a new controller that reads
 * the stored state, as a new start would. Only without it (no UI, e.g. in an extension) quit as before.
 */
actual fun restartApp() {
    // Write settings first; the keychain writes immediately anyway (ADR 0042).
    sharedDefaults.synchronize()
    restartHandler?.let { return it() }
    platform.posix.exit(0)
}

@OptIn(ExperimentalForeignApi::class)
actual fun deleteRadioCache() {
    val fm = NSFileManager.defaultManager
    val dir = platformDataDir()
    fm.contentsOfDirectoryAtPath(dir, error = null).orEmpty().filterIsInstance<String>().filter { it.startsWith("meshtastic") }
        .forEach { fm.removeItemAtPath("$dir/$it", error = null) }
}

actual fun platformDeviceName(): String = platform.UIKit.UIDevice.currentDevice.model

@OptIn(ExperimentalForeignApi::class)
actual fun platformDataDir(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) as NSURL?
    return checkNotNull(url?.path) { "Dokumentenverzeichnis nicht verfügbar." }
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
private class IosTextFile(private val path: String) : TextFile {
    override fun read(): String? = NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)

    override fun write(content: String) {
        @Suppress("CAST_NEVER_SUCCEEDS")
        val ok = (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        check(ok) { "Datei konnte nicht gespeichert werden." }
    }
}

actual fun platformTextFile(name: String): TextFile = IosTextFile("${platformDataDir()}/$name")
