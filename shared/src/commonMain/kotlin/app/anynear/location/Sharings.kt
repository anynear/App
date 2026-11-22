package app.anynear.location

import app.anynear.anmp.core.AnmpCore
import app.anynear.anmp.core.CoreEvent
import app.anynear.anmp.core.PlaceSharing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Interface of the app to the core for „Standort freigeben“ (ADR 0123 §9); replaceable in tests. */
interface SharingSource {
    /** Person ID whenever its sharing changed. */
    val changes: Flow<String>

    fun all(): List<PlaceSharing>

    suspend fun adopt(person: String, level: Int, asked: Int): Boolean

    suspend fun ready() = Unit
}

/** [SharingSource] on the ANMP core. */
class CoreSharingSource(private val core: AnmpCore) : SharingSource {
    override val changes: Flow<String> = core.events.filterIsInstance<CoreEvent.SharingChanged>().map { it.person }

    override fun all(): List<PlaceSharing> = core.sharings()

    override suspend fun adopt(person: String, level: Int, asked: Int): Boolean = core.adoptSharing(person, level, asked)

    override suspend fun ready() {
        core.identity.first { it != null }
    }
}

/**
 * „Standort freigeben“ (ADR 0123 §2) as the core keeps it from the cards in the chat: per person what both agreed on
 * and an open question. [LocationSharing.shared] reads the agreed level from here, so that it counts for what goes out.
 */
class Sharings(private val scope: CoroutineScope, private val source: SharingSource) {
    private val _all = MutableStateFlow<Map<String, PlaceSharing>>(emptyMap())

    /** Sharings that hold or wait for an answer, by person ID. */
    val all: StateFlow<Map<String, PlaceSharing>> = _all.asStateFlow()

    /** Called with the person ID after a change, once [all] holds it. */
    var onChanged: (String) -> Unit = {}

    /** Called once the core has loaded what it knows (for the move of what the app kept itself, ADR 0123 §9). */
    var onReady: suspend () -> Unit = {}

    fun start() {
        scope.launch {
            source.changes.collect { person ->
                refresh()
                runCatching { onChanged(person) }
            }
        }
        scope.launch {
            source.ready()
            refresh()
            runCatching { onReady() }
            refresh()
        }
    }

    fun refresh() {
        _all.value = source.all().associateBy { it.person }
    }

    fun of(person: String): PlaceSharing? = _all.value[person]

    /** The level both agreed on with [person], `null` for none. */
    fun level(person: String): ShareLevel? = of(person)?.takeIf { it.active }?.let { ShareLevel.ofWire(it.level) }

    /** The open question with [person]: its level and whether the own person asked; `null` for none. */
    fun asked(person: String): Pair<ShareLevel, Boolean>? = of(person)?.takeIf { it.open }?.let { ShareLevel.ofWire(it.asked) to it.askedByUs }

    suspend fun adopt(person: String, level: ShareLevel?, asked: ShareLevel?): Boolean =
        source.adopt(person, level?.wire ?: 0, asked?.wire ?: 0).also { refresh() }
}
