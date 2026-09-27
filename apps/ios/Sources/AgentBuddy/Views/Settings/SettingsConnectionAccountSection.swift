import SwiftUI

struct SettingsConnectionAccountSection: View {
    @Environment(AppModel.self) private var appModel
    let server: AppServerSnapshot
    @State private var apiKey = ""
    @State private var openAIBaseURL = ""
    @State private var isAuthWorking = false
    @State private var authError: String?
    @State private var hasStoredApiKey = OpenAIApiKeyStore.shared.hasStoredKey
    @State private var hasStoredBaseURL = OpenAIApiKeyStore.shared.hasStoredBaseURL
    @State private var hasStoredChatGPTTokens = false

    var body: some View {
        Section {
            HStack(spacing: 12) {
                Circle()
                    .fill(authColor)
                    .frame(width: 10, height: 10)
                VStack(alignment: .leading, spacing: 2) {
                    Text(authTitle)
                        .agentBuddyFont(.subheadline)
                        .foregroundColor(AgentBuddyTheme.textPrimary)
                    if let sub = authSubtitle {
                        Text(sub)
                            .agentBuddyFont(.caption)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                    }
                }
                Spacer()
                if server.isLocal, server.account != nil {
                    Button("Logout") {
                        Task { await logout() }
                    }
                    .agentBuddyFont(.caption)
                    .foregroundColor(AgentBuddyTheme.danger)
                }
            }
            .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))

            if server.isLocal, hasStoredApiKey {
                Text("Local OpenAI API key is saved.")
                    .agentBuddyFont(.caption)
                    .foregroundColor(AgentBuddyTheme.accent)
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
            }

            if server.isLocal, hasStoredBaseURL {
                Text("OpenAI-compatible base URL is saved.")
                    .agentBuddyFont(.caption)
                    .foregroundColor(AgentBuddyTheme.accent)
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
            }

            // [baozi-fork] ChatGPT OAuth login button removed: replaying a
            // first-party ChatGPT subscription login in a third-party client risks
            // OpenAI's terms. Use a BYO API key (below) for the local environment.

            if server.isLocal, allowsLocalEnvApiKey {
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 6) {
                        if hasStoredApiKey {
                            Text("OpenAI API key saved in the local environment.")
                                .agentBuddyFont(.caption)
                                .foregroundColor(AgentBuddyTheme.textSecondary)
                        } else if isChatGPTAccount {
                            Text("Save an API key in the local Codex environment.")
                                .agentBuddyFont(.caption)
                                .foregroundColor(AgentBuddyTheme.textSecondary)
                        }
                        SecureField("sk-...", text: $apiKey)
                            .agentBuddyFont(.footnote)
                            .foregroundColor(AgentBuddyTheme.textPrimary)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }
                    Button {
                        let key = apiKey.trimmingCharacters(in: .whitespaces)
                        guard !key.isEmpty else { return }
                        Task {
                            isAuthWorking = true
                            await saveApiKey(key)
                            isAuthWorking = false
                        }
                    } label: {
                        Text(LocalizedStringKey(hasStoredApiKey ? "Update API Key" : "Save API Key"))
                    }
                    .agentBuddyFont(.caption)
                    .foregroundColor(AgentBuddyTheme.accent)
                    .disabled(apiKey.trimmingCharacters(in: .whitespaces).isEmpty || isAuthWorking)
                }
                .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))

                VStack(alignment: .leading, spacing: 8) {
                    if hasStoredBaseURL {
                        Text("Custom OpenAI-compatible endpoint saved for the local Codex server.")
                            .agentBuddyFont(.caption)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                    } else {
                        Text("Optional OpenAI-compatible endpoint for local models.")
                            .agentBuddyFont(.caption)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                    }
                    HStack(spacing: 8) {
                        TextField("http://host:port/v1", text: $openAIBaseURL)
                            .agentBuddyFont(.footnote)
                            .foregroundColor(AgentBuddyTheme.textPrimary)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.URL)
                        Button {
                            let baseURL = openAIBaseURL.trimmingCharacters(in: .whitespacesAndNewlines)
                            Task {
                                isAuthWorking = true
                                await saveBaseURL(baseURL)
                                isAuthWorking = false
                            }
                        } label: {
                            Text(LocalizedStringKey(hasStoredBaseURL ? "Update Base URL" : "Save Base URL"))
                        }
                        .agentBuddyFont(.caption)
                        .foregroundColor(AgentBuddyTheme.accent)
                        .disabled(openAIBaseURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isAuthWorking)
                    }
                    if hasStoredBaseURL {
                        Button("Clear Base URL") {
                            Task {
                                isAuthWorking = true
                                await clearBaseURL()
                                isAuthWorking = false
                            }
                        }
                        .agentBuddyFont(.caption)
                        .foregroundColor(AgentBuddyTheme.danger)
                        .disabled(isAuthWorking)
                    }
                }
                .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
            }

            if let authError {
                Text(authError)
                    .agentBuddyFont(.caption)
                    .foregroundColor(AgentBuddyTheme.danger)
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
            }
        } header: {
            Text("Account")
                .foregroundColor(AgentBuddyTheme.textSecondary)
        }
        .task(id: server.serverId) {
            refreshStoredCredentialFlags()
            await refreshAuthStatusIfNeeded()
        }
    }

    private var allowsLocalEnvApiKey: Bool {
        server.isLocal
    }

    private var isChatGPTAccount: Bool {
        if case .chatgpt? = server.account {
            return true
        }
        return false
    }

    private var hasStoredLocalCredentials: Bool {
        hasStoredApiKey || hasStoredChatGPTTokens
    }

    private var authColor: Color {
        switch server.account {
        case .chatgpt?:
            return AgentBuddyTheme.accent
        case .apiKey?:
            return Color(hex: "#00AAFF")
        case nil where server.isLocal && hasStoredChatGPTTokens:
            return AgentBuddyTheme.accent.opacity(0.7)
        case nil where server.isLocal && hasStoredApiKey:
            return Color(hex: "#00AAFF").opacity(0.7)
        case nil:
            return AgentBuddyTheme.textMuted
        }
    }

    private var authTitle: String {
        switch server.account {
        case .chatgpt(let email, _)?:
            return email.isEmpty ? "ChatGPT" : email
        case .apiKey?:
            return String(localized: "API Key")
        case nil where server.isLocal && hasStoredChatGPTTokens:
            return "ChatGPT"
        case nil where server.isLocal && hasStoredApiKey:
            return String(localized: "API Key")
        case nil:
            return String(localized: "Not logged in")
        }
    }

    private var authSubtitle: String? {
        switch server.account {
        case .chatgpt?:
            return String(localized: "ChatGPT account")
        case .apiKey?:
            return String(localized: "OpenAI API key")
        case nil where server.isLocal && hasStoredChatGPTTokens:
            return String(localized: "Stored locally; restoring session")
        case nil where server.isLocal && hasStoredApiKey:
            return String(localized: "Saved locally; refreshing local account")
        case nil:
            return nil
        }
    }

    private func loginWithChatGPT() async {
        guard server.isLocal else {
            authError = "Settings login is only available for the local server."
            return
        }
        do {
            authError = nil
            try await appModel.loginLocalChatGPTAccount(serverId: server.serverId)
        } catch ChatGPTOAuthError.cancelled {
            return
        } catch {
            authError = error.localizedDescription
        }
    }

    private func refreshStoredCredentialFlags() {
        hasStoredApiKey = OpenAIApiKeyStore.shared.hasStoredKey
        hasStoredBaseURL = OpenAIApiKeyStore.shared.hasStoredBaseURL
        do {
            hasStoredChatGPTTokens = try ChatGPTOAuthTokenStore.shared.load() != nil
        } catch let error as ChatGPTOAuthError where error.isTransientKeychainAvailabilityFailure {
            hasStoredChatGPTTokens = false
        } catch {
            hasStoredChatGPTTokens = false
        }
    }

    private func refreshAuthStatusIfNeeded() async {
        guard server.isLocal, server.account == nil else { return }
        guard hasStoredLocalCredentials else { return }
        await appModel.restoreStoredLocalAuthState(serverId: server.serverId)
        await refreshAccount()
    }

    private func refreshAccount() async {
        do {
            _ = try await appModel.client.refreshAccount(
                serverId: server.serverId,
                params: AppRefreshAccountRequest(refreshToken: false)
            )
            await appModel.refreshSnapshot()
            refreshStoredCredentialFlags()
            authError = nil
        } catch {
            authError = error.localizedDescription
        }
    }

    private func saveApiKey(_ key: String) async {
        guard server.isLocal else {
            authError = "API keys can only be saved for the local server."
            return
        }
        do {
            authError = nil
            try OpenAIApiKeyStore.shared.save(key)
            if case .apiKey? = server.account {
                _ = try await appModel.client.logoutAccount(serverId: server.serverId)
            }
            try await appModel.restartLocalServer()
            refreshStoredCredentialFlags()
            guard hasStoredApiKey else {
                authError = "API key did not persist locally."
                return
            }
        } catch {
            authError = error.localizedDescription
        }
    }

    private func saveBaseURL(_ rawBaseURL: String) async {
        guard server.isLocal else {
            authError = "Base URL can only be saved for the local server."
            return
        }
        guard let baseURL = normalizedOpenAIBaseURL(rawBaseURL) else {
            authError = "Enter a valid http or https base URL."
            return
        }
        do {
            authError = nil
            try OpenAIApiKeyStore.shared.saveBaseURL(baseURL)
            try await appModel.restartLocalServer()
            refreshStoredCredentialFlags()
            guard hasStoredBaseURL else {
                authError = "Base URL did not persist locally."
                return
            }
            openAIBaseURL = ""
        } catch {
            authError = error.localizedDescription
        }
    }

    private func clearBaseURL() async {
        guard server.isLocal else {
            authError = "Base URL can only be cleared for the local server."
            return
        }
        do {
            authError = nil
            try OpenAIApiKeyStore.shared.clearBaseURL()
            try await appModel.restartLocalServer()
            refreshStoredCredentialFlags()
            openAIBaseURL = ""
        } catch {
            authError = error.localizedDescription
        }
    }

    private func normalizedOpenAIBaseURL(_ rawValue: String) -> String? {
        let trimmed = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed),
              let scheme = url.scheme?.lowercased(),
              scheme == "http" || scheme == "https",
              url.host != nil else {
            return nil
        }
        return trimmed.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }

    private func logout() async {
        guard server.isLocal else {
            authError = "Settings logout is only available for the local server."
            return
        }
        do {
            try? ChatGPTOAuthTokenStore.shared.clear()
            try? OpenAIApiKeyStore.shared.clear()
            _ = try await appModel.client.logoutAccount(serverId: server.serverId)
            try await appModel.restartLocalServer()
            refreshStoredCredentialFlags()
            authError = nil
        } catch {
            authError = error.localizedDescription
        }
    }
}
