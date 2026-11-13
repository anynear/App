package app.anynear.attachments

import app.anynear.chats.longestFitting

/**
 * Result of the local speech recognition of a recording (ADR 0030). [complete] is `false` if only
 * the beginning was recognised; then even a short preview is "truncated".
 */
class VoiceTranscript(val text: String, val complete: Boolean)

/**
 * Text preview of a voice message for the radio path (ADR 0030): the beginning of the recognised text, never
 * a summary. The limit is the same as in the input field ([fits], measured against the radio packet with
 * announcement); it is truncated at a word boundary, with "…" at the end.
 */
object VoicePreview {
    const val ELLIPSIS = "…"

    /** This much of the recording is recognised; for the few words over radio the beginning is enough. */
    const val RECOGNIZE_MILLIS: Long = 30_000

    /** Preview from [transcript]; `null` if nothing readable fits. Whitespace becomes a single space. */
    fun cut(transcript: VoiceTranscript, fits: (String) -> Boolean): String? {
        val text = transcript.text.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
        if (text.isEmpty()) return null
        if (transcript.complete && fits(text)) return text
        var head = longestFitting(text) { fits(it + ELLIPSIS) }
        // Preferably at a word boundary, as long as no more than half is lost in the process.
        if (head.length < text.length && text[head.length] != ' ') {
            val space = head.lastIndexOf(' ')
            if (space * 2 >= head.length) head = head.take(space)
        }
        head = head.trimEnd(' ', ',', ';', ':', '-', '–')
        return if (head.isEmpty()) null else head + ELLIPSIS
    }

    /** Is [text] a truncated preview? */
    fun isShortened(text: String): Boolean = text.endsWith(ELLIPSIS)

    private val WHITESPACE = Regex("\\s+")
}
