package app.anynear.autofill

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.Toast
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import app.anynear.i18n.texts
import app.anynear.passwords.AndroidPasswordKeyStore
import app.anynear.passwords.DeviceCheck
import app.anynear.passwords.Login
import app.anynear.passwords.LoginMatch
import app.anynear.passwords.Otp
import app.anynear.passwords.copySecret
import app.anynear.passwords.UnlockResult
import app.anynear.passwords.VaultFile
import app.anynear.passwords.platformPasswordFiles
import app.anynear.ui.PasswordPickScreen

/**
 * After „Mit anynear ausfüllen“: device lock, then fill in (ADR 0039, item 6). Exactly one matching login for the
 * browser's domain fills in immediately, otherwise one chooses from the list. Cancelling releases nothing.
 * If the login has a one-time code (ADR 0052), it goes into the code field or, for the page after, to the clipboard.
 */
class AutofillAuthActivity : ComponentActivity() {
    private val keyguard = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onChecked(it.resultCode == Activity.RESULT_OK)
    }

    private val usernameId: AutofillId? get() = intent.parcelable(EXTRA_USERNAME_ID)
    private val passwordId: AutofillId? get() = intent.parcelable(EXTRA_PASSWORD_ID)
    private val otpId: AutofillId? get() = intent.parcelable(EXTRA_OTP_ID)
    private val onlyCode: Boolean get() = usernameId == null && passwordId == null
    private val domain: String? get() = intent.getStringExtra(EXTRA_DOMAIN)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Header area night blue, hence light icons in the status bar (like MainActivity).
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        if (savedInstanceState == null) DeviceCheck.start(this, texts.passwords.fillWithApp, keyguard::launch, ::onChecked)
    }

    private fun onChecked(ok: Boolean) {
        val result = if (ok) AndroidPasswordKeyStore(applicationContext).read() else UnlockResult.Cancelled
        val all = (result as? UnlockResult.Unlocked)?.let { unlocked ->
            VaultFile.readAll(platformPasswordFiles(), unlocked.key)?.logins.also { unlocked.key.fill(0) }
        }
        // Only for a code field: only logins with a one-time code.
        val logins = if (onlyCode) all?.filter { it.otp != null } else all?.filter { it.password.isNotEmpty() }
        if (logins.isNullOrEmpty()) return finish()
        val asked = domain
        val matching = asked?.let { d -> logins.filter { LoginMatch.matches(it, d) } }.orEmpty()
        if (matching.size == 1) return fill(matching.single())
        setContent { PasswordPickScreen(logins, asked, onPick = ::fill, onCancel = ::finish) }
    }

    private fun fill(login: Login) {
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, LoginMatch.displayName(login.website))
        }
        val code = login.otp?.let { Otp.parse(it) }?.code(System.currentTimeMillis())
        val dataset = Dataset.Builder().apply {
            usernameId?.let { setValue(it, AutofillValue.forText(login.username), presentation) }
            passwordId?.let { setValue(it, AutofillValue.forText(login.password), presentation) }
            if (code != null) otpId?.let { setValue(it, AutofillValue.forText(code), presentation) }
        }.build()
        // Code field only on the next page: keep the code ready for pasting.
        if (code != null && otpId == null) {
            copySecret(code)
            Toast.makeText(this, texts.passwords.otpReadyToPaste, Toast.LENGTH_LONG).show()
        }
        setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
        finish()
    }

    companion object {
        const val EXTRA_USERNAME_ID = "username"
        const val EXTRA_PASSWORD_ID = "password"
        const val EXTRA_OTP_ID = "otp"
        const val EXTRA_DOMAIN = "domain"
    }
}

private inline fun <reified T : android.os.Parcelable> Intent.parcelable(name: String): T? =
    if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(name, T::class.java) else @Suppress("DEPRECATION") getParcelableExtra(name)
