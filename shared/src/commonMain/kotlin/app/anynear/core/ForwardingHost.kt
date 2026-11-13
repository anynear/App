package app.anynear.core

import app.anynear.anmp.core.Conversation
import app.anynear.anmp.core.AnmpCoreHost

/** Passes the questions of the core on to the app as soon as it is up (the core is created before the controller). */
internal class ForwardingHost : AnmpCoreHost {
    var target: AnmpCoreHost? = null

    override suspend fun circleHistory(circleId: String): List<ByteArray> = target?.circleHistory(circleId).orEmpty()

    override suspend fun sharedContent(conversation: Conversation, hash: String, fromDevice: String): ByteArray? =
        target?.sharedContent(conversation, hash, fromDevice)
}
