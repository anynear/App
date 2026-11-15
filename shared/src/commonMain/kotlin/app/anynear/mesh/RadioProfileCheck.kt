package app.anynear.mesh

import app.anynear.anmp.core.radio.RadioProfile
import app.anynear.anmp.core.radio.RadioTarget
import org.meshtastic.proto.Config

// Uniform device profile (ADR 0006), device name and gateway values are in ANMP since ADR 0095 §5
// (`app.anynear.anmp.core.radio`), so that the app and the web app write the same values. Here only the comparison
// for the dialog „Funkgerät einrichten“.

/**
 * Comparison of [snapshot] with the device profile and the radio settings of the area ([target], ADR 0054 §1b), for
 * the dialog. [known]: settings of all areas of the radio list, for [DeviceSetupCheck.areaChange].
 */
fun RadioProfile.check(
    snapshot: RadioProfile.DeviceConfigSnapshot,
    target: RadioTarget = RadioTarget.DEFAULT,
    known: Collection<RadioTarget> = emptyList(),
): DeviceSetupCheck {
    val deviations = deviations(snapshot, target)
    val role = snapshot.device?.role ?: Config.DeviceConfig.Role.CLIENT
    val kind = when {
        deviations.isEmpty() -> DeviceSetupCheck.Kind.OK
        isUnconfigured(snapshot) -> DeviceSetupCheck.Kind.NEW
        role in RadioProfile.ROUTER_ROLES -> DeviceSetupCheck.Kind.ROUTER
        wasSetUpForApp(snapshot) -> DeviceSetupCheck.Kind.UPDATE
        else -> DeviceSetupCheck.Kind.DIFFERENT
    }
    return DeviceSetupCheck(kind, deviations, snapshot.owner?.long_name.orEmpty(), isAreaChange(snapshot, deviations, known))
}
