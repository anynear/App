package app.anynear

import app.anynear.app.NoOpPlatformHooks
import app.anynear.data.ObservedKeyValueStore
import app.anynear.location.LocationSharing
import app.anynear.location.ShareLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Who sees where the person is (ADR 0121 §3, §10): the default, circles set differently, the question. */
class LocationSharingTest {
    private val t0 = 1_800_000_000_000L

    private class Hooks : NoOpPlatformHooks() {
        val exact = MutableStateFlow(false)
        var asked = 0
        override val preciseLocationAllowed: StateFlow<Boolean> get() = exact
        override fun requestPreciseLocation() { asked++ }
    }

    private fun sharing(store: ObservedKeyValueStore = ObservedKeyValueStore(InMemoryStore()), hooks: Hooks = Hooks(), now: () -> Long = { t0 }) =
        LocationSharing(CoroutineScope(Dispatchers.Unconfined), store, hooks, now).also { it.start() }

    @Test
    fun beforeTheAnswerNobodySeesAnything() {
        val s = sharing()
        assertNull(s.default.value)
        assertEquals(ShareLevel.Off, s.level("familie"))
    }

    @Test
    fun theAnswerIsTheDefaultAndACircleCanDiffer() {
        val hooks = Hooks()
        val store = ObservedKeyValueStore(InMemoryStore())
        val s = sharing(store, hooks)
        s.answer(ShareLevel.WhenNeeded)
        assertEquals(ShareLevel.WhenNeeded, s.level("familie"))
        // Yes without the permission of the system: the app asks for the exact location, once.
        assertEquals(1, hooks.asked)

        s.setLevel("nachbarn", ShareLevel.Off)
        assertEquals(ShareLevel.Off, s.level("nachbarn"))
        assertEquals(ShareLevel.WhenNeeded, s.level("familie"))
        // Set back to what the default says anyway: no own entry is kept.
        s.setLevel("nachbarn", ShareLevel.WhenNeeded)
        assertTrue(s.circles.value.isEmpty())
        assertNull(store.getString(LocationSharing.KEY_CIRCLES))

        // „Nein“ asks the system for nothing; a circle can still be switched on.
        hooks.exact.value = true
        s.answer(ShareLevel.Off)
        assertEquals(1, hooks.asked)
        s.setLevel("familie", ShareLevel.WhenNeeded)
        assertEquals(ShareLevel.WhenNeeded, s.level("familie"))
        assertEquals(ShareLevel.Off, s.level("nachbarn"))

        // A new start reads the same.
        val again = sharing(store, hooks)
        assertEquals(ShareLevel.Off, again.default.value)
        assertEquals(ShareLevel.WhenNeeded, again.level("familie"))
    }

    @Test
    fun anAnswerFromAnotherOwnDeviceArrivesThroughTheStore() {
        val store = ObservedKeyValueStore(InMemoryStore())
        val s = sharing(store)
        // As the settings of the person arrive from the network (ADR 0106).
        store.putString(LocationSharing.KEY_DEFAULT, "when")
        assertEquals(ShareLevel.WhenNeeded, s.default.value)
        store.putString(LocationSharing.KEY_CIRCLES, """{"nachbarn":"off"}""")
        assertEquals(ShareLevel.Off, s.level("nachbarn"))
        // A level of a newer version includes „wenn etwas ist“; something unreadable changes nothing.
        store.putString(LocationSharing.KEY_DEFAULT, "approx")
        assertEquals(ShareLevel.WhenNeeded, s.default.value)
        store.putString(LocationSharing.KEY_CIRCLES, "kaputt")
        assertTrue(s.circles.value.isEmpty())
    }

    @Test
    fun laterPostponesTheQuestionForThreeDays() {
        val s = sharing()
        s.postpone()
        assertEquals(t0 + 3 * 24 * 3_600_000L, s.postponedUntil.value)
        assertNull(s.default.value)
    }
}
