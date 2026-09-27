import SwiftUI

extension ConversationInfoView {
    // MARK: - Context Window

    var contextWindowSection: some View {
        Group {
            if let used = thread?.contextTokensUsed, let window = thread?.modelContextWindow, window > 0 {
                let percent = Double(used) / Double(window)
                VStack(spacing: 8) {
                    HStack {
                        Text("Context Window")
                            .agentBuddyFont(size: 14, weight: .semibold)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                        Spacer()
                        Text("\(Int(percent * 100))%")
                            .agentBuddyFont(size: 14, weight: .bold)
                            .foregroundStyle(contextColor(percent: percent))
                    }

                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 4)
                                .fill(AgentBuddyTheme.border)
                                .frame(height: 8)
                            RoundedRectangle(cornerRadius: 4)
                                .fill(contextColor(percent: percent))
                                .frame(width: geo.size.width * min(1, percent), height: 8)
                        }
                    }
                    .frame(height: 8)

                    HStack {
                        Text(formatTokens(used))
                            .agentBuddyFont(size: 11)
                            .foregroundStyle(AgentBuddyTheme.textMuted)
                        Spacer()
                        Text(formatTokens(window))
                            .agentBuddyFont(size: 11)
                            .foregroundStyle(AgentBuddyTheme.textMuted)
                    }
                }
                .padding(16)
                .modifier(GlassRectModifier(cornerRadius: 12))
            }
        }
    }

    private func contextColor(percent: Double) -> Color {
        if percent >= 0.8 { return AgentBuddyTheme.danger }
        if percent >= 0.6 { return AgentBuddyTheme.warning }
        return AgentBuddyTheme.accent
    }

    private func formatTokens(_ tokens: UInt64) -> String {
        if tokens >= 1_000_000 {
            return String(format: "%.1fM", Double(tokens) / 1_000_000)
        } else if tokens >= 1_000 {
            return String(format: "%.1fK", Double(tokens) / 1_000)
        }
        return "\(tokens)"
    }

    // MARK: - Per-Conversation Stats

    var conversationStatsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Conversation Stats")
                .agentBuddyFont(size: 14, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.textPrimary)

            LazyVGrid(columns: [
                GridItem(.flexible(), spacing: 12),
                GridItem(.flexible(), spacing: 12)
            ], spacing: 12) {
                statCard("Messages", value: "\(stats?.totalMessages ?? 0)", detail: "\(stats?.userMessageCount ?? 0) user · \(stats?.assistantMessageCount ?? 0) assistant")
                statCard("Turns", value: "\(stats?.turnCount ?? 0)")
                statCard("Commands", value: "\(stats?.commandsExecuted ?? 0)", detail: "\(stats?.commandsSucceeded ?? 0) ok · \(stats?.commandsFailed ?? 0) fail")
                statCard("Files Changed", value: "\(stats?.filesChanged ?? 0)", detail: "+\(stats?.diffAdditions ?? 0) / -\(stats?.diffDeletions ?? 0)")
                statCard("MCP Calls", value: "\(stats?.mcpToolCallCount ?? 0)")
                statCard("Exec Time", value: formatDuration(Int64(stats?.totalCommandDurationMs ?? 0)))
            }
        }
        .padding(16)
        .modifier(GlassRectModifier(cornerRadius: 12))
    }

    private func statCard(_ title: String, value: String, detail: String? = nil) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .agentBuddyFont(size: 20, weight: .bold)
                .foregroundStyle(AgentBuddyTheme.accent)
            Text(title)
                .agentBuddyFont(size: 12, weight: .medium)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
            if let detail {
                Text(detail)
                    .agentBuddyFont(size: 10)
                    .foregroundStyle(AgentBuddyTheme.textMuted)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .modifier(GlassRectModifier(cornerRadius: 8))
    }

    private func formatDuration(_ ms: Int64) -> String {
        if ms < 1000 { return "\(ms)ms" }
        let secs = Double(ms) / 1000
        if secs < 60 { return String(format: "%.1fs", secs) }
        let mins = Int(secs / 60)
        let remainSecs = Int(secs) % 60
        return "\(mins)m \(remainSecs)s"
    }
}
