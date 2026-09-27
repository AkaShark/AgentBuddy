import SwiftUI
import PhotosUI
import UIKit

extension ConversationInputBar {
    func contextPercent() -> Int64? {
        guard let contextWindow = snapshot.modelContextWindow else { return nil }
        let baseline: Int64 = 12_000
        guard contextWindow > baseline else { return 0 }
        let totalTokens = snapshot.contextTokensUsed ?? baseline
        let effectiveWindow = contextWindow - baseline
        let usedTokens = max(0, totalTokens - baseline)
        let remainingTokens = max(0, effectiveWindow - usedTokens)
        let percent = Int64((Double(remainingTokens) / Double(effectiveWindow) * 100).rounded())
        return min(max(percent, 0), 100)
    }

    func clearAttachment() {
        attachedImage = nil
    }

    func removeFileAttachment(_ file: ComposerFileAttachment) {
        attachedFiles.removeAll { $0 == file }
    }

    func applyPickedFile(_ picked: PickedComposerFile) {
        switch picked {
        case .image(let image):
            attachedImage = image
        case .file(let file):
            if !attachedFiles.contains(file) {
                attachedFiles.append(file)
            }
        }
    }

    func respondToPendingUserInput(_ answers: [String: [String]]) {
        guard let pendingUserInputRequest else { return }
        let payload: [PendingUserInputAnswer] = pendingUserInputRequest.questions.compactMap { question in
            guard let selectedAnswers = answers[question.id], !selectedAnswers.isEmpty else { return nil }
            return PendingUserInputAnswer(questionId: question.id, answers: selectedAnswers)
        }
        Task {
            do {
                try await appModel.store.respondToUserInput(
                    requestId: pendingUserInputRequest.id,
                    answers: payload
                )
            } catch {
                slashErrorMessage = error.localizedDescription
            }
        }
    }

    func steerQueuedFollowUp(_ preview: AppQueuedFollowUpPreview) {
        Task {
            do {
                try await appModel.store.steerQueuedFollowUp(
                    key: snapshot.threadKey,
                    previewId: preview.id
                )
            } catch {
                slashErrorMessage = error.localizedDescription
            }
        }
    }

    func deleteQueuedFollowUp(_ preview: AppQueuedFollowUpPreview) {
        Task {
            do {
                try await appModel.store.deleteQueuedFollowUp(
                    key: snapshot.threadKey,
                    previewId: preview.id
                )
            } catch {
                slashErrorMessage = error.localizedDescription
            }
        }
    }

    func handleSend() {
        let text = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        let image = attachedImage
        let files = attachedFiles
        guard !text.isEmpty || image != nil || !files.isEmpty else { return }
        if let request = snapshot.pendingUserInputRequest {
            appState.dismissPendingUserInput(id: request.id)
        }
        if image == nil,
           files.isEmpty,
           let invocation = parseSlashCommandInvocation(text) {
            inputText = ""
            attachedImage = nil
            attachedFiles = []
            hideComposerPopups()
            isComposerFocused = false
            executeSlashCommand(invocation.command, args: invocation.args)
            return
        }
        // Offline: keep the draft and attachments; every send path (button,
        // return key, expanded composer, Mac shortcut) ends up here.
        guard snapshot.isConnected else { return }
        inputText = ""
        attachedImage = nil
        attachedFiles = []
        hideComposerPopups()
        isComposerFocused = false
        let skillMentions = collectSkillMentionsForSubmission(text)
        let pluginMentions = collectPluginMentionsForSubmission(text)
        pluginMentionSelections = []
        onSend(text, image, files, skillMentions, pluginMentions)
    }

    func dismissPendingUserInput() {
        guard let request = snapshot.pendingUserInputRequest else { return }
        appState.dismissPendingUserInput(id: request.id)
    }

    func startVoiceRecording() {
        Task {
            let granted = await voiceManager.requestMicPermission()
            guard granted else {
                showMicPermissionAlert = true
                return
            }
            voiceManager.startRecording()
        }
    }

    func stopVoiceRecording() {
        Task {
            let auth = try? await appModel.client.authStatus(
                serverId: snapshot.threadKey.serverId,
                params: AuthStatusRequest(includeToken: true, refreshToken: false)
            )
            if let text = await voiceManager.stopAndTranscribe(
                authMethod: auth?.authMethod,
                authToken: auth?.authToken
            ), !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                insertTranscriptAtCursor(text)
                DispatchQueue.main.async {
                    isComposerFocused = true
                }
            }
        }
    }

    private func insertTranscriptAtCursor(_ transcript: String) {
        let insertion = transcript.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !insertion.isEmpty else { return }

        let nsText = inputText as NSString
        let textLength = nsText.length
        let location = min(max(composerSelectionRange.location, 0), textLength)
        let length = min(max(composerSelectionRange.length, 0), textLength - location)
        let range = NSRange(location: location, length: length)
        let replacement = composerInsertionText(insertion, in: nsText, replacing: range)
        let updated = nsText.replacingCharacters(in: range, with: replacement)
        inputText = updated
        let cursor = (updated as NSString).length - ((nsText.length - range.location - range.length))
        composerSelectionRange = NSRange(location: cursor, length: 0)
    }

    func interruptActiveTurn() {
        guard !isStopping else { return }
        guard let activeTurnId else {
            LLog.warn("conversation", "interrupt requested but no activeTurnId")
            return
        }
        isStopping = true
        let threadKey = snapshot.threadKey
        LLog.info(
            "conversation",
            "interrupt turn",
            fields: ["serverId": threadKey.serverId, "threadId": threadKey.threadId, "turnId": activeTurnId]
        )
        Task {
            do {
                _ = try await appModel.client.interruptTurn(
                    serverId: threadKey.serverId,
                    params: AppInterruptTurnRequest(
                        threadId: threadKey.threadId,
                        turnId: activeTurnId
                    )
                )
                LLog.info("conversation", "interrupt turn rpc ok")
            } catch {
                LLog.warn("conversation", "interrupt turn failed", fields: ["error": String(describing: error)])
                isStopping = false
                slashErrorMessage = error.localizedDescription
            }
        }
    }

    func openAppSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    func loadSelectedPhoto(_ item: PhotosPickerItem) async {
        if let data = try? await item.loadTransferable(type: Data.self),
           let image = UIImage(data: data) {
            attachedImage = image
        }
        selectedPhoto = nil
    }

    func dismissPlanImplementationPrompt() {
        appModel.store.dismissPlanImplementationPrompt(key: snapshot.threadKey)
    }

    func implementPlan() async {
        do {
            try await appModel.store.implementPlan(key: snapshot.threadKey)
        } catch {
            slashErrorMessage = error.localizedDescription
        }
    }
}
