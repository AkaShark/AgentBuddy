import SwiftUI

extension ConversationInputBar {
    func applySlashSuggestion(_ command: ComposerSlashCommand) {
        showSlashPopup = false
        activeSlashToken = nil
        slashSuggestions = []
        inputText = ""
        attachedImage = nil
        attachedFiles = []
        isComposerFocused = false
        executeSlashCommand(command, args: nil)
    }

    func executeSlashCommand(_ command: ComposerSlashCommand, args: String?) {
        switch command {
        case .plan:
            onOpenModePicker()
        case .model:
            showModelSelector = true
        case .permissions:
            showPermissionsSheet = true
        case .experimental:
            showExperimentalSheet = true
            Task { await loadExperimentalFeatures() }
        case .skills:
            showSkillsSheet = true
            Task { await loadSkills() }
        case .review:
            Task { await startReview() }
        case .goal:
            Task { await handleGoalCommand(args) }
        case .rename:
            let initialName = args?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if initialName.isEmpty {
                let currentTitle = snapshot.threadPreview.trimmingCharacters(in: .whitespacesAndNewlines)
                renameCurrentThreadTitle = currentTitle.isEmpty ? "Untitled thread" : currentTitle
                renameDraft = ""
                showRenamePrompt = true
            } else {
                Task { await renameThread(initialName) }
            }
        case .new:
            appState.showServerPicker = true
        case .fork:
            Task { await forkConversation() }
        case .resume:
            onResumeSessions?(snapshot.threadKey.serverId)
        }
    }

    func parseSlashCommandInvocation(_ text: String) -> (command: ComposerSlashCommand, args: String?)? {
        let firstLine = text.split(separator: "\n", maxSplits: 1, omittingEmptySubsequences: false).first.map(String.init) ?? ""
        let trimmed = firstLine.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("/") else { return nil }
        let commandAndArgs = trimmed.dropFirst()
        let commandName = commandAndArgs.split(separator: " ", maxSplits: 1, omittingEmptySubsequences: true).first.map(String.init) ?? ""
        guard let command = ComposerSlashCommand(rawCommand: commandName) else { return nil }
        let args = commandAndArgs.split(separator: " ", maxSplits: 1, omittingEmptySubsequences: true).dropFirst().first.map(String.init)
        return (command, args)
    }

    private func startReview() async {
        do {
            _ = try await appModel.client.startReview(
                serverId: snapshot.threadKey.serverId,
                params: AppStartReviewRequest(
                    threadId: snapshot.threadKey.threadId,
                    target: .uncommittedChanges,
                    delivery: "inline"
                )
            )
        } catch {
            slashErrorMessage = error.localizedDescription
        }
    }

    func renameThread(_ newName: String) async {
        do {
            try await appModel.renameThread(
                serverId: snapshot.threadKey.serverId,
                threadId: snapshot.threadKey.threadId,
                title: newName
            )
            showRenamePrompt = false
            renameCurrentThreadTitle = ""
            renameDraft = ""
        } catch {
            slashErrorMessage = error.localizedDescription
        }
    }

    private func forkConversation() async {
        do {
            let nextKey = try await appModel.client.forkThread(
                serverId: snapshot.threadKey.serverId,
                params: AppThreadLaunchConfig(
                    agentRuntimeKind: pendingAgentRuntimeKindOverride,
                    model: pendingModelOverride,
                    approvalPolicy: appState.launchApprovalPolicy(for: snapshot.threadKey),
                    sandbox: appState.launchSandboxMode(for: snapshot.threadKey),
                    developerInstructions: nil,
                    persistExtendedHistory: true
                ).threadForkRequest(threadId: snapshot.threadKey.threadId, cwdOverride: workDir)
            )
            appModel.store.setActiveThread(key: nextKey)
            await appModel.refreshThreadSnapshot(key: nextKey)
            let nextCwd = workDir.trimmingCharacters(in: .whitespacesAndNewlines)
            if !nextCwd.isEmpty {
                workDir = nextCwd
                appState.currentCwd = nextCwd
            }
            onOpenConversation?(nextKey)
        } catch {
            slashErrorMessage = error.localizedDescription
        }
    }
}
