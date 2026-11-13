package app.anynear.contacts

import app.anynear.core.b64
import app.anynear.data.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import app.anynear.mesh.RadioContacts

class ContactRepository(private val store: KeyValueStore) {
    private val _contacts = MutableStateFlow(load())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    sealed interface ImportResult {
        data class Added(val contact: Contact) : ImportResult

        data class AlreadyKnown(val contact: Contact) : ImportResult

        /**
         * Same device ID, but a different key. Is deliberately not taken over silently:
         * this can be a replacement device, a factory reset or a forgery attempt.
         */
        data class KeyMismatch(val existing: Contact) : ImportResult

        data object OwnDevice : ImportResult

        data class Invalid(val reason: String) : ImportResult
    }

    fun find(nodeNum: Int): Contact? = _contacts.value.firstOrNull { it.nodeNum == nodeNum }

    /**
     * Takes over a radio contact that came along in a contact or invitation link.
     * A different key for the same radio is not taken over silently.
     */
    fun add(contact: Contact, ownNodeNum: Int?): ImportResult {
        if (contact.nodeNum == ownNodeNum) return ImportResult.OwnDevice
        val existing = find(contact.nodeNum)
        if (existing != null) {
            val oldKey = existing.publicKey
            val newKey = contact.publicKey
            return if (oldKey != null && newKey != null && oldKey != newKey) {
                ImportResult.KeyMismatch(existing)
            } else {
                ImportResult.AlreadyKnown(existing)
            }
        }
        save(_contacts.value + contact)
        return ImportResult.Added(contact)
    }

    fun remove(nodeNum: Int) {
        save(_contacts.value.filterNot { it.nodeNum == nodeNum })
    }

    private fun save(list: List<Contact>) {
        store.putString(KEY, list.joinToString("\n") { encode(it) })
        _contacts.value = list
    }

    private fun load(): List<Contact> =
        store.getString(KEY).orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull(::decode)
            .toList()

    companion object {
        // v2: per line Base64url of the Meshtastic SharedContact, without the former prefix `PINA1:` (ADR 0034).
        private const val KEY = "contacts.v2"

        /** Radio contact as bytes, as it goes along in the contact and invitation link (field "radio" in the ANMP link). */
        fun bytesOf(contact: Contact): ByteArray = contact.bytes.toByteArray()

        /** Radio contact from a link; `null` if not readable or without a radio. */
        fun fromBytes(bytes: ByteArray?): Contact? = RadioContacts.fromBytes(bytes)

        private fun encode(contact: Contact): String = b64.encode(bytesOf(contact))

        private fun decode(line: String): Contact? = runCatching { b64.decode(line.trim()) }.getOrNull()?.let(::fromBytes)
    }
}
