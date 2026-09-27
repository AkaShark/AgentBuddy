import SwiftUI
import HairballUI

struct SubagentCardView: View {
    @Environment(AppModel.self) private var appModel
    let data: ConversationMultiAgentActionData
    let serverId: String
    @State private var expanded: Bool
    @State private var sheetThreadKey: ThreadKey?
    @State private var sheetAgentLabel: String?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(
        data: ConversationMultiAgentActionData,
        serverId: String
    ) {
        self.data = data
        self.serverId = serverId
        _expanded = State(initialValue: true)
    }

    private var isInProgress: Bool { data.isInProgress }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            headerRow
                .contentShape(Rectangle())
                .onTapGesture {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        expanded.toggle()
                    }
                }

            if expanded {
                VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                    ForEach(Array(agentRows.enumerated()), id: \.offset) { _, row in
                        agentRowView(row)
                    }
                }
                .padding(.bottom, BuddySpacing.xs)
            }
        }
        .padding(.horizontal, BuddySpacing.md)
        .padding(.vertical, BuddySpacing.xxs)
        .frame(maxWidth: .infinity, alignment: .leading)
        .timelineDetailCard()
        .sheet(item: $sheetThreadKey) { key in
            let resolvedKey = appModel.snapshot?.resolvedThreadKey(for: key.threadId, serverId: key.serverId) ?? key
            SubagentDetailSheet(threadKey: resolvedKey, agentLabel: sheetAgentLabel)
                .environment(appModel)
        }
    }

    // MARK: - Header

    private var headerRow: some View {
        HStack(spacing: BuddySpacing.sm) {
            TimelineStatusGlyph(status: data.status.toolCallStatus, fallbackSystemImage: "person.2")

            Text(verbatim: actionLabel)
                .buddyText(.label)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .lineLimit(1)

            Spacer(minLength: BuddySpacing.xs)

            TimelineDisclosureChevron(expanded: expanded)
        }
        .frame(minHeight: BuddySize.minHitTarget)
        .accessibilityAddTraits(.isButton)
    }

    private var actionLabel: String {
        let agentCount = max(data.targets.count, data.agentStates.count)
        let suffix = agentCount == 1 ? String(localized: "1 agent") : String(localized: "\(agentCount) agents")
        switch data.tool.lowercased() {
        case "spawnagent", "spawn_agent":
            return String(localized: "Spawning \(suffix)")
        case "sendinput", "send_input":
            return String(localized: "Sending input to \(suffix)")
        case "resumeagent", "resume_agent":
            return String(localized: "Resuming \(suffix)")
        case "wait":
            return String(localized: "Waiting for \(suffix)")
        case "closeagent", "close_agent":
            return String(localized: "Closing \(suffix)")
        default:
            return "\(data.tool) \(suffix)"
        }
    }

    // MARK: - Agent Rows

    private var agentRows: [AgentRowData] {
        let statesByTarget = Dictionary(
            data.agentStates.map { ($0.targetId, $0) },
            uniquingKeysWith: { _, last in last }
        )

        var rows: [AgentRowData] = []
        for (index, target) in data.targets.enumerated() {
            let threadId = index < data.receiverThreadIds.count ? data.receiverThreadIds[index] : nil
            let state = threadId.flatMap { statesByTarget[$0] }
                ?? statesByTarget[target]
            let agentPrompt = index < data.perAgentPrompts.count ? data.perAgentPrompts[index] : nil
            rows.append(AgentRowData(
                label: target,
                threadId: threadId,
                status: state?.status,
                statusMessage: state?.message,
                prompt: agentPrompt
            ))
        }

        for state in data.agentStates where !rows.contains(where: { $0.threadId == state.targetId }) {
            if !rows.contains(where: { $0.label == state.targetId }) {
                rows.append(AgentRowData(
                    label: state.targetId,
                    threadId: state.targetId,
                    status: state.status,
                    statusMessage: state.message,
                    prompt: nil
                ))
            }
        }

        return rows
    }

    // MARK: - Resolve

    private func resolvedLabel(for row: AgentRowData) -> String {
        if !row.label.isEmpty && !looksLikeRawId(row.label) {
            return row.label
        }
        if let resolved = appModel.snapshot?.resolvedAgentTargetLabel(for: row.label, serverId: serverId) {
            return resolved
        }
        if let threadId = row.threadId,
           let resolved = appModel.snapshot?.resolvedAgentTargetLabel(for: threadId, serverId: serverId) {
            return resolved
        }
        return row.label
    }

    private func resolvedThreadKey(for row: AgentRowData) -> ThreadKey? {
        if let threadId = row.threadId {
            return appModel.snapshot?.resolvedThreadKey(for: threadId, serverId: serverId)
        }
        return nil
    }

    private func liveStatus(for row: AgentRowData) -> AppSubagentStatus? {
        if let key = resolvedThreadKey(for: row),
           let summary = appModel.snapshot?.sessionSummary(for: key) {
            if summary.hasActiveTurn { return .running }
            if summary.agentStatus != .unknown {
                return summary.agentStatus
            }
        }
        return row.status
    }

    private func looksLikeRawId(_ value: String) -> Bool {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 16 else { return false }
        return trimmed.range(of: #"^[0-9a-fA-F-]+$"#, options: .regularExpression) != nil
    }

    // MARK: - Row View

    private func agentRowView(_ row: AgentRowData) -> some View {
        let resolvedKey = resolvedThreadKey(for: row)
        let displayLabel = resolvedLabel(for: row)
        let status = liveStatus(for: row)
        let parts = parseAgentLabel(displayLabel)

        return VStack(alignment: .leading, spacing: 2) {
            // Line 1: Name + status + Open
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                let statusStr = readableStatus(status)
                let isActive = status == .running

                (
                    Text(parts.nickname)
                        .fontWeight(.semibold)
                        .foregroundColor(AgentBuddyTheme.textPrimary)
                    + Text(parts.roleSuffix)
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                    + Text(" \(statusStr)")
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                )
                .buddyText(.label, weight: .regular)
                .lineLimit(1)
                .truncationMode(.tail)
                .modifier(ShimmerText(active: isActive && !reduceMotion))

                Spacer(minLength: 8)

                if row.threadId != nil {
                    Button {
                        sheetAgentLabel = displayLabel
                        if let key = resolvedKey {
                            sheetThreadKey = key
                        } else if let threadId = row.threadId {
                            sheetThreadKey = ThreadKey(serverId: serverId, threadId: threadId)
                        }
                    } label: {
                        Text("Open")
                            .buddyText(.label, weight: .semibold)
                            .foregroundStyle(resolvedKey != nil ? AgentBuddyTheme.link : AgentBuddyTheme.textSecondary)
                            .frame(minWidth: BuddySize.minHitTarget, minHeight: BuddySize.minHitTarget)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }

            // Line 2: Per-agent prompt if available, else shared prompt
            if let prompt = row.prompt, !prompt.isEmpty {
                Text(prompt)
                    .buddyText(.caption)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .lineLimit(2)
                    .truncationMode(.tail)
            } else if let prompt = data.prompt, !prompt.isEmpty {
                Text(prompt)
                    .buddyText(.caption)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .lineLimit(2)
                    .truncationMode(.tail)
            }
        }
        .padding(.vertical, BuddySpacing.xxs)
    }

    private func readableStatus(_ status: AppSubagentStatus?) -> String {
        switch status ?? .unknown {
        case .running: return "is thinking"
        case .pendingInit: return "is awaiting instruction"
        case .completed: return "has completed"
        case .errored: return "encountered an error"
        case .interrupted: return "was interrupted"
        case .shutdown: return "was shut down"
        case .unknown: return ""
        }
    }

    private func parseAgentLabel(_ label: String) -> (nickname: String, roleSuffix: String) {
        guard label.hasSuffix("]"),
              let openBracket = label.lastIndex(of: "[") else {
            return (label, "")
        }
        let nickname = String(label[..<openBracket]).trimmingCharacters(in: .whitespacesAndNewlines)
        let roleStart = label.index(after: openBracket)
        let roleEnd = label.index(before: label.endIndex)
        let role = String(label[roleStart..<roleEnd])
        return (nickname, " (\(role))")
    }

}

// MARK: - Shimmer

private struct ShimmerText: ViewModifier {
    let active: Bool

    func body(content: Content) -> some View {
        if active {
            TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { timeline in
                let t = timeline.date.timeIntervalSinceReferenceDate
                let phase = CGFloat(t.truncatingRemainder(dividingBy: 2.0) / 2.0)

                content
                    .overlay {
                        GeometryReader { geo in
                            let w = geo.size.width
                            LinearGradient(
                                stops: [
                                    .init(color: AgentBuddyTheme.textPrimary.opacity(0), location: max(0, phase - 0.2)),
                                    .init(color: AgentBuddyTheme.textPrimary.opacity(0.35), location: phase),
                                    .init(color: AgentBuddyTheme.textPrimary.opacity(0), location: min(1, phase + 0.2))
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                            .frame(width: w, height: geo.size.height)
                        }
                        .blendMode(.sourceAtop)
                    }
                    .compositingGroup()
            }
        } else {
            content
        }
    }
}

extension ThreadKey: Identifiable {
    public var id: String { "\(serverId)/\(threadId)" }
}

private struct AgentRowData {
    let label: String
    let threadId: String?
    let status: AppSubagentStatus?
    let statusMessage: String?
    let prompt: String?
}

#if DEBUG
#Preview("Subagent Card") {
    ZStack {
        AgentBuddyTheme.background.ignoresSafeArea()
        VStack(spacing: 20) {
            SubagentCardView(
                data: ConversationMultiAgentActionData(
                    tool: "spawnAgent",
                    status: .inProgress,
                    prompt: "Explore /Users/sigkitten/dev/codex-app with a repo-orientation focus. Scan the top-level directories and identify the main modules.",
                    targets: ["Locke [explorer]", "Dalton [explorer]"],
                    receiverThreadIds: ["thread-abc-123", "thread-def-456"],
                    agentStates: [
                        ConversationMultiAgentState(targetId: "thread-abc-123", status: .running, message: nil),
                        ConversationMultiAgentState(targetId: "thread-def-456", status: .running, message: nil)
                    ]
                ),
                serverId: "preview-server"
            )

            SubagentCardView(
                data: ConversationMultiAgentActionData(
                    tool: "wait",
                    status: .completed,
                    prompt: nil,
                    targets: ["Locke [explorer]", "Dalton [explorer]"],
                    receiverThreadIds: ["thread-abc-123", "thread-def-456"],
                    agentStates: [
                        ConversationMultiAgentState(targetId: "thread-abc-123", status: .completed, message: nil),
                        ConversationMultiAgentState(targetId: "thread-def-456", status: .errored, message: "context limit")
                    ]
                ),
                serverId: "preview-server"
            )
        }
        .padding(16)
    }
}
#endif
