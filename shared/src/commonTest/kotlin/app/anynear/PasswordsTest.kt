package app.anynear

import app.anynear.attachments.BinaryFiles
import app.anynear.passwords.Login
import app.anynear.passwords.LoginMatch
import app.anynear.passwords.PasswordKeyState
import app.anynear.passwords.PasswordKeyStore
import app.anynear.passwords.PasswordState
import app.anynear.passwords.PasswordStore
import app.anynear.passwords.Removed
import app.anynear.passwords.UnlockResult
import app.anynear.passwords.Vault
import app.anynear.passwords.VaultFile
import app.anynear.passwords.Webauthn
import app.anynear.core.cryptoProvider
import dev.whyoleg.cryptography.algorithms.AES
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ADR 0039, ADR 0048: passwords with their own key, matching to the website, states of the area. */
class PasswordsTest {
    private val login = Login("a", "https://www.meinebank.example/login", "name@example.com", "ausgedacht-1", 1L)

    @Test
    fun vaultOpensOnlyWithItsKey() {
        val key = ByteArray(32) { 1 }
        val sealed = VaultFile.seal(key, listOf(login))
        assertEquals(listOf(login), VaultFile.open(key, sealed))
        assertNull(VaultFile.open(ByteArray(32) { 2 }, sealed))
        assertNull(VaultFile.open(key, sealed.copyOf(sealed.size - 1)))
    }

    @Test
    fun matchesSameDomainAndSubdomainsOnly() {
        assertEquals("meinebank.example", LoginMatch.host(login.website))
        assertTrue(LoginMatch.matches(login, "meinebank.example"))
        assertTrue(LoginMatch.matches(login, "login.meinebank.example"))
        assertTrue(LoginMatch.matches(login, "https://WWW.MeineBank.example/"))
        assertFalse(LoginMatch.matches(login, "meinebank.example.evil.example"))
        assertFalse(LoginMatch.matches(login, "falsche-meinebank.example"))
        assertFalse(LoginMatch.matches(login, "example"))
        val sub = login.copy(website = "konto.meinebank.example")
        assertFalse(LoginMatch.matches(sub, "meinebank.example"))
        assertNull(LoginMatch.host("kein host mit leerzeichen"))
    }

    @Test
    fun matchingLoginsComeFirst() {
        val other = Login("b", "anderes.example", "", "x", 1L)
        assertEquals(listOf(login, other), LoginMatch.sorted(listOf(other, login), "meinebank.example"))
    }

    /** After a lock-out (ADR 0042 §6) the key `pv` is new: at once if unlocked, else at the next unlock. */
    @Test
    fun renewKeyAfterLockOut() {
        val keys = FakeKeys()
        val files = Files()
        val store = PasswordStore(files, keys, InMemorySecretStore()) { 5L }
        var pushed: ByteArray? = null
        store.onVaultChanged = { _, _, k -> pushed = k }
        assertTrue(store.setUp())
        val old = keys.key!!.copyOf()
        store.onUnlock(UnlockResult.Unlocked(old.copyOf()))
        store.save(null, "meinebank.example", "name", "geheim")
        store.lock()

        // Locked: nothing yet, the next unlock makes the new key.
        store.renewKey()
        assertContentEquals(old, keys.key)
        store.onUnlock(UnlockResult.Unlocked(old.copyOf()))
        val fresh = keys.key!!.copyOf()
        assertFalse(fresh.contentEquals(old))
        assertContentEquals(fresh, pushed)
        assertEquals("geheim", VaultFile.open(fresh, files.read(VaultFile.NAME)!!)!!.single().password)
        assertNull(VaultFile.open(old, files.read(VaultFile.NAME)!!))

        // Unlocked: at once, and only once.
        store.renewKey()
        val third = keys.key!!.copyOf()
        assertFalse(third.contentEquals(fresh))
        store.lock()
        store.onUnlock(UnlockResult.Unlocked(third.copyOf()))
        assertContentEquals(third, keys.key)
    }

    @Test
    fun setUpUnlockSaveAndLock() {
        val keys = FakeKeys()
        val files = Files()
        val store = PasswordStore(files, keys, InMemorySecretStore()) { 5L }
        assertEquals(PasswordState.NotSetUp, store.state.value)
        assertTrue(store.setUp())
        assertEquals(PasswordState.Locked, store.state.value)
        // While locked nothing is saved.
        assertNull(store.save(null, "x.example", "", "p"))

        store.onUnlock(UnlockResult.Unlocked(keys.key!!.copyOf()))
        val saved = store.save(null, " meinebank.example ", "name", "geheim")!!
        assertEquals("meinebank.example", saved.website)
        assertEquals(listOf(saved), (store.state.value as PasswordState.Unlocked).logins)
        store.save(saved.id, "meinebank.example", "name", "neu")
        assertEquals("neu", (store.state.value as PasswordState.Unlocked).logins.single().password)

        store.lock()
        assertEquals(PasswordState.Locked, store.state.value)
        assertTrue(store.hasVault())
        // The file is readable only with the key.
        assertEquals("neu", VaultFile.open(keys.key!!, files.read(VaultFile.NAME)!!)!!.single().password)

        store.onUnlock(UnlockResult.Cancelled)
        assertEquals(PasswordState.Locked, store.state.value)
    }

    @Test
    fun withoutDeviceLockNothingStarts() {
        val keys = FakeKeys(secure = false)
        val store = PasswordStore(Files(), keys, InMemorySecretStore()) { 0L }
        assertEquals(PasswordState.NoDeviceLock, store.state.value)
        assertFalse(store.setUp())
    }

    @Test
    fun lostKeyMakesVaultUnreadableUntilStartOver() {
        val keys = FakeKeys()
        val files = Files()
        val store = PasswordStore(files, keys, InMemorySecretStore()) { 0L }
        store.setUp()
        store.onUnlock(UnlockResult.Unlocked(keys.key!!))
        store.save(null, "a.example", "", "p")
        store.lock()
        keys.state = PasswordKeyState.INVALIDATED
        store.refresh()
        assertEquals(PasswordState.Unreadable, store.state.value)
        store.reset()
        assertFalse(store.hasVault())
        assertEquals(PasswordState.NotSetUp, store.state.value)
    }

    @Test
    fun restoredKeyWaitsForADeviceLock() {
        val keys = FakeKeys(secure = false)
        val secrets = InMemorySecretStore()
        val files = Files()
        val key = ByteArray(32) { 3 }
        files.write(VaultFile.NAME, VaultFile.seal(key, listOf(login)))
        val store = PasswordStore(files, keys, secrets) { 0L }
        store.restoreKey(key)
        assertEquals(PasswordState.NoDeviceLock, store.state.value)
        keys.secure = true
        store.refresh()
        assertEquals(PasswordState.Locked, store.state.value)
        assertContentEquals(key, keys.key)
        assertNull(secrets.get("passwords.pending-key"))
        store.onUnlock(UnlockResult.Unlocked(key))
        assertIs<PasswordState.Unlocked>(store.state.value)
    }

    @Test
    fun newerVaultFromAnotherDeviceIsMergedAndChangesArePublished() {
        val keys = FakeKeys()
        var t = 100L
        val store = PasswordStore(Files(), keys, InMemorySecretStore()) { t }
        val published = mutableListOf<Long>()
        store.onVaultChanged = { _, changed, _ -> published += changed }
        assertTrue(store.setUp())
        val key = keys.key!!.copyOf()
        store.onUnlock(UnlockResult.Unlocked(key.copyOf()))
        store.save(null, "a.example", "", "1")
        // Already the first unlock stores the (empty) state with the key so that other devices get it.
        assertEquals(listOf(100L, 101L), published)

        // A state from another device is merged and shows up at once; the result goes up again.
        val other = VaultFile.seal(key, listOf(Login("x", "b.example", "", "2", 150)))
        assertTrue(store.takeRemote(other, 150, key))
        assertEquals(listOf("a.example", "b.example"), (store.state.value as PasswordState.Unlocked).logins.map { it.website })
        assertEquals(151L, published.last())
        // The next own change is newer than the adopted state, even if the clock runs slow.
        t = 120
        store.save(null, "c.example", "", "3")
        assertEquals(152L, published.last())
    }

    @Test
    fun secondDeviceTakesTheKeyFromTheRelaysInsteadOfStartingOver() {
        val keyA = ByteArray(32) { 4 }
        val keys = FakeKeys()
        val store = PasswordStore(Files(), keys, InMemorySecretStore()) { 10L }
        assertEquals(PasswordState.NotSetUp, store.state.value)
        // State without a key (older device): wait, not „nicht mehr lesbar“, and no new key.
        assertTrue(store.takeRemote(VaultFile.seal(keyA, listOf(login)), 5))
        assertEquals(PasswordState.WaitingForKey, store.state.value)
        assertFalse(store.setUp())
        // With a key: adopted, the device lock opens it.
        assertTrue(store.takeRemote(VaultFile.seal(keyA, listOf(login)), 6, keyA))
        assertEquals(PasswordState.Locked, store.state.value)
        assertContentEquals(keyA, keys.key)
        store.onUnlock(UnlockResult.Unlocked(keys.key!!.copyOf()))
        assertEquals(listOf(login), (store.state.value as PasswordState.Unlocked).logins)
    }

    @Test
    fun twoDevicesWithOwnKeysEndUpWithTheKeyOfTheRelaysAndAllLogins() {
        val keyA = ByteArray(32) { 4 }
        val keys = FakeKeys()
        val files = Files()
        val store = PasswordStore(files, keys, InMemorySecretStore()) { 10L }
        val published = mutableListOf<ByteArray>()
        store.onVaultChanged = { _, _, key -> published += key }
        store.setUp()
        val keyB = keys.key!!.copyOf()
        store.onUnlock(UnlockResult.Unlocked(keyB.copyOf()))
        store.save(null, "hier.example", "", "b")
        store.lock()

        // Locked: the foreign state does not replace the own file, it stays readable.
        val fromA = VaultFile.seal(keyA, listOf(login))
        assertFalse(store.takeRemote(fromA, 50, keyA))
        assertEquals("hier.example", VaultFile.open(keyB, files.read(VaultFile.NAME)!!)!!.single().website)
        // On unlock: merged, stored under the relays' key and uploaded again.
        store.onUnlock(UnlockResult.Unlocked(keyB.copyOf()))
        assertEquals(setOf("hier.example", login.website), (store.state.value as PasswordState.Unlocked).logins.map { it.website }.toSet())
        assertContentEquals(keyA, keys.key)
        assertContentEquals(keyA, published.last())
        assertEquals(2, VaultFile.open(keyA, files.read(VaultFile.NAME)!!)!!.size)
    }

    @Test
    fun fileFromAnotherKeyBecomesReadableOnceThatKeyArrives() {
        // This is what it looked like after the earlier bug: file from the first device, key from the second.
        val keyA = ByteArray(32) { 4 }
        val keys = FakeKeys()
        val files = Files()
        files.write(VaultFile.NAME, VaultFile.seal(keyA, listOf(login)))
        val keyB = ByteArray(32) { 5 }
        keys.store(keyB)
        val store = PasswordStore(files, keys, InMemorySecretStore()) { 10L }
        assertFalse(store.takeRemote(VaultFile.seal(keyA, listOf(login)), 20, keyA))
        store.onUnlock(UnlockResult.Unlocked(keyB.copyOf()))
        assertEquals(listOf(login), (store.state.value as PasswordState.Unlocked).logins)
        assertContentEquals(keyA, keys.key)
    }

    @Test
    fun deletedLoginsStayDeletedWhenMerging() {
        val keys = FakeKeys()
        var t = 100L
        val store = PasswordStore(Files(), keys, InMemorySecretStore()) { t }
        store.setUp()
        val key = keys.key!!.copyOf()
        store.onUnlock(UnlockResult.Unlocked(key.copyOf()))
        val a = store.save(null, "a.example", "", "1")!!
        val older = VaultFile.seal(key, listOf(a))
        t = 200
        store.delete(a.id)
        // An older state containing the login does not bring it back, a later change does.
        store.takeRemote(older, 150, key)
        assertEquals(emptyList(), (store.state.value as PasswordState.Unlocked).logins)
        store.takeRemote(VaultFile.seal(key, listOf(a.copy(password = "neu", changed = 300))), 300, key)
        assertEquals("neu", (store.state.value as PasswordState.Unlocked).logins.single().password)
    }

    @Test
    fun mergeIsTheSameOnBothSides() {
        val one = Vault(listOf(login, Login("b", "b.example", "", "1", 5)), listOf(Removed("c", 9)))
        val two = Vault(listOf(login.copy(password = "anders", changed = 2), Login("c", "c.example", "", "1", 3)), listOf(Removed("b", 6)))
        assertEquals(one.merge(two).logins.toSet(), two.merge(one).logins.toSet())
        assertEquals(listOf("anders"), one.merge(two).logins.map { it.password })
    }

    @Test
    fun vaultStaysVersionOneWithoutCodesOrPasskeys() {
        val key = ByteArray(32) { 1 }
        val plain = VaultFile.seal(key, listOf(login))
        val withCode = VaultFile.seal(key, listOf(login.copy(otp = "otpauth://totp/X?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")))
        assertEquals(1, version(key, plain))
        assertEquals(2, version(key, withCode))
        assertEquals("otpauth://totp/X?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", VaultFile.open(key, withCode)!!.single().otp)
    }

    private fun version(key: ByteArray, sealed: ByteArray): Int {
        val plain = cryptoProvider.get(AES.GCM).keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key).cipher().decryptBlocking(sealed)
        return plain.decodeToString().substringAfter("\"v\":").takeWhile { it.isDigit() }.toInt()
    }

    @Test
    fun codeAndPasskeyStayWhenEditingTheLogin() {
        val keys = FakeKeys()
        val store = PasswordStore(Files(), keys, InMemorySecretStore()) { 5L }
        store.setUp()
        store.onUnlock(UnlockResult.Unlocked(keys.key!!.copyOf()))
        val saved = store.save(null, "meinebank.example", "name", "geheim")!!
        store.setOtp(saved.id, "otpauth://totp/X?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        val edited = store.save(saved.id, "meinebank.example", "name", "neu")!!
        assertEquals("otpauth://totp/X?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", edited.otp)
        store.setOtp(saved.id, null)
        assertNull((store.state.value as PasswordState.Unlocked).logins.single().otp)
    }

    @Test
    fun passkeyFromOutsideArrivesWithTheNextUnlock() {
        val keys = FakeKeys()
        val files = Files()
        val published = mutableListOf<List<Login>>()
        val store = PasswordStore(files, keys, InMemorySecretStore(), { published += it }) { 5L }
        store.setUp()
        store.onUnlock(UnlockResult.Unlocked(keys.key!!.copyOf()))
        store.save(null, "meinebank.example", "name", "geheim")
        store.lock()

        // The Android provider or the iOS extension stores a new passkey for the same user name.
        val passkey = Webauthn.create("meinebank.example", byteArrayOf(1, 2, 3), "NAME", 7L).passkey
        val target = LoginMatch.withPasskey(VaultFile.readAll(files, keys.key!!)!!.logins, passkey, "neu", 7L)
        assertTrue(VaultFile.addIncoming(files, keys.key!!, target))
        assertEquals(listOf(passkey), VaultFile.readAll(files, keys.key!!)!!.logins.single().passkeys)

        store.mergeIncoming(keys.key!!.copyOf())
        assertEquals(PasswordState.Locked, store.state.value)
        assertFalse(VaultFile.INCOMING in files.names())
        store.onUnlock(UnlockResult.Unlocked(keys.key!!.copyOf()))
        val only = (store.state.value as PasswordState.Unlocked).logins.single()
        assertEquals("geheim", only.password)
        assertEquals(listOf(passkey), only.passkeys)
        assertEquals(listOf(passkey), published.last().single().passkeys)

        // Last passkey of a login without a password gone: login gone.
        val bare = store.save(null, "nur-passkey.example", "x", "")!!
        store.save(bare.id, "nur-passkey.example", "x", "")
        assertEquals(2, (store.state.value as PasswordState.Unlocked).logins.size)
        store.removePasskey(only.id, passkey.credentialId)
        assertEquals("geheim", (store.state.value as PasswordState.Unlocked).logins.first { it.id == only.id }.password)
    }

    @Test
    fun passkeysMatchTheExactSiteOnly() {
        val passkey = Webauthn.create("meinebank.example", byteArrayOf(1), "name", 1L).passkey
        val logins = listOf(login.copy(passkeys = listOf(passkey)))
        assertEquals(1, LoginMatch.passkeys(logins, "MeineBank.example").size)
        assertTrue(LoginMatch.passkeys(logins, "login.meinebank.example").isEmpty())
        assertTrue(LoginMatch.passkeys(logins, "example").isEmpty())
    }

    private class FakeKeys(var secure: Boolean = true) : PasswordKeyStore {
        var key: ByteArray? = null
        var state = PasswordKeyState.NONE

        override fun deviceSecure() = secure
        override fun state() = state
        override fun store(key: ByteArray): Boolean {
            if (!secure) return false
            this.key = key.copyOf()
            state = PasswordKeyState.PRESENT
            return true
        }
        override fun delete() {
            key = null
            state = PasswordKeyState.NONE
        }
    }

    private class Files : BinaryFiles {
        private val map = linkedMapOf<String, ByteArray>()
        override fun read(name: String) = map[name]
        override fun write(name: String, content: ByteArray) {
            map[name] = content
        }
        override fun delete(name: String) {
            map.remove(name)
        }
        override fun names() = map.keys.toList()
    }
}
