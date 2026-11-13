package app.anynear.passwords

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ComponentName
import android.credentials.CredentialManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.PersistableBundle
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.view.autofill.AutofillManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.anynear.AndroidPlatform
import app.anynear.attachments.BinaryFiles
import app.anynear.attachments.platformBinaryFiles
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import kotlin.io.encoding.Base64
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Password key `pv` wrapped with RSA-OAEP (ADR 0039, item 2). The RSA key lives in the Android Keystore;
 * unwrapping needs a confirmation of the device lock within the last [AUTH_SECONDS] seconds, wrapping does not.
 */
class AndroidPasswordKeyStore(private val context: Context) : PasswordKeyStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun deviceSecure(): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    override fun state(): PasswordKeyState {
        if (prefs.getString(WRAPPED, null) == null) return PasswordKeyState.NONE
        val key = privateKey() ?: return PasswordKeyState.INVALIDATED
        return try {
            cipher().init(Cipher.DECRYPT_MODE, key, OAEP)
            PasswordKeyState.PRESENT
        } catch (e: KeyPermanentlyInvalidatedException) {
            PasswordKeyState.INVALIDATED
        } catch (e: Exception) {
            // Usually UserNotAuthenticatedException: valid, just not confirmed yet.
            PasswordKeyState.PRESENT
        }
    }

    override fun store(key: ByteArray): Boolean {
        if (!deviceSecure()) return false
        return runCatching {
            val keyStore = keyStore()
            if (keyStore.getCertificate(ALIAS) == null || state() == PasswordKeyState.INVALIDATED) generate()
            val public = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyStore().getCertificate(ALIAS).publicKey.encoded))
            val c = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, public, OAEP) }
            prefs.edit().putString(WRAPPED, Base64.encode(c.doFinal(key))).commit()
        }.getOrDefault(false)
    }

    override fun delete() {
        prefs.edit().remove(WRAPPED).commit()
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    /** After a successful confirmation of the device lock: unwrap the key. */
    fun read(): UnlockResult {
        val wrapped = prefs.getString(WRAPPED, null) ?: return UnlockResult.KeyLost
        val key = privateKey() ?: return UnlockResult.KeyLost
        return try {
            UnlockResult.Unlocked(cipher().apply { init(Cipher.DECRYPT_MODE, key, OAEP) }.doFinal(Base64.decode(wrapped)))
        } catch (e: KeyPermanentlyInvalidatedException) {
            UnlockResult.KeyLost
        } catch (e: Exception) {
            UnlockResult.Cancelled
        }
    }

    private fun generate() {
        runCatching { keyStore().deleteEntry(ALIAS) }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT)
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(AUTH_SECONDS, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(AUTH_SECONDS)
                }
            }
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE).apply { initialize(spec) }.generateKeyPair()
    }

    private fun privateKey(): PrivateKey? = runCatching { keyStore().getKey(ALIAS, null) as? PrivateKey }.getOrNull()

    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun cipher() = Cipher.getInstance(TRANSFORMATION)

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "passwords"
        internal const val PREFS = "passwords"
        private const val WRAPPED = "key"
        private const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        const val AUTH_SECONDS = 30

        // The Android Keystore only knows SHA-1 for MGF1.
        private val OAEP = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    }
}

/**
 * Device lock prompt by the system: from Android 11 on `BiometricPrompt` with strong fingerprint or
 * PIN/pattern/password, before that the device lock prompt ([confirmCredential] starts its intent).
 * Also for autofill.
 */
object DeviceCheck {
    fun start(activity: Activity, title: String, confirmCredential: (Intent) -> Unit, done: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
                .build()
            prompt.authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = done(true)
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = done(false)
                },
            )
        } else {
            @Suppress("DEPRECATION")
            val intent = activity.getSystemService(KeyguardManager::class.java)?.createConfirmDeviceCredentialIntent(title, null)
            if (intent == null) done(false) else confirmCredential(intent)
        }
    }
}

actual fun platformPasswordKeyStore(): PasswordKeyStore = AndroidPasswordKeyStore(AndroidPlatform.requireContext())

actual fun platformPasswordFiles(): BinaryFiles = platformBinaryFiles(PasswordStore.DIR_NAME)

@Composable
internal actual fun rememberPasswordUnlock(onResult: (UnlockResult) -> Unit): (reason: String) -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    val keys = remember { AndroidPasswordKeyStore(context.applicationContext) }
    val finish: (Boolean) -> Unit = { ok -> callback.value(if (ok) keys.read() else UnlockResult.Cancelled) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        finish(it.resultCode == Activity.RESULT_OK)
    }
    return remember(launcher) {
        { reason ->
            val activity = context.findActivity()
            when {
                !keys.deviceSecure() -> callback.value(UnlockResult.NoDeviceLock)
                keys.state() != PasswordKeyState.PRESENT -> callback.value(UnlockResult.KeyLost)
                activity == null -> callback.value(UnlockResult.Cancelled)
                else -> DeviceCheck.start(activity, reason, { launcher.launch(it) }, finish)
            }
        }
    }
}

@Composable
internal actual fun rememberDeviceCheck(onResult: (Boolean?) -> Unit): (reason: String) -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        callback.value(it.resultCode == Activity.RESULT_OK)
    }
    return remember(launcher) {
        { reason ->
            val activity = context.findActivity()
            when {
                context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure != true -> callback.value(null)
                activity == null -> callback.value(false)
                else -> DeviceCheck.start(activity, reason, { launcher.launch(it) }) { ok -> callback.value(ok) }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** From Android 14 on the passkey provider is part of it (ADR 0052); the same system page turns both on. */
internal actual fun checkAutofillEnabled(done: (Boolean) -> Unit) {
    val context = AndroidPlatform.requireContext()
    val autofill = context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
    val passkeys = Build.VERSION.SDK_INT < 34 || runCatching {
        context.getSystemService(CredentialManager::class.java)
            ?.isEnabledCredentialProviderService(ComponentName(context.packageName, PASSKEY_SERVICE))
    }.getOrNull() != false
    done(autofill && passkeys)
}

/** Class of the passkey provider in `androidApp` (`autofill/PasskeyProviderService`). */
private const val PASSKEY_SERVICE = "app.anynear.autofill.PasskeyProviderService"

internal actual fun passkeysSupported(): Boolean = Build.VERSION.SDK_INT >= 34

/** For the passkey provider, which sees only these identifiers while locked (ADR 0052, item 4). */
actual fun publishCredentialIdentities(logins: List<Login>) {
    PasskeyIndex.write(AndroidPlatform.requireContext(), logins)
}

/**
 * Website, user name and identifier of every passkey in the app's private storage. With this Android shows the
 * matching passkeys at sign-in before the device lock releases the key.
 */
object PasskeyIndex {
    private const val NAME = "passkeys"
    private val json = Json { ignoreUnknownKeys = true }

    fun write(context: Context, logins: List<Login>) {
        val entries = logins.flatMap { l -> l.passkeys.map { PasskeyIdentity(it.rpId, it.userName, it.credentialId) } }
        context.getSharedPreferences(AndroidPasswordKeyStore.PREFS, Context.MODE_PRIVATE).edit()
            .putString(NAME, json.encodeToString(ListSerializer(PasskeyIdentity.serializer()), entries)).apply()
    }

    fun read(context: Context): List<PasskeyIdentity> =
        context.getSharedPreferences(AndroidPasswordKeyStore.PREFS, Context.MODE_PRIVATE).getString(NAME, null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(PasskeyIdentity.serializer()), it) }.getOrNull() }
            .orEmpty()
}

internal actual fun openAutofillSettings() {
    val context = AndroidPlatform.requireContext()
    // From Android 14 on the page “Passwörter, Passkeys und Konten”, which turns on autofill and passkeys together.
    if (Build.VERSION.SDK_INT >= 34) {
        val page = Intent(Settings.ACTION_CREDENTIAL_PROVIDER, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(page) }.isSuccess) return
    }
    val request = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Some manufacturers do not know the direct dialog; then the general page for language and input.
    runCatching { context.startActivity(request) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

internal actual fun openDeviceLockSettings() {
    val context = AndroidPlatform.requireContext()
    runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

actual fun copySecret(text: String) {
    val context = AndroidPlatform.requireContext()
    val clip = ClipData.newPlainText("", text)
    // Android 13+ does not show confidential content in the clipboard preview.
    clip.description.extras = PersistableBundle().apply {
        putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}
