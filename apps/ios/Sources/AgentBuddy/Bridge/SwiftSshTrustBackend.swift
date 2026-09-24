import Foundation
import Security

/// Keychain-backed implementation of the Rust `TerminalSshTrustBackend`
/// callback interface. Stores per-host SHA-256 fingerprints under a
/// dedicated service so they don't collide with the SSH credential
/// keychain entries (`SSHCredentialStore`).
final class SwiftSshTrustBackend: TerminalSshTrustBackend, @unchecked Sendable {
    static let shared = SwiftSshTrustBackend()

    private let service = "com.akashark.agentbuddy.ssh.trust"

    private init() {}

    /// Only `errSecItemNotFound` means "not pinned"; any other Keychain
    /// failure is reported as unavailable so Rust refuses the connect instead
    /// of pinning whatever key the server presents.
    func read(host: String, port: UInt16) -> SshTrustLookup {
        let account = account(host: host, port: port)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        switch status {
        case errSecSuccess:
            guard let data = item as? Data,
                  let value = String(data: data, encoding: .utf8),
                  !value.isEmpty else {
                return .unavailable(detail: "pinned host key for \(account) is unreadable")
            }
            return .pinned(fingerprint: value)
        case errSecItemNotFound:
            return .notPinned
        default:
            return .unavailable(detail: "Keychain read failed (OSStatus \(status))")
        }
    }

    func write(host: String, port: UInt16, fingerprint: String) {
        let account = account(host: host, port: port)
        guard let data = fingerprint.data(using: .utf8) else { return }
        let addAttributes: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: data,
        ]
        let addStatus = SecItemAdd(addAttributes as CFDictionary, nil)
        if addStatus == errSecDuplicateItem {
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: service,
                kSecAttrAccount as String: account,
            ]
            let updates: [String: Any] = [
                kSecValueData as String: data,
                kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            ]
            let updateStatus = SecItemUpdate(query as CFDictionary, updates as CFDictionary)
            if updateStatus != errSecSuccess {
                LLog.error(
                    "ssh",
                    "SSH host-key pin Keychain update failed",
                    fields: ["account": account, "osStatus": Int(updateStatus)]
                )
            }
        } else if addStatus != errSecSuccess {
            LLog.error(
                "ssh",
                "SSH host-key pin Keychain add failed",
                fields: ["account": account, "osStatus": Int(addStatus)]
            )
        }
    }

    func remove(host: String, port: UInt16) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account(host: host, port: port),
        ]
        SecItemDelete(query as CFDictionary)
    }

    private func account(host: String, port: UInt16) -> String {
        "\(host.lowercased()):\(port)"
    }
}

/// Process-wide SSH host-key trust shared with Rust. Registered once at
/// startup so every SSH connect path (not only the terminal) pins a host key
/// on first use and refuses a changed one.
enum SshHostKeyTrust {
    static let store = TerminalSshTrustStore(backend: SwiftSshTrustBackend.shared)

    static func register() {
        setSshTrustStore(store: store)
    }

    /// The host-key mismatch the user can act on, carried by a typed Rust SSH
    /// error (`ClientError` / `TerminalError`): a changed key ("Trust New
    /// Key") or an unreadable saved key ("Forget Saved Host Key"). Nil for any
    /// other error.
    static func promptableHostKey(in error: Error) -> AppSshHostKeyMismatch? {
        let mismatch: AppSshHostKeyMismatch
        switch error {
        case let ClientError.SshHostKeyMismatch(value):
            mismatch = value
        case let TerminalError.SshHostKeyMismatch(value):
            mismatch = value
        default:
            return nil
        }
        return mismatch.isPromptable ? mismatch : nil
    }

    /// Pin exactly the fingerprint the user approved, replacing any previous
    /// pin, so a retry connects only if the server still presents that key.
    static func trust(_ mismatch: AppSshHostKeyMismatch) {
        store.pin(host: mismatch.host, port: mismatch.port, fingerprint: mismatch.fingerprint)
    }

    /// Remove the saved pin that could not be read, so a retry treats the
    /// host as new instead of being refused forever.
    static func forget(_ mismatch: AppSshHostKeyMismatch) {
        store.unpin(host: mismatch.host, port: mismatch.port)
    }
}

extension AppSshHostKeyMismatch {
    /// Changed key (offer "Trust New Key") or unreadable saved key (offer
    /// "Forget Saved Host Key"); both are shown by `sshHostKeyChangeAlert`.
    var isPromptable: Bool {
        kind == .changed || kind == .trustStoreUnavailable
    }
}
