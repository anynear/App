package app.anynear

import app.anynear.people.Member
import app.anynear.people.Person
import app.anynear.people.radioDevicesOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** Devices of a person for a direct message over radio (hardware test 11.11.2026). */
class RadioDevicesTest {
    private fun member(device: String, node: Int = 0, circle: String = "c1") =
        Member(circleId = circle, deviceId = device, name = "B", publicKey = "k-$device", nodeNum = node, personId = "person-b")

    private fun person(node: Int?) = Person("p", "B", "person-b", "k", emptyList(), node, null)

    @Test
    fun devicesAtTheRadioComeFirstAndTheBrowserIsLeftOut() {
        // Phone at the radio, browser without radio, listed last: before, the browser could get the packet.
        val members = listOf(member("phone", node = 0x939d1998.toInt()), member("browser"))
        assertEquals(listOf("phone"), radioDevicesOf(person(0x939d1998.toInt()), members).map { it.deviceId })
    }

    @Test
    fun everyDeviceWithARadioGetsItOnce() {
        val members = listOf(member("old", node = 7), member("phone", node = 9), member("phone", node = 9, circle = "c2"), member("browser"))
        assertEquals(listOf("old", "phone"), radioDevicesOf(person(7), members).map { it.deviceId })
    }

    @Test
    fun withoutAnyKnownRadioAllDevicesUpToTheLimit() {
        val members = (1..5).map { member("d$it") }
        assertEquals(listOf("d5", "d4", "d3"), radioDevicesOf(person(null), members).map { it.deviceId })
    }

    @Test
    fun withoutAnynearNothing() {
        assertEquals(emptyList(), radioDevicesOf(Person("p", "B", null, null, emptyList(), 5, null), listOf(member("x", 5))))
    }
}
