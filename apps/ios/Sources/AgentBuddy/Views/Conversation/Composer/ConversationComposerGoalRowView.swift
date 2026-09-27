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

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            HStack(alignment: .center, spacing: BuddySpacing.xs) {
                statusPill
                Spacer(minLength: 0)
                overflowMenu
            }

            Text(verbatim: goal.objective)
                .buddyText(.label, weight: .regular)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .lineLimit(3)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.trailing, BuddySpacing.xs)
                .padding(.top, -BuddySpacing.xs)
                .contentShape(Rectangle())
                .onTapGesture {
                    draftObjective = goal.objective
                    showEditSheet = true
                }
                .accessibilityHint("Tap to edit objective")

            if let progress = budgetProgress {
                budgetGauge(progress: progress)
            }

            if hasUsageMetrics {
                usageMetricsRow
            }
        }
        .padding(.leading, BuddySpacing.sm)
        .padding(.trailing, BuddySpacing.xxs)
        .padding(.vertical, BuddySpacing.xxs)
        .frame(maxWidth: .infinity, alignment: .leading)
        .buddyCard(.surface, radius: BuddyRadius.detailCard, padding: nil)
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
            withAnimation(reduceMotion ? nil : .spring(response: 0.55, dampingFraction: 0.85)) {
                animatedProgress = new
            }
        }
        .onChange(of: goal.status) { _, new in
            pulsing = (new == .active)
        }
    }

    private var statusPill: some View {
        Button(action: { if canTogglePause { actions.togglePause() } }) {
            HStack(spacing: BuddySpacing.xxs) {
                Image(systemName: statusIcon)
                    .font(.system(size: 12, weight: .bold))
                    .opacity(goal.status == .active && !reduceMotion ? (pulsing ? 0.45 : 1.0) : 1.0)
                    .animation(
                        goal.status == .active && !reduceMotion
                            ? .easeInOut(duration: 1.1).repeatForever(autoreverses: true)
                            : .default,
                        value: pulsing
                    )
                    .accessibilityHidden(true)

                Text(statusLabel)
                    .buddyText(.caption, weight: .semibold)
                    .lineLimit(1)
            }
            .foregroundStyle(statusTint)
            .padding(.horizontal, BuddySpacing.xs)
            .frame(minHeight: 28)
            .background(statusSurface, in: Capsule())
            .frame(minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .fixedSize()
        .disabled(!canTogglePause)
        .accessibilityLabel(pauseToggleAccessibilityLabel)
    }

    private var overflowMenu: some View {
        Menu {
            if let pauseResume = pauseResumeMenuItem {
                Button {
                    actions.togglePause()
                } label: {
                    Label(LocalizedStringKey(pauseResume.label), systemImage: pauseResume.systemImage)
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
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("Goal actions")
    }

    private func budgetGauge(progress: Double) -> some View {
        let percent = Int((progress * 100).rounded())
        return HStack(spacing: BuddySpacing.xs) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(AgentBuddyTheme.surfaceSoft)
                    Capsule()
                        .fill(progressTint)
                        .frame(width: max(geo.size.width * animatedProgress, animatedProgress > 0 ? 6 : 0))
                }
            }
            .frame(height: 6)
            .clipShape(Capsule())
            .accessibilityHidden(true)

            HStack(spacing: BuddySpacing.xxs) {
                if let budgetLabel {
                    Text(verbatim: budgetLabel)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
                Text(verbatim: "\(percent)%")
                    .fontWeight(.semibold)
                    .foregroundStyle(progressTextTint)
            }
            .buddyText(.caption)
            .monospacedDigit()
            .fixedSize()
        }
        .padding(.trailing, BuddySpacing.xs)
        .accessibilityElement(children: .combine)
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
        case .active: return AgentBuddyTheme.link
        case .paused: return AgentBuddyTheme.textSecondary
        case .blocked, .usageLimited, .budgetLimited: return AgentBuddyTheme.warning
        case .complete: return AgentBuddyTheme.success
        }
    }

    private var statusSurface: Color {
        switch goal.status {
        case .active, .paused: return AgentBuddyTheme.surfaceSoft
        case .blocked, .usageLimited, .budgetLimited: return AgentBuddyTheme.warningSurface
        case .complete: return AgentBuddyTheme.successSurface
        }
    }

    private var statusIcon: String {
        switch goal.status {
        case .active: return "target"
        case .paused: return "pause.fill"
        case .blocked: return "exclamationmark.triangle.fill"
        case .usageLimited, .budgetLimited: return "gauge.with.dots.needle.100percent"
        case .complete: return "checkmark"
        }
    }

    private var statusLabel: LocalizedStringKey {
        switch goal.status {
        case .active: return "Goal active"
        case .paused: return "Paused"
        case .blocked: return "Blocked"
        case .usageLimited: return "Usage limit"
        case .budgetLimited: return "Budget reached"
        case .complete: return "Complete"
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
        HStack(spacing: BuddySpacing.xs) {
            if goal.tokensUsed > 0 {
                HStack(spacing: BuddySpacing.xxs) {
                    Image(systemName: "circle.hexagongrid")
                        .accessibilityHidden(true)
                    RollingMetricText(formatTokens(goal.tokensUsed))
                }
            }
            if goal.tokensUsed > 0 && goal.timeUsedSeconds > 0 {
                Text(verbatim: "·")
                    .accessibilityHidden(true)
            }
            if goal.timeUsedSeconds > 0 {
                HStack(spacing: BuddySpacing.xxs) {
                    Image(systemName: "clock")
                        .accessibilityHidden(true)
                    RollingMetricText(formatSeconds(goal.timeUsedSeconds))
                }
            }
            Spacer(minLength: 0)
        }
        .buddyText(.caption)
        .monospacedDigit()
        .foregroundStyle(AgentBuddyTheme.textSecondary)
        .padding(.bottom, BuddySpacing.xxs)
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
