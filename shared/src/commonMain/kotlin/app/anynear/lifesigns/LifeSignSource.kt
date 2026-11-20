package app.anynear.lifesigns

import app.anynear.anmp.core.AlivePlan
import app.anynear.anmp.core.AlivePosition
import app.anynear.anmp.core.AliveStatus
import app.anynear.anmp.core.AnmpCore
import app.anynear.anmp.core.CoreEvent
import app.anynear.location.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

/** Interface of the app to the core for check-ins (ADR 0083 §5); replaceable in tests. */
interface LifeSignSource {
    /** Plan ID on every change (new packet, answer, receipt). */
    val changes: Flow<String>

    /** All known ones, also ended ones (the core drops them after a week), newest first. */
    fun statuses(now: Long): List<AliveStatus>

    fun nextAt(now: Long): Long?

    /** In the circle [circleId], or with [person] for that one person instead (then [circleId] does not count). */
    suspend fun start(circleId: String, label: String, until: Long, quiet: Long, firstDue: Long?, person: String? = null): AlivePlan?

    suspend fun confirm(planId: String): Boolean

    suspend fun end(planId: String): Boolean

    suspend fun care(planId: String, state: Int): Boolean

    /** What is due for own check-ins (not confirmed, end); returns the next time. */
    suspend fun tick(): Long?

    /**
     * Where this phone is, for the own running check-in [planId] (ADR 0121 §10): the core keeps it and sends it only
     * with „nicht bestätigt“; [trail], where the phone was before, goes only into the call the helpers get then.
     * `null` forgets both.
     */
    suspend fun setPlace(planId: String, place: Place?, trail: List<Place> = emptyList()): Boolean
}

/** [LifeSignSource] on the ANMP core. */
class CoreLifeSignSource(private val core: AnmpCore) : LifeSignSource {
    override val changes: Flow<String> = core.events.filterIsInstance<CoreEvent.AliveChanged>().map { it.planId }

    override fun statuses(now: Long): List<AliveStatus> = core.alivePlans().mapNotNull { core.aliveStatus(it.id, now) }

    override fun nextAt(now: Long): Long? = core.aliveNextAt(now)

    override suspend fun start(circleId: String, label: String, until: Long, quiet: Long, firstDue: Long?, person: String?): AlivePlan? =
        core.startAlive(circleId, label, until, quiet, firstDue = firstDue, person = person)

    override suspend fun confirm(planId: String): Boolean = core.confirmAlive(planId)

    override suspend fun end(planId: String): Boolean = core.endAlive(planId)

    override suspend fun care(planId: String, state: Int): Boolean = core.careAlive(planId, state)

    override suspend fun tick(): Long? = core.aliveTick()

    override suspend fun setPlace(planId: String, place: Place?, trail: List<Place>): Boolean =
        core.aliveSetPosition(
            planId, place?.let { AlivePosition(it.latitude, it.longitude, it.atMillis, it.accuracyMeters) },
            trail.map { AlivePosition(it.latitude, it.longitude, it.atMillis, it.accuracyMeters) },
        )
}
