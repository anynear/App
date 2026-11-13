package app.anynear.people

/**
 * Devices of [person] for a direct packet over radio (ADR 0042, 0051, 0057). Radio address (route tag) and encryption
 * apply per device, not per person: a packet for the wrong device is dropped silently by the one at the radio. First
 * the devices at the person's radio, then those with any radio, newest first; only without any known radio all devices.
 * At most [max], so a person with many devices does not fill the radio network.
 */
fun radioDevicesOf(person: Person, members: List<Member>, max: Int = MAX_RADIO_DEVICES): List<Member> {
    val personId = person.deviceId ?: return emptyList()
    val devices = members.filter { it.personId == personId }.asReversed().distinctBy { it.deviceId }
    val atPersonRadio = devices.filter { person.nodeNum != null && it.nodeNum == person.nodeNum }
    val withRadio = devices.filter { it.nodeNum != 0 }
    val chosen = (atPersonRadio + withRadio).distinctBy { it.deviceId }.ifEmpty { devices }
    return chosen.take(max)
}

/** Devices of one person that get a direct message over radio, each its own packet. */
const val MAX_RADIO_DEVICES = 3
