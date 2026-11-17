package app.anynear

import app.anynear.circles.ProfileRepository
import app.anynear.contacts.Contact
import app.anynear.people.ContactNames
import app.anynear.people.Member
import app.anynear.people.Person
import app.anynear.people.circleMembers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Own names for other people (setting `contact_names`): only for oneself, on all own devices, covering the profile name. */
class ContactNamesTest {
    private fun member(person: String, name: String, circle: String = "k") =
        Member(circleId = circle, deviceId = "$person-dev", name = name, publicKey = "pk", nodeNum = 0, personId = person)

    @Test
    fun ownNameCoversProfileNameEverywhere() {
        val members = listOf(member("p1", "Hans"), member("p2", "Eva"))
        val people = Person.merge(members, emptyList<Contact>(), listOf("k"), ownNames = mapOf("p1" to "Opa"))
        val opa = people.single { it.deviceId == "p1" }
        assertEquals("Opa", opa.name)
        assertEquals("Hans", opa.profileName)
        assertTrue(opa.hasOwnName)
        assertFalse(people.single { it.deviceId == "p2" }.hasOwnName)
        val inCircle = circleMembers(members, "k", null, null, mapOf("p1" to "Opa"))
        assertEquals(listOf("Eva", "Opa"), inCircle.map { it.name })
        assertEquals("Hans", inCircle.single { it.personId == "p1" }.profileName)
    }

    @Test
    fun laterChangeWinsAndResetGoesAlong() {
        val phone = ProfileRepository(InMemoryStore())
        val tablet = ProfileRepository(InMemoryStore())
        phone.setContactName("p1", "  Opa  ", now = 10)
        assertEquals(mapOf("p1" to "Opa"), ContactNames.active(phone.contactNameEntries.value))
        tablet.applyPersonalSettings(phone.personalSettings())
        assertEquals(mapOf("p1" to "Opa"), ContactNames.active(tablet.contactNameEntries.value))

        // „Zurücksetzen“ on the tablet wins on the phone, an older state from elsewhere does not bring the name back.
        tablet.setContactName("p1", "", now = 20)
        assertFalse(phone.applyPersonalSettings(tablet.personalSettings()))
        assertEquals(emptyMap(), ContactNames.active(phone.contactNameEntries.value))
        assertTrue(phone.applyPersonalSettings(mapOf(ContactNames.SETTING_KEY to """{"p1":{"n":"Opa","t":10}}""")))
        assertEquals(emptyMap(), ContactNames.active(phone.contactNameEntries.value))
    }

    @Test
    fun mergeIsTheSameOnAllDevicesAndBrokenValuesAreIgnored() {
        val a = mapOf("p1" to ContactNames.Entry("A", 5), "p2" to ContactNames.Entry("X", 1))
        val b = mapOf("p1" to ContactNames.Entry("B", 5), "p3" to ContactNames.Entry("Y", 2))
        assertEquals(ContactNames.merge(a, b), ContactNames.merge(b, a))
        assertEquals("B", ContactNames.merge(a, b).getValue("p1").n)
        assertEquals(ContactNames.merge(a, b), ContactNames.decode(ContactNames.encode(ContactNames.merge(a, b))))
        assertEquals(emptyMap(), ContactNames.decode("kaputt"))
        assertEquals("x".repeat(ContactNames.MAX_NAME), ContactNames.clean("x".repeat(60)))
    }

    @Test
    fun sameNameAgainChangesNothing() {
        val phone = ProfileRepository(InMemoryStore())
        phone.setContactName("p1", "Opa", now = 10)
        phone.setContactName("p1", "Opa ", now = 30)
        assertEquals(10L, phone.contactNameEntries.value.getValue("p1").t)
        // A clock behind the stored change still makes the change the later one.
        phone.setContactName("p1", "Papa", now = 5)
        assertEquals(11L, phone.contactNameEntries.value.getValue("p1").t)
    }
}
