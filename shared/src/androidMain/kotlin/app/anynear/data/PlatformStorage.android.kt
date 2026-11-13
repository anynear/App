package app.anynear.data

import android.content.Context
import app.anynear.AndroidPlatform

/** App settings (brand-neutral since ADR 0061, decision 3). */
private const val VALUES_NAME = "values"

private class SharedPreferencesStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences(VALUES_NAME, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}

actual fun platformKeyValueStore(): KeyValueStore = SharedPreferencesStore(AndroidPlatform.requireContext())

actual fun platformDataDir(): String = AndroidPlatform.requireContext().filesDir.absolutePath

private class AndroidTextFile(private val file: java.io.File) : TextFile {
    override fun read(): String? = file.takeIf { it.exists() }?.readText()

    override fun write(content: String) {
        val tmp = java.io.File(file.parentFile, "${file.name}.tmp")
        java.io.FileOutputStream(tmp).use { out ->
            out.write(content.encodeToByteArray())
            out.fd.sync()
        }
        check(tmp.renameTo(file)) { "${file.name} konnte nicht gespeichert werden." }
    }
}

actual fun platformTextFile(name: String): TextFile = AndroidTextFile(java.io.File(AndroidPlatform.requireContext().filesDir, name))

/** The same settings as [SharedPreferencesStore], raw for the backup (ADR 0027). */
private class SharedPreferencesRawAccess(context: Context) : RawKeyValueAccess {
    private val prefs = context.getSharedPreferences(VALUES_NAME, Context.MODE_PRIVATE)

    override fun entries(): Map<String, String> = prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()

    override fun replaceAll(values: Map<String, String>) {
        // commit instead of apply: right afterwards the app reads the restored state.
        prefs.edit().clear().apply { values.forEach { (key, value) -> putString(key, value) } }.commit()
    }
}

actual fun platformRawKeyValueAccess(): RawKeyValueAccess = SharedPreferencesRawAccess(AndroidPlatform.requireContext())

/**
 * New task with the start screen, then end the process; the new process restores the backup or
 * starts with the keys passed in (ADR 0042). Before that, everything to disk: `apply()` writes
 * asynchronously, and `exit(0)` does not wait for it.
 */
actual fun restartApp() {
    val context = AndroidPlatform.requireContext()
    listOf(VALUES_NAME, SECRETS_NAME, app.anynear.passwords.AndroidPasswordKeyStore.PREFS).forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().commit() }
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.component?.let { component ->
        context.startActivity(android.content.Intent.makeRestartActivityTask(component))
    }
    Runtime.getRuntime().exit(0)
}

actual fun deleteRadioCache() {
    val context = AndroidPlatform.requireContext()
    // On Android, SQLDelight puts the databases in the database folder, not in the folder passed in.
    listOfNotNull(context.filesDir, context.getDatabasePath("x").parentFile).forEach { dir ->
        dir.listFiles()?.filter { it.name.startsWith("meshtastic") }?.forEach { it.deleteRecursively() }
    }
}

actual fun platformDeviceName(): String = android.os.Build.MODEL.orEmpty()
