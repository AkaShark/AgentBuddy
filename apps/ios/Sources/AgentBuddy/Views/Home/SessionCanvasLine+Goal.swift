import SwiftUI
import UIKit

extension SessionCanvasLine {
    /// Goal banner that sits inside the dashboard panel at z4. Shows
    /// the small "GOAL" small-caps label, a status dot tinted by the
    /// goal's lifecycle, and the objective text (allowed to wrap).
    @ViewBuilder
    func goalBanner(goal: AppThreadGoal) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Text("GOAL")
                    .agentBuddyMonoFont(size: 9, weight: .semibold)
                    .tracking(1.2)
                    .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.65))
                Text(goalStatusLabel(goal.status))
                    .agentBuddyMonoFont(size: 9, weight: .semibold)
                    .tracking(0.6)
                    .foregroundStyle(goalStatusTint(goal.status).opacity(0.85))
            }
            HStack(alignment: .top, spacing: 8) {
                Circle()
                    .fill(goalStatusTint(goal.status))
                    .frame(width: 6, height: 6)
                    .padding(.top, 5)
                Text(goal.objective)
                    .agentBuddyMonoFont(size: 12, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary.opacity(0.95))
                    .lineLimit(2)
                    .truncationMode(.tail)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func goalStatusLabel(_ status: AppThreadGoalStatus) -> String {
        switch status {
        case .active: return "· ACTIVE"
        case .paused: return "· PAUSED"
        case .blocked: return "· BLOCKED"
        case .usageLimited: return "· USAGE"
        case .budgetLimited: return "· BUDGET"
        case .complete: return "· COMPLETE"
        }
    }

    // MARK: - Zoom 2+: goal line

    /// Single-line goal row with status pill, objective, and usage chips
    /// (tokens + elapsed seconds). Mirrors the in-conversation goal card
    /// without the gauge — the home card stays scan-friendly.
    @ViewBuilder
    var goalLine: some View {
        if let goal = session.goal {
            HStack(spacing: 6) {
                Circle()
                    .fill(goalStatusTint(goal.status))
                    .frame(width: 5, height: 5)
                Text(goal.objective)
                    .foregroundStyle(AgentBuddyTheme.textSecondary.opacity(0.85))
                    .lineLimit(1)
                    .truncationMode(.tail)
                Spacer(minLength: 6)
                if goal.tokensUsed > 0 {
                    HStack(spacing: 2) {
                        Image(systemName: "circle.hexagongrid")
                            .agentBuddyFont(size: 8)
                        RollingMetricText(formatGoalTokens(goal.tokensUsed))
                    }
                    .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.7))
                }
                if goal.timeUsedSeconds > 0 {
                    HStack(spacing: 2) {
                        Image(systemName: "clock")
                            .agentBuddyFont(size: 8)
                        RollingMetricText(formatGoalSeconds(goal.timeUsedSeconds))
                    }
                    .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.7))
                }
            }
            .agentBuddyMonoFont(size: 10, weight: .regular)
            .padding(.top, 1)
        }
    }

    private func goalStatusTint(_ status: AppThreadGoalStatus) -> Color {
        switch status {
        case .active: return AgentBuddyTheme.accent
        case .paused: return AgentBuddyTheme.textMuted
        case .blocked, .usageLimited, .budgetLimited: return AgentBuddyTheme.warning
        case .complete: return AgentBuddyTheme.success
        }
    }

    private func formatGoalTokens(_ value: Int64) -> String {
        if value >= 1_000_000 {
            return String(format: "%.1fM", Double(value) / 1_000_000.0)
        }
        if value >= 1_000 {
            return String(format: "%.1fk", Double(value) / 1_000.0)
        }
        return "\(value)"
    }

    private func formatGoalSeconds(_ seconds: Int64) -> String {
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
