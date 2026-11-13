package app.anynear

import app.anynear.attachments.BinaryFiles
import app.anynear.data.EncryptedKeyValueStore
import app.anynear.data.KeyValueStore
import app.anynear.data.RawKeyValueAccess
import app.anynear.data.SecretStore
import app.anynear.data.StateBackup
import app.anynear.data.TextFile
import app.anynear.core.LocalKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** ADR 0027: backup with password, restoring on a new installation or a new phone. */
class StateBackupTest {
    private val password = "ausgedachtes-Testpasswort"

    /** An installation: storage as on the device, without platform. */
    private class Device {
        val raw = RawStore()
        val secrets = Secrets()
        val history = MemoryFile()
        val attachments = MemoryFiles()
        val avatars = MemoryFiles()
        val pending = MemoryFiles()
        var restoredPasswordKey: ByteArray? = null
        val backup = StateBackup(
            keyValues = raw,
            secrets = secrets,
            secretKeys = { secrets.keys() },
            textFiles = mapOf("messages.v1" to history),
            binaryDirs = mapOf("attachments" to attachments, "avatars" to avatars),
            pending = pending,
            restorePasswordKey = { restoredPasswordKey = it },
        )

        /** As on app start: key of the data on the device from `core.device.x`, new if needed. */
        fun cipher() = LocalKey.cipher(secrets)

        fun settings(): KeyValueStore = EncryptedKeyValueStore(raw, cipher())

        /** Like AppGraph on start. */
        fun start(): Boolean = backup.applyPendingRestore { cipher() }
    }

    private fun filledDevice() = Device().apply {
        val cipher = cipher()
        settings().putString("own_name", "Testperson")
        // Keys and state of the ANMP core (ADR 0027): made-up values.
        secrets.put("pmp.device.sign", ByteArray(32) { 4 })
        secrets.put("pmp.inbox.owner", ByteArray(32) { 5 })
        secrets.put("pmp.core.state", "{}".encodeToByteArray())
        history.content = cipher.seal("""{"v":1,"messages":[]}""")
        attachments.write("blob-1", cipher.sealBytes(byteArrayOf(1, 2, 3)))
        avatars.write("me", cipher.sealBytes(byteArrayOf(9)))
    }

    @Test
    fun restoresEverythingOnANewDevice() {
        val old = filledDevice()
        val file = old.backup.create(password, createdAtMillis = 1_000, appVersion = "test")

        // New phone: app freshly installed, already has its own device key and something of its own.
        val new = Device()
        val ownCipher = new.cipher()
        new.settings().putString("own_name", "Neu")
        new.attachments.write("fremd", byteArrayOf(7))

        val check = new.backup.stageRestore(file, password, ownCipher)
        assertIs<StateBackup.RestoreCheck.Staged>(check)
        assertEquals(1_000, check.createdAtMillis)
        // Replaced only on the next start.
        assertEquals("Neu", new.settings().getString("own_name"))

        assertTrue(new.start())
        assertEquals(old.raw.entries(), new.raw.entries())
        assertEquals(old.secrets.keys(), new.secrets.keys())
        assertContentEquals(ByteArray(32) { 4 }, new.secrets.get("pmp.device.sign"))
        assertContentEquals(ByteArray(32) { 5 }, new.secrets.get("pmp.inbox.owner"))
        assertContentEquals("{}".encodeToByteArray(), new.secrets.get("pmp.core.state"))
        assertEquals(old.history.content, new.history.content)
        assertEquals(listOf("blob-1"), new.attachments.names())
        // The same identity as before so that history and settings are readable again.
        assertEquals("Testperson", new.settings().getString("own_name"))
        assertContentEquals(byteArrayOf(1, 2, 3), new.cipher().openBytes(new.attachments.read("blob-1")!!))
        assertFalse(new.backup.hasPendingRestore())
        // A second start changes nothing more.
        assertFalse(new.start())
    }

    /**
     * ADR 0093: the restored phone becomes a new device. Device and stream keys of the old phone are not taken over (the
     * core makes new ones), the local data stays readable through the seed, the old phone is remembered for the question
     * after restoring. With a backup code typed in any spelling, and with an old password as before.
     */
    @Test
    fun restoredPhoneIsANewDeviceAndKeepsItsData() {
        val old = Device().apply {
            secrets.put("core.device.sign", ByteArray(32) { 4 })
            secrets.put("core.device.x", ByteArray(32) { 6 })
            secrets.put("core.stream.seed", ByteArray(32) { 8 })
            secrets.put("core.person.key", ByteArray(32) { 5 })
            settings().putString("own_name", "Testperson")
            attachments.write("blob-1", cipher().sealBytes(byteArrayOf(1, 2, 3)))
        }
        val code = StateBackup.newCode()
        val file = old.backup.create(code, 1_000, "test")

        val new = Device()
        val typed = code.lowercase().replace("-", " ")
        assertEquals(StateBackup.RestoreCheck.WrongPassword, new.backup.stageRestore(file, typed, new.cipher()))
        assertIs<StateBackup.RestoreCheck.Staged>(new.backup.stageRestore(file, StateBackup.normalize(typed), new.cipher()))
        assertTrue(new.start())

        assertEquals(null, new.secrets.get("core.device.sign"))
        assertEquals(null, new.secrets.get("core.device.x"))
        assertEquals(null, new.secrets.get("core.stream.seed"))
        assertContentEquals(ByteArray(32) { 5 }, new.secrets.get("core.person.key"))
        assertContentEquals(ByteArray(32) { 4 }, new.backup.previousDeviceSeed())
        // The data of the backup is readable with the key it came with.
        assertEquals("Testperson", new.settings().getString("own_name"))
        assertContentEquals(byteArrayOf(1, 2, 3), new.cipher().openBytes(new.attachments.read("blob-1")!!))
        new.backup.forgetPreviousDevice()
        assertEquals(null, new.backup.previousDeviceSeed())

        // An older backup with a password of its own still opens.
        val older = old.backup.create(password, 1_000, "test")
        val other = Device()
        assertIs<StateBackup.RestoreCheck.Staged>(other.backup.stageRestore(older, StateBackup.normalize(password), other.cipher()))
    }

    /** Backups from before the fresh start (format 1, ADR 0034 §4) no longer fit and are rejected. */
    @Test
    fun backupFromBeforeTheFreshStartIsRefused() {
        val file = filledDevice().backup.create(password, 1_000, "test", format = 1)
        val new = Device()
        assertEquals(StateBackup.RestoreCheck.TooOld, new.backup.stageRestore(file, password, new.cipher()))
        assertFalse(new.backup.hasPendingRestore())
    }

    /** ADR 0039: the password key comes along only if passed at creation, and goes to the passwords on start. */
    @Test
    fun passwordKeyTravelsWithTheBackup() {
        val key = ByteArray(32) { 7 }
        val withKey = filledDevice().backup.create(password, 1L, null, passwordKey = key)
        val target = Device()
        assertIs<StateBackup.RestoreCheck.Staged>(target.backup.stageRestore(withKey, password, target.cipher()))
        assertTrue(target.start())
        assertContentEquals(key, target.restoredPasswordKey)

        val without = filledDevice().backup.create(password, 1L, null)
        val other = Device()
        other.backup.stageRestore(without, password, other.cipher())
        assertTrue(other.start())
        assertEquals(null, other.restoredPasswordKey)
    }

    @Test
    fun wrongPasswordChangesNothing() {
        val file = filledDevice().backup.create(password, 1, null)
        val new = Device()
        assertEquals(StateBackup.RestoreCheck.WrongPassword, new.backup.stageRestore(file, "falsches-Passwort", new.cipher()))
        assertFalse(new.backup.hasPendingRestore())
        assertFalse(new.start())
    }

    @Test
    fun changedFileIsRejected() {
        val file = filledDevice().backup.create(password, 1, null)
        file[file.size - 1] = (file[file.size - 1].toInt() xor 1).toByte()
        val new = Device()
        assertEquals(StateBackup.RestoreCheck.WrongPassword, new.backup.stageRestore(file, password, new.cipher()))
    }

    @Test
    fun otherFilesAreNoBackup() {
        val new = Device()
        val cipher = new.cipher()
        assertEquals(StateBackup.RestoreCheck.NotABackup, new.backup.stageRestore("%PDF-1.7 kein Backup".encodeToByteArray(), password, cipher))
        assertEquals(StateBackup.RestoreCheck.NotABackup, new.backup.stageRestore(ByteArray(0), password, cipher))
    }

    @Test
    fun samePasswordGivesDifferentFiles() {
        val device = filledDevice()
        assertNotEquals(device.backup.create(password, 1, null).toList(), device.backup.create(password, 1, null).toList())
    }

    @Test
    fun shortPasswordIsRefused() {
        assertFailsWith<IllegalArgumentException> { filledDevice().backup.create("kurz", 1, null) }
    }

    @Test
    fun discardKeepsCurrentState() {
        val file = filledDevice().backup.create(password, 1, null)
        val new = Device()
        new.settings().putString("own_name", "Neu")
        new.backup.stageRestore(file, password, new.cipher())
        new.backup.discardPendingRestore()
        assertFalse(new.start())
        assertEquals("Neu", new.settings().getString("own_name"))
    }

    @Test
    fun restoredFlagMarksTheStateAndTravelsWithIt() {
        // AppGraph marks after restoring; only then does the app offer to delete the old one (ADR 0025).
        val device = Device()
        val profile = app.anynear.circles.ProfileRepository(device.settings())
        assertFalse(profile.restoredFromBackup)
        profile.markRestoredFromBackup(5)
        assertTrue(app.anynear.circles.ProfileRepository(device.settings()).restoredFromBackup)
    }

    private class RawStore : RawKeyValueAccess, KeyValueStore {
        private val map = linkedMapOf<String, String>()

        override fun entries(): Map<String, String> = map.toMap()

        override fun replaceAll(values: Map<String, String>) {
            map.clear()
            map.putAll(values)
        }

        override fun getString(key: String): String? = map[key]

        override fun putString(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
    }

    private class Secrets : SecretStore {
        private val map = linkedMapOf<String, ByteArray>()

        fun keys(): Set<String> = map.keys.toSet()

        override fun get(key: String): ByteArray? = map[key]

        override fun put(key: String, value: ByteArray?) {
            if (value == null) map.remove(key) else map[key] = value
        }
    }

    private class MemoryFile(var content: String? = null) : TextFile {
        override fun read(): String? = content

        override fun write(content: String) {
            this.content = content
        }
    }

    private class MemoryFiles : BinaryFiles {
        private val map = linkedMapOf<String, ByteArray>()

        override fun read(name: String): ByteArray? = map[name]

        override fun write(name: String, content: ByteArray) {
            map[name] = content
        }

        override fun delete(name: String) {
            map.remove(name)
        }

        override fun names(): List<String> = map.keys.toList()
    }
}
