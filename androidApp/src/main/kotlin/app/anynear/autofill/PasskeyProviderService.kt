package app.anynear.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import app.anynear.i18n.texts
import app.anynear.passwords.AndroidPasswordKeyStore
import app.anynear.passwords.PasskeyIndex
import app.anynear.passwords.PasswordKeyState

/**
 * Passkey provider from Android 14 on (ADR 0052, item 3). Nothing is decrypted here: for creating it offers „anynear“,
 * for signing in the matching passkeys from [PasskeyIndex]. Only [PasskeyActivity] asks for the device lock,
 * creates or signs.
 */
@RequiresApi(34)
class PasskeyProviderService : CredentialProviderService() {
    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        if (request !is BeginCreatePublicKeyCredentialRequest) return callback.onError(CreateCredentialUnknownException())
        // Without passwords set up there is no place for the passkey; then anynear does not offer itself.
        if (AndroidPasswordKeyStore(this).state() != PasswordKeyState.PRESENT) return callback.onResult(BeginCreateCredentialResponse())
        val intent = Intent(this, PasskeyActivity::class.java).setAction(PasskeyActivity.ACTION_CREATE)
        val entry = CreateEntry.Builder(texts.passwords.title, pending(intent, 0)).setDescription(texts.passwords.passkeySave).build()
        callback.onResult(BeginCreateCredentialResponse(createEntries = listOf(entry)))
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        val known = PasskeyIndex.read(this)
        val entries = mutableListOf<CredentialEntry>()
        request.beginGetCredentialOptions.filterIsInstance<BeginGetPublicKeyCredentialOption>().forEach { option ->
            val asked = PasskeyRequest.get(option.requestJson) ?: return@forEach
            known.filter { it.rpId.equals(asked.rpId, ignoreCase = true) && (asked.allowed.isEmpty() || it.credentialId in asked.allowed) }
                .forEach { identity ->
                    val intent = Intent(this, PasskeyActivity::class.java)
                        .setAction(PasskeyActivity.ACTION_GET)
                        .putExtra(PasskeyActivity.EXTRA_CREDENTIAL_ID, identity.credentialId)
                    entries += PublicKeyCredentialEntry.Builder(this, identity.userName, pending(intent, entries.size + 1), option)
                        .setDisplayName(identity.rpId)
                        .build()
                }
        }
        callback.onResult(BeginGetCredentialResponse(credentialEntries = entries))
    }

    // anynear keeps no sign-in state that it would have to reset.
    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) = callback.onResult(null)

    // Mutable: Android attaches the website's request when opening.
    private fun pending(intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(this, requestCode, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
