package app.anynear

import app.anynear.chats.LinkPreviews
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Preview for links (ADR 0069): what is loaded and what is read from the page. */
class LinkPreviewsTest {
    @Test
    fun onlyHttpsLinksOnTheInternetAndNotOurs() {
        assertEquals("https://example.org/a", LinkPreviews.firstPreviewable("Schau https://example.org/a und https://example.com/b"))
        assertNull(LinkPreviews.firstPreviewable("http://example.org/a"))
        assertNull(LinkPreviews.firstPreviewable("https://anynear.app/k#abc"))
        assertNull(LinkPreviews.firstPreviewable("https://anynear.app/hilfe"))
        assertEquals("https://example.org/x", LinkPreviews.firstPreviewable("https://anynear.app/c#abc und https://example.org/x"))
        assertFalse(LinkPreviews.isAllowed("https://localhost/a"))
        assertFalse(LinkPreviews.isAllowed("https://192.168.1.10/a"))
        assertFalse(LinkPreviews.isAllowed("https://[::1]/a"))
        assertFalse(LinkPreviews.isAllowed("https://drucker.local/a"))
        assertFalse(LinkPreviews.isAllowed("https://router/"))
        assertTrue(LinkPreviews.isAllowed("https://www.example.org/a?b=c"))
        assertEquals("example.org", LinkPreviews.hostOf("https://www.Example.org:443/a"))
    }

    @Test
    fun readsOpenGraphFirst() {
        val html = """
            <html><head><title>Fallback</title>
            <meta property="og:title" content="Ein &amp; Titel">
            <meta name='description' content='Beschreibung'>
            <meta property="og:site_name" content="Beispiel">
            <meta content="/bild.jpg" property="og:image">
            </head></html>
        """.trimIndent()
        val info = LinkPreviews.parse(html, "https://example.org/artikel/1")!!
        assertEquals("Ein & Titel", info.title)
        assertEquals("Beschreibung", info.description)
        assertEquals("Beispiel", info.site)
        assertEquals("https://example.org/bild.jpg", info.image)
    }

    @Test
    fun fallsBackToTitleAndDropsInsecureImages() {
        val info = LinkPreviews.parse("<title>  Nur\n Titel </title><meta property=\"og:image\" content=\"http://example.org/b.jpg\">", "https://example.org/")!!
        assertEquals("Nur Titel", info.title)
        assertNull(info.image)
        assertNull(LinkPreviews.parse("<html><body>leer</body></html>", "https://example.org/"))
        assertEquals(LinkPreviews.TITLE_MAX, LinkPreviews.parse("<title>${"x".repeat(500)}</title>", "https://example.org/")!!.title!!.length)
    }
}
