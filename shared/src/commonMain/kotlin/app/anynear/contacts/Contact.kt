package app.anynear.contacts

import app.anynear.mesh.radioNumberLabel
import okio.ByteString

/**
 * A contact is a known radio with a name, not an online account.
 * It says nothing about whether the device is reachable right now.
 *
 * [bytes] is the radio contact as it came along in the link and is stored; read and write only via `mesh/`.
 */
data class Contact(
    val nodeNum: Int,
    val displayName: String,
    val shortName: String,
    /** Public PKI key of the device, if contained in the code. */
    val publicKey: ByteString?,
    val bytes: ByteString,
) {
    val nodeIdLabel: String get() = radioNumberLabel(nodeNum)
}
