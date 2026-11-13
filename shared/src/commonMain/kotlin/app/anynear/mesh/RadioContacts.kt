package app.anynear.mesh

import app.anynear.contacts.Contact
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.SharedContact
import org.meshtastic.sdk.NodeId

/** Radio contacts in the Meshtastic format `SharedContact`, as they appear in links and in storage. */
object RadioContacts {
    /** Radio contact from bytes; `null` if not readable or without a radio. */
    fun fromBytes(bytes: ByteArray?): Contact? =
        bytes?.let { runCatching { SharedContact.ADAPTER.decode(it) }.getOrNull() }
            ?.takeIf { it.node_num != 0 && it.user != null }
            ?.let { toContact(it, bytes) }

    internal fun of(shared: SharedContact): Contact = toContact(shared, SharedContact.ADAPTER.encode(shared))

    internal fun shared(contact: Contact): SharedContact = SharedContact.ADAPTER.decode(contact.bytes)

    private fun toContact(shared: SharedContact, bytes: ByteArray) = Contact(
        nodeNum = shared.node_num,
        displayName = shared.user?.long_name.orEmpty(),
        shortName = shared.user?.short_name.orEmpty(),
        publicKey = shared.user?.public_key?.takeIf { it.size > 0 },
        bytes = bytes.toByteString(),
    )
}

/** Radio number as Meshtastic shows it (`!1a2b3c4d`). */
fun radioNumberLabel(nodeNum: Int): String = NodeId(nodeNum).toString()
