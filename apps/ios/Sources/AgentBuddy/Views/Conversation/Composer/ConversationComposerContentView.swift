import SwiftUI
import UIKit

struct ConversationComposerContentView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let attachedImage: UIImage?
    let attachedFiles: [ComposerFileAttachment]
    let collaborationMode: AppModeKind
    let activePlanProgress: AppPlanProgressSnapshot?
    let pendingUserInputRequest: PendingUserInputRequest?
    let hasPendingPlanImplementation: Bool
    let activeTaskSummary: ConversationActiveTaskSummary?
    let queuedFollowUps: [AppQueuedFollowUpPreview]
    let pluginMentions: [PluginMentionSelection]
    let goal: AppThreadGoal?
    let goalActions: GoalCardActions
    let rateLimits: RateLimitSnapshot?
    let contextPercent: Int64?
    let isTurnActive: Bool
    let isStopping: Bool
    let isConnected: Bool
    let isSubmitting: Bool
    let placeholder: LocalizedStringKey
    let showModeChip: Bool
    let voiceManager: VoiceTranscriptionManager
    let allowsVoiceInput: Bool
    @Binding var showAttachMenu: Bool
    let onClearAttachment: () -> Void
    let onRemoveFileAttachment: (ComposerFileAttachment) -> Void
    let onRespondToPendingUserInput: ([String: [String]]) -> Void
    let onDismissPendingUserInput: () -> Void
    let onImplementPlan: () -> Void
    let onDismissPlanImplementation: () -> Void
    let onSteerQueuedFollowUp: (AppQueuedFollowUpPreview) -> Void
    let onDeleteQueuedFollowUp: (AppQueuedFollowUpPreview) -> Void
    let onRemovePluginMention: (PluginMentionSelection) -> Void
    let onPasteImage: (UIImage) -> Void
    let onOpenModePicker: () -> Void
    let onSendText: () -> Void
    let onStopRecording: () -> Void
    let onStartRecording: () -> Void
    let onInterrupt: () -> Void
    @Binding var inputText: String
    @Binding var isComposerFocused: Bool
    @Binding var composerSelectionRange: NSRange

    init(
        attachedImage: UIImage?,
        attachedFiles: [ComposerFileAttachment] = [],
        collaborationMode: AppModeKind,
        activePlanProgress: AppPlanProgressSnapshot?,
        pendingUserInputRequest: PendingUserInputRequest?,
        hasPendingPlanImplementation: Bool = false,
        activeTaskSummary: ConversationActiveTaskSummary?,
        queuedFollowUps: [AppQueuedFollowUpPreview],
        pluginMentions: [PluginMentionSelection] = [],
        goal: AppThreadGoal? = nil,
        goalActions: GoalCardActions = .noop,
        rateLimits: RateLimitSnapshot?,
        contextPercent: Int64?,
        isTurnActive: Bool,
        isStopping: Bool = false,
        isConnected: Bool = true,
        isSubmitting: Bool = false,
        placeholder: LocalizedStringKey = "Add details, or change direction…",
        showModeChip: Bool = true,
        voiceManager: VoiceTranscriptionManager,
        allowsVoiceInput: Bool = true,
        showAttachMenu: Binding<Bool>,
        onClearAttachment: @escaping () -> Void,
        onRemoveFileAttachment: @escaping (ComposerFileAttachment) -> Void = { _ in },
        onRespondToPendingUserInput: @escaping ([String: [String]]) -> Void,
        onDismissPendingUserInput: @escaping () -> Void = {},
        onImplementPlan: @escaping () -> Void = {},
        onDismissPlanImplementation: @escaping () -> Void = {},
        onSteerQueuedFollowUp: @escaping (AppQueuedFollowUpPreview) -> Void,
        onDeleteQueuedFollowUp: @escaping (AppQueuedFollowUpPreview) -> Void,
        onRemovePluginMention: @escaping (PluginMentionSelection) -> Void = { _ in },
        onPasteImage: @escaping (UIImage) -> Void,
        onOpenModePicker: @escaping () -> Void,
        onSendText: @escaping () -> Void,
        onStopRecording: @escaping () -> Void,
        onStartRecording: @escaping () -> Void,
        onInterrupt: @escaping () -> Void,
        inputText: Binding<String>,
        isComposerFocused: Binding<Bool>,
        composerSelectionRange: Binding<NSRange> = .constant(NSRange(location: 0, length: 0))
    ) {
        self.attachedImage = attachedImage
        self.attachedFiles = attachedFiles
        self.collaborationMode = collaborationMode
        self.activePlanProgress = activePlanProgress
        self.pendingUserInputRequest = pendingUserInputRequest
        self.hasPendingPlanImplementation = hasPendingPlanImplementation
        self.activeTaskSummary = activeTaskSummary
        self.queuedFollowUps = queuedFollowUps
        self.pluginMentions = pluginMentions
        self.goal = goal
        self.goalActions = goalActions
        self.rateLimits = rateLimits
        self.contextPercent = contextPercent
        self.isTurnActive = isTurnActive
        self.isStopping = isStopping
        self.isConnected = isConnected
        self.isSubmitting = isSubmitting
        self.placeholder = placeholder
        self.showModeChip = showModeChip
        self.voiceManager = voiceManager
        self.allowsVoiceInput = allowsVoiceInput
        _showAttachMenu = showAttachMenu
        self.onClearAttachment = onClearAttachment
        self.onRemoveFileAttachment = onRemoveFileAttachment
        self.onRespondToPendingUserInput = onRespondToPendingUserInput
        self.onDismissPendingUserInput = onDismissPendingUserInput
        self.onImplementPlan = onImplementPlan
        self.onDismissPlanImplementation = onDismissPlanImplementation
        self.onSteerQueuedFollowUp = onSteerQueuedFollowUp
        self.onDeleteQueuedFollowUp = onDeleteQueuedFollowUp
        self.onRemovePluginMention = onRemovePluginMention
        self.onPasteImage = onPasteImage
        self.onOpenModePicker = onOpenModePicker
        self.onSendText = onSendText
        self.onStopRecording = onStopRecording
        self.onStartRecording = onStartRecording
        self.onInterrupt = onInterrupt
        _inputText = inputText
        _isComposerFocused = isComposerFocused
        _composerSelectionRange = composerSelectionRange
    }

    var body: some View {
        VStack(spacing: 0) {
            if let attachedImage {
                HStack {
                    ZStack(alignment: .topTrailing) {
                        Image(uiImage: attachedImage)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 60, height: 60)
                            .clipShape(RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous))

                        Button(action: onClearAttachment) {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 20))
                                .symbolRenderingMode(.palette)
                                .foregroundStyle(AgentBuddyTheme.onAction, AgentBuddyTheme.action)
                                .frame(width: 32, height: 32)
                                .contentShape(Rectangle())
                        }
                        .accessibilityLabel(Text("Remove image"))
                        .offset(x: 10, y: -10)
                    }

                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
            }

            if !attachedFiles.isEmpty {
                ConversationComposerFileChipStrip(
                    files: attachedFiles,
                    onRemove: onRemoveFileAttachment
                )
                .padding(.horizontal, 16)
                .padding(.top, attachedImage == nil ? 8 : 6)
            }

            VStack(alignment: .trailing, spacing: 0) {
                if let goal {
                    ConversationComposerGoalRowView(goal: goal, actions: goalActions)
                        .padding(.horizontal, 12)
                        .padding(.top, 8)
                }

                if let activePlanProgress {
                    ConversationComposerPlanProgressView(progress: activePlanProgress)
                        .id(activePlanProgress.turnId)
                        .padding(.horizontal, 12)
                        .padding(.top, 8)
                }

                if let activeTaskSummary {
                    ConversationComposerActiveTaskRowView(summary: activeTaskSummary)
                        .padding(.horizontal, 12)
                        .padding(.top, 8)
                }

                if let pendingUserInputRequest {
                    PendingUserInputPromptView(
                        request: pendingUserInputRequest,
                        onSubmit: onRespondToPendingUserInput,
                        onDismiss: onDismissPendingUserInput
                    )
                    .padding(.horizontal, 12)
                    .padding(.top, 8)
                }

                if hasPendingPlanImplementation {
                    PlanImplementationPromptView(
                        onImplement: onImplementPlan,
                        onDismiss: onDismissPlanImplementation
                    )
                    .padding(.horizontal, 12)
                    .padding(.top, 8)
                }

                if !queuedFollowUps.isEmpty {
                    QueuedFollowUpsPreviewView(
                        previews: queuedFollowUps,
                        onSteer: onSteerQueuedFollowUp,
                        onDelete: onDeleteQueuedFollowUp
                    )
                        .padding(.horizontal, 12)
                        .padding(.top, 8)
                }

                if !pluginMentions.isEmpty {
                    ConversationComposerPluginChipStrip(
                        plugins: pluginMentions,
                        onRemove: onRemovePluginMention
                    )
                    .padding(.horizontal, 12)
                    .padding(.top, 6)
                }

                if !isConnected {
                    BuddyBanner(
                        tone: .warning,
                        message: Text("Connection lost. Task status will sync when the host is back; your draft is kept."),
                        systemImage: "wifi.slash"
                    )
                    .padding(.horizontal, BuddySpacing.md)
                    .padding(.top, BuddySpacing.xs)
                }

                ConversationComposerEntryRowView(
                    showAttachMenu: $showAttachMenu,
                    inputText: $inputText,
                    isComposerFocused: $isComposerFocused,
                    composerSelectionRange: $composerSelectionRange,
                    voiceManager: voiceManager,
                    isTurnActive: isTurnActive,
                    hasAttachment: attachedImage != nil || !attachedFiles.isEmpty,
                    allowsVoiceInput: allowsVoiceInput,
                    isStopping: isStopping,
                    isConnected: isConnected,
                    isSubmitting: isSubmitting,
                    placeholder: placeholder,
                    onPasteImage: onPasteImage,
                    onSendText: onSendText,
                    onStopRecording: onStopRecording,
                    onStartRecording: onStartRecording,
                    onInterrupt: onInterrupt
                )

                ConversationComposerContextBarView(
                    rateLimits: rateLimits,
                    contextPercent: contextPercent
                )
            }
        }
        .frame(maxWidth: AgentBuddyPlatform.isRegularSurface(horizontalSizeClass: horizontalSizeClass) ? 760 : .infinity)
        .frame(maxWidth: .infinity, alignment: .center)
    }
}

private struct ConversationComposerFileChipStrip: View {
    let files: [ComposerFileAttachment]
    let onRemove: (ComposerFileAttachment) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(files) { file in
                    HStack(spacing: 5) {
                        Image(systemName: "doc")
                            .agentBuddyFont(size: 10, weight: .semibold)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(file.label)
                                .agentBuddyFont(.caption, weight: .semibold)
                                .foregroundStyle(AgentBuddyTheme.textPrimary)
                                .lineLimit(1)
                            Text(file.path)
                                .agentBuddyFont(size: 10)
                                .foregroundStyle(AgentBuddyTheme.textMuted)
                                .lineLimit(1)
                        }
                        .frame(maxWidth: 180, alignment: .leading)
                        Button {
                            onRemove(file)
                        } label: {
                            Image(systemName: "xmark")
                                .agentBuddyFont(size: 9, weight: .bold)
                                .foregroundStyle(AgentBuddyTheme.accent)
                                .padding(3)
                                .background(Circle().fill(AgentBuddyTheme.accent.opacity(0.18)))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Remove file \(file.label)")
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(
                        RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous)
                            .fill(AgentBuddyTheme.surfaceSoft)
                    )
                }
            }
        }
    }
}

private struct ConversationComposerPluginChipStrip: View {
    let plugins: [PluginMentionSelection]
    let onRemove: (PluginMentionSelection) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(plugins, id: \.path) { plugin in
                    HStack(spacing: 4) {
                        Image(systemName: "puzzlepiece.extension.fill")
                            .agentBuddyFont(size: 10, weight: .semibold)
                            .foregroundStyle(AgentBuddyTheme.accent)
                        Text(plugin.displayTitle)
                            .agentBuddyFont(.caption, weight: .semibold)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                            .lineLimit(1)
                        Button {
                            onRemove(plugin)
                        } label: {
                            Image(systemName: "xmark")
                                .agentBuddyFont(size: 9, weight: .bold)
                                .foregroundStyle(AgentBuddyTheme.accent)
                                .padding(3)
                                .background(Circle().fill(AgentBuddyTheme.accent.opacity(0.18)))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Remove plugin \(plugin.displayTitle)")
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(AgentBuddyTheme.surfaceSoft))
                }
            }
        }
    }
}
