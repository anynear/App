package app.anynear.chats

/**
 * Longest beginning of at most [n] UTF-16 units without splitting a character: no half
 * surrogate pair, no separated emoji sequence (joiner, skin tone, variant), no half flag,
 * no accent without its letter. Applies to input fields and to the text preview (ADR 0011, 0030).
 */
fun String.safePrefix(n: Int): String {
    if (n >= length) return this
    var best = 0
    var i = 0
    var previous = -1
    var regional = 0
    while (i < length && i <= n) {
        val cp = codePointAt(i)
        val isRegional = cp in 0x1F1E6..0x1F1FF
        if (i > 0 && !extends(cp) && previous != ZWJ && !(isRegional && regional % 2 == 1)) best = i
        regional = if (isRegional) regional + 1 else 0
        previous = cp
        i += if (cp > 0xFFFF) 2 else 1
    }
    return take(best)
}

/** Longest beginning of [text] for which [fits] holds, at safe positions ([safePrefix]); empty if nothing fits. */
fun longestFitting(text: String, fits: (String) -> Boolean): String {
    if (fits(text)) return text
    var low = 0
    var high = text.length
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (fits(text.safePrefix(mid))) low = mid else high = mid - 1
    }
    return text.safePrefix(low)
}

/**
 * Number of characters as Unicode code points: a surrogate pair (most emojis) counts once. Emoji sequences
 * with joiners or skin tones and flags count each of their code points. The web app counts the same way
 * (`[...text].length`), so a limit in characters means the same in both; at most 4 bytes per character in UTF-8.
 */
fun String.codePointCount(): Int {
    var n = 0
    var i = 0
    while (i < length) {
        i += if (this[i].isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) 2 else 1
        n++
    }
    return n
}

private fun extends(cp: Int): Boolean =
    cp == ZWJ ||
        cp in 0x0300..0x036F || cp in 0x1AB0..0x1AFF || cp in 0x1DC0..0x1DFF || cp in 0x20D0..0x20FF ||
        cp in 0xFE00..0xFE0F || cp in 0xFE20..0xFE2F || cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F

private fun String.codePointAt(i: Int): Int {
    val c = this[i]
    if (c.isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) {
        return 0x10000 + ((c.code - 0xD800) shl 10) + (this[i + 1].code - 0xDC00)
    }
    return c.code
}

private const val ZWJ = 0x200D
