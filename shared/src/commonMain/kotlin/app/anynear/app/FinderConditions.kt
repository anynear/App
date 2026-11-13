package app.anynear.app

/** Battery and network of the device for the search for documents (ADR 0071 items 3 and 5). */
internal expect fun platformFinderConditions(): FinderConditions

/**
 * Keeps a self-started search run alive in the background (ADR 0071 item 9): Android via a
 * foreground service with progress, iOS from 26 on via `BGContinuedProcessingTask`. Otherwise the run pauses as soon
 * as the app is not visible.
 */
interface FinderKeepAlive {
    /**
     * A run begins. `true` if it may continue in the background. The platform calls [stop] on “Anhalten”,
     * [expired] when it withdraws the background (afterwards the run pauses as without).
     */
    fun begin(stop: () -> Unit, expired: () -> Unit): Boolean

    /** How far the run is; [pause] says why it is waiting right now. */
    fun progress(checked: Int, total: Int, found: Int, pause: FinderPause?)

    /** The run is over: [completed] all looked at, [found] suggestions, [visible] whether the app is visible. */
    fun end(found: Int, completed: Boolean, visible: Boolean)

    /** The silent look-out has found [found] new important documents (ADR 0081 item 6). */
    fun foundNew(found: Int) {}
}

/** No background: the run pauses as soon as the app is not visible (ADR 0071 item 5). */
object NoFinderKeepAlive : FinderKeepAlive {
    override fun begin(stop: () -> Unit, expired: () -> Unit) = false
    override fun progress(checked: Int, total: Int, found: Int, pause: FinderPause?) = Unit
    override fun end(found: Int, completed: Boolean, visible: Boolean) = Unit
}

internal expect fun platformFinderKeepAlive(): FinderKeepAlive

/** Reopens sources of the search after a restart or update as long as the access exists (ADR 0071 item 10). */
internal expect fun platformDocSources(): DocSourceOpener

/** Schedules the regular look for new documents (ADR 0081). */
internal expect fun platformFinderSchedule(): FinderSchedule
