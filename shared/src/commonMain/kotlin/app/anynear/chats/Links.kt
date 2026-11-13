package app.anynear.chats

/** A link in the message text: [range] in the text, [url] for opening. */
data class TextLink(val range: IntRange, val url: String)

private val LINK = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"]+""")

/** Punctuation at the end mostly belongs to the sentence, not to the address („Schau auf www.example.org.“). */
private const val TRAILING = ".,;:!?'\"*"

/**
 * Finds web addresses (`http://`, `https://`, `www.`) in [text]. Other schemes (e.g. `javascript:`,
 * `file:`, `intent:`) are deliberately not made clickable.
 */
fun findLinks(text: String): List<TextLink> = LINK.findAll(text).mapNotNull { match ->
    var end = match.range.last
    while (end > match.range.first) {
        val c = text[end]
        val unbalancedParen = c == ')' && text.substring(match.range.first, end + 1).count { it == '(' } < text.substring(match.range.first, end + 1).count { it == ')' }
        if (c in TRAILING || unbalancedParen) end-- else break
    }
    val raw = text.substring(match.range.first, end + 1)
    val url = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
    // Only "www." or "https://" without a rest is not an address.
    if (url.substringAfter("://").trimEnd('/').length < 4 || '.' !in url.substringAfter("://")) return@mapNotNull null
    TextLink(match.range.first..end, url)
}.toList()

/**
 * First invitation link (`https://anynear.app/k#…`) or contact link (`…/c#…`, ADR 0036; also on anynear.net,
 * ADR 0061) in [text], e.g. from a pasted text
 * or a QR code; `null` if there is none in it.
 */
fun findAppLink(text: String): String? =
    findLinks(text).map { it.url }.firstOrNull { APP_LINK.matches(it) }

private val APP_LINK = Regex("""(?i)https://anynear\.(?:app|net)/[kc]/?#\S+""")

/** Link into the settings of the app, `https://anynear.app/s#erase` (ADR 0089); also on anynear.net. */
fun isSettingsLink(url: String): Boolean = SETTINGS_LINK.matches(url)

private val SETTINGS_LINK = Regex("""(?i)https://anynear\.(?:app|net)/s/?#[a-z-]+""")
