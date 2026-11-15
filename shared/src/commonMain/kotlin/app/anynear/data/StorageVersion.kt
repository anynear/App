package app.anynear.data

/**
 * Version of what is stored on the device (ADR 0061, decision 13): from anynear on there is never a reinstall again, so
 * stored data is migrated instead of discarded. Any change to names or shape of stored values increments [CURRENT] and
 * comes with a [Migration] with a test; [MIGRATIONS] contains them in the order of the versions.
 *
 * The number is stored unencrypted under [KEY] in the key-value store.
 */
object StorageVersion {
    const val KEY = "storage.version"

    /** First version under anynear. */
    const val FIRST = 1

    const val CURRENT = 7

    /** Beta key and time of its last check; no longer needed since version 2 (download without sign-in). */
    internal val OLD_BETA_SECRETS = listOf("beta_key", "beta_key_checked")

    /** Migration from version `FIRST + i` to `FIRST + i + 1` at position `i`. */
    val MIGRATIONS: List<Migration> = listOf(
        // 1 → 2: The beta key is dropped; the app loads version data and APK without sign-in.
        Migration { s -> OLD_BETA_SECRETS.forEach { s.secrets.put(it, null) } },
        // 2 → 3: Messages in the history have `deleted` (ADR 0068) and `link` (ADR 0069). Both with a default, older
        // entries read unchanged as "not deleted, without preview"; there is nothing to rewrite.
        Migration { },
        // 3 → 4: Files of the storages have `onlineOnly` (ADR 0080) with default `false`: older entries read as
        // "on the phone and online", as they are; there is nothing to rewrite.
        Migration { },
        // 4 → 5: Files of the storages have `operators` and `everSecured` (ADR 0028 §7) with default 0 and `false`:
        // older entries read as "being backed up in the network" until the next sync counts the providers; there is
        // nothing to rewrite.
        Migration { },
        // 5 → 6: Files of the storages have `title` (ADR 0091) with default `null`: older entries read as "no title yet",
        // reading makes one. The order of the storages is a new key `files.sort` (default: newest first). There is
        // nothing to rewrite.
        Migration { },
        // 6 → 7: Messages in the history have `replyTo` (ADR 0092) with default `null`: older entries read as "no
        // answer"; there is nothing to rewrite.
        Migration { },
    )

    /** What a migration may change: all stores of the app, before anything reads them. */
    class Stores(val keyValues: RawKeyValueAccess, val secrets: SecretStore, val secretKeys: () -> Set<String>)

    fun interface Migration {
        fun migrate(stores: Stores)
    }

    /**
     * Before everything else at start, after the fresh start. Without a number the store is
     * new or from [FIRST]. A higher number than known (older app restored over a newer one) stays untouched.
     * Returns the version the store is at afterwards.
     */
    fun migrate(stores: Stores, migrations: List<Migration> = MIGRATIONS, current: Int = FIRST + migrations.size): Int {
        val stored = stores.keyValues.entries()[KEY]?.toIntOrNull() ?: FIRST
        if (stored >= current) {
            if (stored == current) write(stores.keyValues, current)
            return stored
        }
        for (version in stored until current) {
            migrations[version - FIRST].migrate(stores)
            // Record after every step: if the app aborts mid-chain, it continues there at the next start.
            write(stores.keyValues, version + 1)
        }
        return current
    }

    private fun write(keyValues: RawKeyValueAccess, version: Int) {
        val entries = keyValues.entries()
        if (entries[KEY] != version.toString()) keyValues.replaceAll(entries + (KEY to version.toString()))
    }
}
