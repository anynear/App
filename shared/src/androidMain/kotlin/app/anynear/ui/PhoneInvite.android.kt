package app.anynear.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.anynear.AndroidPlatform

/**
 * Picker for one phone number of the phone's contacts. The system grants read access to just the chosen entry, so
 * anynear needs no READ_CONTACTS permission and never sees the rest of the address book.
 */
@Composable
internal actual fun rememberPhoneContactPicker(onPicked: (PickedPhoneContact?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(PickPhoneNumber) { uri ->
        callback.value(uri?.let { PickedPhoneContact(listOfNotNull(readNumber(context, it))) })
    }
    return {
        try {
            launcher.launch(Unit)
        } catch (e: ActivityNotFoundException) {
            // No contacts app (rare): straight to the share menu.
            callback.value(PickedPhoneContact(emptyList()))
        }
    }
}

private object PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit) =
        Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
}

private fun readNumber(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (e: SecurityException) {
    null
}

internal actual fun openSms(number: String, text: String): Boolean {
    val context = AndroidPlatform.requireContext()
    // Tablets without a SIM may still have a messaging app, but cannot send: use the share menu there.
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return false
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
        .putExtra("sms_body", text)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
