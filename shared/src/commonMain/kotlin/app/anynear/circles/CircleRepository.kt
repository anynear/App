package app.anynear.circles

import app.anynear.anmp.core.CircleState
import app.anynear.data.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Circles of this app as an image of the circles in the ANMP core ([source], ADR 0036): the core holds key,
 * key version, name and image, the app remembers the
 * currently selected circle. Changing is only possible via the core.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CircleRepository(scope: CoroutineScope, source: StateFlow<List<CircleState>>, private val store: KeyValueStore) {
    val circles: StateFlow<List<Circle>> = source
        .mapLatest { list -> list.map { toCircle(it) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _selectedId = MutableStateFlow(store.getString(SELECTED_KEY))
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    fun find(id: String): Circle? = circles.value.firstOrNull { it.id == id }

    fun select(id: String?) {
        store.putString(SELECTED_KEY, id)
        _selectedId.value = id
    }

    private fun toCircle(c: CircleState): Circle {
        return Circle(
            id = c.id, name = c.name, secret = c.secret, removed = c.removed, epoch = c.epoch,
            previousSecret = c.previousSecret, nameTs = c.nameTs, image = c.image, imageTs = c.imageTs, joinedAt = c.joinedAt,
            historyPending = c.historyPending,
        )
    }

    private companion object {
        const val SELECTED_KEY = "selected_circle"
    }
}
