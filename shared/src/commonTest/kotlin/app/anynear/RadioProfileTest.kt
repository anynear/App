package app.anynear

import app.anynear.mesh.DeviceName
import app.anynear.mesh.DeviceSetupCheck
import app.anynear.mesh.RadioProfile
import app.anynear.mesh.RadioProfile.DeviceConfigSnapshot
import app.anynear.mesh.ProfileDeviation
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.ModuleSettings
import org.meshtastic.proto.User
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadioProfileTest {
    @Test
    fun deviceWrittenWithProfileIsCompliant() {
        val check = RadioProfile.check(compliant())
        assertEquals(DeviceSetupCheck.Kind.OK, check.kind)
        assertEquals(emptySet(), check.deviations)
    }

    @Test
    fun profileValues() {
        val lora = RadioProfile.lora(null)
        assertEquals(Config.LoRaConfig.RegionCode.EU_868, lora.region)
        assertEquals(Config.LoRaConfig.ModemPreset.SHORT_SLOW, lora.modem_preset)
        assertEquals(869.525f, lora.override_frequency)
        assertEquals(5, lora.hop_limit)
        assertTrue(lora.tx_enabled)
        assertEquals(0, lora.tx_power)
        assertEquals(Config.DeviceConfig.Role.CLIENT, RadioProfile.device(null).role)
        val telemetry = RadioProfile.telemetry(null)
        assertEquals(3600, telemetry.device_update_interval)
        assertTrue(telemetry.device_telemetry_enabled)
        assertFalse(telemetry.power_measurement_enabled || telemetry.environment_measurement_enabled || telemetry.air_quality_enabled)
    }

    @Test
    fun freshDeviceWithoutRegionIsNew() {
        val fresh = compliant().copy(configs = configs(lora = Config.LoRaConfig.Builder().build()))
        assertEquals(DeviceSetupCheck.Kind.NEW, RadioProfile.check(fresh).kind)
    }

    @Test
    fun oldHopLimitOnOwnDeviceIsAnUpdate() {
        val old = compliant().copy(configs = configs(lora = RadioProfile.lora(null).newBuilder().also { it.hop_limit = 3 }.build()))
        val check = RadioProfile.check(old)
        assertEquals(DeviceSetupCheck.Kind.UPDATE, check.kind)
        assertEquals(setOf(ProfileDeviation.HOPS), check.deviations)
    }

    @Test
    fun foreignDeviceWithOtherSettingsIsDifferent() {
        val foreign = compliant().copy(
            configs = configs(lora = RadioProfile.lora(null).newBuilder().also { it.hop_limit = 3 }.build()),
            owner = user("Meshtastic 3f2a"),
        )
        val check = RadioProfile.check(foreign)
        assertEquals(DeviceSetupCheck.Kind.DIFFERENT, check.kind)
        assertEquals(setOf(ProfileDeviation.HOPS, ProfileDeviation.NAME), check.deviations)
    }

    @Test
    fun fixedTransmitPowerIsADeviation() {
        val fixed = compliant().copy(configs = configs(lora = RadioProfile.lora(null).newBuilder().also { it.tx_power = 20 }.build()))
        assertEquals(setOf(ProfileDeviation.TX_POWER), RadioProfile.deviations(fixed))
        assertEquals(0, RadioProfile.lora(fixed.lora).tx_power)
        // After writing 0 the firmware reports back the region's limit.
        val stored = compliant().copy(configs = configs(lora = RadioProfile.lora(null).newBuilder().also { it.tx_power = 27 }.build()))
        assertEquals(emptySet(), RadioProfile.deviations(stored))
    }

    @Test
    fun routerIsFlaggedSeparately() {
        val router = compliant().copy(configs = configs(device = Config.DeviceConfig.Builder().also { it.role = Config.DeviceConfig.Role.ROUTER }.build()))
        val check = RadioProfile.check(router)
        assertEquals(DeviceSetupCheck.Kind.ROUTER, check.kind)
        assertEquals(setOf(ProfileDeviation.ROLE), check.deviations)
    }

    @Test
    fun telemetryAndSensorsAreChecked() {
        val slow = compliant().copy(modules = listOf(telemetryModule(RadioProfile.telemetry(null).newBuilder().also { it.device_update_interval = 1800 }.build())))
        assertEquals(setOf(ProfileDeviation.TELEMETRY), RadioProfile.deviations(slow))
        val power = compliant().copy(modules = listOf(telemetryModule(RadioProfile.telemetry(null).newBuilder().also { it.power_measurement_enabled = true }.build())))
        assertEquals(setOf(ProfileDeviation.SENSORS), RadioProfile.deviations(power))
    }

    @Test
    fun messageAndBellAlertsAreDeviations() {
        val firmwareDefault = ModuleConfig.ExternalNotificationConfig.Builder().also {
            it.enabled = true
            it.output_buzzer = 48
            it.use_pwm = true
            it.alert_message_buzzer = true
        }.build()
        val buzzing = compliant().copy(modules = compliant().modules + notificationModule(firmwareDefault))
        assertEquals(setOf(ProfileDeviation.ALERTS), RadioProfile.deviations(buzzing))
        val bell = compliant().copy(modules = compliant().modules + notificationModule(ModuleConfig.ExternalNotificationConfig.Builder().also { it.alert_bell = true }.build()))
        assertEquals(setOf(ProfileDeviation.ALERTS), RadioProfile.deviations(bell))

        val quiet = RadioProfile.notification(firmwareDefault)
        assertEquals(emptySet(), RadioProfile.deviations(compliant().copy(modules = compliant().modules + notificationModule(quiet))))
        // Outputs stay as the firmware set them for the device.
        assertTrue(quiet.enabled)
        assertEquals(48, quiet.output_buzzer)
    }

    @Test
    fun positionOnPrimaryOrCircleChannelCountsForeignChannelsDoNot() {
        val primary = compliant().copy(channels = listOf(channel(0, "", Channel.Role.PRIMARY, precision = 13)))
        assertEquals(setOf(ProfileDeviation.POSITION), RadioProfile.deviations(primary))
        val circle = compliant().copy(channels = compliant().channels + channel(1, "anynear", Channel.Role.SECONDARY, precision = 32))
        assertEquals(setOf(ProfileDeviation.POSITION), RadioProfile.deviations(circle))
        val foreign = compliant().copy(channels = compliant().channels + channel(2, "Wandergruppe", Channel.Role.SECONDARY, precision = 32))
        assertEquals(emptySet(), RadioProfile.deviations(foreign))
        val gps = compliant().copy(configs = configs(position = gps(Config.PositionConfig.GpsMode.ENABLED)))
        assertEquals(setOf(ProfileDeviation.POSITION), RadioProfile.deviations(gps))
    }

    @Test
    fun removingPositionKeepsNameAndKey() {
        val primary = channel(0, "", Channel.Role.PRIMARY, precision = 13).let {
            it.newBuilder().also { c -> c.settings = it.settings!!.newBuilder().also { s -> s.psk = "AQ==".encodeUtf8() }.build() }.build()
        }
        val cleaned = RadioProfile.withoutPosition(primary)
        assertEquals(0, cleaned.settings?.module_settings?.position_precision)
        assertEquals(primary.settings?.psk, cleaned.settings?.psk)
        assertEquals(primary.settings?.name, cleaned.settings?.name)
        assertEquals(Channel.Role.PRIMARY, cleaned.role)
    }

    @Test
    fun gpsWithoutHardwareStaysNotPresent() {
        val position = RadioProfile.position(gps(Config.PositionConfig.GpsMode.NOT_PRESENT))
        assertEquals(Config.PositionConfig.GpsMode.NOT_PRESENT, position.gps_mode)
        assertEquals(Config.PositionConfig.GpsMode.DISABLED, RadioProfile.position(gps(Config.PositionConfig.GpsMode.ENABLED)).gps_mode)
    }

    @Test
    fun nameWithoutPrefixIsADeviation() {
        val named = compliant().copy(owner = user("Meshtastic 3f2a"))
        assertEquals(setOf(ProfileDeviation.NAME), RadioProfile.deviations(named))
    }

    @Test
    fun oldNameIsAnUpdateThatKeepsTheOwnPart() {
        val old = compliant().copy(owner = user("PINA Fuchs 27"))
        val check = RadioProfile.check(old)
        assertEquals(DeviceSetupCheck.Kind.UPDATE, check.kind)
        assertEquals(setOf(ProfileDeviation.NAME), check.deviations)
        assertTrue(DeviceName.isOwnName("PINA Fuchs 27"))
        assertFalse(DeviceName.isCurrentName("PINA Fuchs 27"))
        assertEquals("Fuchs 27", DeviceName.suffixOf("PINA Fuchs 27"))
        assertEquals("anynear.app | Fuchs 27", DeviceName.longName(DeviceName.suffixOf("PINA Fuchs 27")!!))
    }

    @Test
    fun formerNameIsAnUpdateThatKeepsTheOwnPart() {
        // ADR 0061, decision 6: `PYRSA …` becomes `anynear.app | …` during setup.
        val old = compliant().copy(owner = user("PYRSA Fuchs 27"))
        val check = RadioProfile.check(old)
        assertEquals(DeviceSetupCheck.Kind.UPDATE, check.kind)
        assertEquals(setOf(ProfileDeviation.NAME), check.deviations)
        assertEquals("anynear.app | Fuchs 27", DeviceName.longName(DeviceName.suffixOf("PYRSA Fuchs 27")!!))
    }

    @Test
    fun plainAnynearNameBecomesAnynearApp() {
        val old = compliant().copy(owner = user("anynear Fuchs 27"))
        val check = RadioProfile.check(old)
        assertEquals(DeviceSetupCheck.Kind.UPDATE, check.kind)
        assertEquals(setOf(ProfileDeviation.NAME), check.deviations)
        assertTrue(DeviceName.isOwnName("anynear Fuchs 27"))
        assertFalse(DeviceName.isCurrentName("anynear Fuchs 27"))
        assertTrue(DeviceName.isCurrentName("anynear.app | Fuchs 27"))
        assertEquals("anynear.app | Fuchs 27", DeviceName.longName(DeviceName.suffixOf("anynear Fuchs 27")!!))
        assertFalse(DeviceName.isCurrentName("anynear.app Fuchs 27"))
        assertEquals("Fuchs 27", DeviceName.suffixOf("anynear.app Fuchs 27"))
    }

    @Test
    fun mqttValuesBelongToTheProfile() {
        val lora = RadioProfile.lora(null)
        assertTrue(lora.config_ok_to_mqtt)
        assertFalse(lora.ignore_mqtt)
        val ignoring = compliant().copy(configs = configs(lora = lora.newBuilder().also { it.ignore_mqtt = true }.build()))
        assertEquals(setOf(ProfileDeviation.MQTT), RadioProfile.deviations(ignoring))
        val notOk = compliant().copy(configs = configs(lora = lora.newBuilder().also { it.config_ok_to_mqtt = false }.build()))
        assertEquals(DeviceSetupCheck.Kind.UPDATE, RadioProfile.check(notOk).kind)
        val mqtt = RadioProfile.mqtt(lora.newBuilder().also { it.ignore_mqtt = true; it.config_ok_to_mqtt = false }.build())
        assertTrue(mqtt.config_ok_to_mqtt)
        assertFalse(mqtt.ignore_mqtt)
        assertEquals(lora.region, mqtt.region)
    }

    @Test
    fun mqttIsWrittenOnlyAfterRegionAndPreset() {
        // The firmware switches `ignore_mqtt` back on for a new region: first the radio profile, MQTT in the next pass.
        assertEquals(RadioProfile.LoraWrite.PROFILE, RadioProfile.loraWrite(setOf(ProfileDeviation.REGION, ProfileDeviation.MQTT)))
        assertEquals(RadioProfile.LoraWrite.PROFILE, RadioProfile.loraWrite(setOf(ProfileDeviation.MODEM)))
        assertEquals(RadioProfile.LoraWrite.MQTT, RadioProfile.loraWrite(setOf(ProfileDeviation.MQTT, ProfileDeviation.NAME)))
        assertEquals(null, RadioProfile.loraWrite(setOf(ProfileDeviation.NAME)))
    }

    @Test
    fun fullOldNameIsShortenedToFitTheLongerPrefix() {
        val ownPart = "ä".repeat(17) // 34 bytes, under „PINA “ exactly 39 bytes
        val suffix = DeviceName.suffixOf("PINA $ownPart")!!
        assertTrue(DeviceName.isValidSuffix(suffix))
        assertEquals("ä".repeat(12), suffix)
        assertTrue(DeviceName.longName(suffix).encodeToByteArray().size <= 39)
    }

    @Test
    fun deviceNames() {
        assertEquals("anynear.app | Fuchs 27", DeviceName.longName("  Fuchs   27 "))
        assertEquals("AFUC", DeviceName.shortName("Fuchs 27"))
        assertEquals("A" + DeviceName.idCode(0x1998), DeviceName.shortName("Reh 1998", userId = "!9e9d1998"))
        assertEquals("A222", DeviceName.shortName("Öl", userId = "!00000000"))
        assertEquals("AFUC", DeviceName.shortName("Fuchs 27", userId = "kaputt"))
        assertEquals("AOEL", DeviceName.shortName("Ölmühle"))
        assertEquals("A27", DeviceName.shortName("27"))
        assertEquals("Küche", DeviceName.suffixOf("anynear.app | Küche"))
        assertEquals("Küche", DeviceName.suffixOf("anynear Küche"))
        assertEquals("Küche", DeviceName.suffixOf("PYRSA Küche"))
        assertEquals(null, DeviceName.suffixOf("Küche"))
        assertFalse(DeviceName.isValidSuffix(""))
        assertFalse(DeviceName.isValidSuffix("!!!"))
        assertFalse(DeviceName.isValidSuffix("x".repeat(DeviceName.SUFFIX_MAX_BYTES + 1)))
        assertTrue(DeviceName.isValidSuffix("x".repeat(DeviceName.SUFFIX_MAX_BYTES)))
        val random = DeviceName.random(Random(1))
        assertTrue(DeviceName.isValidSuffix(random))
        assertTrue(DeviceName.isOwnName(DeviceName.longName(random)))
    }

    @Test
    fun derivedNameIsStablePerDeviceAndEndsWithNodeId() {
        val node = 0x9E9D1998.toInt()
        assertEquals(DeviceName.derived(node), DeviceName.derived(node))
        assertTrue(DeviceName.derived(node).endsWith(" 1998"))
        assertTrue(DeviceName.derived(0x11093E42).endsWith(" 3e42"))
        assertTrue(DeviceName.derived(0x00000007).endsWith(" 0007"))
        assertTrue(DeviceName.isValidSuffix(DeviceName.derived(node)))
        // Neighbouring node numbers get different words.
        val words = (0 until 20).map { DeviceName.derived(node + it).substringBefore(' ') }.toSet()
        assertTrue(words.size > 10)
    }

    @Test
    fun derivedNameSkipsNamesHeardNearby() {
        val node = 4711
        val first = DeviceName.derived(node)
        val second = DeviceName.derived(node, taken = listOf(DeviceName.longName(first)))
        assertTrue(first != second)
        assertEquals(first.substringAfter(' '), second.substringAfter(' ')) // Node ID stays, only the word changes
        // Upper/lower case and spaces do not count.
        assertEquals(second, DeviceName.derived(node, taken = listOf("  " + DeviceName.longName(first).uppercase())))
        assertTrue(DeviceName.isTaken(first, listOf(DeviceName.longName(first))))
        assertFalse(DeviceName.isTaken(second, listOf(DeviceName.longName(first))))
        // A device still called „PINA …“ occupies the name just the same.
        assertTrue(DeviceName.isTaken(first, listOf("PINA $first")))
        assertEquals(second, DeviceName.derived(node, taken = listOf("PINA $first")))
    }

    @Test
    fun shortNameIsUniquePerNodeIdAndReadable() {
        val codes = (0..0xFFFF).map { DeviceName.idCode(it) }
        assertEquals(65536, codes.toSet().size)
        assertTrue(codes.all { it.length == 3 && it.none { c -> c in "0Oo1lI" } })
    }

    @Test
    fun ownerKeepsIdentity() {
        val owner = RadioProfile.owner(user("Alt", id = "!1234abcd"), "Linde 12")
        assertEquals("!1234abcd", owner.id)
        assertEquals("anynear.app | Linde 12", owner.long_name)
        assertEquals("A" + DeviceName.idCode(0xabcd), owner.short_name)
        assertEquals(4, owner.short_name.length)
        assertEquals("ALIN", RadioProfile.owner(null, "Linde 12").short_name)
    }

    private fun compliant() = DeviceConfigSnapshot(
        configs = configs(),
        modules = listOf(telemetryModule(RadioProfile.telemetry(null))),
        channels = listOf(channel(0, "", Channel.Role.PRIMARY, precision = 0)),
        owner = user("anynear.app | Fuchs 27"),
    )

    private fun configs(
        lora: Config.LoRaConfig = RadioProfile.lora(null),
        device: Config.DeviceConfig = RadioProfile.device(null),
        position: Config.PositionConfig = RadioProfile.position(null),
    ) = listOf(
        Config.Builder().also { it.lora = lora }.build(),
        Config.Builder().also { it.device = device }.build(),
        Config.Builder().also { it.position = position }.build(),
    )

    private fun telemetryModule(telemetry: ModuleConfig.TelemetryConfig) = ModuleConfig.Builder().also { it.telemetry = telemetry }.build()

    private fun notificationModule(n: ModuleConfig.ExternalNotificationConfig) = ModuleConfig.Builder().also { it.external_notification = n }.build()

    private fun gps(mode: Config.PositionConfig.GpsMode) = Config.PositionConfig.Builder().also { it.gps_mode = mode }.build()

    private fun user(longName: String, id: String = "") = User.Builder().also {
        it.id = id
        it.long_name = longName
    }.build()

    private fun channel(index: Int, name: String, role: Channel.Role, precision: Int) = Channel.Builder().also { c ->
        c.index = index
        c.role = role
        c.settings = ChannelSettings.Builder().also {
            it.name = name
            it.module_settings = ModuleSettings.Builder().also { m -> m.position_precision = precision }.build()
        }.build()
    }.build()
}
