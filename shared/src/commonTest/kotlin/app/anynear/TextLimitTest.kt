package app.anynear

import app.anynear.ui.TextLimit
import app.anynear.ui.charsLeftHint
import app.anynear.ui.limitInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextLimitTest {
    private val max5 = TextLimit.chars(5)

    @Test
    fun typingStopsAtTheLimit() {
        assertEquals("abcde", max5.accept("abcde", "abcdef"))
        assertEquals("abcd", max5.accept("abc", "abcd"))
    }

    @Test
    fun pasteKeepsOnlyWhatFits() {
        assertEquals("abcde", max5.accept("ab", "abcdefghij"))
        // Into the middle: start and end stay, the inserted part is shortened.
        assertEquals("aXYZb", max5.accept("ab", "aXYZWVb"))
    }

    @Test
    fun deletingIsAlwaysAllowed() {
        assertEquals("abcdef", max5.accept("abcdefg", "abcdef"))
        assertEquals("", max5.accept("abcdefg", ""))
    }

    @Test
    fun emojisAreNotCutInHalf() {
        val bytes = TextLimit.bytes(6)
        // „😀“ has 4 bytes; after „ab“ it fits exactly, a second one does not.
        assertEquals("ab😀", bytes.accept("ab", "ab😀😀"))
        assertEquals("abc", bytes.accept("abc", "abc😀"))
    }

    @Test
    fun replacingASelectionWithTooMuchKeepsTheField() {
        // Replaced entirely by something of which nothing fits: then nothing of the change at all.
        assertEquals("abcde", limitInput("abcde", "😀😀😀") { it.encodeToByteArray().size <= 3 })
    }

    @Test
    fun hintOnlyNearTheLimit() {
        assertNull(charsLeftHint(11, 10))
        assertEquals("Noch 10 Zeichen", charsLeftHint(10, 10))
        assertEquals("Noch 0 Zeichen", charsLeftHint(0, 10))
        assertEquals("2 Zeichen zu viel", charsLeftHint(-2, 10))
    }
}
