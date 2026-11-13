import Foundation
import LocalAuthentication
import Security

/**
 Password key `pv` in the keychain (ADR 0039, item 2): only with a device passcode set up, only on this
 iPhone, reading only after Face ID, Touch ID or device passcode. It is in the first keychain group of the
 entitlements (`…app.anynear.shared`), which app and password extension share. Compiled for both targets.
 */
enum PasswordKeychain {
    private static let service = "passwords"
    private static let account = "pv"

    /// Reasons when `unlock` yields no key; same as `IosPasswordKeychain` in the shared code.
    static let cancelled = "cancelled"
    static let noDeviceLock = "no-device-lock"
    static let keyLost = "key-lost"

    static func deviceSecure() -> Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
    }

    /// Does the entry exist? Prompts for nothing: a protected entry responds with "confirmation required".
    static func hasKey() -> Bool {
        let context = LAContext()
        context.interactionNotAllowed = true
        var query = baseQuery()
        query[kSecUseAuthenticationContext as String] = context
        query[kSecReturnData as String] = false
        let status = SecItemCopyMatching(query as CFDictionary, nil)
        return status == errSecSuccess || status == errSecInteractionNotAllowed
    }

    /// Storing needs no confirmation; without a device passcode iOS refuses.
    static func store(_ key: Data) -> Bool {
        remove()
        guard let access = SecAccessControlCreateWithFlags(nil, kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly, .userPresence, nil) else {
            return false
        }
        var query = baseQuery()
        query[kSecValueData as String] = key
        query[kSecAttrAccessControl as String] = access
        return SecItemAdd(query as CFDictionary, nil) == errSecSuccess
    }

    static func remove() {
        SecItemDelete(baseQuery() as CFDictionary)
    }

    /// First the system's prompt, then read with the same confirmation (no second prompt).
    static func unlock(reason: String, done: @escaping (Data?, String) -> Void) {
        guard deviceSecure() else { return done(nil, noDeviceLock) }
        guard hasKey() else { return done(nil, keyLost) }
        let context = LAContext()
        context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { ok, _ in
            guard ok else { return done(nil, cancelled) }
            var query = baseQuery()
            query[kSecUseAuthenticationContext as String] = context
            query[kSecReturnData as String] = true
            query[kSecMatchLimit as String] = kSecMatchLimitOne
            var result: AnyObject?
            let status = SecItemCopyMatching(query as CFDictionary, &result)
            if status == errSecSuccess, let data = result as? Data {
                done(data, "")
            } else {
                done(nil, status == errSecItemNotFound ? keyLost : cancelled)
            }
        }
    }

    private static func baseQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}
