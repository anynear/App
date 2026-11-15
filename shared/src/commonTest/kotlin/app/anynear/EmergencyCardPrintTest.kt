package app.anynear

import app.anynear.ui.emergencyCardHtml
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class EmergencyCardPrintTest {
    @Test
    fun pageHoldsCodeQrAndTextEscaped() {
        val html = emergencyCardHtml("K7QM-2XRA-0000-1111-2222-3333-4*Z", byteArrayOf(1, 2, 3), "anynear <Notfallkarte>", "Text & mehr", "Erstellt am 15.11.2026")
        assertContains(html, "K7QM-2XRA-0000-1111-2222-3333-4*Z")
        assertContains(html, "data:image/png;base64,AQID")
        assertContains(html, "anynear &lt;Notfallkarte&gt;")
        assertContains(html, "Text &amp; mehr")
        assertContains(html, "Erstellt am 15.11.2026")
        assertFalse(html.contains("<script"))
    }

    @Test
    fun withoutQrThereIsNoImage() {
        assertFalse(emergencyCardHtml("A", null, "t", "x", "d").contains("<img"))
    }
}
