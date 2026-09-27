import SwiftUI

extension RealtimeVoiceScreen {
    var realtimeApiKeyPrompt: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Realtime needs an API key")
                .font(AgentBuddyFont.styled(.headline, weight: .semibold))
                .foregroundColor(primaryTextColor)

            Text("Enter your OpenAI API key for this device. AgentBuddy will store it in the local Codex environment as OPENAI_API_KEY.")
                .font(AgentBuddyFont.styled(.caption))
                .foregroundColor(secondaryTextColor)
                .fixedSize(horizontal: false, vertical: true)

            SecureField("sk-...", text: $apiKey)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .font(AgentBuddyFont.monospaced(.body))
                .foregroundColor(primaryTextColor)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(controlFillColor)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(promptStrokeColor.opacity(1.75), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 14))

            if let apiKeyError, !apiKeyError.isEmpty {
                Text(apiKeyError)
                    .font(AgentBuddyFont.styled(.caption))
                    .foregroundColor(AgentBuddyTheme.danger)
                    .fixedSize(horizontal: false, vertical: true)
            }

            apiKeySaveButton
        }
        .padding(18)
        .frame(maxWidth: 420)
        .background(promptFillColor)
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(promptStrokeColor, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 24))
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

    @ViewBuilder
    private var apiKeySaveButton: some View {
        Button {
            saveApiKeyAndRetry()
        } label: {
            apiKeySaveButtonLabel
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(controlFillColor)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(promptStrokeColor, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .disabled(trimmedApiKey.isEmpty || isSavingApiKey)
        .opacity(trimmedApiKey.isEmpty || isSavingApiKey ? 0.55 : 1)
    }

    private var apiKeySaveButtonLabel: some View {
        HStack(spacing: 10) {
            if isSavingApiKey {
                ProgressView()
                    .tint(primaryTextColor)
                    .scaleEffect(0.85)
            }
            Text(isSavingApiKey ? "Saving…" : "Save API Key")
                .font(AgentBuddyFont.styled(.subheadline, weight: .semibold))
        }
        .foregroundColor(primaryTextColor)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
    }
}
