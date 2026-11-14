package app.anynear

import app.anynear.anmp.Anmp
import app.anynear.anmp.AnmpContent
import app.anynear.anmp.AnmpKind
import app.anynear.anmp.AnmpVerifier
import app.anynear.anmp.core.CircleState
import app.anynear.anmp.core.AnmpCoreConfig
import app.anynear.AppConfig
import app.anynear.app.RelayState
import app.anynear.app.relayStatuses
import app.anynear.attachments.BinaryFiles
import app.anynear.chats.findAppLink
import app.anynear.circles.CircleRepository
import app.anynear.core.CoreBlobs
import app.anynear.core.CoreSecretKeys
import app.anynear.core.FreshStart
import app.anynear.core.AppNet
import app.anynear.core.b64
import app.anynear.data.LocalCipher
import app.anynear.data.RawKeyValueAccess
import app.anynear.data.TextFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Connection of the app to the ANMP core (ADR 0034–0036, 0061). Made-up keys, no real data. */
class CoreGlueTest {
    private val secret = b64.encode(ByteArray(32) { it.toByte() })

    @Test
    fun circleMirrorsTheCoreWithoutARadioChannelOfItsOwn() = runTest {
        val source = MutableStateFlow(listOf(CircleState(id = "kreis-1", name = "Familie", secret = secret)))
        val repo = CircleRepository(backgroundScope, source, InMemoryStore())
        val circle = repo.circles.first { it.isNotEmpty() }.single()
        assertEquals("kreis-1", circle.id)
        assertEquals("Familie", circle.name)
        // There are no channels per circle (ADR 0051): a former circle channel is cleared away.
        assertTrue(AppConfig.isOldChannelName("PYRSA-K3V9Q"))
    }

    @Test
    fun channelsOfEarlierAppsAreOld() {
        // ADR 0061, decision 6: the app clears away the channels of earlier versions.
        assertTrue(AppConfig.isOldChannelName("PYRSA"))
        assertTrue(AppConfig.isOldChannelName("PYRSA-K3V9Q"))
        assertTrue(AppConfig.isOldChannelName("PINA"))
        assertTrue(AppConfig.isOldChannelName("PINA-K3V9QX"))
        assertFalse(AppConfig.isOldChannelName("anynear"))
        assertFalse(AppConfig.isOldChannelName("Mesh Hessen"))
        assertFalse(AppConfig.isOldChannelName(""))
        assertFalse(AppConfig.isOldChannelName(null))
    }

    @Test
    fun linksLiveOnAnynearAppAndNet() {
        // Created on anynear.app, also recognised on anynear.net (ADR 0061, decision 4); old hosts no longer.
        assertEquals("https://anynear.app/k#abc", findAppLink("Komm dazu: https://anynear.app/k#abc"))
        assertEquals("https://anynear.net/k#abc", findAppLink("Komm dazu: https://anynear.net/k#abc"))
        assertEquals("https://anynear.net/c#xyz", findAppLink("https://anynear.net/c#xyz"))
        assertNull(findAppLink("https://old-host.example/k#abc"))
        assertNull(findAppLink("https://example.org/k#abc"))
    }

    @Test
    fun networkOfTheAppIsTheDefaultNetwork() {
        val config = AppNet.config()
        if (!AppNet.DEV) assertEquals(AnmpCoreConfig.default().networkId, config.networkId)
        assertEquals("anynear.app", config.linkHost)
        assertEquals(listOf("anynear.net"), config.otherLinkHosts)
    }

    @Test
    fun devAddressesGoThroughTheHostPorts() {
        assertTrue(AppNet.devAddress("172.29.0.13:8080").endsWith(":9083"))
        assertEquals("78.0.0.1:443", AppNet.devAddress("78.0.0.1:443"))
    }

    @Test
    fun relayRowsShowSiteAndState() {
        val rows = relayStatuses(app.anynear.anmp.core.RelayStatus(listOf("nodeA-12345", "nodeB-12345"), listOf("nodeB-12345"), listOf("fr-par-01", "")))
        assertEquals(listOf("fr-par-01", ""), rows.map { it.label })
        assertEquals(listOf(RelayState.UNREACHABLE, RelayState.REACHED), rows.map { it.state })
    }

    @Test
    fun coreBlobsAreEncryptedOnTheDevice() = runTest {
        val files = MemoryFiles()
        val blobs = CoreBlobs(files, LocalCipher(ByteArray(32) { 9 }))
        blobs.write("core/att/abc_-1", byteArrayOf(1, 2, 3))
        assertEquals(listOf("core~att~abc_-1"), files.names())
        assertFalse(files.read("core~att~abc_-1")!!.contentEquals(byteArrayOf(1, 2, 3)))
        assertContentEquals(byteArrayOf(1, 2, 3), blobs.read("core/att/abc_-1"))
        blobs.delete("core/att/abc_-1")
        assertNull(blobs.read("core/att/abc_-1"))
    }

    // --- Fresh start (ADR 0046) -----------------------------------------------------

    private class Raw(var map: Map<String, String> = emptyMap()) : RawKeyValueAccess {
        override fun entries() = map

        override fun replaceAll(values: Map<String, String>) {
            map = values
        }
    }

    private class Text(var content: String = "") : TextFile {
        override fun read() = content

        override fun write(content: String) {
            this.content = content
        }
    }

    @Test
    fun requestedResetWipesEverythingWithoutNotice() {
        val raw = Raw(mapOf("own_name" to "local-v1:neu"))
        val secrets = InMemorySecretStore().apply {
            put(CoreSecretKeys.DEVICE_X, ByteArray(32))
            put("beta_key", "ausgedacht".encodeToByteArray())
        }
        val files = MemoryFiles().apply { write("blob", byteArrayOf(1)) }
        val history = Text("verlauf")
        val keys = setOf(CoreSecretKeys.DEVICE_X, "beta_key")

        FreshStart.request(raw)
        assertTrue(FreshStart.requested(raw))
        assertTrue(FreshStart.runIfNeeded(raw, secrets, keys, listOf(files), listOf(history)))
        assertNull(secrets.get(CoreSecretKeys.DEVICE_X))
        assertNull(secrets.get("beta_key"))
        assertEquals(emptyList(), files.names())
        assertEquals("", history.content)
        assertEquals(emptyMap(), raw.map)
        assertFalse(FreshStart.requested(raw))
        assertFalse(FreshStart.runIfNeeded(raw, secrets, emptySet(), listOf(files), listOf(history)))
    }

    @Test
    fun removedByAnotherOwnDeviceWipesEverythingWithNotice() {
        val raw = Raw(mapOf("own_name" to "local-v1:neu"))
        val secrets = InMemorySecretStore().apply {
            put(CoreSecretKeys.DEVICE_X, ByteArray(32))
            put("beta_key", "ausgedacht".encodeToByteArray())
        }
        val files = MemoryFiles().apply { write("blob", byteArrayOf(1)) }
        val history = Text("verlauf")

        FreshStart.request(raw, removed = true)
        assertTrue(FreshStart.requested(raw))
        assertTrue(FreshStart.runIfNeeded(raw, secrets, setOf(CoreSecretKeys.DEVICE_X, "beta_key"), listOf(files), listOf(history)))
        assertNull(secrets.get(CoreSecretKeys.DEVICE_X))
        assertNull(secrets.get("beta_key"))
        assertEquals(emptyList(), files.names())
        assertEquals("", history.content)
        assertEquals(mapOf(FreshStart.NOTICE_KEY to FreshStart.REMOVED), raw.map)
        assertFalse(FreshStart.requested(raw))

        val plain = InMemoryStore().apply { putString(FreshStart.NOTICE_KEY, FreshStart.REMOVED) }
        assertTrue(FreshStart.removedNoticePending(plain))
        assertFalse(FreshStart.noticePending(plain))
    }

    @Test
    fun newInstallationIsNoFreshStart() {
        val raw = Raw(mapOf("own_name" to "local-v1:neu"))
        assertFalse(FreshStart.runIfNeeded(raw, InMemorySecretStore(), setOf(CoreSecretKeys.DEVICE_X), emptyList(), emptyList()))
        assertEquals(mapOf("own_name" to "local-v1:neu"), raw.map)
    }
}

/** In-memory folders for tests. */
internal class MemoryFiles : BinaryFiles {
    private val map = mutableMapOf<String, ByteArray>()

    override fun read(name: String): ByteArray? = map[name]

    override fun write(name: String, content: ByteArray) {
        map[name] = content
    }

    override fun delete(name: String) {
        map.remove(name)
    }

    override fun names(): List<String> = map.keys.toList()
}
