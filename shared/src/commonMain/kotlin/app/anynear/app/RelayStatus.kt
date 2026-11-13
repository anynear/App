package app.anynear.app

import app.anynear.anmp.core.RelayStatus as CoreRelayStatus

/** State of a relay from the app's point of view, for Verbindungen → Internet. */
enum class RelayState {
    /** Answered most recently. */
    REACHED,

    /** The last request failed or no answer has come yet. */
    UNREACHABLE,

    /** Known, but not queried right now (holds no space of this installation). */
    UNUSED,
}

/** A relay with identifier ([hostPort]: start of the node key, [label]: site, e.g. fr-par-01, empty: unknown) and state. */
data class RelayStatus(val hostPort: String, val label: String, val state: RelayState)

/**
 * Relays of the network list with their state from the ANMP core: reached if the relay answered most recently,
 * otherwise not reachable. Order as in the list.
 */
internal fun relayStatuses(status: CoreRelayStatus): List<RelayStatus> =
    status.known.mapIndexed { i, node ->
        val state = if (node in status.reachable) RelayState.REACHED else RelayState.UNREACHABLE
        RelayStatus(node.take(8), status.sites.getOrNull(i).orEmpty(), state)
    }
