package app.anynear

import app.anynear.alerts.GeoPoint
import app.anynear.anmp.core.RadioList
import app.anynear.app.RadioAreas
import app.anynear.mesh.DeviceSetupCheck
import app.anynear.anmp.core.radio.ProfileDeviation
import app.anynear.anmp.core.radio.RadioProfile
import app.anynear.mesh.check
import app.anynear.anmp.core.radio.RadioProfile.DeviceConfigSnapshot
import app.anynear.anmp.core.radio.RadioTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Radio areas of the radio list (ADR 0054 §1b): settings per area, choice by location or by hand, silent change. */
class RadioAreasTest {
    private val list = RadioList.parse(
        """{"issued":1,"regions":[
            {"name":"Hessen","area":[[49.4,7.7],[51.7,7.7],[51.7,10.3],[49.4,10.3]],"lora":"EU_868","preset":"SHORT_SLOW","frequency":869.525},
            {"name":"Europa","area":[[34,-25],[72,-25],[72,24],[34,24]],"lora":"EU_868","preset":"LONG_FAST"},
            {"name":"Nordamerika","area":[[14,-170],[72,-170],[72,-52],[14,-52]],"lora":"US","preset":"LONG_FAST"}]}""",
    )
    private val europe = RadioTarget.of(list.region("Europa")!!)!!
    private val us = RadioTarget.of(list.region("Nordamerika")!!)!!
    private val known = list.regions.mapNotNull(RadioTarget::of)

    @Test
    fun hessenIsTheDefaultProfile() {
        val hessen = RadioTarget.of(list.region("Hessen")!!)!!
        assertEquals(RadioTarget.DEFAULT.copy(area = "Hessen"), hessen)
        assertEquals(RadioTarget.DEFAULT, RadioAreas.targetOf(null, null, null))
    }

    @Test
    fun loraFollowsTheArea() {
        val lora = RadioProfile.lora(null, us)
        assertEquals(Config.LoRaConfig.RegionCode.US, lora.region)
        assertEquals(Config.LoRaConfig.ModemPreset.LONG_FAST, lora.modem_preset)
        assertEquals(0f, lora.override_frequency, "default slot of region and preset")
        assertEquals(0, lora.tx_power)
        assertEquals(5, lora.hop_limit)
        assertEquals(emptySet(), RadioProfile.deviations(snapshot(lora), us))
        // The firmware reports the limit of the region instead of 0.
        assertEquals(emptySet(), RadioProfile.deviations(snapshot(lora.newBuilder().also { it.tx_power = 30 }.build()), us))
        assertEquals(setOf(ProfileDeviation.TX_POWER), RadioProfile.deviations(snapshot(lora.newBuilder().also { it.tx_power = 27 }.build()), us))
    }

    @Test
    fun radioOnAnotherAreaChangesWithoutAsking() {
        // Set up for Hessen (the default), now in Europe outside Hessen.
        val check = RadioProfile.check(snapshot(RadioProfile.lora(null)), europe, known)
        assertEquals(DeviceSetupCheck.Kind.UPDATE, check.kind)
        assertEquals(setOf(ProfileDeviation.MODEM, ProfileDeviation.FREQUENCY), check.deviations)
        assertTrue(check.areaChange)
        // Across the ocean also region and power change.
        assertTrue(RadioProfile.check(snapshot(RadioProfile.lora(null).newBuilder().also { it.tx_power = 27 }.build()), us, known).areaChange)
    }

    @Test
    fun otherDeviationsStillAsk() {
        // Changed by hand to a preset no area uses.
        val own = RadioProfile.lora(null).newBuilder().also { it.modem_preset = Config.LoRaConfig.ModemPreset.MEDIUM_FAST }.build()
        assertFalse(RadioProfile.check(snapshot(own), europe, known).areaChange)
        // Another deviation besides the area.
        val hops = RadioProfile.lora(null).newBuilder().also { it.hop_limit = 3 }.build()
        assertFalse(RadioProfile.check(snapshot(hops), europe, known).areaChange)
        // A radio not set up for the app.
        assertFalse(RadioProfile.check(snapshot(RadioProfile.lora(null), name = "Meshtastic 1234"), europe, known).areaChange)
        // Only MQTT after the restart is no area change.
        val mqttOnly = RadioProfile.lora(null, europe).newBuilder().also { it.ignore_mqtt = true }.build()
        assertFalse(RadioProfile.check(snapshot(mqttOnly), europe, known).areaChange)
    }

    @Test
    fun automaticFollowsTheLocationWithHysteresis() = runTest {
        var at = GeoPoint(50.11, 8.68)
        val store = InMemoryStore()
        val areas = areas(store) { at }
        areas.refresh()
        assertEquals("Hessen", areas.located.value)
        assertEquals("Hessen", areas.target.value.area)
        // 1 km south of Hessen: stays.
        at = GeoPoint(49.39, 9.5)
        areas.refresh()
        assertEquals("Hessen", areas.target.value.area)
        // Well outside: Europe, kept across a restart.
        at = GeoPoint(52.52, 13.40)
        areas.refresh()
        assertEquals("Europa", areas.target.value.area)
        assertEquals("Europa", areas(store) { null }.target.value.area)
        // Outside all areas the radio keeps its area.
        at = GeoPoint(35.68, 139.69)
        areas.refresh()
        assertEquals("Europa", areas.target.value.area)
    }

    @Test
    fun choiceByHandWinsAndNeedsNoLocation() = runTest {
        var asked = 0
        val store = InMemoryStore()
        val areas = areas(store) { asked++; GeoPoint(50.11, 8.68) }
        areas.choose("Nordamerika")
        areas.refresh()
        assertEquals(0, asked)
        assertEquals("Nordamerika", areas.target.value.area)
        assertEquals("Nordamerika", areas(store) { null }.choice.value)
        areas.choose(null)
        areas.refresh()
        assertEquals("Hessen", areas.target.value.area)
    }

    @Test
    fun noLocationWithoutPermission() = runTest {
        var asked = 0
        val areas = areas(InMemoryStore(), allowed = false) { asked++; GeoPoint(50.11, 8.68) }
        areas.refresh()
        assertEquals(0, asked)
        assertNull(areas.located.value)
        assertEquals(RadioTarget.DEFAULT, areas.target.value)
    }

    @Test
    fun areaGoneFromTheListFallsBack() {
        assertEquals("Hessen", RadioAreas.targetOf(list, "Weg", "Hessen").area)
        assertEquals(RadioTarget.DEFAULT, RadioAreas.targetOf(list, null, "Weg"))
        val unknownRegion = RadioList.parse("""{"issued":1,"regions":[{"name":"X","area":[[0,0],[1,0],[1,1]],"lora":"EU_868","preset":"LONG_FAST"}]}""")
        assertEquals("X", RadioAreas.targetOf(unknownRegion, "X", null).area)
    }

    private fun TestScope.areas(store: InMemoryStore, allowed: Boolean = true, locate: suspend () -> GeoPoint?) =
        RadioAreas(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)), store, MutableStateFlow(list), MutableStateFlow(allowed), locate)

    private fun snapshot(lora: Config.LoRaConfig, wifi: Boolean = false, name: String = "anynear.app | Fuchs 27") = DeviceConfigSnapshot(
        configs = listOf(
            Config.Builder().also { it.lora = lora }.build(),
            Config.Builder().also { it.device = RadioProfile.device(null) }.build(),
            Config.Builder().also { it.position = RadioProfile.position(null) }.build(),
            Config.Builder().also { it.network = Config.NetworkConfig.Builder().also { n -> n.wifi_enabled = wifi }.build() }.build(),
        ),
        modules = listOf(ModuleConfig.Builder().also { it.telemetry = RadioProfile.telemetry(null) }.build()),
        channels = listOf(
            Channel.Builder().also { c ->
                c.index = 0
                c.role = Channel.Role.PRIMARY
                c.settings = ChannelSettings.Builder().build()
            }.build(),
        ),
        owner = User.Builder().also { it.long_name = name }.build(),
    )
}
