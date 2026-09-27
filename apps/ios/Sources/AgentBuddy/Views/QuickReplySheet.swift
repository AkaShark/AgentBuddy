import SwiftUI

/// Minimal reply composer shown when the user swipes right on a home
/// session row. Sends a turn on the targeted thread and dismisses.
struct QuickReplySheet: View {
    let thread: HomeDashboardRecentSession
    let onSend: @MainActor (ThreadKey, String) async -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var text: String = ""
    @State private var isSending = false
    @State private var errorMessage: String?
    @FocusState private var isFocused: Bool

    private var canSend: Bool {
        !isSending && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: BuddySpacing.md) {
                VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                    Text(thread.sessionTitle)
                        .buddyText(.heading)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                        .lineLimit(2)

                    Text(thread.serverDisplayName + " · " + (HomeDashboardSupport.workspaceLabel(for: thread.cwd) ?? PathDisplay.display(thread.cwd, isLocal: thread.isLocal)))
                        .buddyText(.caption)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }

                composer

                if let errorMessage {
                    BuddyBanner(tone: .danger, message: Text(errorMessage))
                }

                Spacer()
            }
            .padding(.horizontal, BuddySpacing.xl)
            .padding(.top, BuddySpacing.sm)
            .frame(maxWidth: .infinity, alignment: .leading)
            .buddyPageBackground()
            .navigationTitle("Reply")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cancel") { dismiss() }
                        .foregroundStyle(AgentBuddyTheme.link)
                }
            }
            .task {
                // Pop the keyboard once the sheet has settled.
                try? await Task.sleep(nanoseconds: 150_000_000)
                isFocused = true
            }
        }
    }

    /// Mint composer card: surface, radius 24, one primary send action.
    private var composer: some View {
        let shape = RoundedRectangle(cornerRadius: BuddyRadius.composer, style: .continuous)
        return VStack(alignment: .trailing, spacing: BuddySpacing.xs) {
            TextField(
                "Reply…",
                text: $text,
                axis: .vertical
            )
            .focused($isFocused)
            .lineLimit(1...8)
            .submitLabel(.send)
            .buddyText(.body)
            .foregroundStyle(AgentBuddyTheme.textPrimary)
            .tint(AgentBuddyTheme.focus)
            .padding(.horizontal, BuddySpacing.xxs)
            .padding(.top, BuddySpacing.xs)
            .frame(maxWidth: .infinity, minHeight: BuddySize.composerMinHeight, alignment: .topLeading)

            sendButton
        }
        .padding(.horizontal, BuddySpacing.sm)
        .padding(.vertical, BuddySpacing.xs)
        .background(AgentBuddyTheme.surface, in: shape)
        .overlay {
            shape.strokeBorder(isFocused ? AgentBuddyTheme.borderControl : AgentBuddyTheme.border, lineWidth: 1)
        }
        .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, y: 8)
    }

    @ViewBuilder
    private var sendButton: some View {
        if isSending {
            ZStack {
                Circle().fill(AgentBuddyTheme.action)
                ProgressView().tint(AgentBuddyTheme.onAction)
            }
            .frame(width: 38, height: 38)
            .frame(minWidth: BuddySize.minHitTarget, minHeight: BuddySize.minHitTarget)
            .accessibilityElement()
            .accessibilityLabel(Text("Sending"))
        } else {
            BuddyIconButton(
                systemImage: "arrow.up",
                accessibilityLabel: "Send",
                tone: .action,
                diameter: 38,
                iconSize: 17,
                isEnabled: canSend
            ) {
                Task { await submit() }
            }
        }
    }

    private func submit() async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !isSending else { return }
        isSending = true
        errorMessage = nil
        await onSend(thread.key, trimmed)
        isSending = false
        dismiss()
    }
}
