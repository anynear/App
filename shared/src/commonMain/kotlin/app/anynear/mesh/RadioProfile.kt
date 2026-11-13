package app.anynear.mesh

import app.anynear.AppConfig
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.ModuleSettings
import org.meshtastic.proto.User
import kotlin.math.abs

/**
 * Uniform device profile (Docs, ADR 0006).
 *
 * Every radio of the app has the same configuration in these values. Everything else (Wi-Fi, Bluetooth,
 * display, foreign channels) is not part of the profile and is never touched.
 */
object RadioProfile {
    const val DEVICE_TELEMETRY_INTERVAL_SECS = 3600

    /**
     * Transmit power 0 = firmware default: highest power allowed for the region, limited
     * by the hardware. The firmware does not know the antenna gain (ADR 0006, risks).
     */
    const val TX_POWER_REGION_MAX = 0

    /**
     * Instead of 0 the firmware stores the limit of the region and reports it back (EU_868: 27 dBm,
     * emulator test with Heltec V3, 19.10.2026). Both therefore count as device profile.
     */
    private val TX_POWER_ACCEPTED = setOf(TX_POWER_REGION_MAX, 27)

    private val ROUTER_ROLES = setOf(
        Config.DeviceConfig.Role.ROUTER,
        Config.DeviceConfig.Role.ROUTER_LATE,
        Config.DeviceConfig.Role.ROUTER_CLIENT,
        Config.DeviceConfig.Role.REPEATER,
    )

    /** What the device currently reports, summarised from configuration, channels and owner. */
    data class DeviceConfigSnapshot(
        val configs: List<Config>,
        val modules: List<ModuleConfig>,
        val channels: List<Channel>,
        val owner: User?,
    ) {
        val lora: Config.LoRaConfig? get() = configs.firstNotNullOfOrNull { it.lora }
        val device: Config.DeviceConfig? get() = configs.firstNotNullOfOrNull { it.device }
        val position: Config.PositionConfig? get() = configs.firstNotNullOfOrNull { it.position }
        val telemetry: ModuleConfig.TelemetryConfig? get() = modules.firstNotNullOfOrNull { it.telemetry }
        val notification: ModuleConfig.ExternalNotificationConfig? get() = modules.firstNotNullOfOrNull { it.external_notification }
    }

    fun check(snapshot: DeviceConfigSnapshot): DeviceSetupCheck {
        val deviations = deviations(snapshot)
        val role = snapshot.device?.role ?: Config.DeviceConfig.Role.CLIENT
        val kind = when {
            deviations.isEmpty() -> DeviceSetupCheck.Kind.OK
            isUnconfigured(snapshot) -> DeviceSetupCheck.Kind.NEW
            role in ROUTER_ROLES -> DeviceSetupCheck.Kind.ROUTER
            wasSetUpForApp(snapshot) -> DeviceSetupCheck.Kind.UPDATE
            else -> DeviceSetupCheck.Kind.DIFFERENT
        }
        return DeviceSetupCheck(kind, deviations, snapshot.owner?.long_name.orEmpty())
    }

    /** Already carries a name of the app (also an old `PYRSA …` or `PINA …`), so it has been set up once before. */
    fun wasSetUpForApp(snapshot: DeviceConfigSnapshot): Boolean = DeviceName.isOwnName(snapshot.owner?.long_name)

    /** Factory-new: without a region the device does not transmit at all. */
    fun isUnconfigured(snapshot: DeviceConfigSnapshot): Boolean =
        (snapshot.lora?.region ?: Config.LoRaConfig.RegionCode.UNSET) == Config.LoRaConfig.RegionCode.UNSET

    fun deviations(snapshot: DeviceConfigSnapshot): Set<ProfileDeviation> = buildSet {
        val lora = snapshot.lora
        if (lora == null || lora.region != Config.LoRaConfig.RegionCode.EU_868) add(ProfileDeviation.REGION)
        if (lora == null || !lora.use_preset || lora.modem_preset != Config.LoRaConfig.ModemPreset.SHORT_SLOW) add(ProfileDeviation.MODEM)
        if (lora == null || abs(lora.override_frequency - AppConfig.Hessenmesh.FREQUENCY_MHZ) > FREQUENCY_TOLERANCE_MHZ) add(ProfileDeviation.FREQUENCY)
        if (lora == null || lora.hop_limit != AppConfig.Hessenmesh.HOP_LIMIT) add(ProfileDeviation.HOPS)
        if (lora == null || !lora.tx_enabled) add(ProfileDeviation.TRANSMIT)
        if (lora == null || lora.tx_power !in TX_POWER_ACCEPTED) add(ProfileDeviation.TX_POWER)
        if (lora == null || !lora.config_ok_to_mqtt || lora.ignore_mqtt) add(ProfileDeviation.MQTT)

        if ((snapshot.device?.role ?: Config.DeviceConfig.Role.CLIENT) != Config.DeviceConfig.Role.CLIENT) add(ProfileDeviation.ROLE)

        val telemetry = snapshot.telemetry
        if (telemetry == null || !telemetry.device_telemetry_enabled || telemetry.device_update_interval != DEVICE_TELEMETRY_INTERVAL_SECS) {
            add(ProfileDeviation.TELEMETRY)
        }
        if (telemetry != null && (telemetry.environment_measurement_enabled || telemetry.air_quality_enabled ||
                telemetry.power_measurement_enabled || telemetry.health_measurement_enabled)
        ) {
            add(ProfileDeviation.SENSORS)
        }

        if (snapshot.notification?.let(::alertsOn) == true) add(ProfileDeviation.ALERTS)

        val position = snapshot.position
        val positionOn = position != null && (position.gps_mode == Config.PositionConfig.GpsMode.ENABLED ||
            position.fixed_position || position.position_broadcast_smart_enabled)
        if (positionOn || channelsSharingPosition(snapshot.channels).isNotEmpty()) add(ProfileDeviation.POSITION)

        // An old name `anynear.app …` (without separator), `anynear …`, `PYRSA …` or `PINA …` counts as a deviation: at the next setup it becomes `anynear.app | …`.
        if (!DeviceName.isCurrentName(snapshot.owner?.long_name)) add(ProfileDeviation.NAME)
    }

    /** Primary channel, circle channels and „Mesh Hessen“ (ADR 0055) that still send a location along. Foreign channels stay untouched. */
    fun channelsSharingPosition(channels: List<Channel>): List<Channel> =
        channels.filter { ch ->
            val name = ch.settings?.name
            val counts = ch.role == Channel.Role.PRIMARY ||
                (ch.role == Channel.Role.SECONDARY && (name == app.anynear.anmp.AnmpInfo.RADIO_CHANNEL_NAME || name == AppConfig.MeshHessen.NAME))
            counts && (ch.settings?.module_settings?.position_precision ?: 0) != 0
        }

    // --- Target values. Each based on the current values, so that fields outside the profile stay. ---

    fun lora(current: Config.LoRaConfig?): Config.LoRaConfig =
        (current?.newBuilder() ?: Config.LoRaConfig.Builder()).also {
            it.region = Config.LoRaConfig.RegionCode.EU_868
            it.use_preset = true
            it.modem_preset = Config.LoRaConfig.ModemPreset.SHORT_SLOW
            it.override_frequency = AppConfig.Hessenmesh.FREQUENCY_MHZ
            it.hop_limit = AppConfig.Hessenmesh.HOP_LIMIT
            it.tx_enabled = true
            it.tx_power = TX_POWER_REGION_MAX
            it.config_ok_to_mqtt = true
            it.ignore_mqtt = false
        }.build()

    /**
     * Only the MQTT values of the radio bridge (ADR 0054 §1): packets may go to the internet via a gateway, and what is
     * heard via MQTT is not discarded. A step of its own after [lora], because the firmware switches `ignore_mqtt` back
     * on with every new region with a duty-cycle limit (EU_868), even if the same write says "off".
     */
    fun mqtt(current: Config.LoRaConfig?): Config.LoRaConfig =
        (current?.newBuilder() ?: Config.LoRaConfig.Builder()).also {
            it.config_ok_to_mqtt = true
            it.ignore_mqtt = false
        }.build()

    /** Radio values that need the whole radio profile ([lora]); everything else in the LoRa part is only [ProfileDeviation.MQTT]. */
    val LORA_PARTS = setOf(
        ProfileDeviation.REGION, ProfileDeviation.MODEM, ProfileDeviation.FREQUENCY, ProfileDeviation.HOPS,
        ProfileDeviation.TRANSMIT, ProfileDeviation.TX_POWER,
    )

    /** What is written in the LoRa part in this pass. */
    enum class LoraWrite { PROFILE, MQTT }

    /**
     * First the radio profile; [ProfileDeviation.MQTT] alone only in the next pass after the restart, when region
     * and preset are in place (otherwise the firmware switches `ignore_mqtt` back on). `null`: nothing in the LoRa part
     */
    fun loraWrite(deviations: Set<ProfileDeviation>): LoraWrite? = when {
        deviations.any { it in LORA_PARTS } -> LoraWrite.PROFILE
        ProfileDeviation.MQTT in deviations -> LoraWrite.MQTT
        else -> null
    }

    fun device(current: Config.DeviceConfig?): Config.DeviceConfig =
        (current?.newBuilder() ?: Config.DeviceConfig.Builder()).also {
            it.role = Config.DeviceConfig.Role.CLIENT
        }.build()

    fun position(current: Config.PositionConfig?): Config.PositionConfig =
        (current?.newBuilder() ?: Config.PositionConfig.Builder()).also {
            // Without GPS hardware the firmware reports NOT_PRESENT; that stays as it is.
            if (current?.gps_mode != Config.PositionConfig.GpsMode.NOT_PRESENT) it.gps_mode = Config.PositionConfig.GpsMode.DISABLED
            it.fixed_position = false
            it.position_broadcast_smart_enabled = false
        }.build()

    fun telemetry(current: ModuleConfig.TelemetryConfig?): ModuleConfig.TelemetryConfig =
        (current?.newBuilder() ?: ModuleConfig.TelemetryConfig.Builder()).also {
            it.device_telemetry_enabled = true
            it.device_update_interval = DEVICE_TELEMETRY_INTERVAL_SECS
            it.environment_measurement_enabled = false
            it.air_quality_enabled = false
            it.power_measurement_enabled = false
            it.health_measurement_enabled = false
        }.build()

    /**
     * No signal (sound, vibration, LED) on the radio, neither for messages nor for the bell:
     * the app reports messages. Outputs and `enabled` stay as the firmware sets them for the device.
     */
    fun notification(current: ModuleConfig.ExternalNotificationConfig?): ModuleConfig.ExternalNotificationConfig =
        (current?.newBuilder() ?: ModuleConfig.ExternalNotificationConfig.Builder()).also {
            it.alert_message = false
            it.alert_message_buzzer = false
            it.alert_message_vibra = false
            it.alert_bell = false
            it.alert_bell_buzzer = false
            it.alert_bell_vibra = false
        }.build()

    private fun alertsOn(n: ModuleConfig.ExternalNotificationConfig): Boolean =
        n.alert_message || n.alert_message_buzzer || n.alert_message_vibra ||
            n.alert_bell || n.alert_bell_buzzer || n.alert_bell_vibra

    /** Channel without location. Name and key stay, also for the primary channel (Hessenmesh). */
    fun withoutPosition(channel: Channel): Channel {
        val settings = channel.settings ?: ChannelSettings.Builder().build()
        val module = (settings.module_settings?.newBuilder() ?: ModuleSettings.Builder()).also { it.position_precision = 0 }.build()
        return channel.newBuilder().also { it.settings = settings.newBuilder().also { s -> s.module_settings = module }.build() }.build()
    }

    fun owner(current: User?, nameSuffix: String): User =
        (current?.newBuilder() ?: User.Builder()).also {
            it.long_name = DeviceName.longName(nameSuffix)
            it.short_name = DeviceName.shortName(nameSuffix, current?.id)
        }.build()

    private const val FREQUENCY_TOLERANCE_MHZ = 0.001f
}

/**
 * Device name `anynear.app | <own part>`. The prefix always stays (branding, and the domain shows others in
 * the public network where the app is), only the part after it is free. The long name goes to everyone in
 * the public network, hence deliberately no personal name as default.
 *
 * Older devices are still called `anynear.app <own part>` (without separator) or `anynear <own part>`, or from the time before anynear `PYRSA <own part>`
 * or `PINA <own part>` (ADR 0025). Such names continue to count as own names (the part after it stays),
 * but are rewritten at the next setup (ADR 0061, decision 6).
 */
object DeviceName {
    const val PREFIX = "anynear.app | "

    /** Former prefixes, only for recognising, never for writing. */
    private val LEGACY_PREFIXES = listOf("anynear.app ", "anynear ", "PYRSA ", "PINA ")
    private val ALL_PREFIXES = listOf(PREFIX) + LEGACY_PREFIXES

    /** Meshtastic allows 39 bytes for the long name. */
    private const val LONG_NAME_MAX_BYTES = 39
    const val SUFFIX_MAX_BYTES = LONG_NAME_MAX_BYTES - PREFIX.length

    /** Words for the name suggestion. Never change the order: otherwise a device gets a different suggestion. */
    private val WORDS = listOf(
        "Ahorn", "Akelei", "Alpaka", "Amsel", "Anemone", "Apfel", "Aster", "Auerhahn", "Bach", "Baumfalke",
        "Beere", "Biber", "Birke", "Birne", "Blaumeise", "Buche", "Buchfink", "Bussard", "Dachs", "Distel",
        "Dohle", "Drossel", "Eiche", "Eichel", "Eisvogel", "Elch", "Elster", "Enzian", "Erle", "Esche",
        "Eule", "Falke", "Farn", "Fasan", "Feldhase", "Felsen", "Fichte", "Fink", "Fischotter", "Fjord",
        "Flieder", "Forelle", "Fuchs", "Gams", "Gecko", "Gimpel", "Ginster", "Habicht", "Hafer", "Hagebutte",
        "Hase", "Haselnuss", "Heide", "Hirsch", "Holunder", "Hummel", "Igel", "Iltis", "Iris", "Kamille",
        "Kastanie", "Kauz", "Kiebitz", "Kiefer", "Kirsche", "Kleiber", "Klee", "Kolibri", "Kormoran",
        "Kranich", "Krokus", "Kuckuck", "Lachs", "Lavendel", "Lerche", "Libelle", "Linde", "Lotus", "Luchs",
        "Lupine", "Marder", "Margerite", "Meise", "Milan", "Mispel", "Mohn", "Moos", "Narzisse", "Nelke",
        "Otter", "Pappel", "Pfau", "Pinie", "Quelle", "Rabe", "Reh", "Reiher", "Rose", "Rotkehlchen",
        "Salbei", "Schwalbe", "Schwan", "Seeadler", "Segler", "Sperber", "Specht", "Star", "Steinbock",
        "Stieglitz", "Storch", "Tanne", "Taube", "Teich", "Thymian", "Tulpe", "Uhu", "Ulme", "Veilchen",
        "Wacholder", "Waldkauz", "Walnuss", "Weide", "Wiesel", "Wildgans", "Zeisig", "Zeder", "Zypresse",
    )

    /** Name of the app, with current or former prefix. */
    fun isOwnName(longName: String?): Boolean = prefixOf(longName) != null

    /** Name with the current prefix, as the app writes it today. */
    fun isCurrentName(longName: String?): Boolean = prefixOf(longName) == PREFIX

    private fun prefixOf(longName: String?): String? =
        longName?.let { name -> ALL_PREFIXES.firstOrNull { name.startsWith(it) && name.length > it.length } }

    /**
     * Own part of an existing name (also of an old `anynear …`, `PYRSA …` or `PINA …`), otherwise `null`.
     * `anynear.app | ` is longer than the old prefixes; an old, fully used part is therefore shortened to
     * [SUFFIX_MAX_BYTES].
     */
    fun suffixOf(longName: String?): String? {
        val prefix = prefixOf(longName) ?: return null
        return fitBytes(longName!!.removePrefix(prefix), SUFFIX_MAX_BYTES).trimEnd()
    }

    /** Shortens to at most [max] bytes of UTF-8 without cutting a character apart. */
    private fun fitBytes(text: String, max: Int): String {
        var out = text
        while (out.encodeToByteArray().size > max) out = out.dropLast(1)
        return out
    }

    fun random(random: kotlin.random.Random = kotlin.random.Random): String =
        "${WORDS.random(random)} ${nodeIdSuffix(random.nextInt())}"

    /**
     * Suggestion from the node number of the radio: word plus the last four digits of the node ID,
     * as Meshtastic shows it (e.g. "Reh 1998" for `!9e9d1998`). The same device always gets
     * the same name, also on another phone. If the name is already taken among [taken]
     * (devices the radio has heard), the app takes the next word.
     */
    fun derived(nodeNum: Int, taken: Collection<String> = emptyList()): String {
        val used = taken.mapTo(HashSet()) { it.trim().lowercase() }
        val id = nodeIdSuffix(nodeNum)
        val start = (mix(nodeNum) % WORDS.size.toUInt()).toInt()
        for (step in WORDS.indices) {
            val candidate = "${WORDS[(start + step) % WORDS.size]} $id"
            if (ALL_PREFIXES.none { (it + normalize(candidate)).lowercase() in used }) return candidate
        }
        return "${WORDS[start]} $id"
    }

    /** Last four digits of the node ID in lower case, as in the Meshtastic default name. */
    fun nodeIdSuffix(nodeNum: Int): String = (nodeNum and 0xFFFF).toString(16).padStart(4, '0')

    /** Is [suffix] already known as the name of another device, also under the old prefix? */
    fun isTaken(suffix: String, taken: Collection<String>): Boolean =
        taken.any { name -> ALL_PREFIXES.any { name.trim().equals(it + normalize(suffix), ignoreCase = true) } }

    /** Spreads neighbouring node numbers over all words (Murmur3 finalizer). */    /** Spreads neighbouring node numbers over all suggestions (Murmur3 finalizer). */
    private fun mix(value: Int): UInt {
        var h = value.toUInt()
        h = h xor (h shr 16)
        h *= 0x85EBCA6Bu
        h = h xor (h shr 13)
        h *= 0xC2B2AE35u
        return h xor (h shr 16)
    }

    fun normalize(suffix: String): String = suffix.trim().replace(Regex("\\s+"), " ")

    fun isValidSuffix(suffix: String): Boolean {
        val s = normalize(suffix)
        return s.isNotEmpty() && s.encodeToByteArray().size <= SUFFIX_MAX_BYTES && shortCore(s).isNotEmpty()
    }

    fun longName(suffix: String): String = PREFIX + normalize(suffix)

    /**
     * Short name (Meshtastic allows 4 characters): `A` plus three characters from the last four digits
     * of the node ID ([userId] `!9e9d1998`). Each of the 65,536 identifiers yields a short name of its own.
     * Without a known ID: `A` plus the first three letters of the own part ("AFUC").
     */
    fun shortName(suffix: String, userId: String? = null): String {
        val id = userId?.removePrefix("!")?.takeIf { it.length in 4..8 }?.toLongOrNull(16)
            ?: return SHORT_PREFIX + shortCore(normalize(suffix)).take(3)
        return SHORT_PREFIX + idCode(id.toInt() and 0xFFFF)
    }

    /** First letter of the short name (anynear; previously `P` for PYRSA). */
    private const val SHORT_PREFIX = "A"

    /** Without easily confused characters (0/O/o, 1/l/I). 56³ ≥ 65,536, hence unique. */
    private const val ID_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz"

    /** Three characters from [ID_ALPHABET] for 16 bits, reversible and thus unique. */
    internal fun idCode(last16Bits: Int): String {
        val base = ID_ALPHABET.length
        return buildString {
            append(ID_ALPHABET[last16Bits / (base * base)])
            append(ID_ALPHABET[(last16Bits / base) % base])
            append(ID_ALPHABET[last16Bits % base])
        }
    }

    private fun shortCore(suffix: String): String =
        suffix.uppercase()
            .replace("Ä", "AE").replace("Ö", "OE").replace("Ü", "UE").replace("ß", "SS")
            .filter { it in 'A'..'Z' || it in '0'..'9' }
}
