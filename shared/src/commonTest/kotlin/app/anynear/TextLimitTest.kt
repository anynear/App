package app.anynear

import app.anynear.chats.codePointCount
import app.anynear.ui.TextLimit
import app.anynear.ui.charsLeftHint
import app.anynear.ui.limitInput
import app.anynear.ui.textHintBelow
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

    @Test
    fun charactersAreCountedAsCodePoints() {
        assertEquals(3, "abc".codePointCount())
        // Umlauts are one character each (2 bytes in UTF-8), an emoji one character (4 bytes, a surrogate pair).
        assertEquals(3, "äöü".codePointCount())
        assertEquals(2, "a😀".codePointCount())
        // Sequences count each code point, as `[...text].length` in the web app: family 5, flag 2.
        assertEquals(5, "👨‍👩‍👧".codePointCount())
        assertEquals(2, "🇩🇪".codePointCount())
    }

    @Test
    fun onlineLimitAllows4096UmlautsOrEmojis() {
        // As in the chat with an internet path: 4096 characters, not bytes.
        val online = TextLimit({ 4096 - it.trim().codePointCount() }, textHintBelow(4096))
        val umlauts = "ä".repeat(4096)
        assertEquals(umlauts, online.accept("", umlauts + "ä"))
        val emojis = "😀".repeat(4096)
        assertEquals(emojis, online.accept(emojis.dropLast(2), emojis + "😀"))
        assertEquals(0, online.left(emojis))
        assertEquals(16384, emojis.encodeToByteArray().size)
        // The hint comes 100 characters before the end, over radio (short limit) 30 before.
        assertNull(online.hint("a".repeat(3995)))
        assertEquals("Noch 100 Zeichen", online.hint("a".repeat(3996)))
        assertEquals(30, textHintBelow(200))
    }
}
