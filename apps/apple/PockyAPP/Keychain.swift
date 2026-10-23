import Foundation
import Security

enum Keychain {
    private static let service = "com.pokecio.app"

    static func read(_ key: String) -> String? {
        var query = base(key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data
        else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func write(_ key: String, _ value: String) {
        let data = Data(value.utf8)
        let query = base(key)
        let update = [kSecValueData as String: data]

        if SecItemUpdate(query as CFDictionary, update as CFDictionary) == errSecItemNotFound {
            var insert = query
            insert[kSecValueData as String] = data
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(insert as CFDictionary, nil)
        }
    }

    static func delete(_ key: String) {
        SecItemDelete(base(key) as CFDictionary)
    }

    private static func base(_ key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
    }
}

enum SessionStore {
    private static let sessionKey = "session"
    private static let databaseKey = "db_key"

    static func loadSession() -> String? {
        Keychain.read(sessionKey)
    }

    static func saveSession(_ session: String) {
        Keychain.write(sessionKey, session)
    }

    static func databasePassphrase() -> String {
        if let existing = Keychain.read(databaseKey) {
            return existing
        }
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let passphrase = Data(bytes).base64EncodedString()
        Keychain.write(databaseKey, passphrase)
        return passphrase
    }

    static func clear() {
        Keychain.delete(sessionKey)
        Keychain.delete(databaseKey)
    }

    static var storePath: String {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("pocky", isDirectory: true).path
    }
}
