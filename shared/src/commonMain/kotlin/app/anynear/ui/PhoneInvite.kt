package app.anynear.ui

import androidx.compose.runtime.Composable

/**
 * Invite someone from the phone's own contacts (ADR 0003): anynear has no directory of phone numbers. The person picks
 * someone in the system's contact picker, the app opens a text message with the own contact link, and that is all.
 * The number is only handed to the SMS app; it is never stored and never sent to relays.
 */
internal data class PickedPhoneContact(val phones: List<String>)

/**
 * Opens the system's contact picker (Android: picker for a phone number without READ_CONTACTS; iOS:
 * CNContactPickerViewController without access to the address book). [onPicked] gets the person's numbers, an empty
 * list if the system has no picker, or `null` if the person canceled.
 */
@Composable
internal expect fun rememberPhoneContactPicker(onPicked: (PickedPhoneContact?) -> Unit): () -> Unit

/** Opens the SMS app with [number] and [text] filled in; `false` if the device cannot send text messages. */
internal expect fun openSms(number: String, text: String): Boolean

/** The number to text: the first one with enough digits, without spaces, dashes or brackets. */
internal fun smsNumber(contact: PickedPhoneContact): String? =
    contact.phones.asSequence().map(::cleanPhoneNumber).firstOrNull { number -> number.count { it.isDigit() } >= 3 }

/** Digits only, with a leading „+“ kept: „+49 (171) 123-45“ → „+4917112345“. */
internal fun cleanPhoneNumber(raw: String): String {
    val trimmed = raw.trim()
    val digits = trimmed.filter { it.isDigit() }
    return if (trimmed.startsWith("+")) "+$digits" else digits
}
