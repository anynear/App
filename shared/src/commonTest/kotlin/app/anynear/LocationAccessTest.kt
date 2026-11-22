package app.anynear

import app.anynear.app.NoOpPlatformHooks
import app.anynear.location.LocationAccess
import app.anynear.location.LocationAccessState
import app.anynear.location.LocationNeed
import app.anynear.location.LocationPurpose
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The one check of the location permission for every action (ADR 0125). */
class LocationAccessTest {
    private class Hooks : NoOpPlatformHooks() {
        val rough = MutableStateFlow(false)
        val exact = MutableStateFlow(false)
        val always = MutableStateFlow(false)
        var on = true
        val calls = mutableListOf<String>()

        /** What the person answers to the system's question. */
        var grants = true
        override val locationAllowed: StateFlow<Boolean> get() = rough
        override val preciseLocationAllowed: StateFlow<Boolean> get() = exact
        override val backgroundLocationAllowed: StateFlow<Boolean> get() = always
        override fun locationServicesOn() = on
        override fun requestLocation() { calls += "rough"; if (grants) rough.value = true }
        override fun requestPreciseLocation() { calls += "exact"; if (grants) { rough.value = true; exact.value = true } }
        override fun requestBackgroundLocation() { calls += "always"; if (grants) always.value = true }
        override fun openLocationSettings() { calls += "settings" }
    }

    private fun access(hooks: Hooks, store: InMemoryStore = InMemoryStore(), settingsOnly: Boolean = false) =
        LocationAccess(hooks, store, lastingPreciseOnlyInSettings = { settingsOnly }, waitMillis = 1_000)

    @Test
    fun statesFollowWhatThePhoneAllows() {
        val hooks = Hooks()
        val a = access(hooks)
        assertEquals(LocationAccessState.Missing, a.state(LocationNeed.Precise))
        hooks.rough.value = true
        assertEquals(LocationAccessState.Ok, a.state(LocationNeed.Approximate))
        assertEquals(LocationAccessState.Limited, a.state(LocationNeed.Precise))
        hooks.exact.value = true
        assertEquals(LocationAccessState.Ok, a.state(LocationNeed.PreciseWhileActive))
        assertEquals(LocationAccessState.Limited, a.state(LocationNeed.Always))
        hooks.always.value = true
        assertEquals(LocationAccessState.Ok, a.state(LocationNeed.Always))
        hooks.on = false
        assertEquals(LocationAccessState.Off, a.state(LocationNeed.Approximate))
    }

    @Test
    fun theSheetComesOncePerPurposeAndAllowGoesToTheSystem() = runTest {
        val hooks = Hooks()
        val a = access(hooks)
        val result = async { a.ensure(LocationNeed.Precise, LocationPurpose.SosContact) }
        assertEquals(LocationAccess.Ask(LocationNeed.Precise, LocationPurpose.SosContact), a.asking.filterNotNull().first())
        a.answer(true)
        assertEquals(LocationAccessState.Ok, result.await())
        assertNull(a.asking.value)
        assertEquals(listOf("exact"), hooks.calls)
        assertTrue(a.asked(LocationPurpose.SosContact))
    }

    @Test
    fun afterNoOnlyTheHintLineAsks() = runTest {
        val hooks = Hooks()
        val a = access(hooks)
        val first = async { a.ensure(LocationNeed.PreciseWhileActive, LocationPurpose.LifeSign) }
        a.asking.filterNotNull().first()
        a.answer(false)
        assertEquals(LocationAccessState.Missing, first.await())
        // The second time: no sheet, no question of the system.
        assertEquals(LocationAccessState.Missing, a.ensure(LocationNeed.PreciseWhileActive, LocationPurpose.LifeSign))
        assertNull(a.asking.value)
        assertTrue(hooks.calls.isEmpty())
        // Another purpose still gets its own sheet.
        val other = async { a.ensure(LocationNeed.Precise, LocationPurpose.Share) }
        yield()
        assertEquals(LocationPurpose.Share, a.asking.filterNotNull().first().purpose)
        a.answer(false)
        other.await()
    }

    @Test
    fun nothingIsAskedWhenAllIsThere() = runTest {
        val hooks = Hooks().apply { rough.value = true; exact.value = true }
        val a = access(hooks)
        assertEquals(LocationAccessState.Ok, a.ensure(LocationNeed.Precise, LocationPurpose.SosContact))
        assertFalse(a.asked(LocationPurpose.SosContact))
    }

    @Test
    fun alwaysGoesStepByStep() = runTest {
        val hooks = Hooks()
        val a = access(hooks)
        assertEquals(LocationAccessState.Ok, a.fixAndWait(LocationNeed.Always, LocationPurpose.Level))
        assertEquals(listOf("exact", "always"), hooks.calls)
    }

    @Test
    fun iPhoneSendsSosContactsToTheSettingsForTheLastingExactPlace() = runTest {
        val hooks = Hooks().apply { rough.value = true; grants = false }
        val a = access(hooks, settingsOnly = true)
        a.fix(LocationNeed.Precise, LocationPurpose.SosContact)
        a.fix(LocationNeed.Precise, LocationPurpose.LifeSign)
        // Sending the place once is fine with the place for now.
        a.fix(LocationNeed.Precise, LocationPurpose.SendPlace)
        assertEquals(listOf("settings", "settings", "exact"), hooks.calls)
    }

    @Test
    fun locationOffOpensTheSettingsAndDeclinedReturnsAfterTheWait() = runTest {
        val hooks = Hooks().apply { on = false }
        val a = access(hooks)
        assertEquals(LocationAccessState.Off, a.fixAndWait(LocationNeed.Precise, LocationPurpose.Share))
        assertEquals(listOf("settings"), hooks.calls)
    }

    @Test
    fun alwaysTakenAwayIsHintedOncePerLoss() {
        val hooks = Hooks().apply { rough.value = true; exact.value = true; always.value = true }
        val a = access(hooks)
        a.checkAlways(needed = true)
        assertFalse(a.alwaysLost.value)
        hooks.always.value = false
        a.checkAlways(needed = true)
        assertTrue(a.alwaysLost.value)
        a.acknowledgeAlwaysLost()
        a.checkAlways(needed = true)
        assertFalse(a.alwaysLost.value)
        // Never had it: nothing to hint.
        val fresh = access(Hooks().apply { rough.value = true; exact.value = true })
        fresh.checkAlways(needed = true)
        assertFalse(fresh.alwaysLost.value)
    }
}
