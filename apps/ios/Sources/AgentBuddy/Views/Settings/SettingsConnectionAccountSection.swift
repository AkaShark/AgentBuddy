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
            HStack(spacing: BuddySpacing.sm) {
                BuddyIconTile(
                    content: .symbol(authIndicator.symbol),
                    fill: authIndicator.fill,
                    foreground: authIndicator.foreground,
                    size: 40
                )
                VStack(alignment: .leading, spacing: 2) {
                    Text(authTitle)
                        .buddyText(.body, weight: .medium)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                    if let sub = authSubtitle {
                        Text(sub)
                            .buddyText(.caption)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                    }
                }
                Spacer(minLength: BuddySpacing.xs)
                if server.isLocal, server.account != nil {
                    Button("Logout") {
                        Task { await logout() }
                    }
                    .buttonStyle(BuddyButtonStyle(kind: .destructive, fullWidth: false))
                }
            }
            .padding(.vertical, BuddySpacing.xxs)
            .accessibilityElement(children: .contain)
            .settingsMintRow()

            if server.isLocal, hasStoredApiKey {
                savedNotice("Local OpenAI API key is saved.")
            }

            if server.isLocal, hasStoredBaseURL {
                savedNotice("OpenAI-compatible base URL is saved.")
            }

            // [baozi-fork] ChatGPT OAuth login button removed: replaying a
            // first-party ChatGPT subscription login in a third-party client risks
            // OpenAI's terms. Use a BYO API key (below) for the local environment.

            if server.isLocal, allowsLocalEnvApiKey {
                VStack(alignment: .leading, spacing: BuddySpacing.xs) {
                    if hasStoredApiKey {
                        Text("OpenAI API key saved in the local environment.")
                            .settingsMintFooter()
                    } else if isChatGPTAccount {
                        Text("Save an API key in the local Codex environment.")
                            .settingsMintFooter()
                    }
                    SecureField("sk-...", text: $apiKey)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .settingsMintInputField()
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
                    .buttonStyle(BuddyButtonStyle(kind: .secondary, fullWidth: false))
                    .disabled(apiKey.trimmingCharacters(in: .whitespaces).isEmpty || isAuthWorking)
                }
                .padding(.vertical, BuddySpacing.xs)
                .settingsMintRow()

                VStack(alignment: .leading, spacing: BuddySpacing.xs) {
                    if hasStoredBaseURL {
                        Text("Custom OpenAI-compatible endpoint saved for the local Codex server.")
                            .settingsMintFooter()
                    } else {
                        Text("Optional OpenAI-compatible endpoint for local models.")
                            .settingsMintFooter()
                    }
                    TextField("http://host:port/v1", text: $openAIBaseURL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .settingsMintInputField()
                    HStack(spacing: BuddySpacing.xs) {
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
                        .buttonStyle(BuddyButtonStyle(kind: .secondary, fullWidth: false))
                        .disabled(openAIBaseURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isAuthWorking)
                        if hasStoredBaseURL {
                            Button("Clear Base URL") {
                                Task {
                                    isAuthWorking = true
                                    await clearBaseURL()
                                    isAuthWorking = false
                                }
                            }
                            .buttonStyle(BuddyButtonStyle(kind: .destructive, fullWidth: false))
                            .disabled(isAuthWorking)
                        }
                    }
                }
                .padding(.vertical, BuddySpacing.xs)
                .settingsMintRow()
            }

            if let authError {
                HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
                    Image(systemName: "exclamationmark.circle")
                        .accessibilityHidden(true)
                    Text(authError)
                        .buddyText(.label, weight: .regular)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .foregroundStyle(AgentBuddyTheme.danger)
                .padding(.vertical, BuddySpacing.xxs)
                .settingsMintRow()
            }
        } header: {
            Text("Account")
                .settingsMintHeader()
        }
        .task(id: server.serverId) {
            refreshStoredCredentialFlags()
            await refreshAuthStatusIfNeeded()
        }
    }

    private func savedNotice(_ message: LocalizedStringKey) -> some View {
        Label {
            Text(message)
                .buddyText(.label, weight: .regular)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
        } icon: {
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(AgentBuddyTheme.success)
                .accessibilityHidden(true)
        }
        .padding(.vertical, BuddySpacing.xxs)
        .settingsMintRow()
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

    /// Account state tile: the icon and the title text carry the state, the
    /// colour only reinforces it.
    private var authIndicator: (symbol: String, fill: Color, foreground: Color) {
        switch server.account {
        case .chatgpt?:
            return ("person.crop.circle.badge.checkmark", AgentBuddyTheme.successSurface, AgentBuddyTheme.success)
        case .apiKey?:
            return ("key.fill", AgentBuddyTheme.successSurface, AgentBuddyTheme.success)
        case nil where server.isLocal && (hasStoredChatGPTTokens || hasStoredApiKey):
            return ("arrow.triangle.2.circlepath", AgentBuddyTheme.warningSurface, AgentBuddyTheme.warning)
        case nil:
            return ("person.crop.circle.badge.xmark", AgentBuddyTheme.surfaceSoft, AgentBuddyTheme.textSecondary)
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
