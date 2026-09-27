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
                            VStack(spacing: 12) {
                                Spacer().frame(height: 40)
                                ProgressView()
                                    .tint(AgentBuddyTheme.accent)
                                Text(isLoading ? "Loading thread..." : "Waiting for agent output...")
                                    .agentBuddyFont(.caption)
                                    .foregroundColor(AgentBuddyTheme.textMuted)
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
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                        }
                    }
                } else {
                    VStack(spacing: 12) {
                        Spacer()
                        Image(systemName: "person.fill.questionmark")
                            .agentBuddyFont(size: 32)
                            .foregroundColor(AgentBuddyTheme.textMuted)
                        Text("Thread not available yet")
                            .agentBuddyFont(.footnote)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                        Text("The agent may still be initializing.")
                            .agentBuddyFont(.caption)
                            .foregroundColor(AgentBuddyTheme.textMuted)
                        Spacer()
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    let parts = parseLabel(title)
                    (
                        Text(parts.nickname)
                            .foregroundColor(titleColor(for: parts.nickname))
                        + Text(parts.roleSuffix)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                    )
                    .agentBuddyFont(.callout, weight: .semibold)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                        .foregroundColor(AgentBuddyTheme.accent)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
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

    private static let colors: [Color] = [
        Color(red: 0.90, green: 0.30, blue: 0.30),
        Color(red: 0.30, green: 0.75, blue: 0.55),
        Color(red: 0.40, green: 0.55, blue: 0.95),
        Color(red: 0.85, green: 0.60, blue: 0.25),
        Color(red: 0.70, green: 0.45, blue: 0.85),
        Color(red: 0.25, green: 0.78, blue: 0.82),
        Color(red: 0.90, green: 0.50, blue: 0.60),
        Color(red: 0.65, green: 0.75, blue: 0.30),
    ]

    private func titleColor(for name: String) -> Color {
        var hash: UInt64 = 5381
        for byte in name.utf8 { hash = ((hash &<< 5) &+ hash) &+ UInt64(byte) }
        return Self.colors[Int(hash % UInt64(Self.colors.count))]
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
