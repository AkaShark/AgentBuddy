import SwiftUI

#if DEBUG
/// Every approval state side by side: pending (command / file / permissions),
/// submitting, failed, and the result cards.
struct MintGalleryApprovalsPage: View {
    private let host = "MacBook Pro"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: BuddySpacing.lg) {
                galleryTitle("Approvals")
                ApprovalCard(
                    approval: MintGalleryFixtures.approval(id: "a", kind: .command, thread: "t"),
                    hostName: host,
                    position: (1, 3),
                    onDecision: { _ in }
                )
                ApprovalCard(
                    approval: MintGalleryFixtures.approval(id: "b", kind: .fileChange, thread: "t"),
                    hostName: host,
                    submittingDecision: .accept,
                    onDecision: { _ in }
                )
                ApprovalCard(
                    approval: MintGalleryFixtures.approval(id: "c", kind: .permissions, thread: "t"),
                    hostName: host,
                    failureMessage: "The host did not respond.",
                    onDecision: { _ in }
                )
                ForEach(
                    [ApprovalOutcome.Kind.allowedOnce, .denied, .resolvedElsewhere],
                    id: \.self
                ) { kind in
                    ApprovalResultCard(
                        outcome: ApprovalOutcome(approvalId: "r", kind: kind, approvalKind: .command, date: Date()),
                        onDismiss: {}
                    )
                }
            }
            .padding(BuddySpacing.xl)
            .padding(.top, 48)
        }
    }
}

/// Composer in each execution state.
struct MintGalleryComposerPage: View {
    @State private var empty = ""
    @State private var filled = "Also check the logout path"
    @State private var voiceA = VoiceTranscriptionManager()
    @State private var voiceB = VoiceTranscriptionManager()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: BuddySpacing.md) {
                galleryTitle("Composer")
                labeled("Idle, empty") { row(text: $empty, active: false) }
                labeled("Idle, with text") { row(text: $filled, active: false) }
                labeled("Running") { row(text: $empty, active: true) }
                labeled("Running, with text (queue)") { row(text: $filled, active: true) }
                labeled("Stopping") { row(text: $empty, active: true, stopping: true) }
                labeled("Offline") { row(text: $filled, active: false, connected: false) }
                labeled("Creating a task") { row(text: $filled, active: false, submitting: true) }
            }
            .padding(.vertical, BuddySpacing.xl)
            .padding(.top, 48)
        }
        .environment(\.conversationPartnerLabel, "Codex")
    }

    private func row(
        text: Binding<String>,
        active: Bool,
        stopping: Bool = false,
        connected: Bool = true,
        submitting: Bool = false
    ) -> some View {
        ConversationComposerEntryRowView(
            showAttachMenu: .constant(false),
            inputText: text,
            isComposerFocused: .constant(false),
            voiceManager: active ? voiceA : voiceB,
            isTurnActive: active,
            hasAttachment: false,
            isStopping: stopping,
            isConnected: connected,
            isSubmitting: submitting,
            onPasteImage: { _ in },
            onSendText: {},
            onStopRecording: {},
            onStartRecording: {},
            onInterrupt: {}
        )
    }

    private func labeled<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
            Text(verbatim: title)
                .buddyText(.caption, weight: .medium)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .padding(.horizontal, BuddySpacing.xl)
            content()
        }
    }
}

private func galleryTitle(_ title: String) -> some View {
    Text(verbatim: title)
        .buddyText(.title)
        .foregroundStyle(AgentBuddyTheme.textPrimary)
        .padding(.horizontal, BuddySpacing.xl)
}
#endif
