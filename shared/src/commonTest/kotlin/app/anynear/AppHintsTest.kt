package app.anynear

import app.anynear.ui.AppHint
import app.anynear.ui.pickHint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Always only one hint at a time, the most important one wins. */
class AppHintsTest {
    private fun pick(run: Boolean = false, rescue: Boolean = false, setup: Boolean = false, pairing: Boolean = false, notice: Boolean = false, link: Boolean = false, inUse: Boolean = false, area: Boolean = false) =
        pickHint(run, rescue, setup, pairing, notice, link, inUse, area)

    @Test
    fun rescueHidesThePairingHint() {
        assertEquals(AppHint.USB_RESCUE, pick(rescue = true, pairing = true, notice = true))
    }

    @Test
    fun runningUpdateWinsOverEverything() {
        assertEquals(AppHint.RADIO_SOFTWARE_RUN, pick(run = true, rescue = true, setup = true, pairing = true, notice = true))
    }

    @Test
    fun pairingBeforeNewSoftware() {
        assertEquals(AppHint.PAIRING, pick(pairing = true, notice = true))
        assertEquals(AppHint.RADIO_SOFTWARE_NOTICE, pick(notice = true))
    }

    @Test
    fun openLinkHoldsBackHints() {
        assertNull(pick(pairing = true, notice = true, link = true))
        assertEquals(AppHint.DEVICE_SETUP, pick(setup = true, pairing = true, link = true))
    }

    @Test
    fun nothingPending() {
        assertNull(pick())
    }

    @Test
    fun radioInUseBeforePairingButNotOverALink() {
        assertEquals(AppHint.RADIO_IN_USE, pick(inUse = true, pairing = true, notice = true))
        assertEquals(AppHint.DEVICE_SETUP, pick(setup = true, inUse = true))
        assertNull(pick(inUse = true, link = true))
    }

    @Test
    fun radioAreaBeforeSetup() {
        // Without an area nothing is written: first the question for the area, then setting up (ADR 0054 §1b).
        assertEquals(AppHint.RADIO_AREA, pick(area = true, setup = true, pairing = true, link = true))
        assertEquals(AppHint.USB_RESCUE, pick(area = true, rescue = true))
    }
}
