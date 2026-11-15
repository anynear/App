package app.anynear.core

import app.anynear.attachments.BinaryFiles
import app.anynear.data.KeyValueStore
import app.anynear.data.LocalCipher
import app.anynear.data.SecretStore
import app.anynear.anmp.core.BlobStore as CoreBlobStore
import app.anynear.anmp.core.KeyValueStore as CoreKeyValueStore
import app.anynear.anmp.core.SecretStore as CoreSecretStore

/*
 * Platform interfaces of the ANMP core (ADR 0036) on top of the app's stores: secrets in the protected
 * storage, state such as read positions and network list in the encrypted key-value store, waiting
 * objects as files, additionally encrypted with the device key.
 */

/** Secrets of the core: device keys (`core.device.sign`, `core.device.x`), mailbox (`core.inbox.owner`), state (`core.core.state`). */
internal class CoreSecrets(private val secrets: SecretStore) : CoreSecretStore {
    override fun get(key: String): ByteArray? = secrets.get(key)

    override fun put(key: String, value: ByteArray?) = secrets.put(key, value)
}

internal class CoreKeyValues(private val store: KeyValueStore) : CoreKeyValueStore {
    override fun getString(key: String): String? = store.getString(key)

    override fun putString(key: String, value: String?) = store.putString(key, value)
}

/**
 * Files of the core (ciphertext of waiting attachments and images, own image) in the folder [DIR_NAME]. The names of
 * the core contain `/`; as a file name `~` stands for it.
 */
internal class CoreBlobs(private val files: BinaryFiles, private val cipher: LocalCipher) : CoreBlobStore {
    override suspend fun read(name: String): ByteArray? = files.read(fileName(name))?.let(cipher::openBytes)

    override suspend fun write(name: String, bytes: ByteArray) = files.write(fileName(name), cipher.sealBytes(bytes))

    override suspend fun delete(name: String) = files.delete(fileName(name))

    private fun fileName(name: String): String {
        require(name.isNotEmpty() && name.all { it.isLetterOrDigit() || it in "_.-/" }) { "Ungültiger Name" }
        return name.replace('/', '~')
    }

    companion object {
        const val DIR_NAME = "core"
    }
}

/** Names in the protected storage that the core creates itself (fresh start, ADR 0046). */
object CoreSecretKeys {
    const val DEVICE_SIGN = "core.device.sign"
    const val DEVICE_X = "core.device.x"
    const val INBOX_OWNER = "core.inbox.owner"
    const val STATE = app.anynear.anmp.core.AnmpCore.STATE_KEY

    val ALL = setOf(DEVICE_SIGN, DEVICE_X, INBOX_OWNER, STATE)
}
