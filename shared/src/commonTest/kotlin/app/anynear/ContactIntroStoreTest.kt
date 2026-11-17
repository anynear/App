package app.anynear

import app.anynear.mesh.ChatMessage
import app.anynear.mesh.ContactIntro
import app.anynear.mesh.Conversation
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Introduced contacts in stored messages (ADR 0116): kept across a restart, older entries read as before. */
class ContactIntroStoreTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun introSurvivesStoring() {
        val intro = ContactIntro("aWQ", "cGVyc29u", "Ilse", "YW5uYQ", 1_800_000_000_000, "Y2FyZA")
        val m = ChatMessage(
            localId = "l1", conversation = Conversation.Direct("d:anna"), fromNodeNum = 0, text = "Kontakt: Ilse",
            timestampMillis = 1, outgoing = false, intro = intro,
        )
        val back = json.decodeFromString(ChatMessage.serializer(), json.encodeToString(ChatMessage.serializer(), m))
        assertEquals(intro, back.intro)
    }

    @Test
    fun olderStoredMessagesHaveNoIntro() {
        val old = """{"localId":"l1","conversation":{"type":"direct","peer":"d:anna"},"fromNodeNum":0,"text":"alt","timestampMillis":1,"outgoing":false}"""
        assertNull(json.decodeFromString(ChatMessage.serializer(), old).intro)
    }
}
