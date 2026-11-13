package app.anynear.alerts

import app.anynear.anmp.AnmpAttachment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Interface of the app to the ANMP core for alerts and channels (implementation plan §6). The only place that knows
 * the core's types is [CoreAlertSource]; everything above it works with the models from `Alert.kt`.
 */
interface AlertSource {
    /** Subscribed and own channels. */
    val channels: StateFlow<List<AlertChannel>>

    /** Alerts of the last 30 days, newest first, verified and deduplicated. */
    val alerts: StateFlow<List<Alert>>

    /** Every newly arrived alert once, for notifications. */
    val arrivals: Flow<Alert>

    suspend fun createChannel(name: String): AlertChannel

    suspend fun join(link: String): ChannelJoinResult

    /** `null`: not a channel link. */
    fun parseLink(link: String): ChannelInvite?

    /** Invitation link for sharing, only at the owner. */
    fun channelLink(channelId: String): String?

    suspend fun leave(channelId: String)

    suspend fun rename(channelId: String, name: String)

    /** Send an alert from the app (owner only), for now without attachments. [untilMillis] `null`: 24 hours. */
    suspend fun send(
        channelId: String,
        priority: AlertPriority,
        title: String?,
        text: String,
        url: String?,
        untilMillis: Long? = null,
        replaces: String? = null,
        ended: Boolean = false,
    ): Alert

    suspend fun createIngress(channelId: String, label: String): AlertIngress

    suspend fun revokeIngress(channelId: String, ingressId: String)

    /** New read key (owner only): whoever has the old link reads nothing new afterwards. */
    suspend fun resetLink(channelId: String)

    /** Removes a subscriber: new key for the others, the old link is no longer valid (ADR 0062 §11). */
    suspend fun removeSubscriber(channelId: String, deviceId: String)

    /** Load an attachment of an alert; `null`: not reachable right now or no longer there. */
    suspend fun fetchAttachment(alert: Alert, attachment: AnmpAttachment): ByteArray?
}

/** As long as the core knows no channels: empty, creating and subscribing fail. */
object UnavailableAlertSource : AlertSource {
    override val channels: StateFlow<List<AlertChannel>> = MutableStateFlow(emptyList())
    override val alerts: StateFlow<List<Alert>> = MutableStateFlow(emptyList())
    override val arrivals: Flow<Alert> = emptyFlow()
    override suspend fun createChannel(name: String): AlertChannel = throw UnsupportedOperationException()
    override suspend fun join(link: String): ChannelJoinResult = ChannelJoinResult.Invalid
    override fun parseLink(link: String): ChannelInvite? = null
    override fun channelLink(channelId: String): String? = null
    override suspend fun leave(channelId: String) = Unit
    override suspend fun rename(channelId: String, name: String) = Unit
    override suspend fun send(
        channelId: String, priority: AlertPriority, title: String?, text: String, url: String?,
        untilMillis: Long?, replaces: String?, ended: Boolean,
    ): Alert = throw UnsupportedOperationException()
    override suspend fun createIngress(channelId: String, label: String): AlertIngress = throw UnsupportedOperationException()
    override suspend fun revokeIngress(channelId: String, ingressId: String) = Unit
    override suspend fun resetLink(channelId: String) = Unit
    override suspend fun removeSubscriber(channelId: String, deviceId: String) = Unit
    override suspend fun fetchAttachment(alert: Alert, attachment: AnmpAttachment): ByteArray? = null
}
