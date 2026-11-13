package app.anynear.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import app.anynear.i18n.texts
import app.anynear.passwords.VaultFile
import app.anynear.passwords.platformPasswordFiles

/**
 * Autofill (ADR 0039, item 6). In login fields there is an entry „Mit anynear ausfüllen“; only
 * [AutofillAuthActivity] asks for the device lock, decrypts and fills in. Nothing is decrypted here.
 * Fields for one-time codes get „Code mit anynear einfügen“ (ADR 0052, item 2).
 * The first version does not save new sign-ins.
 */
class PasswordAutofillService : AutofillService() {
    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
        if (structure.activityComponent.packageName == packageName) return callback.onSuccess(null)
        if (VaultFile.NAME !in platformPasswordFiles().names()) return callback.onSuccess(null)
        val fields = LoginFields.find(structure)
        if (fields.password == null && fields.username == null && fields.otp == null) return callback.onSuccess(null)
        val ids = listOfNotNull(fields.username, fields.password, fields.otp)
        val onlyCode = fields.password == null && fields.username == null

        val intent = Intent(this, AutofillAuthActivity::class.java)
            .putExtra(AutofillAuthActivity.EXTRA_USERNAME_ID, fields.username)
            .putExtra(AutofillAuthActivity.EXTRA_PASSWORD_ID, fields.password)
            .putExtra(AutofillAuthActivity.EXTRA_OTP_ID, fields.otp)
            .putExtra(AutofillAuthActivity.EXTRA_DOMAIN, fields.webDomain)
        // Mutable: Android attaches the screen structure when opening.
        val pending = PendingIntent.getActivity(
            this, request.id, intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, if (onlyCode) texts.passwords.fillCode else texts.passwords.fillWithApp)
        }
        val dataset = Dataset.Builder(presentation).apply {
            ids.forEach { setValue(it, null) }
            setAuthentication(pending.intentSender)
        }.build()
        callback.onSuccess(FillResponse.Builder().addDataset(dataset).build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) = callback.onSuccess()
}

/** Login fields of a third-party page or app, a field for the one-time code and the domain the browser reports. */
internal class LoginFields(val username: AutofillId?, val password: AutofillId?, val otp: AutofillId?, val webDomain: String?) {
    companion object {
        fun find(structure: AssistStructure): LoginFields {
            var username: AutofillId? = null
            var password: AutofillId? = null
            var otp: AutofillId? = null
            var domain: String? = null
            fun visit(node: AssistStructure.ViewNode) {
                if (domain == null) node.webDomain?.takeIf { it.isNotBlank() }?.let { domain = it }
                val id = node.autofillId
                if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT) {
                    when {
                        password == null && isPassword(node) -> password = id
                        otp == null && isOtp(node) -> otp = id
                        username == null && isUsername(node) -> username = id
                    }
                }
                for (i in 0 until node.childCount) visit(node.getChildAt(i))
            }
            for (i in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(i).rootViewNode)
            return LoginFields(username, password, otp, domain)
        }

        private fun isPassword(node: AssistStructure.ViewNode): Boolean {
            if (node.autofillHints.orEmpty().any { it.contains("password", ignoreCase = true) }) return true
            val variation = node.inputType and InputType.TYPE_MASK_VARIATION
            if (node.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
                variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
            ) {
                return true
            }
            return node.htmlInfo?.attributes.orEmpty().any { it.first == "type" && it.second == "password" }
        }

        /** Field for a one-time code: hint `one-time-code`/`otp` or a typical name. */
        private fun isOtp(node: AssistStructure.ViewNode): Boolean {
            if (node.autofillHints.orEmpty().any { h -> OTP_HINTS.any { h.contains(it, ignoreCase = true) } }) return true
            val html = node.htmlInfo?.attributes.orEmpty()
            if (html.any { it.first == "autocomplete" && it.second.equals("one-time-code", ignoreCase = true) }) return true
            val names = listOfNotNull(node.idEntry, node.hint) + html.filter { it.first == "name" || it.first == "id" }.map { it.second }
            return names.any { n -> OTP_WORDS.any { n.contains(it, ignoreCase = true) } }
        }

        private fun isUsername(node: AssistStructure.ViewNode): Boolean {
            val hints = node.autofillHints.orEmpty()
            if (hints.any { h -> USER_HINTS.any { h.contains(it, ignoreCase = true) } }) return true
            val variation = node.inputType and InputType.TYPE_MASK_VARIATION
            if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) return true
            val html = node.htmlInfo?.attributes.orEmpty()
            if (html.any { it.first == "type" && it.second == "email" }) return true
            if (html.any { it.first == "autocomplete" && USER_HINTS.any { h -> it.second.contains(h, ignoreCase = true) } }) return true
            val names = listOfNotNull(node.idEntry, node.hint) + html.filter { it.first == "name" || it.first == "id" }.map { it.second }
            return names.any { n -> USER_WORDS.any { n.contains(it, ignoreCase = true) } }
        }

        private val USER_HINTS = listOf("username", "email")
        private val OTP_HINTS = listOf("otp", "one-time", "onetime", "2fa", "smsotpcode")
        private val OTP_WORDS = listOf("otp", "totp", "2fa", "mfa", "one-time", "onetime", "einmalcode", "verification-code", "verificationcode", "authcode")
        private val USER_WORDS = listOf("user", "email", "e-mail", "login", "benutzer", "nutzer")
    }
}
