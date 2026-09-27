import SwiftUI
import HairballUI

// MARK: - Detail Sheet

struct SubagentDetailSheet: View {
    @Environment(AppModel.self) private var appModel
    let threadKey: ThreadKey
    var agentLabel: String? = nil
    @Environment(\.dismiss) private var dismiss
    @State private var isLoading = false

    private var threadSnapshot: AppThreadSnapshot? {
        appModel.snapshot?.threadSnapshot(for: threadKey)
    }

    private var title: String {
        if let label = threadSnapshot.flatMap({ appModel.snapshot?.sessionSummary(for: $0.key)?.agentDisplayLabel }) {
            return label
        }
        if let label = appModel.snapshot?.sessionSummary(for: threadKey)?.agentDisplayLabel {
            return label
        }
        if let label = agentLabel, !label.isEmpty, !looksLikeId(label) { return label }
        if let resolved = appModel.snapshot?.resolvedAgentTargetLabel(for: threadKey.threadId, serverId: threadKey.serverId) {
            return resolved
        }
        return agentLabel ?? "Agent"
    }

    private func looksLikeId(_ value: String) -> Bool {
        value.count >= 16 && value.range(of: #"^[0-9a-fA-F-]+$"#, options: .regularExpression) != nil
    }

    var body: some View {
        NavigationStack {
            Group {
                if let threadSnapshot {
                    let items = threadSnapshot.hydratedConversationItems.map(\.conversationItem)
                    ScrollView {
                        if items.isEmpty {
                            VStack(spacing: BuddySpacing.sm) {
                                Spacer().frame(height: 40)
                                ProgressView()
                                    .tint(AgentBuddyTheme.textSecondary)
                                Text(isLoading ? "Loading thread..." : "Waiting for agent output...")
                                    .buddyText(.label, weight: .regular)
                                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                                Spacer()
                            }
                            .frame(maxWidth: .infinity)
                        } else {
                            ConversationTurnTimeline(
                                items: items,
                                isLive: threadSnapshot.activeTurnId != nil || threadSnapshot.info.status == .active,
                                serverId: threadKey.serverId,
                                originThreadId: threadKey.threadId,
                                agentDirectoryVersion: 0,
                                messageActionsDisabled: true,
                                onStreamingSnapshotRendered: nil,
                                onLiveContentLayoutChanged: nil,
                                resolveTargetLabel: { _ in nil },
                                onWidgetPrompt: { _ in },
                                onEditUserItem: { _ in },
                                onForkFromUserItem: { _ in }
                            )
                            .padding(.horizontal, BuddySpacing.md)
                            .padding(.vertical, BuddySpacing.sm)
                        }
                    }
                } else {
                    VStack(spacing: BuddySpacing.sm) {
                        Spacer()
                        BuddyIconTile(content: .symbol("person.fill.questionmark"), size: 48)
                        Text("Thread not available yet")
                            .buddyText(.heading)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                            .multilineTextAlignment(.center)
                        Text("The agent may still be initializing.")
                            .buddyText(.body)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                            .multilineTextAlignment(.center)
                        Spacer()
                    }
                    .padding(.horizontal, BuddySpacing.xl)
                    .frame(maxWidth: .infinity)
                }
            }
            .buddyPageBackground()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    let parts = parseLabel(title)
                    (
                        Text(parts.nickname)
                            .foregroundColor(AgentBuddyTheme.textPrimary)
                        + Text(parts.roleSuffix)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                    )
                    .buddyText(.heading)
                    .lineLimit(1)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                        .foregroundStyle(AgentBuddyTheme.link)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .buddySheetStyle()
        .task(id: threadKey.id) {
            await loadThreadIfNeeded()
        }
    }

    private func parseLabel(_ label: String) -> (nickname: String, roleSuffix: String) {
        guard label.hasSuffix("]"), let openBracket = label.lastIndex(of: "[") else {
            return (label, "")
        }
        let nickname = String(label[..<openBracket]).trimmingCharacters(in: .whitespacesAndNewlines)
        let role = String(label[label.index(after: openBracket)..<label.index(before: label.endIndex)])
        return (nickname, " (\(role))")
    }

    private func loadThreadIfNeeded() async {
        guard threadSnapshot == nil, !isLoading else { return }

        isLoading = true
        defer { isLoading = false }
        do {
            _ = try await appModel.resumeThread(
                key: threadKey,
                launchConfig: AppThreadLaunchConfig(
                    model: nil,
                    approvalPolicy: nil,
                    sandbox: nil,
                    developerInstructions: nil,
                    persistExtendedHistory: true
                ),
                cwdOverride: nil
            )
            await appModel.refreshThreadSnapshot(key: threadKey)
        } catch {}
    }
}
