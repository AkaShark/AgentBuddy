import SwiftUI
import UIKit

/// Mint composer card (radius 24): editor on top, then attach / partner chip
/// on the left and dictation plus the single primary action on the right.
///
/// Primary action by state:
/// - idle, nothing typed: send disabled;
/// - idle with text or attachment: send;
/// - running, nothing typed: explicit Stop (then "Stopping…" until the
///   snapshot says the turn ended);
/// - running with text: Stop stays available and send becomes "Queue",
///   because the shared store queues follow-ups while a turn is active;
/// - disconnected: the draft stays, sending is disabled.
struct ConversationComposerEntryRowView: View {
    @Binding var showAttachMenu: Bool
    @Binding var inputText: String
    @Binding var isComposerFocused: Bool
    @Binding var composerSelectionRange: NSRange
    let voiceManager: VoiceTranscriptionManager
    let isTurnActive: Bool
    let hasAttachment: Bool
    let allowsVoiceInput: Bool
    var isStopping: Bool = false
    var isConnected: Bool = true
    /// A send that creates something (new task) is in flight.
    var isSubmitting: Bool = false
    var placeholder: LocalizedStringKey = "Add details, or change direction…"
    let onPasteImage: (UIImage) -> Void
    let onSendText: () -> Void
    let onStopRecording: () -> Void
    let onStartRecording: () -> Void
    let onInterrupt: () -> Void

    @Environment(\.conversationPartnerLabel) private var partnerLabel
    @Environment(AppState.self) private var appState: AppState?
    @State private var showExpanded: Bool = false

    init(
        showAttachMenu: Binding<Bool>,
        inputText: Binding<String>,
        isComposerFocused: Binding<Bool>,
        composerSelectionRange: Binding<NSRange> = .constant(NSRange(location: 0, length: 0)),
        voiceManager: VoiceTranscriptionManager,
        isTurnActive: Bool,
        hasAttachment: Bool,
        allowsVoiceInput: Bool = true,
        isStopping: Bool = false,
        isConnected: Bool = true,
        isSubmitting: Bool = false,
        placeholder: LocalizedStringKey = "Add details, or change direction…",
        onPasteImage: @escaping (UIImage) -> Void,
        onSendText: @escaping () -> Void,
        onStopRecording: @escaping () -> Void,
        onStartRecording: @escaping () -> Void,
        onInterrupt: @escaping () -> Void
    ) {
        _showAttachMenu = showAttachMenu
        _inputText = inputText
        _isComposerFocused = isComposerFocused
        _composerSelectionRange = composerSelectionRange
        self.voiceManager = voiceManager
        self.isTurnActive = isTurnActive
        self.hasAttachment = hasAttachment
        self.allowsVoiceInput = allowsVoiceInput
        self.isStopping = isStopping
        self.isConnected = isConnected
        self.isSubmitting = isSubmitting
        self.placeholder = placeholder
        self.onPasteImage = onPasteImage
        self.onSendText = onSendText
        self.onStopRecording = onStopRecording
        self.onStartRecording = onStartRecording
        self.onInterrupt = onInterrupt
    }

    private var hasText: Bool {
        !inputText.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private var hasContent: Bool { hasText || hasAttachment }

    private var isVoiceBusy: Bool { voiceManager.isRecording || voiceManager.isTranscribing }

    private var canSend: Bool { hasContent && isConnected && !isVoiceBusy && !isSubmitting }

    /// Show the expand affordance once the composer is multi-line or starts to wrap.
    private var shouldShowExpand: Bool {
        !isVoiceBusy && (inputText.contains("\n") || inputText.count > 60)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            editor
            controls
                .buddyChromeTypeLimit()
        }
        .padding(.horizontal, BuddySpacing.sm)
        .padding(.top, BuddySpacing.xs)
        .padding(.bottom, BuddySpacing.xs)
        .background(AgentBuddyTheme.surface, in: RoundedRectangle(cornerRadius: BuddyRadius.composer, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: BuddyRadius.composer, style: .continuous)
                .strokeBorder(isComposerFocused ? AgentBuddyTheme.borderControl : AgentBuddyTheme.border, lineWidth: 1)
        }
        .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, y: 8)
        .padding(.horizontal, BuddySpacing.md)
        .padding(.vertical, BuddySpacing.xxs)
        .animation(.easeOut(duration: BuddyMotion.Kind.state.duration), value: isTurnActive)
        .animation(.easeOut(duration: BuddyMotion.Kind.state.duration), value: hasContent)
        .fullScreenCover(isPresented: $showExpanded) {
            ConversationComposerExpandedView(
                inputText: $inputText,
                isPresented: $showExpanded,
                onPasteImage: onPasteImage,
                onSend: onSendText,
                hasAttachment: hasAttachment
            )
        }
    }

    // MARK: Editor

    private var editor: some View {
        ZStack(alignment: .topLeading) {
            ConversationComposerTextView(
                text: $inputText,
                isFocused: $isComposerFocused,
                selectedRange: $composerSelectionRange,
                onPasteImage: onPasteImage,
                onHardwareSubmit: {
                    if canSend { onSendText() }
                }
            )
            if inputText.isEmpty {
                Text(placeholder)
                    .buddyText(.body)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .padding(.leading, 4)
                    .padding(.top, 10)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
        .frame(maxWidth: .infinity, minHeight: BuddySize.composerMinHeight, alignment: .leading)
        .overlay(alignment: .topTrailing) {
            if shouldShowExpand {
                Button {
                    showExpanded = true
                } label: {
                    Image(systemName: "arrow.up.left.and.arrow.down.right")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .frame(width: 32, height: 32)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(Text("Expand composer"))
                .transition(.opacity)
            }
        }
    }

    // MARK: Controls

    private var controls: some View {
        HStack(spacing: BuddySpacing.xs) {
            if !isVoiceBusy {
                BuddyIconButton(
                    systemImage: "plus",
                    accessibilityLabel: "Attach",
                    tone: .soft,
                    diameter: 36,
                    iconSize: 17
                ) {
                    showAttachMenu = true
                }
            }
            if let partnerLabel, let appState {
                Button {
                    appState.showModelSelector = true
                } label: {
                    HStack(spacing: 6) {
                        Text(verbatim: partnerLabel)
                            .lineLimit(1)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 10, weight: .semibold))
                            .accessibilityHidden(true)
                    }
                    .buddyText(.label)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .padding(.horizontal, BuddySpacing.sm)
                    .frame(minHeight: BuddySize.compactPill)
                    .background(AgentBuddyTheme.surfaceSoft, in: Capsule())
                    .frame(minHeight: BuddySize.minHitTarget)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .layoutPriority(-1)
                .accessibilityLabel(Text("Partner: \(partnerLabel)"))
                .accessibilityHint(Text("Choose partner, model and permissions"))
            }

            Spacer(minLength: BuddySpacing.xxs)

            voiceControl
            if isTurnActive {
                stopButton
            }
            if hasContent || !isTurnActive {
                sendButton
            }
        }
    }

    @ViewBuilder
    private var voiceControl: some View {
        if voiceManager.isRecording {
            AudioWaveformView(level: voiceManager.audioLevel)
                .frame(width: 44, height: 20)
            BuddyIconButton(
                systemImage: "stop.fill",
                accessibilityLabel: "Stop recording",
                tone: .soft,
                diameter: 36,
                iconSize: 14,
                action: onStopRecording
            )
        } else if voiceManager.isTranscribing {
            ProgressView()
                .tint(AgentBuddyTheme.textSecondary)
                .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                .accessibilityLabel(Text("Transcribing"))
        } else if allowsVoiceInput {
            BuddyIconButton(
                systemImage: "mic",
                accessibilityLabel: "Dictate",
                tone: .plain,
                diameter: 36,
                iconSize: 18,
                action: onStartRecording
            )
        }
    }

    @ViewBuilder
    private var stopButton: some View {
        if isStopping {
            HStack(spacing: 6) {
                ProgressView().controlSize(.small)
                Text("Stopping…")
                    .buddyText(.label, weight: .medium)
                    .fixedSize()
            }
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .padding(.horizontal, BuddySpacing.sm)
            .frame(minHeight: BuddySize.minHitTarget)
            .accessibilityElement(children: .combine)
        } else {
            Button(action: onInterrupt) {
                HStack(spacing: 6) {
                    Image(systemName: "stop.fill")
                        .font(.system(size: 11, weight: .bold))
                    if !hasContent {
                        Text("Stop")
                            .buddyText(.label, weight: .semibold)
                            .fixedSize()
                    }
                }
                .foregroundStyle(hasContent ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.onAction)
                .padding(.horizontal, hasContent ? 0 : BuddySpacing.md)
                .frame(minWidth: 36, minHeight: 36)
                .background(hasContent ? AgentBuddyTheme.surfaceSoft : AgentBuddyTheme.action, in: Capsule())
                .frame(minWidth: BuddySize.minHitTarget, minHeight: BuddySize.minHitTarget)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Stop task"))
            .transition(.opacity)
        }
    }

    @ViewBuilder
    private var sendButton: some View {
        if isTurnActive {
            // Queue: the store holds this until the current turn finishes;
            // it can then be steered or removed from the queue list.
            Button(action: onSendText) {
                HStack(spacing: 6) {
                    Image(systemName: "text.line.last.and.arrowtriangle.forward")
                        .font(.system(size: 13, weight: .semibold))
                    Text("Queue")
                        .buddyText(.label, weight: .semibold)
                        .fixedSize()
                }
                .foregroundStyle(canSend ? AgentBuddyTheme.onAction : AgentBuddyTheme.onDisabled)
                .padding(.horizontal, BuddySpacing.md)
                .frame(minHeight: 36)
                .background(canSend ? AgentBuddyTheme.action : AgentBuddyTheme.disabled, in: Capsule())
                .frame(minHeight: BuddySize.minHitTarget)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!canSend)
            .accessibilityLabel(Text("Queue message"))
            .accessibilityHint(Text("Sends after the current step finishes"))
        } else if isSubmitting {
            ZStack {
                Circle().fill(AgentBuddyTheme.action)
                ProgressView().tint(AgentBuddyTheme.onAction)
            }
            .frame(width: 38, height: 38)
            .frame(minWidth: BuddySize.minHitTarget, minHeight: BuddySize.minHitTarget)
            .accessibilityLabel(Text("Sending"))
        } else {
            BuddyIconButton(
                systemImage: "arrow.up",
                accessibilityLabel: "Send",
                tone: .action,
                diameter: 38,
                iconSize: 17,
                isEnabled: canSend,
                action: onSendText
            )
        }
    }
}
