package app.anynear

import app.anynear.app.ReleaseNotes
import app.anynear.i18n.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReleaseNotesTest {
    private val json = """
        {"version":"0.9.0-pre.10030",
         "en":{"intro":["A calmer version."],"sections":[{"title":"New","items":["Bin: files stay for 7 days."]},{"title":"Fixed","items":[]}]},
         "de":{"intro":[],"sections":[{"title":"Neu","items":["Papierkorb: Dateien bleiben 7 Tage.","<b>fett</b>"]},{"title":"Behoben","items":["Seltener leere Benachrichtigungen."]}],"extra":1}}
    """.trimIndent()

    @Test
    fun picksTheLanguageOfTheApp() {
        val de = ReleaseNotes.parse(json, Language.GERMAN)!!
        assertEquals("0.9.0-pre.10030", de.version)
        assertEquals(listOf("Neu", "Behoben"), de.sections.map { it.title })
        // Text stays text.
        assertEquals("<b>fett</b>", de.sections[0].items[1])
        val en = ReleaseNotes.parse(json, Language.ENGLISH)!!
        assertEquals(listOf("A calmer version."), en.intro)
        // Headings without entries are left out.
        assertEquals(listOf("New"), en.sections.map { it.title })
    }

    @Test
    fun unusableAnswersShowNothing() {
        assertNull(ReleaseNotes.parse("""{"error":"not_found"}""", Language.GERMAN))
        assertNull(ReleaseNotes.parse("""{"version":"../x","de":{"sections":[{"title":"Neu","items":["a"]}]}}""", Language.GERMAN))
        assertNull(ReleaseNotes.parse("""{"version":"1.0.0","de":{"sections":[]}}""", Language.GERMAN))
        assertNull(ReleaseNotes.parse("""{"version":"1.0.0","de":{"sections":[{"title":"Neu","items":["a"]}]}}""", Language.ENGLISH))
        assertNull(ReleaseNotes.parse("not json", Language.GERMAN))
    }
}
