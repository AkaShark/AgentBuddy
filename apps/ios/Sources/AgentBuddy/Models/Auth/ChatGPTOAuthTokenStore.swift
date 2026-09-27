import Foundation
import Security

struct ChatGPTOAuthTokenBundle: Codable, Equatable {
    let accessToken: String
    let idToken: String
    let refreshToken: String?
    let accountID: String
    let planType: String?
}

final class ChatGPTOAuthTokenStore {
    static let shared = ChatGPTOAuthTokenStore()

    private let service = "com.akashark.agentbuddy.chatgpt.tokens"
    private let account = "default"
    private let accessibility = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly

    private init() {}

    func load() throws -> ChatGPTOAuthTokenBundle? {
        let query = baseQuery().merging([
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]) { _, new in new }

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        switch status {
        case errSecSuccess:
            guard let data = item as? Data else {
                throw ChatGPTOAuthError.missingStoredTokens
            }
            return try JSONDecoder().decode(ChatGPTOAuthTokenBundle.self, from: data)
        case errSecItemNotFound:
            return nil
        default:
            throw ChatGPTOAuthError.keychain(status)
        }
    }

    func save(_ tokens: ChatGPTOAuthTokenBundle) throws {
        let data = try JSONEncoder().encode(tokens)
        let attributes: [String: Any] = baseQuery().merging([
            kSecAttrAccessible as String: accessibility,
            kSecValueData as String: data
        ]) { _, new in new }

        let status = SecItemAdd(attributes as CFDictionary, nil)
        if status == errSecDuplicateItem {
            let updates: [String: Any] = [
                kSecAttrAccessible as String: accessibility,
                kSecValueData as String: data
            ]
            let updateStatus = SecItemUpdate(baseQuery() as CFDictionary, updates as CFDictionary)
            guard updateStatus == errSecSuccess else {
                throw ChatGPTOAuthError.keychain(updateStatus)
            }
            return
        }

        guard status == errSecSuccess else {
            throw ChatGPTOAuthError.keychain(status)
        }
    }

    func clear() throws {
        let status = SecItemDelete(baseQuery() as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw ChatGPTOAuthError.keychain(status)
        }
    }

    private func baseQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }
}
