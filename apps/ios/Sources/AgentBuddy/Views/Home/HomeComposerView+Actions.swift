import PhotosUI
import SwiftUI
import UIKit

extension HomeComposerView {
    func handleSend() {
        let text = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        let image = attachedImage
        let files = attachedFiles
        guard !text.isEmpty || image != nil || !files.isEmpty else { return }
        guard !isSubmitting else { return }
        guard let project else {
            errorMessage = "Pick a project before sending."
            return
        }

        isSubmitting = true
        errorMessage = nil

        Task {
            defer { isSubmitting = false }
            do {
                guard try await appModel.ensureLocalAuthForThreadStart(serverId: project.serverId) else {
                    return
                }
                inputText = ""
                attachedImage = nil
                attachedFiles = []
                composerSelectionRange = NSRange(location: 0, length: 0)
                isComposerFocused = false

                let pendingModel = appState.preferredModel.trimmingCharacters(in: .whitespacesAndNewlines)
                let modelOverride = pendingModel.isEmpty ? nil : pendingModel
                let agentRuntimeOverride = modelOverride == nil ? nil : appState.preferredAgentRuntimeKind
                let pendingEffort = appState.preferredReasoningEffort.trimmingCharacters(in: .whitespacesAndNewlines)
                let effortOverride = ReasoningEffort(wireValue: pendingEffort.isEmpty ? nil : pendingEffort)
                let launchConfig = AppThreadLaunchConfig(
                    agentRuntimeKind: agentRuntimeOverride,
                    model: modelOverride,
                    approvalPolicy: appState.launchApprovalPolicy(for: nil),
                    sandbox: appState.launchSandboxMode(for: nil),
                    developerInstructions: nil,
                    persistExtendedHistory: true
                )
                let threadKey = try await appModel.client.startThread(
                    serverId: project.serverId,
                    params: launchConfig.threadStartRequest(
                        cwd: project.cwd,
                        dynamicTools: appModel.localGenerativeUiToolSpecs(for: project.serverId)
                    )
                )
                RecentDirectoryStore.shared.record(path: project.cwd, for: project.serverId)
                let preparedAttachment = image.flatMap(ConversationAttachmentSupport.prepareImage)
                var additionalInputs: [AppUserInput] = []
                let mentionsToSend = collectPluginMentionsForSubmission(text)
                pluginMentionSelections = []
                showPluginPopup = false
                activeAtToken = nil
                for mention in mentionsToSend {
                    additionalInputs.append(
                        AppUserInput.mention(name: mention.name, path: mention.path)
                    )
                }
                if let preparedAttachment {
                    additionalInputs.append(preparedAttachment.userInput)
                }
                let payload = AppComposerPayload(
                    text: text,
                    additionalInputs: additionalInputs,
                    fileAttachments: files,
                    approvalPolicy: appState.launchApprovalPolicy(for: threadKey),
                    sandboxPolicy: appState.turnSandboxPolicy(for: threadKey),
                    model: modelOverride,
                    effort: effortOverride,
                    serviceTier: nil
                )
                try await appModel.startTurn(key: threadKey, payload: payload)
                await appModel.refreshThreadSnapshot(key: threadKey)
                onThreadCreated(threadKey)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func startVoiceRecording() {
        Task {
            let granted = await voiceManager.requestMicPermission()
            guard granted else { return }
            voiceManager.startRecording()
        }
    }

    func loadSelectedPhoto(_ item: PhotosPickerItem) async {
        if let data = try? await item.loadTransferable(type: Data.self),
           let image = UIImage(data: data) {
            attachedImage = image
        }
        selectedPhoto = nil
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

    func stopVoiceRecording() {
        guard let serverId = resolvedTranscriptionServerId else {
            voiceManager.cancelRecording()
            return
        }
        Task {
            let auth = try? await appModel.client.authStatus(
                serverId: serverId,
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
}
