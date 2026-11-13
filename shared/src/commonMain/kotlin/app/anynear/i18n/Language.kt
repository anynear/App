package app.anynear.i18n

/** Languages of the app. German for every `de*`, English for all other languages (ADR 0026). */
enum class Language {
    GERMAN,
    ENGLISH,
    ;

    companion object {
        /** Language for a language tag such as `de-AT`, `de_DE` or `fr-FR`. */
        fun of(tag: String): Language = if (tag.lowercase().startsWith("de")) GERMAN else ENGLISH
    }
}

/** Language tag of the phone, e.g. `de-DE` or `en-US`. */
internal expect fun systemLocaleTag(): String

/** Fixed language tag instead of the phone's, only for tests. */
internal var localeTagOverride: String? = null

internal fun localeTag(): String = localeTagOverride ?: systemLocaleTag()

/** Language of the UI, as the phone currently specifies it. */
fun currentLanguage(): Language = Language.of(localeTag())

/** Country code from a language tag, e.g. `US` from `en-US` or `TW` from `zh-Hant-TW`; empty without a country. */
internal fun regionOf(tag: String): String =
    tag.split('-', '_').drop(1).firstOrNull { part -> (part.length == 2 && part.all { it.isLetter() }) || (part.length == 3 && part.all { it.isDigit() }) }
        ?.uppercase().orEmpty()

/**
 * Language tag for date and time: in German always `de-DE` (as before "19.10.2026, 14:05"), in
 * English, English with the country of the phone, e.g. `en-US` ("2:05 PM") or `en-GB` ("14:05").
 */
internal fun formatLocaleTag(): String = when (currentLanguage()) {
    Language.GERMAN -> "de-DE"
    Language.ENGLISH -> "en-" + regionOf(localeTag()).ifEmpty { "US" }
}
