import SwiftUI
import UIKit

extension SessionCanvasLine {
    // MARK: - Zoom 2: meta line

    private var metaLine: some View {
        HStack(spacing: 4) {
            Text(timeAgo)
                .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.8))
            // Only show the tool label + pulsing dots when a tool call
            // is actually executing. During pure LLM thinking/streaming
            // we fall through to the server + workspace metadata, same
            // as when the turn is idle.
            if isActive && isToolCallRunning {
                Text("\u{00b7}")
                    .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.5))
                toolActivityLabel
                SessionPulsingDots()
                statChips
            } else {
                Text("\u{00b7}")
                    .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.5))
                Text(session.serverDisplayName)
                    .foregroundStyle(AgentBuddyTheme.textSecondary.opacity(0.7))
                if let workspace = HomeDashboardSupport.workspaceLabel(for: session.cwd) {
                    Text("\u{00b7}")
                        .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.5))
                    Text(workspace)
                        .foregroundStyle(AgentBuddyTheme.textSecondary.opacity(0.8))
                }
                statChips
            }
        }
        .agentBuddyMonoFont(size: 10, weight: .regular)
        .lineLimit(1)
        .padding(.top, 2)
    }

    /// Inline stat chips: tool calls, turns, context %
    @ViewBuilder
    private var statChips: some View {
        if toolCallCount > 0 || turnCount > 0 {
            Text("\u{00b7}")
                .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.5))
        }
        if toolCallCount > 0 {
            Image(systemName: "chevron.left.forwardslash.chevron.right")
                .agentBuddyFont(size: 8)
                .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.7))
            RollingMetricText("\(toolCallCount)")
                .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.8))
        }
        if turnCount > 0 {
            Image(systemName: "arrow.turn.down.right")
                .agentBuddyFont(size: 8)
                .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.7))
            RollingMetricText("\(turnCount)")
                .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.8))
        }
        if let tu = session.tokenUsage, let window = tu.contextWindow, window > 0 {
            let pct = Int((Double(tu.totalTokens) / Double(window)) * 100)
            Text("\u{00b7}")
                .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.5))
            RollingMetricText("\(pct)%")
                .foregroundStyle(pct > 80 ? AgentBuddyTheme.warning.opacity(0.8) : AgentBuddyTheme.textMuted.opacity(0.8))
        }
    }

    @ViewBuilder
    private var toolActivityLabel: some View {
        if let toolLabel = session.lastToolLabel {
            let parts = toolLabel.split(separator: " ", maxSplits: 1)
            let name = String(parts.first ?? "")
            toolIconView(for: name)
                .foregroundStyle(AgentBuddyTheme.accent)
            if parts.count > 1 {
                Text(String(parts.last ?? ""))
                    .foregroundStyle(AgentBuddyTheme.textSecondary.opacity(0.8))
                    .lineLimit(1)
                    .truncationMode(.tail)
            }
        } else {
            Text("thinking")
                .foregroundStyle(AgentBuddyTheme.accent)
        }
    }

    // MARK: - Zoom 2+: identity strip (time · server · model · branch)
    //
    // This row owns *only* identity. Telemetry (counts, %, adds/rems,
    // stopwatch) lives below in `telemetryStrip` so a 390 px iPhone row
    // never has to choose between truncating the server name and showing
    // a stat. At zoom 2 the strip stands alone (no telemetry yet); at
    // zoom 3+ it sits on top of the telemetry strip.

    var modelBadgeLine: some View {
        HStack(spacing: 4) {
            Text(timeAgo)
                .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.8))
            Text("\u{00b7}")
                .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.5))
            Image(systemName: "server.rack")
                .agentBuddyFont(size: 8)
                .foregroundStyle(AgentBuddyTheme.accent.opacity(0.5))
            Text(session.serverDisplayName)
                .foregroundStyle(AgentBuddyTheme.accent.opacity(0.6))
            let m = session.model.trimmingCharacters(in: .whitespacesAndNewlines)
            if !m.isEmpty {
                Text("\u{00b7}").foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.5))
                HomeRuntimeIcon(kind: session.agentRuntimeKind)
                Text(m)
                    .foregroundStyle(AgentBuddyTheme.textSecondary.opacity(0.7))
            }
            if let lineage = session.lineage, lineage.hasMultipleBranches {
                Text("\u{00b7}").foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.5))
                branchChip(lineage: lineage)
            } else if session.isFork {
                Text("\u{00b7}").foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.5))
                Text("fork")
                    .foregroundStyle(AgentBuddyTheme.warning.opacity(0.8))
            }
            if session.isSubagent, let agent = session.agentLabel {
                Text("\u{00b7}").foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.5))
                Text(agent)
                    .foregroundStyle(AgentBuddyTheme.accent.opacity(0.6))
            }
            Spacer(minLength: 0)
        }
        .agentBuddyMonoFont(size: 10, weight: .regular)
        .lineLimit(1)
        .truncationMode(.tail)
        .padding(.top, 1)
    }
}

private struct SessionPulsingDots: View {
    @State private var phase = 0

    var body: some View {
        HStack(spacing: 2) {
            ForEach(0..<3, id: \.self) { i in
                Circle()
                    .fill(AgentBuddyTheme.accent)
                    .frame(width: 3, height: 3)
                    .opacity(phase == i ? 1.0 : 0.25)
            }
        }
        .onAppear {
            Timer.scheduledTimer(withTimeInterval: 0.3, repeats: true) { _ in
                withAnimation(.easeInOut(duration: 0.15)) {
                    phase = (phase + 1) % 3
                }
            }
        }
    }
}

private struct HomeRuntimeIcon: View {
    let kind: AgentRuntimeKind

    var body: some View {
        AgentIconView(kind: kind, size: 15)
            .clipShape(RoundedRectangle(cornerRadius: 3, style: .continuous))
            .accessibilityLabel(kind.displayLabel)
    }
}
