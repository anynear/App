package app.anynear.mesh

import org.meshtastic.sdk.SendFailure
import org.meshtastic.sdk.SendState

/**
 * Translates the SDK send state into a display state.
 *
 * `Acked` and `Delivered` both count only as [MessageStatus.RELAYED]: the SDK
 * (0.2.0) does not distinguish whether a routing acknowledgement comes from the recipient or from a
 * forwarding node. [MessageStatus.CONFIRMED] is therefore set exclusively by
 * [SdkMeshLink], when the acknowledgement verifiably comes from the target device.
 */
fun SendState.toMessageStatus(): MessageStatus = when (this) {
    SendState.Queued -> MessageStatus.QUEUED
    SendState.Sent -> MessageStatus.HANDED_TO_RADIO
    SendState.Acked, SendState.Delivered -> MessageStatus.RELAYED
    is SendState.Failed -> when (reason) {
        SendFailure.AckTimeout, SendFailure.Timeout -> MessageStatus.NO_CONFIRMATION
        else -> MessageStatus.FAILED
    }
}
