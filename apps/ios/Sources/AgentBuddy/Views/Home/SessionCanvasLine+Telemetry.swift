import SwiftUI
import UIKit

extension SessionCanvasLine {
    // MARK: - Zoom 3+: telemetry strip (counts · adds/rems · stopwatch · ctx%)
    //
    // The numeric line. Lives on its own row so identity above can render
    // full-width without competing for space. Wraps gracefully if the
    // device is narrow or the text scale is large — the only soft contract
    // is that nothing here truncates with an ellipsis.

    @ViewBuilder
    var telemetryStrip: some View {
        if zoomLevel >= 4 {
            telemetryDashboard
        } else {
            telemetryRow
        }
    }

    /// Zoom 3: tight horizontal strip — chips flow left, no labels, no
    /// borders. Density-friendly so multiple sessions still fit on
    /// screen at this zoom.
    @ViewBuilder
    private var telemetryRow: some View {
        let stats = s
        let hasDiff = (stats?.diffAdditions ?? 0) > 0 || (stats?.diffDeletions ?? 0) > 0
        let hasContextPct: Bool = {
            guard let tu = session.tokenUsage, let window = tu.contextWindow else { return false }
            return window > 0
        }()
        let hasAny = turnCount > 0 || toolCallCount > 0 || hasDiff || session.lastTurnStart != nil || hasContextPct

        if hasAny {
            HStack(spacing: 12) {
                if turnCount > 0 {
                    HStack(spacing: 2) {
                        Image(systemName: "arrow.turn.down.right")
                            .agentBuddyFont(size: 8)
                        RollingMetricText("\(turnCount)")
                    }
                    .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.7))
                }
                if toolCallCount > 0 {
                    HStack(spacing: 2) {
                        Image(systemName: "chevron.left.forwardslash.chevron.right")
                            .agentBuddyFont(size: 8)
                        RollingMetricText("\(toolCallCount)")
                    }
                    .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.7))
                }
                if let stats, hasDiff {
                    HStack(spacing: 5) {
                        RollingMetricText("+\(stats.diffAdditions)")
                            .foregroundStyle(AgentBuddyTheme.accent.opacity(0.75))
                        RollingMetricText("-\(stats.diffDeletions)")
                            .foregroundStyle(AgentBuddyTheme.danger.opacity(0.65))
                    }
                }
                if let start = session.lastTurnStart {
                    TurnStopwatchChip(start: start, end: session.lastTurnEnd)
                }
                if let tu = session.tokenUsage, let window = tu.contextWindow, window > 0 {
                    let pct = Int((Double(tu.totalTokens) / Double(window)) * 100)
                    RollingMetricText("\(pct)%")
                        .foregroundStyle(pct > 80 ? AgentBuddyTheme.warning.opacity(0.85) : AgentBuddyTheme.textMuted.opacity(0.75))
                }
                Spacer(minLength: 0)
            }
            .agentBuddyMonoFont(size: 10, weight: .regular)
            .padding(.top, 4)
        }
    }

    /// Zoom 4: 2-column × 3-row dashboard between dashed rules. Each
    /// cell is `[icon] value label` — value bold/coloured, label dim.
    /// Cells with no data are placed as empty spaces so paired rows
    /// stay aligned. When the session has a goal, an objective banner
    /// folds into the top of the same dashed panel, and the tokens /
    /// duration cells prefer goal-derived numbers (the goal's budget
    /// burndown is more meaningful than session-wide totals).
    @ViewBuilder
    private var telemetryDashboard: some View {
        let stats = s
        let files = stats?.filesChanged ?? 0
        let pct: Int? = {
            guard let tu = session.tokenUsage, let window = tu.contextWindow, window > 0 else { return nil }
            return Int((Double(tu.totalTokens) / Double(window)) * 100)
        }()
        // Tokens: prefer goal.tokensUsed when goal is set, otherwise
        // session-wide totalTokens. Same swap for duration. Either way
        // the cell shows a single number, just from the most relevant
        // source for the current session state.
        let goal = session.goal
        let totalTokens: Int64? = {
            if let g = goal, g.tokensUsed > 0 { return g.tokensUsed }
            guard let tu = session.tokenUsage, tu.totalTokens > 0 else { return nil }
            return tu.totalTokens
        }()
        let durationSeconds: Int64? = {
            if let g = goal, g.timeUsedSeconds > 0 { return g.timeUsedSeconds }
            if let ms = stats?.sessionDurationMs, ms > 0 { return ms / 1000 }
            return nil
        }()
        let adds = stats?.diffAdditions ?? 0
        let rems = stats?.diffDeletions ?? 0

        let hasMetrics = files > 0 || adds > 0 || rems > 0 || pct != nil || totalTokens != nil || durationSeconds != nil
        let hasAny = hasMetrics || goal != nil
        if hasAny {
            VStack(alignment: .leading, spacing: 0) {
                if let goal {
                    goalBanner(goal: goal)
                    if hasMetrics {
                        // Mid-rule between objective and metrics so they
                        // read as two zones inside the same panel.
                        Rectangle()
                            .fill(AgentBuddyTheme.border.opacity(0.4))
                            .frame(height: 0.5)
                            .padding(.vertical, 8)
                    }
                }
                if hasMetrics {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(alignment: .top, spacing: 14) {
                            statCell(icon: "chevron.left.forwardslash.chevron.right",
                                     value: files > 0 ? "\(files)" : nil,
                                     valueColor: AgentBuddyTheme.textPrimary,
                                     label: "files")
                            statCell(icon: nil,
                                     valuePrefix: nil,
                                     value: pct.map { "\($0)%" },
                                     valueColor: (pct ?? 0) > 80 ? AgentBuddyTheme.warning : AgentBuddyTheme.warning.opacity(0.85),
                                     label: "context")
                        }
                        HStack(alignment: .top, spacing: 14) {
                            statCell(icon: nil,
                                     value: adds > 0 ? "+\(adds.formatted(.number.grouping(.automatic)))" : nil,
                                     valueColor: AgentBuddyTheme.accent,
                                     label: "added")
                            statCell(icon: "snowflake",
                                     value: totalTokens.map { Self.formatTokens($0) },
                                     valueColor: AgentBuddyTheme.textPrimary,
                                     label: "tok")
                        }
                        HStack(alignment: .top, spacing: 14) {
                            statCell(icon: nil,
                                     value: rems > 0 ? "-\(rems.formatted(.number.grouping(.automatic)))" : nil,
                                     valueColor: AgentBuddyTheme.danger.opacity(0.85),
                                     label: "removed")
                            statCell(icon: "clock",
                                     value: durationSeconds.map { Self.formatDuration($0) },
                                     valueColor: AgentBuddyTheme.textPrimary,
                                     label: "duration")
                        }
                    }
                }
            }
            .agentBuddyMonoFont(size: 12, weight: .regular)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, 10)
            .overlay(alignment: .top) {
                Rectangle().fill(AgentBuddyTheme.border.opacity(0.5))
                    .frame(height: 0.5)
            }
            .overlay(alignment: .bottom) {
                Rectangle().fill(AgentBuddyTheme.border.opacity(0.5))
                    .frame(height: 0.5)
            }
            .padding(.top, 8)
        }
    }

    /// Single dashboard cell: optional icon, value (bold/coloured),
    /// dim label. If `value` is nil the cell renders as an empty
    /// spacer so paired rows in the dashboard stay aligned.
    @ViewBuilder
    private func statCell(
        icon: String?,
        valuePrefix: String? = nil,
        value: String?,
        valueColor: Color,
        label: String
    ) -> some View {
        if let value {
            HStack(spacing: 6) {
                if let icon {
                    Image(systemName: icon)
                        .agentBuddyFont(size: 10)
                        .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.8))
                        .frame(width: 14)
                }
                if let valuePrefix {
                    Text(valuePrefix)
                        .foregroundStyle(valueColor)
                }
                RollingMetricText(value)
                    .foregroundStyle(valueColor)
                Text(label)
                    .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.65))
                    .agentBuddyMonoFont(size: 11, weight: .regular)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            // Empty slot — keeps the column width stable so the paired
            // cell across the row doesn't shift when this one is missing.
            Color.clear.frame(height: 1).frame(maxWidth: .infinity)
        }
    }

    /// "86,012,400" → "86.0M", "12,400" → "12.4k", small values → "412".
    private static func formatTokens(_ value: Int64) -> String {
        if value >= 1_000_000 {
            return String(format: "%.1fM", Double(value) / 1_000_000.0)
        }
        if value >= 1_000 {
            return String(format: "%.1fk", Double(value) / 1_000.0)
        }
        return "\(value)"
    }

    /// "176560" → "49h 6m", "3320" → "55m 20s", "45" → "45s".
    private static func formatDuration(_ seconds: Int64) -> String {
        if seconds < 60 { return "\(seconds)s" }
        let total = Int(seconds)
        let minutes = total / 60
        let remainSecs = total % 60
        if total < 3600 {
            return remainSecs == 0 ? "\(minutes)m" : "\(minutes)m \(remainSecs)s"
        }
        let hours = total / 3600
        let remainMins = (total % 3600) / 60
        return remainMins == 0 ? "\(hours)h" : "\(hours)h \(remainMins)m"
    }
}

// MARK: - Canvas Animation Components

/// Stopwatch chip rendered at the right of the modelBadgeLine. When
/// `end` is nil the turn is live and a `TimelineView` drives a 1 Hz
/// re-eval. When `end` is provided, the chip is static and shows the
/// calculated turn duration (`end - start`) — no in-memory freeze.
private struct TurnStopwatchChip: View {
    let start: Date
    let end: Date?

    var body: some View {
        if let end {
            chip(seconds: max(0, end.timeIntervalSince(start)))
        } else {
            TimelineView(.periodic(from: .now, by: 1.0)) { context in
                chip(seconds: max(0, context.date.timeIntervalSince(start)))
            }
        }
    }

    @ViewBuilder
    private func chip(seconds: TimeInterval) -> some View {
        HStack(spacing: 2) {
            Image(systemName: "stopwatch")
                .agentBuddyFont(size: 8)
            // Monospaced digits so "14s" and "15s" have the same width.
            // Without this, each tick changes the chip's intrinsic size,
            // which cascades into list row re-measure → RootGeometry
            // invalidation on every active card every second. Mono
            // digits freeze that width so the chip can update in-place.
            RollingMetricText(Self.format(seconds))
        }
        .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.7))
    }

    private static func format(_ seconds: TimeInterval) -> String {
        let total = Int(seconds.rounded())
        if total < 60 { return "\(total)s" }
        let mins = total / 60
        let secs = total % 60
        return secs == 0 ? "\(mins)m" : "\(mins)m\(secs)s"
    }
}
