package app.anynear.mesh

import app.anynear.anmp.core.radio.RadioTarget
import app.anynear.anmp.core.firmware.FirmwarePath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import app.anynear.anmp.core.radio.GatewaySetup
import app.anynear.contacts.Contact
import kotlin.time.Duration

/**
 * Boundary between app and Meshtastic SDK. The UI only knows this interface,
 * so that it stays testable without a radio and the SDK is replaceable.
 */
interface MeshLink {
    val state: StateFlow<LinkState>
    val ownDevice: StateFlow<OwnDevice?>
    /** The radio channel `anynear` on the connected radio (ADR 0051), as a list; `null` as long as it is not known. */
    val circleChannels: StateFlow<List<RadioChannel>?>

    /** Names of old channels ([app.anynear.AppConfig.isOldChannelName], channels of earlier versions); the app clears them away. */
    val oldCircleChannels: StateFlow<List<String>> get() = NoOldChannels

    /**
     * Secondary slots occupied by channels of other apps (neither primary channel nor circle channel). They
     * are never touched; for circles, [CIRCLE_SLOTS] minus these remain.
     */
    val otherChannelSlots: StateFlow<Int> get() = NO_OTHER_CHANNELS

    /** Names of known devices from the NodeDB of the radio (node number → long name). */
    val knownNames: StateFlow<Map<Int, String>>

    /** Texts received on a circle channel or as a direct message to us. Everything else is discarded. */
    val incoming: Flow<IncomingText>

    /** ANMP packets received over radio (ADR 0021), only on circle channels or directly to us. */
    val incomingData: Flow<IncomingData> get() = emptyFlow()

    /**
     * Every other packet the radio passes on (NodeInfo, telemetry, position, text …), only who sent it and over how many
     * hops (ADR 0090): the radio of a person is in reach again. ANMP packets come through [incomingData].
     */
    val heardNodes: Flow<HeardNode> get() = emptyFlow()

    /**
     * Channel 0 is fit for ANMP (ADR 0054 §1, implementation plan phase 1): main channel with the Meshtastic default
     * key and without a name of its own. `false` as long as the channels are not known.
     */
    val openChannel0: StateFlow<Boolean> get() = NoChannel0

    /** Public channels on the connected radio (ADR 0055); empty as long as none are known. */
    val publicChannels: StateFlow<List<PublicRadioChannel>> get() = NoPublicChannels

    /** Texts to everyone on a public channel (ADR 0055), without own ones. */
    val incomingPublic: Flow<IncomingPublicText> get() = emptyFlow()

    /**
     * Sends [text] as an ordinary Meshtastic text to everyone on the public channel [channel] (ADR 0055),
     * so that the official app shows it too. No ANMP, no signature.
     *
     * @throws NotReadyException if not connected or the channel is missing.
     */
    fun sendPublic(text: String, channel: PublicChannel): RadioSend = throw NotReadyException("Öffentlicher Funk fehlt.")

    /** Meshtastic reaction to everyone on [channel] (ADR 0055 §3). @return packet ID, `null` if nothing went out. */
    fun sendPublicReaction(emoji: String, replyTo: Int, channel: PublicChannel): Int? = null

    /**
     * Creates the public channel [name] with [psk] on a free slot (ADR 0055), without location. If there already is
     * a channel of the same name, it stays as it is, even with a different key: it then belongs to someone else.
     */
    suspend fun ensurePublicChannel(name: String, psk: ByteArray): ChannelSetupResult = ChannelSetupResult.NoFreeSlot

    /** Starts the permanent connection incl. reconnection. Replaces a running one. */
    fun start(device: PairedDevice)

    suspend fun stop()

    /**
     * Steps back from a radio that another phone or app is using (Wi-Fi serves only one connection, the newest wins):
     * disconnects and no longer reconnects on its own, state [LinkState.InUseElsewhere]. [start] connects again and
     * thereby disconnects the other one.
     */
    suspend fun yieldRadio() {}

    /** Whether the app is visible. In the background the connection looks for a missing radio less often (battery). */
    fun setInForeground(visible: Boolean) {}

    /** Checks once whether a Meshtastic radio answers at [device]. Does not change any assignment. */
    suspend fun probe(device: PairedDevice): Boolean

    /**
     * Sends [text] to everyone in the circle channel [channelIndex] ([to] = null) or as a direct message.
     * The flow ends with the last reliably known state.
     *
     * @throws NotReadyException if not connected or the channel is missing.
     * @throws org.meshtastic.sdk.MeshtasticException.PayloadTooLarge if the text is too long.
     */
    fun send(text: String, to: Int?, channelIndex: Int): RadioSend

    /**
     * Sends an ANMP packet [payload] (ADR 0021) on `PRIVATE_APP` to everyone in the circle channel [channelIndex]
     * ([to] = null) or to a radio. States as in [send].
     *
     * @throws NotReadyException if not connected or the channel is missing.
     */
    suspend fun sendData(payload: ByteArray, to: Int?, channelIndex: Int): RadioSend

    /**
     * Sends an ANMP packet to everyone on the channel [channelIndex] with its own [hopLimit] (ask and patrons,
     * ADR 0051). Without an implementation of its own, with the hop limit of the radio profile.
     */
    suspend fun sendDataWithHops(payload: ByteArray, channelIndex: Int, hopLimit: Int): RadioSend = sendData(payload, null, channelIndex)

    /**
     * Radio check (ADR 0053): sends a small packet with `want_ack` directly to the radio [to] on the channel
     * [channelIndex], with the hop limit of the radio profile like a real message, and waits at most [timeout]
     * for the answer from its firmware. Only what comes from the target radio itself counts.
     *
     * @return the answer, `null` without an answer.
     * @throws NotReadyException if not connected or the channel is missing.
     */
    suspend fun radioCheck(to: Int, channelIndex: Int, timeout: Duration): RadioCheckReply? = null

    /**
     * Sends [emoji] as a Meshtastic reaction to the radio packet [replyTo] (ADR 0018), to everyone in the channel
     * ([to] = null) or to a radio. Only still for messages from radios without anynear; among
     * anynear apps reactions go as ANMP. Without a queue: if the radio is not there right now, it goes
     * only over the internet. @return packet ID of the transmission, `null` if nothing went out.
     */
    fun sendReaction(emoji: String, replyTo: Int, to: Int?, channelIndex: Int): Int? = null

    /**
     * Comparison of the connected device with the device profile (ADR 0006); `null` as long as the
     * configuration has not been read yet. Is kept across short connection drops.
     */
    val setupCheck: StateFlow<DeviceSetupCheck?>

    /**
     * Writes the device profile including the device name `anynear [nameSuffix]` and checks until everything fits.
     * Only call after consent. The device restarts in the process. [force]: rewrite everything, even
     * what is already right („Erweitert“).
     */
    suspend fun applyRadioProfile(nameSuffix: String, force: Boolean = false): DeviceSetupResult

    /**
     * Only changes name and short name. The firmware restarts afterwards; it is only done once the device
     * reports the new name after the restart.
     */
    suspend fun renameDevice(nameSuffix: String): DeviceSetupResult

    /**
     * Creates the circle channel [name] with key [psk] (free secondary slot, nothing foreign is
     * overwritten) or renews the key of an existing channel of the same name. Confirmed
     * by reading back. This does not change radio settings, see [applyRadioProfile].
     */
    suspend fun ensureChannel(name: String, psk: ByteArray): ChannelSetupResult

    /**
     * Like [ensureChannel], but if no slot is free, the channel goes onto the slot of the circle channel
     * [replace] (ADR 0038). Channels other than circle channels are never overwritten.
     */
    suspend fun replaceChannel(replace: String, name: String, psk: ByteArray): ChannelSetupResult = ChannelSetupResult.NoFreeSlot

    /** Removes the circle channel [name] (e.g. when leaving a circle). */
    suspend fun removeChannel(name: String): Boolean

    /**
     * Switches the way of use over the current connection (ADR 0094 §3): [ssid] set = Wi-Fi on with name and password
     * (Bluetooth goes off), `null` = Wi-Fi off and Bluetooth on. One transaction, the radio restarts and the current
     * connection ends. `false` only if nothing could be sent (not connected) or the radio refused it; a missing
     * answer during the restart counts as sent.
     */
    suspend fun setWayOfUse(ssid: String?, password: String?): Boolean = false

    /**
     * Where radios on Wi-Fi upload as gateways (ADR 0054 §5b), from the signed radio list; `null`: none. The link keeps
     * the radio in line with it on every connection ([GatewaySetup]).
     */
    fun useGateway(gateway: app.anynear.anmp.core.RadioGateway?) {}

    /**
     * Radio settings of the region the radio is in (ADR 0054 §1b, from the radio list); the profile check and every
     * write of the radio profile use them. Until set: [RadioTarget.DEFAULT]. [known]: settings of all areas of the
     * list, so that a radio still on another area counts as [DeviceSetupCheck.areaChange].
     */
    fun useRadioTarget(target: RadioTarget, known: List<RadioTarget> = emptyList()) {}

    /** Software and model of the connected radio (ADR 0056); `null` as long as not read. */
    val firmware: StateFlow<RadioFirmware?> get() = NoFirmware

    /**
     * Paths by which this app can install new software. Empty until a path is implemented according to the observer
     * specification (ADR 0032) and tested on real hardware (ADR 0056, tests U1–U8).
     */
    val firmwarePaths: Set<FirmwarePath> get() = emptySet()

    /**
     * Installs [image] via [path] onto the connected radio (ADR 0056 §2 step 5). [onProgress] from 0 to 1, for the
     * first time as soon as the radio is being written to. Only call after consent and while the app is open.
     */
    suspend fun installFirmware(path: FirmwarePath, image: ByteArray, onProgress: (Float) -> Unit): FirmwareInstallResult =
        FirmwareInstallResult.Unsupported

    /** Own contact for passing on, as soon as the device is connected. */
    fun ownContact(): Contact?

    /** Passes a contact on to the radio so that it knows its key for direct messages. */
    suspend fun registerContact(contact: Contact): Boolean

    companion object {
        /** Channel slots 1–7; slot 0 is the primary channel (Hessenmesh). */
        const val CIRCLE_SLOTS = 7

        private val NO_OTHER_CHANNELS: StateFlow<Int> = MutableStateFlow(0)
    }
}

/** A radio transmission: packet ID ([packetId], 0 if unknown) and its states. */
class RadioSend(val packetId: Int, val statuses: Flow<MessageStatus>)

class NotReadyException(message: String) : IllegalStateException(message)

private val NoOldChannels: StateFlow<List<String>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())

private val NoFirmware: StateFlow<RadioFirmware?> = kotlinx.coroutines.flow.MutableStateFlow(null)

private val NoPublicChannels: StateFlow<List<PublicRadioChannel>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())

private val NoChannel0: StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false)
