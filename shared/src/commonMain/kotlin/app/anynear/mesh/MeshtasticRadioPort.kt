package app.anynear.mesh

import app.anynear.anmp.AnmpInfo
import app.anynear.anmp.core.RadioFrame
import app.anynear.anmp.core.RadioHeard
import app.anynear.anmp.core.RadioPort
import app.anynear.anmp.core.RadioSendState
import app.anynear.anmp.core.RadioSent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Radio for the ANMP core (ADR 0057): ANMP packets always on `PRIVATE_APP` ([MeshLink.sendData]) and always to all,
 * on channel 0 if it is suitable and the relays allow it ([channel0Allowed], ADR 0054 §1, implementation plan phase 1),
 * otherwise in the radio channel `anynear` (ADR 0051, 0061). [onSent] receives the packet ID of every transmission.
 */
class MeshtasticRadioPort(
    scope: CoroutineScope,
    private val link: MeshLink,
    private val channel0Allowed: StateFlow<Boolean> = MutableStateFlow(false),
    private val onSent: (packetId: Int) -> Unit = {},
    private val log: (String) -> Unit = {},
) : RadioPort {
    /** Slot that is sent on; `null` while neither channel 0 is suitable nor the radio channel `anynear` is there. */
    fun channelIndex(): Int? = sendChannel(anynearIndex(link.circleChannels.value), link.openChannel0.value, channel0Allowed.value)

    override val ready: StateFlow<Boolean> = combine(link.state, link.circleChannels, link.openChannel0, channel0Allowed) { state, channels, open0, allowed ->
        state == LinkState.Connected && sendChannel(anynearIndex(channels), open0, allowed) != null
    }.stateIn(scope, SharingStarted.Eagerly, false)

    override val incoming: Flow<RadioFrame> = link.incomingData.map {
        RadioFrame(it.payload, fromNode = it.fromNodeNum, packetId = it.packetId, atMillis = it.timestampMillis, hops = it.hops, snr = it.snr)
    }

    override val heard: Flow<RadioHeard> = link.heardNodes.map { RadioHeard(it.node, it.hops) }

    override suspend fun broadcast(payload: ByteArray, hopLimit: Int?): RadioSent? {
        val channel = channelIndex() ?: return null.also { log("Funk: kein Kanal zum Senden (weder `anynear` noch offener Kanal 0)") }
        return try {
            val sent = if (hopLimit == null) link.sendData(payload, null, channel) else link.sendDataWithHops(payload, channel, hopLimit)
            if (sent.packetId != 0) onSent(sent.packetId)
            RadioSent(sent.packetId, sent.statuses.map { it.toRadioState() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Funk: Senden auf Kanal $channel fehlgeschlagen: ${e.message}")
            null
        }
    }
}

private fun MessageStatus.toRadioState(): RadioSendState = when (this) {
    MessageStatus.QUEUED -> RadioSendState.QUEUED
    MessageStatus.HANDED_TO_RADIO -> RadioSendState.HANDED
    MessageStatus.NO_CONFIRMATION -> RadioSendState.NO_CONFIRMATION
    MessageStatus.FAILED -> RadioSendState.FAILED
    else -> RadioSendState.RELAYED
}

/** Slot of the radio channel `anynear` among the reported channels. */
internal fun anynearIndex(channels: List<RadioChannel>?): Int? = channels.orEmpty().firstOrNull { it.name == AnmpInfo.RADIO_CHANNEL_NAME }?.index

/** Channel 0 before `anynear` (ADR 0054 §1), but only if it is suitable and allowed. */
internal fun sendChannel(anynearIndex: Int?, openChannel0: Boolean, channel0Allowed: Boolean): Int? =
    if (openChannel0 && channel0Allowed) 0 else anynearIndex

/**
 * Channel for the radio check to [node] (ADR 0053): the path a message to it takes. A radio without anynear does not
 * know channel 0 or `anynear` for sure, so the channel its last message came on; otherwise and for anynear radios
 * [sendChannel] (channel 0 before `anynear`).
 */
internal fun radioCheckChannel(messages: List<ChatMessage>, node: Int, radioOnly: Boolean, sendChannel: Int?): Int? {
    if (radioOnly) messages.lastOrNull { !it.outgoing && it.fromNodeNum == node && it.radioChannel != null }?.radioChannel?.let { return it }
    return sendChannel
}

/** State of the radio transmission from the core as state of the message. */
fun RadioSendState.toMessageStatus(): MessageStatus = when (this) {
    RadioSendState.QUEUED -> MessageStatus.QUEUED
    RadioSendState.HANDED -> MessageStatus.HANDED_TO_RADIO
    RadioSendState.RELAYED -> MessageStatus.RELAYED
    RadioSendState.NO_CONFIRMATION -> MessageStatus.NO_CONFIRMATION
    RadioSendState.FAILED -> MessageStatus.FAILED
}
