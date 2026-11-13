package app.anynear

import app.anynear.anmp.core.ImageRef
import app.anynear.anmp.core.MemberState
import app.anynear.contacts.Contact
import app.anynear.people.Member
import app.anynear.people.MemberDirectory
import app.anynear.people.Person
import app.anynear.people.circleMembers
import app.anynear.people.ownDevicesIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Made-up test data, no real devices or people. */
class MemberDirectoryTest {
    private val member = Member("kreis-1", "geraet-b", "Testperson", "schluessel-b", nodeNum = 42)

    /** The directory mirrors the members and contacts in the ANMP core. */
    @Test
    fun mirrorsTheCore() = runTest {
        val image = ImageRef("bild", "schluessel", "space", "hash")
        val source = MutableStateFlow(
            listOf(
                MemberState("kreis-1", "geraet-b", "Testperson", "schluessel-b", "sign-b", node = 4_000_000_000, image = image, imageTs = 5),
                MemberState(MemberState.CONTACTS, "geraet-b", "Testperson", "schluessel-b"),
            ),
        )
        val directory = MemberDirectory(backgroundScope, source)
        val m = directory.find("kreis-1", "geraet-b")!!
        assertEquals(4_000_000_000L.toInt(), m.nodeNum)
        assertEquals(image, m.image)
        assertEquals("sign-b", directory.signKeyOf("geraet-b"))
        assertEquals("schluessel-b", directory.pinnedKey("geraet-b"))
        assertEquals(1, directory.of(MemberDirectory.DIRECT_CONTACTS).size)
    }

    @Test
    fun contactWithoutCircleIsAPersonWithoutCircles() {
        val contact = member.copy(circleId = MemberDirectory.DIRECT_CONTACTS)
        val person = Person.merge(listOf(contact), emptyList(), listOf("kreis-1")).single()
        assertEquals("Testperson", person.name)
        assertEquals("schluessel-b", person.publicKey)
        assertEquals(emptyList(), person.circleIds)
    }

    @Test
    fun appNameWinsOverRadioName() {
        // Two devices report the same radio (e.g. passed on); the radio contact renames neither.
        val other = Member("kreis-1", "geraet-c", "Zweite Person", "schluessel-c", nodeNum = 42)
        val radio = sharedContact(42, name = "Funkname")

        val people = Person.merge(listOf(member, other), listOf(radio), listOf("kreis-1"))
        assertEquals(setOf("Testperson", "Zweite Person"), people.map { it.name }.toSet())
    }
    @Test
    fun personIsFoundByTheDeviceIdOfAJustScannedContact() {
        // Conversation opened when only the device ID was known; now the core keeps the person under their person ID.
        val scanned = member.copy(circleId = MemberDirectory.DIRECT_CONTACTS, personId = "person-b")
        val people = Person.merge(listOf(scanned), emptyList(), emptyList())
        assertEquals("d:person-b", Person.find(people, "d:person-b")?.peer)
        assertEquals("d:person-b", Person.find(people, "d:" + member.deviceId)?.peer)
        assertEquals(null, Person.find(people, "d:fremdes-geraet"))
    }

    /** In the circle each person counts once; one's own further devices belong to the own line (ADR 0042). */
    @Test
    fun circleListsPeopleNotDevicesAndLeavesOutOwnDevices() {
        val list = listOf(
            Member("kreis-1", "eigenes-geraet", "Ich", "s1", personId = "ich"),
            Member("kreis-1", "eigenes-zweitgeraet", "Ich", "s2", personId = "ich"),
            Member("kreis-1", "handy-b", "Testperson", "s3", personId = "person-b"),
            Member("kreis-1", "tablet-b", "Testperson", "s4", personId = "person-b"),
            Member("kreis-2", "handy-c", "Andere Person", "s5", personId = "person-c"),
        )
        val others = circleMembers(list, "kreis-1", ownPerson = "ich", ownDevice = "eigenes-geraet")
        assertEquals(listOf("person-b"), others.map { it.personId })
        assertEquals(listOf("handy-b", "tablet-b"), others.single().devices)
        assertEquals(2, ownDevicesIn(list, "kreis-1", "ich", "eigenes-geraet"))
        assertEquals(1, ownDevicesIn(list, "kreis-2", "ich", "eigenes-geraet"))
        // Own person not yet known: at least this device stays out.
        assertEquals(setOf("ich", "person-b"), circleMembers(list, "kreis-1", null, "eigenes-geraet").map { it.personId }.toSet())
    }
}
