package app.anynear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.Contacts.CNContact
import platform.Contacts.CNLabelPhoneNumberMobile
import platform.Contacts.CNLabelPhoneNumberiPhone
import platform.Contacts.CNLabeledValue
import platform.Contacts.CNPhoneNumber
import platform.ContactsUI.CNContactPickerDelegateProtocol
import platform.ContactsUI.CNContactPickerViewController
import platform.MessageUI.MFMessageComposeViewController
import platform.MessageUI.MFMessageComposeViewControllerDelegateProtocol
import platform.MessageUI.MessageComposeResult
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_MSEC
import platform.darwin.NSObject
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/**
 * CNContactPickerViewController runs outside the app: iOS hands over only the chosen person, without asking for
 * access to the address book (no NSContactsUsageDescription needed).
 */
@Composable
internal actual fun rememberPhoneContactPicker(onPicked: (PickedPhoneContact?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val picker = remember { IosPhoneContactPicker { callback.value(it) } }
    return picker::open
}

private class IosPhoneContactPicker(private val deliver: (PickedPhoneContact?) -> Unit) {
    // UIKit holds delegates only weakly.
    private var delegate: ContactDelegate? = null

    fun open() {
        val picker = CNContactPickerViewController()
        val d = ContactDelegate(deliver)
        delegate = d
        picker.delegate = d
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

private class ContactDelegate(private val deliver: (PickedPhoneContact?) -> Unit) : NSObject(), CNContactPickerDelegateProtocol {
    override fun contactPicker(picker: CNContactPickerViewController, didSelectContact: CNContact) {
        // Mobile numbers first: they can receive the text message.
        val numbers = didSelectContact.phoneNumbers.filterIsInstance<CNLabeledValue>()
            .sortedBy { if (it.label == CNLabelPhoneNumberMobile || it.label == CNLabelPhoneNumberiPhone) 0 else 1 }
            .mapNotNull { (it.value as? CNPhoneNumber)?.stringValue }
        // The picker closes itself; the next screen can only be shown once it is gone.
        afterDismiss { deliver(PickedPhoneContact(numbers)) }
    }

    override fun contactPickerDidCancel(picker: CNContactPickerViewController) = deliver(null)
}

private fun afterDismiss(block: () -> Unit) =
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (600uL * NSEC_PER_MSEC).toLong()), dispatch_get_main_queue(), block)

// Held strongly while the message screen is open.
private var smsDelegate: SmsDelegate? = null

internal actual fun openSms(number: String, text: String): Boolean {
    // iPad without SIM, the simulator, or iMessage switched off: the share menu instead.
    if (!MFMessageComposeViewController.canSendText()) return false
    val top = topViewController() ?: return false
    val composer = MFMessageComposeViewController()
    composer.recipients = listOf(number)
    composer.body = text
    val d = SmsDelegate()
    smsDelegate = d
    composer.messageComposeDelegate = d
    top.presentViewController(composer, animated = true, completion = null)
    return true
}

private class SmsDelegate : NSObject(), MFMessageComposeViewControllerDelegateProtocol {
    override fun messageComposeViewController(controller: MFMessageComposeViewController, didFinishWithResult: MessageComposeResult) {
        controller.dismissViewControllerAnimated(true, completion = null)
        smsDelegate = null
    }
}
