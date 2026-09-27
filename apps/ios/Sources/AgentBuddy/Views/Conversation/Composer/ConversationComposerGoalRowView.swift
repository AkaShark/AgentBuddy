import SwiftUI

struct GoalCardActions {
    var togglePause: () -> Void
    var markComplete: () -> Void
    var setObjective: (String) -> Void
    var setBudget: (Int64?) -> Void
    var clear: () -> Void

    static let noop = GoalCardActions(
        togglePause: {},
        markComplete: {},
        setObjective: { _ in },
        setBudget: { _ in },
        clear: {}
    )
}

struct ConversationComposerGoalRowView: View {
    let goal: AppThreadGoal
    let actions: GoalCardActions

    @State private var showEditSheet = false
    @State private var showBudgetSheet = false
    @State private var showClearConfirm = false
    @State private var draftObjective = ""
    @State private var draftBudget = ""
    @State private var pulsing = false
    @State private var animatedProgress: Double = 0

    private let cornerRadius: CGFloat = 12

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .center, spacing: 8) {
                statusPill

                Text(goal.objective)
                    .agentBuddyFont(.caption)
                    .foregroundColor(AgentBuddyTheme.textPrimary)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        draftObjective = goal.objective
                        showEditSheet = true
                    }
                    .accessibilityHint("Tap to edit objective")

                overflowMenu
            }

            if let progress = budgetProgress {
                budgetGauge(progress: progress)
            }

            if hasUsageMetrics {
                usageMetricsRow
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .modifier(GoalCardChromeModifier(statusTint: statusTint, cornerRadius: cornerRadius))
        .alert("Edit Goal", isPresented: $showEditSheet) {
            TextField("Objective", text: $draftObjective, axis: .vertical)
            Button("Save") {
                let trimmed = draftObjective.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty { actions.setObjective(trimmed) }
            }
            Button("Cancel", role: .cancel) {}
        }
        .alert("Token Budget", isPresented: $showBudgetSheet) {
            TextField("e.g. 50000", text: $draftBudget)
                .keyboardType(.numberPad)
            Button("Save") {
                let trimmed = draftBudget.trimmingCharacters(in: .whitespaces)
                if let value = Int64(trimmed), value > 0 {
                    actions.setBudget(value)
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Set a token cap for this goal. The agent will pause when the cap is reached.")
        }
        .confirmationDialog(
            "Clear this goal?",
            isPresented: $showClearConfirm,
            titleVisibility: .visible
        ) {
            Button("Clear Goal", role: .destructive) { actions.clear() }
            Button("Cancel", role: .cancel) {}
        }
        .onAppear {
            animatedProgress = budgetProgress ?? 0
            if goal.status == .active { pulsing = true }
        }
        .onChange(of: budgetProgress ?? 0) { _, new in
            withAnimation(.spring(response: 0.55, dampingFraction: 0.85)) {
                animatedProgress = new
            }
        }
        .onChange(of: goal.status) { _, new in
            pulsing = (new == .active)
        }
    }

    private var statusPill: some View {
        Button(action: { if canTogglePause { actions.togglePause() } }) {
            HStack(spacing: 5) {
                Circle()
                    .fill(statusTint)
                    .frame(width: 6, height: 6)
                    .opacity(goal.status == .active ? (pulsing ? 0.35 : 1.0) : 1.0)
                    .animation(
                        goal.status == .active
                            ? .easeInOut(duration: 1.1).repeatForever(autoreverses: true)
                            : .default,
                        value: pulsing
                    )

                Text(statusLabel)
                    .agentBuddyMonoFont(size: 10, weight: .semibold)
                    .foregroundColor(statusTint)
                    .textCase(.uppercase)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(Capsule().fill(statusTint.opacity(0.14)))
            .overlay(Capsule().stroke(statusTint.opacity(0.35), lineWidth: 0.5))
        }
        .buttonStyle(.plain)
        .disabled(!canTogglePause)
        .accessibilityLabel(pauseToggleAccessibilityLabel)
    }

    private var overflowMenu: some View {
        Menu {
            if let pauseResume = pauseResumeMenuItem {
                Button {
                    actions.togglePause()
                } label: {
                    Label(pauseResume.label, systemImage: pauseResume.systemImage)
                }
            }

            Button {
                draftObjective = goal.objective
                showEditSheet = true
            } label: {
                Label("Edit Objective", systemImage: "pencil")
            }

            Button {
                draftBudget = goal.tokenBudget.map { String($0) } ?? ""
                showBudgetSheet = true
            } label: {
                Label("Set Token Budget", systemImage: "gauge.with.dots.needle.50percent")
            }

            if goal.status != .complete {
                Button {
                    actions.markComplete()
                } label: {
                    Label("Mark Complete", systemImage: "checkmark.circle")
                }
            }

            Divider()

            Button(role: .destructive) {
                showClearConfirm = true
            } label: {
                Label("Clear Goal", systemImage: "trash")
            }
        } label: {
            Image(systemName: "ellipsis")
                .agentBuddyFont(size: 12, weight: .bold)
                .foregroundColor(AgentBuddyTheme.textSecondary)
                .frame(width: 24, height: 22)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("Goal actions")
    }

    private func budgetGauge(progress: Double) -> some View {
        let percent = Int((progress * 100).rounded())
        return HStack(spacing: 8) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(statusTint.opacity(0.10))
                    Capsule()
                        .fill(
                            LinearGradient(
                                colors: [progressTint.opacity(0.85), progressTint],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: max(geo.size.width * animatedProgress, animatedProgress > 0 ? 6 : 0))
                }
            }
            .frame(height: 6)
            .clipShape(Capsule())

            HStack(spacing: 4) {
                if let budgetLabel {
                    Text(budgetLabel)
                        .agentBuddyMonoFont(size: 10, weight: .semibold)
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                }
                Text("\(percent)%")
                    .agentBuddyMonoFont(size: 10, weight: .bold)
                    .foregroundColor(progressTextTint)
            }
            .fixedSize()
        }
    }

    private var canTogglePause: Bool {
        switch goal.status {
        case .active, .paused, .blocked, .usageLimited, .budgetLimited: return true
        case .complete: return false
        }
    }

    private var pauseToggleAccessibilityLabel: String {
        switch goal.status {
        case .active: return "Pause goal"
        case .paused: return "Resume goal"
        case .blocked: return "Resume goal (override block)"
        case .usageLimited: return "Resume goal (override usage cap)"
        case .budgetLimited: return "Resume goal (override budget cap)"
        case .complete: return "Goal complete"
        }
    }

    private var pauseResumeMenuItem: (label: String, systemImage: String)? {
        switch goal.status {
        case .active: return ("Pause Goal", "pause.circle")
        case .paused: return ("Resume Goal", "play.circle")
        case .blocked: return ("Resume Goal (override block)", "play.circle")
        case .usageLimited: return ("Resume Goal (override usage cap)", "play.circle")
        case .budgetLimited: return ("Resume Goal (override cap)", "play.circle")
        case .complete: return nil
        }
    }

    private var statusTint: Color {
        switch goal.status {
        case .active: return AgentBuddyTheme.accent
        case .paused: return AgentBuddyTheme.textMuted
        case .blocked, .usageLimited, .budgetLimited: return AgentBuddyTheme.warning
        case .complete: return AgentBuddyTheme.success
        }
    }

    private var statusLabel: String {
        switch goal.status {
        case .active: return "active"
        case .paused: return "paused"
        case .blocked: return "blocked"
        case .usageLimited: return "usage limit"
        case .budgetLimited: return "limited"
        case .complete: return "complete"
        }
    }

    private var budgetProgress: Double? {
        guard let budget = goal.tokenBudget, budget > 0 else { return nil }
        let raw = Double(goal.tokensUsed) / Double(budget)
        return min(max(raw, 0), 1)
    }

    private var budgetLabel: String? {
        guard let budget = goal.tokenBudget, budget > 0 else { return nil }
        return "\(formatTokens(goal.tokensUsed)) / \(formatTokens(budget))"
    }

    private var progressTextTint: Color {
        guard let progress = budgetProgress else { return AgentBuddyTheme.textSecondary }
        if progress >= 1.0 { return AgentBuddyTheme.danger }
        if progress >= 0.85 { return AgentBuddyTheme.warning }
        return AgentBuddyTheme.textSecondary
    }

    private var progressTint: Color {
        guard let progress = budgetProgress else { return statusTint }
        if progress >= 1.0 { return AgentBuddyTheme.danger }
        if progress >= 0.85 { return AgentBuddyTheme.warning }
        return statusTint
    }

    private func formatTokens(_ value: Int64) -> String {
        if value >= 1_000_000 {
            return String(format: "%.1fM", Double(value) / 1_000_000.0)
        }
        if value >= 1_000 {
            return String(format: "%.1fk", Double(value) / 1_000.0)
        }
        return "\(value)"
    }

    private var hasUsageMetrics: Bool {
        goal.tokensUsed > 0 || goal.timeUsedSeconds > 0
    }

    private var usageMetricsRow: some View {
        HStack(spacing: 6) {
            if goal.tokensUsed > 0 {
                HStack(spacing: 3) {
                    Image(systemName: "circle.hexagongrid")
                        .agentBuddyMonoFont(size: 9, weight: .semibold)
                    RollingMetricText(formatTokens(goal.tokensUsed))
                        .agentBuddyMonoFont(size: 10, weight: .semibold)
                }
                .foregroundColor(AgentBuddyTheme.textSecondary)
            }
            if goal.tokensUsed > 0 && goal.timeUsedSeconds > 0 {
                Text("·")
                    .agentBuddyMonoFont(size: 10, weight: .semibold)
                    .foregroundColor(AgentBuddyTheme.textMuted.opacity(0.6))
            }
            if goal.timeUsedSeconds > 0 {
                HStack(spacing: 3) {
                    Image(systemName: "clock")
                        .agentBuddyMonoFont(size: 9, weight: .semibold)
                    RollingMetricText(formatSeconds(goal.timeUsedSeconds))
                        .agentBuddyMonoFont(size: 10, weight: .semibold)
                }
                .foregroundColor(AgentBuddyTheme.textSecondary)
            }
            Spacer(minLength: 0)
        }
    }

    private func formatSeconds(_ seconds: Int64) -> String {
        if seconds < 60 { return "\(seconds)s" }
        let totalSeconds = Int(seconds)
        let minutes = totalSeconds / 60
        let remainSecs = totalSeconds % 60
        if totalSeconds < 3600 {
            return remainSecs == 0 ? "\(minutes)m" : "\(minutes)m \(remainSecs)s"
        }
        let hours = totalSeconds / 3600
        let remainMins = (totalSeconds % 3600) / 60
        return remainMins == 0 ? "\(hours)h" : "\(hours)h \(remainMins)m"
    }
}

/// Card chrome for the goal row. On iOS 26+ uses Liquid Glass tinted with the
/// status color; on older iOS falls back to a vertical gradient that blends
/// the codeBackground into a subtle status-tinted wash at the bottom.
private struct GoalCardChromeModifier: ViewModifier {
    let statusTint: Color
    let cornerRadius: CGFloat

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .glassEffect(
                    .regular.tint(statusTint.opacity(0.14)).interactive(),
                    in: .rect(cornerRadius: cornerRadius)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .stroke(statusTint.opacity(0.20), lineWidth: 0.5)
                )
        } else {
            content
                .background(
                    LinearGradient(
                        colors: [
                            AgentBuddyTheme.codeBackground.opacity(0.92),
                            statusTint.opacity(0.08)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .stroke(statusTint.opacity(0.28), lineWidth: 1)
                )
        }
    }
}
