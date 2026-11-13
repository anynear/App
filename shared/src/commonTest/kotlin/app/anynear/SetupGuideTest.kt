package app.anynear

import app.anynear.ui.GuideStep
import app.anynear.ui.planGuide
import kotlin.test.Test
import kotlin.test.assertEquals

/** Guided setup (ADR 0073): only steps that have something to do on this device, in a fixed order. */
class SetupGuideTest {
    private fun plan(
        card: Boolean = false,
        notifications: Boolean = true,
        background: Boolean = true,
        alertsAvailable: Boolean = true,
        alertsOn: Boolean = false,
        doNotDisturb: Boolean = true,
        withRadio: Boolean = true,
        paired: Boolean = false,
    ) = planGuide(card, notifications, background, alertsAvailable, alertsOn, doNotDisturb, withRadio, paired)

    @Test
    fun allStepsInOrderOnAFreshAndroidPhone() {
        assertEquals(listOf(GuideStep.Notifications, GuideStep.Background, GuideStep.Alerts, GuideStep.DoNotDisturb, GuideStep.Radio), plan())
    }

    @Test
    fun emergencyCardComesFirstWhileItIsMissing() {
        // ADR 0093: before the permissions, so that the root key leaves the phone early.
        assertEquals(GuideStep.EmergencyCard, plan(card = true).first())
        assertEquals(false, GuideStep.EmergencyCard in plan(card = false))
    }

    @Test
    fun grantedOrUnaskedPermissionsAreSkipped() {
        // Notifications already allowed (or before Android 13), iOS without background notice and without „Nicht stören“ access.
        assertEquals(listOf(GuideStep.Alerts, GuideStep.Radio), plan(notifications = false, background = false, doNotDisturb = false))
    }

    @Test
    fun officialAlertsOnlyWhenConfiguredAndNotYetOn() {
        assertEquals(false, GuideStep.Alerts in plan(alertsAvailable = false))
        assertEquals(false, GuideStep.Alerts in plan(alertsOn = true))
    }

    @Test
    fun doNotDisturbOnlyWhenAccessIsMissing() {
        assertEquals(true, GuideStep.DoNotDisturb in plan())
        assertEquals(false, GuideStep.DoNotDisturb in plan(doNotDisturb = false))
    }

    @Test
    fun noRadioStepFromALinkOrWhenAlreadyPaired() {
        assertEquals(false, GuideStep.Radio in plan(withRadio = false))
        assertEquals(false, GuideStep.Radio in plan(paired = true))
    }

    @Test
    fun nothingToDoMeansNoSteps() {
        assertEquals(emptyList(), plan(notifications = false, background = false, alertsOn = true, doNotDisturb = false, paired = true))
    }
}
