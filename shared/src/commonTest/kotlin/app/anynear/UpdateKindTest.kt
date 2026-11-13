package app.anynear

import app.anynear.app.UpdateKind
import app.anynear.app.classifyUpdate
import kotlin.test.Test
import kotlin.test.assertEquals

/** ADR 0025, app ID change: a new app ID is not an update but a move. */
class UpdateKindTest {
    private val old = "app.anynear"
    private val new = "com.pyrsa.pyrsa"

    @Test
    fun olderVersionDataWithoutPackageStayOrdinaryUpdates() {
        assertEquals(UpdateKind.NEWER, classifyUpdate(old, 100, null, 101))
        assertEquals(UpdateKind.NONE, classifyUpdate(old, 101, null, 101))
    }

    @Test
    fun samePackageIsAnOrdinaryUpdate() {
        assertEquals(UpdateKind.NEWER, classifyUpdate(old, 100, old, 101))
        assertEquals(UpdateKind.NONE, classifyUpdate(old, 101, old, 100))
    }

    @Test
    fun otherPackageMeansMovingWhateverTheVersion() {
        assertEquals(UpdateKind.MOVED, classifyUpdate(old, 100, new, 101))
        assertEquals(UpdateKind.MOVED, classifyUpdate(old, 200, new, 1))
    }

    @Test
    fun theNewAppUpdatesNormally() {
        assertEquals(UpdateKind.NEWER, classifyUpdate(new, 120, new, 121))
    }

    // ADR 0037: a new signing key means reinstalling.
    private val oldKey = "ac376d8ed29c2798fff5c5ef85fb831f72028517aeaf3d60ce6c70de89ccb2e7"
    private val newKey = "0".repeat(64)

    @Test
    fun sameSignerIsAnOrdinaryUpdate() {
        assertEquals(UpdateKind.NEWER, classifyUpdate(new, 170, new, 171, oldKey, oldKey))
        assertEquals(UpdateKind.NEWER, classifyUpdate(new, 170, new, 171, oldKey, oldKey.uppercase()))
    }

    @Test
    fun otherSignerMeansReinstallWhateverTheVersion() {
        assertEquals(UpdateKind.REINSTALL, classifyUpdate(new, 170, new, 171, oldKey, newKey))
        assertEquals(UpdateKind.REINSTALL, classifyUpdate(new, 170, new, 1, oldKey, newKey))
    }

    @Test
    fun missingSignerOnEitherSideCountsOnlyTheVersion() {
        assertEquals(UpdateKind.NEWER, classifyUpdate(new, 170, new, 171, null, newKey))
        assertEquals(UpdateKind.NEWER, classifyUpdate(new, 170, new, 171, oldKey, null))
    }

    @Test
    fun otherPackageStillWinsOverOtherSigner() {
        assertEquals(UpdateKind.MOVED, classifyUpdate(old, 100, new, 101, oldKey, newKey))
    }
}
