package app.anynear.people

import app.anynear.contacts.Contact
import app.anynear.images.ImageRef
import app.anynear.mesh.PeerId

/**
 * A person in the chat, no matter by which path they can be reached: with anynear over the internet ([deviceId],
 * since ADR 0042 the person ID that the core accepts for all their devices), via their radio ([nodeNum]) or
 * both. Direct messages go over all paths.
 */
data class Person(
    val peer: String,
    val name: String,
    /** Person ID for the core (for apps without a person key the device ID), `null` without anynear. */
    val deviceId: String?,
    /** Device key for direct messages over the internet. */
    val publicKey: String?,
    /** Circles in which we know this person. Direct messages run through one of them. */
    val circleIds: List<String>,
    val nodeNum: Int?,
    /** Contact with a radio key (contact code), if present. */
    val radioContact: Contact?,
    /** The person's own picture, `null` = generated picture from [peer]. */
    val image: ImageRef? = null,
    /** The person's devices with anynear (device IDs), e.g. phone and tablet. */
    val devices: List<String> = listOfNotNull(deviceId),
    /** Name from the person's own profile; differs from [name] if one gave them an own name ([ContactNames]). */
    val profileName: String = name,
) {
    /** One gave the person an own name; [profileName] is shown small below it. */
    val hasOwnName: Boolean get() = name != profileName

    /**
     * Whether the person is in [circleId] (or, with [MemberDirectory.DIRECT_CONTACTS], a contact one added): by any of
     * its devices. [deviceId] is the person ID (ADR 0042), the entries of [members] carry device IDs.
     */
    fun isIn(members: List<Member>, circleId: String): Boolean =
        deviceId != null && members.any { it.circleId == circleId && (it.personId == deviceId || it.deviceId in devices) }
    companion object {
        /**
         * Person for [peer], also via one of their device IDs: a contact that was just scanned first has its device ID
         * as identifier, soon afterwards the core lists it under its person ID (ADR 0042). A conversation already
         * open then still carries the old identifier and must find the person anyway.
         */
        fun find(people: List<Person>, peer: String): Person? =
            people.firstOrNull { it.peer == peer } ?: PeerId.deviceIdOf(peer)?.let { device -> people.firstOrNull { device in it.devices } }

        /**
         * Merges app members (from all circles) and radio contacts. All devices of a person are
         * one person (ADR 0042), also in several circles; one's own further devices ([ownPerson]) are left out. If a member reports a radio that is also
         * stored as a contact, that is the same person. The name from the app applies: nobody
         * chooses the name of the radio deliberately, and a radio can (e.g. after being passed on)
         * be reported by several devices. An own name ([ownNames], person ID → name) covers the name from the profile.
         */
        fun merge(
            members: List<Member>,
            contacts: List<Contact>,
            circleOrder: List<String>,
            ownPerson: String? = null,
            ownNames: Map<String, String> = emptyMap(),
        ): List<Person> {
            val byNode = contacts.associateBy { it.nodeNum }
            val fromMembers = members.filter { it.personId != ownPerson }.groupBy { it.personId }.map { (personId, entries) ->
                val latest = entries.last()
                val node = entries.map { it.nodeNum }.firstOrNull { it != 0 }
                val contact = node?.let(byNode::get)
                val profileName = latest.name.ifBlank { contact?.displayName.orEmpty() }
                Person(
                    peer = PeerId.device(personId),
                    name = ownNames[personId] ?: profileName,
                    deviceId = personId,
                    publicKey = latest.publicKey,
                    circleIds = entries.map { it.circleId }.filter { it != MemberDirectory.DIRECT_CONTACTS }.distinct().sortedBy { circleOrder.indexOf(it) },
                    nodeNum = node,
                    radioContact = contact,
                    image = entries.maxByOrNull { it.imageTs }?.takeIf { it.imageTs > 0 }?.image,
                    devices = entries.map { it.deviceId }.distinct(),
                    profileName = profileName,
                )
            }
            val linkedNodes = fromMembers.mapNotNull { it.nodeNum }.toSet()
            val radioOnly = contacts.filter { it.nodeNum !in linkedNodes }.map { c ->
                Person(PeerId.node(c.nodeNum), c.displayName, null, null, emptyList(), c.nodeNum, c)
            }
            return (fromMembers + radioOnly).sortedBy { it.name.lowercase() }
        }
    }
}
