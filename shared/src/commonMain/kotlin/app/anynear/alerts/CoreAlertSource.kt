package app.anynear.alerts

import app.anynear.anmp.AnmpAttachment
import app.anynear.anmp.core.AlertItem
import app.anynear.anmp.core.BlobResult
import app.anynear.anmp.core.ChannelState
import app.anynear.anmp.core.CoreEvent
import app.anynear.anmp.core.Ingress
import app.anynear.anmp.core.AnmpCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import app.anynear.anmp.core.AlertStatus as CoreAlertStatus
import app.anynear.anmp.core.ChannelJoinResult as CoreJoinResult

/**
 * [AlertSource] on the ANMP core (implementation plan §6): the only place that knows its types for channels and
 * alerts. Times of the core in seconds become milliseconds; the app computes “expired” itself ([statusAt]).
 */
class CoreAlertSource(private val core: AnmpCore, scope: CoroutineScope) : AlertSource {
    override val channels: StateFlow<List<AlertChannel>> =
        core.channels.map { list -> list.map(::channelOf) }.stateIn(scope, SharingStarted.Eagerly, core.channels.value.map(::channelOf))

    override val alerts: StateFlow<List<Alert>> =
        core.alerts.map { list -> list.map(::alertOf) }.stateIn(scope, SharingStarted.Eagerly, core.alerts.value.map(::alertOf))

    // Older alerts of a channel just subscribed to or taken over from the own device are history, a
    // repetition in the same incident without higher priority is only an update (ADR 0062 §4): no notice.
    override val arrivals: Flow<Alert> = core.events.filterIsInstance<CoreEvent.Alert>().filterNot { it.history || it.quiet }.map { alertOf(it.item) }

    override suspend fun createChannel(name: String): AlertChannel = channelOf(core.createChannel(name))

    override suspend fun join(link: String): ChannelJoinResult = when (val r = core.joinChannel(link)) {
        is CoreJoinResult.Joined -> ChannelJoinResult.Joined(channelOf(r.channel))
        is CoreJoinResult.AlreadyJoined -> ChannelJoinResult.AlreadyJoined(channelOf(r.channel))
        CoreJoinResult.ForeignNetwork -> ChannelJoinResult.ForeignNetwork
        CoreJoinResult.Invalid -> ChannelJoinResult.Invalid
    }

    override fun parseLink(link: String): ChannelInvite? =
        core.parseChannelLink(link)?.let { ChannelInvite(it.channelId, it.name, it.curated, it.networkId, it.seenByOwner) }

    override fun channelLink(channelId: String): String? = core.channelLink(channelId)

    override suspend fun leave(channelId: String) = core.leaveChannel(channelId)

    override suspend fun rename(channelId: String, name: String) {
        check(core.renameChannel(channelId, name)) { "rename" }
    }

    override suspend fun send(
        channelId: String, priority: AlertPriority, title: String?, text: String, url: String?,
        untilMillis: Long?, replaces: String?, ended: Boolean,
    ): Alert = alertOf(
        checkNotNull(core.sendAlert(channelId, priority.wire, title, text, url, until = untilMillis?.div(1000), replaces = replaces, ended = ended)) { "send" },
    )

    override suspend fun createIngress(channelId: String, label: String): AlertIngress =
        ingressOf(checkNotNull(core.createIngress(channelId, label)) { "ingress" })

    override suspend fun revokeIngress(channelId: String, ingressId: String) {
        check(core.revokeIngress(channelId, ingressId)) { "revoke" }
    }

    override suspend fun resetLink(channelId: String) {
        checkNotNull(core.resetChannelLink(channelId)) { "reset" }
    }

    override suspend fun removeSubscriber(channelId: String, deviceId: String) {
        check(core.removeSubscriber(channelId, deviceId)) { "remove subscriber" }
    }

    override suspend fun fetchAttachment(alert: Alert, attachment: AnmpAttachment): ByteArray? {
        val item = core.alerts.value.firstOrNull { it.channelId == alert.channelId && it.id == alert.id } ?: return null
        return (core.fetchAlertAttachment(item, attachment) as? BlobResult.Found)?.plaintext
    }

    companion object {
        fun channelOf(c: ChannelState) = AlertChannel(
            c.id, c.name, c.owner, c.curated, c.ingress.map(::ingressOf),
            subscribers = c.subscribers.map { AlertSubscriber(it.deviceId, it.name, it.since) },
            seenByOwner = c.seenByOwner,
        )

        private fun ingressOf(i: Ingress) = AlertIngress(i.id, i.label, i.url, i.created)

        /** Alert of the core (times in seconds) as a model of the app. */
        fun alertOf(a: AlertItem) = Alert(
            channelId = a.channelId,
            id = a.id,
            timestampMillis = a.ts * 1000,
            priority = AlertPriority.of(a.prio),
            title = a.title,
            text = a.text,
            url = a.url,
            untilMillis = a.until * 1000,
            replaces = a.replaces,
            ended = a.ended,
            source = a.source,
            area = a.area,
            attachments = a.attachments,
            sender = a.sender,
            endedByOther = a.status == CoreAlertStatus.ENDED && !a.ended,
        )
    }
}
