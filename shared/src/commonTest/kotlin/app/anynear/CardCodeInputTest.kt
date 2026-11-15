package app.anynear

import app.anynear.app.CardCodeInput
import app.anynear.app.CardCodeInput.Value
import kotlin.test.Test
import kotlin.test.assertEquals

class CardCodeInputTest {
    /** Types [chars] one by one at the end, as the keyboard does. */
    private fun type(chars: String, start: String = ""): Value =
        chars.fold(Value(start, start.length)) { v, c ->
            val t = v.text.substring(0, v.cursor) + c + v.text.substring(v.cursor)
            CardCodeInput.format(v.text, t, v.cursor + 1)
        }

    private fun backspace(v: Value): Value =
        CardCodeInput.format(v.text, v.text.removeRange(v.cursor - 1, v.cursor), v.cursor - 1)

    @Test
    fun lowerCaseBecomesUpperCase() {
        assertEquals(Value("K7Q", 3), type("k7q"))
    }

    @Test
    fun dashComesAfterFourCharacters() {
        assertEquals(Value("K7QM-", 5), type("k7qm"))
        assertEquals(Value("K7QM-2X", 7), type("k7qm2x"))
        assertEquals(Value("K7QM-2XRA-", 10), type("k7qm2xra"))
    }

    @Test
    fun typedDashesAndSpacesAreIgnored() {
        assertEquals(Value("K7QM-2X", 7), type("k7qm-2x"))
        assertEquals(Value("K7QM-2X", 7), type("k7qm 2x"))
    }

    @Test
    fun fullCodeHasNoTrailingDashAndStopsAtItsLength() {
        val code = "K7QM2XRA00001111222233334*"
        val v = type(code + "ZZZ")
        assertEquals("K7QM-2XRA-0000-1111-2222-3333-4*Z", v.text)
        assertEquals(CardCodeInput.LENGTH, v.text.count { it != '-' })
        assertEquals(v.text.length, v.cursor)
    }

    @Test
    fun checkCharacterSymbolsAreKept() {
        assertEquals(Value("ABCD-*~\$=-", 10), type("abcd*~\$="))
    }

    @Test
    fun pastingAWholeCodeFormatsIt() {
        val pasted = "k7qm 2xra-0000 1111 2222 3333 4*z"
        assertEquals(Value("K7QM-2XRA-0000-1111-2222-3333-4*Z", 33), CardCodeInput.format("", pasted, pasted.length))
    }

    @Test
    fun backspaceOverTheDashDeletesTheCharacterBeforeIt() {
        val v = type("k7qm")
        assertEquals(Value("K7Q", 3), backspace(v))
        assertEquals(Value("K7QM-", 5), backspace(type("k7qm2")))
    }

    @Test
    fun backspaceOverADashInTheMiddleKeepsTheRest() {
        // Cursor right after the first dash of "K7QM-2XRA".
        assertEquals(Value("K7Q2-XRA", 3), backspace(Value("K7QM-2XRA", 5)))
    }

    @Test
    fun typingInTheMiddleKeepsTheCursorBehindTheNewCharacter() {
        // "K7QM-2XRA", a "B" typed after "K7".
        val v = CardCodeInput.format("K7QM-2XRA", "K7BQM-2XRA", 3)
        assertEquals(Value("K7BQ-M2XR-A", 3), v)
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals(Value("", 0), CardCodeInput.format("A", "", 0))
    }
}
