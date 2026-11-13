import CryptoKit
import Foundation

/// A login from `vault`; fields like `Login` in the shared code (`passwords/PasswordVault.kt`).
struct VaultLogin: Codable, Identifiable {
    let id: String
    let website: String
    let username: String
    let password: String
    let changed: Int64
    /// `otpauth://` link of the one-time code (ADR 0052).
    let otp: String?
    let passkeys: [VaultPasskey]?

    func with(passkey: VaultPasskey, changed: Int64) -> VaultLogin {
        VaultLogin(id: id, website: website, username: username, password: password, changed: changed, otp: otp,
                   passkeys: (passkeys ?? []) + [passkey])
    }
}

private struct VaultRemoved: Codable {
    let id: String
    let changed: Int64
}

private struct VaultContent: Codable {
    let v: Int
    let logins: [VaultLogin]
    let removed: [VaultRemoved]?
}

/**
 Reads `passwords/vault` from the app group's container (ADR 0039, item 1): AES-256-GCM, 12-byte nonce in front,
 16-byte tag at the end, inside UTF-8 JSON. Changing is only possible in the app; the extension puts new passkeys into
 `passwords/incoming`, and the app takes them over on the next unlock (ADR 0052).
 */
enum PasswordVaultFile {
    static let version = 2

    private static var folder: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.app.anynear")?.appendingPathComponent("passwords")
    }

    /// Logins including `incoming`; with the same identifier the newest change applies.
    static func read(key: Data) -> [VaultLogin]? {
        let own = open(name: "vault", key: key)
        let incoming = open(name: "incoming", key: key)
        if own == nil && incoming == nil { return nil }
        var byId: [String: VaultLogin] = [:]
        var order: [String] = []
        for login in (own ?? []) + (incoming ?? []) {
            if byId[login.id] == nil { order.append(login.id) }
            if (byId[login.id]?.changed ?? Int64.min) <= login.changed { byId[login.id] = login }
        }
        return order.compactMap { byId[$0] }
    }

    /// Put a new or changed login into `incoming`. `false` if an existing file does not match the key.
    static func addIncoming(key: Data, login: VaultLogin) -> Bool {
        guard let folder else { return false }
        let url = folder.appendingPathComponent("incoming")
        var logins: [VaultLogin] = []
        if FileManager.default.fileExists(atPath: url.path) {
            guard let before = open(name: "incoming", key: key) else { return false }
            logins = before.filter { $0.id != login.id }
        }
        logins.append(login)
        let anyExtended = logins.contains { $0.otp != nil || !($0.passkeys ?? []).isEmpty }
        guard let plain = try? JSONEncoder().encode(VaultContent(v: anyExtended ? 2 : 1, logins: logins, removed: nil)),
              let sealed = try? AES.GCM.seal(plain, using: SymmetricKey(data: key)).combined
        else { return false }
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return (try? sealed.write(to: url, options: .atomic)) != nil
    }

    private static func open(name: String, key: Data) -> [VaultLogin]? {
        guard let url = folder?.appendingPathComponent(name),
              let sealed = try? Data(contentsOf: url),
              let box = try? AES.GCM.SealedBox(combined: sealed),
              let plain = try? AES.GCM.open(box, using: SymmetricKey(data: key)),
              let content = try? JSONDecoder().decode(VaultContent.self, from: plain),
              content.v <= version
        else { return nil }
        return content.logins
    }
}

/// Matching like `LoginMatch` in the shared code: same domain or a subdomain of the stored one.
enum LoginMatch {
    static func host(_ website: String) -> String? {
        var s = website.trimmingCharacters(in: .whitespaces).lowercased()
        if let r = s.range(of: "://") { s = String(s[r.upperBound...]) }
        s = String(s.split(separator: "/", omittingEmptySubsequences: false).first ?? "")
        s = String(s.split(separator: "?", omittingEmptySubsequences: false).first ?? "")
        s = String(s.split(separator: "#", omittingEmptySubsequences: false).first ?? "")
        if let at = s.lastIndex(of: "@") { s = String(s[s.index(after: at)...]) }
        s = String(s.split(separator: ":", omittingEmptySubsequences: false).first ?? "")
        while s.hasSuffix(".") { s.removeLast() }
        if s.hasPrefix("www.") { s.removeFirst(4) }
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: ".-"))
        if s.isEmpty || s.hasPrefix(".") || s.contains("..") || s.unicodeScalars.contains(where: { !allowed.contains($0) }) { return nil }
        return s
    }

    static func matches(_ login: VaultLogin, domain: String) -> Bool {
        guard let stored = host(login.website), let asked = host(domain) else { return false }
        return asked == stored || asked.hasSuffix("." + stored)
    }

    static func displayName(_ website: String) -> String {
        host(website) ?? website.trimmingCharacters(in: .whitespaces)
    }

    /// Passkeys for exactly this RP ID, without subdomains (ADR 0052).
    static func passkeys(_ logins: [VaultLogin], rpId: String) -> [(VaultLogin, VaultPasskey)] {
        logins.flatMap { l in (l.passkeys ?? []).filter { $0.rpId.lowercased() == rpId.lowercased() }.map { (l, $0) } }
    }

    /// New passkey for the login with the same website and the same username, otherwise a new login without password.
    static func withPasskey(_ logins: [VaultLogin], passkey: VaultPasskey, newId: String, now: Int64) -> VaultLogin {
        if let same = logins.first(where: { host($0.website) == host(passkey.rpId) && $0.username.lowercased() == passkey.userName.lowercased() }) {
            return same.with(passkey: passkey, changed: max(now, same.changed + 1))
        }
        return VaultLogin(id: newId, website: passkey.rpId, username: passkey.userName, password: "", changed: now, otp: nil, passkeys: [passkey])
    }
}
