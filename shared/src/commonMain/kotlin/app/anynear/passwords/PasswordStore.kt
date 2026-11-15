package app.anynear.passwords

import app.anynear.attachments.BinaryFiles
import app.anynear.data.SecretStore
import app.anynear.data.secureRandomBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.io.encoding.Base64

/** State of the password area (ADR 0039, ADR 0048). */
sealed interface PasswordState {
    /** No device lock: without it there are no passwords. */
    data object NoDeviceLock : PasswordState

    /** Not set up yet. */
    data object NotSetUp : PasswordState

    data object Locked : PasswordState

    /** Unlocked; [logins] in the order of the file. */
    data class Unlocked(val logins: List<Login>) : PasswordState

    /**
     * The logins come from another device of one's own, the key for them not yet: it arrives as soon as they are
     * opened there once (ADR 0048).
     */
    data object WaitingForKey : PasswordState

    /** There are logins but no valid key any more (device lock turned off, key lost). */
    data object Unreadable : PasswordState
}

/**
 * Logins on this phone and, via the app, on the relays (ADR 0048). Unlocked, it keeps the key and the
 * logins in memory only; [lock] discards both. Reads and writes blocking, the file is small. All devices
 * of a person have the same key `pv`; states of one's own devices are merged, never simply replaced.
 */
class PasswordStore(
    private val files: BinaryFiles,
    private val keys: PasswordKeyStore,
    private val secrets: SecretStore,
    /**
     * Unlocked, after every change: identifiers of the logins for the system's suggestions (ADR 0052, item 4), never
     * passwords, codes or keys.
     */
    private val publishIdentities: (List<Login>) -> Unit = {},
    private val now: () -> Long,
) {
    private val _state = MutableStateFlow<PasswordState>(PasswordState.Locked)
    val state: StateFlow<PasswordState> = _state.asStateFlow()

    private var key: ByteArray? = null
    private var vault: Vault? = null

    /** State from the relays that only the next unlock can check and merge. */
    private var pending: Remote? = null

    private class Remote(val data: ByteArray, val changed: Long, val key: ByteArray?)

    init {
        refresh()
    }

    fun hasVault(): Boolean = VaultFile.NAME in files.names()

    /** Determine the state anew, e.g. after coming back from the system setting. Does not lock. */
    fun refresh() {
        if (key != null) return
        adoptRestoredKey()
        _state.value = when {
            !keys.deviceSecure() ->
                if (hasVault() && keys.state() != PasswordKeyState.PRESENT && secrets.get(PENDING_KEY) == null) PasswordState.Unreadable
                else PasswordState.NoDeviceLock
            keys.state() == PasswordKeyState.PRESENT -> PasswordState.Locked
            keys.state() == PasswordKeyState.INVALIDATED -> PasswordState.Unreadable
            hasVault() || pending != null -> PasswordState.WaitingForKey
            else -> PasswordState.NotSetUp
        }
    }

    /**
     * Create a new key. Afterwards the area is locked and is opened with the device lock. If there are already
     * logins (also from another device), it does not create one: a new key would make them unreadable.
     */
    fun setUp(): Boolean {
        if (hasVault() || hasKey() || pending != null) return false.also { refresh() }
        val fresh = secureRandomBytes(VaultFile.KEY_SIZE)
        if (!keys.store(fresh)) return false.also { refresh() }
        rememberKeyId(fresh)
        refresh()
        return true
    }

    /** Result of the device lock ([rememberPasswordUnlock]). */
    fun onUnlock(result: UnlockResult) {
        when (result) {
            is UnlockResult.Unlocked -> open(result.key)?.let { onKeyAvailable?.invoke(it) }
            UnlockResult.KeyLost -> _state.value = if (pending?.key != null) PasswordState.WaitingForKey else PasswordState.Unreadable
            UnlockResult.NoDeviceLock, UnlockResult.Cancelled -> refresh()
        }
    }

    /**
     * Unlocked: open the own file and a waiting state from the relays, merge them and store them under the key
     * of the relays if that is a different one (this happens when two devices have each created one).
     * Result: the key that applies now, `null` if nothing is readable.
     */
    private fun open(own: ByteArray): ByteArray? {
        val remote = pending
        val candidates = listOfNotNull(own, remote?.key)
        val local = files.read(VaultFile.NAME)?.let { data -> candidates.firstNotNullOfOrNull { VaultFile.openVault(it, data) } }
        val fromRelays = remote?.let { r -> candidates.firstNotNullOfOrNull { VaultFile.openVault(it, r.data) } }
        // New passkeys from the iOS extension or the Android provider (ADR 0052).
        val incoming = files.read(VaultFile.INCOMING)?.let { data -> candidates.firstNotNullOfOrNull { VaultFile.openVault(it, data) } }
        if (local == null && hasVault() && fromRelays == null) {
            if (key == null) _state.value = PasswordState.Unreadable
            return null
        }
        // A state that none of the keys opens is ignored.
        val target = remote?.key?.takeIf { fromRelays != null } ?: own
        if (!target.contentEquals(own) && !keys.store(target)) {
            if (key == null) _state.value = PasswordState.Unreadable
            return null
        }
        rememberKeyId(target)
        pending = null
        key = target.copyOf()
        val merged = (local ?: Vault()).merge(fromRelays ?: Vault()).merge(incoming ?: Vault())
        if (incoming != null) files.delete(VaultFile.INCOMING)
        if (merged != local || !target.contentEquals(own) || (fromRelays != null && (merged != fromRelays || remote.key == null))) {
            write(merged, after = remote?.changed ?: 0)
        } else {
            if (remote != null && remote.changed > vaultChanged()) secrets.put(CHANGED_KEY, remote.changed.toString().encodeToByteArray())
            show(merged)
        }
        if (secrets.get(RENEW_KEY) != null) {
            applyRenewal()
            return key?.copyOf()
        }
        return target.copyOf()
    }

    fun lock() {
        key?.fill(0)
        key = null
        vault = null
        refresh()
    }

    /**
     * After a device was locked out (ADR 0042 §6): a new key `pv`, so that the thief's copy of the old key opens
     * nothing that is saved from now on. Unlocked it happens at once, otherwise at the next unlock. The logins are
     * sealed anew and go to the relays with the new key; the own other devices take it from there.
     */
    fun renewKey() {
        if (!hasKey() && !hasVault()) return
        secrets.put(RENEW_KEY, "1".encodeToByteArray())
        if (key != null) applyRenewal()
    }

    private fun applyRenewal() {
        val current = vault ?: return
        if (secrets.get(RENEW_KEY) == null || key == null) return
        val fresh = secureRandomBytes(VaultFile.KEY_SIZE)
        if (!keys.store(fresh)) return
        rememberKeyId(fresh)
        key?.fill(0)
        key = fresh.copyOf()
        secrets.put(RENEW_KEY, null)
        write(current)
        onKeyAvailable?.invoke(fresh.copyOf())
    }

    /** Unlocked: the key, e.g. to put a state of one's own on the relays with it; otherwise `null`. */
    fun unlockedKey(): ByteArray? = key?.copyOf()

    private fun show(v: Vault) {
        vault = v
        _state.value = PasswordState.Unlocked(v.logins)
        publishIdentities(v.logins)
    }

    /** New or changed (same [Login.id]); one-time code and passkeys stay. */
    fun save(id: String?, website: String, username: String, password: String): Login? {
        val existing = vault?.logins?.firstOrNull { it.id == id }
        val login = (existing ?: Login(newId(), "", "", "", 0)).copy(website = website.trim(), username = username.trim(), password = password)
        return put(login)
    }

    /** Set the one-time code ([otp] as a link, [Otp.uri]) or remove it (`null`). */
    fun setOtp(id: String, otp: String?): Login? = vault?.logins?.firstOrNull { it.id == id }?.let { put(it.copy(otp = otp)) }

    /** Remove a passkey; a login without a password and without a passkey disappears with it. */
    fun removePasskey(id: String, credentialId: String) {
        val login = vault?.logins?.firstOrNull { it.id == id } ?: return
        val rest = login.copy(passkeys = login.passkeys.filterNot { it.credentialId == credentialId })
        if (rest.password.isEmpty() && rest.passkeys.isEmpty()) delete(id) else put(rest)
    }

    private fun put(login: Login): Login? {
        val current = vault ?: return null
        val stamped = login.copy(changed = nextChange(current.logins.firstOrNull { it.id == login.id }?.changed))
        val logins = if (current.logins.any { it.id == stamped.id }) current.logins.map { if (it.id == stamped.id) stamped else it } else current.logins + stamped
        return stamped.takeIf { write(Vault(logins, current.removed.filterNot { it.id == stamped.id })) }
    }

    /**
     * Take over new passkeys from [VaultFile.INCOMING] with the key [k] that the Android provider has after the
     * device lock (ADR 0052). A locked area stays locked afterwards; the state goes to the relays.
     */
    fun mergeIncoming(k: ByteArray) {
        if (VaultFile.INCOMING !in files.names()) return
        val own = key
        if (own != null) {
            open(own)
        } else if (open(k) != null) {
            lock()
        }
    }

    fun delete(id: String) {
        val current = vault ?: return
        val gone = current.logins.firstOrNull { it.id == id } ?: return
        write(Vault(current.logins - gone, current.removed + Removed(id, nextChange(gone.changed))))
    }

    /**
     * Discard everything on this device and start over when the logins can no longer be read or the key
     * does not arrive. Older states from the relays are ignored afterwards; one's other devices merge their logins
     * with the new state the next time they are opened.
     */
    fun reset() {
        lock()
        pending = null
        files.delete(VaultFile.NAME)
        keys.delete()
        secrets.put(PENDING_KEY, null)
        secrets.put(KEY_ID, null)
        secrets.put(CHANGED_KEY, now().toString().encodeToByteArray())
        refresh()
    }

    /** For adding a device (ADR 0039, item 5): key after the device lock, `null` without logins. */
    fun keyForHandover(unlocked: ByteArray): ByteArray? = unlocked.takeIf { hasVault() }

    /** Encrypted file of the logins, for another device of one's own (ADR 0042 §5); `null` without logins. */
    fun vaultBytes(): ByteArray? = files.read(VaultFile.NAME)

    /** Take over the file of the logins from another device of one's own; the key is brought by [restoreKey]. */
    fun restoreVault(bytes: ByteArray) {
        files.write(VaultFile.NAME, bytes)
        refresh()
    }

    /**
     * New file written ([write]): content (encrypted with `pv`), timestamp and `pv`. The app puts it into the
     * password space on the relays, with the key, so that every device of one's own can open it (ADR 0048).
     */
    var onVaultChanged: ((data: ByteArray, changed: Long, key: ByteArray) -> Unit)? = null

    /** Unlocked: the key `pv`, so that the app can store it for the emergency card and one's own devices. */
    var onKeyAvailable: ((key: ByteArray) -> Unit)? = null

    /** Is there a key `pv` on this device (also one waiting for a device lock)? */
    fun hasKey(): Boolean = keys.state() == PasswordKeyState.PRESENT || secrets.get(PENDING_KEY) != null

    /** When the file on this device was last changed or taken over (ms), 0 without one. */
    fun vaultChanged(): Long = secrets.get(CHANGED_KEY)?.decodeToString()?.toLongOrNull() ?: 0

    /**
     * State from the relays ([remoteKey]: the key that is stored there with it). `true` if something changed here.
     * - Unlocked: merge; what is new here goes straight back to the relays.
     * - Without a key on this device: take over state and key, afterwards the device lock opens it.
     * - Locked with the same key: the newer state replaces the file (for autofill).
     * - Locked with a different or unknown key: waits for the unlock, the own file stays readable.
     */
    fun takeRemote(data: ByteArray, changed: Long, remoteKey: ByteArray? = null): Boolean {
        val own = key
        if (own != null) {
            pending = Remote(data, changed, remoteKey)
            open(own)
            return true
        }
        if (!hasKey() || keys.state() == PasswordKeyState.INVALIDATED) {
            if (remoteKey == null) {
                if (hasVault() && changed <= vaultChanged()) return false
                files.write(VaultFile.NAME, data)
                secrets.put(CHANGED_KEY, changed.toString().encodeToByteArray())
                refresh()
                return true
            }
            if (changed <= vaultChanged() && !hasVault()) return false
            files.write(VaultFile.NAME, data)
            secrets.put(CHANGED_KEY, changed.toString().encodeToByteArray())
            restoreKey(remoteKey)
            return true
        }
        if (remoteKey == null || secrets.get(KEY_ID)?.decodeToString() != VaultFile.keyId(remoteKey)) {
            pending = Remote(data, changed, remoteKey)
            return false
        }
        if (changed <= vaultChanged()) return false
        files.write(VaultFile.NAME, data)
        secrets.put(CHANGED_KEY, changed.toString().encodeToByteArray())
        refresh()
        return true
    }

    /**
     * Key from another device: store it. If that is not possible yet (no device lock
     * on the new phone), it waits in the app's protected storage until one is set up.
     */
    fun restoreKey(restored: ByteArray) {
        keys.delete()
        if (!keys.store(restored)) secrets.put(PENDING_KEY, restored)
        rememberKeyId(restored)
        refresh()
    }

    private fun adoptRestoredKey() {
        val pendingKey = secrets.get(PENDING_KEY) ?: return
        if (keys.deviceSecure() && keys.store(pendingKey)) secrets.put(PENDING_KEY, null)
    }

    private fun rememberKeyId(k: ByteArray) = secrets.put(KEY_ID, VaultFile.keyId(k).encodeToByteArray())

    /** Timestamp of a change: now, but never earlier than the previous one, even if the clock is slow. */
    private fun nextChange(previous: Long?): Long = maxOf(now(), (previous ?: 0) + 1)

    /** Writes [next] and hands it to the app for the relays; [after]: the new state must be newer than this one. */
    private fun write(next: Vault, after: Long = 0): Boolean {
        val k = key ?: return false
        val sealed = VaultFile.seal(k, next)
        val changed = maxOf(now(), vaultChanged() + 1, after + 1)
        files.write(VaultFile.NAME, sealed)
        secrets.put(CHANGED_KEY, changed.toString().encodeToByteArray())
        show(next)
        onVaultChanged?.invoke(sealed, changed, k.copyOf())
        return true
    }

    private fun newId() = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(secureRandomBytes(12))

    companion object {
        /** Restored key that is still waiting for a device lock (the app's protected storage). */
        internal const val PENDING_KEY = "passwords.pending-key"

        /** Timestamp of the file (ms), decides between one's own devices which state is newer (ADR 0042). */
        private const val CHANGED_KEY = "passwords.vault-changed"

        /** Fingerprint of the key here ([VaultFile.keyId]), to assign states from the relays without unlocking. */
        private const val KEY_ID = "passwords.key-id"

        /** Set after a lock-out: the next unlock makes a new key `pv` ([renewKey]). */
        internal const val RENEW_KEY = "passwords.renew-key"

        const val DIR_NAME = "passwords"
    }
}
