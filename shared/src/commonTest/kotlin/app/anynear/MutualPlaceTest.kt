package app.anynear

import app.anynear.anmp.core.PlaceSharing
import app.anynear.app.NoOpPlatformHooks
import app.anynear.data.ObservedKeyValueStore
import app.anynear.location.LocationSharing
import app.anynear.location.ShareLevel
import app.anynear.location.SharingSource
import app.anynear.location.Sharings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ADR 0123: a sharing of the place holds once both agreed, at one level; it counts on top of the circles. */
class MutualPlaceTest {
    private fun kotlinx.coroutines.test.TestScope.sharing(store: ObservedKeyValueStore = ObservedKeyValueStore(InMemoryStore())) =
        LocationSharing(backgroundScope, store, NoOpPlatformHooks(), now = { 0L })

    private class Source(var list: List<PlaceSharing> = emptyList()) : SharingSource {
        override val changes = MutableSharedFlow<String>()
        val adopted = mutableListOf<Triple<String, Int, Int>>()
        override fun all(): List<PlaceSharing> = list
        override suspend fun adopt(person: String, level: Int, asked: Int): Boolean {
            if (list.any { it.person == person }) return false
            adopted += Triple(person, level, asked)
            list = list + PlaceSharing(person, level = level, asked = asked, askedByUs = asked > 0)
            return true
        }
    }

    @Test
    fun aSharingGivesAtLeastItsLevelAndEndsWithoutLeavingAnythingBehind() = runTest {
        val s = sharing()
        val agreed = mutableMapOf<String, ShareLevel>()
        s.shared = { agreed[it] }
        s.answer(ShareLevel.WhenNeeded)
        assertEquals(ShareLevel.WhenNeeded, s.levelFor("anna", emptyList()))
        agreed["anna"] = ShareLevel.Exact
        assertEquals(ShareLevel.Exact, s.levelFor("anna", emptyList()))
        // Ended: back to what the circles say, nothing to put back by hand.
        agreed.remove("anna")
        assertEquals(ShareLevel.WhenNeeded, s.levelFor("anna", emptyList()))
        // Paused: nothing, also with a sharing.
        agreed["anna"] = ShareLevel.Exact
        s.setPaused(true)
        assertEquals(ShareLevel.Off, s.levelFor("anna", emptyList()))
    }

    @Test
    fun aPersonsOwnSettingOnlyLowersWhatTheCirclesGive() = runTest {
        val s = sharing()
        s.setLevel("familie", ShareLevel.Exact, members = listOf("anna"))
        assertEquals(ShareLevel.Exact, s.circleLevelFor("anna", listOf("familie")))
        s.setPersonLevel("anna", ShareLevel.WhenNeeded)
        assertEquals(ShareLevel.WhenNeeded, s.levelFor("anna", listOf("familie")))
        assertEquals(ShareLevel.Exact, s.circleLevelFor("anna", listOf("familie")))
    }

    @Test
    fun whatTheAppKeptMovesIntoTheCoreOnce() = runTest {
        val store = ObservedKeyValueStore(InMemoryStore())
        // As the app before ADR 0123 left it: Anna agreed at „Genau“ (before that „Nur bei SOS“), Tom was asked.
        store.putString("location_agreed_anna", "exact")
        store.putString("location_mutual_anna", "when")
        store.putString("location_mutual_open_tom", "1")
        store.putString("location_mutual_tom", "-")
        store.putString(LocationSharing.KEY_PERSONS, "{\"anna\":\"exact\",\"tom\":\"area\"}")
        val s = sharing(store)
        val source = Source()
        val sharings = Sharings(backgroundScope, source)
        s.migrateMutual(listOf("anna", "tom", "ben")) { id, level, asked -> sharings.adopt(id, level, asked) }
        assertEquals(listOf(Triple("anna", ShareLevel.Exact.wire, 0), Triple("tom", 0, ShareLevel.Area.wire)), source.adopted)
        // What was shown for the agreement and the question goes back to what held before.
        assertEquals(ShareLevel.WhenNeeded, s.persons.value["anna"])
        assertNull(s.persons.value["tom"])
        assertEquals(ShareLevel.Exact, sharings.level("anna"))
        assertEquals(ShareLevel.Area to true, sharings.asked("tom"))
        // A second time nothing happens.
        s.migrateMutual(listOf("anna", "tom")) { id, level, asked -> sharings.adopt(id, level, asked) }
        assertEquals(2, source.adopted.size)
        assertNull(store.getString("location_agreed_anna"))
    }

    /** The reported bug (21.11.2026): a sharing that holds is shown, and only another level is offered. */
    @Test
    fun anExistingSharingIsShownAndOnlyAnotherLevelIsOffered() = runTest {
        val s = sharing()
        s.shared = { if (it == "anna") ShareLevel.Exact else null }
        val between = s.between("anna", emptyList(), ShareLevel.Exact)
        assertTrue(between.exists)
        assertEquals(ShareLevel.Exact, between.shared)
        assertEquals(listOf(ShareLevel.Area), between.askable)
        // An open question of the own person: that level is not offered again.
        val tom = s.between("tom", emptyList(), null, asked = ShareLevel.Exact, askedByUs = true)
        assertTrue(tom.exists)
        assertEquals(listOf(ShareLevel.Area), tom.askable)
        // Nothing at all: a fresh question, starting at „Ungefähr“.
        val eva = s.between("eva", emptyList(), null)
        assertTrue(!eva.exists)
        assertEquals(listOf(ShareLevel.Area, ShareLevel.Exact), eva.askable)
        assertEquals(ShareLevel.Area, eva.suggested)
    }
}
