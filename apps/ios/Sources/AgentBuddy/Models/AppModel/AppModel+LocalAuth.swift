import Foundation
import UIKit

enum LocalAccountLoginFlowError: LocalizedError {
    case localServerUnavailable
    case remoteServer
    case loginDidNotAttach

    var errorDescription: String? {
        switch self {
        case .localServerUnavailable:
            return "Local Codex isn't running. ChatGPT login requires the local bridge."
        case .remoteServer:
            return "ChatGPT login is only available for the local server."
        case .loginDidNotAttach:
            return "ChatGPT login completed, but the local account did not attach."
        }
    }
}

extension AppModel {
    private static let localAuthRestoreRetryDelays: [Duration] = [
        .seconds(1),
        .seconds(2),
        .seconds(4)
    ]

    func loginLocalChatGPTAccount(serverId: String) async throws {
        guard let server = snapshot?.serverSnapshot(for: serverId) else {
            throw LocalAccountLoginFlowError.localServerUnavailable
        }
        guard server.isLocal else {
            throw LocalAccountLoginFlowError.remoteServer
        }

        let tokens = try await ChatGPTOAuth.login()
        _ = try await client.loginAccount(
            serverId: serverId,
            params: .chatgptAuthTokens(
                accessToken: tokens.accessToken,
                chatgptAccountId: tokens.accountID,
                chatgptPlanType: tokens.planType
            )
        )
        await refreshSnapshot()
    }

    func ensureLocalAuthForThreadStart(serverId: String) async throws -> Bool {
        guard let server = snapshot?.serverSnapshot(for: serverId) else {
            return true
        }
        guard server.isLocal else {
            return true
        }
        guard server.account == nil else {
            return true
        }

        if await restoreStoredLocalAuthIfNeeded(serverId: serverId, reason: "startThread") {
            return true
        }

        do {
            try await loginLocalChatGPTAccount(serverId: serverId)
        } catch ChatGPTOAuthError.cancelled {
            return false
        }

        guard snapshot?.serverSnapshot(for: serverId)?.account != nil else {
            throw LocalAccountLoginFlowError.loginDidNotAttach
        }
        return true
    }

    @discardableResult
    func restoreStoredLocalAuthIfNeeded(serverId: String, reason: String) async -> Bool {
        guard let server = snapshot?.serverSnapshot(for: serverId), server.isLocal else {
            return false
        }
        guard server.account == nil else {
            return false
        }
        let storedApiKey = await loadStoredLocalApiKey()?.trimmingCharacters(in: .whitespacesAndNewlines)
        let storedTokens = await loadStoredLocalChatGPTTokens()
        guard storedTokens != nil || storedApiKey?.isEmpty == false else {
            return false
        }

        LLog.info(
            "auth",
            "restoring stored local auth before local session operation",
            fields: [
                "serverId": serverId,
                "reason": reason
            ]
        )
        await restoreStoredLocalAuthState(serverId: serverId)
        return snapshot?.serverSnapshot(for: serverId)?.account != nil
    }

    func restoreStoredLocalAuthState(serverId: String) async {
        let storedApiKey: String?
        if let rawApiKey = await loadStoredLocalApiKey() {
            let trimmedApiKey = rawApiKey.trimmingCharacters(in: .whitespacesAndNewlines)
            storedApiKey = trimmedApiKey.isEmpty ? nil : trimmedApiKey
        } else {
            storedApiKey = nil
        }
        let storedTokens = await loadStoredLocalChatGPTTokens()

        guard storedApiKey != nil || storedTokens != nil else { return }

        for attempt in 0...Self.localAuthRestoreRetryDelays.count {
            if let storedTokens,
               await restoreStoredLocalChatGPTAuth(
                serverId: serverId,
                storedTokens: storedTokens
               ) {
                await refreshSnapshot()
                return
            }

            if let storedApiKey {
                OpenAIApiKeyStore.shared.applyToEnvironment()
                if await loginStoredLocalApiKeyAuth(serverId: serverId, apiKey: storedApiKey) {
                    await refreshSnapshot()
                    return
                }
            }

            guard attempt < Self.localAuthRestoreRetryDelays.count else { break }
            let delay = Self.localAuthRestoreRetryDelays[attempt]
            LLog.warn(
                "auth",
                "stored local auth restore did not stick; retrying after startup delay",
                fields: [
                    "serverId": serverId,
                    "attempt": attempt + 1,
                    "delaySeconds": delay.components.seconds
                ]
            )
            try? await Task.sleep(for: delay)
        }

        guard storedApiKey != nil else { return }
        OpenAIApiKeyStore.shared.applyToEnvironment()
        guard await reconnectLocalServerForStoredApiKeyRestore(serverId: serverId) else { return }
        if let storedApiKey, await loginStoredLocalApiKeyAuth(serverId: serverId, apiKey: storedApiKey) {
            await refreshSnapshot()
        }
    }

    func restoreMissingLocalAuthStateIfNeeded() async {
        guard let snapshot else { return }
        let localServerIds = snapshot.servers
            .filter { $0.isLocal && $0.account == nil }
            .map(\.serverId)

        guard !localServerIds.isEmpty else { return }

        for serverId in localServerIds {
            await restoreStoredLocalAuthState(serverId: serverId)
        }
        await refreshSnapshot()
    }

    private func loadStoredLocalApiKey() async -> String? {
        do {
            return try OpenAIApiKeyStore.shared.load()
        } catch let error as NSError where isTransientLocalKeychainFailure(error) {
            for delay in [0.5, 1.0, 2.0] {
                LLog.warn(
                    "auth",
                    "local OpenAI API key unavailable until keychain unlock; retrying",
                    fields: ["delaySeconds": delay]
                )
                try? await Task.sleep(for: .seconds(delay))
                do {
                    return try OpenAIApiKeyStore.shared.load()
                } catch let retryError as NSError where isTransientLocalKeychainFailure(retryError) {
                    continue
                } catch {
                    LLog.error(
                        "auth",
                        "loading stored local OpenAI API key failed",
                        fields: ["error": String(describing: error)]
                    )
                    return nil
                }
            }
            return nil
        } catch {
            LLog.error(
                "auth",
                "loading stored local OpenAI API key failed",
                fields: ["error": error.localizedDescription]
            )
            return nil
        }
    }

    private func isTransientLocalKeychainFailure(_ error: NSError) -> Bool {
        guard error.domain == NSOSStatusErrorDomain else { return false }
        return error.code == Int(errSecInteractionNotAllowed)
            || error.code == Int(errSecNotAvailable)
    }

    private func restoreStoredLocalChatGPTAuth(
        serverId: String,
        storedTokens: ChatGPTOAuthTokenBundle
    ) async -> Bool {
        let refreshedTokens = try? await ChatGPTOAuth.refreshStoredTokens(
            previousAccountID: nil,
            storedTokens: storedTokens
        )
        if let refreshedTokens,
           await loginStoredLocalChatGPTAuth(serverId: serverId, tokens: refreshedTokens) {
            return true
        }

        if await loginStoredLocalChatGPTAuth(serverId: serverId, tokens: storedTokens) {
            return true
        }

        guard refreshedTokens == nil else {
            return false
        }

        try? await Task.sleep(for: .seconds(2))
        if let retriedRefresh = try? await ChatGPTOAuth.refreshStoredTokens(
            previousAccountID: nil,
            storedTokens: storedTokens
        ) {
            return await loginStoredLocalChatGPTAuth(serverId: serverId, tokens: retriedRefresh)
        }
        return false
    }

    private func loginStoredLocalApiKeyAuth(serverId: String, apiKey: String) async -> Bool {
        do {
            _ = try await client.loginAccount(
                serverId: serverId,
                params: .apiKey(apiKey: apiKey)
            )
            lastError = nil
            return true
        } catch {
            LLog.warn(
                "auth",
                "restoring stored local API key auth failed",
                fields: [
                    "serverId": serverId,
                    "error": error.localizedDescription
                ]
            )
            return false
        }
    }

    private func reconnectLocalServerForStoredApiKeyRestore(serverId: String) async -> Bool {
        guard let localServer = snapshot?.servers.first(where: { $0.serverId == serverId && $0.isLocal })
            ?? snapshot?.servers.first(where: \.isLocal) else {
            return false
        }

        LLog.warn(
            "auth",
            "reconnecting local server to re-inherit stored API key environment",
            fields: ["serverId": serverId]
        )

        serverBridge.disconnectServer(serverId: localServer.serverId)

        do {
            _ = try await serverBridge.connectLocalServer(
                serverId: localServer.serverId,
                displayName: resolvedLocalServerDisplayName(),
                host: "127.0.0.1",
                port: 0
            )
            return true
        } catch {
            LLog.warn(
                "auth",
                "reconnecting local server for stored API key restore failed",
                fields: [
                    "serverId": serverId,
                    "error": error.localizedDescription
                ]
            )
            return false
        }
    }

    private func loadStoredLocalChatGPTTokens() async -> ChatGPTOAuthTokenBundle? {
        do {
            return try ChatGPTOAuthTokenStore.shared.load()
        } catch let error as ChatGPTOAuthError where error.isTransientKeychainAvailabilityFailure {
            for delay in [0.5, 1.0, 2.0] {
                LLog.warn(
                    "auth",
                    "local ChatGPT auth tokens unavailable until keychain unlock; retrying",
                    fields: ["delaySeconds": delay]
                )
                try? await Task.sleep(for: .seconds(delay))
                do {
                    return try ChatGPTOAuthTokenStore.shared.load()
                } catch let retryError as ChatGPTOAuthError where retryError.isTransientKeychainAvailabilityFailure {
                    continue
                } catch {
                    LLog.error(
                        "auth",
                        "loading stored local ChatGPT auth tokens failed",
                        fields: ["error": String(describing: error)]
                    )
                    return nil
                }
            }
            return nil
        } catch {
            LLog.error(
                "auth",
                "loading stored local ChatGPT auth tokens failed",
                fields: ["error": error.localizedDescription]
            )
            return nil
        }
    }

    private func loginStoredLocalChatGPTAuth(
        serverId: String,
        tokens: ChatGPTOAuthTokenBundle
    ) async -> Bool {
        do {
            _ = try await client.loginAccount(
                serverId: serverId,
                params: .chatgptAuthTokens(
                    accessToken: tokens.accessToken,
                    chatgptAccountId: tokens.accountID,
                    chatgptPlanType: tokens.planType
                )
            )
            return true
        } catch {
            lastError = error.localizedDescription
            return false
        }
    }
}
