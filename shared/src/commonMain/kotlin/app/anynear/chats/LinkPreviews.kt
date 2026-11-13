package app.anynear.chats

import app.anynear.attachments.DraftAttachment

/**
 * Preview of a web link while writing (ADR 0069), already loaded: title, description, site and a small photo
 * (shrunk, without image data). Goes with the message only over the internet.
 */
class LinkDraft(
    val url: String,
    val title: String?,
    val description: String?,
    val site: String?,
    val image: DraftAttachment?,
)

/** What is read from a page before the image is loaded. */
data class PageInfo(val title: String?, val description: String?, val site: String?, val image: String?)

object LinkPreviews {
    const val TITLE_MAX = 120
    const val DESCRIPTION_MAX = 200

    /** At most this much HTML is read. */
    const val MAX_HTML_BYTES = 512 * 1024

    /** The image may be at most this large before it is shrunk. */
    const val MAX_IMAGE_BYTES = 2 * 1024 * 1024

    /** Longest edge of the preview image in pixels. */
    const val IMAGE_MAX_EDGE = 600

    const val MAX_REDIRECTS = 3

    /**
     * The first web link in [text] that gets a preview: only `https`, no anynear links (they have their card)
     * and no addresses in the own network.
     */
    fun firstPreviewable(text: String): String? {
        val invites = findInviteLinks(text).map { it.url }.toSet()
        return findLinks(text).map { it.url }.firstOrNull { it !in invites && isAllowed(it) && !isOurs(it) }
    }

    /** Only `https` to a name on the internet: no IP addresses, no `localhost`, no names in the local network. */
    fun isAllowed(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val host = hostOf(url) ?: return false
        if (host == "localhost" || !host.contains('.')) return false
        if (LOCAL_SUFFIXES.any { host.endsWith(it) }) return false
        // IPv4, IPv6 in brackets or only digits: no preview.
        if (host.startsWith("[") || host.all { it.isDigit() || it == '.' }) return false
        return true
    }

    /** Name of the site from the address, without `www.`; the card always shows it so that nobody fakes a foreign site. */
    fun hostOf(url: String): String? = lookupHostOf(url)?.removePrefix("www.")?.takeIf { it.isNotEmpty() }

    /** Name of the host from the address, the way it is resolved (with `www.`). */
    fun lookupHostOf(url: String): String? {
        val rest = url.substringAfter("://", "").takeIf { it.isNotEmpty() } ?: return null
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').let { if (it.startsWith("[")) it.substringBefore(']') + "]" else it.substringBefore(':') }
        return host.lowercase().takeIf { it.isNotEmpty() }
    }

    private fun isOurs(url: String): Boolean = hostOf(url)?.let { it == "anynear.app" || it == "anynear.net" || it.endsWith(".anynear.app") || it.endsWith(".anynear.net") } == true

    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".internal", ".home", ".localhost", ".arpa")

    /**
     * Reads title, description, site and image from [html]: first `og:` data, otherwise `<title>` and
     * `<meta name="description">`. Relative image addresses are resolved with [url]. `null` without title and description.
     */
    fun parse(html: String, url: String): PageInfo? {
        val metas = META.findAll(html).mapNotNull { m ->
            val attrs = ATTR.findAll(m.value).associate { a -> a.groupValues[1].lowercase() to decode(a.groupValues[2].ifEmpty { a.groupValues[3] }.ifEmpty { a.groupValues[4] }) }
            val key = (attrs["property"] ?: attrs["name"])?.lowercase() ?: return@mapNotNull null
            attrs["content"]?.let { key to it }
        }.toList()
        fun meta(vararg keys: String) = keys.firstNotNullOfOrNull { k -> metas.firstOrNull { it.first == k }?.second?.trim()?.takeIf(String::isNotEmpty) }
        val title = meta("og:title", "twitter:title") ?: TITLE.find(html)?.groupValues?.get(1)?.let(::decode)?.trim()?.takeIf(String::isNotEmpty)
        val description = meta("og:description", "twitter:description", "description")
        if (title == null && description == null) return null
        val image = meta("og:image", "og:image:url", "twitter:image")?.let { resolve(url, it) }?.takeIf(::isAllowed)
        return PageInfo(
            title?.let(::clean)?.take(TITLE_MAX),
            description?.let(::clean)?.take(DESCRIPTION_MAX),
            meta("og:site_name")?.let(::clean)?.take(TITLE_MAX),
            image,
        )
    }

    /** Collapse whitespace. */
    private fun clean(s: String) = s.replace(SPACES, " ").trim()

    private fun resolve(base: String, ref: String): String = when {
        ref.startsWith("https://") || ref.startsWith("http://") -> ref
        ref.startsWith("//") -> "https:$ref"
        ref.startsWith("/") -> base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore('/') + ref
        else -> base.substringBeforeLast('/') + "/" + ref
    }

    private fun decode(s: String): String = ENTITY.replace(s) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos", "#39" -> "'"
            "nbsp" -> " "
            else -> when {
                e.startsWith("#x", true) -> e.drop(2).toIntOrNull(16)?.let(::codePoint) ?: m.value
                e.startsWith("#") -> e.drop(1).toIntOrNull()?.let(::codePoint) ?: m.value
                else -> m.value
            }
        }
    }

    private fun codePoint(cp: Int): String? = when {
        cp in 0..0xFFFF -> cp.toChar().toString()
        cp in 0x10000..0x10FFFF -> (cp - 0x10000).let { charArrayOf((0xD800 + (it shr 10)).toChar(), (0xDC00 + (it and 0x3FF)).toChar()).concatToString() }
        else -> null
    }

    private val META = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("""([a-zA-Z:_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>"']+))""")
    private val TITLE = Regex("""<title[^>]*>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE)
    private val ENTITY = Regex("""&(#?[a-zA-Z0-9]+);""")
    private val SPACES = Regex("""\s+""")
}
