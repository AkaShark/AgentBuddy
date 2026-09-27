import SwiftUI
import UIKit

struct ConversationBottomChrome: View {
    @Environment(AppModel.self) private var appModel
    let pinnedContextItems: [ConversationItem]
    let composer: ConversationComposerSnapshot
    @Binding var composerInputText: String
    @Binding var composerAttachedImage: UIImage?
    let onSend: (String, UIImage?, [ComposerFileAttachment], [SkillMentionSelection], [PluginMentionSelection]) -> Void
    let onFileSearch: (String) async throws -> [FileSearchResult]
    var bottomInset: CGFloat = 0
    let onOpenConversation: ((ThreadKey) -> Void)?
    let onResumeSessions: ((String) -> Void)?
    @State private var showCollaborationModeSelector = false
    @State private var collaborationModePresets: [AppCollaborationModePreset] = []
    @State private var collaborationModesLoading = false
    @State private var collaborationModeError: String?

    var body: some View {
        VStack(spacing: BuddySpacing.xs) {
            ConversationApprovalStack(threadKey: composer.threadKey)
            ConversationPinnedContextStrip(
                items: pinnedContextItems
            )
            ConversationInputBar(
                snapshot: composer,
                onSend: onSend,
                onFileSearch: onFileSearch,
                bottomInset: bottomInset,
                showModeChip: !hasPinnedDiff,
                onOpenModePicker: openCollaborationModePicker,
                onOpenConversation: onOpenConversation,
                onResumeSessions: onResumeSessions,
                inputText: $composerInputText,
                attachedImage: $composerAttachedImage
            )
            .background(.clear, ignoresSafeAreaEdges: .bottom)
            ConversationHostStatusLine(threadKey: composer.threadKey)
        }
        .padding(.bottom, 4)
        .background(
            LinearGradient(
                stops: [
                    .init(color: AgentBuddyTheme.background.opacity(0), location: 0),
                    .init(color: AgentBuddyTheme.background, location: 0.35),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .padding(.top, -24)
            .ignoresSafeArea(.container, edges: .bottom)
            .allowsHitTesting(false)
        )
        .sheet(isPresented: $showCollaborationModeSelector) {
            CollaborationModeSelectorSheet(
                presets: collaborationModePresets.isEmpty ? fallbackCollaborationModePresets : collaborationModePresets,
                selectedMode: composer.collaborationMode,
                isLoading: collaborationModesLoading,
                onSelect: { mode in
                    showCollaborationModeSelector = false
                    Task { await setCollaborationMode(mode) }
                }
            )
            .presentationDetents([.height(220)])
            .presentationDragIndicator(.visible)
            .task {
                await loadCollaborationModes()
            }
        }
        .alert("Collaboration Mode", isPresented: Binding(
            get: { collaborationModeError != nil },
            set: { if !$0 { collaborationModeError = nil } }
        )) {
            Button("OK", role: .cancel) { collaborationModeError = nil }
        } message: {
            Text(collaborationModeError ?? "Unable to update collaboration mode.")
        }
    }

    private var hasPinnedDiff: Bool {
        pinnedContextItems.contains {
            if case .fileChange(let data) = $0.content {
                return data.changes.contains {
                    !$0.diff.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                }
            }
            if case .turnDiff(let data) = $0.content {
                return !data.diff.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            }
            return false
        }
    }

    private var fallbackCollaborationModePresets: [AppCollaborationModePreset] {
        [
            AppCollaborationModePreset(
                kind: .`default`,
                name: "Default",
                model: nil,
                reasoningEffort: nil
            ),
            AppCollaborationModePreset(
                kind: .plan,
                name: "Plan",
                model: nil,
                reasoningEffort: .medium
            )
        ]
    }

    private func openCollaborationModePicker() {
        showCollaborationModeSelector = true
    }

    private func loadCollaborationModes() async {
        guard !collaborationModesLoading else { return }
        collaborationModesLoading = true
        defer { collaborationModesLoading = false }
        do {
            collaborationModePresets = try await appModel.client.listCollaborationModes(
                serverId: composer.threadKey.serverId
            )
        } catch {
            collaborationModePresets = fallbackCollaborationModePresets
        }
    }

    private func setCollaborationMode(_ mode: AppModeKind) async {
        do {
            try await appModel.store.setThreadCollaborationMode(
                key: composer.threadKey,
                mode: mode
            )
        } catch {
            collaborationModeError = error.localizedDescription
        }
    }
}

/// "💻 MacBook Pro · 已连接" under the composer. Connection is shown apart from
/// task state, so a lost connection never reads as a failed task.
struct ConversationHostStatusLine: View {
    let threadKey: ThreadKey
    @Environment(AppModel.self) private var appModel

    var body: some View {
        if let server = appModel.snapshot?.serverSnapshot(for: threadKey.serverId) {
            let state: BuddyConnectionState = server.isConnected ? .connected : .disconnected
            HStack(spacing: 6) {
                Image(systemName: server.isLocal ? "iphone" : "laptopcomputer")
                    .font(.system(size: 12, weight: .medium))
                    .accessibilityHidden(true)
                Text(verbatim: server.displayName)
                Text(verbatim: "·")
                Circle().fill(state.dotColor).frame(width: 6, height: 6)
                    .accessibilityHidden(true)
                Text(state.title)
            }
            .buddyText(.caption, weight: .medium)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .lineLimit(1)
            .frame(maxWidth: .infinity)
            .padding(.top, 2)
            .accessibilityElement(children: .combine)
        }
    }
}
