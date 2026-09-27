import SwiftUI
import PhotosUI
import UIKit
import os

struct ConversationInputBar: View {
    @Environment(AppState.self) var appState
    @Environment(AppModel.self) var appModel
    let snapshot: ConversationComposerSnapshot
    @AppStorage("workDir") var workDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?.path ?? "/"
    @AppStorage("fastMode") private var fastMode = false

    let onSend: (String, UIImage?, [ComposerFileAttachment], [SkillMentionSelection], [PluginMentionSelection]) -> Void
    let onFileSearch: (String) async throws -> [FileSearchResult]
    var bottomInset: CGFloat = 0
    let showModeChip: Bool
    let onOpenModePicker: () -> Void
    let onOpenConversation: ((ThreadKey) -> Void)?
    let onResumeSessions: ((String) -> Void)?

    @Binding var inputText: String
    @Binding var attachedImage: UIImage?
    @State var attachedFiles: [ComposerFileAttachment] = []
    @State private var showAttachMenu = false
    @State private var showPhotoPicker = false
    @State private var showCamera = false
    @State private var showFileImporter = false
    @State var selectedPhoto: PhotosPickerItem?
    @State var showSlashPopup = false
    @State var activeSlashToken: ComposerSlashQueryContext?
    @State var slashSuggestions: [ComposerSlashCommand] = []
    @State var showFilePopup = false
    @State var activeAtToken: ComposerTokenContext?
    @State var showSkillPopup = false
    @State var activeDollarToken: ComposerTokenContext?
    @State var fileSearchLoading = false
    @State var fileSearchError: String?
    @State var fileSuggestions: [FileSearchResult] = []
    @State var fileSearchGeneration = 0
    @State var fileSearchTask: Task<Void, Never>?
    @State var popupRefreshTask: Task<Void, Never>?
    @State var showModelSelector = false
    @State var showPermissionsSheet = false
    @State var showExperimentalSheet = false
    @State var showSkillsSheet = false
    @State var showRenamePrompt = false
    @State var renameCurrentThreadTitle = ""
    @State var renameDraft = ""
    @State var slashErrorMessage: String?
    @State var experimentalFeatures: [ExperimentalFeature] = []
    @State var experimentalFeaturesLoading = false
    @State var skills: [SkillMetadata] = []
    @State var skillsLoading = false
    @State var mentionSkillPathsByName: [String: String] = [:]
    @State var hasAttemptedSkillMentionLoad = false
    @State var pluginCacheByCwd: [String: [PluginSummary]] = [:]
    @State var pluginUnsupportedCwds: Set<String> = []
    @State var pluginLoadingCwds: Set<String> = []
    @State var pluginMentionSelections: [PluginMentionSelection] = []
    @State var voiceManager = VoiceTranscriptionManager()
    @State var showMicPermissionAlert = false
    @State private var hasLoggedFirstFocus = false
    @State private var hasLoggedKeyboardShown = false
    @State var isComposerFocused = false
    @State var composerSelectionRange = NSRange(location: 0, length: 0)

    var pendingUserInputRequest: PendingUserInputRequest? {
        guard let request = snapshot.pendingUserInputRequest else { return nil }
        return appState.isPendingUserInputDismissed(id: request.id) ? nil : request
    }

    var pendingModelOverride: String? {
        let trimmed = appState.selectedModel.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    var pendingAgentRuntimeKindOverride: AgentRuntimeKind? {
        pendingModelOverride == nil ? nil : appState.selectedAgentRuntimeKind
    }

    private var isTurnActive: Bool {
        snapshot.isTurnActive
    }

    var activeTurnId: String? {
        guard let value = snapshot.activeTurnId?.trimmingCharacters(in: .whitespacesAndNewlines),
              !value.isEmpty else {
            return nil
        }
        return value
    }

    private var popupState: ConversationComposerPopupState {
        if showSlashPopup {
            return .slash(slashSuggestions)
        }
        if showFilePopup {
            return .file(
                loading: fileSearchLoading,
                error: fileSearchError,
                suggestions: fileSuggestions,
                plugins: pluginSuggestions
            )
        }
        if showSkillPopup {
            return .skill(loading: skillsLoading, suggestions: skillSuggestions)
        }
        return .none
    }

    var body: some View {
        ConversationComposerModalCoordinator(
            snapshot: snapshot,
            experimentalFeatures: experimentalFeatures,
            experimentalFeaturesLoading: experimentalFeaturesLoading,
            skills: skills,
            skillsLoading: skillsLoading,
            showAttachMenu: $showAttachMenu,
            showPhotoPicker: $showPhotoPicker,
            showCamera: $showCamera,
            showFileImporter: $showFileImporter,
            selectedPhoto: $selectedPhoto,
            attachedImage: $attachedImage,
            showModelSelector: $showModelSelector,
            showPermissionsSheet: $showPermissionsSheet,
            showExperimentalSheet: $showExperimentalSheet,
            showSkillsSheet: $showSkillsSheet,
            showRenamePrompt: $showRenamePrompt,
            renameCurrentThreadTitle: $renameCurrentThreadTitle,
            renameDraft: $renameDraft,
            slashErrorMessage: $slashErrorMessage,
            showMicPermissionAlert: $showMicPermissionAlert,
            onOpenSettings: openAppSettings,
            onLoadSelectedPhoto: loadSelectedPhoto,
            onLoadSelectedFile: { url in
                guard let picked = ConversationAttachmentSupport.loadPickedFile(at: url) else { return }
                applyPickedFile(picked)
            },
            onLoadExperimentalFeatures: loadExperimentalFeatures,
            onIsExperimentalFeatureEnabled: { featureId, fallback in
                isExperimentalFeatureEnabled(featureId, fallback: fallback)
            },
            onSetExperimentalFeature: { featureName, enabled in
                await setExperimentalFeature(named: featureName, enabled: enabled)
            },
            onLoadSkills: { forceReload, showErrors in
                await loadSkills(forceReload: forceReload, showErrors: showErrors)
            },
            onRenameThread: renameThread
        ) {
            composerSurface
        }
        .onChange(of: inputText) { _, next in
            scheduleComposerPopupRefresh(for: next)
        }
        .onChange(of: snapshot.composerPrefillRequest?.id) { _, _ in
            guard let prefill = snapshot.composerPrefillRequest else { return }
            inputText = prefill.text
            composerSelectionRange = NSRange(location: (prefill.text as NSString).length, length: 0)
            attachedImage = nil
            attachedFiles = []
            hideComposerPopups()
            appModel.clearComposerPrefill(id: prefill.id)
        }
        .onChange(of: isComposerFocused) { _, focused in
            if focused {
                guard !hasLoggedFirstFocus else { return }
                hasLoggedFirstFocus = true
                os_signpost(.event, log: conversationViewSignpostLog, name: "ComposerFirstFocus")
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardDidShowNotification)) { _ in
            guard !hasLoggedKeyboardShown else { return }
            hasLoggedKeyboardShown = true
            os_signpost(.event, log: conversationViewSignpostLog, name: "KeyboardShown")
        }
        #if targetEnvironment(macCatalyst)
        .onReceive(NotificationCenter.default.publisher(for: .agentBuddyCommandSendComposer)) { _ in
            handleSend()
        }
        #endif
        .onDisappear {
            if voiceManager.isRecording { voiceManager.cancelRecording() }
            popupRefreshTask?.cancel()
            popupRefreshTask = nil
            fileSearchTask?.cancel()
            fileSearchTask = nil
        }
    }

    private var composerSurface: some View {
        VStack(spacing: 0) {
            ConversationComposerContentView(
                attachedImage: attachedImage,
                attachedFiles: attachedFiles,
                collaborationMode: snapshot.collaborationMode,
                activePlanProgress: snapshot.activePlanProgress,
                pendingUserInputRequest: pendingUserInputRequest,
                hasPendingPlanImplementation: snapshot.pendingPlanImplementationPrompt != nil,
                activeTaskSummary: snapshot.activeTaskSummary,
                queuedFollowUps: snapshot.queuedFollowUps,
                pluginMentions: pluginMentionSelections,
                goal: snapshot.goal,
                goalActions: makeGoalCardActions(),
                rateLimits: snapshot.rateLimits,
                contextPercent: contextPercent(),
                isTurnActive: isTurnActive,
                showModeChip: showModeChip,
                voiceManager: voiceManager,
                showAttachMenu: $showAttachMenu,
                onClearAttachment: clearAttachment,
                onRemoveFileAttachment: removeFileAttachment,
                onRespondToPendingUserInput: respondToPendingUserInput,
                onDismissPendingUserInput: dismissPendingUserInput,
                onImplementPlan: { Task { await implementPlan() } },
                onDismissPlanImplementation: dismissPlanImplementationPrompt,
                onSteerQueuedFollowUp: steerQueuedFollowUp,
                onDeleteQueuedFollowUp: deleteQueuedFollowUp,
                onRemovePluginMention: removePluginMention,
                onPasteImage: { image in attachedImage = image },
                onOpenModePicker: onOpenModePicker,
                onSendText: handleSend,
                onStopRecording: stopVoiceRecording,
                onStartRecording: startVoiceRecording,
                onInterrupt: interruptActiveTurn,
                inputText: $inputText,
                isComposerFocused: $isComposerFocused,
                composerSelectionRange: $composerSelectionRange
            )
            .overlay(alignment: .bottom) {
                ConversationComposerPopupOverlayView(
                    state: popupState,
                    onApplySlashSuggestion: applySlashSuggestion,
                    onApplyFileSuggestion: applyFileSuggestion,
                    onApplySkillSuggestion: applySkillSuggestion,
                    onApplyPluginSuggestion: applyPluginSuggestion
                )
            }
        }
        .dropDestination(for: URL.self) { urls, _ in
            guard let picked = urls.lazy.compactMap({ ConversationAttachmentSupport.loadPickedFile(at: $0) }).first else {
                return false
            }
            applyPickedFile(picked)
            return true
        }
        .dropDestination(for: Data.self) { items, _ in
            guard let image = items.lazy.compactMap({ UIImage(data: $0) }).first else {
                return false
            }
            attachedImage = image
            return true
        }
    }
}
