package app.anynear

import app.anynear.contacts.Contact
import app.anynear.data.KeyValueStore
import app.anynear.mesh.RadioContacts
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User

class InMemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()

    override fun getString(key: String): String? = map[key]

    override fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

/** Made-up test data, no real devices or people. */
fun sharedContact(nodeNum: Int, name: String = "Testgerät", key: String? = "schluessel-a"): Contact = RadioContacts.of(
    SharedContact.Builder().also { b ->
        b.node_num = nodeNum
        b.user = User.Builder().also { u ->
            u.long_name = name
            u.short_name = name.take(4)
            if (key != null) u.public_key = key.encodeUtf8()
        }.build()
    }.build(),
)

class InMemorySecretStore : app.anynear.data.SecretStore {
    private val map = mutableMapOf<String, ByteArray>()

    override fun get(key: String): ByteArray? = map[key]

    override fun put(key: String, value: ByteArray?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

/** Made-up family key for tests (bytes 0..31), not a real key. */
val testFamilySecret = ByteArray(32) { it.toByte() }

/** Valid message IDs (8 bytes, Base64url) for tests. */
internal const val MID_CIRCLE = "Y2lyY2xlMDE"
internal const val MID_DIRECT = "ZGlyZWN0MDE"

