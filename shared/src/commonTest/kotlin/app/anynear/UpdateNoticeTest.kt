package app.anynear

import app.anynear.anmp.core.UpdateDemand
import app.anynear.anmp.core.UpdateRule
import app.anynear.app.UpdateNotice
import app.anynear.app.UpdateWay
import app.anynear.app.afterLater
import app.anynear.app.isNewerVersion
import app.anynear.app.updateNotice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ADR 0096: rule of the network × way to the new version × debug build → nothing, band or block. */
class UpdateNoticeTest {
    private val required = UpdateDemand.Required(min = 1200, from = 1_000)
    private val soon = UpdateDemand.Soon(min = 1200, from = 1_000)

    @Test
    fun nothingAskedShowsNothing() {
        assertEquals(UpdateNotice.None, updateNotice(UpdateDemand.None, UpdateWay.None, debug = false))
        assertEquals(UpdateNotice.None, updateNotice(UpdateDemand.None, UpdateWay.Install(1300), debug = false))
        assertEquals(UpdateNotice.None, updateNotice(UpdateDemand.None, UpdateWay.OpenPage, debug = false))
    }

    @Test
    fun beforeFromTheBandCarriesTheDate() {
        assertEquals(UpdateNotice.Soon(1_000), updateNotice(soon, UpdateWay.None, debug = false))
        assertEquals(UpdateNotice.Soon(1_000), updateNotice(soon, UpdateWay.OpenPage, debug = false))
        assertEquals(UpdateNotice.Soon(1_000), updateNotice(soon, UpdateWay.Install(1300), debug = true))
    }

    @Test
    fun requiredBlocksOnlyWithAWayToANewEnoughBuild() {
        // Android: own updater or Google Play offers a build ≥ min.
        assertEquals(UpdateNotice.Block(1200), updateNotice(required, UpdateWay.Install(1200), debug = false))
        assertEquals(UpdateNotice.Block(1200), updateNotice(required, UpdateWay.Install(1250), debug = false))
        // Offered build still too old, or nothing offered: only the band.
        assertEquals(UpdateNotice.Needed, updateNotice(required, UpdateWay.Install(1199), debug = false))
        assertEquals(UpdateNotice.Needed, updateNotice(required, UpdateWay.None, debug = false))
        // iOS cannot ask: blocks after `from`.
        assertEquals(UpdateNotice.Block(1200), updateNotice(required, UpdateWay.OpenPage, debug = false))
    }

    @Test
    fun debugBuildsNeverBlock() {
        assertEquals(UpdateNotice.Needed, updateNotice(required, UpdateWay.Install(1300), debug = true))
        assertEquals(UpdateNotice.Needed, updateNotice(required, UpdateWay.OpenPage, debug = true))
    }

    @Test
    fun clientsMinJoinsTheSameBand() {
        assertEquals(UpdateNotice.Needed, updateNotice(UpdateDemand.None, UpdateWay.None, debug = false, clientsBehind = true))
        // The date and the block say more than clients.min.
        assertEquals(UpdateNotice.Soon(1_000), updateNotice(soon, UpdateWay.None, debug = false, clientsBehind = true))
        assertEquals(UpdateNotice.Block(1200), updateNotice(required, UpdateWay.OpenPage, debug = false, clientsBehind = true))
    }

    @Test
    fun laterHidesTheBandsButNeverTheBlock() {
        assertEquals(UpdateNotice.None, UpdateNotice.Needed.afterLater(laterThisRun = true, laterToday = false))
        assertEquals(UpdateNotice.Needed, UpdateNotice.Needed.afterLater(laterThisRun = false, laterToday = true))
        // With a date: only until the next day, a „Später“ on the other band does not hide it.
        assertEquals(UpdateNotice.None, UpdateNotice.Soon(1_000).afterLater(laterThisRun = false, laterToday = true))
        assertEquals(UpdateNotice.Soon(1_000), UpdateNotice.Soon(1_000).afterLater(laterThisRun = true, laterToday = false))
        assertEquals(UpdateNotice.Block(1200), UpdateNotice.Block(1200).afterLater(laterThisRun = true, laterToday = true))
    }

    @Test
    fun ruleOfTheRadioListToNotice() {
        val rule = UpdateRule(min = 1200, from = 1_000)
        val iphone = UpdateWay.OpenPage
        assertEquals(UpdateNotice.Soon(1_000), updateNotice(rule.demand(build = 1100, nowMillis = 999), iphone, debug = false))
        assertEquals(UpdateNotice.Block(1200), updateNotice(rule.demand(build = 1100, nowMillis = 1_000), iphone, debug = false))
        assertEquals(UpdateNotice.None, updateNotice(rule.demand(build = 1200, nowMillis = 1_000), iphone, debug = false))
        // Unknown build (local build): never asked.
        assertEquals(UpdateNotice.None, updateNotice(rule.demand(build = 0, nowMillis = 1_000), iphone, debug = false))
    }

    @Test
    fun appStoreVersionCompare() {
        assertTrue(isNewerVersion("0.4.0", "0.3.0"))
        assertTrue(isNewerVersion("1.0", "0.9.9"))
        assertTrue(isNewerVersion("0.3.1", "0.3.0-1a2b3c4"), "Zusatz nach - zählt nicht")
        assertFalse(isNewerVersion("0.3.0", "0.3.0"))
        assertFalse(isNewerVersion("0.3.0", "0.3.0.0"))
        assertFalse(isNewerVersion("0.2.9", "0.3.0"))
        assertFalse(isNewerVersion("abc", "0.3.0"), "unlesbar: nie neuer")
    }
}
