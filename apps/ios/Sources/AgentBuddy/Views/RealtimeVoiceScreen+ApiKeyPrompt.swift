import SwiftUI

extension RealtimeVoiceScreen {
    var realtimeApiKeyPrompt: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.sm) {
            Text("Realtime needs an API key")
                .buddyText(.heading)
                .foregroundStyle(primaryTextColor)

            Text("Enter your OpenAI API key for this device. AgentBuddy will store it in the local Codex environment as OPENAI_API_KEY.")
                .buddyText(.label, weight: .regular)
                .foregroundStyle(secondaryTextColor)
                .fixedSize(horizontal: false, vertical: true)

            SecureField("sk-...", text: $apiKey)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .buddyText(.code)
                .foregroundStyle(primaryTextColor)
                .tint(AgentBuddyTheme.focus)
                .padding(.horizontal, BuddySpacing.md)
                .frame(minHeight: BuddySize.control)
                .background(AgentBuddyTheme.background, in: RoundedRectangle(cornerRadius: BuddyRadius.button, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: BuddyRadius.button, style: .continuous)
                        .strokeBorder(AgentBuddyTheme.borderControl, lineWidth: 1)
                )

            if let apiKeyError, !apiKeyError.isEmpty {
                BuddyBanner(tone: .danger, message: Text(verbatim: apiKeyError))
            }

            BuddyButton(
                isSavingApiKey ? "Saving…" : "Save API Key",
                kind: .primary,
                isLoading: isSavingApiKey
            ) {
                saveApiKeyAndRetry()
            }
            .disabled(trimmedApiKey.isEmpty || isSavingApiKey)
        }
        .padding(BuddySpacing.lg)
        .frame(maxWidth: 420)
        .background(AgentBuddyTheme.surface, in: RoundedRectangle(cornerRadius: BuddyRadius.card, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: BuddyRadius.card, style: .continuous)
                .strokeBorder(AgentBuddyTheme.border, lineWidth: 1)
        )
    }

    private func saveApiKeyAndRetry() {
        guard !trimmedApiKey.isEmpty, !isSavingApiKey else { return }
        guard server?.isLocal == true else {
            apiKeyError = "API keys are only saved on the local server."
            return
        }

        isSavingApiKey = true
        apiKeyError = nil

        Task {
            var apiKeySaved = false
            var authError: String?
            do {
                try OpenAIApiKeyStore.shared.save(trimmedApiKey)
                if case .apiKey? = server?.account {
                    _ = try await appModel.client.logoutAccount(serverId: threadKey.serverId)
                }
                await voiceRuntime.stopActiveVoiceSession()
                try await appModel.restartLocalServer()
                let persisted = OpenAIApiKeyStore.shared.hasStoredKey
                guard persisted else {
                    authError = "API key did not persist locally."
                    throw CancellationError()
                }
                apiKeySaved = true
                authError = nil
            } catch {
                apiKeySaved = false
                if authError == nil {
                    authError = error.localizedDescription
                }
            }

            if apiKeySaved {
                await MainActor.run {
                    isRetryingAfterAuthSave = true
                }
                await voiceRuntime.stopActiveVoiceSession()
                try? await Task.sleep(for: .milliseconds(150))
                do {
                    try await voiceRuntime.startVoiceOnThread(threadKey)
                } catch {
                    await MainActor.run {
                        isRetryingAfterAuthSave = false
                        apiKeyError = error.localizedDescription
                    }
                }
            }

            await MainActor.run {
                isSavingApiKey = false
                hasCheckedAuth = true
                if apiKeySaved {
                    hasStoredApiKey = true
                    apiKey = ""
                }
                if !apiKeySaved {
                    isRetryingAfterAuthSave = false
                    apiKeyError = authError ?? "Failed to save API key"
                }
            }
        }
    }
}
