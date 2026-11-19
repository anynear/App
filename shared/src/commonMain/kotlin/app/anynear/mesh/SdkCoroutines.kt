package app.anynear.mesh

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine context for a `RadioClient` (meshtastic-sdk), as the SDK's default (`SupervisorJob() + Dispatchers.Default`)
 * plus one targeted exception handler.
 *
 * meshtastic-sdk 0.2.0 closes its message channel when its engine loop ends. If that loop ends on its own (a
 * `CancellationException` from inside it), its sibling coroutines under the same `SupervisorJob` keep running, and the
 * one watching the transport state may still pass a state change into the closed channel. The resulting
 * [ClosedSendChannelException] is not caught in the SDK and ended the app (crash report, test round 2). The connection
 * is being torn down anyway at that point and the app connects anew, so only this case is logged ([onSwallowed]).
 * Everything else goes on to the usual handling, which ends the app and leaves a crash report (ADR 0082).
 */
internal fun radioSdkContext(onSwallowed: (Throwable) -> Unit): CoroutineContext =
    SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { context, e ->
        if (isClosedSdkChannel(context, e)) onSwallowed(e) else throw e
    }

/** A send into a closed channel from one of the SDK's own coroutines (named `meshtastic-…`). */
internal fun isClosedSdkChannel(context: CoroutineContext, e: Throwable): Boolean =
    e is ClosedSendChannelException && context[CoroutineName]?.name?.startsWith(SDK_COROUTINE_PREFIX) == true

private const val SDK_COROUTINE_PREFIX = "meshtastic-"
