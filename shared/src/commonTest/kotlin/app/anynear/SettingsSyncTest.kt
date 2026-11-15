package app.anynear

import app.anynear.app.SettingsSync
import app.anynear.data.ObservedKeyValueStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Settings in the network (ADR 0106): which keys go where, and how they come back. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSyncTest {
    private class FakeTarget : SettingsSync.Target {
        val person = mutableMapOf<String, String>()
        val device = mutableMapOf<String, String>()
        var other: Map<String, ByteArray>? = null

        override suspend fun setPersonSettings(values: Map<String, ByteArray>) {
            values.forEach { (k, v) -> person[k] = v.decodeToString() }
        }

        override suspend fun setDeviceSettings(values: Map<String, ByteArray>) {
            values.forEach { (k, v) -> device[k] = v.decodeToString() }
        }

        override suspend fun takeOverDevice(deviceId: String): Map<String, ByteArray>? = other
    }

    @Test
    fun pushesCurrentValuesAndChangesToTheRightPlace() = runTest(UnconfinedTestDispatcher()) {
        val store = ObservedKeyValueStore(InMemoryStore())
        store.putString("files.sort", "NAME")
        val target = FakeTarget()
        val sync = SettingsSync(backgroundScope, store, target)
        sync.start()
        // Current values once: person ones with the prefix, device ones as they are, missing ones as removed.
        assertEquals("1NAME", target.person["kv:files.sort"])
        assertEquals("0", target.device["paired_device.identifier"])
        // Changes follow; keys of neither kind stay on the device.
        store.putString("paired_device.identifier", "usb:1")
        store.putString("lifesigns.circle", "c1")
        store.putString("usage_stats_last", "5")
        assertEquals("1usb:1", target.device["paired_device.identifier"])
        assertEquals("1c1", target.person["kv:lifesigns.circle"])
        assertFalse(target.device.containsKey("usage_stats_last"))
        assertFalse(target.person.keys.any { it.endsWith("usage_stats_last") })
        assertTrue(SettingsSync.PERSON.intersect(SettingsSync.DEVICE).isEmpty())
    }

    @Test
    fun applyPersonWritesOnlyPersonKeys() {
        val store = ObservedKeyValueStore(InMemoryStore())
        store.putString("lifesigns.single", "old")
        val sync = SettingsSync(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), store, FakeTarget())
        val changed = sync.applyPerson(
            mapOf(
                "kv:files.sort" to "1DATE".encodeToByteArray(),
                "kv:lifesigns.single" to "0".encodeToByteArray(),
                "kv:paired_device.identifier" to "1usb:9".encodeToByteArray(),
                "dark_mode" to "DARK".encodeToByteArray(),
            ),
        )
        assertTrue(changed)
        assertEquals("DATE", store.getString("files.sort"))
        assertNull(store.getString("lifesigns.single"))
        assertNull(store.getString("paired_device.identifier"))
        assertFalse(sync.applyPerson(mapOf("kv:files.sort" to "1DATE".encodeToByteArray())))
    }

    @Test
    fun takeOverWritesOnlyDeviceKeys() = runTest {
        val store = ObservedKeyValueStore(InMemoryStore())
        store.putString("alerts.region", "old")
        val target = FakeTarget()
        val sync = SettingsSync(backgroundScope, store, target)
        assertFalse(sync.takeOver("gone"))
        target.other = mapOf(
            "paired_device.name" to "1Heltec".encodeToByteArray(),
            "alerts.region" to "0".encodeToByteArray(),
            "files.sort" to "1NAME".encodeToByteArray(),
        )
        assertTrue(sync.takeOver("old-phone"))
        assertEquals("Heltec", store.getString("paired_device.name"))
        assertNull(store.getString("alerts.region"))
        assertNull(store.getString("files.sort"))
    }
}
