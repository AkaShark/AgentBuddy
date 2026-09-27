import SwiftUI
import PhotosUI
import UIKit
import os

/// Composer variant for the home screen. When a project is selected, typing
/// and hitting send creates a new thread on (project.serverId, project.cwd)
/// and submits the initial turn. User stays on home — the new thread appears
/// in the task list and streams in place.
struct HomeComposerView: View {
    let project: AppProject?
    let transcriptionServerId: String?
    let onThreadCreated: (ThreadKey) -> Void
    /// Fires when the composer becomes "active" (keyboard up, text/image
    /// entered, or voice recording/transcribing) or returns to idle.
    var onActiveChange: ((Bool) -> Void)? = nil
    /// When true, the composer requests keyboard focus the moment it
    /// appears. Used when the view is revealed by tapping `+`.
    var autoFocus: Bool = false

    @Environment(AppModel.self) var appModel
    @Environment(AppState.self) var appState

    @State var inputText = ""
    @State var attachedImage: UIImage?
    @State var attachedFiles: [ComposerFileAttachment] = []
    @State private var showAttachMenu = false
    @State private var showPhotoPicker = false
    @State private var showCamera = false
    @State private var showFileImporter = false
    @State var selectedPhoto: PhotosPickerItem?
    @State var voiceManager = VoiceTranscriptionManager()
    @State var isSubmitting = false
    @State var errorMessage: String?
    @State var pluginCacheByCwd: [String: [PluginSummary]] = [:]
    @State var pluginUnsupportedCwds: Set<String> = []
    @State var pluginLoadingCwds: Set<String> = []
    @State var pluginMentionSelections: [PluginMentionSelection] = []
    @State var activeAtToken: ComposerTokenContext?
    @State var showPluginPopup = false
    @State var popupRefreshTask: Task<Void, Never>?
    /// Plain `@State`, not `@FocusState`: the composer's text view is a
    /// UIKit `UITextView` wrapped in a UIViewRepresentable, not a SwiftUI
    /// focusable view. Using `@FocusState` without a matching `.focused()`
    /// modifier causes SwiftUI's focus manager to immediately revert any
    /// programmatic `true` back to `false`, which made the keyboard close
    /// the moment it opened.
    @State var isComposerFocused: Bool = false
    @State var composerSelectionRange = NSRange(location: 0, length: 0)

    private var isDisabled: Bool { project == nil }
    var resolvedTranscriptionServerId: String? {
        project?.serverId ?? transcriptionServerId
    }

    private var attachSheetDetentHeight: CGFloat {
        let showsCamera = !AgentBuddyPlatform.isCatalyst
        let count = 2 + (showsCamera ? 1 : 0)
        return count >= 3 ? 300 : 240
    }

    private var isActive: Bool {
        isComposerFocused
            || !inputText.isEmpty
            || attachedImage != nil
            || !attachedFiles.isEmpty
            || voiceManager.isRecording
            || voiceManager.isTranscribing
    }

    var body: some View {
        VStack(spacing: 0) {
            if let errorMessage {
                BuddyBanner(
                    tone: .warning,
                    message: Text(verbatim: errorMessage),
                    actionTitle: "Dismiss",
                    action: {
                        self.errorMessage = nil
                        isComposerFocused = false
                    }
                )
                .padding(.horizontal, BuddySpacing.md)
                .padding(.bottom, BuddySpacing.xs)
            }

            ConversationComposerContentView(
                attachedImage: attachedImage,
                attachedFiles: attachedFiles,
                collaborationMode: .default,
                activePlanProgress: nil,
                pendingUserInputRequest: nil,
                hasPendingPlanImplementation: false,
                activeTaskSummary: nil,
                queuedFollowUps: [],
                pluginMentions: pluginMentionSelections,
                rateLimits: nil,
                contextPercent: nil,
                isTurnActive: false,
                isSubmitting: isSubmitting,
                placeholder: "What should AgentBuddy do? One sentence is enough.",
                showModeChip: false,
                voiceManager: voiceManager,
                allowsVoiceInput: project != nil,
                showAttachMenu: $showAttachMenu,
                onClearAttachment: { attachedImage = nil },
                onRemoveFileAttachment: { file in
                    attachedFiles.removeAll { $0 == file }
                },
                onRespondToPendingUserInput: { _ in },
                onSteerQueuedFollowUp: { _ in },
                onDeleteQueuedFollowUp: { _ in },
                onRemovePluginMention: removePluginMention,
                onPasteImage: { image in attachedImage = image },
                onOpenModePicker: {},
                onSendText: handleSend,
                onStopRecording: stopVoiceRecording,
                onStartRecording: startVoiceRecording,
                onInterrupt: {},
                inputText: $inputText,
                isComposerFocused: Binding(
                    get: { isComposerFocused },
                    set: { isComposerFocused = $0 }
                ),
                composerSelectionRange: $composerSelectionRange
            )
            .overlay(alignment: .bottom) {
                if showPluginPopup, project != nil {
                    HomePluginAutocompletePopup(
                        plugins: filteredPluginSuggestions,
                        onSelect: applyPluginSuggestion
                    )
                }
            }
        }
        .onChange(of: inputText) { _, newValue in
            scheduleHomePopupRefresh(for: newValue)
        }
        .onChange(of: isActive) { _, active in
            onActiveChange?(active)
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
        .sheet(isPresented: $showAttachMenu) {
            ConversationComposerAttachSheet(
                onPickPhotoLibrary: {
                    showAttachMenu = false
                    showPhotoPicker = true
                },
                onChooseFile: {
                    showAttachMenu = false
                    showFileImporter = true
                },
                onTakePhoto: AgentBuddyPlatform.isCatalyst ? nil : {
                    showAttachMenu = false
                    showCamera = true
                }
            )
            .presentationDetents([.height(attachSheetDetentHeight)])
            .buddySheetStyle()
            .presentationDragIndicator(.visible)
        }
        .photosPicker(isPresented: $showPhotoPicker, selection: $selectedPhoto, matching: .images)
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: ConversationAttachmentSupport.supportedFileContentTypes,
            allowsMultipleSelection: false
        ) { result in
            guard case let .success(urls) = result,
                  let url = urls.first else { return }
            guard let picked = ConversationAttachmentSupport.loadPickedFile(at: url) else { return }
            applyPickedFile(picked)
        }
        .onChange(of: selectedPhoto) { _, item in
            guard let item else { return }
            Task { await loadSelectedPhoto(item) }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraView(image: $attachedImage)
                .ignoresSafeArea()
        }
        .task {
            // Focus as early as possible so the keyboard rises in parallel
            // with the glass-morph spring — the two animations then feel
            // like one fluid motion. A tiny 40ms yield lets the view land
            // in the window tree; the UIViewRepresentable picks up focus on
            // its next `updateUIView` pass. Re-issue once after the spring
            // settles as a safety net for edge cases where the first pass
            // fired before the window attachment.
            guard autoFocus else { return }
            try? await Task.sleep(nanoseconds: 40_000_000)
            isComposerFocused = true
            try? await Task.sleep(nanoseconds: 400_000_000)
            if !isComposerFocused {
                isComposerFocused = true
            }
        }
    }
}

private struct HomePluginAutocompletePopup: View {
    let plugins: [PluginSummary]
    let onSelect: (PluginSummary) -> Void

    var body: some View {
        VStack(spacing: 0) {
            if plugins.isEmpty {
                Text("No plugins")
                    .agentBuddyFont(.footnote)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
            } else {
                let visible = Array(plugins.prefix(8))
                ForEach(Array(visible.enumerated()), id: \.element.id) { item in
                    let plugin = item.element
                    VStack(spacing: 0) {
                        Button {
                            onSelect(plugin)
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "puzzlepiece.extension.fill")
                                    .agentBuddyFont(.caption)
                                    .foregroundColor(AgentBuddyTheme.accent)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(plugin.displayTitle)
                                        .agentBuddyFont(.footnote, weight: .semibold)
                                        .foregroundColor(AgentBuddyTheme.textPrimary)
                                        .lineLimit(1)
                                    if let subtitle = plugin.interface?.shortDescription, !subtitle.isEmpty {
                                        Text(subtitle)
                                            .agentBuddyFont(.caption)
                                            .foregroundColor(AgentBuddyTheme.textSecondary)
                                            .lineLimit(1)
                                    }
                                }
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 9)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)

                        Divider()
                            .background(AgentBuddyTheme.border)
                            .opacity(item.offset < visible.count - 1 ? 1 : 0)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .background(AgentBuddyTheme.surface.opacity(0.95))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(AgentBuddyTheme.border, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .padding(.horizontal, 12)
        .padding(.bottom, 56)
    }
}
