import SwiftUI

extension ConversationInfoView {
    // MARK: - Context Window

    var contextWindowSection: some View {
        Group {
            if let used = thread?.contextTokensUsed, let window = thread?.modelContextWindow, window > 0 {
                let percent = Double(used) / Double(window)
                let level = InfoUsageLevel(fraction: percent)
                VStack(alignment: .leading, spacing: BuddySpacing.sm) {
                    HStack(alignment: .firstTextBaseline) {
                        Text("Context Window")
                            .buddyText(.heading)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                        Spacer(minLength: BuddySpacing.xs)
                        InfoUsageValue(level: level, text: "\(Int(percent * 100))%")
                    }

                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(AgentBuddyTheme.surfaceSoft)
                            Capsule()
                                .fill(level.color)
                                .frame(width: geo.size.width * min(1, percent))
                        }
                    }
                    .frame(height: 8)
                    .accessibilityHidden(true)

                    HStack {
                        Text(verbatim: formatTokens(used))
                        Spacer()
                        Text(verbatim: formatTokens(window))
                    }
                    .buddyText(.caption)
                    .monospacedDigit()
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
                .buddyCard(.surface, radius: BuddyRadius.card, padding: BuddySpacing.lg)
                .accessibilityElement(children: .combine)
            }
        }
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
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            BuddySectionHeader("Conversation Stats")

            LazyVGrid(columns: [
                GridItem(.flexible(), spacing: BuddySpacing.sm, alignment: .top),
                GridItem(.flexible(), spacing: BuddySpacing.sm, alignment: .top)
            ], spacing: BuddySpacing.sm) {
                statCard(
                    "Messages",
                    value: "\(stats?.totalMessages ?? 0)",
                    detail: Text("\(Int(stats?.userMessageCount ?? 0)) user · \(Int(stats?.assistantMessageCount ?? 0)) assistant")
                )
                statCard("Turns", value: "\(stats?.turnCount ?? 0)")
                statCard(
                    "Commands",
                    value: "\(stats?.commandsExecuted ?? 0)",
                    detail: Text("\(Int(stats?.commandsSucceeded ?? 0)) ok · \(Int(stats?.commandsFailed ?? 0)) fail")
                )
                statCard(
                    "Files Changed",
                    value: "\(stats?.filesChanged ?? 0)",
                    detail: Text(verbatim: "+\(stats?.diffAdditions ?? 0) / -\(stats?.diffDeletions ?? 0)")
                )
                statCard("MCP Calls", value: "\(stats?.mcpToolCallCount ?? 0)")
                statCard("Exec Time", value: formatDuration(Int64(stats?.totalCommandDurationMs ?? 0)))
            }
        }
    }

    /// Small stat card (radius 16): value, label, optional breakdown.
    private func statCard(_ title: LocalizedStringKey, value: String, detail: Text? = nil) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: value)
                .buddyText(.title)
                .monospacedDigit()
                .foregroundStyle(AgentBuddyTheme.textPrimary)
            Text(title)
                .buddyText(.label)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
            if let detail {
                detail
                    .buddyText(.caption)
                    .monospacedDigit()
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .buddyCard(.soft, radius: BuddyRadius.tile, padding: BuddySpacing.md)
        .accessibilityElement(children: .combine)
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

// MARK: - Usage level

/// Semantic colour for a usage fraction (context window, rate limits):
/// under 60% success, from 60% warning, from 80% danger. Warning and
/// danger also show an icon, so the level never relies on colour alone.
struct InfoUsageLevel: Equatable {
    enum Kind { case normal, elevated, critical }

    let kind: Kind

    init(fraction: Double) {
        if fraction >= 0.8 {
            kind = .critical
        } else if fraction >= 0.6 {
            kind = .elevated
        } else {
            kind = .normal
        }
    }

    var color: Color {
        switch kind {
        case .normal: return AgentBuddyTheme.success
        case .elevated: return AgentBuddyTheme.warning
        case .critical: return AgentBuddyTheme.danger
        }
    }

    var systemImage: String? {
        switch kind {
        case .normal: return nil
        case .elevated: return "exclamationmark.triangle"
        case .critical: return "exclamationmark.octagon"
        }
    }
}

/// Percentage text in the level colour, with the level icon when elevated.
struct InfoUsageValue: View {
    let level: InfoUsageLevel
    let text: String

    var body: some View {
        HStack(spacing: BuddySpacing.xxs) {
            if let systemImage = level.systemImage {
                Image(systemName: systemImage)
                    .accessibilityHidden(true)
            }
            Text(verbatim: text)
                .monospacedDigit()
        }
        .buddyText(.label, weight: .semibold)
        .foregroundStyle(level.color)
    }
}
