package app.anynear.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The name typed when adding a device stays; only a device without a name gets the model name (tester report). */
class OwnDeviceNameTest {
    @Test
    fun typedNameStays() {
        assertNull(ownDeviceNameToSet("Tablet", "iPad"))
    }

    @Test
    fun withoutNameTheModelName() {
        assertEquals("iPhone", ownDeviceNameToSet("", "iPhone"))
        assertEquals("iPhone", ownDeviceNameToSet(null, "iPhone"))
        assertEquals("iPhone", ownDeviceNameToSet("  ", "iPhone"))
    }
}
