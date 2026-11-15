package app.anynear.app

/**
 * Typing the code of the emergency card (FUT 14.11.2026): capital letters and the dash after every four characters
 * come by themselves, so one never has to switch the keyboard. The code itself is ANMP `RecoveryCode`: 27 characters
 * in groups of four, the last one may also be one of `* ~ $ =`.
 */
object CardCodeInput {
    const val LENGTH = 27
    private const val GROUP = 4

    /** Text and cursor position of the field. */
    data class Value(val text: String, val cursor: Int)

    /**
     * Turns what the field holds after a key press ([text], cursor at [cursor]) into the formatted code. [old] is the
     * text before the key press: if only a dash was deleted, the character in front of it goes too, otherwise the
     * backspace key would seem to do nothing.
     */
    fun format(old: String, text: String, cursor: Int): Value {
        val at = cursor.coerceIn(0, text.length)
        var before = significant(text.substring(0, at))
        var after = significant(text.substring(at))
        if (text.length < old.length && before + after == significant(old) && before.isNotEmpty()) before = before.dropLast(1)
        val all = (before + after).take(LENGTH)
        val pos = before.length.coerceAtMost(all.length)
        after = all.substring(pos)
        val grouped = group(all)
        // At the end of a full group the dash is already there, so that the next character just follows.
        val trailing = after.isEmpty() && all.isNotEmpty() && all.length < LENGTH && all.length % GROUP == 0
        return Value(if (trailing) "$grouped-" else grouped, if (trailing) grouped.length + 1 else cursorAt(pos))
    }

    private fun significant(s: String): String =
        s.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it in "*~\$=" }

    private fun group(s: String): String = s.chunked(GROUP).joinToString("-")

    /** Position in the grouped text right after the first [n] characters. */
    private fun cursorAt(n: Int): Int = if (n == 0) 0 else n + (n - 1) / GROUP
}
