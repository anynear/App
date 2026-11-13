package app.anynear

import app.anynear.mesh.AndroidFirmwareFlasher
import kotlin.test.Test
import kotlin.test.assertEquals

class FirmwareFlasherAndroidTest {
    @Test
    fun bootloaderMayUseTheNextAddress() {
        assertEquals("AA:BB:CC:DD:EE:10", AndroidFirmwareFlasher.nextAddress("aa:bb:cc:dd:ee:0f"))
        assertEquals("AA:BB:CC:DD:EE:00", AndroidFirmwareFlasher.nextAddress("AA:BB:CC:DD:EE:FF"))
    }
}
