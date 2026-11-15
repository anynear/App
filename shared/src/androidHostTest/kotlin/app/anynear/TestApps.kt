package app.anynear

import app.anynear.anmp.core.AnmpCore
import app.anynear.anmp.core.AnmpCoreConfig
import app.anynear.anmp.core.AnmpPlatform
import app.anynear.anmp.core.RelayConnector
import app.anynear.app.AppController
import app.anynear.app.PlatformHooks
import app.anynear.attachments.AttachmentFiles
import app.anynear.attachments.BinaryFiles
import app.anynear.circles.CircleRepository
import app.anynear.circles.ProfileRepository
import app.anynear.contacts.ContactRepository
import app.anynear.core.CoreBlobs
import app.anynear.core.CoreKeyValues
import app.anynear.core.CoreSecrets
import app.anynear.core.ForwardingHost
import app.anynear.core.LocalKey
import app.anynear.data.DraftStore
import app.anynear.data.KeyValueStore
import app.anynear.data.MessageStore
import app.anynear.data.PairedDeviceRepository
import app.anynear.data.SecretStore
import app.anynear.images.AvatarImages
import app.anynear.mesh.FoundDevice
import app.anynear.mesh.MeshLink
import app.anynear.people.MemberDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/** Thread-safe in-memory files for the app tests. */
internal class TestFiles : BinaryFiles {
    private val map = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    override fun read(name: String) = map[name]
    override fun write(name: String, content: ByteArray) { map[name] = content }
    override fun delete(name: String) { map.remove(name) }
    override fun names() = map.keys().toList()
}

/** Thread-safe stores for the app tests (several coroutines, one device). */
internal class ConcurrentStore : KeyValueStore {
    private val map = java.util.concurrent.ConcurrentHashMap<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
}

internal class ConcurrentSecrets : SecretStore {
    private val map = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    override fun get(key: String): ByteArray? = map[key]
    override fun put(key: String, value: ByteArray?) { if (value == null) map.remove(key) else map[key] = value }
}

/**
 * Full app controller as in `createAppController`, with the ANMP core against [config] via [connector]
 * and the stores in memory. Starts it right away.
 */
internal fun testController(
    scope: CoroutineScope,
    link: MeshLink,
    hooks: PlatformHooks,
    config: AnmpCoreConfig,
    connector: RelayConnector,
    store: KeyValueStore = ConcurrentStore(),
    secrets: SecretStore = ConcurrentSecrets(),
    // Never finds anything (instead of ending empty, which triggers an exception in the device scan).
    scanNetwork: () -> Flow<FoundDevice> = { flow { awaitCancellation() } },
): AppController {
    val cipher = LocalKey.cipher(secrets)
    val host = ForwardingHost()
    val core = AnmpCore(scope, config, AnmpPlatform(CoreSecrets(secrets), CoreKeyValues(store), CoreBlobs(TestFiles(), cipher), connector = connector, log = testCoreLog()), host)
    return AppController(
        scope = scope,
        link = link,
        pairedDevices = PairedDeviceRepository(store),
        contactRepository = ContactRepository(store),
        messageStore = MessageStore(),
        hooks = hooks,
        scanBluetooth = { emptyFlow() },
        scanNetwork = scanNetwork,
        circleRepository = CircleRepository(scope, core.circles, store),
        profile = ProfileRepository(store),
        memberDirectory = MemberDirectory(scope, core.members),
        core = core,
        store = store,
        attachmentFiles = AttachmentFiles(TestFiles(), cipher),
        avatars = AvatarImages(AttachmentFiles(TestFiles(), cipher), store),
        drafts = DraftStore(store, AttachmentFiles(TestFiles(), cipher)),
        networkId = config.networkId.toString(),
    ).also {
        host.target = it.coreHost
        it.start()
    }
}

/** Log of the core to the console, only with `CORE_LOG=1` (troubleshooting in the E2E suites). */
private fun testCoreLog(): app.anynear.anmp.core.CoreLog =
    if (System.getenv("CORE_LOG") == "1") app.anynear.anmp.core.CoreLog { println("core ${Thread.currentThread().id}: $it") } else app.anynear.anmp.core.CoreLog.NONE
