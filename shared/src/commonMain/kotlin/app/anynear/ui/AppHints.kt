package app.anynear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.anynear.anmp.core.AliveStatus
import app.anynear.app.AppController
import app.anynear.app.RadioSoftwareState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hints that the app shows on its own initiative. Always only one at a time (hardware test 03.11.2026: pairing
 * hint and rescue on top of each other were confusing); if a more important one is needed, the other disappears.
 * Order = priority.
 */
internal enum class AppHint { RADIO_SOFTWARE_RUN, USB_RESCUE, RADIO_AREA, DEVICE_SETUP, RADIO_IN_USE, PAIRING, RADIO_SOFTWARE_NOTICE, LOCATION_SHARE, LOCATION_ALWAYS_LOST }

/** The hint whose turn it is; `null`: none, then the general ones may show (app update, background …). */
internal fun pickHint(
    softwareRun: Boolean,
    rescueOpen: Boolean,
    deviceSetup: Boolean,
    pairing: Boolean,
    softwareNotice: Boolean,
    linkOpen: Boolean,
    radioInUse: Boolean = false,
    radioArea: Boolean = false,
    locationShare: Boolean = false,
    alwaysLost: Boolean = false,
): AppHint? = when {
    softwareRun -> AppHint.RADIO_SOFTWARE_RUN
    rescueOpen -> AppHint.USB_RESCUE
    // Before setting up: without an area the app writes nothing to the radio (ADR 0054 §1b).
    radioArea -> AppHint.RADIO_AREA
    deviceSetup -> AppHint.DEVICE_SETUP
    // An opened invitation or contact link takes precedence over these two.
    linkOpen -> null
    radioInUse -> AppHint.RADIO_IN_USE
    pairing -> AppHint.PAIRING
    softwareNotice -> AppHint.RADIO_SOFTWARE_NOTICE
    // The question about the location after the update (ADR 0121 §10) can wait for everything else.
    locationShare -> AppHint.LOCATION_SHARE
    // „Immer“ taken away in the system's settings (ADR 0125 §5): no hurry either.
    alwaysLost -> AppHint.LOCATION_ALWAYS_LOST
    else -> null
}

@Composable
internal fun currentHint(controller: AppController, linkOpen: Boolean): AppHint? {
    val software = controller.radioSoftware
    val run by (software?.state ?: NoRun).collectAsState()
    val notice by (software?.notice ?: NoNotice).collectAsState()
    val rescueOpen by controller.usbRescueOpen.collectAsState()
    val setup by controller.deviceSetupPrompt.collectAsState()
    val pairing by controller.pairingHint.collectAsState()
    val radioInUse by controller.radioInUsePrompt.collectAsState()
    val radioArea by controller.radioAreaNeeded.collectAsState()
    val locationQuestion by controller.locationQuestion.collectAsState()
    val alwaysLost by (controller.locationAccess?.alwaysLost ?: NoLoss).collectAsState()
    // Not on top of a check-in that asks or warns: that comes first.
    val lifeSigns by (controller.lifeSigns?.statuses ?: NoLifeSigns).collectAsState()
    val lifeSignBusy = lifeSigns.any { it.phase == AliveStatus.ASK || it.phase == AliveStatus.HINT || it.phase == AliveStatus.ALARM }
    return pickHint(
        softwareRun = run is RadioSoftwareState.Installing || run is RadioSoftwareState.Done || run is RadioSoftwareState.Failed,
        rescueOpen = rescueOpen && controller.usbRescue != null,
        deviceSetup = setup != null,
        pairing = pairing > 0 && controller.canOpenBluetoothSettings,
        softwareNotice = notice != null,
        linkOpen = linkOpen,
        radioInUse = radioInUse,
        radioArea = radioArea,
        locationShare = locationQuestion && !lifeSignBusy,
        alwaysLost = alwaysLost && !lifeSignBusy,
    )
}

private val NoLoss = MutableStateFlow(false)
private val NoRun = MutableStateFlow<RadioSoftwareState>(RadioSoftwareState.Idle)
private val NoNotice = MutableStateFlow<Any?>(null)
private val NoLifeSigns = MutableStateFlow<List<AliveStatus>>(emptyList())
