package app.anynear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.anynear.chats.safePrefix
import app.anynear.circles.ProfileRepository
import app.anynear.i18n.texts
import app.anynear.mesh.DeviceName

/**
 * Limit of an input field, everywhere by the same principle: more than allowed cannot be
 * entered, and shortly before the limit it says „Noch X Zeichen“ in red. [left] says how much is still
 * free for a text (characters or, where it is about the radio packet, bytes).
 */
internal class TextLimit(val left: (String) -> Int, val hintBelow: Int = NAME_HINT_BELOW) {
    /** New field content after an input: of the newly inserted only what still fits remains. */
    fun accept(old: String, new: String): String = limitInput(old, new) { left(it) >= 0 }

    /** Hint on [text] or `null` as long as enough is free. */
    fun hint(text: String): String? = charsLeftHint(left(text), hintBelow)

    companion object {
        /** At most [max] characters, counted as when shortening received names (`take`). */
        fun chars(max: Int, hintBelow: Int = NAME_HINT_BELOW) = TextLimit({ max - it.trim().length }, hintBelow)

        /** At most [max] bytes in UTF-8, e.g. for names that go to the radio. */
        fun bytes(max: Int, hintBelow: Int = NAME_HINT_BELOW) = TextLimit({ max - it.trim().encodeToByteArray().size }, hintBelow)
    }
}

/** Name of a circle, as it is shortened on reception. */
internal val CircleNameLimit = TextLimit.chars(app.anynear.anmp.core.AnmpLinks.MAX_NAME)

/** Your own name. */
internal val OwnNameLimit = TextLimit.chars(ProfileRepository.MAX_OWN_NAME)

/** Own part of the radio name (after „anynear.app | “), measured in bytes as in the radio. */
internal val DeviceNameLimit = TextLimit.bytes(DeviceName.SUFFIX_MAX_BYTES)

/** From this few free characters on, a name field shows how much is still possible. */
internal const val NAME_HINT_BELOW = 10

/** „Noch X Zeichen“ shortly before the limit, „X Zeichen zu viel“ above it, otherwise `null`. */
internal fun charsLeftHint(left: Int, hintBelow: Int): String? = when {
    left < 0 -> texts.chat.charsTooMany(-left)
    left <= hintBelow -> texts.chat.charsLeft(left)
    else -> null
}

/**
 * Shortens the change from [old] to [new] so that [fits] holds. Only the newly inserted is shortened
 * (typed or pasted, also in the middle of the text), the rest stays. If [old] itself already does not fit
 * (the limit has become tighter), deleting is always allowed.
 */
internal fun limitInput(old: String, new: String, fits: (String) -> Boolean): String {
    if (fits(new) || new.length <= old.length) return new
    var start = 0
    while (start < old.length && old[start] == new[start]) start++
    var end = 0
    while (end < old.length - start && old[old.length - 1 - end] == new[new.length - 1 - end]) end++
    // Do not cut surrogate pairs (emojis) apart; within the inserted text [safePrefix] ensures whole characters.
    if (start > 0 && new[start - 1].isHighSurrogate()) start--
    if (end > 0 && new[new.length - end].isLowSurrogate()) end--
    val head = new.substring(0, start)
    val inserted = new.substring(start, new.length - end)
    val tail = new.substring(new.length - end)
    // Longest piece of the inserted text that still fits (binary search, also for long pasted text).
    var low = 0
    var high = inserted.length
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (fits(head + inserted.safePrefix(mid) + tail)) low = mid else high = mid - 1
    }
    // If nothing of the inserted text fits, the field stays as it was.
    val kept = inserted.safePrefix(low)
    return if (kept.isEmpty()) old else head + kept + tail
}

/** The hint on a limited field, e.g. as `supportingText`; right-aligned at the edge of the field. */
@Composable
internal fun TextLimitHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.End,
        modifier = modifier.fillMaxWidth(),
    )
}
