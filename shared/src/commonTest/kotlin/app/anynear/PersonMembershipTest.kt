package app.anynear

import app.anynear.people.Member
import app.anynear.people.MemberDirectory
import app.anynear.people.Person
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A person with a key chain (ADR 0042) is listed under its person ID, its entries carry device IDs. Whether it is a
 * contact or in a circle must hold by person: otherwise „Kontakt teilen“ and „Kontakt entfernen“ were missing after
 * a contact request (device test 17.11.2026). Made-up test data.
 */
class PersonMembershipTest {
    private val contacts = MemberDirectory.DIRECT_CONTACTS

    @Test
    fun contactByPersonIdWithItsDevices() {
        val phone = Member(contacts, "geraet-handy", "Testperson Carla", "schluessel-1", personId = "person-carla")
        val tablet = Member("kreis-1", "geraet-tablet", "Testperson Carla", "schluessel-2", personId = "person-carla")
        val carla = Person.merge(listOf(phone, tablet), emptyList(), listOf("kreis-1")).single()
        assertTrue(carla.deviceId == "person-carla", "listed by person")
        assertTrue(carla.isIn(listOf(phone, tablet), contacts), "a contact, though the entry carries the device ID")
        assertTrue(carla.isIn(listOf(phone, tablet), "kreis-1"))
        assertFalse(carla.isIn(listOf(phone, tablet), "kreis-2"))
    }

    @Test
    fun onlyFromACircleIsNoContact() {
        val inCircle = Member("kreis-1", "geraet-ben", "Testperson Ben", "schluessel-3", personId = "person-ben")
        val ben = Person.merge(listOf(inCircle), emptyList(), listOf("kreis-1")).single()
        assertFalse(ben.isIn(listOf(inCircle), contacts))
    }

    @Test
    fun deviceWithoutKeyChainStillWorks() {
        val old = Member(contacts, "geraet-alt", "Testperson Alt", "schluessel-4")
        val person = Person.merge(listOf(old), emptyList(), emptyList()).single()
        assertTrue(person.isIn(listOf(old), contacts))
    }
}
