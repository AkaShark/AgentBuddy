import Charts
import SwiftUI

extension ConversationInfoView {
    // MARK: - Section B: Server-Wide Charts

    var serverChartsSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Server Usage")
                .agentBuddyFont(size: 14, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.textPrimary)

            if let usage = serverUsage {
                if !usage.tokensByThread.isEmpty {
                    tokenUsageChart(usage)
                }

                if !usage.activityByDay.isEmpty {
                    activityChart(usage)
                }

                if !usage.modelUsage.isEmpty {
                    modelBreakdownChart(usage)
                }
            }

            if let rateLimits = server?.rateLimits {
                rateLimitGauge(rateLimits)
            }
        }
        .padding(16)
        .modifier(GlassRectModifier(cornerRadius: 12))
    }

    private func tokenUsageChart(_ usage: AppServerUsageStats) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Token Usage by Conversation")
                .agentBuddyFont(size: 12, weight: .medium)
                .foregroundStyle(AgentBuddyTheme.textSecondary)

            Chart(Array(usage.tokensByThread.enumerated()), id: \.offset) { _, entry in
                AreaMark(
                    x: .value("Thread", entry.threadTitle),
                    y: .value("Tokens", entry.tokens)
                )
                .foregroundStyle(AgentBuddyTheme.accent.opacity(0.3))
                .interpolationMethod(.catmullRom)

                LineMark(
                    x: .value("Thread", entry.threadTitle),
                    y: .value("Tokens", entry.tokens)
                )
                .foregroundStyle(AgentBuddyTheme.accent)
                .interpolationMethod(.catmullRom)
            }
            .chartXAxis {
                AxisMarks { _ in
                    AxisValueLabel()
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundStyle(AgentBuddyTheme.textMuted)
                }
            }
            .chartYAxis {
                AxisMarks { _ in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                        .foregroundStyle(AgentBuddyTheme.border)
                    AxisValueLabel()
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundStyle(AgentBuddyTheme.textMuted)
                }
            }
            .frame(height: 160)
        }
    }

    private func activityChart(_ usage: AppServerUsageStats) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Activity Timeline")
                .agentBuddyFont(size: 12, weight: .medium)
                .foregroundStyle(AgentBuddyTheme.textSecondary)

            Chart(Array(usage.activityByDay.enumerated()), id: \.offset) { _, entry in
                BarMark(
                    x: .value("Date", Date(timeIntervalSince1970: TimeInterval(entry.dateEpoch)), unit: .day),
                    y: .value("Activity", entry.turnCount)
                )
                .foregroundStyle(AgentBuddyTheme.accent.opacity(0.7))
                .cornerRadius(2)
            }
            .chartXAxis {
                AxisMarks(values: .automatic(desiredCount: 5)) { _ in
                    AxisValueLabel(format: .dateTime.month(.abbreviated).day())
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundStyle(AgentBuddyTheme.textMuted)
                }
            }
            .chartYAxis {
                AxisMarks { _ in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                        .foregroundStyle(AgentBuddyTheme.border)
                    AxisValueLabel()
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundStyle(AgentBuddyTheme.textMuted)
                }
            }
            .frame(height: 140)
        }
    }

    private func modelBreakdownChart(_ usage: AppServerUsageStats) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Model Usage")
                .agentBuddyFont(size: 12, weight: .medium)
                .foregroundStyle(AgentBuddyTheme.textSecondary)

            Chart(Array(usage.modelUsage.enumerated()), id: \.offset) { _, entry in
                BarMark(
                    x: .value("Count", entry.threadCount),
                    y: .value("Model", entry.model)
                )
                .foregroundStyle(AgentBuddyTheme.accent.opacity(0.7))
                .cornerRadius(2)
            }
            .chartXAxis {
                AxisMarks { _ in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                        .foregroundStyle(AgentBuddyTheme.border)
                    AxisValueLabel()
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundStyle(AgentBuddyTheme.textMuted)
                }
            }
            .chartYAxis {
                AxisMarks { _ in
                    AxisValueLabel()
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
            }
            .frame(height: CGFloat(max(usage.modelUsage.count * 32, 60)))
        }
    }

    private func rateLimitGauge(_ rateLimits: RateLimitSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Rate Limits")
                .agentBuddyFont(size: 12, weight: .medium)
                .foregroundStyle(AgentBuddyTheme.textSecondary)

            HStack(spacing: 16) {
                if let primary = rateLimits.primary {
                    rateLimitRing(label: "Primary", window: primary)
                }
                if let secondary = rateLimits.secondary {
                    rateLimitRing(label: "Secondary", window: secondary)
                }
            }
        }
    }

    private func rateLimitRing(label: String, window: RateLimitWindow) -> some View {
        VStack(spacing: 6) {
            ZStack {
                Circle()
                    .stroke(AgentBuddyTheme.border, lineWidth: 4)
                Circle()
                    .trim(from: 0, to: Double(window.usedPercent) / 100)
                    .stroke(rateLimitColor(percent: Int(window.usedPercent)), style: StrokeStyle(lineWidth: 4, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                Text("\(window.usedPercent)%")
                    .agentBuddyFont(size: 12, weight: .bold)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
            }
            .frame(width: 56, height: 56)

            Text(label)
                .agentBuddyFont(size: 10)
                .foregroundStyle(AgentBuddyTheme.textMuted)
        }
    }

    private func rateLimitColor(percent: Int) -> Color {
        if percent >= 80 { return AgentBuddyTheme.danger }
        if percent >= 60 { return AgentBuddyTheme.warning }
        return AgentBuddyTheme.accent
    }
}
