package app.anynear

import app.anynear.i18n.Language
import app.anynear.i18n.Strings
import app.anynear.i18n.currentLanguage
import app.anynear.i18n.formatLocaleTag
import app.anynear.i18n.localeTagOverride
import app.anynear.i18n.regionOf
import app.anynear.i18n.texts
import app.anynear.ui.formatChatListTime
import app.anynear.ui.formatDateTime
import app.anynear.ui.formatDayAndTime
import app.anynear.ui.formatFutureDayAndTime
import app.anynear.ui.formatShortDate
import app.anynear.ui.formatTime
import java.lang.reflect.Method
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Language choice, completeness of both catalogs and date formats per language (ADR 0026). */
class LanguageTest {
    @AfterTest
    fun reset() {
        localeTagOverride = null
    }

    @Test
    fun germanForEveryGermanLocaleEnglishForAllOthers() {
        for (tag in listOf("de", "de-DE", "de-AT", "de_CH", "DE-de")) assertEquals(Language.GERMAN, Language.of(tag), tag)
        for (tag in listOf("en", "en-US", "en-GB", "fr-FR", "tr-TR", "dk", "")) assertEquals(Language.ENGLISH, Language.of(tag), tag)
    }

    @Test
    fun textsFollowTheLanguageOfThePhone() {
        localeTagOverride = "de-AT"
        assertEquals(Language.GERMAN, currentLanguage())
        assertSame(Strings.german, texts)
        assertEquals("Abbrechen", texts.common.cancel)
        localeTagOverride = "fr-FR"
        assertSame(Strings.english, texts)
        assertEquals("Cancel", texts.common.cancel)
    }

    @Test
    fun regionForDateFormats() {
        assertEquals("US", regionOf("en-US"))
        assertEquals("GB", regionOf("en_GB"))
        assertEquals("TW", regionOf("zh-Hant-TW"))
        assertEquals("419", regionOf("es-419"))
        assertEquals("", regionOf("en"))
        localeTagOverride = "de-CH"
        assertEquals("de-DE", formatLocaleTag())
        localeTagOverride = "en-GB"
        assertEquals("en-GB", formatLocaleTag())
        localeTagOverride = "fr-FR"
        assertEquals("en-FR", formatLocaleTag())
        localeTagOverride = "en"
        assertEquals("en-US", formatLocaleTag())
    }

    @Test
    fun bothCatalogsAreCompleteAndNotEmpty() {
        val de = allTexts(Strings.german)
        val en = allTexts(Strings.english)
        assertEquals(de.keys - OPTIONAL, en.keys - OPTIONAL)
        assertTrue(de.size > 100, "zu wenige Texte gefunden: ${de.size}")
        for ((key, text) in de) assertTrue(text.isNotBlank(), "leerer deutscher Text: $key")
        for ((key, text) in en) assertTrue(text.isNotBlank(), "leerer englischer Text: $key")
    }

    @Test
    fun englishCatalogHasNoGerman() {
        val german = Regex("[äöüÄÖÜß]|\\b(und|oder|nicht|der|die|das|ist|mit|ein|eine|dein|deine|Kreis|Funkgerät|Nachricht|Bitte|noch)\\b")
        val found = allTexts(Strings.english).filterValues { german.containsMatchIn(it) }
        assertTrue(found.isEmpty(), "Deutsch im englischen Katalog: $found")
    }

    @Test
    fun englishDiffersFromGerman() {
        val de = allTexts(Strings.german)
        val same = allTexts(Strings.english).filter { (key, text) -> de[key] == text && text.any { it.isLetter() } }
        // Words that are the same in both languages (OK, Internet, anynear …) are allowed, but rare.
        assertTrue(same.size < de.size / 10, "auffällig viele unübersetzte Texte: ${same.keys}")
    }

    @Test
    fun germanFormats() {
        localeTagOverride = "de-DE"
        val at = millis(2026, 10, 19, 14, 5)
        assertEquals("14:05", formatTime(at))
        assertEquals("19.10.2026, 14:05", formatDateTime(at))
        assertEquals("19.10.26", formatShortDate(at))
        assertEquals("14:05", formatChatListTime(at, at + HOUR))
        assertEquals("Gestern", formatChatListTime(at, at + DAY))
        assertEquals("Montag", formatChatListTime(at, at + 3 * DAY))
        assertEquals("19.10.26", formatChatListTime(at, at + 10 * DAY))
        assertEquals("heute, 14:05", formatDayAndTime(at, at + HOUR))
        assertEquals("gestern, 14:05", formatDayAndTime(at, at + DAY))
        assertEquals("Montag, 14:05", formatDayAndTime(at, at + 3 * DAY))
        assertEquals("19.10.2026, 14:05", formatDayAndTime(at, at + 10 * DAY))
        assertEquals("heute, 14:05", formatFutureDayAndTime(at, at - HOUR))
        assertEquals("morgen, 14:05", formatFutureDayAndTime(at, at - DAY))
        assertEquals("19.10.2026, 14:05", formatFutureDayAndTime(at, at - 10 * DAY))
    }

    @Test
    fun englishFormatsFollowTheRegion() {
        val at = millis(2026, 10, 19, 14, 5)
        localeTagOverride = "en-US"
        assertEquals("2:05 PM", formatTime(at).spaces())
        assertEquals("Oct 19, 2026, 2:05 PM", formatDateTime(at).spaces())
        assertEquals("10/19/26", formatShortDate(at))
        assertEquals("Yesterday", formatChatListTime(at, at + DAY))
        assertEquals("Monday", formatChatListTime(at, at + 3 * DAY))
        assertEquals("today, 2:05 PM", formatDayAndTime(at, at + HOUR).spaces())
        assertEquals("Monday, 2:05 PM", formatDayAndTime(at, at + 3 * DAY).spaces())
        localeTagOverride = "en-GB"
        assertEquals("14:05", formatTime(at))
        assertEquals("19 Oct 2026, 14:05", formatDateTime(at).spaces())
        assertEquals("19/10/2026", formatShortDate(at))
        assertNotEquals(formatDateTime(at), run { localeTagOverride = "de-DE"; formatDateTime(at) })
    }

    private fun String.spaces() = replace(' ', ' ').replace(' ', ' ')

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * All texts of a catalog via reflection: properties directly, functions with sample values
     * (text „Anna“, number 1 and 3, so that singular and plural both get their turn).
     */
    private fun allTexts(strings: Strings): Map<String, String> {
        val result = sortedMapOf<String, String>()
        for (area in Strings::class.java.methods.filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType.isInterface }) {
            val catalog = area.invoke(strings)
            for (method in area.returnType.methods) {
                if (method.returnType != String::class.java) continue
                for ((suffix, args) in sampleArgs(method)) {
                    // Only explicitly optional texts (OPTIONAL) may be missing in a language.
                    val key = "${area.name.removePrefix("get")}.${method.name}$suffix"
                    val text = method.invoke(catalog, *args) as String?
                    if (text == null) {
                        assertTrue(key in OPTIONAL, "fehlender Text: $key")
                        continue
                    }
                    result[key] = text
                }
            }
        }
        return result
    }

    private fun sampleArgs(method: Method): List<Pair<String, Array<Any?>>> {
        val types = method.parameterTypes
        if (types.isEmpty()) return listOf("" to emptyArray())
        return listOf(1, 3).mapNotNull { n ->
            val args = types.map { type ->
                when (type) {
                    String::class.java -> "Anna"
                    Int::class.javaPrimitiveType, Int::class.javaObjectType -> n
                    Long::class.javaPrimitiveType, Long::class.javaObjectType -> n.toLong()
                    Boolean::class.javaPrimitiveType -> n == 1
                    List::class.java -> listOf("Anna", "Ben", "Clara").take(n)
                    else -> enumSample(type) ?: return@mapNotNull null
                }
            }
            "($n)" to args.toTypedArray<Any?>()
        }
    }

    /** For enum parameters (e.g. `problem(…)`) the first value. */
    private fun enumSample(type: Class<*>): Any? = type.enumConstants?.firstOrNull()

    private companion object {
        /** Texts that need not exist in every language: the tagline, until a wording is approved. */
        val OPTIONAL = setOf("Setup.getTagline")
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
