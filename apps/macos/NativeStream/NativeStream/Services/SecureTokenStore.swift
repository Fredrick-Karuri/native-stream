// SecureTokenStore.swift
//
// Stores the hosted-server API token via Keychain, not
// UserDefaults like SettingsStore — the token is a secret, everything else
// in SettingsStore isn't. Mirrors SecureTokenStore.kt on Android: kept as a
// separate type/file so "this field is encrypted" is visible structurally
// rather than a convention to remember per-property.

import Foundation
import Security

final class SecureTokenStore {

    private let service: String
    private let account = "api_token"

    /// Defaults to the app's bundle identifier so this doesn't collide with
    /// other apps' Keychain items on the same Mac. Injectable for tests.
    init(service: String = Bundle.main.bundleIdentifier ?? "com.nativestream.mac") {
        self.service = service
    }

    /// nil when unset — same convention as SettingsStore.serverURLString
    /// post-HOST-010, so callers don't need two different "is this set"
    /// checks depending on which store a value came from.
    func getAPIToken() -> String? {
        var query = baseQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func setAPIToken(_ token: String) {
        let data = Data(token.utf8)

        if getAPIToken() != nil {
            let update: [String: Any] = [kSecValueData as String: data]
            SecItemUpdate(baseQuery() as CFDictionary, update as CFDictionary)
        } else {
            var addQuery = baseQuery()
            addQuery[kSecValueData as String] = data
            SecItemAdd(addQuery as CFDictionary, nil)
        }
    }

    func clearAPIToken() {
        SecItemDelete(baseQuery() as CFDictionary)
    }

    private func baseQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}
