import Charts
import SwiftUI

extension ConversationInfoView {
    // MARK: - Section B: Server-Wide Charts

    var serverChartsSection: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.lg) {
            Text("Server Usage")
                .buddyText(.heading)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .accessibilityAddTraits(.isHeader)

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
        .frame(maxWidth: .infinity, alignment: .leading)
        .buddyCard(.surface, radius: BuddyRadius.card, padding: BuddySpacing.lg)
    }

    private func chartTitle(_ title: LocalizedStringKey) -> some View {
        Text(title)
            .buddyText(.label)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
    }

    /// Axis labels: 12pt caption with tabular digits, secondary text.
    private var axisLabelFont: Font { .caption.monospacedDigit() }

    private func tokenUsageChart(_ usage: AppServerUsageStats) -> some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            chartTitle("Token Usage by Conversation")

            Chart(Array(usage.tokensByThread.enumerated()), id: \.offset) { _, entry in
                AreaMark(
                    x: .value("Thread", entry.threadTitle),
                    y: .value("Tokens", entry.tokens)
                )
                .foregroundStyle(AgentBuddyTheme.link.opacity(0.18))
                .interpolationMethod(.catmullRom)

                LineMark(
                    x: .value("Thread", entry.threadTitle),
                    y: .value("Tokens", entry.tokens)
                )
                .foregroundStyle(AgentBuddyTheme.link)
                .lineStyle(StrokeStyle(lineWidth: 2))
                .interpolationMethod(.catmullRom)
            }
            .chartXAxis {
                AxisMarks { _ in
                    AxisValueLabel()
                        .font(axisLabelFont)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
            }
            .chartYAxis {
                AxisMarks { _ in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                        .foregroundStyle(AgentBuddyTheme.border)
                    AxisValueLabel()
                        .font(axisLabelFont)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
            }
            .frame(height: 160)
        }
    }

    private func activityChart(_ usage: AppServerUsageStats) -> some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            chartTitle("Activity Timeline")

            Chart(Array(usage.activityByDay.enumerated()), id: \.offset) { _, entry in
                BarMark(
                    x: .value("Date", Date(timeIntervalSince1970: TimeInterval(entry.dateEpoch)), unit: .day),
                    y: .value("Activity", entry.turnCount)
                )
                .foregroundStyle(AgentBuddyTheme.success)
                .cornerRadius(3)
            }
            .chartXAxis {
                AxisMarks(values: .automatic(desiredCount: 5)) { _ in
                    AxisValueLabel(format: .dateTime.month(.abbreviated).day())
                        .font(axisLabelFont)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
            }
            .chartYAxis {
                AxisMarks { _ in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                        .foregroundStyle(AgentBuddyTheme.border)
                    AxisValueLabel()
                        .font(axisLabelFont)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
            }
            .frame(height: 140)
        }
    }

    private func modelBreakdownChart(_ usage: AppServerUsageStats) -> some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            chartTitle("Model Usage")

            Chart(Array(usage.modelUsage.enumerated()), id: \.offset) { _, entry in
                BarMark(
                    x: .value("Count", entry.threadCount),
                    y: .value("Model", entry.model)
                )
                .foregroundStyle(AgentBuddyTheme.link)
                .cornerRadius(3)
            }
            .chartXAxis {
                AxisMarks { _ in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                        .foregroundStyle(AgentBuddyTheme.border)
                    AxisValueLabel()
                        .font(axisLabelFont)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
            }
            .chartYAxis {
                AxisMarks { _ in
                    AxisValueLabel()
                        .font(axisLabelFont)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                }
            }
            .frame(height: CGFloat(max(usage.modelUsage.count * 36, 60)))
        }
    }

    private func rateLimitGauge(_ rateLimits: RateLimitSnapshot) -> some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            chartTitle("Rate Limits")

            HStack(spacing: BuddySpacing.xl) {
                if let primary = rateLimits.primary {
                    rateLimitRing(label: "Primary", window: primary)
                }
                if let secondary = rateLimits.secondary {
                    rateLimitRing(label: "Secondary", window: secondary)
                }
            }
        }
    }

    private func rateLimitRing(label: LocalizedStringKey, window: RateLimitWindow) -> some View {
        let level = InfoUsageLevel(fraction: Double(window.usedPercent) / 100)
        return VStack(spacing: BuddySpacing.xs) {
            ZStack {
                Circle()
                    .stroke(AgentBuddyTheme.surfaceSoft, lineWidth: 6)
                Circle()
                    .trim(from: 0, to: Double(window.usedPercent) / 100)
                    .stroke(level.color, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                Text(verbatim: "\(window.usedPercent)%")
                    .buddyText(.label, weight: .semibold)
                    .monospacedDigit()
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
            }
            .frame(width: 64, height: 64)

            HStack(spacing: BuddySpacing.xxs) {
                if let systemImage = level.systemImage {
                    Image(systemName: systemImage)
                        .foregroundStyle(level.color)
                        .accessibilityHidden(true)
                }
                Text(label)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
            }
            .buddyText(.caption)
        }
        .accessibilityElement(children: .combine)
    }
}
