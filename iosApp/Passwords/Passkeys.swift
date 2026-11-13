import CryptoKit
import Foundation

/**
 One-time codes and passkeys for the password extension (ADR 0052), same as `passwords/Otp.kt` and
 `passwords/Passkeys.kt` in the shared code. Compiled for app and extension; only Foundation and CryptoKit.
 */
enum Totp {
    /// Current code for an `otpauth://totp/…` link; `nil` if the link is not valid.
    static func code(_ uri: String, now: Date = Date()) -> String? {
        guard let parts = URLComponents(string: uri), parts.scheme?.lowercased() == "otpauth", parts.host?.lowercased() == "totp" else { return nil }
        var params: [String: String] = [:]
        for item in parts.queryItems ?? [] { params[item.name.lowercased()] = item.value ?? "" }
        guard let secret = params["secret"].flatMap(base32) else { return nil }
        let digits = Int(params["digits"] ?? "") ?? 6
        let period = Int(params["period"] ?? "") ?? 30
        guard (6...8).contains(digits), (1...300).contains(period) else { return nil }
        var counter = UInt64(now.timeIntervalSince1970) / UInt64(period)
        let message = withUnsafeBytes(of: &counter) { Data($0.reversed()) }
        let key = SymmetricKey(data: secret)
        let hash: [UInt8]
        switch (params["algorithm"] ?? "SHA1").uppercased() {
        case "SHA256": hash = Array(HMAC<SHA256>.authenticationCode(for: message, using: key))
        case "SHA512": hash = Array(HMAC<SHA512>.authenticationCode(for: message, using: key))
        case "SHA1": hash = Array(HMAC<Insecure.SHA1>.authenticationCode(for: message, using: key))
        default: return nil
        }
        let offset = Int(hash[hash.count - 1] & 0x0f)
        let binary = (UInt32(hash[offset] & 0x7f) << 24) | (UInt32(hash[offset + 1]) << 16) | (UInt32(hash[offset + 2]) << 8) | UInt32(hash[offset + 3])
        let mod = UInt32(pow(10.0, Double(digits)))
        let text = String(binary % mod)
        return String(repeating: "0", count: digits - text.count) + text
    }

    private static let alphabet = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567")

    static func base32(_ text: String) -> Data? {
        let clean = text.uppercased().filter { $0 != " " && $0 != "-" && $0 != "=" }
        var out = Data()
        var buffer = 0
        var bits = 0
        for c in clean {
            guard let v = alphabet.firstIndex(of: c) else { return nil }
            buffer = (buffer << 5) | v
            bits += 5
            if bits >= 8 {
                bits -= 8
                out.append(UInt8((buffer >> bits) & 0xff))
                buffer &= (1 << bits) - 1
            }
        }
        return out.count >= 10 ? out : nil
    }
}

/// A passkey in `vault`; fields like `Passkey` in the shared code.
struct VaultPasskey: Codable {
    let credentialId: String
    let rpId: String
    let userHandle: String
    let userName: String
    let privateKey: String
    let created: Int64
}

/// WebAuthn authenticator like `Webauthn` in the shared code: ES256, attestation „none“, AAGUID of zeros, counter 0.
enum Webauthn {
    private static let up: UInt8 = 0x01, uv: UInt8 = 0x04, be: UInt8 = 0x08, bs: UInt8 = 0x10, at: UInt8 = 0x40

    struct Registration {
        let passkey: VaultPasskey
        let credentialId: Data
        let attestationObject: Data
    }

    static func create(rpId: String, userHandle: Data, userName: String, now: Date = Date()) -> Registration {
        let key = P256.Signing.PrivateKey()
        var credentialId = Data(count: 16)
        _ = credentialId.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 16, $0.baseAddress!) }
        let point = key.publicKey.x963Representation
        let cose = Cbor.map([
            (Cbor.int(1), Cbor.int(2)),
            (Cbor.int(3), Cbor.int(-7)),
            (Cbor.int(-1), Cbor.int(1)),
            (Cbor.int(-2), Cbor.bytes(point.subdata(in: 1..<33))),
            (Cbor.int(-3), Cbor.bytes(point.subdata(in: 33..<65))),
        ])
        var authData = rpIdHash(rpId)
        authData.append(up | uv | be | bs | at)
        authData.append(Data(count: 4))
        authData.append(Data(count: 16))
        authData.append(contentsOf: [UInt8(credentialId.count >> 8), UInt8(credentialId.count & 0xff)])
        authData.append(credentialId)
        authData.append(cose)
        let attestation = Cbor.map([
            (Cbor.text("fmt"), Cbor.text("none")),
            (Cbor.text("attStmt"), Cbor.map([])),
            (Cbor.text("authData"), Cbor.bytes(authData)),
        ])
        let passkey = VaultPasskey(
            credentialId: b64(credentialId),
            rpId: rpId,
            userHandle: b64(userHandle),
            userName: userName,
            privateKey: b64(key.derRepresentation),
            created: Int64(now.timeIntervalSince1970 * 1000)
        )
        return Registration(passkey: passkey, credentialId: credentialId, attestationObject: attestation)
    }

    /// Signature for signing in: authenticatorData and signature over authenticatorData and clientDataHash.
    static func assert(_ passkey: VaultPasskey, clientDataHash: Data) -> (authenticatorData: Data, signature: Data)? {
        guard let der = unb64(passkey.privateKey), let key = try? P256.Signing.PrivateKey(derRepresentation: der) else { return nil }
        var authData = rpIdHash(passkey.rpId)
        authData.append(up | uv | be | bs)
        authData.append(Data(count: 4))
        guard let signature = try? key.signature(for: authData + clientDataHash) else { return nil }
        return (authData, signature.derRepresentation)
    }

    private static func rpIdHash(_ rpId: String) -> Data { Data(SHA256.hash(data: Data(rpId.utf8))) }

    static func b64(_ data: Data) -> String {
        data.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    static func unb64(_ text: String) -> Data? {
        var s = text.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while s.count % 4 != 0 { s += "=" }
        return Data(base64Encoded: s)
    }
}

/// The little bit of CBOR for attestation and COSE key, like `Cbor` in the shared code.
private enum Cbor {
    static func int(_ v: Int) -> Data { v >= 0 ? head(0, UInt64(v)) : head(1, UInt64(-1 - v)) }
    static func bytes(_ b: Data) -> Data { head(2, UInt64(b.count)) + b }
    static func text(_ s: String) -> Data { head(3, UInt64(s.utf8.count)) + Data(s.utf8) }

    static func map(_ entries: [(Data, Data)]) -> Data {
        entries.reduce(head(5, UInt64(entries.count))) { $0 + $1.0 + $1.1 }
    }

    private static func head(_ major: UInt8, _ n: UInt64) -> Data {
        let m = major << 5
        switch n {
        case ..<24: return Data([m | UInt8(n)])
        case ..<0x100: return Data([m | 24, UInt8(n)])
        case ..<0x10000: return Data([m | 25, UInt8(n >> 8), UInt8(n & 0xff)])
        default: return Data([m | 26, UInt8((n >> 24) & 0xff), UInt8((n >> 16) & 0xff), UInt8((n >> 8) & 0xff), UInt8(n & 0xff)])
        }
    }
}
