import AuthenticationServices
import SwiftUI
import UniformTypeIdentifiers

/**
 Password extension (ADR 0039, item 6; ADR 0052): after Face ID, Touch ID or device passcode it shows the logins, those
 matching the website on top. From iOS 17 on it signs in with passkeys and creates new ones, from iOS 18 on it inserts
 one-time codes. Suggestions above the keyboard come from the identities the app puts into `ASCredentialIdentityStore`;
 chosen ones reach the target via `prepareInterfaceToProvideCredential` without a list. It releases nothing unconfirmed.
 */
final class CredentialProviderViewController: ASCredentialProviderViewController {
    private let model = PickModel()

    // MARK: Passwords from the list

    override func prepareCredentialList(for serviceIdentifiers: [ASCredentialServiceIdentifier]) {
        model.domain = domain(serviceIdentifiers)
        model.kind = .password
        present { [weak self] login in self?.completePassword(login) }
    }

    // MARK: Passkeys (iOS 17)

    @available(iOS 17.0, *)
    override func prepareCredentialList(for serviceIdentifiers: [ASCredentialServiceIdentifier], requestParameters: ASPasskeyCredentialRequestParameters) {
        let rpId = requestParameters.relyingPartyIdentifier
        let allowed = Set(requestParameters.allowedCredentials.map(Webauthn.b64))
        model.domain = rpId
        model.kind = .passkey(rpId: rpId, allowed: allowed)
        present { [weak self] login in
            guard let self, let passkey = LoginMatch.passkeys([login], rpId: rpId).map(\.1).first(where: { allowed.isEmpty || allowed.contains($0.credentialId) })
            else { return }
            self.completePasskey(passkey, clientDataHash: requestParameters.clientDataHash)
        }
    }

    @available(iOS 17.0, *)
    override func prepareInterface(forPasskeyRegistration registrationRequest: ASCredentialRequest) {
        guard let request = registrationRequest as? ASPasskeyCredentialRequest,
              let identity = request.credentialIdentity as? ASPasskeyCredentialIdentity,
              request.supportedAlgorithms.contains(ASCOSEAlgorithmIdentifier.ES256)
        else { return cancel(.failed) }
        model.kind = .saving
        showView()
        PasswordKeychain.unlock(reason: Texts.savePasskey) { [weak self] key, why in
            DispatchQueue.main.async {
                guard let self else { return }
                guard let key else { return self.cancel(why == PasswordKeychain.cancelled ? .userCanceled : .failed) }
                let logins = PasswordVaultFile.read(key: key) ?? []
                let now = Int64(Date().timeIntervalSince1970 * 1000)
                let reg = Webauthn.create(rpId: identity.relyingPartyIdentifier, userHandle: identity.userHandle, userName: identity.userName)
                var id = Data(count: 12)
                _ = id.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 12, $0.baseAddress!) }
                let login = LoginMatch.withPasskey(logins, passkey: reg.passkey, newId: Webauthn.b64(id), now: now)
                guard PasswordVaultFile.addIncoming(key: key, login: login) else { return self.cancel(.failed) }
                ASCredentialIdentityStore.shared.saveCredentialIdentities([
                    ASPasskeyCredentialIdentity(
                        relyingPartyIdentifier: identity.relyingPartyIdentifier,
                        userName: identity.userName,
                        credentialID: reg.credentialId,
                        userHandle: identity.userHandle,
                        recordIdentifier: login.id
                    ),
                ]) { _, _ in }
                self.extensionContext.completeRegistrationRequest(using: ASPasskeyRegistrationCredential(
                    relyingParty: identity.relyingPartyIdentifier,
                    clientDataHash: request.clientDataHash,
                    credentialID: reg.credentialId,
                    attestationObject: reg.attestationObject
                ))
            }
        }
    }

    // MARK: One-time codes (iOS 18)

    @available(iOS 18.0, *)
    override func prepareOneTimeCodeCredentialList(for serviceIdentifiers: [ASCredentialServiceIdentifier]) {
        model.domain = domain(serviceIdentifiers)
        model.kind = .code
        present { [weak self] login in
            guard let self, let code = login.otp.flatMap({ Totp.code($0) }) else { return }
            self.extensionContext.completeOneTimeCodeRequest(using: ASOneTimeCodeCredential(code: code))
        }
    }

    // MARK: Suggestion above the keyboard chosen

    override func provideCredentialWithoutUserInteraction(for credentialIdentity: ASPasswordCredentialIdentity) {
        cancel(.userInteractionRequired)
    }

    @available(iOS 17.0, *)
    override func provideCredentialWithoutUserInteraction(for credentialRequest: ASCredentialRequest) {
        cancel(.userInteractionRequired)
    }

    override func prepareInterfaceToProvideCredential(for credentialIdentity: ASPasswordCredentialIdentity) {
        provideDirectly(recordId: credentialIdentity.recordIdentifier) { [weak self] login in self?.completePassword(login) }
    }

    @available(iOS 17.0, *)
    override func prepareInterfaceToProvideCredential(for credentialRequest: ASCredentialRequest) {
        if let request = credentialRequest as? ASPasskeyCredentialRequest,
           let identity = request.credentialIdentity as? ASPasskeyCredentialIdentity {
            let wanted = Webauthn.b64(identity.credentialID)
            return provideDirectly(recordId: identity.recordIdentifier) { [weak self] login in
                guard let passkey = (login.passkeys ?? []).first(where: { $0.credentialId == wanted }) else { return self?.cancel(.credentialIdentityNotFound) ?? () }
                self?.completePasskey(passkey, clientDataHash: request.clientDataHash)
            }
        }
        if #available(iOS 18.0, *), credentialRequest.type == .oneTimeCode {
            return provideDirectly(recordId: credentialRequest.credentialIdentity.recordIdentifier) { [weak self] login in
                guard let code = login.otp.flatMap({ Totp.code($0) }) else { return self?.cancel(.credentialIdentityNotFound) ?? () }
                self?.extensionContext.completeOneTimeCodeRequest(using: ASOneTimeCodeCredential(code: code))
            }
        }
        let recordId = (credentialRequest.credentialIdentity as? ASPasswordCredentialIdentity)?.recordIdentifier
        provideDirectly(recordId: recordId) { [weak self] login in self?.completePassword(login) }
    }

    // MARK: Finish

    /// Fill in the password; if the login has a one-time code, it is then ready for pasting for 90 seconds.
    private func completePassword(_ login: VaultLogin) {
        if let code = login.otp.flatMap({ Totp.code($0) }) {
            UIPasteboard.general.setItems(
                [[UTType.utf8PlainText.identifier: code]],
                options: [.localOnly: true, .expirationDate: Date(timeIntervalSinceNow: 90)]
            )
        }
        extensionContext.completeRequest(withSelectedCredential: ASPasswordCredential(user: login.username, password: login.password), completionHandler: nil)
    }

    @available(iOS 17.0, *)
    private func completePasskey(_ passkey: VaultPasskey, clientDataHash: Data) {
        guard let signed = Webauthn.assert(passkey, clientDataHash: clientDataHash),
              let userHandle = Webauthn.unb64(passkey.userHandle),
              let credentialId = Webauthn.unb64(passkey.credentialId)
        else { return cancel(.failed) }
        extensionContext.completeAssertionRequest(using: ASPasskeyAssertionCredential(
            userHandle: userHandle,
            relyingParty: passkey.rpId,
            signature: signed.signature,
            clientDataHash: clientDataHash,
            authenticatorData: signed.authenticatorData,
            credentialID: credentialId
        ))
    }

    // MARK: Flow

    /// Show the list and unlock; [onPick] gets the chosen login. Exactly one matching one is taken right away.
    private func present(onPick: @escaping (VaultLogin) -> Void) {
        model.onPick = onPick
        model.onCancel = { [weak self] in self?.cancel(.userCanceled) }
        model.onRetry = { [weak self] in self?.unlock() }
        showView()
        unlock()
    }

    /// Without a list: unlock and take the login [recordId].
    private func provideDirectly(recordId: String?, then: @escaping (VaultLogin) -> Void) {
        PasswordKeychain.unlock(reason: Texts.reason) { [weak self] key, why in
            DispatchQueue.main.async {
                guard let self else { return }
                guard let key, let logins = PasswordVaultFile.read(key: key) else {
                    return self.cancel(why == PasswordKeychain.cancelled ? .userCanceled : .failed)
                }
                guard let login = logins.first(where: { $0.id == recordId }) else { return self.cancel(.credentialIdentityNotFound) }
                then(login)
            }
        }
    }

    private func showView() {
        guard children.isEmpty else { return }
        let host = UIHostingController(rootView: PickView(model: model))
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)
    }

    private func unlock() {
        PasswordKeychain.unlock(reason: Texts.reason) { [weak self] key, why in
            DispatchQueue.main.async {
                guard let self else { return }
                guard let key, let logins = PasswordVaultFile.read(key: key) else {
                    self.model.state = why == PasswordKeychain.cancelled ? .locked : .unavailable
                    return
                }
                self.model.logins = logins
                self.model.state = .unlocked
                // Passkey or code: exactly one matching one is taken without a list.
                if self.model.kind != .password, self.model.matching.count == 1 { self.model.onPick(self.model.matching[0]) }
            }
        }
    }

    private func domain(_ serviceIdentifiers: [ASCredentialServiceIdentifier]) -> String? {
        serviceIdentifiers.lazy.compactMap { LoginMatch.host($0.identifier) }.first
    }

    private func cancel(_ code: ASExtensionError.Code) {
        extensionContext.cancelRequest(withError: NSError(domain: ASExtensionErrorDomain, code: code.rawValue))
    }
}

final class PickModel: ObservableObject {
    enum State { case waiting, locked, unavailable, unlocked }

    enum Kind: Equatable {
        case password, code, saving
        case passkey(rpId: String, allowed: Set<String>)

        var isPasskey: Bool { if case .passkey = self { return true } else { return false } }
    }

    @Published var state = State.waiting
    @Published var logins: [VaultLogin] = []
    var domain: String?
    var kind = Kind.password
    var onPick: (VaultLogin) -> Void = { _ in }
    var onCancel: () -> Void = {}
    var onRetry: () -> Void = {}

    /// What matches the request at all: passwords, logins with a one-time code or with a passkey for the RP ID.
    var usable: [VaultLogin] {
        switch kind {
        case .password: return logins.filter { !$0.password.isEmpty }
        case .code: return logins.filter { $0.otp != nil }
        case .saving: return []
        case let .passkey(rpId, allowed):
            return logins.filter { l in LoginMatch.passkeys([l], rpId: rpId).contains { allowed.isEmpty || allowed.contains($0.1.credentialId) } }
        }
    }

    var matching: [VaultLogin] {
        if kind.isPasskey { return usable }
        return domain.map { d in usable.filter { LoginMatch.matches($0, domain: d) } } ?? []
    }

    /// Passkeys exist only for exactly this website; the list then does not show the other logins.
    var others: [VaultLogin] {
        if kind.isPasskey { return [] }
        return usable.filter { l in !matching.contains { $0.id == l.id } }
    }
}

private struct PickView: View {
    @ObservedObject var model: PickModel
    @State private var query = ""

    var body: some View {
        NavigationView {
            content
                .navigationTitle(model.kind == .code ? Texts.chooseCode : Texts.choose)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(Texts.cancel, action: model.onCancel) }
                }
        }
        .navigationViewStyle(.stack)
    }

    @ViewBuilder private var content: some View {
        switch model.state {
        case .waiting:
            ProgressView()
        case .locked:
            VStack(spacing: 16) {
                Text(Texts.locked).multilineTextAlignment(.center)
                Button(Texts.unlock, action: model.onRetry).buttonStyle(.borderedProminent)
            }.padding()
        case .unavailable:
            Text(Texts.unavailable).multilineTextAlignment(.center).padding()
        case .unlocked:
            list
        }
    }

    private var list: some View {
        let filter: (VaultLogin) -> Bool = { query.isEmpty || $0.website.localizedCaseInsensitiveContains(query) || $0.username.localizedCaseInsensitiveContains(query) }
        let order: (VaultLogin, VaultLogin) -> Bool = { LoginMatch.displayName($0.website).lowercased() < LoginMatch.displayName($1.website).lowercased() }
        let matching = model.matching.filter(filter).sorted(by: order)
        let others = model.others.filter(filter).sorted(by: order)
        return List {
            if !matching.isEmpty {
                Section(header: Text(Texts.matching)) { ForEach(matching) { row($0) } }
            }
            if !others.isEmpty {
                Section(header: Text(matching.isEmpty ? Texts.logins : Texts.others)) { ForEach(others) { row($0) } }
            }
            if matching.isEmpty && others.isEmpty { Text(Texts.nothing).foregroundColor(.secondary) }
        }
        .searchable(text: $query, prompt: Texts.search)
    }

    private func row(_ login: VaultLogin) -> some View {
        Button { model.onPick(login) } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(LoginMatch.displayName(login.website)).foregroundColor(.primary)
                if !login.username.isEmpty { Text(login.username).font(.subheadline).foregroundColor(.secondary) }
            }
        }
    }
}

/// Texts in German (phone `de*`) and English as in the app (ADR 0026).
private enum Texts {
    private static let de = (Locale.preferredLanguages.first ?? "en").hasPrefix("de")
    static let reason = de ? "Passwort ausfüllen" : "Fill in password"
    static let savePasskey = de ? "Passkey speichern" : "Save passkey"
    static let choose = de ? "Zugang wählen" : "Choose login"
    static let chooseCode = de ? "Code wählen" : "Choose code"
    static let cancel = de ? "Abbrechen" : "Cancel"
    static let unlock = de ? "Entsperren" : "Unlock"
    static let locked = de ? "Deine Passwörter öffnen sich nur mit Face ID, Touch ID oder dem Gerätecode." : "Your passwords only open with Face ID, Touch ID or your passcode."
    static let unavailable = de ? "Öffne anynear und richte dort Passwörter ein." : "Open anynear and set up passwords there."
    static let matching = de ? "Passend zu dieser Seite" : "Matching this page"
    static let logins = de ? "Zugänge" : "Logins"
    static let others = de ? "Weitere Zugänge" : "Other logins"
    static let nothing = de ? "Nichts gefunden" : "Nothing found"
    static let search = de ? "Zugänge durchsuchen" : "Search logins"
}
