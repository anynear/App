package app.anynear.autofill

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PendingIntentHandler
import app.anynear.MainApplication
import app.anynear.R
import app.anynear.data.secureRandomBytes
import app.anynear.i18n.texts
import app.anynear.passwords.AndroidPasswordKeyStore
import app.anynear.passwords.DeviceCheck
import app.anynear.passwords.LoginMatch
import app.anynear.passwords.UnlockResult
import app.anynear.passwords.VaultFile
import app.anynear.passwords.Webauthn
import app.anynear.passwords.platformPasswordFiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest

/**
 * Create a passkey or sign in with it (ADR 0052, item 3): first the device lock, then open the vault. New
 * passkeys go via [VaultFile.INCOMING] into the logins and from there to your own devices. Cancelling releases nothing.
 */
@RequiresApi(34)
class PasskeyActivity : ComponentActivity() {
    private val keyguard = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onChecked(it.resultCode == Activity.RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        if (savedInstanceState != null) return
        val reason = if (intent.action == ACTION_CREATE) texts.passwords.passkeySave else texts.passwords.passkeySignIn
        DeviceCheck.start(this, reason, keyguard::launch, ::onChecked)
    }

    private fun onChecked(ok: Boolean) {
        val key = (if (ok) AndroidPasswordKeyStore(applicationContext).read() else null) as? UnlockResult.Unlocked
        if (key == null) return cancel()
        try {
            if (intent.action == ACTION_CREATE) create(key.key) else get(key.key)
        } finally {
            key.key.fill(0)
        }
    }

    private fun create(key: ByteArray) {
        val request = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        val call = request?.callingRequest as? CreatePublicKeyCredentialRequest ?: return fail()
        val asked = PasskeyRequest.create(call.requestJson) ?: return fail()
        val client = clientData("webauthn.create", asked.challenge, asked.rpId, request.callingAppInfo, call.clientDataHash) ?: return fail()
        val files = platformPasswordFiles()
        val logins = VaultFile.readAll(files, key)?.logins.orEmpty()
        // Already a passkey from anynear for this account: the website should not create a second one.
        if (logins.any { l -> l.passkeys.any { it.credentialId in asked.excluded } }) {
            PendingIntentHandler.setCreateCredentialException(result, CreatePublicKeyCredentialDomException(InvalidStateError()))
            return done()
        }
        val now = System.currentTimeMillis()
        val reg = Webauthn.create(asked.rpId, asked.userHandle, asked.userName, now)
        val login = LoginMatch.withPasskey(logins, reg.passkey, Webauthn.b64(secureRandomBytes(12)), now)
        if (!VaultFile.addIncoming(files, key, login)) return fail()
        (application as MainApplication).controller.passwords?.mergeIncoming(key.copyOf())
        val json = buildJsonObject {
            put("id", reg.passkey.credentialId)
            put("rawId", reg.passkey.credentialId)
            put("type", "public-key")
            put("authenticatorAttachment", "platform")
            putJsonObject("response") {
                put("clientDataJSON", Webauthn.b64(client.json))
                put("attestationObject", Webauthn.b64(reg.attestationObject))
                put("authenticatorData", Webauthn.b64(reg.authenticatorData))
                put("publicKey", Webauthn.b64(reg.publicKeyDer))
                put("publicKeyAlgorithm", Webauthn.ALG_ES256)
                put("transports", buildJsonArray { add(JsonPrimitive("internal")); add(JsonPrimitive("hybrid")) })
            }
            putJsonObject("clientExtensionResults") { putJsonObject("credProps") { put("rk", true) } }
        }
        PendingIntentHandler.setCreateCredentialResponse(result, CreatePublicKeyCredentialResponse(json.toString()))
        done()
    }

    private fun get(key: ByteArray) {
        val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent) ?: return fail()
        val option = request.credentialOptions.filterIsInstance<GetPublicKeyCredentialOption>().firstOrNull() ?: return fail()
        val asked = PasskeyRequest.get(option.requestJson) ?: return fail()
        val credentialId = intent.getStringExtra(EXTRA_CREDENTIAL_ID) ?: return fail()
        val logins = VaultFile.readAll(platformPasswordFiles(), key)?.logins.orEmpty()
        val passkey = LoginMatch.passkeys(logins, asked.rpId).map { it.second }.firstOrNull { it.credentialId == credentialId } ?: return fail()
        val client = clientData("webauthn.get", asked.challenge, asked.rpId, request.callingAppInfo, option.clientDataHash) ?: return fail()
        val assertion = Webauthn.assert(passkey, client.hash)
        val json = buildJsonObject {
            put("id", passkey.credentialId)
            put("rawId", passkey.credentialId)
            put("type", "public-key")
            put("authenticatorAttachment", "platform")
            putJsonObject("response") {
                put("clientDataJSON", Webauthn.b64(client.json))
                put("authenticatorData", Webauthn.b64(assertion.authenticatorData))
                put("signature", Webauthn.b64(assertion.signature))
                put("userHandle", passkey.userHandle)
            }
            putJsonObject("clientExtensionResults") {}
        }
        PendingIntentHandler.setGetCredentialResponse(result, GetCredentialResponse(PublicKeyCredential(json.toString())))
        done()
    }

    private class ClientData(val json: ByteArray, val hash: ByteArray)

    /**
     * Origin (ADR 0052, item 3): a browser from Google's list names the page and brings the hash of its
     * clientDataJSON along; the page must match the RP ID. Every other app gets `android:apk-key-hash:…` from its
     * signature, and anynear builds the clientDataJSON itself; the website then checks whether it knows the app.
     */
    private fun clientData(type: String, challenge: String, rpId: String, caller: CallingAppInfo, browserHash: ByteArray?): ClientData? {
        val allowlist = resources.openRawResource(R.raw.privileged_browsers).bufferedReader().use { it.readText() }
        val browserOrigin = runCatching { caller.getOrigin(allowlist) }.getOrNull()
        if (browserOrigin != null && browserHash != null) {
            val host = LoginMatch.host(browserOrigin) ?: return null
            if (!(host == rpId.lowercase() || host.endsWith(".${rpId.lowercase()}"))) return null
            // The browser inserts its own clientDataJSON; here there is only a placeholder.
            return ClientData("{}".encodeToByteArray(), browserHash)
        }
        val signer = caller.signingInfo.apkContentsSigners.firstOrNull() ?: return null
        val origin = "android:apk-key-hash:" + Webauthn.b64(MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()))
        val json = buildJsonObject {
            put("type", type)
            put("challenge", challenge)
            put("origin", origin)
            put("androidPackageName", caller.packageName)
        }.toString().encodeToByteArray()
        return ClientData(json, Webauthn.sha256(json))
    }

    private val result = Intent()

    private fun done() {
        setResult(RESULT_OK, result)
        finish()
    }

    private fun cancel() {
        if (intent.action == ACTION_CREATE) {
            PendingIntentHandler.setCreateCredentialException(result, CreateCredentialCancellationException())
        } else {
            PendingIntentHandler.setGetCredentialException(result, GetCredentialCancellationException())
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun fail() {
        if (intent.action == ACTION_CREATE) {
            PendingIntentHandler.setCreateCredentialException(result, CreateCredentialUnknownException())
        } else {
            PendingIntentHandler.setGetCredentialException(result, GetCredentialUnknownException())
        }
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        const val ACTION_CREATE = "app.anynear.passkey.CREATE"
        const val ACTION_GET = "app.anynear.passkey.GET"
        const val EXTRA_CREDENTIAL_ID = "credentialId"
    }
}

/** What anynear needs from the website's WebAuthn options (JSON like `PublicKeyCredentialCreationOptionsJSON`). */
internal class PasskeyRequest(
    val rpId: String,
    val challenge: String,
    val userHandle: ByteArray = ByteArray(0),
    val userName: String = "",
    val excluded: Set<String> = emptySet(),
    val allowed: Set<String> = emptySet(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Only ES256; without `-7` in `pubKeyCredParams` anynear creates no passkey. */
        fun create(text: String): PasskeyRequest? = runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            val rp = o.getValue("rp").jsonObject
            val user = o.getValue("user").jsonObject
            val algs = o["pubKeyCredParams"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["alg"]?.jsonPrimitive?.content?.toIntOrNull() }
            if (algs.isNotEmpty() && Webauthn.ALG_ES256 !in algs) return null
            PasskeyRequest(
                rpId = rp.getValue("id").jsonPrimitive.content,
                challenge = o.getValue("challenge").jsonPrimitive.content,
                userHandle = Webauthn.unb64(user.getValue("id").jsonPrimitive.content),
                userName = user["name"]?.jsonPrimitive?.content ?: user["displayName"]?.jsonPrimitive?.content.orEmpty(),
                excluded = ids(o, "excludeCredentials"),
            )
        }.getOrNull()

        fun get(text: String): PasskeyRequest? = runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            PasskeyRequest(
                rpId = o.getValue("rpId").jsonPrimitive.content,
                challenge = o.getValue("challenge").jsonPrimitive.content,
                allowed = ids(o, "allowCredentials"),
            )
        }.getOrNull()

        private fun ids(o: JsonObject, name: String): Set<String> =
            o[name]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content?.trimEnd('=') }.toSet()
    }
}
