package app.anynear

import app.anynear.contacts.ContactRepository
import app.anynear.contacts.ContactRepository.ImportResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ContactRepositoryTest {
    private val store = InMemoryStore()
    private val repo = ContactRepository(store)

    @Test
    fun addsAndPersistsContact() {
        assertIs<ImportResult.Added>(repo.add(sharedContact(7, "Testgerät Sieben"), ownNodeNum = 1))

        val reloaded = ContactRepository(store)
        assertEquals(listOf("Testgerät Sieben"), reloaded.contacts.value.map { it.displayName })
        // Without the former prefix `PINA1:` (ADR 0034).
        assertEquals(false, store.getString("contacts.v2").orEmpty().contains("PINA"))
    }

    @Test
    fun sameContactTwiceIsNoDuplicate() {
        repo.add(sharedContact(7), ownNodeNum = 1)

        assertIs<ImportResult.AlreadyKnown>(repo.add(sharedContact(7), ownNodeNum = 1))
        assertEquals(1, repo.contacts.value.size)
    }

    @Test
    fun changedKeyIsNotSilentlyAccepted() {
        repo.add(sharedContact(7, key = "schluessel-a"), ownNodeNum = 1)

        val result = repo.add(sharedContact(7, key = "schluessel-b"), ownNodeNum = 1)

        assertIs<ImportResult.KeyMismatch>(result)
        assertEquals("schluessel-a", repo.find(7)?.publicKey?.utf8())
    }

    @Test
    fun ownDeviceIsRejected() {
        assertIs<ImportResult.OwnDevice>(repo.add(sharedContact(1), ownNodeNum = 1))
    }

    /** How the radio contact travels along in the contact and invitation link (field „radio“ in the ANMP link). */
    @Test
    fun radioContactBytesRoundTrip() {
        val contact = sharedContact(7, "Testgerät Sieben")
        assertEquals(contact, ContactRepository.fromBytes(ContactRepository.bytesOf(contact)))
        assertNull(ContactRepository.fromBytes(byteArrayOf(1, 2, 3)))
        assertNull(ContactRepository.fromBytes(null))
    }
}
