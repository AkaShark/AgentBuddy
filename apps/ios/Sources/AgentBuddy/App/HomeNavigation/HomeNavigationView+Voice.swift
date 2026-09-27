import SwiftUI

extension HomeNavigationView {
    var homeVoiceLauncher: some View {
        HomeVoiceOrbButton(
            session: voiceRuntime.activeVoiceSession,
            isAvailable: true,
            isStarting: isStartingVoice,
            action: startHomeVoiceSession
        )
        // Match the bottom inset used by `HomeBottomBar` inside
        // `HomeDashboardView.bottomChrome` so the mic button sits on the
        // same horizontal line as the `+` and search pills on the right.
        .padding(.leading, 14)
        .padding(.bottom, 4)
    }

    private func startHomeVoiceSession() {
        guard !isStartingVoice else { return }
        isStartingVoice = true
        actionErrorMessage = nil

        Task {
            do {
                let selectedModel = normalizedPreferredModel()
                let selectedEffort = appState.preferredReasoningEffort.trimmingCharacters(in: .whitespacesAndNewlines)
                voiceRuntime.handoffModel = selectedModel
                voiceRuntime.handoffEffort = selectedEffort.isEmpty ? nil : selectedEffort
                voiceRuntime.handoffFastMode = false
                let voicePermissions = await voicePermissionConfig()
                let voiceKey = try await voiceRuntime.startPinnedLocalVoiceCall(
                    cwd: preferredVoiceWorkingDirectory(),
                    model: selectedModel,
                    approvalPolicy: voicePermissions.approvalPolicy,
                    sandboxMode: voicePermissions.sandboxMode
                )
                await MainActor.run {
                    openRealtimeVoice(voiceKey)
                }
            } catch {
                await MainActor.run {
                    actionErrorMessage = error.localizedDescription
                }
            }
            await MainActor.run {
                isStartingVoice = false
            }
        }
    }

    private func normalizedPreferredModel() -> String? {
        let trimmed = appState.preferredModel.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private func preferredVoiceWorkingDirectory() -> String {
        let current = appState.currentCwd.trimmingCharacters(in: .whitespacesAndNewlines)
        if !current.isEmpty {
            return current
        }

        let stored = UserDefaults.standard.string(forKey: "workDir")?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !stored.isEmpty {
            return stored
        }

        return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?.path ?? "/"
    }

    private func voicePermissionConfig() async -> (
        approvalPolicy: AppAskForApproval?,
        sandboxMode: AppSandboxMode?
    ) {
        let storedThreadId = UserDefaults.standard.string(forKey: VoiceRuntimeController.persistedLocalVoiceThreadIDKey)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let threadKey = storedThreadId.flatMap { threadId -> ThreadKey? in
            guard !threadId.isEmpty else { return nil }
            return ThreadKey(serverId: VoiceRuntimeController.localServerID, threadId: threadId)
        }
        let resolvedThreadKey: ThreadKey?
        if let threadKey {
            resolvedThreadKey = await appModel.hydrateThreadPermissions(for: threadKey, appState: appState)
                ?? threadKey
        } else {
            resolvedThreadKey = nil
        }
        return (
            approvalPolicy: appState.launchApprovalPolicy(for: resolvedThreadKey),
            sandboxMode: appState.launchSandboxMode(for: resolvedThreadKey)
        )
    }
}
