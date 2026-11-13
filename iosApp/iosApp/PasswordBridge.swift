import AuthenticationServices
import Shared
import UIKit

/**
 Swift side of the passwords for the shared code (`IosPasswordKeychain`, ADR 0039): keychain, Face ID and
 the system settings for the password provider.
 */
final class PasswordBridge: NSObject, IosPasswordKeychain {
    func deviceSecure() -> Bool { PasswordKeychain.deviceSecure() }

    func hasKey() -> Bool { PasswordKeychain.hasKey() }

    func store(key: Data) -> Bool { PasswordKeychain.store(key) }

    func remove() { PasswordKeychain.remove() }

    func unlock(reason: String, done: @escaping (Data?, String) -> Void) {
        PasswordKeychain.unlock(reason: reason, done: done)
    }

    func checkAutofill(done: @escaping (KotlinBoolean) -> Void) {
        ASCredentialIdentityStore.shared.getState { state in done(KotlinBoolean(value: state.isEnabled)) }
    }

    func openAutofillSettings() {
        if #available(iOS 17.0, *) {
            ASSettingsHelper.openCredentialProviderAppSettings { _ in }
        } else {
            openSettings()
        }
    }

    /// Identities for the suggestions above the keyboard (ADR 0052, item 4); replaces the previous ones.
    func publishIdentities(json: String) {
        guard let entries = try? JSONDecoder().decode([Identity].self, from: Data(json.utf8)) else { return }
        let store = ASCredentialIdentityStore.shared
        store.getState { state in
            guard state.isEnabled else { return }
            let passwords = entries.filter(\.password).map {
                ASPasswordCredentialIdentity(serviceIdentifier: ASCredentialServiceIdentifier(identifier: $0.website, type: .domain), user: $0.username, recordIdentifier: $0.id)
            }
            guard #available(iOS 17.0, *) else {
                return store.replaceCredentialIdentities(with: passwords) { _, _ in }
            }
            var all: [ASCredentialIdentity] = passwords
            for e in entries {
                for p in e.passkeys {
                    guard let credentialId = Webauthn.unb64(p.credentialId), let userHandle = Webauthn.unb64(p.userHandle) else { continue }
                    all.append(ASPasskeyCredentialIdentity(relyingPartyIdentifier: p.rpId, userName: p.userName, credentialID: credentialId, userHandle: userHandle, recordIdentifier: e.id))
                }
                if #available(iOS 18.0, *), e.otp {
                    let service = ASCredentialServiceIdentifier(identifier: e.website, type: .domain)
                    all.append(ASOneTimeCodeCredentialIdentity(serviceIdentifier: service, label: e.username.isEmpty ? e.website : e.username, recordIdentifier: e.id))
                }
            }
            store.replaceCredentialIdentities(all) { _, _ in }
        }
    }

    /// Like `IosIdentity` in the shared code.
    private struct Identity: Decodable {
        let id: String
        let website: String
        let username: String
        let password: Bool
        let otp: Bool
        let passkeys: [PasskeyIdentity]
    }

    private struct PasskeyIdentity: Decodable {
        let rpId: String
        let userName: String
        let credentialId: String
        let userHandle: String
    }

    // iOS does not allow a direct jump to „Face ID & Code“; the app's page is the closest way.
    func openDeviceLockSettings() { openSettings() }

    private func openSettings() {
        DispatchQueue.main.async {
            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
        }
    }
}
