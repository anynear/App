package app.anynear.mesh

import app.anynear.AppConfig
import app.anynear.anmp.core.radio.DefaultChannel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config

/**
 * Public channels of the radio that anynear shows as chats of their own (ADR 0055). Everyone in radio range
 * reads along; none of it is verified or encrypted in the sense of anynear.
 */
@Serializable
enum class PublicChannel {
    /** The Meshtastic default channel ([DefaultChannel]), on whatever slot: every radio with default settings. */
    @SerialName("nearby")
    NEARBY,

    /** Channel of the Meshhessen community on a slot 1–7. */
    @SerialName("mesh-hessen")
    MESH_HESSEN,
}

/** A public channel on the connected radio. */
data class PublicRadioChannel(val channel: PublicChannel, val index: Int)

/** Text received over radio to everyone on a public channel. */
data class IncomingPublicText(
    val packetId: Int,
    val fromNodeNum: Int,
    val channel: PublicChannel,
    val text: String,
    val timestampMillis: Long,
    /** Meshtastic reaction: packet it applies to (ADR 0018); 0 = normal text. */
    val reactionTo: Int = 0,
    /** Passed on over this many radios, 0 = heard directly; `null` if the packet does not say. */
    val hops: Int? = null,
    /** Reception quality at the own radio, if reported. */
    val snr: Float? = null,
    val rssi: Int? = null,
    /** Came over a bridge from the internet (MQTT, ADR 0054). */
    val viaMqtt: Boolean = false,
)

object PublicChannels {
    /**
     * Recognises public channels only by slot, name and key. Other channels (of other apps, private on the
     * shared radio, ADR 0022) stay invisible. [meshHessenPsk]: expected key of „Mesh Hessen“;
     * empty as long as it is not known. [lora] gives the preset whose name the default channel may carry.
     */
    fun of(channels: List<Channel>, meshHessenPsk: ByteArray = AppConfig.MeshHessen.psk, lora: Config.LoRaConfig? = null): List<PublicRadioChannel> {
        // The default channel on whatever slot; otherwise, as before, a primary channel anyone in range can read.
        val nearby = DefaultChannel.index(channels, lora)
            ?: channels.firstOrNull { it.role == Channel.Role.PRIMARY && it.settings?.psk?.let(::isReadableByAll) == true }?.index
        return channels.mapNotNull { ch ->
            val settings = ch.settings ?: return@mapNotNull null
            when {
                ch.index == nearby -> PublicRadioChannel(PublicChannel.NEARBY, ch.index)
                ch.role == Channel.Role.SECONDARY && isMeshHessen(settings.name, settings.psk, meshHessenPsk) -> PublicRadioChannel(PublicChannel.MESH_HESSEN, ch.index)
                else -> null
            }
        }
    }

    /** Default key or none at all (unencrypted): both can be read by anyone in range. */
    internal fun isReadableByAll(psk: ByteString): Boolean = psk == DefaultChannel.PSK || psk.size == 0

    internal fun isMeshHessen(name: String?, psk: ByteString, expected: ByteArray): Boolean =
        name == AppConfig.MeshHessen.NAME && expected.size == 32 && psk == expected.toByteString()
}
