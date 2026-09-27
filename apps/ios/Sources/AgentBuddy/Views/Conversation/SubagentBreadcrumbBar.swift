import SwiftUI

private extension AppThreadSnapshot {
    var agentDisplayLabel: String? {
        let nickname = info.agentNickname?.trimmingCharacters(in: .whitespacesAndNewlines)
        let role = info.agentRole?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let nickname, !nickname.isEmpty { return nickname }
        if let role, !role.isEmpty { return role }
        return nil
    }
}

struct SubagentBreadcrumbBar: View {
    let thread: AppThreadSnapshot
    let topInset: CGFloat
    let onNavigateToParent: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button(action: onNavigateToParent) {
                HStack(spacing: 4) {
                    Image(systemName: "chevron.left")
                        .agentBuddyFont(size: 10, weight: .semibold)
                    Text("Parent")
                        .agentBuddyFont(.caption, weight: .medium)
                }
                .foregroundColor(AgentBuddyTheme.accent)
            }
            .buttonStyle(.plain)

            Divider()
                .frame(height: 14)
                .background(AgentBuddyTheme.border)

            HStack(spacing: 4) {
                Image(systemName: "person.fill")
                    .agentBuddyFont(size: 10, weight: .semibold)
                    .foregroundColor(AgentBuddyTheme.success)
                Text(thread.agentDisplayLabel ?? "Agent")
                    .agentBuddyFont(.caption, weight: .medium)
                    .foregroundColor(AgentBuddyTheme.textPrimary)
                    .lineLimit(1)
            }

            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .padding(.top, topInset + 8)
        .background(
            AgentBuddyTheme.surface.opacity(0.85)
                .background(.ultraThinMaterial)
                .ignoresSafeArea()
        )
    }
}
