package app.anynear

import app.anynear.data.RawKeyValueAccess
import app.anynear.data.StorageVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stored data is migrated, never discarded (ADR 0061, decision 13). Made-up values. */
class StorageVersionTest {
    private class Raw(var map: Map<String, String> = emptyMap()) : RawKeyValueAccess {
        override fun entries() = map

        override fun replaceAll(values: Map<String, String>) {
            map = values
        }
    }

    private fun stores(raw: Raw) = StorageVersion.Stores(raw, InMemorySecretStore()) { emptySet() }

    @Test
    fun newInstallationStartsAtTheCurrentVersion() {
        val raw = Raw()
        assertEquals(StorageVersion.CURRENT, StorageVersion.migrate(stores(raw)))
        assertEquals(mapOf(StorageVersion.KEY to StorageVersion.CURRENT.toString()), raw.map)
    }

    @Test
    fun theListOfMigrationsMatchesTheCurrentVersion() {
        assertEquals(StorageVersion.CURRENT, StorageVersion.FIRST + StorageVersion.MIGRATIONS.size)
    }

    @Test
    fun migrationsRunInOrderAndOnlyOnce() {
        val ran = mutableListOf<Int>()
        val migrations = listOf(
            StorageVersion.Migration { s -> ran += 1; s.keyValues.replaceAll(s.keyValues.entries() + ("b" to "aus a")) },
            StorageVersion.Migration { s -> ran += 2; s.keyValues.replaceAll(s.keyValues.entries() - "a") },
        )
        val raw = Raw(mapOf("a" to "1"))
        assertEquals(3, StorageVersion.migrate(stores(raw), migrations))
        assertEquals(listOf(1, 2), ran)
        assertEquals(mapOf("b" to "aus a", StorageVersion.KEY to "3"), raw.map)

        assertEquals(3, StorageVersion.migrate(stores(raw), migrations))
        assertEquals(listOf(1, 2), ran)
    }

    @Test
    fun anInterruptedChainContinuesWhereItStopped() {
        val ran = mutableListOf<Int>()
        val migrations = listOf(StorageVersion.Migration { ran += 1 }, StorageVersion.Migration { ran += 2 })
        val raw = Raw(mapOf(StorageVersion.KEY to "2"))
        assertEquals(3, StorageVersion.migrate(stores(raw), migrations))
        assertEquals(listOf(2), ran)
    }

    @Test
    fun aNewerStorageStaysUntouched() {
        val raw = Raw(mapOf(StorageVersion.KEY to "9", "x" to "y"))
        assertEquals(9, StorageVersion.migrate(stores(raw)))
        assertEquals(mapOf(StorageVersion.KEY to "9", "x" to "y"), raw.map)
        assertTrue(raw.map.containsKey("x"))
    }

    @Test
    fun version2DropsTheOldBetaKey() {
        val raw = Raw(mapOf(StorageVersion.KEY to "1"))
        val secrets = InMemorySecretStore().apply {
            put("beta_key", "ausgedacht".encodeToByteArray())
            put("beta_key_checked", "1".encodeToByteArray())
            put("anderes", byteArrayOf(1))
        }
        assertEquals(StorageVersion.CURRENT, StorageVersion.migrate(StorageVersion.Stores(raw, secrets) { emptySet() }))
        assertEquals(null, secrets.get("beta_key"))
        assertEquals(null, secrets.get("beta_key_checked"))
        assertTrue(secrets.get("anderes") != null)
    }

    @Test
    fun version3ChangesNothingStored() {
        val raw = Raw(mapOf(StorageVersion.KEY to "2", "andere" to "bleibt"))
        val secrets = InMemorySecretStore().apply { put("anderes", byteArrayOf(1)) }
        assertEquals(StorageVersion.CURRENT, StorageVersion.migrate(StorageVersion.Stores(raw, secrets) { emptySet() }))
        assertEquals("bleibt", raw.map["andere"])
        assertTrue(secrets.get("anderes") != null)
    }

    /** 3 → 4: `onlineOnly` on the files of the storages (ADR 0080) has a default; nothing is rewritten. */
    @Test
    fun version4ChangesNothingStored() {
        val files = """[{"id":"a","name":"a.pdf","kind":"PDF","size":1,"addedAt":1}]"""
        val raw = Raw(mapOf(StorageVersion.KEY to "3", "personal.files" to files))
        assertEquals(4, StorageVersion.migrate(StorageVersion.Stores(raw, InMemorySecretStore()) { emptySet() }, StorageVersion.MIGRATIONS.take(3), 4))
        assertEquals(files, raw.map["personal.files"])
    }

    /** 4 → 5: `operators` and `everSecured` (ADR 0028 §7) have defaults; nothing is rewritten. */
    @Test
    fun version5ChangesNothingStored() {
        val files = """[{"id":"a","name":"a.pdf","kind":"PDF","size":1,"addedAt":1,"online":true}]"""
        val raw = Raw(mapOf(StorageVersion.KEY to "4", "personal.files" to files))
        assertEquals(5, StorageVersion.migrate(StorageVersion.Stores(raw, InMemorySecretStore()) { emptySet() }, StorageVersion.MIGRATIONS.take(4), 5))
        assertEquals(files, raw.map["personal.files"])
    }

    /** 5 → 6: `title` (ADR 0091) has a default and the order is a new key; nothing is rewritten. */
    @Test
    fun version6ChangesNothingStored() {
        val files = """[{"id":"a","name":"a.pdf","kind":"PDF","size":1,"addedAt":1,"online":true,"operators":2}]"""
        val raw = Raw(mapOf(StorageVersion.KEY to "5", "personal.files" to files))
        assertEquals(6, StorageVersion.migrate(StorageVersion.Stores(raw, InMemorySecretStore()) { emptySet() }, StorageVersion.MIGRATIONS.take(5), 6))
        assertEquals(files, raw.map["personal.files"])
    }

    /** 6 → 7: `replyTo` (ADR 0092) has a default; stored messages stay as they are and read as "no answer". */
    @Test
    fun version7ChangesNothingStored() {
        val history = """[{"localId":"a","conversation":{"type":"circle","circleId":"k"},"fromNodeNum":0,"text":"Hallo","timestampMillis":1,"outgoing":false}]"""
        val raw = Raw(mapOf(StorageVersion.KEY to "6", "messages" to history))
        assertEquals(7, StorageVersion.migrate(StorageVersion.Stores(raw, InMemorySecretStore()) { emptySet() }, StorageVersion.MIGRATIONS.take(6), 7))
        assertEquals(history, raw.map["messages"])
    }

    /** 7 → 8: own names for other people are a new key `contact_names`; everything stored stays as it is. */
    @Test
    fun version8ChangesNothingStored() {
        val history = """[{"localId":"a","conversation":{"type":"circle","circleId":"k"},"fromNodeNum":0,"text":"Hallo","timestampMillis":1,"outgoing":false}]"""
        val raw = Raw(mapOf(StorageVersion.KEY to "7", "messages" to history, "read_until" to """{"c:k":1}"""))
        assertEquals(StorageVersion.CURRENT, StorageVersion.migrate(StorageVersion.Stores(raw, InMemorySecretStore()) { emptySet() }))
        assertEquals(history, raw.map["messages"])
        assertEquals("""{"c:k":1}""", raw.map["read_until"])
        assertEquals(null, raw.map["contact_names"])
    }

    /** 8 → 9: messages have `spot` (ADR 0121 §8) with default `null`; a history without it reads as before. */
    @Test
    fun version9ReadsMessagesWithoutAPlace() {
        val history = """[{"localId":"a","conversation":{"type":"circle","circleId":"k"},"fromNodeNum":0,"text":"Hallo","timestampMillis":1,"outgoing":false}]"""
        val raw = Raw(mapOf(StorageVersion.KEY to "8", "messages" to history))
        assertEquals(9, StorageVersion.migrate(StorageVersion.Stores(raw, InMemorySecretStore()) { emptySet() }))
        assertEquals(history, raw.map["messages"])
        val read = kotlinx.serialization.json.Json.decodeFromString<List<app.anynear.mesh.ChatMessage>>(history)
        assertEquals(null, read.single().spot)
        val with = read.single().copy(spot = app.anynear.mesh.ChatSpot(app.anynear.mesh.ChatSpot.MEET, 50.1, 8.6, 1, label = "Eingang"))
        assertEquals(with, kotlinx.serialization.json.Json.decodeFromString<app.anynear.mesh.ChatMessage>(kotlinx.serialization.json.Json.encodeToString(app.anynear.mesh.ChatMessage.serializer(), with)))
    }
}
