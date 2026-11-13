package app.anynear.mesh

import app.anynear.anmp.core.firmware.FirmwarePath
import app.anynear.AppConfig
import app.anynear.app.AppLog
import app.anynear.core.sha256
import app.anynear.contacts.Contact
import app.anynear.i18n.texts
import app.anynear.anmp.Anmp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel as Queue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Data
import org.meshtastic.proto.Config
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.ModuleSettings
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.OTAMode
import org.meshtastic.proto.PortNum
import org.meshtastic.sdk.MessageHandle
import org.meshtastic.proto.Routing
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.AutoReconnectConfig
import org.meshtastic.sdk.ChannelIndex
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.LogLevel
import org.meshtastic.sdk.LogSink
import org.meshtastic.sdk.MeshEvent
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RadioTransport
import org.meshtastic.sdk.asRouting
import org.meshtastic.sdk.asText
import org.meshtastic.sdk.connectAndAwaitReady
import org.meshtastic.sdk.nodeMap
import org.meshtastic.sdk.storage.sqldelight.SqlDelightStorageProvider
import org.meshtastic.sdk.textMessages
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * [MeshLink] based on meshtastic-sdk.
 *
 * Connection strategy: the SDK takes over reconnection after drops
 * ([AutoReconnectConfig]). If even the first connection attempt fails (device off,
 * out of range), this class retries with growing intervals.
 */
class SdkMeshLink(
    private val scope: CoroutineScope,
    private val dataDir: String,
    private val transportFactory: suspend (PairedDevice) -> RadioTransport = { createTransport(it) },
    /** Diagnostic log. By default the SDK does not log message contents. */
    private val appLog: AppLog = AppLog.Silent,
    /** Transfers new software to the radio (ADR 0056); `null` where the platform cannot do it. */
    private val flasher: FirmwareFlasher? = platformFirmwareFlasher { appLog.log(app.anynear.app.LogLevel.INFO, "App", "Aktualisierung: $it", null) },
) : MeshLink {

    private val logSink = LogSink { level, tag, message, cause ->
        val appLevel = when (level) {
            LogLevel.ERROR -> app.anynear.app.LogLevel.ERROR
            LogLevel.WARN -> app.anynear.app.LogLevel.WARN
            LogLevel.INFO -> app.anynear.app.LogLevel.INFO
            else -> app.anynear.app.LogLevel.DEBUG
        }
        appLog.log(appLevel, tag, message, cause)
    }

    private fun log(message: String, cause: Throwable? = null) = logSink.log(LogLevel.INFO, "App", message, cause)

    private val _state = MutableStateFlow<LinkState>(LinkState.NoDevice)
    override val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _ownDevice = MutableStateFlow<OwnDevice?>(null)
    override val ownDevice: StateFlow<OwnDevice?> = _ownDevice.asStateFlow()

    private val _channels = MutableStateFlow<List<RadioChannel>?>(null)
    override val circleChannels: StateFlow<List<RadioChannel>?> = _channels.asStateFlow()

    /** Slots of all active channels on the radio (primary and secondary), for replies to radios without anynear. */
    private val _activeSlots = MutableStateFlow<Set<Int>>(emptySet())

    private val _openChannel0 = MutableStateFlow(false)
    override val openChannel0: StateFlow<Boolean> = _openChannel0.asStateFlow()

    private val _oldChannels = MutableStateFlow<List<String>>(emptyList())
    override val oldCircleChannels: StateFlow<List<String>> = _oldChannels.asStateFlow()

    private val _otherSlots = MutableStateFlow(0)
    override val otherChannelSlots: StateFlow<Int> = _otherSlots.asStateFlow()

    private val _firmware = MutableStateFlow<RadioFirmware?>(null)
    override val firmware: StateFlow<RadioFirmware?> = _firmware.asStateFlow()

    private val _setupCheck = MutableStateFlow<DeviceSetupCheck?>(null)
    override val setupCheck: StateFlow<DeviceSetupCheck?> = _setupCheck.asStateFlow()

    /** Last known own NodeInfo, regardless of whether the handshake is currently completed. */
    private var ownNode: NodeInfo? = null

    private val _names = MutableStateFlow<Map<Int, String>>(emptyMap())
    override val knownNames: StateFlow<Map<Int, String>> = _names.asStateFlow()

    private val _incoming = MutableSharedFlow<IncomingText>(extraBufferCapacity = 64)
    override val incoming: Flow<IncomingText> = _incoming.asSharedFlow()
    private val _incomingData = MutableSharedFlow<IncomingData>(extraBufferCapacity = 64)
    override val incomingData: Flow<IncomingData> = _incomingData.asSharedFlow()

    private val _heardNodes = MutableSharedFlow<HeardNode>(extraBufferCapacity = 64)
    override val heardNodes: Flow<HeardNode> = _heardNodes.asSharedFlow()

    private val _publicChannels = MutableStateFlow<List<PublicRadioChannel>>(emptyList())
    override val publicChannels: StateFlow<List<PublicRadioChannel>> = _publicChannels.asStateFlow()
    private val _incomingPublic = MutableSharedFlow<IncomingPublicText>(extraBufferCapacity = 64)
    override val incomingPublic: Flow<IncomingPublicText> = _incomingPublic.asSharedFlow()

    /** Only set as long as the handshake is completed. */
    private val client = MutableStateFlow<RadioClient?>(null)
    private var loopJob: Job? = null
    private val inForeground = MutableStateFlow(true)

    override fun setInForeground(visible: Boolean) {
        inForeground.value = visible
    }

    /** Radio last connected with [start], for reconnecting after an update. */
    private var current: PairedDevice? = null

    override fun start(device: PairedDevice) {
        current = device
        loopJob?.cancel()
        _channels.value = null
        _activeSlots.value = emptySet()
        _openChannel0.value = false
        _ownDevice.value = null
        _firmware.value = null
        _setupCheck.value = null
        ownNode = null
        loopJob = scope.launch { runConnectionLoop(device) }
    }

    override suspend fun stop() {
        loopJob?.cancelAndJoin()
        loopJob = null
        _setupCheck.value = null
        _state.value = LinkState.NoDevice
    }

    override suspend fun yieldRadio() {
        loopJob?.cancelAndJoin()
        loopJob = null
        _setupCheck.value = null
        _state.value = LinkState.InUseElsewhere
        log("Funkgerät wird woanders genutzt: nicht mehr selbst verbinden")
    }

    override suspend fun probe(device: PairedDevice): Boolean {
        var rc: RadioClient? = null
        return try {
            // Outside the time limit: on Android this waits for the pairing (PIN), which takes as long as it takes.
            val transport = transportFactory(device)
            withTimeout(PROBE_TIMEOUT) {
                rc = RadioClient.Builder()
                    .transport(transport)
                    .storage(SqlDelightStorageProvider(baseDir = dataDir))
                    .skipNodeDb()
                    .build()
                rc?.connect()
                true
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) false else throw e
        } catch (e: Exception) {
            false
        } finally {
            withContext(NonCancellable) { rc?.disconnect() }
        }
    }

    private suspend fun runConnectionLoop(device: PairedDevice) = coroutineScope {
        var failures = 0
        while (isActive) {
            _state.value = if (failures == 0) LinkState.Connecting else LinkState.Searching

            var rc: RadioClient? = null
            try {
                rc = RadioClient.Builder()
                    .transport(transportFactory(device))
                    .storage(SqlDelightStorageProvider(baseDir = dataDir))
                    // Limited: afterwards the loop here takes over, which searches less often in the background.
                    .autoReconnect(AutoReconnectConfig(maxBackoff = 30.seconds, maxAttempts = SDK_RECONNECT_ATTEMPTS))
                    .logger(logSink)
                    .build()
                val session = launch { observe(rc) }
                try {
                    rc.connectAndAwaitReady()
                    failures = 0
                    client.value = rc
                    // Anew after every connect: after an update (ADR 0056) the device reports a new version.
                    launch { readFirmware(rc) }
                    // Stays here until the SDK gives up for good. AutoReconnect bridges drops.
                    rc.connection.first { it is ConnectionState.Disconnected }
                } finally {
                    session.cancel()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                log("Verbindung fehlgeschlagen (Versuch $failures)", e)
            } finally {
                client.value = null
                withContext(NonCancellable) { rc?.disconnect() }
            }
            _state.value = LinkState.Searching
            delay(retryDelaySeconds(failures).seconds)
            // Radio not reachable for a longer time (left at home) and app in the background: do not search over
            // Bluetooth every 30 seconds. When the app comes to the front, it continues immediately.
            if (failures >= BACKGROUND_SLOWDOWN_AFTER && !inForeground.value) {
                withTimeoutOrNull(BACKGROUND_RETRY_PAUSE) { inForeground.first { it } }
            }
        }
    }

    override val firmwarePaths: Set<FirmwarePath> get() = flasher?.paths.orEmpty()

    /**
     * ADR 0056 §2 step 5. nRF52: disconnect the own connection, the DFU library jumps into the bootloader itself.
     * ESP32: `ota_request` with path and SHA-256 of the file; only when the device reports "Rebooting to …" (or nothing
     * at all, older firmware), disconnect and transfer to the loader. Then connect again.
     */
    override suspend fun installFirmware(path: FirmwarePath, image: ByteArray, onProgress: (Float) -> Unit): FirmwareInstallResult {
        val flasher = flasher?.takeIf { path in it.paths } ?: return FirmwareInstallResult.Unsupported
        val device = current ?: return FirmwareInstallResult.Failed(wroteAnything = false)
        val name = _ownDevice.value?.longName ?: device.name
        return when (path) {
            FirmwarePath.NRF52_DFU -> {
                if (NetworkTransport.isNetworkDevice(device)) return FirmwareInstallResult.Unsupported
                releaseForUpdate()
                // From here on the device jumps into the bootloader: no more aborting (ADR 0056 §2 step 5).
                onProgress(0f)
                try {
                    flasher.nrf52Dfu(device.identifier, name, image, onProgress)
                } finally {
                    start(device)
                }
            }
            FirmwarePath.ESP32_WIFI, FirmwarePath.ESP32_BLE -> {
                val rc = client.value ?: return FirmwareInstallResult.Failed(wroteAnything = false)
                val sha = sha256(image)
                val target = if (path == FirmwarePath.ESP32_WIFI) {
                    OtaTarget.Wifi(NetworkTransport.addressOf(device)?.host ?: return FirmwareInstallResult.Unsupported)
                } else {
                    OtaTarget.Ble(device.identifier)
                }
                if (!requestOta(rc, path, sha)) return FirmwareInstallResult.Failed(wroteAnything = false)
                // The device starts into the loader: no more aborting.
                onProgress(0f)
                releaseForUpdate()
                try {
                    flasher.esp32Ota(target, image, sha.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }, onProgress)
                } finally {
                    start(device)
                }
            }
            FirmwarePath.USB -> FirmwareInstallResult.Unsupported
        }
    }

    /**
     * Sends `ota_request` and waits up to [OTA_CONFIRM_WAIT] for the answer of the firmware. "Rebooting to …" means
     * go; any other message about the update (no loader, wrong path, wrong hash) means no.
     */
    private suspend fun requestOta(rc: RadioClient, path: FirmwarePath, sha: ByteArray): Boolean = coroutineScope {
        val reply = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(OTA_CONFIRM_WAIT) {
                rc.events.filterIsInstance<MeshEvent.Notification>()
                    .map { it.notification.message }
                    .first { it.startsWith("Rebooting to") || it.contains("OTA") }
            }
        }
        val mode = if (path == FirmwarePath.ESP32_WIFI) OTAMode.OTA_WIFI else OTAMode.OTA_BLE
        val sent = rc.admin.otaRequest(AdminMessage.OTAEvent.Builder().reboot_ota_mode(mode).ota_hash(sha.toByteString()).build())
        if (sent !is AdminResult.Success) {
            reply.cancel()
            log("Aktualisierung: ota_request nicht angenommen ($sent)")
            return@coroutineScope false
        }
        val message = reply.await()
        log("Aktualisierung: Antwort des Funkgeräts: ${message ?: "keine"}")
        message == null || message.startsWith("Rebooting to")
    }

    /** Release the own connection so that bootloader or loader are free; Meshtastic waits briefly afterwards. */
    private suspend fun releaseForUpdate() {
        loopJob?.cancelAndJoin()
        loopJob = null
        _state.value = LinkState.Searching
        delay(RELEASE_PAUSE)
    }

    private suspend fun readFirmware(rc: RadioClient) {
        repeat(SETUP_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(RETRY_PAUSE)
            try {
                val meta = (rc.admin.getDeviceMetadata() as? AdminResult.Success)?.value
                if (meta != null && meta.firmware_version.isNotBlank()) {
                    _firmware.value = RadioFirmware(meta.firmware_version, meta.hw_model.name, hasWifi = meta.hasWifi, hasBluetooth = meta.hasBluetooth)
                    return
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Software lesen: Versuch ${attempt + 1} abgebrochen", e)
            }
        }
    }

    private suspend fun observe(rc: RadioClient) = coroutineScope {
        launch {
            rc.connection.collect { cs ->
                _state.value = when (cs) {
                    ConnectionState.Connected -> LinkState.Connected
                    is ConnectionState.Reconnecting -> LinkState.Lost
                    is ConnectionState.Connecting, is ConnectionState.Configuring ->
                        if (_state.value == LinkState.Lost) LinkState.Lost else LinkState.Connecting
                    ConnectionState.Disconnected -> _state.value
                }
            }
        }
        launch {
            rc.ownNode.collect { node ->
                if (node != null) ownNode = node
                _ownDevice.value = node?.toOwnDevice() ?: _ownDevice.value
            }
        }
        // Empty list = connection is just being established. Then keep the last known state,
        // so that the chat does not disappear during short drops.
        launch {
            rc.channels.collect { channels ->
                if (!channels.isNullOrEmpty()) {
                    _channels.value = circleChannelsOf(channels)
                    _activeSlots.value = activeSlotsOf(channels)
                    _oldChannels.value = channels
                        .filter { it.role == Channel.Role.SECONDARY && isOldChannelName(it.settings?.name) }
                        .mapNotNull { it.settings?.name }
                    _otherSlots.value = otherChannelsOf(channels)
                    _publicChannels.value = PublicChannels.of(channels)
                    _openChannel0.value = PublicChannels.isOpenChannel0(channels)
                }
            }
        }
        launch {
            rc.nodeMap().collect { nodes ->
                _names.value = nodes.entries
                    .mapNotNull { (id, info) -> info.user?.long_name?.takeIf { it.isNotBlank() }?.let { id.raw to it } }
                    .toMap()
            }
        }
        // Profile check only with a complete state: configuration read and primary channel known.
        launch {
            combine(rc.configBundle, rc.channels, rc.ownNode) { bundle, channels, node ->
                if (bundle == null || channels.isNullOrEmpty() || channels.none { it.role == Channel.Role.PRIMARY }) return@combine null
                if (bundle.configs.none { it.lora != null }) return@combine null
                RadioProfile.check(RadioProfile.DeviceConfigSnapshot(bundle.configs, bundle.moduleConfigs, channels, node?.user))
            }.collect { check -> if (check != null) _setupCheck.value = check }
        }
        launch {
            rc.textMessages.collect { packet ->
                packet.toIncoming(rc)?.let { _incoming.emit(it) }
                packet.toIncomingPublic(rc)?.let { _incomingPublic.emit(it) }
            }
        }
        launch {
            rc.packets.collect { packet ->
                val data = packet.toIncomingData(rc)
                if (data != null) {
                    _incomingData.emit(data)
                } else if (packet.from != 0 && packet.from != rc.ownNode.value?.num) {
                    _heardNodes.tryEmit(HeardNode(packet.from, packet.hop_start.takeIf { it > 0 && it >= packet.hop_limit }?.let { it - packet.hop_limit }))
                }
            }
        }
    }

    /**
     * `false`: to everyone in the circle channel, `true`: directly to us, `null`: not for circles and contacts. Public
     * channels run separately via [toIncomingPublic] (ADR 0055); foreign channels stay invisible.
     */
    private fun MeshPacket.directOrNull(rc: RadioClient): Boolean? {
        val ownNum = rc.ownNode.value?.num
        val isCircleChannel = _channels.value.orEmpty().any { it.index == channel }
        return when {
            to == NodeId.BROADCAST.raw && isCircleChannel -> false
            ownNum != null && to == ownNum -> true
            else -> null
        }
    }

    /**
     * ANMP packet (ADR 0021): data packet on `PRIVATE_APP` from another radio, to all in the radio channel `anynear`
     * or on channel 0 (ADR 0054 §1, even if we ourselves do not send there), or directly to us. Text on channel 0 never
     * goes here, but to „Alle in der Nähe“ ([toIncomingPublic]); separation is by port.
     */
    private fun MeshPacket.toIncomingData(rc: RadioClient): IncomingData? {
        val data = decoded ?: return null.also { if (from != rc.ownNode.value?.num) noteUndecrypted(from, channel) }
        if (data.portnum != PortNum.PRIVATE_APP || from == rc.ownNode.value?.num) return null
        val payload = data.payload.toByteArray()
        if (!Anmp.isPmp(payload)) return null
        val direct = directOrNull(rc)
            ?: (if (isChannel0Broadcast(to, channel, _openChannel0.value)) false else null)
            ?: return null.also {
                log(
                    "Funk: anynear-Paket verworfen (Kanal $channel, an ${if (to == NodeId.BROADCAST.raw) "alle" else nodeName(to)}, " +
                        "von ${nodeName(from)}, Kanal 0 offen: ${_openChannel0.value}, anynear auf ${_channels.value.orEmpty().map { it.index }})",
                )
            }
        return IncomingData(
            packetId = id,
            fromNodeNum = from,
            direct = direct,
            channelIndex = channel,
            payload = payload,
            timestampMillis = rx_time?.takeIf { it > 0 }?.let { it * 1000L } ?: Clock.System.now().toEpochMilliseconds(),
            hops = hop_start.takeIf { it > 0 && it >= hop_limit }?.let { it - hop_limit },
            snr = rx_snr.takeIf { it != 0f },
        )
    }

    private var undecrypted = 0
    private var undecryptedSince = 0L

    /**
     * Packets the radio passed on without being able to open them (channel the radio does not have): counted and logged
     * at most once a minute, with the last sender and channel hash, so a channel mismatch shows in the log.
     */
    private fun noteUndecrypted(from: Int, channelHash: Int) {
        val now = Clock.System.now().toEpochMilliseconds()
        undecrypted++
        if (now - undecryptedSince < 60_000) return
        log("Funk: $undecrypted Pakete nicht entschlüsselt (zuletzt von ${nodeName(from)}, Kanal-Hash $channelHash)")
        undecrypted = 0
        undecryptedSince = now
    }

    private fun MeshPacket.toIncoming(rc: RadioClient): IncomingText? {
        val text = asText() ?: return null
        val direct = directOrNull(rc) ?: return null
        return IncomingText(
            packetId = id,
            fromNodeNum = from,
            direct = direct,
            channelIndex = channel,
            text = text,
            timestampMillis = rx_time?.takeIf { it > 0 }?.let { it * 1000L } ?: Clock.System.now().toEpochMilliseconds(),
            // Meshtastic reaction: emoji flag and reference to the packet it applies to (ADR 0018).
            reactionTo = decoded?.takeIf { it.emoji != 0 }?.reply_id ?: 0,
        )
    }

    /** Text or reaction to everyone on a public channel (ADR 0055). Own packets stay out. */
    private fun MeshPacket.toIncomingPublic(rc: RadioClient): IncomingPublicText? {
        if (to != NodeId.BROADCAST.raw || from == rc.ownNode.value?.num) return null
        val public = _publicChannels.value.firstOrNull { it.index == channel } ?: return null
        val text = asText()?.takeIf { it.isNotBlank() } ?: return null
        return IncomingPublicText(
            packetId = id,
            fromNodeNum = from,
            channel = public.channel,
            text = text,
            timestampMillis = rx_time?.takeIf { it > 0 }?.let { it * 1000L } ?: Clock.System.now().toEpochMilliseconds(),
            reactionTo = decoded?.takeIf { it.emoji != 0 }?.reply_id ?: 0,
            hops = hop_start.takeIf { it > 0 && it >= hop_limit }?.let { it - hop_limit },
            snr = rx_snr.takeIf { it != 0f },
            rssi = rx_rssi.takeIf { it != 0 },
            viaMqtt = via_mqtt,
        )
    }

    override fun sendPublic(text: String, channel: PublicChannel): RadioSend {
        val rc = client.value ?: throw NotReadyException("Funkgerät ist nicht verbunden.")
        val index = _publicChannels.value.firstOrNull { it.channel == channel }?.index
            ?: throw NotReadyException("Öffentlicher Kanal fehlt.")
        val handle = rc.sendText(text = text, to = NodeId.BROADCAST, channel = ChannelIndex(index))
        return track(rc, handle, null)
    }

    override fun sendPublicReaction(emoji: String, replyTo: Int, channel: PublicChannel): Int? {
        val rc = client.value ?: return null
        val index = _publicChannels.value.firstOrNull { it.channel == channel }?.index ?: return null
        if (replyTo == 0) return null
        return runCatching {
            rc.sendReaction(emoji = emoji, to = NodeId.BROADCAST, channel = ChannelIndex(index), replyId = replyTo).id.raw
        }.onFailure { log("Öffentliche Reaktion nicht gesendet", it) }.getOrNull()
    }

    override suspend fun ensurePublicChannel(name: String, psk: ByteArray): ChannelSetupResult =
        writeChannel(name, psk, replace = null, keepExisting = true)

    override fun send(text: String, to: Int?, channelIndex: Int): RadioSend {
        val rc = client.value ?: throw NotReadyException("Funkgerät ist nicht verbunden.")
        if (!mayUse(channelIndex, to)) {
            throw NotReadyException("Dein Kreis ist auf dem Funkgerät noch nicht eingerichtet.")
        }

        // Direct messages also run over a circle channel: if the device knows the public
        // key of the recipient, the firmware encrypts via PKI. Otherwise it falls back to the
        // channel key, and that must not be the publicly known Hessenmesh key.
        val handle = rc.sendText(
            text = text,
            to = to?.let(::NodeId) ?: NodeId.BROADCAST,
            channel = ChannelIndex(channelIndex),
        )
        return track(rc, handle, to)
    }

    /**
     * Circle channel always; directly to one radio also any other active channel, so that the reply to a radio without
     * anynear goes back where its message came from (it does not know the channel `anynear`).
     */
    private fun mayUse(channelIndex: Int, to: Int?): Boolean =
        isUsableChannel(channelIndex, to, _channels.value.orEmpty(), _activeSlots.value)

    override suspend fun sendData(payload: ByteArray, to: Int?, channelIndex: Int): RadioSend {
        val rc = client.value ?: throw NotReadyException("Funkgerät ist nicht verbunden.")
        // ANMP to everyone may also go on an open channel 0 (ADR 0054 §1, AppConfig.RADIO_CHANNEL0).
        if (!isAnmpChannel(channelIndex, _channels.value, _openChannel0.value && to == null)) {
            log("Funk: Senden abgelehnt, Kanal $channelIndex ist weder `anynear` noch ein offener Kanal 0")
            throw NotReadyException("Dein Kreis ist auf dem Funkgerät noch nicht eingerichtet.")
        }
        log("Funk: sende ANMP auf Kanal $channelIndex an ${to?.let(::nodeName) ?: "alle"}")
        // Like text over a circle channel; directly to a radio the firmware additionally encrypts via PKI.
        val handle = rc.send(
            portnum = PortNum.PRIVATE_APP,
            payload = payload,
            to = to?.let(::NodeId) ?: NodeId.BROADCAST,
            channel = ChannelIndex(channelIndex),
            wantAck = true,
        )
        return track(rc, handle, to)
    }

    override suspend fun sendDataWithHops(payload: ByteArray, channelIndex: Int, hopLimit: Int): RadioSend {
        val rc = client.value ?: throw NotReadyException("Funkgerät ist nicht verbunden.")
        if (!isAnmpChannel(channelIndex, _channels.value, _openChannel0.value)) {
            log("Funk: Senden abgelehnt, Kanal $channelIndex ist weder `anynear` noch ein offener Kanal 0")
            throw NotReadyException("Funkkanal fehlt.")
        }
        log("Funk: sende ANMP auf Kanal $channelIndex an alle mit $hopLimit Hops")
        val data = Data.Builder().also {
            it.portnum = PortNum.PRIVATE_APP
            it.payload = payload.toByteString()
        }.build()
        val packet = MeshPacket.Builder().also {
            it.to = NodeId.BROADCAST.raw
            it.channel = channelIndex
            it.decoded = data
            it.hop_limit = hopLimit.coerceIn(0, MAX_HOPS)
            it.want_ack = false
        }.build()
        return track(rc, rc.send(packet), null)
    }

    override suspend fun radioCheck(to: Int, channelIndex: Int, timeout: Duration): RadioCheckReply? {
        val rc = client.value ?: throw NotReadyException("Funkgerät ist nicht verbunden.")
        // Directly to one radio: `anynear`, an open channel 0 or the channel a radio without anynear wrote on.
        if (!mayUse(channelIndex, to)) throw NotReadyException("Funkkanal fehlt.")
        log("Funkcheck: sende auf Kanal $channelIndex an ${nodeName(to)}")
        return coroutineScope {
            // Listen before sending so that a quick answer is not lost.
            val replies = Queue<MeshPacket>(Queue.UNLIMITED)
            val listening = launch(start = CoroutineStart.UNDISPATCHED) {
                rc.packets.filter { it.from == to && it.decoded?.portnum == PortNum.ROUTING_APP }.collect { replies.send(it) }
            }
            try {
                // No ANMP start: anynear apps at the target silently drop the packet, the firmware still confirms it.
                val handle = rc.send(
                    portnum = PortNum.PRIVATE_APP,
                    payload = RADIO_CHECK_PAYLOAD,
                    to = NodeId(to),
                    channel = ChannelIndex(channelIndex),
                    wantAck = true,
                )
                withTimeoutOrNull(timeout) {
                    var reply: MeshPacket
                    do reply = replies.receive() while (!reply.isCheckReplyFrom(to, handle.id.raw))
                    if (reply.asRouting()?.error_reason.let { it != null && it != Routing.Error.NONE }) {
                        log("Funkcheck: Zielgerät meldet ${reply.asRouting()?.error_reason}")
                    }
                    reply.toCheckReply()
                }
            } finally {
                listening.cancel()
            }
        }
    }

    private fun track(rc: RadioClient, handle: MessageHandle, to: Int?): RadioSend {
        val sdkStatus = handle.state.map { it.toMessageStatus() }
        val recipientAck = if (to == null) {
            emptyFlow()
        } else {
            rc.packets
                .filter { it.isAckFrom(to, requestId = handle.id.raw) }
                .map { MessageStatus.CONFIRMED }
        }
        // Group chat ends with the first final SDK state. Direct messages keep waiting after
        // RELAYED/NO_CONFIRMATION for the confirmation of the recipient; the waiting time
        // is limited by the caller.
        return RadioSend(
            packetId = handle.id.raw,
            statuses = merge(sdkStatus, recipientAck).transformWhile { status ->
                emit(status)
                !isFinalFor(status, isDirect = to != null)
            },
        )
    }

    override fun sendReaction(emoji: String, replyTo: Int, to: Int?, channelIndex: Int): Int? {
        val rc = client.value ?: return null
        if (replyTo == 0 || !mayUse(channelIndex, to)) return null
        return runCatching {
            rc.sendReaction(emoji = emoji, to = to?.let(::NodeId) ?: NodeId.BROADCAST, channel = ChannelIndex(channelIndex), replyId = replyTo).id.raw
        }.onFailure { log("Reaktion nicht gesendet", it) }.getOrNull()
    }

    /**
     * Robust against shaky connections: only what is confirmed by reading back on the device
     * counts. If the connection drops or a confirmation fails to arrive (the firmware
     * does not reliably confirm local write commands over TCP), it is checked again after the automatic
     * reconnection and, if necessary, written again. This is idempotent.
     */
    override suspend fun ensureChannel(name: String, psk: ByteArray): ChannelSetupResult = writeChannel(name, psk, replace = null)

    override suspend fun replaceChannel(replace: String, name: String, psk: ByteArray): ChannelSetupResult =
        writeChannel(name, psk, replace)

    private suspend fun writeChannel(name: String, psk: ByteArray, replace: String?, keepExisting: Boolean = false): ChannelSetupResult {
        val settings = ChannelSettings.Builder().also {
            it.name = name
            it.psk = psk.toByteString()
            // No location in circles (ADR 0006).
            it.module_settings = ModuleSettings.Builder().also { m -> m.position_precision = 0 }.build()
        }.build()
        return withRetries("Kanal $name", ChannelSetupResult.NotConnected, ChannelSetupResult.Failed(texts.status.radioSetupNotConfirmed)) { rc, attempt ->
            tryEnsureChannel(rc, settings, replace, attempt, keepExisting)
        }
    }

    override suspend fun setWayOfUse(ssid: String?, password: String?): Boolean {
        val rc = client.value ?: return false
        val configs = rc.configBundle.value?.configs.orEmpty()
        val network = (configs.firstNotNullOfOrNull { it.network }?.newBuilder() ?: Config.NetworkConfig.Builder()).also {
            it.wifi_enabled = ssid != null
            if (ssid != null) {
                it.wifi_ssid = ssid
                it.wifi_psk = password.orEmpty()
            }
        }.build()
        val bluetooth = (configs.firstNotNullOfOrNull { it.bluetooth }?.newBuilder() ?: Config.BluetoothConfig.Builder())
            .also { it.enabled = true }.build()
        return try {
            // One transaction, one restart; Bluetooth stays enabled, the firmware switches it off while Wi-Fi is on.
            val result = rc.admin.editSettings {
                setConfig(Config.Builder().also { it.network = network }.build())
                setConfig(Config.Builder().also { it.bluetooth = bluetooth }.build())
            }
            // A timeout or an unreachable radio can be the restart itself; a refusal is a refusal.
            result is AdminResult.Success || result is AdminResult.Timeout || result is AdminResult.NodeUnreachable
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The radio restarts after the commit; a lost answer does not mean it refused. The search on the new way
            // decides (the app then says honestly whether it showed up).
            log("Einsatzart umstellen, Antwort fehlt: ${e.message}")
            true
        }
    }

    override suspend fun removeChannel(name: String): Boolean =
        withRetries("Kanal $name entfernen", false, false) { rc, _ -> tryRemoveChannel(rc, name) }

    private suspend fun <T> withRetries(
        what: String,
        notConnected: T,
        failed: T,
        attemptOnce: suspend (RadioClient, Int) -> T?,
    ): T {
        repeat(SETUP_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(RETRY_PAUSE)
            val rc = awaitConnected() ?: return notConnected
            try {
                attemptOnce(rc, attempt)?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("$what: Versuch ${attempt + 1} abgebrochen", e)
            }
        }
        return failed
    }

    /**
     * Waits until a device is connected. Also during a reconnection of the same client
     * (restart of the device): [client] stays the same, only its state changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun awaitConnected(): RadioClient? = withTimeoutOrNull(RECONNECT_WAIT) {
        client.filterNotNull()
            .flatMapLatest { rc -> rc.connection.filter { it == ConnectionState.Connected }.map { rc } }
            .first()
    }

    /** @return result, or `null` if another attempt is needed. */
    private suspend fun tryEnsureChannel(
        rc: RadioClient,
        settings: ChannelSettings,
        replace: String?,
        attempt: Int,
        keepExisting: Boolean = false,
    ): ChannelSetupResult? {
        // Only write with a fresh, complete channel list. If the connection is gone right now, the
        // list is empty or stale, and a "free" slot could in truth be taken.
        if (rc.connection.value != ConnectionState.Connected) return null
        val channels = rc.channels.value.orEmpty()
        if (channels.none { it.role == Channel.Role.PRIMARY }) return null
        val existing = channels.firstOrNull { it.role != Channel.Role.DISABLED && it.settings?.name == settings.name }
        if (existing != null && existing.settings?.psk == settings.psk) {
            // An earlier attempt made it after all, or the channel was already there.
            return ChannelSetupResult.Done
        }
        // Public channel of the same name with a different key: belongs to someone else, never overwrite (ADR 0055).
        if (existing != null && keepExisting) return ChannelSetupResult.Done
        // Existing channel of the same name: renew the key (e.g. after removing a member).
        val slot = existing?.index ?: freeSecondarySlot(channels)
            ?: (if (keepExisting) null else oldChannelSlot(channels)) ?: replace?.let { replaceableSlot(channels, it) }
            ?: return ChannelSetupResult.NoFreeSlot
        val newChannel = Channel.Builder().also {
            it.index = slot
            it.role = Channel.Role.SECONDARY
            it.settings = settings
        }.build()

        log("Kreis-Kanal: Versuch ${attempt + 1}, schreibe auf Platz $slot")
        val written = rc.admin.setChannel(newChannel)
        log("Kreis-Kanal: setChannel -> $written (wird durch Rücklesen geprüft)")

        val readBack = (rc.admin.getChannel(ChannelIndex(slot)) as? AdminResult.Success)?.value
        log("Kreis-Kanal: Rücklesen -> ${if (readBack == null) "keine Antwort" else "Platz ${readBack.index}, ${readBack.role}"}")
        if (readBack != null && readBack.settings?.name == settings.name && readBack.settings?.psk == settings.psk) {
            // Public channels are not among the circle channels; the list comes with the next channel report.
            if (keepExisting) return ChannelSetupResult.Done
            _channels.value =(_channels.value.orEmpty().filterNot { it.index == slot } + RadioChannel(slot, settings.name, settings.psk))
                .sortedBy { it.index }
            return ChannelSetupResult.Done
        }
        return null
    }

    private suspend fun tryRemoveChannel(rc: RadioClient, name: String): Boolean? {
        if (rc.connection.value != ConnectionState.Connected) return null
        val channels = rc.channels.value.orEmpty()
        if (channels.none { it.role == Channel.Role.PRIMARY }) return null
        val existing = channels.firstOrNull { it.role != Channel.Role.DISABLED && it.settings?.name == name } ?: return true
        if (existing.role == Channel.Role.PRIMARY) return false // Never touch the primary channel.
        rc.admin.setChannel(Channel.Builder().also { it.index = existing.index; it.role = Channel.Role.DISABLED }.build())
        val readBack = (rc.admin.getChannel(ChannelIndex(existing.index)) as? AdminResult.Success)?.value
        if (readBack != null && readBack.role == Channel.Role.DISABLED) {
            _channels.value = _channels.value.orEmpty().filterNot { it.index == existing.index }
            _oldChannels.value = _oldChannels.value - name
            return true
        }
        return null
    }

    /**
     * Writes only what deviates, each area separately, and then checks against the freshly read state
     * of the device. An edit session (`editSettings`) does not work over TCP: the firmware
     * does not confirm the start and the data stream stays silent until the connection drops
     * (emulator test 19.10.2026, cf. finding W4). Restarts and drops are bridged by the automatic
     * reconnection; writing is idempotent.
     */
    override suspend fun applyRadioProfile(nameSuffix: String, force: Boolean): DeviceSetupResult =
        writeUntilDone(nameSuffix, onlyName = false, force = force)

    override suspend fun renameDevice(nameSuffix: String): DeviceSetupResult =
        writeUntilDone(nameSuffix, onlyName = true, force = false)

    /**
     * Writes, checks against the freshly read state and repeats until everything fits. [force]: in the first
     * pass write everything, even what is already right. [onlyName]: only name and short name.
     */
    private suspend fun writeUntilDone(nameSuffix: String, onlyName: Boolean, force: Boolean): DeviceSetupResult {
        if (!DeviceName.isValidSuffix(nameSuffix)) return DeviceSetupResult.Failed(texts.status.deviceNameInvalid)
        val expectedLong = DeviceName.longName(nameSuffix)
        fun nameDone(owner: User?) = owner?.long_name == expectedLong && owner.short_name == DeviceName.shortName(nameSuffix, owner.id)
        var remaining: Set<ProfileDeviation>? = null
        var forcePending = force
        repeat(PROFILE_ATTEMPTS) { attempt ->
            val rc = awaitConnected() ?: return if (remaining == null) DeviceSetupResult.NotConnected else unfinished(remaining)
            val snapshot = snapshotOf(rc) ?: run { delay(RETRY_PAUSE); return@repeat }
            val deviations = when {
                onlyName -> emptySet()
                forcePending -> ProfileDeviation.entries.toSet()
                else -> RadioProfile.deviations(snapshot) - ProfileDeviation.NAME
            }
            val nameOk = nameDone(snapshot.owner) && !forcePending
            forcePending = false
            remaining = if (nameOk) deviations else deviations + ProfileDeviation.NAME
            if (deviations.isEmpty() && nameOk) return DeviceSetupResult.Done
            val lora = snapshot.lora
            log("Geräteprofil: Versuch ${attempt + 1}, schreibe $remaining (Funk: ${lora?.region} ${lora?.modem_preset} preset=${lora?.use_preset} hops=${lora?.hop_limit} tx_power=${lora?.tx_power})")
            val acked = writeProfile(rc, snapshot, deviations, nameSuffix, writeName = !nameOk)
            if (!acked) {
                // Over TCP the device only confirms commands right after the connection is established; if the
                // confirmation fails to arrive, the data stream is silent (finding W4/E10). Wait for the reconnection
                // and then write again immediately.
                withTimeoutOrNull(RECONNECT_WAIT) { rc.connection.first { it != ConnectionState.Connected } }
            } else {
                // Even after a pure name change the firmware restarts (`AdminModule::saveChanges`,
                // after 7 s). Only the state after the restart counts: releasing earlier shows the old name,
                // and a second save triggers the next restart.
                withTimeoutOrNull(REBOOT_WAIT) { rc.connection.first { it != ConnectionState.Connected } }
                delay(RETRY_PAUSE)
            }
        }
        return unfinished(remaining)
    }

    private fun unfinished(remaining: Set<ProfileDeviation>?): DeviceSetupResult =
        DeviceSetupResult.Failed(texts.status.radioSettingsIncomplete).also { log("Geräteprofil: offen $remaining") }

    private fun snapshotOf(rc: RadioClient): RadioProfile.DeviceConfigSnapshot? {
        val bundle = rc.configBundle.value ?: return null
        val channels = rc.channels.value.orEmpty()
        if (channels.none { it.role == Channel.Role.PRIMARY } || bundle.configs.none { it.lora != null }) return null
        return RadioProfile.DeviceConfigSnapshot(bundle.configs, bundle.moduleConfigs, channels, rc.ownNode.value?.user)
    }

    /**
     * Radio profile last: it restarts the device, everything before it is then already written.
     * @return `true` if the device has confirmed all write commands; otherwise it stops at the first one.
     */
    private suspend fun writeProfile(
        rc: RadioClient,
        snapshot: RadioProfile.DeviceConfigSnapshot,
        deviations: Set<ProfileDeviation>,
        nameSuffix: String,
        writeName: Boolean,
    ): Boolean {
        val steps = buildList<Pair<String, suspend () -> AdminResult<Unit>>> {
            if (writeName) {
                add("Name" to { rc.admin.setOwner(RadioProfile.owner(snapshot.owner, nameSuffix)) })
            }
            if (ProfileDeviation.TELEMETRY in deviations || ProfileDeviation.SENSORS in deviations) {
                add("Telemetrie" to { rc.admin.setModuleConfig(ModuleConfig.Builder().also { it.telemetry = RadioProfile.telemetry(snapshot.telemetry) }.build()) })
            }
            if (ProfileDeviation.ALERTS in deviations) {
                add("Signale" to { rc.admin.setModuleConfig(ModuleConfig.Builder().also { it.external_notification = RadioProfile.notification(snapshot.notification) }.build()) })
            }
            if (ProfileDeviation.POSITION in deviations) {
                RadioProfile.channelsSharingPosition(snapshot.channels).forEach { ch ->
                    add("Kanal ${ch.index}" to { rc.admin.setChannel(RadioProfile.withoutPosition(ch)) })
                }
                add("Standort" to { rc.admin.setConfig(Config.Builder().also { it.position = RadioProfile.position(snapshot.position) }.build()) })
            }
            if (ProfileDeviation.ROLE in deviations) {
                add("Rolle" to { rc.admin.setConfig(Config.Builder().also { it.device = RadioProfile.device(snapshot.device) }.build()) })
            }
            when (RadioProfile.loraWrite(deviations)) {
                RadioProfile.LoraWrite.PROFILE ->
                    add("Funk" to { rc.admin.setConfig(Config.Builder().also { it.lora = RadioProfile.lora(snapshot.lora) }.build()) })
                RadioProfile.LoraWrite.MQTT ->
                    add("MQTT" to { rc.admin.setConfig(Config.Builder().also { it.lora = RadioProfile.mqtt(snapshot.lora) }.build()) })
                null -> Unit
            }
        }
        for ((what, write) in steps) {
            if (rc.connection.value != ConnectionState.Connected) return false
            val result = try {
                withTimeoutOrNull(WRITE_TIMEOUT) { write() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Geräteprofil: $what abgebrochen", e)
                return false
            }
            log("Geräteprofil: $what -> ${result ?: "keine Bestätigung"}")
            if (result !is AdminResult.Success) return false
        }
        return true
    }

    override fun ownContact(): Contact? {
        val own = ownNode ?: return null
        val user = own.user ?: return null
        return RadioContacts.of(
            SharedContact.Builder().also {
                it.node_num = own.num
                it.user = user
            }.build(),
        )
    }

    override suspend fun registerContact(contact: Contact): Boolean {
        val rc = client.value ?: return false
        val shared = runCatching { RadioContacts.shared(contact) }.getOrNull() ?: return false
        return rc.admin.addContact(shared) is AdminResult.Success
    }

    companion object {
        private val PROBE_TIMEOUT = 15.seconds
        private const val SDK_RECONNECT_ATTEMPTS = 10
        private const val BACKGROUND_SLOWDOWN_AFTER = 5
        private val BACKGROUND_RETRY_PAUSE = 5.minutes
        private const val SETUP_ATTEMPTS = 5

        /** Waiting time for "Rebooting to …" after `ota_request` (ADR 0056). */
        private val OTA_CONFIRM_WAIT = 5.seconds

        /** Pause after releasing the connection before bootloader or loader are addressed. */
        private val RELEASE_PAUSE = 1500.milliseconds
        private val RETRY_PAUSE = 3.seconds
        private val RECONNECT_WAIT = 90.seconds
        private const val PROFILE_ATTEMPTS = 6
        private val WRITE_TIMEOUT = 15.seconds
        /** After the radio profile the device only restarts after a good 20 s (emulator test 19.10.2026). */
        private val REBOOT_WAIT = 45.seconds

        internal fun retryDelaySeconds(failures: Int): Long = when {
            failures <= 1 -> 2L
            failures >= 5 -> 30L
            else -> 2L shl (failures - 1)
        }

        internal fun isFinalFor(status: MessageStatus, isDirect: Boolean): Boolean = when (status) {
            MessageStatus.CONFIRMED, MessageStatus.FAILED -> true
            MessageStatus.RELAYED, MessageStatus.NO_CONFIRMATION -> !isDirect
            MessageStatus.QUEUED, MessageStatus.HANDED_TO_RADIO -> false
            MessageStatus.ONLINE, MessageStatus.READ -> false // Never comes from the radio.
        }

        /** Highest hop limit Meshtastic knows. */
        private const val MAX_HOPS = 7

        /** To everyone on channel 0, as long as it is suitable for ANMP ([PublicChannels.isOpenChannel0]). */
        internal fun isChannel0Broadcast(to: Int, channel: Int, openChannel0: Boolean): Boolean =
            openChannel0 && channel == 0 && to == NodeId.BROADCAST.raw

        /** Old channels that the app clears away (see [AppConfig.isOldChannelName]). */
        internal fun isOldChannelName(name: String?): Boolean = AppConfig.isOldChannelName(name)

        /** The radio channel `anynear` (ADR 0051, 0061): secondary channel with exactly this name. The primary channel (Hessenmesh) never belongs to it. */
        /**
         * Slot on which ANMP may go to everyone: the radio channel `anynear` or, when [openChannel0], channel 0
         * (ADR 0054 §1). Before 12.11.2026 only `anynear` was allowed, so with channel 0 nothing went out over radio.
         */
        internal fun isAnmpChannel(channelIndex: Int, circleChannels: List<RadioChannel>?, openChannel0: Boolean): Boolean =
            circleChannels.orEmpty().any { it.index == channelIndex } || (openChannel0 && channelIndex == 0)

        /** Radio node as Meshtastic names it (`!939d1998`). */
        internal fun nodeName(node: Int): String = "!" + node.toUInt().toString(16).padStart(8, '0')

        internal fun circleChannelsOf(channels: List<Channel>): List<RadioChannel> =
            channels
                .filter { it.role == Channel.Role.SECONDARY && it.settings?.name == app.anynear.anmp.AnmpInfo.RADIO_CHANNEL_NAME }
                .mapNotNull { ch -> ch.settings?.let { RadioChannel(ch.index, it.name, it.psk) } }
                .sortedBy { it.index }

        /** Slots of all channels that are switched on (primary and secondary). */
        internal fun activeSlotsOf(channels: List<Channel>): Set<Int> =
            channels.filter { it.role == Channel.Role.PRIMARY || it.role == Channel.Role.SECONDARY }.map { it.index }.toSet()

        /** Circle channel for everything; any other active channel only directly to one radio ([to] not `null`). */
        internal fun isUsableChannel(channelIndex: Int, to: Int?, circleChannels: List<RadioChannel>, activeSlots: Set<Int>): Boolean =
            circleChannels.any { it.index == channelIndex } || (to != null && channelIndex in activeSlots)

        /** Secondary slots of other apps: taken, but not a circle channel. The app clears away old channels anyway. */
        internal fun otherChannelsOf(channels: List<Channel>): Int =
            channels.count {
                val name = it.settings?.name
                it.role == Channel.Role.SECONDARY && name != app.anynear.anmp.AnmpInfo.RADIO_CHANNEL_NAME && !isOldChannelName(name)
            }

        /** Slot of the circle channel [name] that may be replaced; never the primary channel, never a foreign one. */
        internal fun replaceableSlot(channels: List<Channel>, name: String): Int? =
            channels.firstOrNull { it.role == Channel.Role.SECONDARY && it.settings?.name == name && isOldChannelName(name) }?.index

        /** Slot of an old channel ([isOldChannelName]) that the own radio channel may take over if no other is free. */
        internal fun oldChannelSlot(channels: List<Channel>): Int? =
            channels.firstOrNull { it.role == Channel.Role.SECONDARY && isOldChannelName(it.settings?.name) }?.index

        /** First free channel slot 1–7. Slot 0 is the primary channel (Hessenmesh) and stays untouched. */
        internal fun freeSecondarySlot(channels: List<Channel>): Int? =
            (1..ChannelIndex.MAX_CHANNEL_INDEX).firstOrNull { index ->
                channels.none { it.index == index && it.role != Channel.Role.DISABLED }
            }

        /** Content of a radio check: one byte without ANMP start (ADR 0053; whether an empty packet works too, clarify on hardware). */
        private val RADIO_CHECK_PAYLOAD = byteArrayOf(0)

        /**
         * Answer to a radio check that verifiably comes from the target device. Unlike with [isAckFrom], an error
         * (such as unknown key) counts too: it also proves that the packet made it there and back.
         */
        internal fun MeshPacket.isCheckReplyFrom(destination: Int, requestId: Int): Boolean {
            if (from != destination) return false
            val data = decoded ?: return false
            return data.portnum == PortNum.ROUTING_APP && data.request_id == requestId
        }

        /** Measurements of the answer packet, i.e. of the way back. */
        internal fun MeshPacket.toCheckReply(): RadioCheckReply = RadioCheckReply(
            hops = hop_start.takeIf { it > 0 && it >= hop_limit }?.let { it - hop_limit },
            snr = rx_snr.takeIf { it != 0f },
            rssi = rx_rssi.takeIf { it != 0 },
        )

        /** Routing ACK that verifiably comes from the target device (not from the own device or a relay). */
        internal fun MeshPacket.isAckFrom(destination: Int, requestId: Int): Boolean {
            if (from != destination) return false
            val data = decoded ?: return false
            if (data.portnum != PortNum.ROUTING_APP || data.request_id != requestId) return false
            val error = asRouting()?.error_reason
            return error == null || error == Routing.Error.NONE
        }
    }
}

private fun NodeInfo.toOwnDevice(): OwnDevice = OwnDevice(
    nodeNum = num,
    longName = user?.long_name.orEmpty(),
    shortName = user?.short_name.orEmpty(),
    batteryLevel = device_metrics?.battery_level,
)
