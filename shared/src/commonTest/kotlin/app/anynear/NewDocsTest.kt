package app.anynear

import app.anynear.app.DeviceDocSource
import app.anynear.app.DeviceItem
import app.anynear.app.DocFinder
import app.anynear.app.DocSourceOpener
import app.anynear.app.FinderConditions
import app.anynear.app.FinderKeepAlive
import app.anynear.app.FinderMemory
import app.anynear.app.FinderPause
import app.anynear.app.FinderSchedule
import app.anynear.app.FinderState
import app.anynear.attachments.AttachmentFiles
import app.anynear.attachments.BinaryFiles
import app.anynear.core.LocalKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ADR 0081: find new documents on its own, only after „Ja“, only on the charger, cloud folders only on Wi-Fi.
 * Texts made up. */
class NewDocsTest {
    private val values = InMemoryStore()
    private val dir = Files()
    private val memory = FinderMemory(values, AttachmentFiles(dir, LocalKey.cipher(InMemorySecretStore())))

    private val id = "Bundesrepublik Deutschland\nPersonalausweis\nIdentity Card\nName Beispiel, Vorname Erika, " +
        "geboren am 12.08.1983 in Beispielhausen, gültig bis 01.02.2031, Staatsangehörigkeit deutsch, Behörde Beispielhausen"
    private val beach = ""

    /** Source with changeable content; [watchable] like the phone's photos, [cloud] like a Dropbox folder. */
    private class Source(
        val texts: MutableMap<String, String>,
        override val ref: String,
        override val watchable: Boolean = true,
        override val cloud: Boolean = false,
    ) : DeviceDocSource {
        override val folder = cloud
        val reads = mutableListOf<String>()
        var closed = 0
        override suspend fun items() = texts.keys.reversed().map { DeviceItem("$ref:$it", "$it.jpg", pdf = false, size = it.length * 1000L + 7) }
        override suspend fun preview(item: DeviceItem) = "small:${item.key}".encodeToByteArray()
        override suspend fun bytes(item: DeviceItem, keep: Boolean): ByteArray {
            reads += item.key
            return "full:${item.key}".encodeToByteArray()
        }
        override fun close() {
            closed++
        }
        fun text(bytes: ByteArray) = texts.getValue(bytes.decodeToString().substringAfter(':').substringAfter("$ref:"))
    }

    private class Conditions(var charging: Boolean = true, var wifi: Boolean = true) : FinderConditions {
        override fun batteryLow() = false
        override fun onWifi() = wifi
        override fun charging() = charging
    }

    private class Notices : FinderKeepAlive {
        val found = mutableListOf<Int>()
        override fun begin(stop: () -> Unit, expired: () -> Unit) = false
        override fun progress(checked: Int, total: Int, found: Int, pause: FinderPause?) = Unit
        override fun end(found: Int, completed: Boolean, visible: Boolean) = Unit
        override fun foundNew(found: Int) {
            this.found += found
        }
    }

    private class Schedule : FinderSchedule {
        val calls = mutableListOf<Boolean>()
        override fun set(on: Boolean) {
            calls += on
        }
    }

    private var clock = 1_800_000_000_000L

    private fun finder(sources: List<Source>, conditions: Conditions = Conditions(), notices: Notices = Notices(), schedule: Schedule = Schedule()) =
        DocFinder(
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
            memory,
            read = { bytes, _, _ -> sources.first { bytes.decodeToString().contains("${it.ref}:") }.text(bytes) },
            look = { null },
            conditions = conditions,
            keepAlive = notices,
            opener = DocSourceOpener { ref -> sources.firstOrNull { it.ref == ref } },
            schedule = schedule,
            now = { clock },
        ) {}

    private suspend fun firstRun(finder: DocFinder, source: Source): FinderState {
        finder.start(source, MutableStateFlow(true))
        return withTimeout(15_000) {
            while (!finder.state.value.done) delay(20)
            finder.state.value
        }
    }

    @Test
    fun staysOffUntilThePersonSaysYes() = runBlocking {
        val photos = Source(mutableMapOf("ausweis" to id, "strand" to beach), "photos")
        val finder = finder(listOf(photos))
        firstRun(finder, photos)
        // Until the question is answered, access stays (otherwise it would already be returned on Android).
        assertEquals(0, photos.closed)
        photos.texts["neu"] = id
        assertEquals(0, finder.checkNew())
        assertFalse(photos.reads.any { it.endsWith(":neu") })
        // „Nein, danke“ returns access as soon as no suggestion needs it any more; the question does not come again.
        finder.decided(finder.state.value.found.map { it.item })
        assertEquals(0, photos.closed)
        finder.setWatch(false)
        assertTrue(photos.closed > 0)
        assertTrue(memory.watchAsked)
        assertEquals(0, finder.checkNew())
    }

    @Test
    fun findsOnlyNewDocumentsWhileChargingAndNotifiesOnceADay() = runBlocking {
        val photos = Source(mutableMapOf("ausweis" to id, "strand" to beach), "photos")
        val notices = Notices()
        val schedule = Schedule()
        val conditions = Conditions(charging = false)
        val finder = finder(listOf(photos), conditions, notices, schedule)
        firstRun(finder, photos)
        finder.decided(finder.state.value.found.map { it.item })
        finder.setWatch(true)
        assertEquals(listOf(true), schedule.calls)
        assertEquals(0, photos.closed)

        photos.texts["neu"] = id
        photos.reads.clear()
        // Without a charger the app does not look.
        assertEquals(0, finder.checkNew())
        conditions.charging = true
        assertEquals(1, finder.checkNew())
        // Only the new is read; ID card and beach are already checked.
        assertEquals(listOf("photos:neu"), photos.reads)
        assertEquals(listOf(1), notices.found)
        assertEquals(listOf("photos:neu"), finder.state.value.found.map { it.item.key })

        // On the same day: the suggestion is added, but no second notification.
        photos.texts["noch"] = id
        assertEquals(1, finder.checkNew())
        assertEquals(listOf(1), notices.found)
        // The next day again.
        clock += 24L * 60 * 60 * 1000
        photos.texts["morgen"] = id
        assertEquals(1, finder.checkNew())
        assertEquals(listOf(1, 1), notices.found)

        finder.setWatch(false)
        assertEquals(listOf(true, false), schedule.calls)
    }

    @Test
    fun cloudFoldersOnlyOnWifi() = runBlocking {
        val photos = Source(mutableMapOf("strand" to beach), "photos")
        val cloud = Source(mutableMapOf("alt" to beach), "dropbox", cloud = true)
        val conditions = Conditions()
        val finder = finder(listOf(photos, cloud), conditions)
        firstRun(finder, photos)
        firstRun(finder, cloud)
        finder.setWatch(true)
        conditions.wifi = false
        photos.texts["brief"] = id
        cloud.texts["scan"] = id
        // Without Wi-Fi only what is on the phone.
        assertEquals(1, finder.checkNew())
        assertFalse(cloud.reads.any { it.endsWith(":scan") })
        conditions.wifi = true
        assertEquals(1, finder.checkNew())
        assertEquals(setOf("photos:brief", "dropbox:scan"), finder.state.value.found.map { it.item.key }.toSet())
    }

    @Test
    fun pickedPhotosAreNeverWatched() = runBlocking {
        val picked = Source(mutableMapOf("ausweis" to id), "picked", watchable = false)
        val finder = finder(listOf(picked))
        firstRun(finder, picked)
        assertTrue(memory.watchSources.isEmpty())
    }

    private class Files : BinaryFiles {
        private val map = linkedMapOf<String, ByteArray>()
        override fun read(name: String) = map[name]
        override fun write(name: String, content: ByteArray) {
            map[name] = content
        }
        override fun delete(name: String) {
            map.remove(name)
        }
        override fun names() = map.keys.toList()
    }
}
