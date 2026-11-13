package app.anynear.data

import app.anynear.attachments.BinaryFiles
import app.anynear.core.CoreSecretKeys
import app.anynear.core.LocalKey
import app.anynear.core.cryptoProvider
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor

/**
 * Manual backup of the whole state of this installation, encrypted with a password of your own
 * choosing (ADR 0027). With it, everything can be restored on a freshly installed app, even on
 * another phone.
 *
 * What is on the device is backed up unchanged: all entries of the key-value store (already
 * encrypted with the device key), all secrets (among them the device key itself, so that the
 * remaining data is readable again), the message history and the folders with attachments, images and
 * drafts. Not backed up: the cache of the radio SDK, it fills up by itself.
 *
 * Restoring happens in two steps: [stageRestore] checks the password and stores the content as staged,
 * [applyPendingRestore] replaces everything at the next start, before the app reads anything. This way the running
 * app can no longer overwrite the restored state.
 */
@OptIn(ExperimentalSerializationApi::class)
class StateBackup(
    private val keyValues: RawKeyValueAccess,
    private val secrets: SecretStore,
    private val secretKeys: () -> Set<String>,
    private val textFiles: Map<String, TextFile>,
    private val binaryDirs: Map<String, BinaryFiles>,
    private val pending: BinaryFiles,
    /** On restore, receives the password key from the backup (ADR 0039, item 5), if it has one. */
    private val restorePasswordKey: (ByteArray) -> Unit = {},
) {
    /**
     * Backup file from the current state, encrypted with [password]. [passwordKey]: key of the passwords,
     * read after the device lock; without it the logins in the backup are not readable (ADR 0039).
     */
    fun create(password: String, createdAtMillis: Long, appVersion: String?, format: Int = FORMAT, passwordKey: ByteArray? = null): ByteArray {
        require(password.length >= MIN_PASSWORD_LENGTH) { "Passwort zu kurz" }
        val content = BackupContent(
            format = format,
            createdAtMillis = createdAtMillis,
            appVersion = appVersion,
            keyValues = keyValues.entries(),
            secrets = secretKeys().mapNotNull { key -> secrets.get(key)?.let { key to it } }.toMap(),
            textFiles = textFiles.mapNotNull { (name, file) -> file.read()?.let { name to it } }.toMap(),
            binaryFiles = binaryDirs.mapValues { (_, files) -> files.names().mapNotNull { n -> files.read(n)?.let { n to it } }.toMap() },
            passwordKey = passwordKey,
        )
        return seal(cbor.encodeToByteArray(BackupContent.serializer(), content), password)
    }

    /**
     * Checks [file] with [password] and stages the content for the next start, encrypted with
     * [localCipher] of this installation (the device key only changes on restore).
     */
    fun stageRestore(file: ByteArray, password: String, localCipher: LocalCipher): RestoreCheck {
        val plain = when (val opened = open(file, password)) {
            is Opened.Ok -> opened.plain
            Opened.NotABackup -> return RestoreCheck.NotABackup
            Opened.WrongPassword -> return RestoreCheck.WrongPassword
        }
        val content = runCatching { cbor.decodeFromByteArray(BackupContent.serializer(), plain) }.getOrNull()
            ?: return RestoreCheck.NotABackup
        if (content.format > FORMAT) return RestoreCheck.TooNew
        if (content.format < FORMAT) return RestoreCheck.TooOld
        pending.write(PENDING_NAME, localCipher.sealBytes(plain))
        return RestoreCheck.Staged(content.createdAtMillis)
    }

    fun hasPendingRestore(): Boolean = PENDING_NAME in pending.names()

    /** Signing key of the phone a restored backup came from, until [forgetPreviousDevice] (ADR 0093). */
    fun previousDeviceSeed(): ByteArray? = secrets.get(PREVIOUS_DEVICE)?.takeIf { it.size == 32 }

    fun forgetPreviousDevice() = secrets.put(PREVIOUS_DEVICE, null)

    /** Discard the staged backup without changing anything. */
    fun discardPendingRestore() = pending.delete(PENDING_NAME)

    /**
     * At start, before anything is read: restore the staged backup. [currentCipher] is the
     * key with which [stageRestore] staged it. `true` if a restore took place.
     */
    fun applyPendingRestore(currentCipher: () -> LocalCipher): Boolean {
        val sealed = pending.read(PENDING_NAME) ?: return false
        val plain = currentCipher().openBytes(sealed)
        val content = plain?.let { runCatching { cbor.decodeFromByteArray(BackupContent.serializer(), it) }.getOrNull() }
        if (content == null) {
            // Not readable (e.g. device restore in between): discard, change nothing.
            pending.delete(PENDING_NAME)
            return false
        }
        for (key in secretKeys()) secrets.put(key, null)
        for ((key, value) in content.secrets) secrets.put(key, value)
        restoreAsNewDevice(content.secrets)
        keyValues.replaceAll(content.keyValues)
        for ((name, file) in textFiles) file.write(content.textFiles[name] ?: "")
        for ((dir, files) in binaryDirs) {
            for (name in files.names()) files.delete(name)
            for ((name, bytes) in content.binaryFiles[dir].orEmpty()) files.write(name, bytes)
        }
        content.passwordKey?.let(restorePasswordKey)
        pending.delete(PENDING_NAME)
        return true
    }

    /**
     * ADR 0093: the restored phone is a new device. Its device keys (and the stream keys tied to it) are not taken
     * over; the core makes new ones at the start and adds the device to the person's list, so that the other own
     * devices ask „Warst du das?“ and a stolen old phone can be locked out. The local data stays readable through
     * [LocalKey.SEED]; the old signing key stays until the app asked about the old phone ([PREVIOUS_DEVICE]).
     */
    private fun restoreAsNewDevice(restored: Map<String, ByteArray>) {
        val oldX = restored[CoreSecretKeys.DEVICE_X] ?: return
        val oldSign = restored[CoreSecretKeys.DEVICE_SIGN] ?: return
        if (restored[LocalKey.SEED] == null) secrets.put(LocalKey.SEED, oldX)
        for (key in DEVICE_BOUND) secrets.put(key, null)
        secrets.put(PREVIOUS_DEVICE, oldSign)
    }

    sealed interface RestoreCheck {
        /** Fits; will be restored at the next start. */
        data class Staged(val createdAtMillis: Long) : RestoreCheck
        data object WrongPassword : RestoreCheck
        data object NotABackup : RestoreCheck

        /** From a newer app version; update the app first. */
        data object TooNew : RestoreCheck

        /** From before ANMP 1 (format 1): no longer readable, the circle must be set up again (ADR 0034 §4). */
        data object TooOld : RestoreCheck
    }

    private sealed interface Opened {
        class Ok(val plain: ByteArray) : Opened
        data object NotABackup : Opened
        data object WrongPassword : Opened
    }

    private fun seal(plain: ByteArray, password: String): ByteArray {
        val salt = secureRandomBytes(SALT_SIZE)
        val header = MAGIC + intBytes(ITERATIONS) + salt
        return header + gcm(password, salt, ITERATIONS).encryptBlocking(plain, header)
    }

    private fun open(file: ByteArray, password: String): Opened {
        val headerSize = MAGIC.size + 4 + SALT_SIZE
        if (file.size <= headerSize || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return Opened.NotABackup
        val iterations = readInt(file, MAGIC.size)
        if (iterations !in 1..MAX_ITERATIONS) return Opened.NotABackup
        val salt = file.copyOfRange(MAGIC.size + 4, headerSize)
        val header = file.copyOfRange(0, headerSize)
        val plain = runCatching {
            gcm(password, salt, iterations).decryptBlocking(file.copyOfRange(headerSize, file.size), header)
        }.getOrNull()
        return if (plain != null) Opened.Ok(plain) else Opened.WrongPassword
    }

    private fun gcm(password: String, salt: ByteArray, iterations: Int) = cryptoProvider.get(AES.GCM).keyDecoder()
        .decodeFromByteArrayBlocking(
            AES.Key.Format.RAW,
            cryptoProvider.get(PBKDF2)
                .secretDerivation(SHA256, iterations, 256.bits, salt)
                .deriveSecretToByteArrayBlocking(password.encodeToByteArray()),
        )
        .cipher()

    companion object {
        const val MIN_PASSWORD_LENGTH = 8

        /** Signing key of the phone the backup came from, until the question after restoring (ADR 0093). */
        const val PREVIOUS_DEVICE = "app.restore.previous-device"

        /** Secrets tied to one device, never taken over from a backup (ADR 0093). */
        private val DEVICE_BOUND = listOf(
            CoreSecretKeys.DEVICE_SIGN, CoreSecretKeys.DEVICE_X,
            app.anynear.anmp.core.AnmpCore.STREAM_SEED, app.anynear.anmp.core.AnmpCore.STREAM_TAG_KEY,
        )

        /**
         * Backup code (ADR 0093): made by the app like the code of the emergency card (128 bits), written down by the
         * person. The file format stays; the code in its written form is the password.
         */
        fun newCode(): String = app.anynear.anmp.core.RecoveryCode.format(app.anynear.anmp.core.RecoveryCode.newCode())

        /** What was typed: a backup code in any spelling becomes its written form; anything else is an old password. */
        fun normalize(typed: String): String =
            app.anynear.anmp.core.RecoveryCode.parse(typed)?.let { app.anynear.anmp.core.RecoveryCode.format(it) } ?: typed

        /** File extension of the backup file; without the brand name, so that it survives a renaming. */
        const val FILE_EXTENSION = "backup"
        const val MIME_TYPE = "application/octet-stream"

        /**
         * 2: since ANMP 1 and ANDP (ADR 0034–0036) with the keys and the state of the ANMP core
         * (`core.device.sign`, `core.device.x`, `core.inbox.owner`, `core.core.state`) and its folder `pmp`.
         * The app rejects backups with format 1.
         */
        internal const val FORMAT = 2

        /** Start of every backup file: "SB" and version, like `AN\x01` in ANMP. */
        private val MAGIC = byteArrayOf('S'.code.toByte(), 'B'.code.toByte(), 1)
        private const val ITERATIONS = 600_000
        private const val MAX_ITERATIONS = 10_000_000
        private const val SALT_SIZE = 16
        private const val PENDING_NAME = "pending"

        /** Folder for the staged backup. */
        const val PENDING_DIR = "restore"

        private val cbor = Cbor { ignoreUnknownKeys = true }

        private fun intBytes(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

        private fun readInt(b: ByteArray, at: Int) =
            ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
                ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)
    }
}

/** Content of a backup, encrypted as CBOR. New fields only with a default value, increment [format] on breaks. */
@Serializable
internal class BackupContent(
    /** Older backups do not write the default value 1; that is why it stays 1. */
    val format: Int = 1,
    val createdAtMillis: Long,
    val appVersion: String? = null,
    val keyValues: Map<String, String> = emptyMap(),
    val secrets: Map<String, ByteArray> = emptyMap(),
    val textFiles: Map<String, String> = emptyMap(),
    val binaryFiles: Map<String, Map<String, ByteArray>> = emptyMap(),
    /** Key of the passwords (ADR 0039); missing in backups without logins and from older versions. */
    val passwordKey: ByteArray? = null,
)

/**
 * Key-value store of the platform without encryption on top: all entries as they are on the
 * device, and replace all of them at once.
 */
interface RawKeyValueAccess {
    fun entries(): Map<String, String>

    /** Removes all previous entries and stores [values]. */
    fun replaceAll(values: Map<String, String>)
}

expect fun platformRawKeyValueAccess(): RawKeyValueAccess

/** Names of all entries in the protected store of the platform ([platformSecretStore]). */
expect fun platformSecretKeys(): Set<String>

/** Restarts the app so that a staged backup is restored (iOS: quits it). */
expect fun restartApp()

/** Delete the cache of the radio SDK (devices in range, databases `meshtastic…`); only before the SDK starts. */
expect fun deleteRadioCache()

/** Name of the device for „Meine Geräte“ (ADR 0042), e.g. the model "Pixel 9" or "iPad". */
expect fun platformDeviceName(): String
