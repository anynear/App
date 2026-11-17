package app.anynear.app

import app.anynear.anmp.core.CoreLog
import app.anynear.anmp.core.AnmpCore
import app.anynear.anmp.core.AnmpPlatform
import app.anynear.AppConfig
import app.anynear.alerts.AlertSettingsStore
import app.anynear.alerts.Alerts
import app.anynear.lifesigns.CoreLifeSignSource
import app.anynear.lifesigns.LifeSigns
import app.anynear.alerts.CoreAlertSource
import app.anynear.attachments.AttachmentFiles
import app.anynear.attachments.platformBinaryFiles
import app.anynear.attachments.readDocumentText
import app.anynear.attachments.readerVersion
import app.anynear.attachments.recognizePhotoTopics
import app.anynear.data.DocTexts
import app.anynear.circles.CircleRepository
import app.anynear.circles.ProfileRepository
import app.anynear.contacts.ContactRepository
import app.anynear.core.CoreBlobs
import app.anynear.core.CoreKeyValues
import app.anynear.core.CoreSecrets
import app.anynear.core.ForwardingHost
import app.anynear.core.FreshStart
import app.anynear.core.LocalKey
import app.anynear.core.AppNet
import app.anynear.data.DraftStore
import app.anynear.data.EncryptedKeyValueStore
import app.anynear.data.ObservedKeyValueStore
import app.anynear.data.MessageHistory
import app.anynear.data.MessageStore
import app.anynear.data.PairedDeviceRepository
import app.anynear.data.PersonalFiles
import app.anynear.data.SharedStorage
import app.anynear.passwords.PasswordStore
import app.anynear.passwords.platformPasswordFiles
import app.anynear.passwords.platformPasswordKeyStore
import app.anynear.passwords.publishCredentialIdentities
import app.anynear.data.deleteRadioCache
import app.anynear.data.platformDataDir
import app.anynear.data.platformKeyValueStore
import app.anynear.data.StorageVersion
import app.anynear.data.platformRawKeyValueAccess
import app.anynear.data.platformSecretKeys
import app.anynear.data.platformSecretStore
import app.anynear.data.platformTextFile
import app.anynear.images.AvatarImages
import app.anynear.mesh.DeviceScanner
import app.anynear.mesh.SdkMeshLink
import app.anynear.mesh.scanNetworkDevices
import app.anynear.people.MemberDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Folders of the app on the device that concern a fresh start. */
/** Where older versions staged a backup file for restoring at the next start (ADR 0027, removed by ADR 0105). */
private const val OLD_RESTORE_DIR = "restore"

internal val APP_BINARY_DIRS = listOf(AttachmentFiles.DIR_NAME, AvatarImages.DIR_NAME, DraftStore.DIR_NAME, CoreBlobs.DIR_NAME, PersonalFiles.DIR_NAME, CircleFiles.DIR_NAME)

/** Wires up the real building blocks. Create once per process. */
@OptIn(FlowPreview::class)
fun createAppController(hooks: PlatformHooks, platformLog: AppLog = AppLog.Silent, crashes: CrashRecorder? = null): AppController {
    // Additionally collect the app's own lines (not those of the radio SDK) for viewing in the app.
    val debugLog = DebugLog()
    // A crash report takes the last lines along, in seconds before the crash (ADR 0082).
    crashes?.logLines = {
        val now = Clock.System.now().toEpochMilliseconds()
        debugLog.entries.value.takeLast(CrashReports.LOG_LINES).map { "-${(now - it.atMillis) / 1000}s ${it.tag}: ${it.message}" }
    }
    val logSink = AppLog { level, tag, message, cause ->
        if (tag in DEBUG_LOG_TAGS) debugLog.add(tag, message + (cause?.let { " (${it::class.simpleName}: ${it.message})" } ?: ""))
        platformLog.log(level, tag, message, cause)
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val secrets = platformSecretStore()
    val rawKeyValues = platformRawKeyValueAccess()
    // On iOS passwords live in the app group's container (password extension), but go into a fresh start just the same.
    val binaryDirs = APP_BINARY_DIRS.associateWith(::platformBinaryFiles) + (PasswordStore.DIR_NAME to platformPasswordFiles())
    val historyFile = platformTextFile(MessageHistory.FILE_NAME)
    // Flagged (ADR 0046, “Gerät zurücksetzen”, “Alle Daten löschen”): everything on the device, like freshly installed.
    val resetRequested = FreshStart.requested(rawKeyValues)
    // Public radio (ADR 0055): own file, deleted on reset.
    val publicFile = platformTextFile(PublicRadio.FILE_NAME)
    // Read text of the storages (ADR 0067): deleted on reset.
    val docTextDir = platformBinaryFiles(DocTexts.DIR_NAME)
    // What the search for documents on the phone has already looked at (ADR 0071): likewise.
    val finderDir = platformBinaryFiles(FinderMemory.DIR_NAME)
    if (FreshStart.runIfNeeded(rawKeyValues, secrets, platformSecretKeys(), binaryDirs.values.toList() + docTextDir + finderDir, listOf(historyFile, publicFile))) {
        debugLog.add("App", "Zurückgesetzt")
    }
    if (resetRequested) {
        platformPasswordKeyStore().delete()
        deleteRadioCache()
    }
    val passwords = PasswordStore(binaryDirs.getValue(PasswordStore.DIR_NAME), platformPasswordKeyStore(), secrets, ::publishCredentialIdentities) {
        Clock.System.now().toEpochMilliseconds()
    }
    // The backup file is gone (ADR 0105): a restore staged by an older version is discarded.
    platformBinaryFiles(OLD_RESTORE_DIR).let { dir -> dir.names().forEach(dir::delete) }
    // Bring stored data to the state of this version (ADR 0061, decision 13) before anything reads it.
    StorageVersion.migrate(StorageVersion.Stores(rawKeyValues, secrets, ::platformSecretKeys))
    val plainStore = platformKeyValueStore()
    val freshStart = FreshStart.noticePending(plainStore)
    val removedHere = FreshStart.removedNoticePending(plainStore)
    if (freshStart || removedHere) FreshStart.noticeShown(plainStore)
    // Settings and history lie on the device encrypted with a key derived from the device key.
    val localCipher = LocalKey.cipher(secrets)
    // Observed, so that settings go to the network as they change (ADR 0106).
    val store = ObservedKeyValueStore(EncryptedKeyValueStore(plainStore, localCipher))
    val scanner = DeviceScanner()
    val profile = ProfileRepository(store)
    // ANMP core on ANDP (ADR 0034–0036): circles, contacts, personal mailbox, sending and fetching.
    val host = ForwardingHost()
    val config = AppNet.config()
    val core = AnmpCore(
        scope,
        config,
        AnmpPlatform(
            secrets = CoreSecrets(secrets),
            store = CoreKeyValues(store),
            blobs = CoreBlobs(platformBinaryFiles(CoreBlobs.DIR_NAME), localCipher),
            connector = AppNet.connector(),
            log = CoreLog { logSink.log(LogLevel.INFO, "Lesen", it, null) },
        ),
        host,
    )
    val history = MessageHistory(historyFile, localCipher)
    val messageStore = MessageStore(restored = history.load())
    // Save briefly batched so that a series of status changes is written only once.
    scope.launch {
        messageStore.messages.drop(1).debounce(300).collect {
            runCatching { history.save(messageStore.snapshot()) }
        }
    }
    val personalFiles = PersonalFiles(store, AttachmentFiles(binaryDirs.getValue(PersonalFiles.DIR_NAME), localCipher)) {
        Clock.System.now().toEpochMilliseconds()
    }
    // One online store for all storages together (ADR 0079).
    val storage = SharedStorage()
    // „Meine Ablage“ permanently on the relays (ADR 0041); waits until the core is running.
    PersonalFilesSync(scope, core, personalFiles, storage = storage) { logSink.log(LogLevel.INFO, "App", it, null) }.start()
    // Read and recognise files of the storages, only on the device (ADR 0067).
    // Small meaning model for search and recognition (ADR 0078).
    val docMeaning = DocMeaning()
    val docReading = DocReading(
        scope, DocTexts(AttachmentFiles(docTextDir, localCipher)), ::readDocumentText, ::recognizePhotoTopics, readerVersion, docMeaning::get,
        language = { if (app.anynear.i18n.currentLanguage() == app.anynear.i18n.Language.GERMAN) "de" else "en" },
    ) {
        logSink.log(LogLevel.INFO, "App", it, null)
    }
    docReading.watch(personalFiles)
    // Search the phone for important documents (ADR 0071), only on tap.
    val docFinder = DocFinder(scope, FinderMemory(store, AttachmentFiles(finderDir, localCipher), readerVersion), ::readDocumentText, ::recognizePhotoTopics, platformFinderConditions(), platformFinderKeepAlive(), platformDocSources(), docMeaning::get, platformFinderSchedule(), { Clock.System.now().toEpochMilliseconds() }) {
        logSink.log(LogLevel.INFO, "App", it, null)
    }
    // Storages of the circles, on the same building blocks (ADR 0049).
    val circleFiles = CircleFiles(
        scope, core, store, AttachmentFiles(binaryDirs.getValue(CircleFiles.DIR_NAME), localCipher),
        now = { Clock.System.now().toEpochMilliseconds() },
        docReading = docReading,
        storage = storage,
    ) { logSink.log(LogLevel.INFO, "App", it, null) }
    circleFiles.start()
    // Clear away text of files that no longer exist; only once the circles are loaded.
    scope.launch {
        circleFiles.byCircle.debounce(30_000).collect { byCircle ->
            docReading.keepOnly((personalFiles.all.value + byCircle.values.flatMap { it.all.value }).map { it.id }.toSet())
        }
    }
    val link = SdkMeshLink(scope = scope, dataDir = platformDataDir(), appLog = logSink)
    val publicRadio = PublicRadio(scope, link, publicFile, localCipher, store, now = { Clock.System.now().toEpochMilliseconds() }) {
        logSink.log(LogLevel.INFO, "App", it, null)
    }
    // Alerts and channels (ADR 0062); the core delivers the channels, settings stay on the device.
    val alerts = Alerts(
        scope, CoreAlertSource(core, scope), AlertSettingsStore(store), hooks, curatedLink = AppConfig.OFFICIAL_ALERTS_LINK,
        now = { Clock.System.now().toEpochMilliseconds() },
        ownName = { profile.ownName.value },
        acks = profile.alertAcks,
        saveAcks = profile::setAlertAcks,
        syncedSeen = profile.alertsSeen,
        saveSyncedSeen = profile::setAlertsSeen,
    ) { logSink.log(LogLevel.INFO, "App", it, null) }
    alerts.start()
    val memberDirectory = MemberDirectory(scope, core.members)
    // Check-ins (ADR 0083): in a circle of choice; the core computes the deadlines. The controller comes later.
    var controllerRef: AppController? = null
    val lifeSigns = LifeSigns(
        scope, CoreLifeSignSource(core), store, hooks,
        now = { Clock.System.now().toEpochMilliseconds() },
        nameOf = { person ->
            if (person == core.identity.value?.personId) profile.ownName.value
            // An own name for the person ([app.anynear.people.ContactNames]) covers the name from their profile.
            else app.anynear.people.ContactNames.active(profile.contactNameEntries.value)[person]
                ?: memberDirectory.members.value.firstOrNull { it.personId == person }?.name.orEmpty()
        },
        circleName = { id -> core.circle(id)?.name.orEmpty() },
        helpersOf = { id ->
            val me = core.identity.value?.personId
            memberDirectory.of(id).map { it.personId }.distinct().filter { it != me }
        },
        me = { core.identity.value?.personId },
        isSupport = { id -> controllerRef?.isSupportCircle(id) == true },
    ) { logSink.log(LogLevel.INFO, "App", it, null) }
    lifeSigns.start()
    // Stand-in names of the search in both languages: they never replace a remembered name of a radio.
    val pairedDevices = PairedDeviceRepository(
        store,
        placeholderNames = setOf(app.anynear.i18n.StatusTextsDe.bluetoothRadioFallbackName, app.anynear.i18n.StatusTextsEn.bluetoothRadioFallbackName),
    )
    // Usage statistics (ADR 0045): once a day, as long as the person has not turned them off.
    val usageStats = UsageStats(
        scope, core, store, profile, pairedDevices, version = { hooks.appVersion },
        now = { Clock.System.now().toEpochMilliseconds() }, testMode = hooks.testBuild,
    ) { logSink.log(LogLevel.INFO, "App", it, null) }
    usageStats.start()
    val controller = AppController(
        scope = scope,
        link = link,
        pairedDevices = pairedDevices,
        contactRepository = ContactRepository(store),
        messageStore = messageStore,
        hooks = hooks,
        scanBluetooth = scanner::scanBluetooth,
        scanNetwork = ::scanNetworkDevices,
        circleRepository = CircleRepository(scope, core.circles, store),
        profile = profile,
        memberDirectory = memberDirectory,
        core = core,
        store = store,
        attachmentFiles = AttachmentFiles(binaryDirs.getValue(AttachmentFiles.DIR_NAME), localCipher),
        avatars = AvatarImages(AttachmentFiles(binaryDirs.getValue(AvatarImages.DIR_NAME), localCipher), store),
        drafts = DraftStore(store, AttachmentFiles(binaryDirs.getValue(DraftStore.DIR_NAME), localCipher)),
        log = { logSink.log(LogLevel.INFO, "Lesen", it, null) },
        debugLog = debugLog,
        crashes = crashes,
        networkId = config.networkId.toString(),
        freshStart = freshStart,
        removedHere = removedHere,
        passwords = passwords,
        personalFiles = personalFiles,
        circleFiles = circleFiles,
        storage = storage,
        docReading = docReading,
        docFinder = docFinder,
        requestReset = { removed -> FreshStart.request(rawKeyValues, removed) },
        publicRadio = publicRadio,
        firmwareSource = RelayFirmwareSource(core),
        alerts = alerts,
        usageStats = usageStats,
        lifeSigns = lifeSigns,
    )
    controllerRef = controller
    host.target = controller.coreHost
    return controller
}

/** Tags in the app's log: read receipt and radio connection, not the verbose ones of the radio SDK. */
private val DEBUG_LOG_TAGS = setOf("Lesen", "App")
