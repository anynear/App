package app.anynear

import app.anynear.circles.ProfileRepository
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupDoneTest {
    @Test
    fun freshInstallStartsWithSetupEvenWithName() {
        val store = InMemoryStore()
        val profile = ProfileRepository(store)
        profile.setOwnName("Testname")
        profile.migrateSetupDone(usedBefore = false)
        assertFalse(profile.setupDone.value)
        // A later start changes nothing about that, not even with a name.
        ProfileRepository(store).apply { migrateSetupDone(usedBefore = false) }.let { assertFalse(it.setupDone.value) }
    }

    @Test
    fun existingInstallIsMigratedOnce() {
        val store = InMemoryStore()
        ProfileRepository(store).migrateSetupDone(usedBefore = true)
        // After leaving the last circle and a restart the setup stays completed.
        val restarted = ProfileRepository(store)
        restarted.migrateSetupDone(usedBefore = false)
        assertTrue(restarted.setupDone.value)
    }

    @Test
    fun finishedSetupSurvivesRestart() {
        val store = InMemoryStore()
        ProfileRepository(store).apply {
            migrateSetupDone(usedBefore = false)
            markSetupDone()
        }
        val restarted = ProfileRepository(store)
        restarted.migrateSetupDone(usedBefore = false)
        assertTrue(restarted.setupDone.value)
    }
}
