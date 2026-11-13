package app.anynear.people

import app.anynear.anmp.core.MemberState
import app.anynear.images.ImageRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A member of a circle or a contact ([MemberDirectory.DIRECT_CONTACTS]), one app installation. */
data class Member(
    val circleId: String,
    val deviceId: String,
    val name: String,
    /** Public X25519 key for direct messages, Base64url (pinned: the first one seen applies). */
    val publicKey: String,
    /** Radio of the member, 0 without a radio. */
    val nodeNum: Int = 0,
    /** The person's own picture (ADR 0012), `null` = generated picture. */
    val image: ImageRef? = null,
    /** When the person last changed their picture; the newest state applies. */
    val imageTs: Long = 0,
    /** Public Ed25519 key of the device, Base64url (ADR 0013); verifies signatures over radio (ADR 0021). */
    val signKey: String? = null,
    /** Person of this device (ADR 0042); for apps without a person key the device ID itself. */
    val personId: String = deviceId,
)

/**
 * Known members of all circles and contacts as an image of the ANMP core ([source]). The core pins the first
 * key seen for a device (trust on first use, across circles) and holds the grants for the
 * personal mailboxes; changes are only possible through it.
 */
class MemberDirectory(scope: CoroutineScope, source: StateFlow<List<MemberState>>) {
    val members: StateFlow<List<Member>> = source.map { list -> list.map(::toMember) }
        .stateIn(scope, SharingStarted.Eagerly, source.value.map(::toMember))

    fun of(circleId: String): List<Member> = members.value.filter { it.circleId == circleId }

    fun find(circleId: String, deviceId: String): Member? = members.value.firstOrNull { it.circleId == circleId && it.deviceId == deviceId }

    fun pinnedKey(deviceId: String): String? = members.value.firstOrNull { it.deviceId == deviceId }?.publicKey

    /** Person of a device (ADR 0042); unknown: the device itself. */
    fun personOf(deviceId: String): String = members.value.firstOrNull { it.deviceId == deviceId }?.personId ?: deviceId

    /** Devices of a person, from all circles and contacts. */
    fun devicesOf(personId: String): List<Member> = members.value.filter { it.personId == personId }.distinctBy { it.deviceId }

    /** Known signing key of a device, from any of its entries. */
    fun signKeyOf(deviceId: String): String? = members.value.firstNotNullOfOrNull { m -> m.signKey?.takeIf { m.deviceId == deviceId } }

    companion object {
        /** Pseudo-circle for contacts (contact link or whoever wrote to us directly), as in the core. */
        const val DIRECT_CONTACTS = MemberState.CONTACTS

        fun toMember(m: MemberState) = Member(
            circleId = m.circleId, deviceId = m.deviceId, name = m.name, publicKey = m.publicKey,
            nodeNum = m.node.toInt(), image = m.image, imageTs = m.imageTs, signKey = m.signKey, personId = m.person,
        )
    }
}

/** Someone else in a circle, all their devices together (ADR 0042). */
data class CircleMember(
    /** Person ID for the core (for apps without a person key the device ID). */
    val personId: String,
    val name: String,
    val image: ImageRef?,
    /** Their devices in this circle. */
    val devices: List<String>,
)

/**
 * The others in [circleId], one entry per person (ADR 0042): all devices of a person count once, one's own
 * devices ([ownPerson], [ownDevice]) are left out, since they are already the own line „(du)“. Name and picture as in
 * [Person.merge]: the name of the last entry, the picture changed last.
 */
fun circleMembers(members: List<Member>, circleId: String, ownPerson: String?, ownDevice: String?): List<CircleMember> =
    members.filter { it.circleId == circleId && !isOwn(it, ownPerson, ownDevice) }
        .groupBy { it.personId }
        .map { (personId, entries) ->
            CircleMember(
                personId = personId,
                name = entries.last().name,
                image = entries.maxByOrNull { it.imageTs }?.takeIf { it.imageTs > 0 }?.image,
                devices = entries.map { it.deviceId }.distinct(),
            )
        }
        .sortedBy { it.name.lowercase() }

/** How many of one's own devices are in [circleId], this one included. */
fun ownDevicesIn(members: List<Member>, circleId: String, ownPerson: String?, ownDevice: String?): Int =
    (members.filter { it.circleId == circleId && isOwn(it, ownPerson, ownDevice) }.map { it.deviceId } + listOfNotNull(ownDevice))
        .distinct().size

private fun isOwn(m: Member, ownPerson: String?, ownDevice: String?): Boolean =
    (ownPerson != null && m.personId == ownPerson) || (ownDevice != null && m.deviceId == ownDevice)
