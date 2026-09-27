import SwiftUI
import UIKit

/// Full-screen editor for long drafts. Sending follows the same rules as the
/// inline composer: `isSendEnabled` is false while offline, submitting or
/// recording, and the draft is kept.
struct ConversationComposerExpandedView: View {
    @Binding var inputText: String
    @Binding var isPresented: Bool
    let onPasteImage: (UIImage) -> Void
    let onSend: () -> Void
    let hasAttachment: Bool
    var isSendEnabled = true
    var isConnected = true

    // Start unfocused so the `.task` below forces a false→true transition,
    // which is what drives `ConversationComposerTextView`'s coordinator to
    // call `becomeFirstResponder` once the UITextView is attached to a
    // window. Starting at `true` can no-op if the view hasn't finished the
    // fullScreenCover transition by the time `syncFocus` runs.
    @State private var isFocused = false

    private var hasContent: Bool {
        !inputText.trimmingCharacters(in: .whitespaces).isEmpty || hasAttachment
    }

    private var canSend: Bool { hasContent && isSendEnabled }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .topLeading) {
                ConversationComposerTextView(
                    text: $inputText,
                    isFocused: $isFocused,
                    onPasteImage: onPasteImage,
                    unboundedHeight: true
                )
                .padding(.horizontal, BuddySpacing.xs)
                .padding(.vertical, BuddySpacing.xxs)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                if inputText.isEmpty {
                    Text("Message AgentBuddy...")
                        .buddyText(.body)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .padding(.leading, BuddySpacing.lg)
                        .padding(.top, BuddySpacing.md)
                        .allowsHitTesting(false)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(AgentBuddyTheme.surface.ignoresSafeArea())
            .safeAreaInset(edge: .top, spacing: 0) {
                if !isConnected {
                    BuddyBanner(
                        tone: .warning,
                        message: Text("Connection lost. Task status will sync when the host is back; your draft is kept."),
                        systemImage: "wifi.slash"
                    )
                    .padding(.horizontal, BuddySpacing.md)
                    .padding(.vertical, BuddySpacing.xs)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        isPresented = false
                    } label: {
                        Image(systemName: "arrow.down.right.and.arrow.up.left")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                            .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel("Collapse composer")
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        guard canSend else { return }
                        onSend()
                        isPresented = false
                    } label: {
                        Image(systemName: "arrow.up")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(canSend ? AgentBuddyTheme.onAction : AgentBuddyTheme.onDisabled)
                            .frame(width: 36, height: 36)
                            .background(canSend ? AgentBuddyTheme.action : AgentBuddyTheme.disabled, in: Circle())
                            .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                            .contentShape(Rectangle())
                    }
                    .disabled(!canSend)
                    .accessibilityLabel("Send")
                }
            }
            .task {
                // Small delay lets the cover finish its transition and the
                // UITextView attach to a window, so becomeFirstResponder sticks.
                try? await Task.sleep(nanoseconds: 150_000_000)
                isFocused = true
            }
        }
    }
}
