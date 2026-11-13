package app.anynear

import app.anynear.ui.shortFileName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileNameTest {
    @Test
    fun shortNamesStayAsTheyAre() {
        assertEquals("Treffpunkt.pdf", shortFileName("Treffpunkt.pdf"))
    }

    @Test
    fun longNamesKeepTheExtension() {
        val short = shortFileName("Hausapotheke und Vorräte im Keller.pdf")
        assertTrue(short.endsWith("….pdf"), short)
        assertTrue(short.length <= 26, short)
        assertTrue(short.startsWith("Hausapotheke"), short)
    }
}
