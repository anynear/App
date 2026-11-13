package app.anynear

import app.anynear.attachments.VoicePreview
import app.anynear.attachments.VoiceTranscript
import app.anynear.ui.limitInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Text preview of a voice message (ADR 0030): shortening to the radio budget without broken characters. */
class VoicePreviewTest {
    private fun bytes(s: String) = s.encodeToByteArray().size

    /** As in the input field: fits as long as the budget in bytes suffices. */
    private fun cut(text: String, max: Int, complete: Boolean = true) = VoicePreview.cut(VoiceTranscript(text, complete)) { bytes(it) <= max }

    @Test
    fun shortTextStaysAsItIs() {
        assertEquals("Bin gleich da", cut("Bin gleich da", 89))
        assertEquals("Bin gleich da", cut("  Bin\n gleich   da ", 89))
    }

    @Test
    fun longTextIsCutAtAWordWithEllipsis() {
        val text = "Hallo zusammen, ich bin gut angekommen und melde mich später noch einmal ausführlich bei euch allen"
        val preview = cut(text, 40)!!
        assertTrue(bytes(preview) <= 40, preview)
        assertTrue(preview.endsWith("…"))
        assertTrue(text.startsWith(preview.removeSuffix("…")))
        // Ends at a whole word, without comma or space before „…“.
        assertEquals("Hallo zusammen, ich bin gut…", preview)
    }

    @Test
    fun incompleteRecognitionIsMarkedEvenIfShort() {
        // Only the beginning recognised: even a short text counts as shortened.
        assertEquals("Bin gleich da…", cut("Bin gleich da", 89, complete = false))
        assertTrue(VoicePreview.isShortened("Bin gleich da…"))
    }

    @Test
    fun longWordIsCutAtACharacter() {
        val preview = cut("Donaudampfschifffahrtsgesellschaftskapitänsmütze", 20)!!
        assertTrue(bytes(preview) <= 20)
        assertEquals("Donaudampfschifff…", preview)
    }

    @Test
    fun umlautsAreNeverSplit() {
        // „ä“ has 2 bytes; at every limit valid UTF-8 remains and the text is a beginning of the original.
        val text = "äääääääääääääääääääää"
        for (max in 8..30) {
            val preview = cut(text, max) ?: continue
            assertTrue(bytes(preview) <= max)
            assertTrue(text.startsWith(preview.removeSuffix("…")), "max=$max: $preview")
        }
    }

    @Test
    fun emojiSequencesStayWhole() {
        val family = "👨‍👩‍👧"
        val flag = "🇩🇪"
        val skin = "👍🏽"
        val accent = "é" // e with combining acute
        val text = "Gruß $family $flag $skin $accent $family $flag $skin $accent und mehr Text danach"
        for (max in 8..bytes(text)) {
            val preview = cut(text, max) ?: continue
            val head = preview.removeSuffix("…")
            assertTrue(bytes(preview) <= max, "max=$max")
            assertTrue(text.startsWith(head), "max=$max")
            // Never in the middle of a sequence: the rest does not begin with an appended character.
            val rest = text.substring(head.length)
            if (rest.isNotEmpty()) {
                val next = rest[0]
                assertTrue(!next.isLowSurrogate() && next != '‍' && next != '́', "max=$max: $preview")
                assertTrue(!rest.startsWith("🏽") && !rest.startsWith("🇪"), "max=$max: $preview")
            }
            assertTrue(!head.endsWith("‍"), "max=$max")
        }
    }

    @Test
    fun inputFieldUsesTheSameSafeCut() {
        // Inserting into a full field: the family stays whole or is dropped whole (as in the preview).
        val family = "👨‍👩‍👧"
        val result = limitInput("Hi ", "Hi $family$family") { bytes(it) <= 3 + bytes(family) + 5 }
        assertEquals("Hi $family", result)
    }

    @Test
    fun nothingUsefulGivesNoPreview() {
        assertNull(cut("", 89))
        assertNull(cut("   ", 89))
        assertNull(cut("Hallo", 3))
    }
}
