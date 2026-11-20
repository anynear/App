package app.anynear

import app.anynear.app.NoOpPlatformHooks
import app.anynear.data.ObservedKeyValueStore
import app.anynear.location.LocationSharing
import app.anynear.location.ShareLevel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ADR 0121 §28: both show each other the same; someone else's step can only lower what the person shows, never raise it. */
class MutualPlaceTest {
    private fun kotlinx.coroutines.test.TestScope.sharing(told: MutableList<Pair<String, Int>> = mutableListOf()) =
        LocationSharing(backgroundScope, ObservedKeyValueStore(InMemoryStore()), NoOpPlatformHooks(), now = { 0L }).apply { onMutualLowered = { id, level -> told += id to level } }

    @Test
    fun askingShowsFirstAndANoGoesBack() = runTest {
        val s = sharing()
        s.setPersonLevel("anna", ShareLevel.Area)
        s.askMutual("anna", ShareLevel.Exact)
        assertEquals(ShareLevel.Exact, s.persons.value["anna"])
        assertTrue(s.mutualOpen("anna"))
        s.mutualAnswered("anna", 0)
        assertEquals(ShareLevel.Area, s.persons.value["anna"])
        assertNull(s.agreed("anna"))
    }

    @Test
    fun aYesToLessLowersTheAskerToo() = runTest {
        val s = sharing()
        s.askMutual("anna", ShareLevel.Exact)
        s.mutualAnswered("anna", ShareLevel.Area.wire)
        assertEquals(ShareLevel.Area, s.persons.value["anna"])
        assertEquals(ShareLevel.Area, s.agreed("anna"))
    }

    @Test
    fun loweringTellsTheOtherSideAndRaisingDoesNot() = runTest {
        val told = mutableListOf<Pair<String, Int>>()
        val s = sharing(told)
        s.agreeMutual("ben", ShareLevel.Exact)
        assertTrue(told.isEmpty())
        // Lower: the other side is told, the agreement holds at most that.
        s.setPersonLevel("ben", ShareLevel.Area)
        assertEquals(listOf("ben" to ShareLevel.Area.wire), told)
        assertEquals(ShareLevel.Area, s.agreed("ben"))
        // Giving more again, alone: nobody is told.
        s.setPersonLevel("ben", ShareLevel.Exact)
        assertEquals(1, told.size)
        // Taken away: told, and the agreement is over.
        s.setPersonLevel("ben", null)
        assertEquals("ben" to 0, told.last())
        assertNull(s.agreed("ben"))
        // Nothing agreed: lowering tells nobody.
        s.setPersonLevel("tom", ShareLevel.Exact)
        s.setPersonLevel("tom", ShareLevel.Off)
        assertEquals(2, told.size)
    }

    @Test
    fun theOtherSideLoweringLowersMeAndICanKeepShowing() = runTest {
        val told = mutableListOf<Pair<String, Int>>()
        val s = sharing(told)
        s.agreeMutual("ben", ShareLevel.Exact)
        assertTrue(s.remoteLowered("ben", ShareLevel.Area.wire))
        assertEquals(ShareLevel.Area, s.persons.value["ben"])
        assertEquals(ShareLevel.Exact, s.kept("ben"))
        // That was not my step: nobody is told back.
        assertTrue(told.isEmpty())
        // „Weiter zeigen“ is my own choice.
        s.keepShowing("ben")
        assertEquals(ShareLevel.Exact, s.persons.value["ben"])
        assertNull(s.kept("ben"))
        // The other side ends it: back to what was before the question, here nothing of its own.
        assertTrue(s.remoteLowered("ben", 0))
        assertNull(s.persons.value["ben"])
        assertNull(s.agreed("ben"))
        // What I showed before the question comes back when the other side ends it.
        s.setPersonLevel("ida", ShareLevel.Area)
        s.askMutual("ida", ShareLevel.Exact)
        s.mutualAnswered("ida", ShareLevel.Exact.wire)
        assertTrue(s.remoteLowered("ida", 0))
        assertEquals(ShareLevel.Area, s.persons.value["ida"])
        // A step of someone I have no agreement with never changes anything, and nothing ever raises.
        s.setPersonLevel("tom", ShareLevel.Exact)
        assertTrue(!s.remoteLowered("tom", 0))
        assertEquals(ShareLevel.Exact, s.persons.value["tom"])
        s.agreeMutual("eva", ShareLevel.Area)
        assertTrue(!s.remoteLowered("eva", ShareLevel.Exact.wire))
        assertEquals(ShareLevel.Area, s.persons.value["eva"])
    }
}
