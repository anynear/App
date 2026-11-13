package app.anynear

import app.anynear.anmp.core.DocKind
import app.anynear.anmp.core.PhotoTopics
import app.anynear.app.DeviceDocSource
import app.anynear.app.DeviceItem
import app.anynear.app.DocFinder
import app.anynear.app.DocSourceOpener
import app.anynear.app.SavedRun
import app.anynear.app.FinderConditions
import app.anynear.app.FinderKeepAlive
import app.anynear.app.FinderMemory
import app.anynear.app.FinderPause
import app.anynear.app.FinderState
import app.anynear.app.NoFinderKeepAlive
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

/** ADR 0071: search run on the device only, pre-check, suggestions, memory per phone, pauses. All texts are made up. */
class DocFinderTest {
    private val secrets = InMemorySecretStore()
    private val values = InMemoryStore()
    private val cipher = LocalKey.cipher(secrets)
    private val dir = Files()
    private val memory = FinderMemory(values, AttachmentFiles(dir, cipher))

    private val invoice = "Standesamt Beispielhausen\nGeburtsurkunde\nVorname Erika, Familienname Beispiel\n" +
        "Geboren am 12.03.2019 in Beispielhausen. Eltern: Max Beispiel und Maria Beispiel, beide wohnhaft in Beispielhausen."
    private val notes = "Einkaufsliste für das Wochenende mit Brot, Butter, Milch, Käse, Äpfeln, Birnen, Tomaten, Gurken und Kaffee"

    /** Images [key] with text per key; „scene“ shows a landscape. */
    private class Source(
        private val texts: Map<String, String>,
        override val folder: Boolean = false,
        private val prefix: String = "",
        override val ref: String? = null,
    ) : DeviceDocSource {
        val fullReads = mutableListOf<String>()
        var closed = false
        override suspend fun items() = texts.keys.map { DeviceItem("$prefix$it", "$it.jpg", pdf = false, size = it.length * 1000L) }
        override suspend fun preview(item: DeviceItem) = "small:${item.key}".encodeToByteArray()
        override suspend fun bytes(item: DeviceItem, keep: Boolean): ByteArray = full(item)
        private fun full(item: DeviceItem): ByteArray {
            fullReads += item.key.removePrefix(prefix)
            return "full:${item.key}".encodeToByteArray()
        }
        override fun close() {
            closed = true
        }
        fun text(bytes: ByteArray) = texts.getValue(bytes.decodeToString().substringAfter(':').removePrefix(prefix))
    }

    private class Conditions(var low: Boolean = false, var wifi: Boolean = true) : FinderConditions {
        override fun batteryLow() = low
        override fun onWifi() = wifi
    }

    private fun finder(
        source: Source,
        conditions: FinderConditions = Conditions(),
        keepAlive: FinderKeepAlive = NoFinderKeepAlive,
        opener: DocSourceOpener = DocSourceOpener { null },
    ) = DocFinder(
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
        memory,
        read = { bytes, _, _ -> source.text(bytes) },
        look = { bytes -> if (bytes.decodeToString().endsWith("scene")) listOf(PhotoTopics.NATURE) else emptyList() },
        conditions = conditions,
        keepAlive = keepAlive,
        opener = opener,
    ) {}

    private val certificate = "Musterschule Beispielhausen\nAbschlusszeugnis\nVorname Erika, Familienname Beispiel, " +
        "hat die Abschlussprüfung an der Musterschule Beispielhausen am 30.06.2018 bestanden. Die Schulleitung."

    /** ADR 0071 item 10: suggestions survive a restart, even after the run has ended. */
    @Test
    fun suggestionsSurviveARestart() = runBlocking {
        val source = Source(mapOf("letter" to invoice, "list" to notes), ref = "photos")
        val before = finder(source)
        before.start(source, MutableStateFlow(true))
        val done = until(before) { it.done }
        assertEquals(1, done.found.size)
        // No longer running, suggestion open: access stays so that „Übernehmen“ can still read it later.
        assertFalse(source.closed)

        // Restart: new object, same memory; the source opens again.
        val reopened = Source(mapOf("letter" to invoice, "list" to notes), ref = "photos")
        val after = finder(reopened, opener = { ref -> reopened.takeIf { ref == "photos" } })
        after.restore(MutableStateFlow(true))
        val restored = after.state.value
        assertTrue(restored.done)
        assertFalse(restored.running)
        assertFalse(restored.interrupted)
        val found = restored.found.single()
        assertEquals(done.found.single().doc.kind, found.doc.kind)
        assertTrue(found.recognized)
        assertEquals(done.found.single().text, found.text)
        assertEquals("small:letter", found.preview?.decodeToString())
        // Accepting reads the whole file from the reopened source.
        assertEquals("full:letter", after.bytesFor(found)?.decodeToString())

        // Decided: nothing open any more, access returned, nothing stored any more.
        after.decided(listOf(found.item))
        assertTrue(reopened.closed)
        assertEquals(null, memory.loadRun())
    }

    /** ADR 0071 item 10: a run ended by a restart or update continues if access still exists. */
    @Test
    fun runningSearchContinuesAfterARestart() = runBlocking {
        val first = Source(mapOf("letter" to invoice), ref = "photos")
        val before = finder(first)
        before.start(first, MutableStateFlow(true))
        until(before) { it.done }
        // This is the state when the phone ends the run midway: running, one suggestion open.
        val saved = memory.loadRun()!!
        memory.saveRun(SavedRun(source = "photos", running = true, found = saved.found))

        val later = Source(mapOf("letter" to invoice, "school" to certificate, "list" to notes), ref = "photos")
        val after = finder(later, opener = { later })
        after.restore(MutableStateFlow(true))
        val state = until(after) { it.done && !it.running }
        assertEquals(setOf("letter", "school"), state.found.map { it.item.key }.toSet())
        // The letter already found is not read again.
        assertFalse("letter" in later.fullReads)
        assertTrue("school" in later.fullReads)
    }

    /** ADR 0071 item 10: without access after the restart the run counts as interrupted, the suggestions stay. */
    @Test
    fun withoutAccessTheRunIsInterruptedButSuggestionsStay() = runBlocking {
        val first = Source(mapOf("letter" to invoice), ref = "folder:x")
        val before = finder(first)
        before.start(first, MutableStateFlow(true))
        until(before) { it.done }
        memory.saveRun(SavedRun(source = "folder:x", running = true, found = memory.loadRun()!!.found))

        val after = finder(first, opener = { null })
        after.restore(MutableStateFlow(true))
        val state = after.state.value
        assertTrue(state.interrupted)
        assertFalse(state.running)
        assertEquals(1, state.found.size)
        assertTrue(state.active)
        assertEquals(null, after.bytesFor(state.found.single()))
    }

    /** Without suggestions at the end the run returns access right away and stores nothing (items 2 and 10). */
    @Test
    fun withoutSuggestionsAccessIsGivenBackAtTheEnd() = runBlocking {
        val source = Source(mapOf("list" to notes), ref = "photos")
        val finder = finder(source)
        finder.start(source, MutableStateFlow(true))
        until(finder) { it.done }
        assertTrue(source.closed)
        assertEquals(null, memory.loadRun())
    }

    /** Background as on Android or iOS 26 (ADR 0071 item 9), with everything the run reports. */
    private class KeepAlive(private val allowed: Boolean) : FinderKeepAlive {
        var stop: (() -> Unit)? = null
        var expired: (() -> Unit)? = null
        val progress = mutableListOf<Int>()
        var ended: Triple<Int, Boolean, Boolean>? = null
        override fun begin(stop: () -> Unit, expired: () -> Unit): Boolean {
            this.stop = stop
            this.expired = expired
            return allowed
        }
        override fun progress(checked: Int, total: Int, found: Int, pause: FinderPause?) {
            progress += checked
        }
        override fun end(found: Int, completed: Boolean, visible: Boolean) {
            ended = Triple(found, completed, visible)
        }
    }

    /** ADR 0071 item 9: a started run finishes in the background if the platform keeps it. */
    @Test
    fun startedSearchFinishesInTheBackground() = runBlocking {
        val source = Source(mapOf("letter" to invoice, "list" to notes))
        val keepAlive = KeepAlive(allowed = true)
        val finder = finder(source, keepAlive = keepAlive)
        finder.start(source, MutableStateFlow(false))
        val state = until(finder) { it.done }
        assertEquals(2, state.checked)
        // „Fertig“ is set shortly before the background ends; wait for it, else the test is flaky on slow machines.
        withTimeout(5_000) { while (keepAlive.ended == null) delay(10) }
        // The shopping list is not an important document (ADR 0074).
        assertEquals(Triple(1, true, false), keepAlive.ended)
        assertEquals(listOf(1, 2), keepAlive.progress.filter { it > 0 })
    }

    @Test
    fun withoutBackgroundOrWhenTakenBackTheSearchPauses() = runBlocking {
        // Without background (before iOS 26): pause as soon as the app is not visible.
        val plain = finder(Source(mapOf("letter" to invoice)))
        plain.start(Source(mapOf("letter" to invoice)), MutableStateFlow(false))
        assertEquals(FinderPause.BACKGROUND, until(plain) { it.pause != null }.pause)
        plain.cancel()

        // With background, but the platform takes it back: the same pause afterwards.
        val conditions = Conditions(low = true)
        val keepAlive = KeepAlive(allowed = true)
        val taken = finder(Source(mapOf("letter" to invoice)), conditions, keepAlive)
        taken.start(Source(mapOf("letter" to invoice)), MutableStateFlow(false))
        assertEquals(FinderPause.BATTERY, until(taken) { it.pause != null }.pause)
        keepAlive.expired!!.invoke()
        conditions.low = false
        assertEquals(FinderPause.BACKGROUND, until(taken) { it.pause == FinderPause.BACKGROUND }.pause)
        taken.cancel()
    }

    @Test
    fun stopInTheNotificationCancelsAndKeepsResults() = runBlocking {
        val conditions = Conditions(low = true)
        val keepAlive = KeepAlive(allowed = true)
        val source = Source(mapOf("letter" to invoice))
        val finder = finder(source, conditions, keepAlive)
        finder.start(source, MutableStateFlow(false))
        until(finder) { it.pause == FinderPause.BATTERY }
        keepAlive.stop!!.invoke()
        val stopped = until(finder) { it.done }
        assertFalse(stopped.running)
        assertEquals(false, keepAlive.ended?.second)
    }

    /** Checked items are skipped and counted; „Alles noch einmal prüfen“ and a better reader include them. */
    @Test
    fun skippedFilesAreCountedAndCanBeCheckedAgain() = runBlocking {
        val source = Source(mapOf("list" to notes, "other" to notes + " und Tee"))
        val first = finder(source)
        first.start(source, MutableStateFlow(true))
        assertEquals(0, until(first) { it.done }.skipped)
        memory.save()
        assertEquals(2, memory.checkedCount())

        val second = finder(source)
        second.start(source, MutableStateFlow(true))
        val skipped = until(second) { it.done }
        assertEquals(2, skipped.skipped)
        assertEquals(0, skipped.total)

        val again = finder(source)
        again.start(source, MutableStateFlow(true), again = true)
        val all = until(again) { it.done }
        assertEquals(0, all.skipped)
        assertEquals(2, all.checked)

        // A better reader (ADR 0078) checks what an older one looked at without a hit.
        memory.save()
        val better = FinderMemory(values, AttachmentFiles(dir, cipher), readerVersion = 2)
        assertEquals(0, better.checkedCount())
        assertFalse(better.skip(DeviceItem("list", "list.jpg", pdf = false, size = 1)))
    }

    private suspend fun until(finder: DocFinder, check: (FinderState) -> Boolean): FinderState = withTimeout(15_000) {
        while (!check(finder.state.value)) delay(20)
        finder.state.value
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
